package com.carenest.service.account.impl;

import com.carenest.dto.account.MyAccountResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private static final String LOG_PREFIX = "[AccountServiceImpl]";

    private final StaffAssignmentService staffAssignmentService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentService enrollmentService;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public MyAccountResponse getMyAccount(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMyAccount|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            // Tóm tắt phạm vi tính giống policy: phân công/liên kết chỉ có hiệu lực khi role tương ứng có trong token
            List<StaffAssignmentSummaryResponse> assignments =
                    staffAssignmentService.findActiveAssignments(currentUser.userId(), LocalDate.now(clock)).stream()
                            .filter(assignment -> currentUser.hasRole(tokenRoleOf(assignment.staffRole())))
                            .toList();
            List<Long> childIds =
                    currentUser.hasRole(CareNestRole.PARENT) ? studyingChildIds(currentUser.userId()) : List.of();

            MyAccountResponse account = new MyAccountResponse(
                    currentUser.userId(),
                    currentUser.username(),
                    currentUser.fullName(),
                    currentUser.email(),
                    currentUser.roles().stream().sorted().toList(),
                    currentUser.permissions().stream().sorted().toList(),
                    assignments,
                    childIds);
            log.info("{}|END", logPrefix);
            return account;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findFullNames(Collection<Long> userIds) {
        String logPrefix = LOG_PREFIX + "|findFullNames|count=" + userIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, String> names = userIds.isEmpty()
                    ? Map.of()
                    : userAccountRepository.findAllById(userIds).stream()
                            .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName));
            log.info("{}|END", logPrefix);
            return names;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Tổ trưởng khối là giáo viên có thêm phân công nhóm tuổi: hiệu lực khi token có role TEACHER
    // CHD-04: trẻ đã nghỉ học/ra trường không còn trong phạm vi phụ huynh, giống ChildAccessPolicy
    private List<Long> studyingChildIds(Long userId) {
        List<Long> linked = guardianChildLinkService.findChildIdsOfGuardian(userId);
        Set<Long> left = enrollmentService.findChildIdsLeftSchool(linked, LocalDate.now(clock));
        return linked.stream().filter(childId -> !left.contains(childId)).toList();
    }

    private static CareNestRole tokenRoleOf(StaffRole staffRole) {
        return staffRole == StaffRole.AGE_GROUP_TEAM_LEADER
                ? CareNestRole.TEACHER
                : CareNestRole.valueOf(staffRole.name());
    }
}
