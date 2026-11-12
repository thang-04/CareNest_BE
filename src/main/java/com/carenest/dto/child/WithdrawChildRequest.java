package com.carenest.dto.child;

/**
 * Cho trẻ nghỉ học (G94-180/CHD-03): {@code leftAt} = ngày bắt đầu nghỉ dạng yyyy-MM-dd (chữ để báo lỗi theo ô), lý do
 * bắt buộc, tối đa 300 ký tự.
 */
public record WithdrawChildRequest(String leftAt, String reason) {}
