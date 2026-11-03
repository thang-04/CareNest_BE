package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Nhập / sửa hàng loạt: FE đọc file rồi gửi từng dòng; có {@code id} là sửa thực phẩm đã có, không có là thêm mới.
 * Một dòng lỗi thì không dòng nào được lưu.
 */
public record FoodImportRequest(@NotEmpty @Size(max = 500) List<@Valid Item> items) {

    public record Item(Long id, @NotNull @Valid FoodRequest data) {}
}
