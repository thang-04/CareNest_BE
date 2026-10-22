package com.carenest.entity.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Entity
@Table(name = "campuses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "short_name", nullable = false, length = 50)
    private String shortName;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Campus create(String code, String name, String shortName, String address, String phone) {
        Campus campus = new Campus();
        campus.changeInfo(code, name, shortName, address, phone);
        return campus;
    }

    public void changeInfo(String code, String name, String shortName, String address, String phone) {
        this.code = code;
        this.name = name;
        this.shortName = shortName;
        this.address = address;
        this.phone = phone;
    }

    public void markMain(boolean main) {
        this.main = main;
    }
}
