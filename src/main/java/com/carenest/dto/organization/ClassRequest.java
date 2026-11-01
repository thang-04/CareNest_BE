package com.carenest.dto.organization;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lớp của một năm học; sĩ số tối đa ≤ min(sĩ số tối đa của nhóm tuổi, sức chứa phòng).
 * locationId = id phòng (/locations); nhận thêm tên cũ schoolYear, roomId cho FE đang dùng.
 */
public record ClassRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull @JsonAlias("schoolYear") Long schoolYearId,
        @NotNull Long campusId,
        @NotNull Long ageGroupId,
        @NotNull @JsonAlias("roomId") Long locationId,
        @NotNull @Min(1) @Max(Short.MAX_VALUE) Integer capacity) {}
