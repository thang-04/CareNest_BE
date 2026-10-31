package com.carenest.dto.health;

/** Nội dung một ảnh kết quả khám chuyên khoa để trả cho trình duyệt. */
public record HealthImageContent(byte[] content, String contentType) {}
