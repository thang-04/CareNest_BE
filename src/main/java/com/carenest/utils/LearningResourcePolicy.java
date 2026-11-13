package com.carenest.utils;

import com.carenest.exception.GlobalException;

public final class LearningResourcePolicy {
    private LearningResourcePolicy() {}

    public static String safeName(String name) {
        String clean = (name == null ? "file" : name).replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .strip();
        if (clean.isBlank() || clean.length() > 255) throw GlobalException.of(ApiCode.FILE_NAME_INVALID);
        return clean;
    }
}
