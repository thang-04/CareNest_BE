package com.carenest.service.meals.impl;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Khóa so sánh tên thực phẩm/món: cùng cách chuẩn hóa với màn danh mục trên Web (bỏ dấu, không phân biệt hoa thường). */
final class CatalogNames {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private CatalogNames() {}

    static String key(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String plain = COMBINING_MARKS
                .matcher(Normalizer.normalize(lower, Normalizer.Form.NFD))
                .replaceAll("");
        return SPACES.matcher(plain.trim()).replaceAll(" ");
    }
}
