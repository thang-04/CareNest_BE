package com.carenest.dto.announcement;

/** Mẫu soạn sẵn; {@code campusId} null = mẫu toàn trường (chỉ HT). */
public record AnnouncementTemplateRequest(String name, String title, String body, Long campusId) {}
