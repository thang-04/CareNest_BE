package com.carenest.dto.child;

/**
 * Hồ sơ trẻ để Web mở lại hồ sơ (mục 4.6 plan tiếp nhận): ChildView kèm khai báo sức khỏe ({@code declaration} null khi
 * chưa khai báo). Lịch sử xếp lớp chưa có API nên không trả.
 */
public record ChildPhotoProfileResponse(ChildViewResponse child, HealthDeclarationResponse declaration) {}
