package com.carenest.dto.organization;

import com.carenest.utils.enums.AppearanceMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** pinnedSceneId bắt buộc khi mode = FIXED; các chế độ khác có thể bỏ trống. */
public record AppearanceRequest(
        @NotNull AppearanceMode mode,
        String pinnedSceneId,
        @NotEmpty List<String> enabledSceneIds) {}
