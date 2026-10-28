package com.carenest.entity.meals;

import com.carenest.utils.enums.MealCountStatus;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.StaffRole;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Sổ báo ăn một bữa của một điểm trường trong ngày, tổng hợp lúc khóa điểm danh (NUT-01). Đã xác nhận thì không sửa
 * ngầm: thay đổi ghi vào các dòng pending, bếp vẫn dùng dòng đã xác nhận tới khi HT/PHT xác nhận lại (NUT-03, ADR-0005).
 */
@Getter
@Entity
@Table(name = "meal_counts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MealCountStatus status;

    @Column(name = "needs_reconfirmation", nullable = false)
    private boolean needsReconfirmation;

    @Column(name = "aggregated_at", nullable = false)
    private Instant aggregatedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmed_by_role", length = 20)
    private StaffRole confirmedByRole;

    @OneToMany(mappedBy = "mealCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<MealCountLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static MealCount aggregate(
            Long campusId, Long schoolYearId, LocalDate date, MealSession session, Instant at) {
        MealCount count = new MealCount();
        count.campusId = campusId;
        count.schoolYearId = schoolYearId;
        count.date = date;
        count.session = session;
        count.status = MealCountStatus.PENDING_CONFIRMATION;
        count.aggregatedAt = at;
        return count;
    }

    public void addLine(Long classroomId, int present, int absent, int missing, int normal) {
        lines.add(MealCountLine.of(this, classroomId, false, present, absent, missing, normal));
    }

    public boolean isConfirmed() {
        return status == MealCountStatus.CONFIRMED;
    }

    public boolean hasPending() {
        return lines.stream().anyMatch(MealCountLine::isPending);
    }

    /** Số đã chốt (bếp thấy) hoặc, với người duyệt, số mới đang chờ xác nhận lại. */
    public List<MealCountLine> visibleLines(boolean reviewer) {
        boolean pending = reviewer && hasPending();
        return lines.stream().filter(l -> l.isPending() == pending).toList();
    }

    /** Trạng thái người duyệt thấy: còn số chờ xác nhận lại ⇒ chờ xác nhận. */
    public MealCountStatus reviewStatus() {
        return hasPending() ? MealCountStatus.PENDING_CONFIRMATION : status;
    }

    public Optional<MealCountLine> visibleLine(Long classroomId) {
        return visibleLines(true).stream()
                .filter(l -> l.getClassroomId().equals(classroomId))
                .findFirst();
    }

    /**
     * Dòng để ghi thay đổi của một lớp: sổ đã xác nhận mà chưa có số chờ ⇒ chép số đã chốt sang pending và đánh dấu
     * cần xác nhận lại; sổ chưa xác nhận ⇒ sửa thẳng.
     */
    private Optional<MealCountLine> workingLine(Long classroomId) {
        if (isConfirmed() && !hasPending()) {
            List<MealCountLine> copies =
                    lines.stream().map(MealCountLine::copyAsPending).toList();
            lines.addAll(copies);
            needsReconfirmation = true;
        }
        return visibleLine(classroomId);
    }

    /** Điều chỉnh của HT/PHT; trả về chênh lệch (0 nếu không đổi). */
    public int[] adjust(Long classroomId, int normal, int substitute) {
        MealCountLine current = visibleLine(classroomId).orElse(null);
        if (current == null || (current.getNormal() == normal && current.getSubstitute() == substitute)) {
            return new int[] {0, 0};
        }
        MealCountLine line = workingLine(classroomId).orElseThrow();
        int[] delta = {normal - line.getNormal(), substitute - line.getSubstitute()};
        line.setServings(normal, substitute);
        return delta;
    }

    /** Điểm danh của lớp đổi sau khi tổng hợp (ATT-07); trả về true nếu số của lớp thay đổi. */
    public boolean replaceClassFigures(Long classroomId, int present, int absent, int missing, int normal) {
        MealCountLine current = visibleLine(classroomId).orElse(null);
        if (current == null || current.sameAs(present, absent, missing, normal, 0)) {
            return false;
        }
        workingLine(classroomId).orElseThrow().setFigures(present, absent, missing, normal, 0);
        return true;
    }

    /** Xác nhận (hoặc xác nhận lại): số chờ thành số chốt, bếp dùng từ đây. */
    public void confirm(Long userId, StaffRole role, Instant at) {
        List<MealCountLine> pending =
                lines.stream().filter(MealCountLine::isPending).toList();
        // Chép số chờ vào dòng đã chốt rồi bỏ dòng chờ (tránh trùng khóa (sổ, lớp, pending) khi flush)
        pending.forEach(p -> lines.stream()
                .filter(l -> !l.isPending() && l.getClassroomId().equals(p.getClassroomId()))
                .findFirst()
                .ifPresent(l ->
                        l.setFigures(p.getPresent(), p.getAbsent(), p.getMissing(), p.getNormal(), p.getSubstitute())));
        lines.removeAll(pending);
        status = MealCountStatus.CONFIRMED;
        needsReconfirmation = false;
        confirmedBy = userId;
        confirmedByRole = role;
        confirmedAt = at;
    }
}
