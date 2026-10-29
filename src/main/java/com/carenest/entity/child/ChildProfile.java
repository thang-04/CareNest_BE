package com.carenest.entity.child;

import com.carenest.utils.enums.YesNo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Hành chính của trẻ, 1–1 (CHD-05; dữ liệu nhạy cảm, phiếu khám đọc từ đây). Nguồn chi trả luôn BHYT. */
@Getter
@Entity
@Table(name = "child_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfile {

    public static final String PAYMENT_SOURCE = "BHYT";

    @Id
    @Column(name = "child_id")
    private Long childId;

    @Column(name = "id_number", length = 20)
    private String idNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private YesNo premature;

    @Column(name = "gestation_weeks")
    private Short gestationWeeks;

    @Column(nullable = false, length = 100)
    private String ethnicity;

    @Column(nullable = false, length = 100)
    private String beneficiary;

    @Column(name = "payment_source", nullable = false, length = 20)
    private String paymentSource;

    @Column(name = "blood_type", length = 100)
    private String bloodType;

    @Column(name = "address_province", nullable = false, length = 100)
    private String addressProvince;

    @Column(name = "address_ward", nullable = false, length = 100)
    private String addressWard;

    @Column(name = "address_street", nullable = false, length = 300)
    private String addressStreet;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChildProfile create(
            Long childId,
            String idNumber,
            YesNo premature,
            Short gestationWeeks,
            String ethnicity,
            String beneficiary,
            String bloodType,
            String addressProvince,
            String addressWard,
            String addressStreet) {
        ChildProfile profile = new ChildProfile();
        profile.childId = childId;
        profile.idNumber = idNumber;
        profile.premature = premature;
        profile.gestationWeeks = gestationWeeks;
        profile.ethnicity = ethnicity;
        profile.beneficiary = beneficiary;
        profile.paymentSource = PAYMENT_SOURCE;
        profile.bloodType = bloodType;
        profile.addressProvince = addressProvince;
        profile.addressWard = addressWard;
        profile.addressStreet = addressStreet;
        return profile;
    }
}
