package com.carenest.service.account.impl;

import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.child.AddGuardianRequest;
import com.carenest.dto.child.AddGuardianResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.child.GuardianResponse;
import com.carenest.dto.child.ParentPasswordResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.mapper.account.AccountMapper;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.GuardianAccountService;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.PhoneNumbers;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tài khoản phụ huynh (AUTH-18, AUTH-19). Không log SĐT/họ tên/mật khẩu — chỉ id. Tạo user Keycloak rồi lưu tài
 * khoản + liên kết trẻ trong một transaction; lỗi ⇒ xóa user Keycloak (AccountLifecycleOperations).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardianAccountServiceImpl implements GuardianAccountService {
    private static final String LOG_PREFIX = "[GuardianAccountServiceImpl]";

    private final UserAccountRepository userAccountRepository;
    private final GuardianChildLinkService guardianChildLinkService;
    private final ChildService childService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final AccountLifecycleOperations accountLifecycle;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final AccountMapper accountMapper;

    @Override
    public List<GuardianResponse> listGuardians(CurrentUser actor, Long childId) {
        String logPrefix = LOG_PREFIX + "|listGuardians|actorUserId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkChild(actor, childId);
            List<GuardianLinkResponse> links = guardianChildLinkService.findLinksOfChild(childId);
            Map<Long, UserAccount> accounts =
                    userAccountRepository
                            .findAllById(links.stream()
                                    .map(GuardianLinkResponse::userId)
                                    .toList())
                            .stream()
                            .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
            List<GuardianResponse> guardians = links.stream()
                    .filter(link -> accounts.containsKey(link.userId()))
                    .map(link -> {
                        UserAccount account = accounts.get(link.userId());
                        return new GuardianResponse(
                                account.getId(),
                                account.getUsername(),
                                account.getFullName(),
                                link.relationship(),
                                account.getStatus(),
                                link.linkedAt());
                    })
                    .toList();
            log.info("{}|END", logPrefix);
            return guardians;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AddGuardianResponse addGuardian(CurrentUser actor, Long childId, AddGuardianRequest request) {
        String logPrefix = LOG_PREFIX + "|addGuardian|actorUserId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkChild(actor, childId);
            String username = PhoneNumbers.normalize(request.phone())
                    .orElseThrow(() -> GlobalException.of(ApiCode.PHONE_NUMBER_INVALID));
            String fullName = request.fullName().trim();

            Optional<UserAccount> existing = userAccountRepository.findByUsername(username);
            if (existing.isPresent()) {
                // Anh/chị em học cùng trường: dùng lại tài khoản phụ huynh, giữ mật khẩu (AUTH-18)
                UserAccount parent = existing.get();
                if (parent.getPrimaryRole() != CareNestRole.PARENT) {
                    throw GlobalException.of(ApiCode.PHONE_BELONGS_TO_STAFF);
                }
                guardianChildLinkService.createLink(parent.getId(), childId, request.relationship());
                log.info("{}|userId={}|newAccount=false|END", logPrefix, parent.getId());
                return response(parent, request, false, null);
            }

            String temporaryPassword = passwordGenerator.generate(); // carenest:allow-secret
            UserAccount created = accountLifecycle.createIdentity(
                    logPrefix,
                    new NewIdentityUser(username, null, fullName, temporaryPassword),
                    CareNestRole.PARENT,
                    keycloakUserId -> {
                        UserAccount parent = userAccountRepository.saveAndFlush(
                                UserAccount.create(keycloakUserId, username, null, fullName, CareNestRole.PARENT));
                        guardianChildLinkService.createLink(parent.getId(), childId, request.relationship());
                        return parent;
                    });
            log.info("{}|userId={}|newAccount=true|END", logPrefix, created.getId());
            return response(created, request, true, temporaryPassword);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void removeGuardian(CurrentUser actor, Long childId, Long userId) {
        String logPrefix = LOG_PREFIX + "|removeGuardian|actorUserId=" + actor.userId() + "|childId=" + childId
                + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            checkChild(actor, childId);
            // Tài khoản giữ nguyên; mất liên kết ⇒ mất quyền xem trẻ ngay (AUTH-04)
            guardianChildLinkService.deleteLink(userId, childId);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AccountResponse updateParentStatus(CurrentUser actor, Long userId, UserStatus status) {
        String logPrefix = LOG_PREFIX + "|updateParentStatus|actorUserId=" + actor.userId() + "|userId=" + userId
                + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            UserAccount parent = loadManageableParent(actor, userId);
            accountLifecycle.applyStatus(logPrefix, parent, status);
            log.info("{}|END", logPrefix);
            return accountMapper.toAccountResponse(parent);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ParentPasswordResponse resetParentPassword(CurrentUser actor, Long userId) {
        String logPrefix = LOG_PREFIX + "|resetParentPassword|actorUserId=" + actor.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            UserAccount parent = loadManageableParent(actor, userId);
            String temporaryPassword = passwordGenerator.generate(); // carenest:allow-secret
            accountLifecycle.resetTemporaryPassword(logPrefix, parent, temporaryPassword);
            log.info("{}|END", logPrefix);
            return new ParentPasswordResponse(parent.getId(), parent.getUsername(), temporaryPassword);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Kiểm phạm vi trước: với PHT, trẻ không tồn tại và trẻ ngoài campus cùng 403 (không dò được id)
    private void checkChild(CurrentUser actor, Long childId) {
        staffAccessPolicy.checkCanManageChildGuardians(actor, childId);
        if (!childService.childExists(childId)) {
            throw GlobalException.of(ApiCode.CHILD_NOT_FOUND);
        }
    }

    private UserAccount loadManageableParent(CurrentUser actor, Long userId) {
        // AUTH-12: không tự thao tác tài khoản của chính mình
        if (userId.equals(actor.userId())) {
            throw GlobalException.of(ApiCode.CANNOT_MANAGE_OWN_ACCOUNT);
        }
        UserAccount account = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> staffAccessPolicy.managesWholeSchool(actor)
                        ? GlobalException.of(ApiCode.ACCOUNT_NOT_FOUND)
                        : GlobalException.of(ApiCode.ACCOUNT_MANAGE_DENIED));
        staffAccessPolicy.checkCanManageParent(actor, userId);
        if (account.getPrimaryRole() != CareNestRole.PARENT) {
            throw GlobalException.of(ApiCode.NOT_PARENT_ACCOUNT);
        }
        return account;
    }

    private static AddGuardianResponse response(
            UserAccount parent, AddGuardianRequest request, boolean newAccount, String temporaryPassword) {
        return new AddGuardianResponse(
                parent.getId(),
                parent.getUsername(),
                parent.getFullName(),
                request.relationship(),
                parent.getStatus(),
                newAccount,
                temporaryPassword);
    }
}
