package com.carenest.service.meals.impl;

import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.FreshCheck;
import com.carenest.entity.meals.StockLot;
import com.carenest.entity.meals.StockMovement;
import com.carenest.entity.meals.Supplier;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.FreshCheckRepository;
import com.carenest.repository.meals.StockLotRepository;
import com.carenest.repository.meals.StockMovementRepository;
import com.carenest.repository.meals.SupplierRepository;
import com.carenest.utils.enums.FoodUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Khung dòng M1 (kiểm tra trước chế biến, INS-01): thực phẩm tươi từ kiểm nhận hàng tươi trong ngày (lần 2 nếu đã
 * ghi, không thì lần 1) kèm nhà cung cấp; thực phẩm khô từ các lô đã trừ kho cho bữa ăn của ngày (NUT-11) kèm nhà cung
 * cấp, ngày nhập, hạn dùng. Khối lượng đổi sang kg / lít như mẫu biểu. Chỉ đọc; ô PHT nhập để trống.
 */
@Component
@RequiredArgsConstructor
class InspectionIntakeSkeleton {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");
    private static final int ROUND_SERVINGS = 2;

    private final FreshCheckRepository freshCheckRepository;
    private final StockMovementRepository movementRepository;
    private final StockLotRepository lotRepository;
    private final FoodRepository foodRepository;
    private final SupplierRepository supplierRepository;
    private final Clock clock;

    List<Map<String, Object>> fresh(Long campusId, LocalDate date) {
        List<FreshCheck> checks = freshCheckRepository.findByCampusIdAndCheckDate(campusId, date);
        // Lần 2 là hàng thực nhận theo suất đã chốt: ưu tiên khi đã ghi
        FreshCheck check = checks.stream()
                .max(Comparator.comparingInt(c -> c.getRound() == ROUND_SERVINGS ? 1 : 0))
                .orElse(null);
        if (check == null || check.getLines().isEmpty()) {
            return List.of();
        }
        Map<Long, Food> foods =
                foods(check.getLines().stream().map(line -> line.getFoodId()).collect(Collectors.toSet()));
        Map<String, Object> supplier = supplierCells(
                check.getSupplierId() == null
                        ? null
                        : supplierRepository.findById(check.getSupplierId()).orElse(null));
        String receivedAt = check.getCheckedAt().atZone(clock.getZone()).format(HOUR_MINUTE);
        return check.getLines().stream()
                .filter(line -> foods.containsKey(line.getFoodId()))
                .sorted(Comparator.comparing(line -> foods.get(line.getFoodId()).getName()))
                .map(line -> {
                    Food food = foods.get(line.getFoodId());
                    BigDecimal quantity = line.getReceivedQty() != null ? line.getReceivedQty() : line.getExpectedQty();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(InspectionColumns.KEY, "food:" + food.getId());
                    row.put("foodId", food.getId());
                    row.put("name", food.getName());
                    row.put("unit", unitLabel(food.getUnit()));
                    row.put("quantity", displayQuantity(quantity));
                    row.put("receivedAt", receivedAt);
                    row.putAll(supplier);
                    return row;
                })
                .toList();
    }

    List<Map<String, Object>> dry(Long campusId, LocalDate date) {
        // Lượng đã trừ cho bữa ăn của ngày theo từng lô: bút toán âm cộng lại, trả lô (dương) trừ bớt
        Map<Long, BigDecimal> issuedByLot = new LinkedHashMap<>();
        for (StockMovement movement : movementRepository.findByCampusIdAndMealDate(campusId, date)) {
            issuedByLot.merge(movement.getLotId(), movement.getQuantity().negate(), BigDecimal::add);
        }
        issuedByLot.values().removeIf(quantity -> quantity.signum() <= 0);
        if (issuedByLot.isEmpty()) {
            return List.of();
        }
        List<StockLot> lots = lotRepository.findAllById(issuedByLot.keySet());
        Map<Long, Food> foods = foods(lots.stream().map(StockLot::getFoodId).collect(Collectors.toSet()));
        Map<Long, Supplier> suppliers = supplierRepository
                .findAllById(lots.stream()
                        .map(StockLot::getSupplierId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        return lots.stream()
                .filter(lot -> foods.containsKey(lot.getFoodId()))
                .sorted(Comparator.comparing(
                                (StockLot lot) -> foods.get(lot.getFoodId()).getName())
                        .thenComparing(StockLot::getExpiryDate))
                .map(lot -> {
                    Food food = foods.get(lot.getFoodId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(InspectionColumns.KEY, "lot:" + lot.getId());
                    row.put("foodId", food.getId());
                    row.put("name", food.getName());
                    row.put("unit", unitLabel(food.getUnit()));
                    row.put("quantity", displayQuantity(issuedByLot.get(lot.getId())));
                    row.put("receivedAt", lot.getReceivedDate().toString());
                    row.put("expiryDate", lot.getExpiryDate().toString());
                    row.putAll(supplierCells(suppliers.get(lot.getSupplierId())));
                    return row;
                })
                .toList();
    }

    private Map<Long, Food> foods(Set<Long> ids) {
        return foodRepository.findAllById(ids).stream().collect(Collectors.toMap(Food::getId, Function.identity()));
    }

    /** Ô "Nơi cung cấp": tên cơ sở, địa chỉ + điện thoại, người giao. Chưa có nhà cung cấp ⇒ không điền (PHT nhập). */
    private static Map<String, Object> supplierCells(Supplier supplier) {
        if (supplier == null) {
            return Map.of();
        }
        Map<String, Object> cells = new LinkedHashMap<>();
        cells.put("supplierName", supplier.getName());
        cells.put(
                "supplierContact",
                Stream.of(supplier.getAddress(), supplier.getPhone())
                        .filter(part -> part != null && !part.isBlank())
                        .collect(Collectors.joining(", ")));
        cells.put("deliveryPerson", supplier.getDeliveryPerson());
        return cells;
    }

    private static String unitLabel(FoodUnit unit) {
        return unit == FoodUnit.ml ? "lít" : "kg";
    }

    /** Lượng gốc (g / ml) sang kg / lít, 3 chữ số thập phân. */
    private static double displayQuantity(BigDecimal baseQuantity) {
        return baseQuantity.divide(THOUSAND, 3, RoundingMode.HALF_UP).doubleValue();
    }
}
