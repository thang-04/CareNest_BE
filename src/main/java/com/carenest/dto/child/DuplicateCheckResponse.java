package com.carenest.dto.child;

import com.carenest.utils.enums.DuplicateChildKind;
import java.time.LocalDate;

/**
 * Hồ sơ trùng khi tiếp nhận (CHD-04). {@code sameCampus} false ⇒ hồ sơ ở điểm trường người hỏi không quản lý: chỉ báo
 * có trùng, các trường còn lại null (không lộ hồ sơ).
 */
public record DuplicateCheckResponse(
        DuplicateChildKind kind,
        boolean sameCampus,
        Long id,
        String code,
        String fullName,
        LocalDate leftAt,
        Boolean canReadmit) {

    public static DuplicateCheckResponse otherCampus(DuplicateChildKind kind) {
        return new DuplicateCheckResponse(kind, false, null, null, null, null, null);
    }
}
