package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Bản cân đối đang sửa, gửi để tính thử hoặc lưu. Dòng không gửi lấy theo bản đã lưu, món mới lấy theo công thức.
 * {@code edits} sửa theo thực phẩm của một nhóm tuổi (gam mỗi trẻ hoặc lượng mua theo đơn vị mua, chỉ một trong hai).
 * {@code applyRounding}: làm tròn lượng mua toàn trường ngay lượt này; {@code roundingStep} là bước kg/lít
 * (0,1 – 0,5 – 1), gửi kèm khi lưu số đã làm tròn. {@code version}: phiên bản đã đọc, null khi chưa có bản lưu.
 * {@code fromRecipe}: bỏ gam đã lưu, dựng lại từ công thức món.
 */
public record DayBalanceRequest(
        Long version,
        @Size(max = 200) List<@NotNull @Valid CountInput> counts,
        @Size(max = 2000) List<@NotNull @Valid LineInput> lines,
        @Size(max = 200) List<@NotNull @Valid EditInput> edits,
        boolean applyRounding,
        BigDecimal roundingStep,
        boolean fromRecipe) {

    public DayBalanceRequest(
            Long version,
            List<CountInput> counts,
            List<LineInput> lines,
            List<EditInput> edits,
            boolean applyRounding,
            BigDecimal roundingStep) {
        this(version, counts, lines, edits, applyRounding, roundingStep, false);
    }

    public record CountInput(
            @NotNull Long campusId,
            @NotNull Long ageGroupId,
            @Min(0) @Max(5000) int children) {}

    public record LineInput(
            @NotNull Long ageGroupId,
            @NotNull MealSession session,
            @NotNull Long dishId,
            @NotNull Long foodId,
            @NotNull @DecimalMin("0") @DecimalMax("5000") BigDecimal baseGrams,
            @NotNull @DecimalMin("0") @DecimalMax("5000") BigDecimal grams) {}

    public record EditInput(
            @NotNull Long ageGroupId,
            @NotNull Long foodId,
            @DecimalMin("0") @DecimalMax("5000") BigDecimal gramsPerChild,
            @DecimalMin("0") @DecimalMax("1000000") BigDecimal purchaseQuantity) {}
}
