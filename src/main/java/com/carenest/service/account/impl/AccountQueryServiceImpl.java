package com.carenest.service.account.impl;

import com.carenest.dto.account.AccountDetailResponse;
import com.carenest.dto.account.AccountSummaryResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.account.AccountMapper;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserAccountSpecifications;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.AccountQueryService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryServiceImpl implements AccountQueryService {
    private static final String LOG_PREFIX = "[AccountQueryServiceImpl]";
    private static final Set<CareNestRole> CAMPUS_MANAGED_ROLES =
            Set.of(CareNestRole.TEACHER, CareNestRole.KITCHEN_STAFF);

    private final UserAccountRepository userAccountRepository;
    private final StaffAssignmentService staffAssignmentService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final ManagedAccountLoader managedAccountLoader;
    private final AccountMapper accountMapper;
    private final Clock clock;

    @Override
    public PageResponse<AccountSummaryResponse> searchAccounts(
            CurrentUser actor,
            CareNestRole role,
            UserStatus status,
            Long campusId,
            String keyword,
            int page,
            int size) {
        String logPrefix =
                LOG_PREFIX + "|searchAccounts|userId=" + actor.userId() + "|role=" + role + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Collection<Long> userIds;
            Collection<CareNestRole> roles = null;
            if (actor.hasRole(CareNestRole.ADMIN) || staffAccessPolicy.managesWholeSchool(actor)) {
                userIds = campusId == null
                        ? null
                        : staffAssignmentService.findActiveStaffUserIds(Set.of(campusId), today);
            } else {
                // AUTH-08: PHT chỉ thấy GV/bếp đang được phân công trong campus mình
                Set<Long> managed = staffAccessPolicy.managedCampusIds(actor);
                if (campusId != null && !managed.contains(campusId)) {
                    throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this campus");
                }
                userIds = staffAssignmentService.findActiveStaffUserIds(
                        campusId == null ? managed : Set.of(campusId), today);
                roles = CAMPUS_MANAGED_ROLES;
            }

            Specification<UserAccount> spec = Specification.allOf(
                    UserAccountSpecifications.idIn(userIds),
                    UserAccountSpecifications.roleIn(roles),
                    UserAccountSpecifications.hasRole(role),
                    UserAccountSpecifications.hasStatus(status),
                    UserAccountSpecifications.matches(keyword));
            Page<UserAccount> accounts = userAccountRepository.findAll(
                    spec,
                    PageRequest.of(page, size, Sort.by("fullName").ascending().and(Sort.by("id"))));
            Map<Long, List<StaffAssignmentSummaryResponse>> assignments =
                    staffAssignmentService.findActiveAssignmentsByUserIds(
                            accounts.getContent().stream()
                                    .map(UserAccount::getId)
                                    .toList(),
                            today);
            PageResponse<AccountSummaryResponse> response = PageResponse.from(
                    accounts,
                    account -> accountMapper.toSummaryResponse(
                            account, assignments.getOrDefault(account.getId(), List.of())));
            log.info("{}|total={}|END", logPrefix, accounts.getTotalElements());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AccountDetailResponse getAccountDetail(CurrentUser actor, Long userId) {
        String logPrefix = LOG_PREFIX + "|getAccountDetail|actorUserId=" + actor.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            UserAccount account = managedAccountLoader.loadManageable(actor, userId);
            AccountDetailResponse detail =
                    accountMapper.toDetailResponse(account, staffAssignmentService.findAssignments(userId));
            log.info("{}|END", logPrefix);
            return detail;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
