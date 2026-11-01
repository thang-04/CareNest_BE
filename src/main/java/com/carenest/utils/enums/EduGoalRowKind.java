package com.carenest.utils.enums;

/** Loại dòng bảng mục tiêu; giá trị lưu DB viết thường như UI: sub (lĩnh vực con), group (nhóm KQMĐ), goal (MT n). */
public enum EduGoalRowKind {
    SUB("sub"),
    GROUP("group"),
    GOAL("goal");

    private final String code;

    EduGoalRowKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static EduGoalRowKind fromCode(String code) {
        for (EduGoalRowKind kind : values()) {
            if (kind.code.equals(code)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown goal row kind: " + code);
    }
}
