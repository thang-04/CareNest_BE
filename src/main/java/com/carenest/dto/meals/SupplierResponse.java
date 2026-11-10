package com.carenest.dto.meals;

import com.carenest.utils.enums.CatalogStatus;

/** {@code idCard} chỉ trả cho người có quyền sửa danh mục; người chỉ xem nhận null (dữ liệu cá nhân — NUT-12). */
public record SupplierResponse(
        Long id,
        String name,
        String phone,
        String address,
        String idCard,
        String deliveryPerson,
        CatalogStatus status) {}
