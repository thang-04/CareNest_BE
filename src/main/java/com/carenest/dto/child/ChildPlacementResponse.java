package com.carenest.dto.child;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Một lần xếp / chuyển lớp ở hồ sơ trẻ ({@code placements[]}, mới nhất trước): {@code fromClassId} null = xếp lần đầu
 * (tiếp nhận có lớp hoặc trẻ chờ xếp lớp); {@code reason} null khi không ghi lý do. {@code effectiveFrom}: ngày vào lớp
 * mới (G94-180/CHD-14, có thể sau hôm nay); {@code cancelledAt} khác null = lịch chưa tới ngày đã bị hủy hoặc bị thay.
 */
public record ChildPlacementResponse(
        Long id,
        Long fromClassId,
        String fromClassName,
        Long toClassId,
        String toClassName,
        Long userId,
        String userName,
        Instant at,
        String reason,
        LocalDate effectiveFrom,
        Instant cancelledAt) {}
