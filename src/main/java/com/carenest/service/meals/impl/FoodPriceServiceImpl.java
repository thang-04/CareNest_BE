package com.carenest.service.meals.impl;

import com.carenest.dto.meals.FoodPriceRequest;
import com.carenest.dto.meals.FoodPriceResponse;
import com.carenest.dto.meals.SupplierContractRequest;
import com.carenest.dto.meals.SupplierFoodResponse;
import com.carenest.dto.meals.SupplierResponse;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.FoodPrice;
import com.carenest.entity.meals.Supplier;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.FoodPriceRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.SupplierRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.FoodPriceService;
import com.carenest.service.meals.FoodPriceService.FoodPriceBook;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FoodPriceServiceImpl implements FoodPriceService {
    private static final String LOG_PREFIX = "[FoodPriceServiceImpl]";

    private final FoodPriceRepository priceRepository;
    private final FoodRepository foodRepository;
    private final SupplierRepository supplierRepository;
    private final NutritionCatalogMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public FoodPriceBook book(Collection<Long> foodIds) {
        String logPrefix = LOG_PREFIX + "|book|foods=" + foodIds.size();
        log.info("{}|START", logPrefix);
        try {
            FoodPriceBook book = loadBook(foodIds);
            log.info("{}|END", logPrefix);
            return book;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FoodPriceBook bookAll() {
        String logPrefix = LOG_PREFIX + "|bookAll";
        log.info("{}|START", logPrefix);
        try {
            FoodPriceBook book = toBook(priceRepository.findAllByOrderByFoodIdAscEffectiveFromAsc());
            log.info("{}|END", logPrefix);
            return book;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void recordInitialPrice(Long foodId, Long supplierId, BigDecimal price, Long userId) {
        String logPrefix = LOG_PREFIX + "|recordInitialPrice|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        try {
            if (supplierId != null) {
                requireActiveSupplier(supplierId);
            }
            priceRepository.save(FoodPrice.of(foodId, supplierId, price, today(), "Giá ban đầu", userId));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodPriceResponse> getPrices(Long foodId) {
        String logPrefix = LOG_PREFIX + "|getPrices|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        try {
            findFood(foodId);
            List<FoodPrice> rows = priceRepository.findByFoodIdOrderByEffectiveFromDesc(foodId);
            Map<Long, String> names = supplierNames(rows);
            LocalDate today = today();
            List<FoodPriceResponse> response =
                    rows.stream().map(row -> toResponse(row, names, today)).toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodPriceResponse adjustPrice(CurrentUser currentUser, Long foodId, FoodPriceRequest request) {
        String logPrefix = LOG_PREFIX + "|adjustPrice|userId=" + currentUser.userId() + "|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        try {
            Food food = findFood(foodId);
            validate(food, request, null);
            FoodPrice row = priceRepository.save(FoodPrice.of(
                    foodId,
                    request.supplierId(),
                    request.price(),
                    request.effectiveFrom(),
                    trim(request.reason()),
                    currentUser.userId()));
            log.info("{}|priceId={}|END", logPrefix, row.getId());
            return toResponse(row, supplierNames(List.of(row)), today());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodPriceResponse updatePrice(CurrentUser currentUser, Long foodId, Long priceId, FoodPriceRequest request) {
        String logPrefix = LOG_PREFIX + "|updatePrice|userId=" + currentUser.userId() + "|foodId=" + foodId
                + "|priceId=" + priceId;
        log.info("{}|START", logPrefix);
        try {
            Food food = findFood(foodId);
            FoodPrice row = findFuturePrice(foodId, priceId);
            validate(food, request, priceId);
            row.change(
                    request.supplierId(),
                    request.price(),
                    request.effectiveFrom(),
                    trim(request.reason()),
                    currentUser.userId());
            priceRepository.flush();
            log.info("{}|END", logPrefix);
            return toResponse(row, supplierNames(List.of(row)), today());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deletePrice(CurrentUser currentUser, Long foodId, Long priceId) {
        String logPrefix = LOG_PREFIX + "|deletePrice|userId=" + currentUser.userId() + "|foodId=" + foodId
                + "|priceId=" + priceId;
        log.info("{}|START", logPrefix);
        try {
            findFood(foodId);
            priceRepository.delete(findFuturePrice(foodId, priceId));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierFoodResponse> getSupplierFoods(Long supplierId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getSupplierFoods|supplierId=" + supplierId;
        log.info("{}|START", logPrefix);
        try {
            findSupplier(supplierId);
            LocalDate at = date == null ? today() : date;
            FoodPriceBook book = toBook(priceRepository.findAllByOrderByFoodIdAscEffectiveFromAsc());
            List<SupplierFoodResponse> response = new ArrayList<>();
            for (Food food : foodRepository.findAllByOrderByNameAsc()) {
                FoodPriceBook.Entry entry = book.entryAt(food.getId(), at);
                if (food.getStatus() == CatalogStatus.ACTIVE
                        && entry != null
                        && supplierId.equals(entry.supplierId())) {
                    response.add(new SupplierFoodResponse(
                            food.getId(), food.getCode(), food.getName(), food.getPurchaseUnit(), entry.price()));
                }
            }
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SupplierResponse applyContract(CurrentUser currentUser, Long supplierId, SupplierContractRequest request) {
        String logPrefix = LOG_PREFIX + "|applyContract|userId=" + currentUser.userId() + "|supplierId=" + supplierId;
        log.info("{}|START", logPrefix);
        try {
            Supplier supplier = requireActiveSupplier(supplierId);
            if (request.contractEnd().isBefore(request.contractStart())) {
                throw GlobalException.of(ApiCode.SUPPLIER_CONTRACT_RANGE_INVALID);
            }
            LocalDate start = request.contractStart();
            requireNotPast(start);
            List<Long> foodIds = request.lines().stream()
                    .map(SupplierContractRequest.Line::foodId)
                    .toList();
            Map<Long, Food> foods = foodRepository.findAllById(foodIds).stream()
                    .collect(Collectors.toMap(Food::getId, Function.identity()));
            Map<Long, FoodPrice> sameDay =
                    priceRepository.findByFoodIdInOrderByFoodIdAscEffectiveFromAsc(foodIds).stream()
                            .filter(row -> row.getEffectiveFrom().equals(start))
                            .collect(Collectors.toMap(FoodPrice::getFoodId, Function.identity()));
            String contractNo = trim(request.contractNo());
            String reason = contractNo.isEmpty() ? "Hợp đồng mới" : "Hợp đồng " + contractNo;
            for (SupplierContractRequest.Line line : request.lines()) {
                Food food = foods.get(line.foodId());
                if (food == null) {
                    throw GlobalException.of(ApiCode.FOOD_NOT_FOUND);
                }
                FoodPrice existing = sameDay.get(food.getId());
                if (existing == null) {
                    priceRepository.save(
                            FoodPrice.of(food.getId(), supplierId, line.price(), start, reason, currentUser.userId()));
                } else if (start.isAfter(today())) {
                    // Mức giá tương lai trùng ngày bắt đầu ⇒ hợp đồng mới thay thế
                    existing.change(supplierId, line.price(), start, reason, currentUser.userId());
                } else {
                    throw GlobalException.of(ApiCode.FOOD_PRICE_DATE_DUPLICATED, food.getName(), start);
                }
            }
            supplier.changeContract(
                    contractNo.isEmpty() ? null : contractNo, start, request.contractEnd(), currentUser.userId());
            priceRepository.flush();
            log.info("{}|lines={}|END", logPrefix, request.lines().size());
            return mapper.toResponse(supplier, true, today());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // NUT-27: ngày hiệu lực ≥ hôm nay, không trùng ngày; nằm trong hạn hợp đồng của NCC đang áp dụng ⇒ phải có lý do
    private void validate(Food food, FoodPriceRequest request, Long ignorePriceId) {
        requireNotPast(request.effectiveFrom());
        if (request.supplierId() != null) {
            requireActiveSupplier(request.supplierId());
        }
        boolean duplicated = ignorePriceId == null
                ? priceRepository.existsByFoodIdAndEffectiveFrom(food.getId(), request.effectiveFrom())
                : priceRepository.existsByFoodIdAndEffectiveFromAndIdNot(
                        food.getId(), request.effectiveFrom(), ignorePriceId);
        if (duplicated) {
            throw GlobalException.of(ApiCode.FOOD_PRICE_DATE_DUPLICATED, food.getName(), request.effectiveFrom());
        }
        FoodPriceBook book = loadBook(List.of(food.getId()));
        FoodPriceBook.Entry current = book.entries(food.getId()).stream()
                .filter(entry -> !Objects.equals(entry.id(), ignorePriceId))
                .filter(entry -> !entry.effectiveFrom().isAfter(request.effectiveFrom()))
                .max(Comparator.comparing(FoodPriceBook.Entry::effectiveFrom))
                .orElse(null);
        FoodPriceBook.SupplierInfo applied = current == null ? null : book.supplier(current.supplierId());
        if (applied != null
                && applied.covers(request.effectiveFrom())
                && trim(request.reason()).isEmpty()) {
            throw GlobalException.of(ApiCode.FOOD_PRICE_REASON_REQUIRED);
        }
    }

    private void requireNotPast(LocalDate date) {
        if (date.isBefore(today())) {
            throw GlobalException.of(ApiCode.FOOD_PRICE_DATE_PAST);
        }
    }

    // Mức đã có hiệu lực (≤ hôm nay) giữ nguyên để chi phí các ngày đã qua không đổi
    private FoodPrice findFuturePrice(Long foodId, Long priceId) {
        FoodPrice row = priceRepository
                .findById(priceId)
                .filter(found -> found.getFoodId().equals(foodId))
                .orElseThrow(() -> GlobalException.of(ApiCode.FOOD_PRICE_NOT_FOUND));
        if (!row.getEffectiveFrom().isAfter(today())) {
            throw GlobalException.of(ApiCode.FOOD_PRICE_LOCKED);
        }
        return row;
    }

    private FoodPriceBook loadBook(Collection<Long> foodIds) {
        return foodIds.isEmpty()
                ? toBook(List.of())
                : toBook(priceRepository.findByFoodIdInOrderByFoodIdAscEffectiveFromAsc(foodIds));
    }

    private FoodPriceBook toBook(List<FoodPrice> rows) {
        Map<Long, List<FoodPriceBook.Entry>> byFood = new HashMap<>();
        for (FoodPrice row : rows) {
            byFood.computeIfAbsent(row.getFoodId(), id -> new ArrayList<>())
                    .add(new FoodPriceBook.Entry(
                            row.getId(), row.getSupplierId(), row.getPrice(), row.getEffectiveFrom(), row.getReason()));
        }
        byFood.values().forEach(list -> list.sort(Comparator.comparing(FoodPriceBook.Entry::effectiveFrom)));
        List<Long> supplierIds = rows.stream()
                .map(FoodPrice::getSupplierId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, FoodPriceBook.SupplierInfo> suppliers = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            for (Supplier s : supplierRepository.findAllById(supplierIds)) {
                suppliers.put(
                        s.getId(),
                        new FoodPriceBook.SupplierInfo(
                                s.getId(), s.getName(), s.getContractNo(), s.getContractStart(), s.getContractEnd()));
            }
        }
        return new FoodPriceBook(byFood, suppliers, today());
    }

    private Map<Long, String> supplierNames(List<FoodPrice> rows) {
        List<Long> ids = rows.stream()
                .map(FoodPrice::getSupplierId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return supplierRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
    }

    private static FoodPriceResponse toResponse(FoodPrice row, Map<Long, String> names, LocalDate today) {
        return new FoodPriceResponse(
                row.getId(),
                row.getSupplierId(),
                row.getSupplierId() == null ? null : names.get(row.getSupplierId()),
                row.getPrice(),
                row.getEffectiveFrom(),
                row.getReason(),
                row.getEffectiveFrom().isAfter(today));
    }

    private Food findFood(Long foodId) {
        return foodRepository.findById(foodId).orElseThrow(() -> GlobalException.of(ApiCode.FOOD_NOT_FOUND));
    }

    private Supplier findSupplier(Long supplierId) {
        return supplierRepository
                .findById(supplierId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SUPPLIER_NOT_FOUND));
    }

    private Supplier requireActiveSupplier(Long supplierId) {
        Supplier supplier = findSupplier(supplierId);
        if (supplier.getStatus() != CatalogStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.SUPPLIER_INACTIVE);
        }
        return supplier;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
