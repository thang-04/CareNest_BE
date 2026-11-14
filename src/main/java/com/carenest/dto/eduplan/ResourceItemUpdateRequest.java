package com.carenest.dto.eduplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Sửa học liệu: trường null = giữ nguyên; {@code clearAgeGroup} = dùng cho mọi nhóm tuổi; {@code themes}/{@code goals} thêm
 * nhãn tay; {@code removeTagIds} bỏ nhãn tay (nhãn tự thêm không bỏ được).
 */
public record ResourceItemUpdateRequest(
        @Size(min = 1, max = 255) String title,
        Long ageGroupId,
        Boolean clearAgeGroup,
        @Size(max = 50) List<@Valid ThemeTag> themes,
        @Size(max = 50) List<@Valid GoalTag> goals,
        @Size(max = 100) List<Long> removeTagIds) {

    public record ThemeTag(Long themeId, @Size(max = 500) String name, Long schoolYearId) {}

    public record GoalTag(@NotNull Long goalId) {}
}
