package com.carenest.dto.account;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Role phân công lấy theo role tài khoản; PHT/bếp ⇒ campusId, GV ⇒ classroomId. validFrom bỏ trống ⇒ hôm nay.
 * substitute = GV dạy thay (AUTH-24, chỉ GV, bắt buộc validTo); bỏ trống ⇒ dạy chính. replacedTeacherId = dạy thay cho GV
 * đứng lớp nào (bắt buộc khi dạy thay, Cần chốt 10/11/2026).
 */
public record CreateStaffAssignmentRequest(
        @NotNull Long userId,
        Long campusId,
        Long classroomId,
        LocalDate validFrom,
        LocalDate validTo,
        Boolean substitute,
        Long replacedTeacherId) {

    public CreateStaffAssignmentRequest(
            Long userId, Long campusId, Long classroomId, LocalDate validFrom, LocalDate validTo, Boolean substitute) {
        this(userId, campusId, classroomId, validFrom, validTo, substitute, null);
    }
}
