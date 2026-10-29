package com.carenest.dto.child;

import com.carenest.utils.enums.ChildHistoryAction;
import java.time.Instant;

public record ChildHistoryResponse(
        ChildHistoryAction action, Long userId, String userFullName, Instant at, String note) {}
