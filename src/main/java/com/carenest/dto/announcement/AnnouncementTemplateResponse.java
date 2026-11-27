package com.carenest.dto.announcement;

import java.time.Instant;

/** Mẫu soạn sẵn; {@code canEdit}: người xem sửa/xóa được (PHT chỉ mẫu điểm trường mình). */
public record AnnouncementTemplateResponse(
        Long id, String name, String title, String body, Long campusId, Instant updatedAt, boolean canEdit) {}
