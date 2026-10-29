package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianAccountStatus;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.SmsStatus;
import java.time.Instant;

/** Phụ huynh trên hồ sơ trẻ; tài khoản NOT_ACTIVATED khi chưa liên kết với trẻ; SMS = lần gửi gần nhất. */
public record ChildGuardianResponse(
        String fullName,
        GuardianRelationship relation,
        String phone,
        String idNumber,
        String email,
        GuardianAccountStatus accountStatus,
        SmsStatus smsStatus,
        Instant smsAt) {}
