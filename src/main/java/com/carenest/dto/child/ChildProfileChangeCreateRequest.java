package com.carenest.dto.child;

import com.carenest.utils.enums.ProfileChangeKind;
import jakarta.validation.constraints.NotNull;

/**
 * GV gửi yêu cầu sửa hồ sơ trẻ (CHD-11): {@code kind = PROFILE} kèm {@code profile} (cùng body với `PUT
 * /children/{id}`), {@code kind = HEALTH_DECLARATION} kèm {@code declaration} (cùng body với `PUT
 * /children/{id}/health-declaration`).
 */
public record ChildProfileChangeCreateRequest(
        @NotNull ProfileChangeKind kind, UpdateChildRequest profile, HealthDeclarationRequest declaration) {}
