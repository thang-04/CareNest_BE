package com.carenest.dto.child;

/** Phụ huynh trên một hồ sơ trẻ, dùng để kiểm một người một tài khoản (AUTH-18) ở feature account. */
public record GuardianRecord(Long childId, String phone, String citizenId, Long userId) {

    @Override
    public String toString() {
        return "GuardianRecord[childId=" + childId + "]";
    }
}
