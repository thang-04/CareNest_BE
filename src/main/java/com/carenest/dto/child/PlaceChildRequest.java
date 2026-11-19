package com.carenest.dto.child;

/**
 * Xếp / chuyển lớp ({@code POST /children/{id}/placement}, plan tiếp nhận mục 4.4): {@code classId} bắt buộc;
 * {@code reason} bắt buộc khi trẻ đang có lớp (chuyển lớp), tối đa 300 ký tự. Lỗi theo ô {@code classId}, {@code reason}.
 */
public record PlaceChildRequest(Long classId, String reason) {}
