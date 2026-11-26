package com.carenest.entity.meals;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bản cân đối khẩu phần một ngày cho cả trường; {@code round} 1 theo số trẻ dự kiến, 2 theo suất đã chốt. */
@Getter
@Entity
@Table(name = "day_balances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DayBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "balance_date", nullable = false)
    private LocalDate balanceDate;

    @Column(name = "round", nullable = false)
    private short round;

    /** Bước làm tròn kg/lít đã áp dụng; null = chưa làm tròn. */
    @Column(name = "rounding_step")
    private BigDecimal roundingStep;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "saved_by", nullable = false)
    private Long savedBy;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @ElementCollection
    @CollectionTable(name = "day_balance_counts", joinColumns = @JoinColumn(name = "day_balance_id"))
    private List<DayBalanceCount> counts = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "day_balance_lines", joinColumns = @JoinColumn(name = "day_balance_id"))
    private List<DayBalanceLine> lines = new ArrayList<>();

    public static DayBalance of(LocalDate date, int round) {
        DayBalance balance = new DayBalance();
        balance.balanceDate = date;
        balance.round = (short) round;
        return balance;
    }

    /** Ghi đè toàn bộ nội dung; {@code savedAt} đổi nên phiên bản dòng luôn tăng. */
    public void replace(
            Collection<DayBalanceCount> newCounts,
            Collection<DayBalanceLine> newLines,
            BigDecimal newRoundingStep,
            Long userId,
            Instant at) {
        counts.clear();
        counts.addAll(newCounts);
        lines.clear();
        lines.addAll(newLines);
        roundingStep = newRoundingStep;
        savedBy = userId;
        savedAt = at;
    }
}
