package com.carenest.dto.organization;

import com.carenest.utils.enums.AppearanceMode;
import java.time.Instant;
import java.util.List;

/** updatedBy là họ tên người lưu gần nhất; null cùng updatedAt khi trường chưa lưu lần nào (đang dùng mặc định). */
public record AppearanceResponse(
        AppearanceMode mode, String pinnedSceneId, List<String> enabledSceneIds, String updatedBy, Instant updatedAt) {}
