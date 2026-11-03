package com.carenest.dto.meals;

import com.carenest.utils.enums.CatalogStatus;
import jakarta.validation.constraints.NotNull;

/** Ngừng dùng / dùng lại một dòng danh mục bán trú (thay cho xóa). */
public record CatalogStatusRequest(@NotNull CatalogStatus status) {}
