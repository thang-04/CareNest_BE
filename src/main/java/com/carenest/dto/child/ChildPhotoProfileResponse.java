package com.carenest.dto.child;

import java.util.List;

/**
 * Hồ sơ trẻ để Web mở lại hồ sơ (mục 4.6 plan tiếp nhận): ChildView, lớp ({@code cls} — null khi chờ xếp lớp), khai báo
 * sức khỏe ({@code declaration} null khi chưa khai báo) và lịch sử xếp / chuyển lớp ({@code placements}, mới nhất trước).
 */
public record ChildPhotoProfileResponse(
        ChildViewResponse child,
        ChildClassResponse cls,
        HealthDeclarationResponse declaration,
        List<ChildPlacementResponse> placements) {}
