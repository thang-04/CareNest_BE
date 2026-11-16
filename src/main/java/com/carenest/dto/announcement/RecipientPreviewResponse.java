package com.carenest.dto.announcement;

import com.carenest.utils.enums.RecipientRole;
import java.util.Map;

/** Xem trước số người nhận theo vai trò (FR-011); {@code total} đã bỏ trùng. */
public record RecipientPreviewResponse(int total, Map<RecipientRole, Integer> byRole) {}
