package com.carenest.dto.child;

import com.carenest.utils.enums.YesNo;

/** Hành chính đã chuẩn hóa (chuỗi đã trim, rỗng ⇒ null với ô không bắt buộc). Dữ liệu nhạy cảm: không log. */
public record ChildProfileDraft(
        String idNumber,
        YesNo premature,
        Short gestationWeeks,
        String ethnicity,
        String beneficiary,
        String bloodType,
        String addressProvince,
        String addressWard,
        String addressStreet) {

    @Override
    public String toString() {
        return "ChildProfileDraft[]";
    }
}
