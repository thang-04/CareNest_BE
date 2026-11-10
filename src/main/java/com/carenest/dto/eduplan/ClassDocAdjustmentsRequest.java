package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** "Kế hoạch điều chỉnh và bổ sung" của các kế hoạch ngày trong giáo án tuần: ngày (yyyy-mm-dd) → văn bản. */
public record ClassDocAdjustmentsRequest(@NotNull Map<String, String> adjustments) {}
