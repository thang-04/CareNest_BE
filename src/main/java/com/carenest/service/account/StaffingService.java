package com.carenest.service.account;

import com.carenest.dto.account.CreateStaffAssignmentRequest;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/** Gán / kết thúc phân công nhân sự theo phạm vi người thao tác (AUTH-16). Ghi phân công qua feature organization. */
public interface StaffingService {

    StaffAssignmentResponse assignStaff(CurrentUser actor, CreateStaffAssignmentRequest request);

    StaffAssignmentResponse endAssignment(CurrentUser actor, Long assignmentId, LocalDate lastDay);
}
