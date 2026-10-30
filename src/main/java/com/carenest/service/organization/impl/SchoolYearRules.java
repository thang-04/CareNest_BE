package com.carenest.service.organization.impl;

import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.SchoolYearStatus;

/**
 * CFG-YR-01: dữ liệu thuộc năm học đã kết thúc chỉ đọc. Một chỗ kiểm cho mọi service của module tổ chức;
 * module khác gọi SchoolYearService#checkYearOpen / checkClassYearOpen / checkDateYearOpen.
 */
final class SchoolYearRules {

    private SchoolYearRules() {}

    static void checkOpen(SchoolYear year) {
        if (year.getStatus() == SchoolYearStatus.CLOSED) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_CLOSED, year.getName());
        }
    }
}
