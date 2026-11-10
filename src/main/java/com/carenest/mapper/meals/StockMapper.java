package com.carenest.mapper.meals;

import com.carenest.dto.meals.StockFoodRef;
import com.carenest.dto.meals.StockLotResponse;
import com.carenest.dto.meals.StockMovementResponse;
import com.carenest.dto.meals.StockReceiptResponse;
import com.carenest.dto.meals.StockShortfallResponse;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.StockDailyDemand;
import com.carenest.entity.meals.StockLot;
import com.carenest.entity.meals.StockMovement;
import com.carenest.entity.meals.StockReceipt;
import com.carenest.entity.meals.StockReceiptLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Kho khô: entity → DTO. Thực phẩm, NCC, tồn lô do service nạp sẵn theo lô để tránh 1 + N. */
@Component
public class StockMapper {

    public StockFoodRef toFoodRef(Food food) {
        if (food == null) {
            return null;
        }
        return new StockFoodRef(
                food.getId(),
                food.getCode(),
                food.getName(),
                food.getUnit().name(),
                food.getPurchaseUnit(),
                food.getPurchaseUnitSize());
    }

    public StockReceiptResponse toResponse(
            StockReceipt receipt, String supplierName, Map<Long, Food> foods, boolean canManage) {
        List<StockReceiptResponse.ReceiptLine> lines = receipt.getLines().stream()
                .map(line -> toLine(line, foods.get(line.getFoodId())))
                .toList();
        BigDecimal total =
                lines.stream().map(StockReceiptResponse.ReceiptLine::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StockReceiptResponse(
                receipt.getId(),
                receipt.getCampusId(),
                receipt.getSupplierId(),
                supplierName,
                receipt.getReceivedDate(),
                receipt.getStatus(),
                receipt.getNote(),
                total,
                lines,
                receipt.getConfirmedBy(),
                receipt.getConfirmedAt(),
                receipt.getCancelledBy(),
                receipt.getCancelledAt(),
                receipt.getCancelReason(),
                receipt.getCreatedAt(),
                canManage);
    }

    public StockLotResponse toResponse(StockLot lot, Food food, String supplierName, BigDecimal remaining) {
        return new StockLotResponse(
                lot.getId(),
                lot.getCampusId(),
                toFoodRef(food),
                lot.getLotNo(),
                lot.getExpiryDate(),
                lot.getReceivedDate(),
                lot.getSupplierId(),
                supplierName,
                lot.getQuantity(),
                remaining,
                lot.getUnitPrice());
    }

    public StockMovementResponse toResponse(StockMovement movement, StockLot lot, Food food) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getCampusId(),
                movement.getLotId(),
                lot == null ? null : lot.getLotNo(),
                toFoodRef(food),
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getMealDate(),
                movement.getReceiptId(),
                movement.getReason(),
                movement.getCreatedBy(),
                movement.getCreatedAt());
    }

    public StockShortfallResponse toResponse(StockDailyDemand demand, Food food) {
        return new StockShortfallResponse(
                demand.getCampusId(),
                demand.getMealDate(),
                toFoodRef(food),
                demand.getRequiredQty(),
                demand.getShortfallQty());
    }

    // Thành tiền = số lượng quy ra đơn vị mua × giá đơn vị mua, làm tròn đồng
    private StockReceiptResponse.ReceiptLine toLine(StockReceiptLine line, Food food) {
        BigDecimal size = food == null ? BigDecimal.ONE : food.getPurchaseUnitSize();
        BigDecimal value = line.getQuantity().multiply(line.getUnitPrice()).divide(size, 0, RoundingMode.HALF_UP);
        return new StockReceiptResponse.ReceiptLine(
                line.getId(),
                toFoodRef(food),
                line.getLotNo(),
                line.getExpiryDate(),
                line.getQuantity(),
                line.getUnitPrice(),
                value);
    }
}
