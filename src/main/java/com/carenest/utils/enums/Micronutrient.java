package com.carenest.utils.enums;

/**
 * 8 vi chất theo bảng Viện Dinh dưỡng (NUT-26). {@code key} trùng tên trường JSON và khóa dòng định mức; đơn vị mg,
 * riêng vitamin A là µg retinol equivalent.
 */
public enum Micronutrient {
    CALCIUM("calcium", "Canxi", "mg"),
    PHOSPHORUS("phosphorus", "Photpho", "mg"),
    IRON("iron", "Sắt", "mg"),
    VITAMIN_A("vitaminA", "Vitamin A", "µg"),
    VITAMIN_B1("vitaminB1", "Vitamin B1", "mg"),
    VITAMIN_B2("vitaminB2", "Vitamin B2", "mg"),
    VITAMIN_PP("vitaminPp", "Vitamin PP", "mg"),
    VITAMIN_C("vitaminC", "Vitamin C", "mg");

    private final String key;
    private final String label;
    private final String unit;

    Micronutrient(String key, String label, String unit) {
        this.key = key;
        this.label = label;
        this.unit = unit;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }
}
