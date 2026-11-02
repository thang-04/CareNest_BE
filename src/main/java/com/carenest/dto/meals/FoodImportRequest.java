package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Nhập hàng loạt: FE đọc file rồi gửi từng dòng; một dòng lỗi thì không dòng nào được lưu. */
public record FoodImportRequest(@NotEmpty @Size(max = 500) List<@Valid FoodRequest> items) {}
