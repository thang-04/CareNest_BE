package com.carenest.dto.child;

import com.carenest.utils.enums.YesNo;

/** Hành chính của trẻ (CHD-05); nguồn chi trả luôn BHYT. */
public record ChildProfileDetailResponse(
        String idNumber,
        YesNo premature,
        Short gestationWeeks,
        String ethnicity,
        String beneficiary,
        String paymentSource,
        String bloodType,
        ChildAddressResponse address) {}
