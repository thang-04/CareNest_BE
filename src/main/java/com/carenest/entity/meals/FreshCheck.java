package com.carenest.entity.meals;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lần kiểm nhận hàng tươi của điểm trường–ngày (NUT-12): lần 1 so bản dự trù, lần 2 so nhu cầu theo suất đã chốt. */
@Getter
@Entity
@Table(name = "fresh_checks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreshCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Column(nullable = false)
    private short round;

    @Column(name = "forecast_id")
    private Long forecastId;

    @Column(name = "checked_by")
    private Long checkedBy;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(nullable = false, length = 500)
    private String note = "";

    @OneToMany(mappedBy = "check", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<FreshCheckLine> lines = new ArrayList<>();

    public static FreshCheck empty(Long campusId, LocalDate checkDate, int round) {
        FreshCheck check = new FreshCheck();
        check.campusId = campusId;
        check.checkDate = checkDate;
        check.round = (short) round;
        return check;
    }

    /**
     * Ghi (hoặc ghi đè) kết quả kiểm. Dòng cùng thực phẩm sửa tại chỗ, dòng không còn thì bỏ, dòng mới thì thêm — không
     * xóa rồi chèn lại cùng khóa (kiểm, thực phẩm) trong một lần flush vì Hibernate chèn trước khi xóa.
     */
    public void record(Long forecastId, Long userId, Instant at, String note, List<FreshCheckLine> incoming) {
        this.forecastId = forecastId;
        this.checkedBy = userId;
        this.checkedAt = at;
        this.note = note;
        lines.removeIf(
                existing -> incoming.stream().noneMatch(line -> line.getFoodId().equals(existing.getFoodId())));
        for (FreshCheckLine line : incoming) {
            lines.stream()
                    .filter(existing -> existing.getFoodId().equals(line.getFoodId()))
                    .findFirst()
                    .ifPresentOrElse(existing -> existing.copyFrom(line), () -> {
                        line.attachTo(this);
                        lines.add(line);
                    });
        }
    }
}
