package com.carenest.dto.child;

/**
 * Hành chính của trẻ (CHD-05): bắt buộc trừ số định danh, nhóm máu; premature YES/NO khớp tuần thai (dưới 37 tuần là sinh
 * non).
 */
public record ChildProfileInput(
        String idNumber,
        String premature,
        Integer gestationWeeks,
        String ethnicity,
        String beneficiary,
        String bloodType,
        ChildAddressInput address) {

    @Override
    public String toString() {
        return "ChildProfileInput[premature=" + premature + "]";
    }
}
