package com.carenest.service.meals.impl;

import com.carenest.dto.meals.FoodHistoryResponse;
import com.carenest.entity.meals.FoodHistory;
import com.carenest.entity.meals.FoodPrice;
import com.carenest.entity.meals.Supplier;
import com.carenest.repository.meals.FoodHistoryRepository;
import com.carenest.repository.meals.FoodPriceRepository;
import com.carenest.repository.meals.SupplierRepository;
import com.carenest.service.account.AccountQueryService;
import com.carenest.service.meals.FoodHistoryService;
import com.carenest.utils.enums.CatalogStatus;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FoodHistoryServiceImpl implements FoodHistoryService {
    private static final String LOG_PREFIX = "[FoodHistoryServiceImpl]";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FIELD_PRICE = "Đơn giá";
    private static final String FIELD_SUPPLIER = "Nhà cung cấp";
    private static final String FIELD_STATUS = "Trạng thái";

    private final FoodHistoryRepository historyRepository;
    private final FoodPriceRepository priceRepository;
    private final SupplierRepository supplierRepository;
    private final AccountQueryService accountQueryService;
    private final Clock clock;

    @Override
    public void recordCreated(Long foodId, Long userId) {
        String logPrefix = LOG_PREFIX + "|recordCreated|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        try {
            historyRepository.save(
                    FoodHistory.of(foodId, userId, clock.instant(), FoodHistory.CREATED, null, null, null));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void recordChanges(Long foodId, Long userId, Map<String, String> before, Map<String, String> after) {
        String logPrefix = LOG_PREFIX + "|recordChanges|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        try {
            List<FoodHistory> rows = new ArrayList<>();
            after.forEach((field, now) -> {
                String old = before.get(field);
                if (!FoodSnapshot.same(old, now)) {
                    rows.add(FoodHistory.of(foodId, userId, clock.instant(), FoodHistory.UPDATED, field, old, now));
                }
            });
            historyRepository.saveAll(rows);
            log.info("{}|changed={}|END", logPrefix, rows.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void recordStatusChange(Long foodId, Long userId, CatalogStatus from, CatalogStatus to) {
        String logPrefix = LOG_PREFIX + "|recordStatusChange|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        try {
            if (from != to) {
                historyRepository.save(FoodHistory.of(
                        foodId,
                        userId,
                        clock.instant(),
                        FoodHistory.STATUS_CHANGED,
                        FIELD_STATUS,
                        from.name(),
                        to.name()));
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodHistoryResponse> getHistory(Long foodId) {
        String logPrefix = LOG_PREFIX + "|getHistory|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        try {
            List<FoodHistoryResponse> response = new ArrayList<>();
            historyRepository
                    .findByFoodIdOrderByChangedAtAscIdAsc(foodId)
                    .forEach(row -> response.add(new FoodHistoryResponse(
                            row.getId(),
                            row.getUserId(),
                            null,
                            row.getChangedAt(),
                            row.getAction(),
                            row.getField(),
                            row.getOldValue(),
                            row.getNewValue(),
                            null)));
            response.addAll(priceChanges(foodId));
            response.sort(Comparator.comparing(FoodHistoryResponse::at));
            Map<Long, String> names = accountQueryService.getDisplayNames(response.stream()
                    .map(FoodHistoryResponse::userId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            List<FoodHistoryResponse> named = response.stream()
                    .map(h -> new FoodHistoryResponse(
                            h.id(),
                            h.userId(),
                            names.get(h.userId()),
                            h.at(),
                            h.action(),
                            h.field(),
                            h.oldValue(),
                            h.newValue(),
                            h.note()))
                    .toList();
            log.info("{}|END", logPrefix);
            return named;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Mức giá đầu tiên là giá lúc tạo; từ mức thứ hai, mỗi mức đổi giá hoặc nhà cung cấp so với mức liền trước
    private List<FoodHistoryResponse> priceChanges(Long foodId) {
        List<FoodPrice> rows = new ArrayList<>(priceRepository.findByFoodIdOrderByEffectiveFromDesc(foodId));
        java.util.Collections.reverse(rows);
        Map<Long, String> suppliers = supplierRepository
                .findAllById(rows.stream()
                        .map(FoodPrice::getSupplierId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));
        List<FoodHistoryResponse> changes = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            FoodPrice prev = rows.get(i - 1);
            FoodPrice row = rows.get(i);
            String note = "Hiệu lực từ " + row.getEffectiveFrom().format(DATE)
                    + (row.getReason() == null || row.getReason().isBlank() ? "" : "; lý do: " + row.getReason());
            if (row.getPrice().compareTo(prev.getPrice()) != 0) {
                changes.add(priceEvent(row, FIELD_PRICE, price(prev), price(row), note));
            }
            if (!Objects.equals(row.getSupplierId(), prev.getSupplierId())) {
                changes.add(priceEvent(
                        row,
                        FIELD_SUPPLIER,
                        suppliers.get(prev.getSupplierId()),
                        suppliers.get(row.getSupplierId()),
                        note));
            }
        }
        return changes;
    }

    private static String price(FoodPrice row) {
        return row.getPrice().stripTrailingZeros().toPlainString();
    }

    private static FoodHistoryResponse priceEvent(FoodPrice row, String field, String old, String now, String note) {
        return new FoodHistoryResponse(
                null, row.getCreatedBy(), null, row.getCreatedAt(), FoodHistory.PRICE_ADJUSTED, field, old, now, note);
    }
}
