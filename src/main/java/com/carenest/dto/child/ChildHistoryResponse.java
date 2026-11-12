package com.carenest.dto.child;

import com.carenest.utils.enums.ChildHistoryAction;
import java.time.Instant;
import java.util.List;

/** Một dòng lịch sử hồ sơ trẻ; {@code changes} chỉ có ở Sửa hồ sơ (rỗng với các thao tác khác). */
public record ChildHistoryResponse(
        ChildHistoryAction action,
        Long userId,
        String userFullName,
        Instant at,
        String note,
        List<ChildFieldChange> changes) {
    public ChildHistoryResponse(ChildHistoryAction action, Long userId, String userFullName, Instant at, String note) {
        this(action, userId, userFullName, at, note, List.of());
    }
}
