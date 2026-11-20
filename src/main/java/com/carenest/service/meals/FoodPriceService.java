package com.carenest.service.meals;

import com.carenest.dto.meals.FoodPriceRequest;
import com.carenest.dto.meals.FoodPriceResponse;
import com.carenest.dto.meals.SupplierContractRequest;
import com.carenest.dto.meals.SupplierFoodResponse;
import com.carenest.dto.meals.SupplierResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.Constants;
import com.carenest.utils.enums.ContractStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Giá và NCC của thực phẩm theo ngày hiệu lực, hợp đồng NCC (NUT-27). */
public interface FoodPriceService {

    /** Nạp bảng giá cho các thực phẩm; mọi phép tính chi phí tra giá qua đây. */
    FoodPriceBook book(Collection<Long> foodIds);

    /** Bảng giá của toàn bộ danh mục (danh sách thực phẩm). */
    FoodPriceBook bookAll();

    /** Mức giá đầu tiên khi tạo thực phẩm, hiệu lực từ hôm nay. */
    void recordInitialPrice(Long foodId, Long supplierId, BigDecimal price, Long userId);

    List<FoodPriceResponse> getPrices(Long foodId);

    FoodPriceResponse adjustPrice(CurrentUser currentUser, Long foodId, FoodPriceRequest request);

    FoodPriceResponse updatePrice(CurrentUser currentUser, Long foodId, Long priceId, FoodPriceRequest request);

    void deletePrice(CurrentUser currentUser, Long foodId, Long priceId);

    List<SupplierFoodResponse> getSupplierFoods(Long supplierId, LocalDate date);

    SupplierResponse applyContract(CurrentUser currentUser, Long supplierId, SupplierContractRequest request);

    /**
     * Bảng giá đã nạp cho một nhóm thực phẩm (NUT-27): giá và NCC tại ngày D = mức có ngày hiệu lực gần nhất ≤ D; trước
     * mức đầu tiên dùng mức đầu tiên (giá ban đầu áp cho cả quá khứ).
     */
    final class FoodPriceBook {

        /** Một mức giá; {@code supplierId} null khi chưa gán NCC. */
        public record Entry(Long id, Long supplierId, BigDecimal price, LocalDate effectiveFrom, String reason) {}

        /** Thông tin NCC cần cho hiển thị và kiểm hạn hợp đồng. */
        public record SupplierInfo(
                Long id, String name, String contractNo, LocalDate contractStart, LocalDate contractEnd) {

            public boolean covers(LocalDate date) {
                return contractStart != null
                        && contractEnd != null
                        && !date.isBefore(contractStart)
                        && !date.isAfter(contractEnd);
            }
        }

        /** Giá và NCC của thực phẩm tại một ngày. */
        public record PriceAt(BigDecimal price, Long supplierId, String supplierName, ContractStatus contractStatus) {}

        private final Map<Long, List<Entry>> entriesByFood;
        private final Map<Long, SupplierInfo> suppliers;
        private final LocalDate today;

        public FoodPriceBook(Map<Long, List<Entry>> entriesByFood, Map<Long, SupplierInfo> suppliers, LocalDate today) {
            this.entriesByFood = entriesByFood;
            this.suppliers = suppliers;
            this.today = today;
        }

        /** Mức giá áp dụng tại ngày; null khi thực phẩm chưa có giá. Danh sách mức đã sắp tăng dần theo ngày. */
        public Entry entryAt(Long foodId, LocalDate date) {
            List<Entry> entries = foodId == null ? List.of() : entriesByFood.getOrDefault(foodId, List.of());
            if (entries.isEmpty()) {
                return null;
            }
            Entry found = entries.getFirst();
            for (Entry entry : entries) {
                if (entry.effectiveFrom().isAfter(date)) {
                    break;
                }
                found = entry;
            }
            return found;
        }

        public BigDecimal price(Long foodId, LocalDate date) {
            Entry entry = entryAt(foodId, date);
            return entry == null ? null : entry.price();
        }

        public PriceAt at(Long foodId, LocalDate date) {
            Entry entry = entryAt(foodId, date);
            if (entry == null) {
                return new PriceAt(null, null, null, ContractStatus.NONE);
            }
            SupplierInfo supplier = entry.supplierId() == null ? null : suppliers.get(entry.supplierId());
            return new PriceAt(
                    entry.price(),
                    entry.supplierId(),
                    supplier == null ? null : supplier.name(),
                    contractStatus(supplier, today));
        }

        public List<Entry> entries(Long foodId) {
            return foodId == null ? List.of() : entriesByFood.getOrDefault(foodId, List.of());
        }

        public SupplierInfo supplier(Long supplierId) {
            return supplierId == null ? null : suppliers.get(supplierId);
        }

        public static ContractStatus contractStatus(SupplierInfo supplier, LocalDate today) {
            if (supplier == null || supplier.contractStart() == null || supplier.contractEnd() == null) {
                return ContractStatus.NONE;
            }
            if (today.isBefore(supplier.contractStart())) {
                return ContractStatus.NOT_STARTED;
            }
            if (today.isAfter(supplier.contractEnd())) {
                return ContractStatus.EXPIRED;
            }
            return today.plusDays(Constants.Nutrition.CONTRACT_EXPIRING_DAYS).isBefore(supplier.contractEnd())
                    ? ContractStatus.ACTIVE
                    : ContractStatus.EXPIRING;
        }
    }
}
