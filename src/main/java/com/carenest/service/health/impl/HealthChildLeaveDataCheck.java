package com.carenest.service.health.impl;

import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.service.child.ChildLeaveDataCheck;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * G94-180/CHD-03 (user chốt 2026-11-25): trẻ có số đo kỳ cân đo hoặc phiếu khám (kể cả nháp) trong ngày thì không cho
 * nghỉ học từ ngày đó. Chỉ dùng cho nghỉ học, không chặn chuyển lớp.
 */
@Component
@RequiredArgsConstructor
public class HealthChildLeaveDataCheck implements ChildLeaveDataCheck {
    private final HealthMeasurementRepository measurementRepository;

    @Override
    public boolean hasData(Long childId, LocalDate date) {
        return measurementRepository.existsByChildIdAndDate(childId, date);
    }
}
