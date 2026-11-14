package com.carenest.service.announcement.impl;

import org.springframework.stereotype.Component;

/** Mẫu có đang được lịch gửi lặp dùng không; lịch lặp (G94-291) thay bằng truy vấn thật. */
@Component
class TemplateUsage {

    boolean isUsed(Long templateId) {
        return false;
    }
}
