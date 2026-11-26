package com.carenest.service.child.impl;

import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.service.organization.SchoolYearEndUsage;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EnrollmentSchoolYearEndUsage implements SchoolYearEndUsage {
    private final EnrollmentRepository enrollmentRepository;

    @Override
    public Optional<String> usageAfter(Long schoolYearId, LocalDate newEnd) {
        long scheduled = enrollmentRepository.countOfYearStartingAfter(schoolYearId, newEnd);
        return scheduled == 0 ? Optional.empty() : Optional.of(scheduled + " lịch vào lớp");
    }
}
