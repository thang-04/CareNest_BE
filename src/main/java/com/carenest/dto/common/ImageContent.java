package com.carenest.dto.common;

/** Nội dung một ảnh để trả cho client (ảnh kết quả khám chuyên khoa, ảnh chân dung phụ huynh). */
public record ImageContent(byte[] content, String contentType) {}
