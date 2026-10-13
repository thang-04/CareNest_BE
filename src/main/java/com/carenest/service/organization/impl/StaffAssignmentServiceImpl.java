package com.carenest.service.organization.impl;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.mapper.organization.StaffAssignmentMapper;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAssignmentServiceImpl implements StaffAssignmentService {

    private final StaffAssignmentRepository staffAssignmentRepository;
    private final StaffAssignmentMapper staffAssignmentMapper;

    @Override
    public boolean hasActivePrincipalAssignment(Long userId, LocalDate date) {
        return staffAssignmentRepository.existsActiveByRole(userId, StaffRole.PRINCIPAL, date);
    }

    @Override
    public boolean hasActiveCampusAssignment(Long userId, StaffRole staffRole, Long campusId, LocalDate date) {
        return staffAssignmentRepository.existsActiveForCampus(userId, staffRole, campusId, date);
    }

    @Override
    public boolean hasActiveTeacherAssignment(Long userId, Long classroomId, LocalDate date) {
        return staffAssignmentRepository.existsActiveTeacherForClassroom(userId, classroomId, date);
    }

    @Override
    public List<StaffAssignmentSummaryResponse> findActiveAssignments(Long userId, LocalDate date) {
        return staffAssignmentRepository.findActiveByUserId(userId, date).stream()
                .map(staffAssignmentMapper::toSummaryResponse)
                .toList();
    }
}
