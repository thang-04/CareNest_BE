package com.carenest.dto.child;

/** Từ chối yêu cầu sửa hồ sơ trẻ: lý do bắt buộc, tối đa 500 ký tự (CHD-11). Duyệt không cần body. */
public record ChildProfileChangeDecisionRequest(String note) {}
