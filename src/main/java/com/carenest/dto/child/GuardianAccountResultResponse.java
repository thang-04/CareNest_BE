package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianAccountResult;
import com.carenest.utils.enums.SmsStatus;

/**
 * Kết quả tài khoản từng phụ huynh khi lưu form tiếp nhận, theo thứ tự phụ huynh (AUTH-20). {@code phoneChanged}
 * luôn false tới khi tiếp nhận sửa được tài khoản có sẵn (AUTH-21, Phase 4); {@code smsStatus} chỉ có khi CREATED.
 */
public record GuardianAccountResultResponse(
        String fullName, String phone, GuardianAccountResult result, boolean phoneChanged, SmsStatus smsStatus) {}
