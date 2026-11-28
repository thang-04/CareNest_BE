package com.carenest.dto.announcement;

import com.carenest.utils.enums.AnnouncementScopeType;
import com.carenest.utils.enums.RecipientRole;
import java.util.Set;

/**
 * Người nhận: phạm vi × vai trò (FR-011). {@code campusId} bắt buộc với CAMPUS, tùy chọn với AGE_GROUP (khối trong một
 * điểm trường, PHT bắt buộc); {@code ageGroupId} với AGE_GROUP; {@code classroomId} với CLASSROOM.
 */
public record RecipientTargetRequest(
        AnnouncementScopeType scopeType, Long campusId, Long ageGroupId, Long classroomId, Set<RecipientRole> roles) {}
