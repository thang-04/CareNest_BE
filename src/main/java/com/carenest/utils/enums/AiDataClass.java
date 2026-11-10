package com.carenest.utils.enums;

/** Loại dữ liệu task gửi cho provider; quyết định provider nào được phép nhận (AI-04, ADR-0007). */
public enum AiDataClass {
    /** Danh mục công khai của trường (món, định mức, giá); không chứa dữ liệu trẻ. */
    PUBLIC_CATALOG,
    /** Dẫn xuất từ dữ liệu trẻ dù đã ẩn danh; chỉ gửi provider không dùng dữ liệu để train. */
    CHILD_DERIVED
}
