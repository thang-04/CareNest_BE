package com.carenest.mapper.organization;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.organization.StaffAssignment;
import org.springframework.stereotype.Component;

@Component
public class StaffAssignmentMapper {

    public StaffAssignmentSummaryResponse toSummaryResponse(StaffAssignment assignment) {
        return new StaffAssignmentSummaryResponse(
                assignment.getStaffRole(),
                assignment.getCampusId(),
                assignment.getClassroomId(),
                assignment.getAgeGroupId(),
                assignment.getValidFrom(),
                assignment.getValidTo());
    }
}
