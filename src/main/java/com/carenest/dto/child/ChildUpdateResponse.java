package com.carenest.dto.child;

import com.carenest.dto.account.GuardianPhoneChangeResponse;
import java.util.List;

/**
 * Kết quả Sửa hồ sơ (mục 4.2 plan tiếp nhận). {@code accountChanges}: tài khoản phụ huynh vừa đổi họ tên/email/CCCD theo
 * hồ sơ. {@code phoneChangeRequests}: SĐT của phụ huynh đã có tài khoản chưa đổi ngay mà chờ duyệt (G94-180/AUTH-21).
 */
public record ChildUpdateResponse(
        ChildViewResponse child,
        List<GuardianAccountChangeResponse> accountChanges,
        List<GuardianPhoneChangeResponse> phoneChangeRequests) {}
