package com.carenest.dto.organization;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

/** userId = null ⇒ gỡ tổ trưởng của nhóm tuổi. Nhận thêm tên schoolYear như FE đang gửi. */
public record TeamLeaderRequest(
        @NotNull @JsonAlias("schoolYear") Long schoolYearId,
        @NotNull Long ageGroupId,
        Long userId) {}
