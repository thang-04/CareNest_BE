package com.carenest.utils.enums;

/**
 * Trạng thái trước/sau trong lịch sử phân quyền (AUTH-14): ON/OFF cho ô ma trận role; GRANT/REVOKE/INHERIT cho
 * ngoại lệ từng người (INHERIT = không có ngoại lệ, theo ma trận).
 */
public enum PermissionState {
    ON,
    OFF,
    GRANT,
    REVOKE,
    INHERIT;

    public static PermissionState of(PermissionEffect effect) {
        return effect == null ? INHERIT : valueOf(effect.name());
    }
}
