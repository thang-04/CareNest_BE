package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.meals.FoodPriceRequest;
import com.carenest.dto.meals.FoodPriceResponse;
import com.carenest.dto.meals.SupplierContractRequest;
import com.carenest.dto.meals.SupplierResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.FoodPriceService;
import com.carenest.service.meals.FoodPriceService.FoodPriceBook;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ContractStatus;
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

/** NUT-27: giá theo ngày hiệu lực, lý do khi đổi trong hạn hợp đồng, khóa mức đã hiệu lực, chuyển giao hợp đồng. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class FoodPriceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private FoodPriceService foodPriceService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private CurrentUser vicePrincipal;
    private LocalDate today;
    private long rice;
    private long oldSupplier;
    private long newSupplier;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        long userId = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-v', 'v', 'v') RETURNING id",
                Long.class);
        vicePrincipal = new CurrentUser(
                userId,
                "kc-v",
                "v",
                "v",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                TestPermissions.defaultsOf(CareNestRole.VICE_PRINCIPAL));
        // Hợp đồng NCC cũ còn hạn tới hết 30 ngày nữa
        oldSupplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, phone, contract_no, contract_start, contract_end)"
                        + " VALUES ('NCC cũ', '0900000011', 'HĐ-C', ?, ?) RETURNING id",
                Long.class,
                today.minusMonths(6),
                today.plusDays(30));
        newSupplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, phone) VALUES ('NCC mới', '0900000012') RETURNING id", Long.class);
        rice = jdbc.queryForObject(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, storage, origin)"
                        + " VALUES ('GAO-P', 'Gạo', 'GRAIN', 'g', 'kg', 1000, 18000, 350, 7, 1, 77, 'TEST', 'DRY',"
                        + " 'PLANT') RETURNING id",
                Long.class);
        jdbc.update(
                "INSERT INTO food_prices (food_id, supplier_id, price, effective_from, reason)"
                        + " VALUES (?, ?, 18000, ?, 'Giá ban đầu')",
                rice,
                oldSupplier,
                today.minusMonths(6));
    }

    @Test
    @DisplayName("NUT-27: giá tại ngày = mức gần nhất ≤ ngày; trước mức mới vẫn giá cũ")
    void priceResolvesByEffectiveDate() {
        foodPriceService.adjustPrice(
                vicePrincipal,
                rice,
                new FoodPriceRequest(oldSupplier, new BigDecimal("19000"), today.plusDays(5), "PL1"));

        FoodPriceBook book = foodPriceService.book(List.of(rice));
        assertThat(book.price(rice, today.plusDays(4))).isEqualByComparingTo("18000");
        assertThat(book.price(rice, today.plusDays(5))).isEqualByComparingTo("19000");
        assertThat(book.price(rice, today.plusDays(90))).isEqualByComparingTo("19000");
        assertThat(book.at(rice, today).contractStatus()).isEqualTo(ContractStatus.EXPIRING);
    }

    @Test
    @DisplayName("NUT-27: đổi giá trong hạn hợp đồng phải ghi lý do; ngày đã qua không chọn được")
    void adjustRules() {
        assertThatThrownBy(() -> foodPriceService.adjustPrice(
                        vicePrincipal, rice, new FoodPriceRequest(oldSupplier, new BigDecimal("19000"), today, " ")))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FOOD_PRICE_REASON_REQUIRED);
        assertThatThrownBy(() -> foodPriceService.adjustPrice(
                        vicePrincipal,
                        rice,
                        new FoodPriceRequest(oldSupplier, new BigDecimal("19000"), today.minusDays(1), "PL")))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FOOD_PRICE_DATE_PAST);
        // Sau hạn hợp đồng thì không bắt lý do
        FoodPriceResponse after = foodPriceService.adjustPrice(
                vicePrincipal,
                rice,
                new FoodPriceRequest(oldSupplier, new BigDecimal("19000"), today.plusDays(40), null));
        assertThat(after.editable()).isTrue();
    }

    @Test
    @DisplayName("NUT-27: mức đã hiệu lực không sửa, không xóa; mức tương lai sửa được")
    void effectiveRowsAreLocked() {
        Long current = foodPriceService.getPrices(rice).getFirst().id();
        assertThatThrownBy(() -> foodPriceService.deletePrice(vicePrincipal, rice, current))
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.FOOD_PRICE_LOCKED);

        FoodPriceResponse future = foodPriceService.adjustPrice(
                vicePrincipal,
                rice,
                new FoodPriceRequest(oldSupplier, new BigDecimal("19000"), today.plusDays(3), "PL"));
        FoodPriceResponse changed = foodPriceService.updatePrice(
                vicePrincipal,
                rice,
                future.id(),
                new FoodPriceRequest(oldSupplier, new BigDecimal("19500"), today.plusDays(3), "PL sửa"));
        assertThat(changed.price()).isEqualByComparingTo("19500");
        foodPriceService.deletePrice(vicePrincipal, rice, future.id());
        assertThat(foodPriceService.getPrices(rice)).hasSize(1);
    }

    @Test
    @DisplayName("NUT-27: áp dụng hợp đồng mới chuyển NCC từ ngày bắt đầu; ngày trước vẫn NCC cũ")
    void applyContractHandsOverSupplier() {
        LocalDate start = today.plusDays(31);
        SupplierResponse supplier = foodPriceService.applyContract(
                vicePrincipal,
                newSupplier,
                new SupplierContractRequest(
                        "HĐ-M",
                        start,
                        start.plusYears(1).minusDays(1),
                        List.of(new SupplierContractRequest.Line(rice, new BigDecimal("17500")))));

        assertThat(supplier.contractNo()).isEqualTo("HĐ-M");
        assertThat(supplier.contractStatus()).isEqualTo(ContractStatus.NOT_STARTED);
        FoodPriceBook book = foodPriceService.book(List.of(rice));
        assertThat(book.at(rice, start.minusDays(1)).supplierName()).isEqualTo("NCC cũ");
        assertThat(book.at(rice, start).supplierName()).isEqualTo("NCC mới");
        assertThat(book.price(rice, start)).isEqualByComparingTo("17500");
        assertThat(foodPriceService.getSupplierFoods(newSupplier, start))
                .singleElement()
                .satisfies(f -> assertThat(f.foodId()).isEqualTo(rice));
    }
}
