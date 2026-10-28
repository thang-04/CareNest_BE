package com.carenest.mapper.organization;

import com.carenest.dto.organization.ClassroomResponse;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.organization.Classroom;
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

    public StaffAssignmentResponse toResponse(StaffAssignment assignment) {
        return new StaffAssignmentResponse(
                assignment.getId(),
                assignment.getUserId(),
                assignment.getStaffRole(),
                assignment.getCampusId(),
                assignment.getClassroomId(),
                assignment.getValidFrom(),
                assignment.getValidTo());
    }

    public ClassroomResponse toClassroomResponse(Classroom classroom) {
        return new ClassroomResponse(classroom.getId(), classroom.getCampusId(), classroom.getName());
    }
}
