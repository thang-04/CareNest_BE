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

    // G94-180/CHD-03 (user chốt 2026-11-25): lịch nghỉ học — vẫn Đang học tới ngày này, job 00:05 ngày đó mới cho nghỉ
    @Column(name = "scheduled_leave_on")
    private LocalDate scheduledLeaveOn;

    @Column(name = "scheduled_leave_reason", length = 300)
    private String scheduledLeaveReason;

    @Column(name = "scheduled_leave_by")
    private Long scheduledLeaveBy;

    // G94-180/CHD-14: ngày bắt đầu đi học chọn khi tiếp nhận / mở lại hồ sơ; null = hồ sơ cũ
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "photo_key", length = 255)
    private String photoKey;

    @Column(name = "photo_content_type", length = 30)
    private String photoContentType;

    @Column(name = "photo_size_bytes")
    private Integer photoSizeBytes;

    // CHD-13: trẻ ăn ở trường, mặc định có
    @Column(name = "meal_registered", nullable = false)
    private boolean mealRegistered = true;

    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    public void changeMealRegistration(boolean mealRegistered) {
        this.mealRegistered = mealRegistered;
    }

    public String replacePhoto(String key, String contentType, int size, Instant at) {
        String previous = photoKey;
        assignEnrollmentPhoto(key, contentType, size, at);
        return previous;
    }

    public void assignEnrollmentPhoto(String key, String contentType, int size, Instant at) {
        this.photoKey = key;
        this.photoContentType = contentType;
        this.photoSizeBytes = size;
        this.photoUpdatedAt = at;
    }

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
            LocalDate enrolledOn,
            boolean mealRegistered) {
        Child child = new Child();
        child.code = code;
        child.fullName = fullName;
        child.dateOfBirth = dateOfBirth;
        child.gender = gender;
        child.status = placed ? ChildStatus.ACTIVE : ChildStatus.PENDING_PLACEMENT;
        child.intakeCampusId = placed ? null : intakeCampusId;
        child.enrolledOn = enrolledOn;
        child.mealRegistered = mealRegistered;
        return child;
    }

    /** Sửa hồ sơ (G94-180/CHD-08): thông tin cơ bản; mã, trạng thái, ngày tiếp nhận không đổi ở đây. */
    public void updateBasics(String fullName, LocalDate dateOfBirth, Gender gender) {
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
    }

    /**
     * Cho nghỉ học (G94-180/CHD-03): Đã nghỉ từ ngày {@code leftOn}, không xóa dữ liệu. Điểm trường sau đó suy từ lớp
     * cuối cùng hoặc điểm trường tiếp nhận (mở lại hồ sơ về đúng điểm trường).
     */
    public void withdraw(LocalDate leftOn, String reason) {
        this.status = ChildStatus.LEFT;
        this.leftOn = leftOn;
        this.leftReason = reason;
        // Đã nghỉ thì không còn lịch nghỉ (ck_children_scheduled_leave_not_left)
        clearScheduledLeave();
    }

    /** Hẹn nghỉ học từ ngày sau hôm nay (G94-180/CHD-03): trẻ vẫn Đang học, lớp không đổi tới ngày {@code on}. */
    public void scheduleLeave(LocalDate on, String reason, Long byUserId) {
        this.scheduledLeaveOn = on;
        this.scheduledLeaveReason = reason;
        this.scheduledLeaveBy = byUserId;
    }

    /** Bỏ lịch nghỉ học: hủy lịch, hoặc lịch đã áp. */
    public void clearScheduledLeave() {
        this.scheduledLeaveOn = null;
        this.scheduledLeaveReason = null;
        this.scheduledLeaveBy = null;
    }

    /**
     * Xếp / chuyển lớp (CHD-10): Đang học, điểm trường theo lớp (enrollment) nên bỏ điểm trường tiếp nhận như tiếp nhận có
     * lớp — giữ lại thì trẻ nghỉ sau khi chuyển điểm trường bị suy sai điểm trường (ưu tiên điểm trường tiếp nhận).
     */
    public void placeInClass() {
        this.status = ChildStatus.ACTIVE;
        this.intakeCampusId = null;
    }

    /** Ghi điểm trường của trẻ khi bỏ lớp duy nhất (nghỉ ngay ngày vào lớp): hồ sơ đã nghỉ vẫn thuộc điểm trường đó. */
    public void keepCampus(Long campusId) {
        this.intakeCampusId = campusId;
    }

    /** Hủy lớp chưa bắt đầu khi trẻ chưa học lớp nào (G94-180/CHD-14): về Chờ xếp lớp ở điểm trường của lớp đó. */
    public void awaitPlacement(Long campusId) {
        this.status = ChildStatus.PENDING_PLACEMENT;
        this.intakeCampusId = campusId;
    }

    /** G94-180/CHD-14: chờ xếp lớp mà đã tới ngày bắt đầu đi học (Web gắn nhãn, đưa lên đầu, nhắc HT / PHT). */
    public boolean isStartDue(LocalDate today) {
        return status == ChildStatus.PENDING_PLACEMENT && startDate != null && !startDate.isAfter(today);
    }

    /** Ngày bắt đầu đi học (G94-180/CHD-14), ghi khi tiếp nhận và khi mở lại hồ sơ. */
    public void startOn(LocalDate startDate) {
        this.startDate = startDate;
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
