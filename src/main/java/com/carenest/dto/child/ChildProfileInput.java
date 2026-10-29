package com.carenest.dto.child;

/** Hành chính của trẻ (CHD-05): bắt buộc trừ số định danh, nhóm máu, tuần thai khi không sinh non; premature YES/NO. */
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
