package com.carenest.service.account.impl;

import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.SelectedParentAccountService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelectedParentAccountServiceImpl implements SelectedParentAccountService {
    private static final String LOG_PREFIX = "[SelectedParentAccountServiceImpl]";
    private final ChildIntakeService childIntakeService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final UserAccountRepository userAccountRepository;
    private final GuardianChildLinkService guardianChildLinkService;
    private final AccountLifecycleOperations accountLifecycle;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final ParentSmsSender smsSender;
    private final TransactionTemplate transactionTemplate;

    @Override
    public Long selectedAccountId(CurrentUser actor, Long childId) {
        String logPrefix = LOG_PREFIX + "|selectedAccountId|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkScope(actor, childId);
            Long result = childIntakeService.findRecipientAccountId(childId).orElse(null);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void checkParentScope(CurrentUser actor, Long userId) {
        String logPrefix = LOG_PREFIX + "|checkParentScope|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            if (!staffAccessPolicy.managesWholeSchool(actor)) {
                var managed = staffAccessPolicy.managedCampusIds(actor);
                boolean allowed =
                        childIntakeService
                                .findChildSummaries(guardianChildLinkService.findChildIdsOfGuardian(userId))
                                .stream()
                                .anyMatch(c -> c.status() != com.carenest.utils.enums.ChildStatus.LEFT
                                        && c.campusId() != null
                                        && managed.contains(c.campusId()));
                if (!allowed) throw GlobalException.of(ApiCode.ACCOUNT_MANAGE_DENIED);
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<ChildGuardianResponse> candidates(CurrentUser actor, Long childId) {
        String logPrefix = LOG_PREFIX + "|candidates|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkScope(actor, childId);
            List<ChildGuardianResponse> result = childIntakeService.accountCandidates(childId);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void recordLink(CurrentUser actor, Long childId, Long guardianId, Long userId) {
        String logPrefix = LOG_PREFIX + "|recordLink|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkScope(actor, childId);
            childIntakeService.linkAccountRecipient(childId, guardianId, userId);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void select(CurrentUser actor, Long childId, Long guardianId) {
        String logPrefix = LOG_PREFIX + "|select|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkScope(actor, childId);
            childIntakeService.selectAccountRecipient(childId, guardianId, actor.userId());
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<ActivationResult> activate(CurrentUser actor, Long childId, Long guardianId) {
        String logPrefix = LOG_PREFIX + "|activate|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            ChildGuardianResponse chosen = candidates(actor, childId).stream()
                    .filter(g -> g.accountRecipient() && g.id().equals(guardianId))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.GUARDIAN_IDENTITY_CONFLICT));
            if (com.carenest.utils.CitizenIds.normalize(chosen.idNumber()).isEmpty()
                    || com.carenest.utils.PhoneNumbers.normalize(chosen.phone()).isEmpty()
                    || chosen.fullName() == null
                    || chosen.fullName().isBlank()) {
                throw GlobalException.of(ApiCode.INVALID_REQUEST_DATA);
            }
            UserAccount existing =
                    userAccountRepository.findByCitizenId(chosen.idNumber()).orElse(null);
            if (existing == null)
                existing = userAccountRepository.findByUsername(chosen.phone()).orElse(null);
            if (existing != null) {
                if (existing.getPrimaryRole() != CareNestRole.PARENT
                        || (existing.getCitizenId() != null
                                && !existing.getCitizenId().equals(chosen.idNumber()))) {
                    throw GlobalException.of(ApiCode.GUARDIAN_IDENTITY_CONFLICT);
                }
                UserAccount parent = existing;
                transactionTemplate.executeWithoutResult(status -> {
                    childIntakeService.linkAccountRecipient(childId, guardianId, parent.getId());
                    UserAccount latest = userAccountRepository
                            .findById(parent.getId())
                            .orElseThrow(() -> GlobalException.of(ApiCode.ACCOUNT_NOT_FOUND));
                    if (latest.getCitizenId() != null && !latest.getCitizenId().equals(chosen.idNumber()))
                        throw GlobalException.of(ApiCode.GUARDIAN_IDENTITY_CONFLICT);
                    if (userAccountRepository.claimCitizenId(parent.getId(), chosen.idNumber()) != 1)
                        throw GlobalException.of(ApiCode.GUARDIAN_IDENTITY_CONFLICT);
                });
                log.info("{}|END", logPrefix);
                return List.of(
                        new ActivationResult(parent.getId(), parent.getFullName(), parent.getUsername(), false, null));
            }
            String temporaryPassword = passwordGenerator.generate(); // carenest:allow-secret
            UserAccount created = accountLifecycle.createIdentity(
                    logPrefix,
                    new NewIdentityUser(chosen.phone(), null, chosen.fullName(), temporaryPassword),
                    CareNestRole.PARENT,
                    identityId -> {
                        UserAccount parent = userAccountRepository.saveAndFlush(UserAccount.createParent(
                                identityId, chosen.phone(), chosen.email(), chosen.fullName(), chosen.idNumber()));
                        childIntakeService.linkAccountRecipient(childId, guardianId, parent.getId());
                        return parent;
                    });
            var smsStatus = smsSender.sendActivation(created, temporaryPassword);
            log.info("{}|END", logPrefix);
            return List.of(new ActivationResult(
                    created.getId(), created.getFullName(), created.getUsername(), true, smsStatus));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void checkScope(CurrentUser actor, Long childId) {
        if (staffAccessPolicy.managesWholeSchool(actor)) return;
        var summary = childIntakeService.findChildSummaries(List.of(childId));
        if (summary.isEmpty()
                || summary.getFirst().status() == com.carenest.utils.enums.ChildStatus.LEFT
                || summary.getFirst().campusId() == null
                || !staffAccessPolicy
                        .managedCampusIds(actor)
                        .contains(summary.getFirst().campusId())) {
            throw GlobalException.of(ApiCode.CHILD_GUARDIAN_MANAGE_DENIED);
        }
    }
}
