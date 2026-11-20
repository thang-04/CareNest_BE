package com.carenest.dto.child;

import com.carenest.utils.enums.Gender;
import java.time.LocalDate;
import java.util.List;

/**
 * Form tiếp nhận đã kiểm và chuẩn hóa (feature child → account để tạo tài khoản rồi ghi hồ sơ). {@code classroomId}
 * null = Chờ xếp lớp ở {@code campusId}; có lớp thì {@code campusId} = điểm trường của lớp. Dữ liệu cá nhân: không log.
 */
public record EnrollmentDraft(
        String fullName,
        LocalDate dateOfBirth,
        Gender gender,
        List<GuardianDraft> guardians,
        ChildProfileDraft profile,
        HealthDeclarationDraft declaration,
        Long classroomId,
        Long campusId,
        Long schoolYearId,
        LocalDate startDate) {

    public EnrollmentDraft {
        guardians = List.copyOf(guardians);
    }

    public EnrollmentDraft(
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            List<GuardianDraft> guardians,
            ChildProfileDraft profile,
            HealthDeclarationDraft declaration,
            Long classroomId,
            Long campusId) {
        this(fullName, dateOfBirth, gender, guardians, profile, declaration, classroomId, campusId, null, null);
    }

    public EnrollmentDraft withPlacement(Long placedClassroomId, Long placedCampusId) {
        return new EnrollmentDraft(
                fullName,
                dateOfBirth,
                gender,
                guardians,
                profile,
                declaration,
                placedClassroomId,
                placedCampusId,
                schoolYearId,
                startDate);
    }

    public EnrollmentDraft withAdmission(Long targetSchoolYearId, LocalDate targetStartDate) {
        return new EnrollmentDraft(
                fullName,
                dateOfBirth,
                gender,
                guardians,
                profile,
                declaration,
                classroomId,
                campusId,
                targetSchoolYearId,
                targetStartDate);
    }

    public EnrollmentDraft withGuardians(List<GuardianDraft> replaced) {
        return new EnrollmentDraft(
                fullName,
                dateOfBirth,
                gender,
                replaced,
                profile,
                declaration,
                classroomId,
                campusId,
                schoolYearId,
                startDate);
    }

    @Override
    public String toString() {
        return "EnrollmentDraft[guardians=" + guardians.size() + ", classroomId=" + classroomId + ", campusId="
                + campusId + "]";
    }
}
