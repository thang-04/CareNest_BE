package com.carenest.dto.announcement;

import java.time.Instant;
import java.util.List;

/** Nội dung đầy đủ của thông báo trường cho người nhận (mở từ chuông / banner). */
public record MyAnnouncementResponse(
        Long id,
        String title,
        String body,
        Instant sentAt,
        String senderName,
        List<AnnouncementAttachmentResponse> attachments) {}
