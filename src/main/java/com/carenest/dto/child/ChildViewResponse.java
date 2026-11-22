package com.carenest.dto.child;

import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Gender;
import java.time.LocalDate;
import java.util.List;

/**
 * Hồ sơ trẻ trả về sau tiếp nhận / mở lại hồ sơ (ChildView, mục 4 plan tiếp nhận). Lớp, điểm trường là hiện tại:
 * có lớp ⇒ theo lớp, chưa có ⇒ điểm trường tiếp nhận. Phụ huynh kèm tài khoản (chỉ người tiếp nhận HT/PHT gọi được).
 * G94-180/CHD-14: chưa có lớp nào bắt đầu thì {@code classId} là lớp sẽ vào; {@code nextClass} = lớp bắt đầu sau hôm
 * nay (lịch chuyển hoặc lớp chưa bắt đầu); {@code startDue} = chờ xếp lớp và đã tới ngày bắt đầu đi học.
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
        boolean mealRegistered,
        ChildProfileDetailResponse profile,
        List<ChildGuardianResponse> guardians,
        List<ChildHistoryResponse> history,
        String photoUrl,
        PlannedAdmissionResponse plannedAdmission,
        LocalDate startDate,
        boolean startDue,
        ChildNextClassResponse nextClass) {

    /** Hồ sơ không kèm ngày bắt đầu đi học / lớp sắp vào (test, nơi dựng sẵn dữ liệu cũ). */
    public ChildViewResponse(
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
            boolean mealRegistered,
            ChildProfileDetailResponse profile,
            List<ChildGuardianResponse> guardians,
            List<ChildHistoryResponse> history,
            String photoUrl,
            PlannedAdmissionResponse plannedAdmission) {
        this(
                id,
                code,
                fullName,
                dateOfBirth,
                gender,
                status,
                campusId,
                classId,
                className,
                ageGroupId,
                schoolYearId,
                enrolledAt,
                leftAt,
                leftReason,
                readmittedAt,
                allergies,
                hasDeclaration,
                mealRegistered,
                profile,
                guardians,
                history,
                photoUrl,
                plannedAdmission,
                null,
                false,
                null);
    }

    public ChildViewResponse withoutGuardianAccounts() {
        return new ChildViewResponse(
                id,
                code,
                fullName,
                dateOfBirth,
                gender,
                status,
                campusId,
                classId,
                className,
                ageGroupId,
                schoolYearId,
                enrolledAt,
                leftAt,
                leftReason,
                readmittedAt,
                allergies,
                hasDeclaration,
                mealRegistered,
                profile,
                guardians.stream()
                        .map(g -> new ChildGuardianResponse(
                                g.fullName(),
                                g.relation(),
                                g.phone(),
                                g.idNumber(),
                                g.email(),
                                null,
                                null,
                                null,
                                g.photoUrl(),
                                g.id(),
                                g.accountRecipient()))
                        .toList(),
                history,
                photoUrl,
                plannedAdmission,
                startDate,
                startDue,
                nextClass);
    }
}
