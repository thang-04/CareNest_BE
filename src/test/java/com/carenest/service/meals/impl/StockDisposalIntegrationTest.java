package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.meals.StockDisposalRequest;
import com.carenest.dto.meals.StockDisposalResponse;
import com.carenest.dto.meals.StockLotResponse;
import com.carenest.dto.meals.StockMovementResponse;
import com.carenest.dto.meals.StockReceiptRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.StockDisposalService;
import com.carenest.service.meals.StockLedgerService;
import com.carenest.service.meals.StockReceiptService;
import com.carenest.support.MovableClock;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StockDisposalKind;
import com.carenest.utils.enums.StockMovementType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Xuất kho khô (NUT-29 trả NCC lô còn hạn, NUT-30 xuất hủy lô hết hạn): quy tắc lô, tồn, bút toán, quyền. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class StockDisposalIntegrationTest {

    private static final LocalDate RECEIVED = LocalDate.of(2026, 11, 2);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private StockReceiptService receiptService;

    @Autowired
    private StockLedgerService ledgerService;

    @Autowired
    private StockDisposalService disposalService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vp;
    private long campus;
    private long supplierA;
    private long supplierB;
    private long rice;
    private long expiredLot;
    private long todayLot;
    private long validLot;

    private static final MovableClock CLOCK = new MovableClock();

    // Phiếu nhập chỉ ghi ngày hôm nay (NUT-11): nhập ngày RECEIVED rồi chuyển đồng hồ sang TODAY để một lô hết hạn
    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    @BeforeEach
    void setUp() {
        CLOCK.set(RECEIVED, LocalTime.of(8, 0));
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-X', 'X', TRUE) RETURNING id");
        supplierA = insertId("INSERT INTO suppliers (name, phone) VALUES ('NCC A', '0900000001') RETURNING id");
        supplierB = insertId("INSERT INTO suppliers (name, phone) VALUES ('NCC B', '0900000002') RETURNING id");
        rice = insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin) VALUES ('GAO', 'Gạo',"
                        + " 'GRAIN', 'g', 'kg', 1000, 20000, 350, 7, 1, 77, 'TEST', 0, 'DRY', 'PLANT') RETURNING id");
        jdbc.update(
                "INSERT INTO food_prices (food_id, supplier_id, price, effective_from, reason) VALUES (?, " + supplierA
                        + ", 20000, DATE '2000-01-01'," + " 'Giá ban đầu')",
                rice);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vp = user("vp", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vp.userId(),
                campus);

        // Hạn 03/11 đã qua, hạn 10/11 đúng ngày lập phiếu (còn hạn), hạn 01/01/2027 còn hạn
        var draft = receiptService.createReceipt(
                principal,
                new StockReceiptRequest(
                        campus,
                        supplierA,
                        RECEIVED,
                        "",
                        List.of(
                                line("L-OLD", "2026-11-03", "400"),
                                line("L-TODAY", "2026-11-10", "200"),
                                line("L-NEW", "2027-01-01", "1000"))));
        receiptService.confirmReceipt(principal, draft.id());
        CLOCK.set(TODAY, LocalTime.of(9, 0));
        expiredLot = lotId("L-OLD");
        todayLot = lotId("L-TODAY");
        validLot = lotId("L-NEW");
    }

    @Test
    @DisplayName("NUT-29: trả NCC lô còn hạn đúng NCC ⇒ trừ lô, bút toán SUPPLIER_RETURN kèm lý do, phiếu đánh số tiếp")
    void returnToSupplierDeductsLotAndLogsMovement() {
        StockDisposalResponse first = disposalService.createReturn(
                principal, returnRequest(supplierA, " Giao thừa so với đơn đặt ", line(validLot, "300")));

        assertThat(first.kind()).isEqualTo(StockDisposalKind.SUPPLIER_RETURN);
        assertThat(first.code()).isEqualTo("XT-20261110-" + campus + "-01");
        assertThat(first.supplierName()).isEqualTo("NCC A");
        assertThat(first.reason()).isEqualTo("Giao thừa so với đơn đặt");
        assertThat(first.lines())
                .extracting(
                        StockDisposalResponse.DisposalLine::lotNo,
                        l -> l.quantity().intValue())
                .containsExactly(tuple("L-NEW", 300));
        assertThat(remaining(validLot)).isEqualByComparingTo("700");
        assertThat(movements(StockMovementType.SUPPLIER_RETURN)).singleElement().satisfies(m -> {
            assertThat(m.quantity()).isEqualByComparingTo("-300");
            assertThat(m.disposalId()).isEqualTo(first.id());
            assertThat(m.reason()).isEqualTo("Giao thừa so với đơn đặt");
            assertThat(m.lotNo()).isEqualTo("L-NEW");
        });

        StockDisposalResponse second =
                disposalService.createReturn(principal, returnRequest(supplierA, "Sai quy cách", line(todayLot, "50")));
        assertThat(second.code()).isEqualTo("XT-20261110-" + campus + "-02");
        assertThat(remaining(todayLot)).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("NUT-29: trả NCC bị từ chối khi lô hết hạn, sai NCC, vượt tồn, trùng lô, thiếu NCC hoặc lý do ngắn")
    void returnRejectsInvalidLines() {
        expectCode(
                () -> disposalService.createReturn(
                        principal, returnRequest(supplierA, "Hết hạn", line(expiredLot, "1"))),
                ApiCode.STOCK_DISPOSAL_LOT_EXPIRED);
        expectCode(
                () -> disposalService.createReturn(principal, returnRequest(supplierB, "Sai NCC", line(validLot, "1"))),
                ApiCode.STOCK_DISPOSAL_SUPPLIER_MISMATCH);
        expectCode(
                () -> disposalService.createReturn(
                        principal, returnRequest(supplierA, "Thừa", line(validLot, "1000.01"))),
                ApiCode.STOCK_DISPOSAL_QUANTITY_EXCEEDED);
        expectCode(
                () -> disposalService.createReturn(
                        principal, returnRequest(supplierA, "Trùng", line(validLot, "1"), line(validLot, "2"))),
                ApiCode.STOCK_DISPOSAL_LOT_DUPLICATE);
        expectCode(
                () -> disposalService.createReturn(principal, returnRequest(null, "Thiếu NCC", line(validLot, "1"))),
                ApiCode.STOCK_DISPOSAL_SUPPLIER_REQUIRED);
        expectCode(
                () -> disposalService.createReturn(principal, returnRequest(supplierA, " a ", line(validLot, "1"))),
                ApiCode.STOCK_DISPOSAL_REASON_TOO_SHORT);
        expectCode(
                () -> disposalService.createReturn(principal, returnRequest(supplierA, "Lô lạ", line(999_999L, "1"))),
                ApiCode.STOCK_DISPOSAL_LOT_NOT_FOUND);

        assertThat(remaining(validLot)).isEqualByComparingTo("1000");
        assertThat(movements(StockMovementType.SUPPLIER_RETURN)).isEmpty();
    }

    @Test
    @DisplayName(
            "NUT-30: xuất hủy chỉ lô đã hết hạn (hạn < ngày lập), không cần NCC; lô hết tồn không còn trong danh sách")
    void writeOffOnlyExpiredLots() {
        expectCode(
                () -> disposalService.createWriteOff(principal, writeOffRequest("Hết hạn", line(validLot, "1"))),
                ApiCode.STOCK_DISPOSAL_LOT_NOT_EXPIRED);
        // Hạn đúng hôm nay vẫn còn hạn
        expectCode(
                () -> disposalService.createWriteOff(principal, writeOffRequest("Hết hạn", line(todayLot, "1"))),
                ApiCode.STOCK_DISPOSAL_LOT_NOT_EXPIRED);

        StockDisposalResponse written =
                disposalService.createWriteOff(principal, writeOffRequest("Hết hạn sử dụng", line(expiredLot, "400")));

        assertThat(written.kind()).isEqualTo(StockDisposalKind.WRITE_OFF);
        assertThat(written.code()).isEqualTo("XH-20261110-" + campus + "-01");
        assertThat(written.supplierId()).isNull();
        assertThat(remaining(expiredLot)).isEqualByComparingTo("0");
        assertThat(ledgerService.getLots(principal, campus, rice, false))
                .extracting(StockLotResponse::lotNo)
                .doesNotContain("L-OLD");
        assertThat(movements(StockMovementType.WRITE_OFF))
                .singleElement()
                .satisfies(m -> assertThat(m.quantity()).isEqualByComparingTo("-400"));
        expectCode(
                () -> disposalService.createWriteOff(principal, writeOffRequest("Hết hạn", line(expiredLot, "1"))),
                ApiCode.STOCK_DISPOSAL_QUANTITY_EXCEEDED);
    }

    @Test
    @DisplayName("AUTH-20: PHT chưa được cấp STOCK_MANAGE bị từ chối; lô của điểm trường khác không tìm thấy")
    void requiresStockManageAndSameCampus() {
        expectCode(
                () -> disposalService.createWriteOff(vp, writeOffRequest("Hết hạn", line(expiredLot, "1"))),
                ApiCode.STOCK_ACCESS_DENIED);
        expectCode(
                () -> disposalService.createReturn(vp, returnRequest(supplierA, "Thừa", line(validLot, "1"))),
                ApiCode.STOCK_ACCESS_DENIED);

        CurrentUser granted = withPermissions(vp, Set.of(Permission.STOCK_MANAGE));
        assertThat(disposalService
                        .createWriteOff(granted, writeOffRequest("Hết hạn", line(expiredLot, "1")))
                        .code())
                .startsWith("XH-");

        long otherCampus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-Y', 'Y', FALSE) RETURNING id");
        expectCode(
                () -> disposalService.createWriteOff(
                        principal,
                        new StockDisposalRequest(otherCampus, null, "Hết hạn", List.of(line(expiredLot, "1")))),
                ApiCode.STOCK_DISPOSAL_LOT_NOT_FOUND);
    }

    private StockDisposalRequest returnRequest(
            Long supplier, String reason, StockDisposalRequest.DisposalLineInput... lines) {
        return new StockDisposalRequest(campus, supplier, reason, List.of(lines));
    }

    private StockDisposalRequest writeOffRequest(String reason, StockDisposalRequest.DisposalLineInput... lines) {
        return new StockDisposalRequest(campus, null, reason, List.of(lines));
    }

    private static StockDisposalRequest.DisposalLineInput line(long lotId, String quantity) {
        return new StockDisposalRequest.DisposalLineInput(lotId, new BigDecimal(quantity));
    }

    private StockReceiptRequest.ReceiptLineInput line(String lotNo, String expiry, String grams) {
        return new StockReceiptRequest.ReceiptLineInput(
                rice, lotNo, LocalDate.parse(expiry), new BigDecimal(grams), new BigDecimal("20000"));
    }

    private long lotId(String lotNo) {
        return ledgerService.getLots(principal, campus, rice, true).stream()
                .filter(l -> l.lotNo().equals(lotNo))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private BigDecimal remaining(long lotId) {
        return ledgerService.getLots(principal, campus, rice, true).stream()
                .filter(l -> l.id().equals(lotId))
                .findFirst()
                .orElseThrow()
                .remaining();
    }

    private List<StockMovementResponse> movements(StockMovementType type) {
        return ledgerService
                .getMovements(principal, campus, LocalDate.of(2020, 1, 1), LocalDate.of(2099, 1, 1), null, type, 0, 50)
                .items();
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        return new CurrentUser(
                id, "kc-" + username, username, username, null, Set.of(role), TestPermissions.defaultsOf(role));
    }

    private static CurrentUser withPermissions(CurrentUser user, Set<Permission> permissions) {
        return new CurrentUser(
                user.userId(),
                user.keycloakUserId(),
                user.username(),
                user.fullName(),
                user.email(),
                user.roles(),
                permissions);
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
