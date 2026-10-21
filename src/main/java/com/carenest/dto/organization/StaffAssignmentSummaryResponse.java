package com.carenest.dto.organization;

import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;

public record StaffAssignmentSummaryResponse(
        StaffRole staffRole, Long campusId, Long classroomId, LocalDate validFrom, LocalDate validTo) {}
