package com.carenest.dto.attendance;

import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.PickupOutcome;
import com.carenest.utils.enums.PickupVerification;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một lần xác minh người đón. Khớp ảnh: người đón là phụ huynh ở vị trí {@code guardianPosition} trên hồ sơ trẻ; gọi phụ
 * huynh: người đón, lý do ghi trong {@code note} (nội dung cuộc gọi). {@code notifiedParents}: số tài khoản phụ huynh đã
 * được báo trẻ được đón.
 */
public record PickupRecordResponse(
        Long id,
        LocalDate date,
        Long childId,
        String childName,
        Long classId,
        String className,
        PickupOutcome outcome,
        PickupVerification verification,
        Integer guardianPosition,
        String pickupPersonName,
        GuardianRelationship pickupPersonRelationship,
        String pickupPersonPhone,
        Instant handedOverAt,
        String note,
        int notifiedParents,
        Long recordedBy,
        String recordedByName,
        Instant recordedAt) {}
