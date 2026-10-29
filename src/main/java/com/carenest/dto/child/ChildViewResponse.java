package com.carenest.dto.child;

import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Gender;
import java.time.LocalDate;
import java.util.List;

/**
 * Hồ sơ trẻ trả về sau tiếp nhận / mở lại hồ sơ (ChildView, mục 4 plan tiếp nhận). Lớp, điểm trường là hiện tại:
 * có lớp ⇒ theo lớp, chưa có ⇒ điểm trường tiếp nhận. Phụ huynh kèm tài khoản (chỉ người tiếp nhận HT/PHT gọi được).
 */
public record ChildViewResponse(
        Long id,
        String code,
        String fullName,
        LocalDate dateOfBirth,
        Gender gender,
        ChildStatus status,
        Long campusId,
        Long classId,
        String className,
        Long ageGroupId,
        Long schoolYearId,
        LocalDate enrolledAt,
        LocalDate leftAt,
        String leftReason,
        LocalDate readmittedAt,
        List<String> allergies,
        boolean hasDeclaration,
        ChildProfileDetailResponse profile,
        List<ChildGuardianResponse> guardians,
        List<ChildHistoryResponse> history) {}
