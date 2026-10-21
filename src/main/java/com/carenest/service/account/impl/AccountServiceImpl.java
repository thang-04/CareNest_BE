package com.carenest.service.account.impl;

import com.carenest.dto.account.MyAccountResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
                            .filter(assignment -> currentUser.hasRole(
                                    CareNestRole.valueOf(assignment.staffRole().name())))
                            .toList();
            List<Long> childIds = currentUser.hasRole(CareNestRole.PARENT)
                    ? guardianChildLinkService.findChildIdsOfGuardian(currentUser.userId())
                    : List.of();

            MyAccountResponse account = new MyAccountResponse(
                    currentUser.userId(),
                    currentUser.username(),
                    currentUser.fullName(),
                    currentUser.email(),
                    currentUser.roles().stream().sorted().toList(),
                    assignments,
                    childIds);
            log.info("{}|END", logPrefix);
            return account;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
