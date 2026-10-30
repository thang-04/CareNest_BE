package com.carenest.service.account.impl;

import com.carenest.config.AuthProperties;
import com.carenest.dto.account.AccountCandidateResponse;
import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.CreateAccountRequest;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.dto.account.StaffAssignmentScopeRequest;
import com.carenest.dto.organization.NewStaffAssignment;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.AuthGatewayClient;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.mapper.account.AccountMapper;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.AccountProvisioningService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.PersonNames;
import com.carenest.utils.PhoneNumbers;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import com.carenest.utils.enums.UserStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Cấp/khóa tài khoản: ghi cả Keycloak lẫn PostgreSQL nhưng không có transaction chung, nên dùng bù trừ
 * (tạo Keycloak lỗi bước sau ⇒ xóa user Keycloak) thay vì distributed transaction (ADR-0011).
 * Không đặt @Transactional ở mức method để không giữ kết nối DB trong lúc gọi HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountProvisioningServiceImpl implements AccountProvisioningService {
    private static final String LOG_PREFIX = "[AccountProvisioningServiceImpl]";

    // ADMIN chỉ tạo trực tiếp trên Keycloak (bootstrap); PARENT tạo qua luồng gắn trẻ (AUTH-18), không qua API này
    private static final Set<CareNestRole> PROVISIONABLE_ROLES = EnumSet.of(
            CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER, CareNestRole.KITCHEN_STAFF);

    private final UserAccountRepository userAccountRepository;
    private final IdentityProviderClient identityProviderClient;
    private final AccountMapper accountMapper;
    private final AuthProperties authProperties;
    private final AuthGatewayClient authGatewayClient;
    private final StaffAccessPolicy staffAccessPolicy;
    private final StaffAssignmentService staffAssignmentService;
    private final ManagedAccountLoader managedAccountLoader;
    private final AccountLifecycleOperations accountLifecycle;
    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentService enrollmentService;
    private final Clock clock;

    @Override
    public AccountCandidateResponse findAccountCandidate(CurrentUser actor, String phone) {
        String logPrefix = LOG_PREFIX + "|findAccountCandidate|actorUserId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            String username =
                    PhoneNumbers.normalize(phone).orElseThrow(() -> GlobalException.of(ApiCode.PHONE_NUMBER_INVALID));
            AccountCandidateResponse candidate = userAccountRepository
                    .findByUsername(username)
                    .map(account -> new AccountCandidateResponse(
                            true,
                            account.getId(),
                            PersonNames.mask(account.getFullName()),
                            account.getPrimaryRole(),
                            account.getStatus()))
                    .orElseGet(AccountCandidateResponse::none);
            log.info("{}|exists={}|END", logPrefix, candidate.exists());
            return candidate;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CreateAccountResponse createAccount(CurrentUser actor, CreateAccountRequest request) {
        String logPrefix = LOG_PREFIX + "|createAccount|actorUserId=" + actor.userId() + "|role=" + request.role();
        log.info("{}|START", logPrefix);
        try {
            if (!PROVISIONABLE_ROLES.contains(request.role())) {
                throw GlobalException.of(ApiCode.ROLE_NOT_CREATABLE);
            }
            // AUTH-15: xác định phạm vi người được tạo và quyền người tạo trước khi đụng Keycloak
            AssignmentScope scope = toAssignmentScope(request);
            Long campusId = scope == null || scope.staffRole() == StaffRole.PRINCIPAL
                    ? null
                    : staffAssignmentService.resolveCampusId(scope.staffRole(), scope.campusId(), scope.classroomId());
            staffAccessPolicy.checkCanCreate(actor, request.role(), campusId);

            // Đăng nhập bằng SĐT (user chốt 2026-10-30); bếp dùng SĐT bếp của cơ sở
            String username = PhoneNumbers.normalize(request.phone())
                    .orElseThrow(() -> GlobalException.of(ApiCode.PHONE_NUMBER_INVALID));
            Optional<UserAccount> existing = userAccountRepository.findByUsername(username);
            if (existing.isPresent()) {
                CreateAccountResponse added = addStaffRoleToParent(actor, existing.get(), request, scope);
                log.info("{}|userId={}|existingAccount=true|END", logPrefix, added.userId());
                return added;
            }
            String email = StringUtils.hasText(request.email())
                    ? request.email().trim().toLowerCase(Locale.ROOT)
                    : null;
            boolean sendSetupEmail = email != null && identityProviderClient.canSendSetupEmail();
            if (request.temporaryPassword() == null && !sendSetupEmail) {
                throw GlobalException.of(ApiCode.TEMP_PASSWORD_REQUIRED);
            }

            // Tài khoản và phân công cùng 1 transaction: không có tài khoản PHT tạo ra mà nằm ngoài campus
            SavedAccount saved = accountLifecycle.createIdentity(
                    logPrefix,
                    new NewIdentityUser(username, email, request.fullName().trim(), request.temporaryPassword()),
                    request.role(),
                    keycloakUserId -> {
                        UserAccount created = userAccountRepository.saveAndFlush(UserAccount.create(
                                keycloakUserId,
                                username,
                                email,
                                request.fullName().trim(),
                                request.role()));
                        StaffAssignmentResponse assignment = scope == null
                                ? null
                                : staffAssignmentService.createAssignment(scope.forUser(created.getId()));
                        return new SavedAccount(created, assignment);
                    });

            UserAccount account = saved.account();
            String keycloakUserId = account.getKeycloakUserId();
            boolean setupEmailSent = sendSetupEmail && sendSetupEmailQuietly(logPrefix, keycloakUserId);
            log.info(
                    "{}|userId={}|keycloakUserId={}|setupEmailSent={}|END",
                    logPrefix,
                    account.getId(),
                    keycloakUserId,
                    setupEmailSent);
            return accountMapper.toCreateAccountResponse(account, request.role(), setupEmailSent, saved.assignment());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AccountResponse updateAccountStatus(CurrentUser actor, Long userId, UserStatus status) {
        String logPrefix = LOG_PREFIX + "|updateAccountStatus|actorUserId=" + actor.userId() + "|userId=" + userId
                + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            // ADMIN tự khóa mình ⇒ có thể không còn ai mở lại qua API (team chốt 2026-10-06)
            if (userId.equals(actor.userId())) {
                throw GlobalException.of(ApiCode.CANNOT_CHANGE_OWN_STATUS);
            }
            // AUTH-17: HT khóa/mở PHT/GV/bếp; PHT (nếu được bật quyền) chỉ GV/bếp campus mình; Admin mọi tài khoản
            UserAccount account = managedAccountLoader.loadManageable(actor, userId);
            accountLifecycle.applyStatus(logPrefix, account, status);
            log.info("{}|END", logPrefix);
            return accountMapper.toAccountResponse(account);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AccountResponse resetTemporaryPassword(CurrentUser actor, Long userId, String temporaryPassword) {
        String logPrefix = LOG_PREFIX + "|resetTemporaryPassword|actorUserId=" + actor.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-12: tự đổi mật khẩu dùng luồng đổi mật khẩu của chính mình
            if (userId.equals(actor.userId())) {
                throw GlobalException.of(ApiCode.USE_CHANGE_PASSWORD_FOR_SELF);
            }
            UserAccount account = managedAccountLoader.loadManageable(actor, userId);
            checkCanManageLinkedParent(actor, account);
            accountLifecycle.resetTemporaryPassword(logPrefix, account, temporaryPassword);
            log.info("{}|END", logPrefix);
            return accountMapper.toAccountResponse(account);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AccountResponse revokeStaffRole(CurrentUser actor, Long userId) {
        String logPrefix = LOG_PREFIX + "|revokeStaffRole|actorUserId=" + actor.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            if (userId.equals(actor.userId())) {
                throw GlobalException.of(ApiCode.CANNOT_MANAGE_OWN_ACCOUNT);
            }
            // AUTH-17: cùng phạm vi với khóa/mở tài khoản nhân sự
            UserAccount account = managedAccountLoader.loadManageable(actor, userId);
            CareNestRole staffRole = account.getPrimaryRole();
            if (!PROVISIONABLE_ROLES.contains(staffRole)) {
                throw GlobalException.of(ApiCode.STAFF_ROLE_NOT_REVOCABLE);
            }
            LocalDate today = LocalDate.now(clock);
            // CHD-04: không còn con đang học thì tài khoản không còn việc gì ⇒ khóa thay vì thu hồi
            List<Long> childIds = guardianChildLinkService.findChildIdsOfGuardian(userId);
            Set<Long> left = enrollmentService.findChildIdsLeftSchool(childIds, today);
            if (childIds.stream().allMatch(left::contains)) {
                throw GlobalException.of(ApiCode.STAFF_ROLE_REVOKE_REQUIRES_CHILD);
            }
            accountLifecycle.revokeRealmRole(logPrefix, account, staffRole, () -> {
                endOpenAssignments(userId, today);
                userAccountRepository.updatePrimaryRole(userId, CareNestRole.PARENT.name());
            });
            account.becomeParentOnly();
            log.info("{}|END", logPrefix);
            return accountMapper.toAccountResponse(account);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Phân công chưa bắt đầu kết thúc ở ngày bắt đầu (không đặt được trước đó); không còn role nên không mở dữ liệu
    private void endOpenAssignments(Long userId, LocalDate today) {
        staffAssignmentService.findAssignments(userId).stream()
                .filter(assignment ->
                        assignment.validTo() == null || !assignment.validTo().isBefore(today))
                .forEach(assignment -> staffAssignmentService.endAssignment(
                        assignment.id(), assignment.validFrom().isAfter(today) ? assignment.validFrom() : today));
    }

    // Nhân sự kiêm phụ huynh: mật khẩu tạm mở luôn quyền xem con ⇒ người không quản toàn trường cần quản được phụ
    // huynh này (AUTH-19), không chỉ quản được nhân sự
    private void checkCanManageLinkedParent(CurrentUser actor, UserAccount account) {
        if (actor.hasRole(CareNestRole.ADMIN)) return;
        if (!guardianChildLinkService.findChildIdsOfGuardian(account.getId()).isEmpty()) {
            staffAccessPolicy.checkCanManageParent(actor, account.getId());
        }
    }

    // Phụ huynh được nhận làm nhân sự: một tài khoản, giữ SĐT/mật khẩu; chỉ khi người tạo đã xác nhận đúng người
    private CreateAccountResponse addStaffRoleToParent(
            CurrentUser actor, UserAccount account, CreateAccountRequest request, AssignmentScope scope) {
        if (account.getPrimaryRole() != CareNestRole.PARENT) {
            throw GlobalException.of(ApiCode.USERNAME_OR_EMAIL_EXISTS);
        }
        if (!account.getId().equals(request.confirmUserId())) {
            throw GlobalException.of(ApiCode.ACCOUNT_CONFIRMATION_REQUIRED);
        }
        if (account.getId().equals(actor.userId())) {
            throw GlobalException.of(ApiCode.CANNOT_MANAGE_OWN_ACCOUNT);
        }
        // AUTH-19: chỉ người quản lý được phụ huynh này (HT, hoặc PHT có con của họ ở campus mình) mới gộp được;
        // gộp xong người tạo quản lý tài khoản ⇒ không để PHT chiếm tài khoản phụ huynh campus khác
        staffAccessPolicy.checkCanManageParent(actor, account.getId());
        StaffAssignmentResponse assignment =
                accountLifecycle.addRealmRole(LOG_PREFIX + "|addStaffRoleToParent", account, request.role(), () -> {
                    userAccountRepository.updatePrimaryRole(
                            account.getId(), request.role().name());
                    return scope == null
                            ? null
                            : staffAssignmentService.createAssignment(scope.forUser(account.getId()));
                });
        account.becomeStaff(request.role());
        return accountMapper.toCreateAccountResponse(account, request.role(), false, assignment);
    }

    // HT tự có phân công toàn trường; phụ huynh không có phân công; nhân sự khác theo request (có thể trống)
    private AssignmentScope toAssignmentScope(CreateAccountRequest request) {
        StaffAssignmentScopeRequest requested = request.assignment();
        LocalDate today = LocalDate.now(clock);
        return switch (request.role()) {
            case PRINCIPAL -> {
                if (requested != null) {
                    throw GlobalException.of(ApiCode.PRINCIPAL_ASSIGNMENT_SCHOOL_WIDE);
                }
                yield new AssignmentScope(StaffRole.PRINCIPAL, null, null, today);
            }
            case VICE_PRINCIPAL, TEACHER, KITCHEN_STAFF ->
                requested == null
                        ? null
                        : new AssignmentScope(
                                StaffRole.valueOf(request.role().name()),
                                requested.campusId(),
                                requested.classroomId(),
                                requested.validFrom() == null ? today : requested.validFrom());
            default -> {
                if (requested != null) {
                    throw GlobalException.of(ApiCode.ROLE_HAS_NO_ASSIGNMENT);
                }
                yield null;
            }
        };
    }

    private record AssignmentScope(StaffRole staffRole, Long campusId, Long classroomId, LocalDate validFrom) {
        NewStaffAssignment forUser(Long userId) {
            return new NewStaffAssignment(userId, staffRole, campusId, classroomId, validFrom, null);
        }
    }

    private record SavedAccount(UserAccount account, StaffAssignmentResponse assignment) {}

    // Email lỗi không hủy tài khoản. Nếu không có mật khẩu tạm, người dùng dùng "Quên mật khẩu" ở trang đăng nhập
    // hoặc Admin đặt lại trên Keycloak Console (chưa có API gửi lại)
    private boolean sendSetupEmailQuietly(String logPrefix, String keycloakUserId) {
        try {
            if (authProperties.enabled()) authGatewayClient.sendReactSetupEmail(keycloakUserId);
            else identityProviderClient.sendSetupEmail(keycloakUserId);
            return true;
        } catch (IdentityProviderException | AuthGatewayClient.ProviderUnavailable ex) {
            log.warn("{}|keycloakUserId={}|setupEmailFailed", logPrefix, keycloakUserId);
            return false;
        }
    }
}
