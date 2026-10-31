package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianRelationship;

/** Phụ huynh trên hồ sơ trẻ để đối chiếu người đón: vị trí, họ tên, quan hệ, SĐT, tài khoản (null khi chưa kích hoạt). */
public record ChildGuardianContact(
        int position, String fullName, GuardianRelationship relationship, String phone, Long userId) {}
