package com.carenest.dto.announcement;

import java.time.Instant;
import java.util.List;

/**
 * Soạn / sửa thông báo của trường. {@code sendAt} null = gửi ngay; có giá trị = hẹn giờ (≤ 30 ngày). Khi sửa,
 * {@code keepAttachmentIds} là các tệp cũ giữ lại; tệp mới gửi kèm multipart.
 */
public record AnnouncementRequest(
        String title, String body, RecipientTargetRequest target, Instant sendAt, List<Long> keepAttachmentIds) {}
