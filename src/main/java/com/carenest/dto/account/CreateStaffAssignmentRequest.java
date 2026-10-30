package com.carenest.dto.account;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Role phân công lấy theo role tài khoản; PHT/bếp ⇒ campusId, GV ⇒ classroomId. validFrom bỏ trống ⇒ hôm nay. */
public record CreateStaffAssignmentRequest(
        @NotNull Long userId, Long campusId, Long classroomId, LocalDate validFrom, LocalDate validTo) {}
