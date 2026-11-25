package com.carenest.dto.meals;

import com.carenest.dto.meals.MealPlanResponse.Evaluation;
import com.carenest.utils.enums.ContractStatus;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import com.carenest.utils.enums.MealSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Thống kê một ngày thực đơn cho N trẻ của một nhóm tuổi hoặc tổng toàn trường với {@code groups} theo từng nhóm.
 * {@code childrenSource}: CONFIRMED (suất đã xác nhận),
 * ENROLLED (sĩ số đang học), INPUT (người xem nhập). Lượng tính theo đơn vị gốc của thực phẩm (g/ml); lượng mua =
 * lượng ăn ÷ (1 − thải bỏ%) (NUT-07, NUT-18). {@code evaluation} cùng dạng bảng kiểm của kế hoạch tuần (NUT-24, NUT-25).
 * {@code dishIngredients}: dòng công thức của từng món (theo bữa) cho MỘT trẻ, cùng công thức với {@code ingredients}
 * nhưng không nhân N và không gộp theo thực phẩm; dùng cho màn thực đơn mẫu (không cần ngày/điểm trường).
 * {@code freshCost} / {@code dryCost}: tổng tiền thực phẩm FRESH (cần mua) / DRY (lấy từ kho khô) theo {@code ingredients[].storage}
 * (NUT-10); {@code freshCost + dryCost = totalCost}. {@code supplierCosts}: tổng tiền theo NCC tại ngày thống kê, thứ tự
 * xuất hiện trong {@code ingredients}; thực phẩm chưa gán NCC gộp một dòng {@code supplierId = null}.
 */
public record MenuStatisticsResponse(
        Long ageGroupId,
        String ageGroupName,
        LocalDate date,
        int children,
        String childrenSource,
        List<Dish> dishes,
        List<Ingredient> ingredients,
        List<DishIngredientLine> dishIngredients,
        BigDecimal totalCost,
        BigDecimal freshCost,
        BigDecimal dryCost,
        List<SupplierCost> supplierCosts,
        BigDecimal costPerChild,
        BigDecimal mealPrice,
        BigDecimal serviceFee,
        BigDecimal foodBudget,
        Evaluation evaluation,
        List<MenuStatisticsResponse> groups) {

    public record SupplierCost(Long supplierId, String supplierName, ContractStatus contractStatus, BigDecimal cost) {}

    public record Dish(MealSession session, Long dishId, String dishName, DishType dishType) {}

    /** Một dòng công thức của món cho một trẻ; {@code purchasePerChild} theo đơn vị gốc (g/ml), {@code costPerChild} đồng. */
    public record DishIngredientLine(
            MealSession session,
            Long dishId,
            String dishName,
            Long foodId,
            String foodName,
            BigDecimal gramsPerChild,
            BigDecimal wastePct,
            BigDecimal purchasePerChild,
            String purchaseUnit,
            BigDecimal unitPrice,
            BigDecimal costPerChild) {}

    /**
     * {@code unitPrice}, NCC và {@code contractStatus} lấy tại ngày thống kê (NUT-27). {@code cost}: tiền ăn theo lượng
     * mua thực dùng (không theo số đơn vị làm tròn). {@code priceSource}: CATALOG (giá danh mục), LOT (hàng khô theo giá
     * lô FEFO trên tồn hiện tại), MIXED (một phần lô, phần còn lại giá danh mục). {@code stockShortQty} (g/ml): phần
     * hàng khô thiếu kho tính theo giá danh mục; null khi không tính theo lô (hàng tươi hoặc chưa chọn điểm trường).
     */
    public record Ingredient(
            Long foodId,
            String foodName,
            FoodUnit unit,
            BigDecimal gramsPerChild,
            BigDecimal eatenTotal,
            BigDecimal wastePct,
            BigDecimal purchaseTotal,
            String purchaseUnit,
            BigDecimal purchaseUnitSize,
            BigDecimal purchaseQty,
            BigDecimal unitPrice,
            BigDecimal cost,
            FoodStorage storage,
            Long supplierId,
            String supplierName,
            ContractStatus contractStatus,
            String priceSource,
            BigDecimal stockShortQty) {}
}
