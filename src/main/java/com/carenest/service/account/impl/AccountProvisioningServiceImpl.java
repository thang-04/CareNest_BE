package com.carenest.service.account.impl;

import com.carenest.config.AuthProperties;
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
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import com.carenest.utils.enums.UserStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Locale;
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
    private final Clock clock;

    @Override
    public CreateAccountResponse createAccount(CurrentUser actor, CreateAccountRequest request) {
        String logPrefix = LOG_PREFIX + "|createAccount|actorUserId=" + actor.userId() + "|role=" + request.role();
        log.info("{}|START", logPrefix);
        try {
            if (!PROVISIONABLE_ROLES.contains(request.role())) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "This role cannot be created via API");
            }
            // AUTH-15: xác định phạm vi người được tạo và quyền người tạo trước khi đụng Keycloak
            AssignmentScope scope = toAssignmentScope(request);
            Long campusId = scope == null || scope.staffRole() == StaffRole.PRINCIPAL
                    ? null
                    : staffAssignmentService.resolveCampusId(scope.staffRole(), scope.campusId(), scope.classroomId());
            staffAccessPolicy.checkCanCreate(actor, request.role(), campusId);

            String username = request.username().trim().toLowerCase(Locale.ROOT);
            String email = StringUtils.hasText(request.email())
                    ? request.email().trim().toLowerCase(Locale.ROOT)
                    : null;
            boolean sendSetupEmail = email != null && identityProviderClient.canSendSetupEmail();
            if (request.temporaryPassword() == null && !sendSetupEmail) {
                throw new GlobalException(
                        ApiCode.BAD_REQUEST, "Temporary password is required when a setup email cannot be sent");
            }
            if (userAccountRepository.existsByUsername(username)) {
                throw new GlobalException(ApiCode.CONFLICT, "Username already exists");
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
                throw new GlobalException(ApiCode.BAD_REQUEST, "You cannot change the status of your own account");
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
                throw new GlobalException(ApiCode.BAD_REQUEST, "Use change password for your own account");
            }
            UserAccount account = managedAccountLoader.loadManageable(actor, userId);
            accountLifecycle.resetTemporaryPassword(logPrefix, account, temporaryPassword);
            log.info("{}|END", logPrefix);
            return accountMapper.toAccountResponse(account);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // HT tự có phân công toàn trường; phụ huynh không có phân công; nhân sự khác theo request (có thể trống)
    private AssignmentScope toAssignmentScope(CreateAccountRequest request) {
        StaffAssignmentScopeRequest requested = request.assignment();
        LocalDate today = LocalDate.now(clock);
        return switch (request.role()) {
            case PRINCIPAL -> {
                if (requested != null) {
                    throw new GlobalException(ApiCode.BAD_REQUEST, "Principal assignment covers the whole school");
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
                    throw new GlobalException(ApiCode.BAD_REQUEST, "This role has no staff assignment");
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
