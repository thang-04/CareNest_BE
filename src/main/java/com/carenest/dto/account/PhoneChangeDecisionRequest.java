package com.carenest.dto.account;

/** Ghi chú khi từ chối yêu cầu đổi SĐT (tùy chọn, tối đa 300 ký tự). */
public record PhoneChangeDecisionRequest(String note) {}
