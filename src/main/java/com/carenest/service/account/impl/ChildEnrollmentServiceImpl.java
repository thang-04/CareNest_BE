package com.carenest.service.account.impl;

import com.carenest.dto.child.ChildEnrollmentResponse;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.GuardianAccountResultResponse;
import com.carenest.dto.child.GuardianCheckRequest;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.dto.child.GuardianDraft;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.LinkedChildResponse;
import com.carenest.dto.child.ParentAccountLookupResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.ChildEnrollmentService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.CitizenIds;
import com.carenest.utils.PhoneNumbers;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.GuardianAccountResult;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SmsStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tài khoản phụ huynh khi tiếp nhận trẻ. Thứ tự lưu form (AC-2): kiểm hết (không ghi) → tạo user Keycloak cho phụ huynh
 * mới → một giao dịch DB ghi tài khoản + hồ sơ trẻ + liên kết → lỗi thì xóa mọi user Keycloak vừa tạo → sau commit gửi
 * SMS. Không log SĐT/CCCD/họ tên/mật khẩu — chỉ id, số lượng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildEnrollmentServiceImpl implements ChildEnrollmentService {
    private static final String LOG_PREFIX = "[ChildEnrollmentServiceImpl]";
    private static final int MAX_GUARDIANS = 3;
    static final String CITIZEN_ID_TAKEN =
            "Số CCCD này đã ghi cho phụ huynh khác (số điện thoại khác). Kiểm tra lại số CCCD.";
    static final String PHONE_HAS_OTHER_ID =
            "Số điện thoại này đã thuộc phụ huynh có số CCCD khác. Kiểm tra lại số điện thoại hoặc số CCCD.";

    private final ChildIntakeService childIntakeService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final UserAccountRepository userAccountRepository;
    private final StaffAccessPolicy staffAccessPolicy;
    private final AccountLifecycleOperations accountLifecycle;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final ParentSmsSender smsSender;

    @Override
    @Transactional(readOnly = true)
    public ParentAccountLookupResponse findParentAccount(CurrentUser actor, String idNumber) {
        String logPrefix = LOG_PREFIX + "|findParentAccount|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            Optional<UserAccount> account = CitizenIds.normalize(idNumber)
                    .flatMap(userAccountRepository::findByCitizenId)
                    .filter(a -> a.getPrimaryRole() == CareNestRole.PARENT);
            if (account.isEmpty()) {
                log.info("{}|found=false|END", logPrefix);
                return null;
            }
            UserAccount parent = account.get();
            List<GuardianChildSummary> linked =
                    childIntakeService
                            .findChildSummaries(guardianChildLinkService.findChildIdsOfGuardian(parent.getId()))
                            .stream()
                            .filter(c -> c.status() != ChildStatus.LEFT)
                            .toList();
            boolean wholeSchool = staffAccessPolicy.managesWholeSchool(actor);
            Set<Long> managed = wholeSchool ? Set.of() : staffAccessPolicy.managedCampusIds(actor);
            List<LinkedChildResponse> visible = linked.stream()
                    .filter(c -> wholeSchool || (c.campusId() != null && managed.contains(c.campusId())))
                    .map(c -> new LinkedChildResponse(c.id(), c.code(), c.fullName(), c.className()))
                    .toList();
            ParentAccountLookupResponse response = new ParentAccountLookupResponse(
                    parent.getId(),
                    parent.getFullName(),
                    parent.getUsername(),
                    parent.getCitizenId(),
                    parent.getEmail(),
                    parent.getStatus(),
                    visible,
                    linked.size() - visible.size(),
                    childIntakeService
                            .findRelationshipOfAccount(parent.getId(), parent.getUsername())
                            .orElse(null));
            log.info("{}|found=true|parentUserId={}|END", logPrefix, parent.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkGuardians(CurrentUser actor, GuardianCheckRequest request) {
        String logPrefix = LOG_PREFIX + "|checkGuardians|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            List<GuardianInput> inputs =
                    request == null || request.guardians() == null ? List.of() : request.guardians();
            if (inputs.size() > MAX_GUARDIANS) {
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA,
                        List.of(new FieldErrorResponse("guardians", "Mỗi trẻ tối đa " + MAX_GUARDIANS + " phụ huynh")));
            }
            // Chỉ kiểm trùng người: ô sai định dạng bỏ qua (form báo lỗi định dạng ở FE và khi lưu)
            List<GuardianDraft> guardians = IntStream.range(0, inputs.size())
                    .mapToObj(i -> {
                        GuardianInput g =
                                inputs.get(i) == null ? new GuardianInput(null, null, null, null, null) : inputs.get(i);
                        return new GuardianDraft(
                                i,
                                g.fullName(),
                                null,
                                PhoneNumbers.normalize(g.phone()).orElse(null),
                                CitizenIds.normalize(g.idNumber()).orElse(null),
                                null);
                    })
                    .toList();
            ParentAccounts accounts = loadAccounts(guardians);
            List<FieldErrorResponse> conflicts = identityConflicts(
                    withAccountInfo(guardians, accounts),
                    accounts,
                    actor.hasPermission(Permission.PARENT_ACCOUNT_MANAGE));
            if (!conflicts.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.GUARDIAN_IDENTITY_CONFLICT, conflicts);
            }
            log.info("{}|guardians={}|END", logPrefix, guardians.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ChildEnrollmentResponse enroll(CurrentUser actor, EnrollChildRequest request) {
        String logPrefix = LOG_PREFIX + "|enroll|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            EnrollmentDraft draft = childIntakeService.prepareEnrollment(actor, request);
            // P-18 A: không có quyền quản lý tài khoản phụ huynh (ở điểm trường nhận trẻ — prepare đã kiểm phạm vi)
            // ⇒ vẫn lưu; phụ huynh chưa có tài khoản chờ kích hoạt, trùng tài khoản có sẵn thì vẫn liên kết
            boolean manageAccounts = actor.hasPermission(Permission.PARENT_ACCOUNT_MANAGE);
            ParentAccounts accounts = loadAccounts(draft.guardians());
            // Tài khoản có sẵn: hồ sơ ghi họ tên, SĐT, email của tài khoản, không sửa tài khoản (C-2; sửa tài khoản
            // theo hồ sơ, đổi SĐT đổi tên đăng nhập làm ở Phase 4 — AUTH-21)
            List<GuardianDraft> guardians = withAccountInfo(draft.guardians(), accounts);
            List<FieldErrorResponse> conflicts = identityConflicts(guardians, accounts, manageAccounts);
            if (!conflicts.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.GUARDIAN_IDENTITY_CONFLICT, conflicts);
            }

            List<GuardianDraft> newParents = manageAccounts
                    ? guardians.stream()
                            .filter(g -> accounts.parentOf(g).isEmpty())
                            .toList()
                    : List.of();
            List<String> passwords = newParents.stream()
                    .map(g -> passwordGenerator.generate()) // carenest:allow-secret
                    .toList();
            List<NewIdentityUser> identities = IntStream.range(0, newParents.size())
                    .mapToObj(i -> new NewIdentityUser(
                            newParents.get(i).phone(), null, newParents.get(i).fullName(), passwords.get(i)))
                    .toList();
            EnrollmentDraft resolved = draft.withGuardians(guardians);

            Enrolled enrolled =
                    accountLifecycle.createIdentities(logPrefix, identities, CareNestRole.PARENT, keycloakUserIds -> {
                        Map<Integer, Long> accountIds = new HashMap<>();
                        for (GuardianDraft g : guardians) {
                            accounts.parentOf(g).ifPresent(existing -> {
                                // AUTH-18: tài khoản cũ chưa có CCCD được bổ sung (đọc lại trong giao dịch)
                                userAccountRepository
                                        .findById(existing.getId())
                                        .ifPresent(a -> a.assignCitizenIdIfMissing(g.citizenId()));
                                accountIds.put(g.position(), existing.getId());
                            });
                        }
                        List<UserAccount> created = new ArrayList<>();
                        for (int i = 0; i < newParents.size(); i++) {
                            GuardianDraft g = newParents.get(i);
                            UserAccount parent = userAccountRepository.saveAndFlush(UserAccount.createParent(
                                    keycloakUserIds.get(i), g.phone(), g.email(), g.fullName(), g.citizenId()));
                            accountIds.put(g.position(), parent.getId());
                            created.add(parent);
                        }
                        Long childId = childIntakeService.createEnrolledChild(actor, resolved, accountIds);
                        return new Enrolled(childId, created, accountIds);
                    });

            // Sau commit: SMS mật khẩu tạm tới SĐT (L-15); mật khẩu không trả về giao diện
            Map<Integer, SmsStatus> smsByPosition = new HashMap<>();
            for (int i = 0; i < newParents.size(); i++) {
                smsByPosition.put(
                        newParents.get(i).position(),
                        smsSender.sendActivation(enrolled.created().get(i), passwords.get(i)));
            }
            ChildViewResponse view = childIntakeService.getChildView(enrolled.childId());
            List<GuardianAccountResultResponse> results = guardians.stream()
                    .map(g -> new GuardianAccountResultResponse(
                            g.fullName(),
                            g.phone(),
                            smsByPosition.containsKey(g.position())
                                    ? GuardianAccountResult.CREATED
                                    : enrolled.accountIds().containsKey(g.position())
                                            ? GuardianAccountResult.LINKED
                                            : GuardianAccountResult.NOT_ACTIVATED,
                            false,
                            smsByPosition.get(g.position())))
                    .toList();
            log.info(
                    "{}|childId={}|created={}|linked={}|END",
                    logPrefix,
                    enrolled.childId(),
                    newParents.size(),
                    enrolled.accountIds().size() - newParents.size());
            return new ChildEnrollmentResponse(view, results);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Tài khoản có SĐT (tên đăng nhập) hoặc CCCD của các phụ huynh trên form. */
    private ParentAccounts loadAccounts(List<GuardianDraft> guardians) {
        Set<String> phones = guardians.stream()
                .map(GuardianDraft::phone)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> citizenIds = guardians.stream()
                .map(GuardianDraft::citizenId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, UserAccount> byCitizenId = citizenIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findAllByCitizenIdIn(citizenIds).stream()
                        .filter(a -> a.getPrimaryRole() == CareNestRole.PARENT)
                        .collect(Collectors.toMap(UserAccount::getCitizenId, Function.identity()));
        Map<String, UserAccount> byUsername = phones.isEmpty()
                ? Map.of()
                : userAccountRepository.findAllByUsernameIn(phones).stream()
                        .collect(Collectors.toMap(UserAccount::getUsername, Function.identity()));
        return new ParentAccounts(byCitizenId, byUsername, phones, citizenIds);
    }

    private static List<GuardianDraft> withAccountInfo(List<GuardianDraft> guardians, ParentAccounts accounts) {
        return guardians.stream()
                .map(g -> accounts.parentOf(g)
                        .map(a -> g.withAccountInfo(a.getFullName(), a.getUsername(), a.getEmail()))
                        .orElse(g))
                .toList();
    }

    /**
     * Một người một tài khoản (AUTH-18, BE giả {@code identityConflict}): so với mọi tài khoản phụ huynh và phụ huynh
     * trên hồ sơ trẻ khác, bỏ bản ghi của chính tài khoản đã khớp. CCCD đã ghi cho SĐT khác ⇒ ô CCCD; SĐT đã thuộc
     * CCCD khác ⇒ ô SĐT. SĐT là tên đăng nhập nhân sự (tài khoản cũ) mà sắp tạo tài khoản ⇒ ô SĐT.
     */
    private List<FieldErrorResponse> identityConflicts(
            List<GuardianDraft> guardians, ParentAccounts accounts, boolean creatingAccounts) {
        List<Identity> records = new ArrayList<>();
        accounts.parents().forEach(a -> records.add(new Identity(a.getId(), a.getUsername(), a.getCitizenId())));
        childIntakeService.findGuardianRecords(accounts.phones(), accounts.citizenIds()).stream()
                .map(r -> new Identity(null, r.phone(), r.citizenId()))
                .forEach(records::add);
        List<FieldErrorResponse> errors = new ArrayList<>();
        for (GuardianDraft g : guardians) {
            if (g.phone() == null || g.citizenId() == null) {
                continue;
            }
            String prefix = "guardians." + g.position() + ".";
            UserAccount same = accounts.parentOf(g).orElse(null);
            Predicate<Identity> own = r -> same != null
                    && (same.getId().equals(r.accountId())
                            || same.getUsername().equals(r.phone())
                            || (same.getCitizenId() != null
                                    && same.getCitizenId().equals(r.citizenId())));
            List<Identity> others = records.stream().filter(own.negate()).toList();
            if (others.stream()
                    .anyMatch(r ->
                            g.citizenId().equals(r.citizenId()) && !g.phone().equals(r.phone()))) {
                errors.add(new FieldErrorResponse(prefix + "idNumber", CITIZEN_ID_TAKEN));
            } else if (others.stream()
                    .anyMatch(r -> g.phone().equals(r.phone())
                            && r.citizenId() != null
                            && !g.citizenId().equals(r.citizenId()))) {
                errors.add(new FieldErrorResponse(prefix + "phone", PHONE_HAS_OTHER_ID));
            } else if (creatingAccounts && same == null && accounts.isStaffUsername(g.phone())) {
                errors.add(new FieldErrorResponse(prefix + "phone", ApiCode.PHONE_OF_STAFF_ACCOUNT.getDefaultDesc()));
            }
        }
        return errors;
    }

    /** Một bản ghi danh tính phụ huynh: tài khoản (accountId) hoặc phụ huynh trên hồ sơ trẻ (accountId null). */
    private record Identity(Long accountId, String phone, String citizenId) {}

    private record Enrolled(Long childId, List<UserAccount> created, Map<Integer, Long> accountIds) {}

    /**
     * Tài khoản khớp phụ huynh (AUTH-18): theo CCCD; tài khoản cũ chưa có CCCD khớp theo SĐT. Không tin id tài khoản
     * client gửi.
     */
    private record ParentAccounts(
            Map<String, UserAccount> byCitizenId,
            Map<String, UserAccount> byUsername,
            Collection<String> phones,
            Collection<String> citizenIds) {

        Optional<UserAccount> parentOf(GuardianDraft g) {
            UserAccount byId = g.citizenId() == null ? null : byCitizenId.get(g.citizenId());
            if (byId != null) {
                return Optional.of(byId);
            }
            UserAccount byPhone = g.phone() == null ? null : byUsername.get(g.phone());
            return byPhone != null && byPhone.getPrimaryRole() == CareNestRole.PARENT && byPhone.getCitizenId() == null
                    ? Optional.of(byPhone)
                    : Optional.empty();
        }

        boolean isStaffUsername(String phone) {
            UserAccount account = byUsername.get(phone);
            return account != null && account.getPrimaryRole() != CareNestRole.PARENT;
        }

        List<UserAccount> parents() {
            Map<Long, UserAccount> all = new HashMap<>();
            byCitizenId.values().forEach(a -> all.put(a.getId(), a));
            byUsername.values().stream()
                    .filter(a -> a.getPrimaryRole() == CareNestRole.PARENT)
                    .forEach(a -> all.put(a.getId(), a));
            return List.copyOf(all.values());
        }
    }
}
