package com.carenest.service.account.impl;

import com.carenest.dto.child.ChildEditDraft;
import com.carenest.dto.child.ChildEditDraft.GuardianEdit;
import com.carenest.dto.child.ChildUpdateResponse;
import com.carenest.dto.child.GuardianAccountChangeResponse;
import com.carenest.dto.child.GuardianDraft;
import com.carenest.dto.child.GuardianRecord;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.dto.common.FieldErrorResponse;
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
 * Sửa hồ sơ trẻ kèm tài khoản phụ huynh (mục 4.2, 5.2, 5.4 plan tiếp nhận; G94-180/AUTH-21 sửa 2026-11-10, 2026-11-11).
 * Không đổi SĐT phụ huynh đã lưu — đổi qua yêu cầu ({@code GuardianPhoneChangeService.request}). Thứ tự: kiểm hết
 * (không ghi) → một giao dịch ghi hồ sơ + tài khoản → sau commit đồng bộ họ tên/email sang Keycloak. Không log
 * SĐT/CCCD/họ tên — chỉ id, số lượng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildProfileEditServiceImpl implements ChildProfileEditService {
    private static final String LOG_PREFIX = "[ChildProfileEditServiceImpl]";
    static final String PHONE_VIA_REQUEST =
            "Không đổi số điện thoại ở Sửa hồ sơ. Dùng nút Yêu cầu đổi số điện thoại để Hiệu trưởng hoặc Phó hiệu trưởng"
                    + " duyệt.";
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
            for (int i = 0; i < draft.guardians().size(); i++) {
                GuardianEdit edit = draft.guardians().get(i);
                String prefix = "guardians." + i + ".";
                // AUTH-21 (sửa 2026-11-11): cùng người (CCCD không đổi) thì đổi SĐT chỉ qua yêu cầu, có hay chưa có
                // tài khoản; đổi cả CCCD của người chưa có tài khoản = thay người khác vào vị trí đó
                boolean phoneChanged = edit.guardianId() != null
                        && !Objects.equals(edit.currentPhone(), edit.draft().phone());
                if (phoneChanged
                        && Objects.equals(edit.currentCitizenId(), edit.draft().citizenId())) {
                    errors.add(new FieldErrorResponse(prefix + "phone", PHONE_VIA_REQUEST));
                    continue;
                }
                UserAccount account = edit.accountId() == null ? null : accounts.get(edit.accountId());
                if (account == null) {
                    continue;
                }
                AccountEdit change = AccountEdit.of(i, edit, account);
                if (phoneChanged && change.citizenChanged()) {
                    errors.add(new FieldErrorResponse(prefix + "idNumber", BOTH_CHANGED));
                }
                if (change.infoChanged() && !manageAccounts) {
                    errors.add(new FieldErrorResponse(prefix + change.firstInfoField(), NEED_ACCOUNT_PERMISSION));
                }
                accountEdits.add(change);
            }
            if (!errors.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
            }
            List<FieldErrorResponse> conflicts = identityConflicts(childId, draft.guardians(), accounts);
            if (!conflicts.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.GUARDIAN_IDENTITY_CONFLICT, conflicts);
            }

            List<UserAccount> updated = transactionTemplate.execute(status -> {
                childRecordService.applyEdit(actor, draft);
                List<UserAccount> changed = new ArrayList<>();
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
                        if (change.infoChanged()) changed.add(account);
                    }
                }
                return changed;
            });

            for (UserAccount account : updated) {
                accountLifecycle.syncProfileQuietly(logPrefix, account);
            }
            List<GuardianAccountChangeResponse> accountChanges = updated.stream()
                    .map(a ->
                            new GuardianAccountChangeResponse(a.getId(), a.getFullName(), a.getUsername(), false, null))
                    .toList();
            // phoneChangeRequests luôn rỗng từ 2026-11-11 (gửi yêu cầu riêng); giữ trường cho FE đang đọc
            ChildUpdateResponse response =
                    new ChildUpdateResponse(childIntakeService.getChildView(childId), accountChanges, List.of());
            log.info("{}|accountsUpdated={}|END", logPrefix, accountChanges.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /**
     * Một người một tài khoản (mục 5.2, như tiếp nhận): so với mọi tài khoản và phụ huynh trên hồ sơ trẻ khác, bỏ dòng của
     * chính trẻ này và bản ghi của chính tài khoản. Đổi SĐT của người đã lưu bị chặn trước đó (đổi qua yêu cầu).
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
            }
        }
        return errors;
    }

    private record Identity(Long accountId, String phone, String citizenId) {}

    /** So phụ huynh đã sửa với tài khoản đang liên kết (email so không phân biệt hoa thường). */
    private record AccountEdit(
            int index,
            GuardianEdit edit,
            UserAccount account,
            boolean nameChanged,
            boolean emailChanged,
            boolean citizenChanged) {

        static AccountEdit of(int index, GuardianEdit edit, UserAccount account) {
            GuardianDraft g = edit.draft();
            String email = account.getEmail();
            return new AccountEdit(
                    index,
                    edit,
                    account,
                    !Objects.equals(account.getFullName(), g.fullName()),
                    !(email == null ? g.email() == null : email.equalsIgnoreCase(g.email())),
                    account.getCitizenId() != null && !account.getCitizenId().equals(g.citizenId()));
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
