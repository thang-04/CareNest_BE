package com.carenest.service.account.impl;

import com.carenest.dto.account.CreateStaffAssignmentRequest;
import com.carenest.dto.organization.NewStaffAssignment;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.StaffingService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffingServiceImpl implements StaffingService {
    private static final String LOG_PREFIX = "[StaffingServiceImpl]";
    // Phân công PRINCIPAL chỉ sinh khi Admin tạo tài khoản HT; phụ huynh không có phân công
    private static final Set<CareNestRole> ASSIGNABLE_ROLES =
            Set.of(CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER, CareNestRole.KITCHEN_STAFF);

    private final StaffAssignmentService staffAssignmentService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final ManagedAccountLoader managedAccountLoader;
    private final Clock clock;

    @Override
    @Transactional
    public StaffAssignmentResponse assignStaff(CurrentUser actor, CreateStaffAssignmentRequest request) {
        String logPrefix = LOG_PREFIX + "|assignStaff|actorUserId=" + actor.userId() + "|userId=" + request.userId();
        log.info("{}|START", logPrefix);
        try {
            // AUTH-12: không tự mở rộng phạm vi của chính mình
            if (request.userId().equals(actor.userId())) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "You cannot change your own assignments");
            }
            UserAccount target = managedAccountLoader.loadExisting(actor, request.userId());
            if (target.getPrimaryRole() == null || !ASSIGNABLE_ROLES.contains(target.getPrimaryRole())) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "This account cannot be assigned");
            }
            StaffRole staffRole = StaffRole.valueOf(target.getPrimaryRole().name());
            Long campusId =
                    staffAssignmentService.resolveCampusId(staffRole, request.campusId(), request.classroomId());
            staffAccessPolicy.checkCanAssign(actor, staffRole, campusId);

            StaffAssignmentResponse created = staffAssignmentService.createAssignment(new NewStaffAssignment(
                    target.getId(),
                    staffRole,
                    request.campusId(),
                    request.classroomId(),
                    request.validFrom() == null ? LocalDate.now(clock) : request.validFrom(),
                    request.validTo()));
            log.info("{}|assignmentId={}|END", logPrefix, created.id());
            return created;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public StaffAssignmentResponse endAssignment(CurrentUser actor, Long assignmentId, LocalDate lastDay) {
        String logPrefix =
                LOG_PREFIX + "|endAssignment|actorUserId=" + actor.userId() + "|assignmentId=" + assignmentId;
        log.info("{}|START", logPrefix);
        try {
            boolean fullScope = actor.hasRole(CareNestRole.ADMIN) || staffAccessPolicy.managesWholeSchool(actor);
            StaffAssignmentResponse assignment = staffAssignmentService
                    .findAssignment(assignmentId)
                    .orElseThrow(() -> fullScope
                            ? new GlobalException(ApiCode.NOT_FOUND, "Assignment not found")
                            : new GlobalException(ApiCode.FORBIDDEN, "You cannot manage this assignment"));
            if (assignment.userId().equals(actor.userId())) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "You cannot change your own assignments");
            }
            Long campusId = assignment.staffRole() == StaffRole.PRINCIPAL
                    ? null
                    : staffAssignmentService.resolveCampusId(
                            assignment.staffRole(), assignment.campusId(), assignment.classroomId());
            staffAccessPolicy.checkCanAssign(actor, assignment.staffRole(), campusId);

            StaffAssignmentResponse ended = staffAssignmentService.endAssignment(assignmentId, lastDay);
            log.info("{}|END", logPrefix);
            return ended;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
