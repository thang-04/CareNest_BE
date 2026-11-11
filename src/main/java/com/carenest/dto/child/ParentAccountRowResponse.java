package com.carenest.dto.child;

/**
 * Một phụ huynh trên hồ sơ trẻ chưa nghỉ học ở bảng tổng quan ({@code GET /children/parent-accounts}, đặc tả FE mục 4.8):
 * trẻ, lớp đang học, vị trí phụ huynh trên hồ sơ (từ 0), phụ huynh như danh sách trẻ (không CCCD, email — AUTH-03) và tên
 * đăng nhập của tài khoản đã liên kết (null khi chưa kích hoạt).
 */
public record ParentAccountRowResponse(
        Long childId,
        String childCode,
        String childName,
        Long campusId,
        String className,
        int guardianIndex,
        ChildGuardianResponse guardian,
        String username) {}
