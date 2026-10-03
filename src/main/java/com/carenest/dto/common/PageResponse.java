package com.carenest.dto.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Dữ liệu danh sách phân trang trả trong {@code data}; không trả trực tiếp {@link Page} của Spring. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
