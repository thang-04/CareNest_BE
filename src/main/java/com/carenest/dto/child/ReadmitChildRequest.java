package com.carenest.dto.child;

/** Mở lại hồ sơ trẻ đã nghỉ học (CHD-03): lý do bắt buộc, tối đa 300 ký tự. */
public record ReadmitChildRequest(String reason) {}
