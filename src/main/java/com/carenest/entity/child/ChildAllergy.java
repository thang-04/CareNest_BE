package com.carenest.entity.child;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Dị ứng của khai báo, có hiệu lực ngay khi lưu: bếp, điểm danh đọc bảng này (CHD-06); thứ tự theo id. */
@Getter
@Entity
@Table(name = "child_allergies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildAllergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    @Column(nullable = false, length = 100)
    private String name;

    public static ChildAllergy of(Long childId, String name) {
        ChildAllergy allergy = new ChildAllergy();
        allergy.childId = childId;
        allergy.name = name;
        return allergy;
    }
}
