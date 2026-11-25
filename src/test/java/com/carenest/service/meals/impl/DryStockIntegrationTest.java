package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.meals.StockLotResponse;
import com.carenest.dto.meals.StockMovementResponse;
import com.carenest.dto.meals.StockReceiptRequest;
import com.carenest.dto.meals.StockReceiptResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.StockLedgerService;
import com.carenest.service.meals.StockReceiptService;
import com.carenest.support.MovableClock;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StockMovementType;
import com.carenest.utils.enums.StockReceiptStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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

/** Kho khô (L2) và trừ kho theo suất đã chốt (L3): lô, bút toán, phần thiếu. syncDay gọi trực tiếp (không qua event). */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DryStockIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate RECEIVED = LocalDate.of(2026, 11, 2);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private StockReceiptService receiptService;

    @Autowired
    private StockLedgerService ledgerService;

    @Autowired
    private InspectionIntakeSkeleton intakeSkeleton;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vp;
    private long campus;
    private long year;
    private long classroom;
    private long supplier;
    private long rice;
    private long pork;
    private long lunchCount;

    private static final MovableClock CLOCK = new MovableClock();

    // Phiếu nhập chỉ ghi ngày hôm nay (NUT-11): "hôm nay" là ngày nhập của phiếu, ngày ăn thứ Hai sau đó
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
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-K', 'K', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'Lớp K', " + ag3 + ") RETURNING id");
        supplier = insertId("INSERT INTO suppliers (name, phone) VALUES ('NCC khô', '0900000001') RETURNING id");
        // Gạo kho khô, thải bỏ 20% ⇒ mua = ăn ÷ 0,8; thịt hàng tươi không vào kho
        rice = food("GAO", "Gạo", "DRY", "20");
        pork = food("THIT", "Thịt", "FRESH", "0");
        long dish = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('COM', 'Cơm', 'CARB') RETURNING id");
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, 60)",
                dish,
                rice,
                ag3);
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, 40)",
                dish,
                pork,
                ag3);
        long week = insertId("INSERT INTO meal_plan_weeks (week_start, status, published_at) "
                + "VALUES ('2026-11-09', 'PUBLISHED', now()) RETURNING id");
        for (int i = 0; i < 5; i++) {
            LocalDate day = MONDAY.plusDays(i);
            jdbc.update(
                    "UPDATE meal_plan_weeks SET days = days || jsonb_build_array("
                            + "jsonb_build_object('date', ?::date, 'off', false, 'note', '')) WHERE id = ?",
                    day,
                    week);
            jdbc.update(
                    "INSERT INTO meal_plan_items (week_id, menu_date, session, menu_block, dish_id)"
                            + " VALUES (?, ?, 'LUNCH', 'KINDERGARTEN', ?)",
                    week,
                    day,
                    dish);
        }
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
    }

    @Test
    @DisplayName("AUTH-20: PHT chưa được cấp STOCK_MANAGE không lập phiếu, vẫn xem tồn kho")
    void receiptRequiresStockManageButLotsAreReadable() {
        expectCode(
                () -> receiptService.createReceipt(vp, request(line(rice, "L1", "2026-12-01", "1000"))),
                ApiCode.STOCK_ACCESS_DENIED);
        assertThat(ledgerService.getLots(vp, campus, null, false)).isEmpty();

        CurrentUser granted = withPermissions(vp, Set.of(Permission.STOCK_MANAGE));
        assertThat(receiptService
                        .createReceipt(granted, request(line(rice, "L1", "2026-12-01", "1000")))
                        .status())
                .isEqualTo(StockReceiptStatus.DRAFT);
    }

    @Test
    @DisplayName("NUT-10: hàng tươi không nhập kho khô; hạn dùng trước ngày nhận bị từ chối")
    void rejectsFreshFoodAndExpiryBeforeReceipt() {
        expectCode(
                () -> receiptService.createReceipt(principal, request(line(pork, "T1", "2026-12-01", "1000"))),
                ApiCode.STOCK_FOOD_NOT_DRY);
        expectCode(
                () -> receiptService.createReceipt(principal, request(line(rice, "L0", "2026-10-01", "1000"))),
                ApiCode.STOCK_EXPIRY_BEFORE_RECEIPT);
    }

    @Test
    @DisplayName("G94-457: thực phẩm phải thuộc nhà cung cấp của phiếu khi lưu nháp, sửa và xác nhận")
    void rejectsFoodNotOfSupplier() {
        long other = insertId("INSERT INTO suppliers (name, phone) VALUES ('NCC khác', '0900000002') RETURNING id");
        StockReceiptRequest wrongSupplier =
                new StockReceiptRequest(campus, other, RECEIVED, "", List.of(line(rice, "L1", "2026-12-01", "1000")));
        expectCode(() -> receiptService.createReceipt(principal, wrongSupplier), ApiCode.STOCK_FOOD_NOT_OF_SUPPLIER);

        StockReceiptResponse draft =
                receiptService.createReceipt(principal, request(line(rice, "L1", "2026-12-01", "1000")));
        expectCode(
                () -> receiptService.updateReceipt(principal, draft.id(), wrongSupplier),
                ApiCode.STOCK_FOOD_NOT_OF_SUPPLIER);

        // Giá hợp đồng chuyển sang NCC khác sau khi lập nháp: xác nhận phải chặn
        jdbc.update(
                "INSERT INTO food_prices (food_id, supplier_id, price, effective_from, reason)"
                        + " VALUES (?, ?, 21000, DATE '2001-01-01', 'Doi NCC')",
                rice,
                other);
        expectCode(() -> receiptService.confirmReceipt(principal, draft.id()), ApiCode.STOCK_FOOD_NOT_OF_SUPPLIER);
    }

    @Test
    @DisplayName("NUT-11: xác nhận phiếu ⇒ mỗi dòng một lô + bút toán nhập; tồn sắp hạn dùng tăng dần; phiếu khóa sửa")
    void confirmCreatesLotsAndReceiptMovements() {
        StockReceiptResponse draft = receiptService.createReceipt(
                principal,
                request(line(rice, "L-LATE", "2027-01-01", "1000"), line(rice, "L-SOON", "2026-12-01", "400")));
        assertThat(draft.totalValue()).isEqualByComparingTo("28000"); // 1,4 kg × 20.000đ/kg

        StockReceiptResponse confirmed = receiptService.confirmReceipt(principal, draft.id());

        assertThat(confirmed.status()).isEqualTo(StockReceiptStatus.CONFIRMED);
        assertThat(ledgerService.getLots(principal, campus, rice, false))
                .extracting(StockLotResponse::lotNo, l -> l.remaining().intValue(), l -> l.supplierName())
                .containsExactly(tuple("L-SOON", 400, "NCC khô"), tuple("L-LATE", 1000, "NCC khô"));
        assertThat(movements(StockMovementType.RECEIPT)).hasSize(2);
        expectCode(
                () -> receiptService.updateReceipt(principal, draft.id(), request(line(rice, "X", "2027-01-01", "1"))),
                ApiCode.STOCK_RECEIPT_STATUS_INVALID);
    }

    @Test
    @DisplayName("NUT-11: hủy phiếu chỉ khi lô chưa bị trừ, bằng bút toán đảo kèm lý do")
    void cancelReversesUntouchedLotsAndIsBlockedAfterDeduction() {
        StockReceiptResponse untouched = confirmReceipt(line(rice, "L-A", "2027-01-01", "500"));
        assertThat(untouched.canCancel()).isTrue();
        StockReceiptResponse cancelled = receiptService.cancelReceipt(principal, untouched.id(), " Nhập nhầm ");
        assertThat(cancelled.status()).isEqualTo(StockReceiptStatus.CANCELLED);
        assertThat(cancelled.canCancel()).isFalse();
        assertThat(cancelled.cancelReason()).isEqualTo("Nhập nhầm");
        assertThat(ledgerService.getLots(principal, campus, rice, false)).isEmpty();

        StockReceiptResponse used = confirmReceipt(line(rice, "L-B", "2027-01-01", "5000"));
        confirmServings(10);
        ledgerService.syncDay(campus, MONDAY);
        // Lô đã bị trừ ⇒ Web khóa nút hủy theo canCancel
        assertThat(receiptService.getReceipt(principal, used.id()).canCancel()).isFalse();
        expectCode(
                () -> receiptService.cancelReceipt(principal, used.id(), "Nhập nhầm"), ApiCode.STOCK_RECEIPT_LOT_USED);
    }

    @Test
    @DisplayName(
            "Số lô: không trùng trong phiếu, không trùng lô đã vào kho cùng thực phẩm; phiếu hủy và thực phẩm khác thì được")
    void lotNoMustBeUniquePerFoodInCampus() {
        // Trùng ngay trong phiếu, kể cả khác hoa thường
        expectCode(
                () -> receiptService.createReceipt(
                        principal,
                        request(line(rice, "L1", "2027-01-01", "500"), line(rice, "l1 ", "2027-02-01", "500"))),
                ApiCode.STOCK_LOT_NO_DUPLICATED_IN_RECEIPT);

        StockReceiptResponse first = confirmReceipt(line(rice, "L-DUP", "2027-01-01", "500"));
        // Cùng thực phẩm, cùng số lô đã vào kho: báo lỗi ở bước lưu nháp
        expectCode(
                () -> receiptService.createReceipt(principal, request(line(rice, "l-dup", "2027-02-01", "300"))),
                ApiCode.STOCK_LOT_NO_EXISTS);
        // Số lô khác thì được
        StockReceiptResponse next = confirmReceipt(line(rice, "L-DUP-2", "2027-02-01", "300"));
        assertThat(next.status()).isEqualTo(StockReceiptStatus.CONFIRMED);

        // Hai nháp cùng số lô chưa vào kho: nháp đầu xác nhận thì nháp sau bị chặn khi xác nhận
        StockReceiptResponse draftA =
                receiptService.createReceipt(principal, request(line(rice, "L-RACE", "2027-01-01", "100")));
        StockReceiptResponse draftB =
                receiptService.createReceipt(principal, request(line(rice, "L-RACE", "2027-01-01", "100")));
        receiptService.confirmReceipt(principal, draftA.id());
        expectCode(() -> receiptService.confirmReceipt(principal, draftB.id()), ApiCode.STOCK_LOT_NO_EXISTS);

        // Phiếu đã hủy không giữ số lô: nhập lại đúng số lô đó được
        receiptService.cancelReceipt(principal, first.id(), "Nhập nhầm");
        assertThat(confirmReceipt(line(rice, "L-DUP", "2027-01-01", "500")).status())
                .isEqualTo(StockReceiptStatus.CONFIRMED);
    }

    @Test
    @DisplayName("NUT-11, P-38: suất chốt ⇒ trừ gam × suất ÷ (1 − thải bỏ), lô hết hạn trước, một lần")
    void confirmDeductsFifoOnce() {
        confirmReceipt(line(rice, "L-LATE", "2027-01-01", "1000"), line(rice, "L-SOON", "2026-12-01", "500"));
        confirmServings(10); // 10 × 60 g ÷ 0,8 = 750 g gạo; thịt tươi không trừ

        ledgerService.syncDay(campus, MONDAY);
        ledgerService.syncDay(campus, MONDAY);

        assertThat(movements(StockMovementType.DEDUCT))
                .extracting(StockMovementResponse::lotNo, m -> m.quantity().intValue(), StockMovementResponse::mealDate)
                .containsExactlyInAnyOrder(tuple("L-SOON", -500, MONDAY), tuple("L-LATE", -250, MONDAY));
        assertThat(ledgerService.getLots(principal, campus, rice, true))
                .extracting(StockLotResponse::lotNo, l -> l.remaining().intValue())
                .containsExactly(tuple("L-SOON", 0), tuple("L-LATE", 750));
        assertThat(ledgerService.getShortfalls(principal, campus)).isEmpty();
    }

    @Test
    @DisplayName("NUT-11, P-38: kho không đủ ⇒ trừ phần có, ghi phần thiếu; phiếu nhập bổ sung trừ tiếp phần thiếu")
    void shortfallRecordedThenSettledByNextReceipt() {
        confirmReceipt(line(rice, "L-1", "2027-01-01", "300"));
        confirmServings(10);

        ledgerService.syncDay(campus, MONDAY);

        assertThat(ledgerService.getShortfalls(principal, campus))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.requiredQty()).isEqualByComparingTo("750");
                    assertThat(s.shortfallQty()).isEqualByComparingTo("450");
                    assertThat(s.food().purchaseUnit()).isEqualTo("kg");
                });

        confirmReceipt(line(rice, "L-2", "2027-02-01", "1000"));

        assertThat(ledgerService.getShortfalls(principal, campus)).isEmpty();
        assertThat(movements(StockMovementType.SHORTFALL_DEDUCT))
                .extracting(StockMovementResponse::lotNo, m -> m.quantity().intValue())
                .containsExactly(tuple("L-2", -450));
    }

    @Test
    @DisplayName("NUT-11, ATT-07: xác nhận lại chỉ ghi chênh lệch — tăng trừ thêm, giảm trả lại lô vừa trừ")
    void reconfirmWritesOnlyDelta() {
        confirmReceipt(line(rice, "L-SOON", "2026-12-01", "500"), line(rice, "L-LATE", "2027-01-01", "1000"));
        confirmServings(10);
        ledgerService.syncDay(campus, MONDAY); // 750: L-SOON 500, L-LATE 250

        setServings(12); // 900 ⇒ +150
        ledgerService.syncDay(campus, MONDAY);
        setServings(8); // 600 ⇒ −300 trả về L-LATE (trừ gần nhất)
        ledgerService.syncDay(campus, MONDAY);

        assertThat(movements(StockMovementType.ADJUST))
                .extracting(StockMovementResponse::lotNo, m -> m.quantity().intValue())
                .containsExactlyInAnyOrder(tuple("L-LATE", -150), tuple("L-LATE", 300));
        assertThat(ledgerService.getLots(principal, campus, rice, true))
                .extracting(StockLotResponse::lotNo, l -> l.remaining().intValue())
                .containsExactly(tuple("L-SOON", 0), tuple("L-LATE", 900));
    }

    @Test
    @DisplayName("NUT-11: ngày nhập phải là hôm nay; phiếu nháp ngày cũ phải đổi ngày về hôm nay rồi mới xác nhận")
    void receiptDatedToday() {
        StockReceiptRequest.ReceiptLineInput rice500 = line(rice, "L1", "2026-12-01", "500");
        expectCode(
                () -> receiptService.createReceipt(
                        principal,
                        new StockReceiptRequest(campus, supplier, RECEIVED.minusDays(1), "", List.of(rice500))),
                ApiCode.STOCK_RECEIPT_DATE_NOT_TODAY);

        StockReceiptResponse draft = receiptService.createReceipt(principal, request(rice500));
        CLOCK.set(RECEIVED.plusDays(1), LocalTime.of(8, 0));
        expectCode(() -> receiptService.confirmReceipt(principal, draft.id()), ApiCode.STOCK_RECEIPT_STALE_DRAFT);

        receiptService.updateReceipt(
                principal,
                draft.id(),
                new StockReceiptRequest(campus, supplier, RECEIVED.plusDays(1), "", List.of(rice500)));
        assertThat(receiptService.confirmReceipt(principal, draft.id()).status())
                .isEqualTo(StockReceiptStatus.CONFIRMED);
    }

    @Test
    @DisplayName(
            "INS-03: M1 kho khô đã ký ⇒ đồng bộ thủ công 409; trừ thêm tự động ghi vào hôm nay, giữ nguyên ngày đã ký")
    void signedDryDayPostsCorrectionsToday() {
        confirmReceipt(line(rice, "L-SOON", "2026-12-01", "500"), line(rice, "L-LATE", "2027-01-01", "1000"));
        confirmServings(10);
        CLOCK.set(MONDAY, LocalTime.of(9, 0));
        ledgerService.syncDay(campus, MONDAY); // 750: L-SOON 500, L-LATE 250
        List<Map<String, Object>> signedRows = intakeSkeleton.dry(campus, MONDAY);
        signDry(MONDAY);

        expectCode(() -> ledgerService.resyncDay(principal, campus, MONDAY), ApiCode.STOCK_DAY_INSPECTION_SIGNED);

        // Hôm sau suất ngày thứ Hai đổi (xác nhận lại): +150 rồi −300, ghi vào thứ Ba kèm correction_for thứ Hai
        LocalDate tuesday = MONDAY.plusDays(1);
        CLOCK.set(tuesday, LocalTime.of(9, 0));
        setServings(12);
        ledgerService.syncDay(campus, MONDAY);
        setServings(8);
        ledgerService.syncDay(campus, MONDAY);

        assertThat(movements(StockMovementType.ADJUST))
                .extracting(
                        m -> m.quantity().intValue(),
                        StockMovementResponse::mealDate,
                        StockMovementResponse::correctionFor)
                .containsExactlyInAnyOrder(tuple(-150, tuesday, MONDAY), tuple(300, tuesday, MONDAY));
        assertThat(movements(StockMovementType.DEDUCT))
                .allSatisfy(m -> assertThat(m.mealDate()).isEqualTo(MONDAY));
        assertThat(ledgerService.getLots(principal, campus, rice, true))
                .extracting(StockLotResponse::lotNo, l -> l.remaining().intValue())
                .containsExactly(tuple("L-SOON", 0), tuple("L-LATE", 900));
        // Đồng bộ ngày thứ Ba (không có suất) không trả/nuốt điều chỉnh của thứ Hai; M1 khô thứ Ba không lẫn dòng đó
        ledgerService.syncDay(campus, tuesday);
        assertThat(ledgerService.getLots(principal, campus, rice, true))
                .extracting(StockLotResponse::lotNo, l -> l.remaining().intValue())
                .containsExactly(tuple("L-SOON", 0), tuple("L-LATE", 900));
        assertThat(intakeSkeleton.dry(campus, tuesday)).isEmpty();

        // Sửa lại biên bản ⇒ đồng bộ thủ công được, khung M1 khô thứ Hai gồm cả phần điều chỉnh
        unsignDry(MONDAY);
        ledgerService.resyncDay(principal, campus, MONDAY);
        assertThat(intakeSkeleton.dry(campus, MONDAY)).isNotEqualTo(signedRows);
    }

    private StockReceiptResponse confirmReceipt(StockReceiptRequest.ReceiptLineInput... lines) {
        StockReceiptResponse draft = receiptService.createReceipt(principal, request(lines));
        return receiptService.confirmReceipt(principal, draft.id());
    }

    private List<StockMovementResponse> movements(StockMovementType type) {
        return ledgerService
                .getMovements(principal, campus, LocalDate.of(2020, 1, 1), LocalDate.of(2099, 1, 1), null, type, 0, 50)
                .items();
    }

    private void confirmServings(int normal) {
        lunchCount =
                insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, aggregated_at,"
                        + " confirmed_at) VALUES (" + campus + ", " + year + ", '" + MONDAY
                        + "', 'LUNCH', 'CONFIRMED', now(), now()) RETURNING id");
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal,"
                        + " substitute) VALUES (?, ?, FALSE, ?, 0, 0, ?, 0)",
                lunchCount,
                classroom,
                normal,
                normal);
    }

    // Đổi số đã chốt bằng SQL (như xác nhận lại): bỏ entity cũ trong persistence context để syncDay đọc số mới
    private void setServings(int normal) {
        entityManager.flush();
        entityManager.clear();
        jdbc.update(
                "UPDATE meal_count_lines SET normal = ?, present = ? WHERE meal_count_id = ?",
                normal,
                normal,
                lunchCount);
    }

    private void signDry(LocalDate date) {
        jdbc.update(
                "INSERT INTO inspection_sheets (campus_id, sheet_date, tab, status, signature) VALUES (?, ?, 'DRY', 'SIGNED',"
                        + " '{\"signedBy\":1,\"signedByName\":\"PHT\",\"signedAt\":\"2026-11-09T03:00:00Z\","
                        + "\"documentVersion\":1,\"imageKey\":\"sig.png\",\"contentType\":\"image/png\","
                        + "\"sizeBytes\":10}'::jsonb)",
                campus,
                date);
    }

    private void unsignDry(LocalDate date) {
        jdbc.update(
                "UPDATE inspection_sheets SET status = 'DRAFT', signature = NULL"
                        + " WHERE campus_id = ? AND sheet_date = ? AND tab = 'DRY'",
                campus,
                date);
    }

    private StockReceiptRequest request(StockReceiptRequest.ReceiptLineInput... lines) {
        return new StockReceiptRequest(campus, supplier, RECEIVED, "", List.of(lines));
    }

    private static StockReceiptRequest.ReceiptLineInput line(long foodId, String lotNo, String expiry, String grams) {
        return new StockReceiptRequest.ReceiptLineInput(
                foodId, lotNo, LocalDate.parse(expiry), new BigDecimal(grams), new BigDecimal("20000"));
    }

    private long food(String code, String name, String storage, String wastePct) {
        long foodId = insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin) VALUES ('" + code
                        + "', '"
                        + name + "', 'GRAIN', 'g', 'kg', 1000, 20000, 350, 7, 1, 77, 'TEST', " + wastePct + ", '"
                        + storage
                        + "', 'PLANT') RETURNING id");
        jdbc.update(
                "INSERT INTO food_prices (food_id, supplier_id, price, effective_from, reason)" + " SELECT id, "
                        + supplier + ", unit_price, DATE '2000-01-01', 'Giá ban đầu' FROM foods WHERE id = ?",
                foodId);
        return foodId;
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
                permissions,
                Set.of());
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
