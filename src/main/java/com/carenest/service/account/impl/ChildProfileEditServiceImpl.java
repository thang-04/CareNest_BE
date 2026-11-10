package com.carenest.service.account.impl;

import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.dto.child.ChildEditDraft;
import com.carenest.dto.child.ChildEditDraft.GuardianEdit;
import com.carenest.dto.child.ChildUpdateResponse;
import com.carenest.dto.child.GuardianAccountChangeResponse;
import com.carenest.dto.child.GuardianDraft;
import com.carenest.dto.child.GuardianRecord;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.account.GuardianPhoneChangeRequest;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.ChildProfileEditService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sửa hồ sơ trẻ kèm tài khoản phụ huynh (mục 4.2, 5.2, 5.4 plan tiếp nhận; G94-180/AUTH-21 sửa 2026-11-10). Thứ tự: kiểm
 * hết (không ghi) → một giao dịch ghi hồ sơ + tài khoản + yêu cầu đổi SĐT + thông báo → sau commit đồng bộ họ tên/email
 * sang Keycloak. Không log SĐT/CCCD/họ tên — chỉ id, số lượng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildProfileEditServiceImpl implements ChildProfileEditService {
    private static final String LOG_PREFIX = "[ChildProfileEditServiceImpl]";
    static final String BOTH_CHANGED =
            "Phụ huynh đã có tài khoản: không đổi cùng lúc CCCD và số điện thoại. Đổi từng thông tin, hoặc gỡ phụ huynh ở"
                    + " màn Tài khoản phụ huynh rồi thêm người mới.";
    static final String NEED_ACCOUNT_PERMISSION =
            "Phụ huynh đã có tài khoản: cần quyền Quản lý tài khoản phụ huynh để sửa họ tên, email, CCCD.";
    static final String STAFF_PHONE =
            "Phụ huynh này cũng là tài khoản nhân sự: đổi số điện thoại ở màn Tài khoản nhân sự.";
    static final String USERNAME_TAKEN = "Số điện thoại này đã là tên đăng nhập của tài khoản khác.";

    private final ChildRecordService childRecordService;
    private final ChildIntakeService childIntakeService;
    private final UserAccountRepository userAccountRepository;
    private final AccountLifecycleOperations accountLifecycle;
    private final PhoneChangeSupport phoneChanges;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ChildUpdateResponse update(CurrentUser actor, Long childId, UpdateChildRequest request) {
        String logPrefix = LOG_PREFIX + "|update|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            ChildEditDraft draft = childRecordService.prepareEdit(actor, childId, request);
            Map<Long, UserAccount> accounts = userAccountRepository
                    .findAllById(draft.guardians().stream()
                            .map(GuardianEdit::accountId)
                            .filter(Objects::nonNull)
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
            boolean manageAccounts = actor.hasPermission(Permission.PARENT_ACCOUNT_MANAGE);

            List<FieldErrorResponse> errors = new ArrayList<>();
            List<AccountEdit> accountEdits = new ArrayList<>();
            List<GuardianEdit> finalGuardians = new ArrayList<>();
            for (int i = 0; i < draft.guardians().size(); i++) {
                GuardianEdit edit = draft.guardians().get(i);
                UserAccount account = edit.accountId() == null ? null : accounts.get(edit.accountId());
                if (account == null) {
                    finalGuardians.add(edit);
                    continue;
                }
                AccountEdit change = AccountEdit.of(i, edit, account);
                String prefix = "guardians." + i + ".";
                if (change.phoneChanged() && change.citizenChanged()) {
                    errors.add(new FieldErrorResponse(prefix + "idNumber", BOTH_CHANGED));
                } else if (change.phoneChanged() && account.getPrimaryRole() != CareNestRole.PARENT) {
                    errors.add(new FieldErrorResponse(prefix + "phone", STAFF_PHONE));
                }
                if (change.infoChanged() && !manageAccounts) {
                    errors.add(new FieldErrorResponse(prefix + change.firstInfoField(), NEED_ACCOUNT_PERMISSION));
                }
                accountEdits.add(change);
                // AUTH-21 (2026-11-10): SĐT mới chờ duyệt, hồ sơ giữ số đang lưu
                finalGuardians.add(
                        change.phoneChanged() ? edit.withDraft(withPhone(edit.draft(), edit.currentPhone())) : edit);
            }
            if (!errors.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
            }
            List<FieldErrorResponse> conflicts = identityConflicts(childId, draft.guardians(), accounts);
            if (!conflicts.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.GUARDIAN_IDENTITY_CONFLICT, conflicts);
            }

            ChildEditDraft resolved = draft.withGuardians(finalGuardians);
            String childName = draft.fullName();
            Saved saved = transactionTemplate.execute(status -> {
                childRecordService.applyEdit(actor, resolved);
                List<UserAccount> updated = new ArrayList<>();
                List<GuardianPhoneChangeRequest> opened = new ArrayList<>();
                for (AccountEdit change : accountEdits) {
                    UserAccount account = userAccountRepository
                            .findById(change.account().getId())
                            .orElseThrow(() -> GlobalException.of(ApiCode.GUARDIAN_IDENTITY_CONFLICT));
                    GuardianDraft g = change.edit().draft();
                    if (change.infoChanged() || account.getCitizenId() == null) {
                        account.applyGuardianContact(g.fullName(), g.email());
                        account.changeCitizenId(g.citizenId());
                        userAccountRepository.saveAndFlush(account);
                        childRecordService.syncGuardianAccountInfo(
                                account.getId(), g.fullName(), g.email(), g.citizenId());
                        if (change.infoChanged()) updated.add(account);
                    }
                    if (change.phoneChanged()) {
                        opened.add(phoneChanges.open(
                                actor,
                                childId,
                                draft.campusId(),
                                childName,
                                change.edit().guardianId(),
                                account,
                                g.phone(),
                                draft.reason()));
                    }
                }
                return new Saved(updated, opened);
            });

            for (UserAccount account : saved.updated()) {
                accountLifecycle.syncProfileQuietly(logPrefix, account);
            }
            List<GuardianAccountChangeResponse> accountChanges = saved.updated().stream()
                    .map(a ->
                            new GuardianAccountChangeResponse(a.getId(), a.getFullName(), a.getUsername(), false, null))
                    .toList();
            List<GuardianPhoneChangeResponse> requests = saved.opened().isEmpty()
                    ? List.of()
                    : phoneChanges.toResponses(actor, saved.opened(), phoneChanges.childrenOf(saved.opened()));
            ChildUpdateResponse response =
                    new ChildUpdateResponse(childIntakeService.getChildView(childId), accountChanges, requests);
            log.info("{}|accountsUpdated={}|phoneRequests={}|END", logPrefix, accountChanges.size(), requests.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /**
     * Một người một tài khoản (mục 5.2, như tiếp nhận): so với mọi tài khoản và phụ huynh trên hồ sơ trẻ khác, bỏ dòng của
     * chính trẻ này và bản ghi của chính tài khoản. Đổi SĐT của người đã có tài khoản: số mới không được là tên đăng nhập
     * của tài khoản khác (kể cả nhân sự).
     */
    private List<FieldErrorResponse> identityConflicts(
            Long childId, List<GuardianEdit> guardians, Map<Long, UserAccount> linked) {
        Set<String> phones = guardians.stream().map(e -> e.draft().phone()).collect(Collectors.toSet());
        Set<String> citizenIds =
                guardians.stream().map(e -> e.draft().citizenId()).collect(Collectors.toSet());
        Map<String, UserAccount> byCitizenId = userAccountRepository.findAllByCitizenIdIn(citizenIds).stream()
                .collect(Collectors.toMap(UserAccount::getCitizenId, Function.identity(), (a, b) -> a));
        Map<String, UserAccount> byUsername = userAccountRepository.findAllByUsernameIn(phones).stream()
                .collect(Collectors.toMap(UserAccount::getUsername, Function.identity(), (a, b) -> a));
        List<Identity> records = new ArrayList<>();
        Map<Long, UserAccount> all = new HashMap<>();
        byCitizenId.values().forEach(a -> all.put(a.getId(), a));
        byUsername.values().stream()
                .filter(a -> a.getPrimaryRole() == CareNestRole.PARENT)
                .forEach(a -> all.put(a.getId(), a));
        all.values().forEach(a -> records.add(new Identity(a.getId(), a.getUsername(), a.getCitizenId())));
        for (GuardianRecord r : childIntakeService.findGuardianRecords(phones, citizenIds)) {
            if (!childId.equals(r.childId())) {
                records.add(new Identity(r.userId(), r.phone(), r.citizenId()));
            }
        }

        List<FieldErrorResponse> errors = new ArrayList<>();
        for (int i = 0; i < guardians.size(); i++) {
            GuardianEdit edit = guardians.get(i);
            GuardianDraft g = edit.draft();
            String prefix = "guardians." + i + ".";
            UserAccount same = edit.accountId() != null
                    ? linked.get(edit.accountId())
                    : Optional.ofNullable(byCitizenId.get(g.citizenId()))
                            .filter(a -> a.getPrimaryRole() == CareNestRole.PARENT)
                            .orElse(null);
            List<Identity> others = records.stream()
                    .filter(r -> same == null
                            || !(same.getId().equals(r.accountId())
                                    || same.getUsername().equals(r.phone())
                                    || (same.getCitizenId() != null
                                            && same.getCitizenId().equals(r.citizenId()))))
                    .toList();
            if (others.stream()
                    .anyMatch(r ->
                            g.citizenId().equals(r.citizenId()) && !g.phone().equals(r.phone()))) {
                errors.add(new FieldErrorResponse(prefix + "idNumber", ChildEnrollmentServiceImpl.CITIZEN_ID_TAKEN));
            } else if (others.stream()
                    .anyMatch(r -> g.phone().equals(r.phone())
                            && r.citizenId() != null
                            && !g.citizenId().equals(r.citizenId()))) {
                errors.add(new FieldErrorResponse(prefix + "phone", ChildEnrollmentServiceImpl.PHONE_HAS_OTHER_ID));
            } else if (edit.accountId() != null && same != null && !g.phone().equals(edit.currentPhone())) {
                UserAccount owner = byUsername.get(g.phone());
                if (owner != null && !owner.getId().equals(same.getId())) {
                    errors.add(new FieldErrorResponse(prefix + "phone", USERNAME_TAKEN));
                }
            }
        }
        return errors;
    }

    private static GuardianDraft withPhone(GuardianDraft g, String phone) {
        return new GuardianDraft(
                g.position(), g.fullName(), g.relationship(), phone, g.citizenId(), g.email(), g.accountRecipient());
    }

    private record Identity(Long accountId, String phone, String citizenId) {}

    private record Saved(List<UserAccount> updated, List<GuardianPhoneChangeRequest> opened) {}

    /** So phụ huynh đã sửa với tài khoản đang liên kết (email so không phân biệt hoa thường). */
    private record AccountEdit(
            int index,
            GuardianEdit edit,
            UserAccount account,
            boolean nameChanged,
            boolean emailChanged,
            boolean citizenChanged,
            boolean phoneChanged) {

        static AccountEdit of(int index, GuardianEdit edit, UserAccount account) {
            GuardianDraft g = edit.draft();
            String email = account.getEmail();
            return new AccountEdit(
                    index,
                    edit,
                    account,
                    !Objects.equals(account.getFullName(), g.fullName()),
                    !(email == null ? g.email() == null : email.equalsIgnoreCase(g.email())),
                    account.getCitizenId() != null && !account.getCitizenId().equals(g.citizenId()),
                    // So với SĐT đang lưu trên hồ sơ: tài khoản demo cũ có tên đăng nhập không phải SĐT (module card)
                    !Objects.equals(edit.currentPhone(), g.phone()));
        }

        boolean infoChanged() {
            return nameChanged || emailChanged || citizenChanged;
        }

        String firstInfoField() {
            if (nameChanged) return "fullName";
            if (emailChanged) return "email";
            return "idNumber";
        }

        @Override
        public String toString() {
            return "AccountEdit[index=" + index + ", userId=" + account.getId() + "]";
        }
    }
}
