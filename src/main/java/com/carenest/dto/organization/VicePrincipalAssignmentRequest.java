package com.carenest.dto.organization;

/**
 * campusId = null ⇒ gỡ phân công PHT của năm học. swap = true ⇒ điểm trường đích đang có PHT khác thì hai người đổi
 * điểm trường cho nhau (AUTH-26); bỏ trống hoặc false thì báo lỗi như cũ.
 */
public record VicePrincipalAssignmentRequest(Long campusId, Boolean swap) {

    public VicePrincipalAssignmentRequest(Long campusId) {
        this(campusId, null);
    }
}
