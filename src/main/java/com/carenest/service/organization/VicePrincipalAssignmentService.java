package com.carenest.service.organization;

import com.carenest.dto.organization.VicePrincipalAssignmentRequest;
import com.carenest.dto.organization.VicePrincipalAssignmentResponse;
import com.carenest.security.CurrentUser;

/** Cấu hình trường › Phân công Phó hiệu trưởng vào điểm trường theo năm học (Hiệu trưởng). */
public interface VicePrincipalAssignmentService {

    VicePrincipalAssignmentResponse getAssignments(CurrentUser currentUser, Long schoolYearId);

    VicePrincipalAssignmentResponse assign(
            CurrentUser currentUser, Long schoolYearId, Long userId, VicePrincipalAssignmentRequest request);
}
