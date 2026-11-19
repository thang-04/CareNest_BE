package com.carenest.dto.announcement;

/** Tệp đính kèm; tải qua {@code GET /announcement-attachments/{id}/content}. */
public record AnnouncementAttachmentResponse(Long id, String fileName, String contentType, long sizeBytes) {}
