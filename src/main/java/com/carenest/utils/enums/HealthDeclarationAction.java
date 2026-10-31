package com.carenest.utils.enums;

/**
 * Việc ghi vào lịch sử khai báo sức khỏe (bảng child_health_declaration_history): khai báo, sửa, cập nhật tiền sử từ
 * phiếu theo dõi, từ phiếu khám.
 */
public enum HealthDeclarationAction {
    DECLARED,
    UPDATED,
    UPDATED_FROM_FOLLOW_UP,
    UPDATED_FROM_EXAM
}
