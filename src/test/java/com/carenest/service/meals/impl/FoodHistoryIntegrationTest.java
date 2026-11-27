package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.meals.FoodHistoryResponse;
import com.carenest.dto.meals.FoodRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.FoodPriceService;
import com.carenest.service.meals.FoodService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** G94-460: lịch sử thực phẩm, món dùng thực phẩm, đổi giá/NCC cùng lần lưu thực phẩm (NUT-27). */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class FoodHistoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private FoodService foodService;

    @Autowired
    private FoodPriceService foodPriceService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private CurrentUser manager;
    private LocalDate today;
    private long rice;
    private long supplierA;
    private long supplierB;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        long userId = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-h', 'h', 'h') RETURNING id",
                Long.class);
        manager = new CurrentUser(
                userId,
                "kc-h",
                "h",
                "h",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL));
        // Hợp đồng còn hạn: đổi giá trong hạn phải ghi lý do
        supplierA = jdbc.queryForObject(
                "INSERT INTO suppliers (name, phone, contract_no, contract_start, contract_end)"
                        + " VALUES ('NCC A', '0900000021', 'HĐ-A', ?, ?) RETURNING id",
                Long.class,
                today.minusMonths(6),
                today.plusMonths(6));
        supplierB = jdbc.queryForObject(
                "INSERT INTO suppliers (name, phone) VALUES ('NCC B', '0900000022') RETURNING id", Long.class);
        rice = jdbc.queryForObject(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, storage, origin)"
                        + " VALUES ('GAO-H', 'Gạo', 'GRAIN', 'g', 'kg', 1000, 18000, 350, 7, 1, 77, 'TEST', 'DRY',"
                        + " 'PLANT') RETURNING id",
                Long.class);
        jdbc.update(
                "INSERT INTO food_prices (food_id, supplier_id, price, effective_from, reason)"
                        + " VALUES (?, ?, 18000, ?, 'Giá ban đầu')",
                rice,
                supplierA,
                today.minusMonths(6));
    }

    @Test
    @DisplayName(
            "NUT-32: có dòng phiếu nhập kho thì khóa đơn vị; sửa quy đổi bị từ chối theo từng ô, chưa nhập thì cho sửa")
    void unitsLockOnceStocked() {
        assertThat(foodService.getFood(rice).unitsLocked()).isFalse();
        FoodRequest repack = request("Gạo", "18000", null, null, null);
        FoodRequest changed = new FoodRequest(
                repack.code(),
                repack.name(),
                repack.foodGroup(),
                repack.unit(),
                "bao",
                new BigDecimal("500"),
                repack.unitPrice(),
                repack.kcal(),
                repack.protein(),
                repack.lipid(),
                repack.glucid(),
                repack.calcium(),
                repack.phosphorus(),
                repack.iron(),
                repack.vitaminA(),
                repack.vitaminB1(),
                repack.vitaminB2(),
                repack.vitaminPp(),
                repack.vitaminC(),
                repack.zinc(),
                repack.nutrientSource(),
                repack.wastePct(),
                repack.storage(),
                repack.origin(),
                repack.note(),
                repack.allergenIds(),
                repack.supplierId(),
                repack.effectiveFrom(),
                repack.priceReason());
        assertThat(foodService.updateFood(manager, rice, changed).purchaseUnit())
                .isEqualTo("bao");
        foodService.updateFood(manager, rice, repack);

        long campus = jdbc.queryForObject(
                "INSERT INTO campuses (code, name, address) VALUES ('CS-LOCK', 'CS lock', 'x') RETURNING id",
                Long.class);
        long receipt = jdbc.queryForObject(
                "INSERT INTO stock_receipts (campus_id, supplier_id, received_date, status)"
                        + " VALUES (?, ?, ?, 'DRAFT') RETURNING id",
                Long.class,
                campus,
                supplierA,
                today);
        jdbc.update(
                "INSERT INTO stock_receipt_lines (receipt_id, food_id, lot_no, expiry_date, quantity, unit_price)"
                        + " VALUES (?, ?, 'L1', ?, 1000, 18)",
                receipt,
                rice,
                today.plusMonths(3));

        assertThat(foodService.getFood(rice).unitsLocked()).isTrue();
        assertThat(foodService.getFoods(null, null, null, null))
                .filteredOn(f -> f.id() == rice)
                .singleElement()
                .satisfies(f -> assertThat(f.unitsLocked()).isTrue());
        assertThatThrownBy(() -> foodService.updateFood(manager, rice, changed))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getFieldErrors())
                                .extracting(e -> e.field())
                                .containsExactly("purchaseUnit", "purchaseUnitSize"));
    }

    @Test
    @DisplayName("Sửa thông tin: ghi từng trường đổi với giá trị trước/sau; lưu không đổi gì thì không ghi")
    void historyRecordsOnlyRealChanges() {
        foodService.updateFood(manager, rice, request("Gạo", "18000", null, null, null));
        assertThat(foodService.getFoodHistory(rice)).isEmpty();

        foodService.updateFood(manager, rice, request("Gạo tẻ", "18000", null, null, null));

        assertThat(foodService.getFoodHistory(rice)).singleElement().satisfies(h -> {
            assertThat(h.field()).isEqualTo("Tên thực phẩm");
            assertThat(h.oldValue()).isEqualTo("Gạo");
            assertThat(h.newValue()).isEqualTo("Gạo tẻ");
            assertThat(h.userId()).isEqualTo(manager.userId());
            assertThat(h.userName()).isEqualTo("h");
            assertThat(h.at()).isNotNull();
        });
    }

    @Test
    @DisplayName("NUT-27: sửa thực phẩm kèm giá/NCC mới ⇒ thêm mức giá mới, mức cũ giữ nguyên, lịch sử gộp cả đổi giá")
    void updateWithPriceCreatesNewPriceRow() {
        foodService.updateFood(
                manager, rice, request("Gạo", "19000", supplierB, today.plusDays(3), "Đổi nhà cung cấp theo phụ lục"));

        assertThat(foodPriceService.getPrices(rice)).hasSize(2);
        assertThat(foodPriceService.book(List.of(rice)).price(rice, today)).isEqualByComparingTo("18000");
        assertThat(foodPriceService.book(List.of(rice)).price(rice, today.plusDays(3)))
                .isEqualByComparingTo("19000");
        List<FoodHistoryResponse> history = foodService.getFoodHistory(rice);
        assertThat(history)
                .extracting(FoodHistoryResponse::field, FoodHistoryResponse::oldValue, FoodHistoryResponse::newValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Đơn giá", "18000", "19000"),
                        org.assertj.core.groups.Tuple.tuple("Nhà cung cấp", "NCC A", "NCC B"));
        assertThat(history.getFirst().note()).contains("Đổi nhà cung cấp theo phụ lục");
    }

    @Test
    @DisplayName("NUT-27: ngày hiệu lực đã qua bị từ chối; trong hạn hợp đồng thiếu lý do bị từ chối")
    void priceRulesApplyToUpdate() {
        assertThatThrownBy(() -> foodService.updateFood(
                        manager, rice, request("Gạo", "19000", null, today.minusDays(1), "Lùi ngày")))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FOOD_PRICE_DATE_PAST);
        assertThatThrownBy(() -> foodService.updateFood(manager, rice, request("Gạo", "19000", null, null, " ")))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FOOD_PRICE_REASON_REQUIRED);
    }

    @Test
    @DisplayName("Món dùng thực phẩm: mỗi món một dòng dù có định lượng ở nhiều nhóm tuổi")
    void dishesAreNotCountedPerAgeGroup() {
        long dish = jdbc.queryForObject(
                "INSERT INTO dishes (code, name, dish_type) VALUES ('COM-H', 'Cơm', 'CARB') RETURNING id", Long.class);
        List<Long> ageGroups = jdbc.queryForList("SELECT id FROM age_groups ORDER BY id LIMIT 2", Long.class);
        for (Long ageGroup : ageGroups) {
            jdbc.update(
                    "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, 60)",
                    dish,
                    rice,
                    ageGroup);
        }

        assertThat(ageGroups).hasSize(2);
        assertThat(foodService.getFoodDishes(rice)).singleElement().satisfies(d -> {
            assertThat(d.dishId()).isEqualTo(dish);
            assertThat(d.name()).isEqualTo("Cơm");
            assertThat(d.portions()).hasSize(2);
            assertThat(d.portions())
                    .allSatisfy(p -> assertThat(p.gramsPerChild()).isEqualByComparingTo("60"));
        });
    }

    private FoodRequest request(String name, String price, Long supplierId, LocalDate effectiveFrom, String reason) {
        return new FoodRequest(
                "GAO-H",
                name,
                FoodGroup.GRAIN,
                FoodUnit.g,
                "kg",
                BigDecimal.valueOf(1000),
                new BigDecimal(price),
                BigDecimal.valueOf(350),
                BigDecimal.valueOf(7),
                BigDecimal.ONE,
                BigDecimal.valueOf(77),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "TEST",
                BigDecimal.ZERO,
                FoodStorage.DRY,
                FoodOrigin.PLANT,
                null,
                List.of(),
                supplierId,
                effectiveFrom,
                reason);
    }
}
