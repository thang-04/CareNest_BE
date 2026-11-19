package com.carenest.dto.child;

/**
 * Một trẻ ở màn Tài khoản phụ huynh ({@code GET /children/guardian-overview}): mục danh sách trẻ như {@code GET /children}
 * kèm tài khoản của phụ huynh nhận tài khoản (như {@code GET /children/{id}/guardians}). {@code account} null khi chưa kích
 * hoạt, chưa chọn người nhận hoặc người xem không quản lý được tài khoản phụ huynh của trẻ.
 */
public record ChildGuardianOverviewResponse(ChildListItemResponse child, GuardianResponse account) {}
