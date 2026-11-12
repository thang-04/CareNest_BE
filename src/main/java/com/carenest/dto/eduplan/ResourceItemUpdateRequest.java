package com.carenest.dto.eduplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Sửa học liệu: trường null = giữ nguyên; {@code clearAgeGroup} = dùng cho mọi nhóm tuổi; {@code tags} thay toàn bộ nhãn. */
public record ResourceItemUpdateRequest(
        @Size(min = 1, max = 255) String title,
        Long ageGroupId,
        Boolean clearAgeGroup,
        Boolean hidden,
        @Valid ResourceTagsRequest tags) {}
