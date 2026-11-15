package com.carenest.service.announcement.impl;

import com.carenest.repository.announcement.AnnouncementScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Mẫu đang được lịch gửi lặp dùng thì không xóa được (PAR-10). */
@Component
@RequiredArgsConstructor
class TemplateUsage {

    private final AnnouncementScheduleRepository scheduleRepository;

    boolean isUsed(Long templateId) {
        return scheduleRepository.existsByTemplateId(templateId);
    }
}
