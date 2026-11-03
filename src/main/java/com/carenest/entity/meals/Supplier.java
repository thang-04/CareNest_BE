package com.carenest.entity.meals;

import com.carenest.utils.enums.CatalogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Nhà cung cấp dùng chung hai điểm trường (NUT-12). {@code idCard} là dữ liệu cá nhân. */
@Getter
@Entity
@Table(name = "suppliers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 300)
    private String address = "";

    @Column(name = "id_card", length = 20)
    private String idCard;

    @Column(name = "delivery_person", nullable = false, length = 100)
    private String deliveryPerson = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Supplier create(Long createdBy) {
        Supplier supplier = new Supplier();
        supplier.createdBy = createdBy;
        supplier.updatedBy = createdBy;
        return supplier;
    }

    public void changeInfo(
            String name, String phone, String address, String idCard, String deliveryPerson, Long updatedBy) {
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.idCard = idCard;
        this.deliveryPerson = deliveryPerson;
        this.updatedBy = updatedBy;
    }

    public void changeStatus(CatalogStatus status, Long updatedBy) {
        this.status = status;
        this.updatedBy = updatedBy;
    }
}
