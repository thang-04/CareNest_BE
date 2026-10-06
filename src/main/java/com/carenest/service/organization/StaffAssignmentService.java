package com.carenest.service.organization;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.organization.StaffRole;
import com.carenest.mapper.organization.StaffAssignmentMapper;
import com.carenest.repository.organization.StaffAssignmentRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Truy vấn phân công cho kiểm tra scope (security/authorization); không gọi ngược policy (MODULE_MAP). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAssignmentService {

    private final StaffAssignmentRepository staffAssignmentRepository;
    private final StaffAssignmentMapper staffAssignmentMapper;

    public boolean hasActivePrincipalAssignment(Long userId, LocalDate date) {
        return staffAssignmentRepository.existsActiveByRole(userId, StaffRole.PRINCIPAL, date);
    }

    public boolean hasActiveCampusAssignment(Long userId, StaffRole staffRole, Long campusId, LocalDate date) {
        return staffAssignmentRepository.existsActiveForCampus(userId, staffRole, campusId, date);
    }

    public boolean hasActiveTeacherAssignment(Long userId, Long classroomId, LocalDate date) {
        return staffAssignmentRepository.existsActiveTeacherForClassroom(userId, classroomId, date);
    }

    public List<StaffAssignmentSummaryResponse> findActiveAssignments(Long userId, LocalDate date) {
        return staffAssignmentRepository.findActiveByUserId(userId, date).stream()
                .map(staffAssignmentMapper::toSummaryResponse)
                .toList();
    }
}
