package com.carenest.utils.enums;

import lombok.Getter;

/**
 * Chất gây dị ứng trong danh mục cố định (HLT-06); thực phẩm gắn chất để món suy ra chất dị ứng.
 *
 * <p>Thứ tự khai báo là thứ tự hiển thị. {@code id} giữ cố định vì API nhận và trả mã số này
 * ({@code FoodRequest.allergenIds}), không được đổi số của chất đã có.
 */
@Getter
public enum Allergen {
    SHRIMP(1L, "Tôm"),
    CRAB(2L, "Cua"),
    FISH(3L, "Cá"),
    EGG(4L, "Trứng"),
    COW_MILK(5L, "Sữa bò"),
    PEANUT(6L, "Đậu phộng"),
    SOY(7L, "Đậu nành"),
    WHEAT(8L, "Lúa mì"),
    SESAME(9L, "Mè (vừng)");

    private final Long id;
    private final String name;

    Allergen(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getCode() {
        return name();
    }

    /** Tìm chất theo mã số API; trả null khi không có. */
    public static Allergen fromId(Long id) {
        for (Allergen allergen : values()) {
            if (allergen.id.equals(id)) {
                return allergen;
            }
        }
        return null;
    }
}
