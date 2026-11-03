package com.carenest.entity.child;

import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "children")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mã trẻ HS + 2 số năm tiếp nhận + số thứ tự (V10, child_code_sequences)
    @Column(nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    // Null chỉ ở dữ liệu demo có trước V10 (ck_children_complete NOT VALID)
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChildStatus status;

    // Điểm trường của trẻ chưa có lớp (CHD-02); có lớp thì điểm trường theo lớp (enrollment)
    @Column(name = "intake_campus_id")
    private Long intakeCampusId;

    @Column(name = "enrolled_on", nullable = false, updatable = false)
    private LocalDate enrolledOn;

    @Column(name = "left_on")
    private LocalDate leftOn;

    @Column(name = "left_reason", length = 300)
    private String leftReason;

    @Column(name = "readmitted_on")
    private LocalDate readmittedOn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Trẻ vừa tiếp nhận: có lớp ⇒ Đang học (điểm trường theo lớp); chưa có lớp ⇒ Chờ xếp lớp ở điểm trường tiếp nhận
     * (CHD-02, CHD-03).
     */
    public static Child enroll(
            String code,
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            boolean placed,
            Long intakeCampusId,
            LocalDate enrolledOn) {
        Child child = new Child();
        child.code = code;
        child.fullName = fullName;
        child.dateOfBirth = dateOfBirth;
        child.gender = gender;
        child.status = placed ? ChildStatus.ACTIVE : ChildStatus.PENDING_PLACEMENT;
        child.intakeCampusId = placed ? null : intakeCampusId;
        child.enrolledOn = enrolledOn;
        return child;
    }

    /** Mở lại hồ sơ trẻ đã nghỉ (CHD-03): về Chờ xếp lớp ở điểm trường cũ, bỏ ngày/lý do nghỉ, giữ mã và dữ liệu. */
    public void readmit(Long campusId, LocalDate today) {
        this.status = ChildStatus.PENDING_PLACEMENT;
        this.intakeCampusId = campusId;
        this.leftOn = null;
        this.leftReason = null;
        this.readmittedOn = today;
    }
}
