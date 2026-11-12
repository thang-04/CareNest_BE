package com.carenest.dto.eduplan;

import jakarta.validation.constraints.Size;
import java.util.List;

/** EDU-87: nhãn gắn cho học liệu khi tạo từ một mục hoạt động hoặc khi sửa ở trang kho. */
public record ResourceTagsRequest(
        Long themeId, @Size(max = 500) String themeName, @Size(max = 50) List<Long> goalIds) {

    public List<Long> goals() {
        return goalIds == null ? List.of() : goalIds;
    }
}
