package com.carenest.entity.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Số suất một lớp của một bữa; pending = số mới đang chờ xác nhận lại. */
@Getter
@Entity
@Table(name = "meal_count_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealCountLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "meal_count_id", nullable = false)
    private MealCount mealCount;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(nullable = false)
    private boolean pending;

    @Column(nullable = false)
    private int present;

    @Column(nullable = false)
    private int absent;

    @Column(nullable = false)
    private int missing;

    @Column(nullable = false)
    private int normal;

    @Column(nullable = false)
    private int substitute;

    // NUT-02: suất thay thế do PHT/HT điều chỉnh tay ⇒ điểm danh đổi không tính lại theo hồ sơ dị ứng
    @Column(name = "substitute_adjusted", nullable = false)
    private boolean substituteAdjusted;

    // Điểm danh đổi sau khi suất thay thế đã điều chỉnh tay ⇒ người duyệt kiểm tra lại số thay thế
    @Column(name = "attendance_changed", nullable = false)
    private boolean attendanceChanged;

    static MealCountLine of(
            MealCount count, Long classroomId, boolean pending, int present, int absent, int missing, int normal) {
        MealCountLine line = new MealCountLine();
        line.mealCount = count;
        line.classroomId = classroomId;
        line.pending = pending;
        line.present = present;
        line.absent = absent;
        line.missing = missing;
        line.normal = normal;
        line.substitute = 0;
        return line;
    }

    MealCountLine copyAsPending() {
        MealCountLine copy = of(mealCount, classroomId, true, present, absent, missing, normal);
        copy.substitute = substitute;
        copy.substituteAdjusted = substituteAdjusted;
        copy.attendanceChanged = attendanceChanged;
        return copy;
    }

    boolean sameAs(int present, int absent, int missing, int normal, int substitute) {
        return this.present == present
                && this.absent == absent
                && this.missing == missing
                && this.normal == normal
                && this.substitute == substitute;
    }

    void setFigures(int present, int absent, int missing, int normal, int substitute) {
        this.present = present;
        this.absent = absent;
        this.missing = missing;
        this.normal = normal;
        this.substitute = substitute;
    }

    void setServings(int normal, int substitute) {
        this.normal = normal;
        this.substitute = substitute;
    }

    void markSubstituteAdjusted() {
        this.substituteAdjusted = true;
        this.attendanceChanged = false;
    }

    void markAttendanceChanged() {
        this.attendanceChanged = true;
    }

    void copyFlagsFrom(MealCountLine other) {
        this.substituteAdjusted = other.substituteAdjusted;
        this.attendanceChanged = other.attendanceChanged;
    }

    void clearAttendanceChanged() {
        this.attendanceChanged = false;
    }
}
