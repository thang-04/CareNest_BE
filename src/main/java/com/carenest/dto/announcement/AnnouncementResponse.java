package com.carenest.dto.announcement;

import com.carenest.utils.enums.AnnouncementStatus;
import java.time.Instant;
import java.util.List;

/** Thông báo của trường cho BGH: số người nhận, không có ai đã đọc (PAR-05, FR-015). */
public record AnnouncementResponse(
        Long id,
        String title,
        String body,
        RecipientTargetRequest target,
        AnnouncementStatus status,
        Instant sendAt,
        Instant sentAt,
        int recipientCount,
        Long senderId,
        String senderName,
        List<AnnouncementAttachmentResponse> attachments,
        // Còn hẹn giờ và người xem được sửa/hủy
        boolean canEdit,
        boolean canDelete) {}
