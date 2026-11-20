package com.carenest.dto.child;

import com.carenest.utils.enums.ProfileChangeKind;
import com.carenest.utils.enums.ProfileChangeStatus;
import java.time.Instant;

/**
 * Yêu cầu sửa hồ sơ trẻ của GV (CHD-11). Đúng một trong {@code profile} / {@code declaration} có giá trị theo
 * {@code kind}. {@code canDecide}: người xem duyệt/từ chối được (HT, PHT điểm trường của trẻ có CHILD_PROFILE_EDIT).
 */
public record ChildProfileChangeResponse(
        Long id,
        Long childId,
        String childCode,
        String childName,
        String className,
        Long campusId,
        ProfileChangeKind kind,
        ProfileChangeStatus status,
        UpdateChildRequest profile,
        HealthDeclarationRequest declaration,
        Long requestedBy,
        String requestedByName,
        Instant requestedAt,
        Long decidedBy,
        String decidedByName,
        Instant decidedAt,
        String decisionNote,
        boolean canDecide) {}
