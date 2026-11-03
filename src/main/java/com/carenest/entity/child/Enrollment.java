package com.carenest.entity.child;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "enrollments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    // Người xếp lớp (V10); null ở enrollment cũ và enrollment do chuyển năm học tạo
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Enrollment start(Long childId, Long classroomId, LocalDate validFrom) {
        Enrollment enrollment = new Enrollment();
        enrollment.childId = childId;
        enrollment.classroomId = classroomId;
        enrollment.validFrom = validFrom;
        return enrollment;
    }

    /** Xếp lớp do người thao tác (tiếp nhận có lớp, xếp/chuyển lớp): ghi người xếp. */
    public static Enrollment start(Long childId, Long classroomId, LocalDate validFrom, Long createdBy) {
        Enrollment enrollment = start(childId, classroomId, validFrom);
        enrollment.createdBy = createdBy;
        return enrollment;
    }

    /** Trẻ rời lớp: còn học tới hết ngày lastDay (không xóa để giữ lịch sử lớp). */
    public void endOn(LocalDate lastDay) {
        this.validTo = lastDay;
    }
}
