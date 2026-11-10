package com.carenest.entity.meals;

import com.carenest.utils.enums.FreshCheckResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Kết quả kiểm một thực phẩm: lượng cần nhận lúc ghi, số thực nhận (không bắt buộc) và đủ/thiếu/thừa PHT chọn. */
@Getter
@Entity
@Table(name = "fresh_check_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreshCheckLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id")
    private FreshCheck check;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "expected_qty", nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedQty;

    @Column(name = "received_qty", precision = 14, scale = 2)
    private BigDecimal receivedQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FreshCheckResult result;

    @Column(nullable = false, length = 300)
    private String note = "";

    public static FreshCheckLine of(
            Long foodId, BigDecimal expectedQty, BigDecimal receivedQty, FreshCheckResult result, String note) {
        FreshCheckLine line = new FreshCheckLine();
        line.foodId = foodId;
        line.expectedQty = expectedQty;
        line.receivedQty = receivedQty;
        line.result = result;
        line.note = note;
        return line;
    }

    void copyFrom(FreshCheckLine incoming) {
        this.expectedQty = incoming.expectedQty;
        this.receivedQty = incoming.receivedQty;
        this.result = incoming.result;
        this.note = incoming.note;
    }

    void attachTo(FreshCheck check) {
        this.check = check;
    }
}
