package com.carenest.service.attendance.impl;

import com.carenest.repository.attendance.LeaveRequestRepository;
import com.carenest.service.organization.SchoolYearEndUsage;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LeaveSchoolYearEndUsage implements SchoolYearEndUsage {
    private final LeaveRequestRepository leaveRequestRepository;

    @Override
    public Optional<String> usageAfter(Long schoolYearId, LocalDate newEnd) {
        long leaves = leaveRequestRepository.countOfYearAfter(schoolYearId, newEnd);
        return leaves == 0 ? Optional.empty() : Optional.of(leaves + " đơn nghỉ");
    }
}
