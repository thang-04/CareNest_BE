package com.carenest.dto.child;

import java.time.Instant;

/**
 * Một lần xếp / chuyển lớp ở hồ sơ trẻ ({@code placements[]}, mới nhất trước): {@code fromClassId} null = xếp lần đầu
 * (tiếp nhận có lớp hoặc trẻ chờ xếp lớp); {@code reason} null khi không ghi lý do.
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
        String reason) {}
