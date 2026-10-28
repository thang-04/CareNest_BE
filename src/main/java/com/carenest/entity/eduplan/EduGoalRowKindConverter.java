package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduGoalRowKind;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Lưu kind dạng viết thường (sub/group/goal) khớp CHECK của bảng edu_goal_rows. */
@Converter
public class EduGoalRowKindConverter implements AttributeConverter<EduGoalRowKind, String> {

    @Override
    public String convertToDatabaseColumn(EduGoalRowKind kind) {
        return kind == null ? null : kind.code();
    }

    @Override
    public EduGoalRowKind convertToEntityAttribute(String code) {
        return code == null ? null : EduGoalRowKind.fromCode(code);
    }
}
