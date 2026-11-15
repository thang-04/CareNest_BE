package com.carenest.utils.enums;

import java.util.Locale;

/** Loại tệp trong kho; thư mục, giới hạn, định dạng của từng loại nằm ở {@code carenest.storage.categories}. */
public enum FileCategory {
    USER_AVATAR,
    CHILD_AVATAR,
    PICKUP,
    PLAN_YEAR_GOAL,
    PLAN_THEME,
    PLAN_LESSON,
    MEAL,
    FOOD_SAMPLE,
    IMPORT,
    LEARNING_RESOURCE,
    USER_SIGNATURE,
    INSPECTION_SIGNATURE;

    /** Khóa cấu hình, vd. {@code learning-resource}. */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
