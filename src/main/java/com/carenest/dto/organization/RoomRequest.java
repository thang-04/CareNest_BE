package com.carenest.dto.organization;

import com.carenest.utils.enums.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code room} = mã/số phòng (vd. P.101), duy nhất trong điểm trường; phòng học bắt buộc có sức chứa. */
public record RoomRequest(
        @NotNull Long campusId,
        @NotBlank @Size(max = 20) String room,
        @NotBlank @Size(max = 50) String name,
        @NotNull RoomType type,
        @Min(1) @Max(Short.MAX_VALUE) Integer capacity,
        Long managerUserId,
        @Size(max = 500) String note) {}
