package com.carenest.service.child.impl;

import java.time.LocalDate;

/** Tuổi vào lớp 1 là 6 tuổi và tính theo năm (Luật Giáo dục 2019, Điều 28 khoản 1 điểm a). */
final class GraduationAge {
    private static final int GRADE_ONE_AGE = 6;

    private GraduationAge() {}

    static boolean oldEnoughForGradeOne(LocalDate dateOfBirth, LocalDate schoolYearStart) {
        return schoolYearStart.getYear() - dateOfBirth.getYear() >= GRADE_ONE_AGE;
    }
}
