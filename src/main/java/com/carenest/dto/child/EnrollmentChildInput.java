package com.carenest.dto.child;

import java.util.List;

/** Thông tin trẻ trên form; ngày dạng yyyy-MM-dd, giới tính MALE/FEMALE (chữ để báo lỗi theo ô thay vì 400 chung). */
public record EnrollmentChildInput(
        String fullName, String dateOfBirth, String gender, List<GuardianInput> guardians, ChildProfileInput profile) {

    @Override
    public String toString() {
        return "EnrollmentChildInput[guardians=" + (guardians == null ? 0 : guardians.size()) + "]";
    }
}
