package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.meals.DayBoardResponse;
import com.carenest.dto.meals.DryIntakeRequest;
import com.carenest.dto.meals.FreshCheckRequest;
import com.carenest.dto.meals.FreshCheckResponse;
import com.carenest.dto.meals.FreshForecastRequest;
import com.carenest.dto.meals.FreshForecastResponse;
import com.carenest.dto.meals.StockReceiptResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.DayBoardService;
import com.carenest.service.meals.FreshGoodsService;
import com.carenest.service.meals.StockReceiptService;
import com.carenest.support.MovableClock;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FreshCheckLock;
import com.carenest.utils.enums.FreshCheckResult;
import com.carenest.utils.enums.FreshCheckSource;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StockReceiptStatus;
import jakarta.persistence.EntityManager;
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

/** Hàng tươi (L4): bản dự trù theo kỳ (NUT-07), kiểm nhận 2 lần (NUT-12), trạng thái trên trang Bán trú hôm nay. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class FreshGoodsIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);
    private static final LocalDate DRAFT_MONDAY = MONDAY.plusWeeks(1);
    private static final LocalDate NO_MENU_MONDAY = MONDAY.plusWeeks(2);
    private static final MovableClock CLOCK = new MovableClock();

    /** Kiểm nhận chỉ ghi được trong ngày: test dời "hôm nay" sang ngày đang kiểm. */
    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock freshTestClock() {
            return CLOCK;
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private FreshGoodsService freshService;

    @Autowired
    private DayBoardService dayBoardService;

    @Autowired
    private StockReceiptService stockReceiptService;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vp;
    private long campus;
    private long year;
    private long classroom;
    private long ag3;
    private long rice;
    private long oil;
    private long pork;
    private long veg;

    @BeforeEach
    void setUp() {
        today(MONDAY);
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-F', 'Cơ sở F', TRUE) RETURNING id");
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        attachCampusesToYear(year);
        classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'Lớp F', " + ag3 + ") RETURNING id");
        // Hai trẻ đang học nhóm ag-3 ở điểm trường
        for (String name : List.of("Trẻ A", "Trẻ B")) {
            long child = insertId(
                    "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), '"
                            + name + "', '2022-06-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
            jdbc.update(
                    "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                    child,
                    classroom);
        }
        // Gạo kho khô; thịt tươi không thải bỏ; rau tươi thải bỏ 20% ⇒ mua = ăn ÷ 0,8
        rice = food("GAO", "Gạo", "DRY", "20", "PLANT");
        oil = food("DAU", "Dầu ăn", "DRY", "0", "PLANT");
        pork = food("THIT", "Thịt", "FRESH", "0", "ANIMAL");
        veg = food("RAU", "Rau", "FRESH", "20", "PLANT");
        long com = dish("COM", "Cơm", rice, 60);
        long thit = dish("THITRIM", "Thịt rim", pork, 40);
        long rau = dish("RAUXAO", "Rau xào", veg, 50);
        week(MONDAY, "PUBLISHED", com, thit, rau);
        week(DRAFT_MONDAY, "DRAFT", com, thit, rau);
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

    private void attachCampusesToYear(long schoolYearId) {
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address, phone, status) "
                        + "SELECT ?, id, code, name, short_name, address, phone, status FROM campuses",
                schoolYearId);
    }

    @Test
    @DisplayName("NUT-07/CHD-13: dự trù không đếm trẻ không báo ăn")
    void previewCountsOnlyMealRegisteredChildren() {
        jdbc.update("UPDATE children SET meal_registered = FALSE WHERE full_name = 'Trẻ B'");

        FreshForecastResponse preview = freshService.previewForecast(principal, request(MONDAY, FRIDAY, null));

        assertThat(preview.counts()).singleElement().satisfies(c -> {
            assertThat(c.ageGroupId()).isEqualTo(ag3);
            assertThat(c.enrolledCount()).isEqualTo(1);
            assertThat(c.expectedCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName(
            "NUT-07: xem trước không gửi sĩ số ⇒ điền sẵn số đang học; sửa số dự kiến ⇒ lượng theo suất, chỉ hàng tươi")
    void previewPrefillsEnrolmentAndScalesWithExpectedCount() {
        setOff(FRIDAY);

        FreshForecastResponse prefilled = freshService.previewForecast(principal, request(MONDAY, FRIDAY, null));

        assertThat(prefilled.id()).isNull();
        assertThat(prefilled.counts()).singleElement().satisfies(c -> {
            assertThat(c.ageGroupId()).isEqualTo(ag3);
            assertThat(c.enrolledCount()).isEqualTo(2);
            assertThat(c.expectedCount()).isEqualTo(2);
        });
        // Thứ Hai–Năm (thứ Sáu nghỉ): thịt 2 × 40 g × 4 ngày; rau 2 × 50 g ÷ 0,8 × 4 ngày; gạo (khô) không có
        assertThat(prefilled.lines())
                .extracting(
                        l -> l.food().code(),
                        l -> l.totalQty().intValue(),
                        l -> l.byDate().size())
                .containsExactlyInAnyOrder(tuple("THIT", 320, 4), tuple("RAU", 500, 4));
        assertThat(prefilled.days())
                .extracting(d -> d.date(), d -> d.off(), d -> d.planStatus())
                .contains(tuple(MONDAY, false, "PUBLISHED"), tuple(FRIDAY, true, "PUBLISHED"));

        FreshForecastResponse edited = freshService.previewForecast(
                principal, request(MONDAY, FRIDAY, List.of(new FreshForecastRequest.FreshForecastCountInput(ag3, 5))));

        assertThat(edited.counts()).singleElement().satisfies(c -> {
            assertThat(c.enrolledCount()).isEqualTo(2);
            assertThat(c.expectedCount()).isEqualTo(5);
        });
        assertThat(edited.lines())
                .filteredOn(l -> "THIT".equals(l.food().code()))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.totalQty()).isEqualByComparingTo("800");
                    assertThat(l.byDate())
                            .extracting(d -> d.date(), d -> d.quantity().intValue())
                            .contains(tuple(MONDAY, 200))
                            .doesNotContain(tuple(FRIDAY, 200));
                });
        assertThat(edited.lines())
                .filteredOn(l -> "RAU".equals(l.food().code()))
                .singleElement()
                .satisfies(l -> assertThat(l.totalQty()).isEqualByComparingTo("1250"));
    }

    @Test
    @DisplayName(
            "NUT-12: đơn vị mua đếm được ⇒ lượng đặt từng ngày làm tròn lên nguyên đơn vị; đơn vị cân đo giữ số lẻ")
    void forecastRoundsCountablePurchaseUnitUpPerDay() {
        jdbc.update("UPDATE foods SET purchase_unit = 'hộp 100 g', purchase_unit_size = 100 WHERE id = ?", pork);

        FreshForecastResponse preview = freshService.previewForecast(principal, request(MONDAY, FRIDAY, null));

        // Thịt 2 × 40 g = 80 g/ngày ⇒ đặt 1 hộp 100 g mỗi ngày; rau (kg) giữ 125 g/ngày
        assertThat(preview.lines())
                .filteredOn(l -> "THIT".equals(l.food().code()))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.byDate())
                            .allSatisfy(d -> assertThat(d.quantity()).isEqualByComparingTo("100"));
                    assertThat(l.totalQty())
                            .isEqualByComparingTo(
                                    BigDecimal.valueOf(100L * l.byDate().size()));
                });
        assertThat(preview.lines())
                .filteredOn(l -> "RAU".equals(l.food().code()))
                .singleElement()
                .satisfies(l -> assertThat(l.byDate())
                        .allSatisfy(d -> assertThat(d.quantity()).isEqualByComparingTo("125")));
    }

    @Test
    @DisplayName("NUT-07: tuần nháp vẫn tính, tuần chưa có thực đơn không có dòng; thứ Bảy, Chủ nhật là ngày nghỉ")
    void draftWeekCountsAndMissingMenuHasNoLines() {
        FreshForecastResponse draft =
                freshService.previewForecast(principal, request(DRAFT_MONDAY, DRAFT_MONDAY.plusDays(6), null));
        assertThat(draft.lines()).hasSize(2);
        assertThat(draft.days()).hasSize(7);
        assertThat(draft.days())
                .extracting(d -> d.date().getDayOfWeek().getValue(), d -> d.off(), d -> d.planStatus())
                .contains(tuple(1, false, "DRAFT"), tuple(6, true, "DRAFT"), tuple(7, true, "DRAFT"));

        FreshForecastResponse none =
                freshService.previewForecast(principal, request(NO_MENU_MONDAY, NO_MENU_MONDAY.plusDays(4), null));
        assertThat(none.lines()).isEmpty();
        assertThat(none.days()).extracting(d -> d.planStatus()).containsOnly("NONE");
    }

    @Test
    @DisplayName(
            "Kỳ dự trù: ngày bắt đầu sau ngày kết thúc hoặc quá 31 ngày ⇒ 6041; đúng 31 ngày được; nhóm tuổi lạ ⇒ 6024")
    void periodAndAgeGroupAreValidated() {
        expectCode(
                () -> freshService.previewForecast(principal, request(FRIDAY, MONDAY, null)),
                ApiCode.FRESH_FORECAST_PERIOD_INVALID);
        expectCode(
                () -> freshService.previewForecast(principal, request(MONDAY, MONDAY.plusDays(31), null)),
                ApiCode.FRESH_FORECAST_PERIOD_INVALID);
        assertThat(freshService
                        .previewForecast(principal, request(MONDAY, MONDAY.plusDays(30), null))
                        .days())
                .hasSize(31);
        expectCode(
                () -> freshService.previewForecast(
                        principal,
                        request(MONDAY, FRIDAY, List.of(new FreshForecastRequest.FreshForecastCountInput(-1L, 3)))),
                ApiCode.AGE_GROUP_INVALID);
        expectCode(
                () -> freshService.previewForecast(
                        principal,
                        request(
                                MONDAY,
                                FRIDAY,
                                List.of(
                                        new FreshForecastRequest.FreshForecastCountInput(ag3, 3),
                                        new FreshForecastRequest.FreshForecastCountInput(ag3, 4)))),
                ApiCode.AGE_GROUP_INVALID);
    }

    @Test
    @DisplayName(
            "NUT-12: lần 1 cần nhận = dòng bản dự trù mới nhất của ngày; thiếu dòng, thực phẩm lạ, trùng ⇒ 6043; ghi đè tại chỗ")
    void roundOneUsesForecastLines() {
        FreshCheckResponse before = freshService.getChecks(principal, campus, MONDAY);
        assertThat(before.rounds().getFirst().available()).isFalse();
        assertThat(before.rounds().getFirst().unavailableReason())
                .isEqualTo("Chưa có bản dự trù hàng tươi cho ngày này");
        expectCode(
                () -> freshService.recordCheck(
                        principal, campus, MONDAY, 1, check(line(pork, FreshCheckResult.ENOUGH))),
                ApiCode.FRESH_CHECK_NOT_AVAILABLE);

        freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(5))));
        FreshForecastResponse latest =
                freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(3))));

        FreshCheckResponse pending = freshService.getChecks(principal, campus, MONDAY);
        FreshCheckResponse.FreshCheckRound round1 = pending.rounds().getFirst();
        assertThat(round1.available()).isTrue();
        assertThat(round1.recorded()).isFalse();
        assertThat(round1.source()).isEqualTo(FreshCheckSource.FORECAST);
        assertThat(round1.forecastId()).isEqualTo(latest.id());
        assertThat(round1.lines())
                .extracting(l -> l.food().code(), l -> l.expectedQty().intValue(), l -> l.result())
                .containsExactlyInAnyOrder(tuple("THIT", 120, null), tuple("RAU", 187, null));

        expectCode(
                () -> freshService.recordCheck(
                        principal, campus, MONDAY, 1, check(line(pork, FreshCheckResult.ENOUGH))),
                ApiCode.FRESH_CHECK_LINES_MISMATCH);
        expectCode(
                () -> freshService.recordCheck(
                        principal,
                        campus,
                        MONDAY,
                        1,
                        check(
                                line(pork, FreshCheckResult.ENOUGH),
                                line(veg, FreshCheckResult.ENOUGH),
                                line(rice, FreshCheckResult.ENOUGH))),
                ApiCode.FRESH_CHECK_LINES_MISMATCH);
        expectCode(
                () -> freshService.recordCheck(
                        principal,
                        campus,
                        MONDAY,
                        1,
                        check(
                                line(pork, FreshCheckResult.ENOUGH),
                                line(pork, FreshCheckResult.SHORT),
                                line(veg, FreshCheckResult.ENOUGH))),
                ApiCode.FRESH_CHECK_LINES_MISMATCH);

        FreshCheckResponse recorded = freshService.recordCheck(
                principal,
                campus,
                MONDAY,
                1,
                check(
                        new FreshCheckRequest.FreshCheckLineInput(
                                pork, new BigDecimal("118.50"), FreshCheckResult.SHORT, " Thiếu chút ", null),
                        line(veg, FreshCheckResult.ENOUGH)));
        assertThat(recorded.rounds().getFirst().recorded()).isTrue();
        assertThat(recorded.rounds().getFirst().checkedBy()).isEqualTo(principal.userId());
        assertThat(recorded.rounds().getFirst().lines())
                .extracting(l -> l.food().code(), l -> l.result(), l -> l.note())
                .containsExactlyInAnyOrder(
                        tuple("THIT", FreshCheckResult.SHORT, "Thiếu chút"), tuple("RAU", FreshCheckResult.ENOUGH, ""));
        assertThat(recorded.rounds().get(1).recorded()).isFalse();

        // Ghi đè: sửa tại chỗ, không nhân đôi dòng
        FreshCheckResponse overwritten = freshService.recordCheck(
                principal,
                campus,
                MONDAY,
                1,
                check(line(pork, FreshCheckResult.OVER), line(veg, FreshCheckResult.SHORT)));
        assertThat(overwritten.rounds().getFirst().lines())
                .extracting(l -> l.food().code(), l -> l.result())
                .containsExactlyInAnyOrder(tuple("THIT", FreshCheckResult.OVER), tuple("RAU", FreshCheckResult.SHORT));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fresh_checks", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fresh_check_lines", Integer.class))
                .isEqualTo(2);

        // INS-01: nhà cung cấp của lần giao ghi kèm kiểm nhận; không có thì bỏ; không tồn tại ⇒ 6025
        long supplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, phone, address, delivery_person)"
                        + " VALUES ('Cơ sở Hòa Phát', '0901234567', '12 Lê Lợi', 'Anh Nam') RETURNING id",
                Long.class);
        FreshCheckResponse withSupplier = freshService.recordCheck(
                principal,
                campus,
                MONDAY,
                1,
                new FreshCheckRequest(
                        "",
                        List.of(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH)),
                        supplier));
        assertThat(withSupplier.rounds().getFirst().supplier()).satisfies(s -> {
            assertThat(s.id()).isEqualTo(supplier);
            assertThat(s.name()).isEqualTo("Cơ sở Hòa Phát");
            assertThat(s.phone()).isEqualTo("0901234567");
            assertThat(s.deliveryPerson()).isEqualTo("Anh Nam");
        });
        assertThat(withSupplier.rounds().get(1).supplier()).isNull();
        expectCode(
                () -> freshService.recordCheck(
                        principal,
                        campus,
                        MONDAY,
                        1,
                        new FreshCheckRequest(
                                "",
                                List.of(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH)),
                                -1L)),
                ApiCode.SUPPLIER_NOT_FOUND);
        assertThat(freshService
                        .recordCheck(
                                principal,
                                campus,
                                MONDAY,
                                1,
                                check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH)))
                        .rounds()
                        .getFirst()
                        .supplier())
                .isNull();
    }

    @Test
    @DisplayName("NUT-12: cùng kỳ chỉ bản mới nhất đang dùng, bản cũ đã thay thế; lần 1 đã ghi giữ bản lúc ghi")
    void newerForecastReplacesOlderButSavedRoundOneKeepsItsForecast() {
        FreshForecastResponse older =
                freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(5))));
        FreshForecastResponse newer =
                freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(3))));

        assertThat(freshService.getForecasts(principal, campus))
                .extracting(f -> f.id(), f -> f.version(), f -> f.current())
                .containsExactly(tuple(newer.id(), 2, true), tuple(older.id(), 1, false));

        freshService.recordCheck(
                principal,
                campus,
                MONDAY,
                1,
                check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH)));
        FreshForecastResponse latest =
                freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(8))));

        FreshCheckResponse.FreshCheckRound round1 =
                freshService.getChecks(principal, campus, MONDAY).rounds().getFirst();
        assertThat(round1.forecastId()).isEqualTo(newer.id());
        assertThat(round1.lines())
                .extracting(l -> l.food().code(), l -> l.expectedQty().intValue())
                .containsExactlyInAnyOrder(tuple("THIT", 120), tuple("RAU", 187));
        assertThat(freshService.getForecasts(principal, campus))
                .extracting(f -> f.id(), f -> f.current())
                .containsExactly(tuple(latest.id(), true), tuple(newer.id(), false), tuple(older.id(), false));
    }

    @Test
    @DisplayName(
            "NUT-12: bản dự trù đã làm căn cứ kiểm nhận không xóa được; bản chưa dùng xóa được; danh sách mới nhất trước")
    void deletingForecastUsedByChecksIsBlocked() {
        FreshForecastResponse first =
                freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(5))));
        FreshForecastResponse second =
                freshService.createForecast(principal, request(MONDAY, MONDAY, List.of(count(3))));
        assertThat(freshService.getForecasts(vp, campus))
                .extracting(f -> f.id(), f -> f.foodCount(), f -> f.deletable())
                .containsExactly(tuple(second.id(), 2, true), tuple(first.id(), 2, true));
        assertThat(freshService.getForecast(vp, first.id()).lines()).hasSize(2);

        // Lần 1 so bản mới nhất có kỳ chứa ngày (bản thứ hai) ⇒ bản đó thành căn cứ kiểm nhận, không xóa được
        freshService.recordCheck(
                principal,
                campus,
                MONDAY,
                1,
                check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH)));
        entityManager.flush();
        expectCode(() -> freshService.deleteForecast(principal, second.id()), ApiCode.FRESH_FORECAST_IN_USE);
        assertThat(freshService.getForecasts(vp, campus))
                .extracting(f -> f.id(), f -> f.deletable())
                .containsExactly(tuple(second.id(), false), tuple(first.id(), true));

        freshService.deleteForecast(principal, first.id());
        entityManager.flush();
        entityManager.clear();
        assertThat(jdbc.queryForObject("SELECT forecast_id FROM fresh_checks", Long.class))
                .isEqualTo(second.id());
        assertThat(freshService.getForecasts(vp, campus))
                .extracting(f -> f.id())
                .containsExactly(second.id());
        expectCode(() -> freshService.deleteForecast(principal, first.id()), ApiCode.FRESH_FORECAST_NOT_FOUND);
    }

    @Test
    @DisplayName("NUT-12: kỳ dự trù bắt đầu trước hôm nay bị chặn; bắt đầu hôm nay được")
    void forecastCannotStartInPast() {
        today(MONDAY.plusDays(1));
        expectCode(
                () -> freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(5)))),
                ApiCode.FRESH_FORECAST_PERIOD_PAST);
        assertThat(freshService
                        .createForecast(principal, request(MONDAY.plusDays(1), FRIDAY, List.of(count(5))))
                        .id())
                .isNotNull();
    }

    @Test
    @DisplayName(
            "NUT-12: lần 2 cần thực đơn công bố, ngày không nghỉ và suất đã xác nhận; cần nhận = nhu cầu hàng tươi theo suất chốt")
    void roundTwoNeedsConfirmedServings() {
        // Chưa có sổ báo ăn
        FreshCheckResponse.FreshCheckRound waiting =
                freshService.getChecks(principal, campus, MONDAY).rounds().get(1);
        assertThat(waiting.available()).isFalse();
        assertThat(waiting.unavailableReason()).isEqualTo("Suất ăn của ngày chưa được xác nhận");
        assertThat(waiting.source()).isEqualTo(FreshCheckSource.CONFIRMED_SERVINGS);
        expectCode(
                () -> freshService.recordCheck(principal, campus, MONDAY, 2, check()),
                ApiCode.FRESH_CHECK_NOT_AVAILABLE);

        // Sổ có nhưng chưa xác nhận
        long count = insertCount("PENDING_CONFIRMATION");
        addLine(count, 10);
        expectCode(
                () -> freshService.recordCheck(principal, campus, MONDAY, 2, check()),
                ApiCode.FRESH_CHECK_NOT_AVAILABLE);

        // Tuần nháp, ngày chưa có thực đơn công bố
        FreshCheckResponse.FreshCheckRound draftRound =
                freshService.getChecks(principal, campus, DRAFT_MONDAY).rounds().get(1);
        assertThat(draftRound.available()).isFalse();
        assertThat(draftRound.unavailableReason()).isEqualTo("Thực đơn tuần chưa công bố");
        today(DRAFT_MONDAY);
        expectCode(
                () -> freshService.recordCheck(principal, campus, DRAFT_MONDAY, 2, check()),
                ApiCode.FRESH_CHECK_NOT_AVAILABLE);
        today(MONDAY);

        // Ngày nghỉ
        setOff(FRIDAY);
        entityManager.flush();
        entityManager.clear();
        assertThat(freshService
                        .getChecks(principal, campus, FRIDAY)
                        .rounds()
                        .get(1)
                        .unavailableReason())
                .isEqualTo("Ngày nghỉ, không phục vụ bữa");
        entityManager.flush();
        entityManager.clear();

        // Xác nhận suất 10: thịt 10 × 40 g; rau 10 × 50 g ÷ 0,8; gạo (khô) không có
        jdbc.update("UPDATE meal_counts SET status = 'CONFIRMED', confirmed_at = now() WHERE id = ?", count);
        FreshCheckResponse.FreshCheckRound ready =
                freshService.getChecks(principal, campus, MONDAY).rounds().get(1);
        assertThat(ready.available()).isTrue();
        assertThat(ready.unavailableReason()).isNull();
        assertThat(ready.forecastId()).isNull();
        assertThat(ready.lines())
                .extracting(l -> l.food().code(), l -> l.expectedQty().intValue())
                .containsExactlyInAnyOrder(tuple("THIT", 400), tuple("RAU", 625));

        FreshCheckResponse recorded = freshService.recordCheck(
                principal,
                campus,
                MONDAY,
                2,
                check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.SHORT)));
        assertThat(recorded.rounds().get(1).recorded()).isTrue();
        assertThat(recorded.rounds().get(1).lines())
                .extracting(l -> l.food().code(), l -> l.result())
                .containsExactlyInAnyOrder(
                        tuple("THIT", FreshCheckResult.ENOUGH), tuple("RAU", FreshCheckResult.SHORT));
        // Lần 2 không gắn bản dự trù và không ảnh hưởng lần 1
        assertThat(recorded.rounds().get(1).forecastId()).isNull();
        assertThat(recorded.rounds().getFirst().recorded()).isFalse();
    }

    @Test
    @DisplayName("NUT-12: lần 2 với đơn vị mua đếm được ⇒ lượng cần nhận làm tròn lên nguyên đơn vị")
    void roundTwoRoundsCountablePurchaseUnitUp() {
        jdbc.update("UPDATE foods SET purchase_unit = 'hộp 150 g', purchase_unit_size = 150 WHERE id = ?", pork);
        long count = insertCount("PENDING_CONFIRMATION");
        addLine(count, 10);
        jdbc.update("UPDATE meal_counts SET status = 'CONFIRMED', confirmed_at = now() WHERE id = ?", count);

        FreshCheckResponse.FreshCheckRound ready =
                freshService.getChecks(principal, campus, MONDAY).rounds().get(1);

        // Thịt 10 × 40 g = 400 g ⇒ 3 hộp 150 g = 450 g; rau (kg) giữ 625 g
        assertThat(ready.lines())
                .extracting(l -> l.food().code(), l -> l.expectedQty().intValue())
                .containsExactlyInAnyOrder(tuple("THIT", 450), tuple("RAU", 625));
    }

    @Test
    @DisplayName("AUTH-20: PHT chưa được cấp STOCK_MANAGE xem dự trù, kiểm nhận được nhưng lưu/xóa/ghi bị chặn (6033)")
    void writesNeedStockManage() {
        FreshForecastResponse saved =
                freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(5))));

        expectCode(() -> freshService.createForecast(vp, request(MONDAY, FRIDAY, null)), ApiCode.STOCK_ACCESS_DENIED);
        expectCode(() -> freshService.deleteForecast(vp, saved.id()), ApiCode.STOCK_ACCESS_DENIED);
        expectCode(
                () -> freshService.recordCheck(
                        vp,
                        campus,
                        MONDAY,
                        1,
                        check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH))),
                ApiCode.STOCK_ACCESS_DENIED);

        assertThat(freshService.getForecasts(vp, campus)).hasSize(1);
        FreshForecastResponse viewed = freshService.getForecast(vp, saved.id());
        assertThat(viewed.canManage()).isFalse();
        assertThat(freshService
                        .previewForecast(vp, request(MONDAY, FRIDAY, null))
                        .canManage())
                .isFalse();
        FreshCheckResponse checks = freshService.getChecks(vp, campus, MONDAY);
        assertThat(checks.canManage()).isFalse();
        assertThat(checks.rounds().getFirst().lines()).hasSize(2);

        CurrentUser granted = withPermissions(vp, Set.of(Permission.STOCK_MANAGE));
        assertThat(freshService
                        .createForecast(granted, request(MONDAY, FRIDAY, null))
                        .canManage())
                .isTrue();
        assertThat(freshService
                        .recordCheck(
                                granted,
                                campus,
                                MONDAY,
                                1,
                                check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH)))
                        .rounds()
                        .getFirst()
                        .recorded())
                .isTrue();
    }

    @Test
    @DisplayName("Kiểm nhận chỉ ghi/sửa trong ngày: qua ngày thì khóa, ngày chưa tới thì chặn, vẫn xem được")
    void checksLockOutsideToday() {
        freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(5))));
        FreshCheckRequest enough = check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH));
        assertThat(freshService.getChecks(principal, campus, MONDAY).editable()).isTrue();
        freshService.recordCheck(principal, campus, MONDAY, 1, enough);

        // Ngày chưa tới
        expectCode(
                () -> freshService.recordCheck(principal, campus, MONDAY.plusDays(1), 1, enough),
                ApiCode.FRESH_CHECK_DATE_LOCKED);

        // Sang hôm sau: bản hôm qua chỉ còn xem
        today(MONDAY.plusDays(1));
        FreshCheckResponse yesterday = freshService.getChecks(principal, campus, MONDAY);
        assertThat(yesterday.editable()).isFalse();
        assertThat(yesterday.lockReason()).isEqualTo(FreshCheckLock.NOT_TODAY);
        assertThat(yesterday.rounds().getFirst().recorded()).isTrue();
        expectCode(
                () -> freshService.recordCheck(
                        principal,
                        campus,
                        MONDAY,
                        1,
                        check(line(pork, FreshCheckResult.SHORT), line(veg, FreshCheckResult.ENOUGH))),
                ApiCode.FRESH_CHECK_DATE_LOCKED);
        assertThat(freshService
                        .getChecks(principal, campus, MONDAY)
                        .rounds()
                        .getFirst()
                        .lines())
                .extracting(FreshCheckResponse.FreshCheckLine::result)
                .containsOnly(FreshCheckResult.ENOUGH);
    }

    @Test
    @DisplayName("Ký M1 tươi khóa kiểm nhận cả hai lần; Sửa lại biên bản (về nháp) thì mở lại")
    void signedFreshSheetLocksChecks() {
        freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(5))));
        FreshCheckRequest enough = check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH));
        freshService.recordCheck(principal, campus, MONDAY, 1, enough);
        jdbc.update(
                "INSERT INTO inspection_sheets (campus_id, sheet_date, tab, status, signature)"
                        + " VALUES (?, ?, 'FRESH', 'SIGNED', '{}'::jsonb)",
                campus,
                MONDAY);
        // Biên bản khác (M1 khô) đã ký không ảnh hưởng
        jdbc.update(
                "INSERT INTO inspection_sheets (campus_id, sheet_date, tab, status, signature)"
                        + " VALUES (?, ?, 'DRY', 'SIGNED', '{}'::jsonb)",
                campus,
                MONDAY.plusDays(1));

        FreshCheckResponse signed = freshService.getChecks(principal, campus, MONDAY);
        assertThat(signed.editable()).isFalse();
        assertThat(signed.lockReason()).isEqualTo(FreshCheckLock.M1_SIGNED);
        expectCode(() -> freshService.recordCheck(principal, campus, MONDAY, 1, enough), ApiCode.FRESH_CHECK_SIGNED);
        expectCode(() -> freshService.recordCheck(principal, campus, MONDAY, 2, check()), ApiCode.FRESH_CHECK_SIGNED);

        jdbc.update(
                "UPDATE inspection_sheets SET status = 'DRAFT', signature = NULL"
                        + " WHERE campus_id = ? AND sheet_date = ? AND tab = 'FRESH'",
                campus,
                MONDAY);
        assertThat(freshService.getChecks(principal, campus, MONDAY).lockReason())
                .isNull();
        freshService.recordCheck(
                principal,
                campus,
                MONDAY,
                1,
                check(line(pork, FreshCheckResult.SHORT), line(veg, FreshCheckResult.ENOUGH)));
    }

    @Test
    @DisplayName("Điểm trường không tồn tại ⇒ 404; lần kiểm ngoài 1|2 bị từ chối")
    void rejectsUnknownCampusAndRound() {
        expectCode(
                () -> freshService.previewForecast(
                        principal, new FreshForecastRequest(-1L, MONDAY, FRIDAY, null, null)),
                ApiCode.CAMPUS_NOT_FOUND);
        expectCode(() -> freshService.recordCheck(principal, -1L, MONDAY, 1, check()), ApiCode.CAMPUS_NOT_FOUND);
        expectCode(() -> freshService.recordCheck(principal, campus, MONDAY, 3, check()), ApiCode.BAD_REQUEST);
    }

    @Test
    @DisplayName(
            "Hub: freshReceiptStatus theo NO_FORECAST → ROUND1_PENDING → ROUND1_DONE → ROUND2_PENDING → DONE; ngày nghỉ DAY_OFF")
    void dayBoardFreshStatus() {
        assertThat(freshStatus(MONDAY)).isEqualTo("NO_FORECAST");

        freshService.createForecast(principal, request(MONDAY, FRIDAY, List.of(count(5))));
        assertThat(freshStatus(MONDAY)).isEqualTo("ROUND1_PENDING");

        freshService.recordCheck(
                principal,
                campus,
                MONDAY,
                1,
                check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH)));
        // Suất chưa xác nhận ⇒ chưa kiểm lần 2 được
        assertThat(freshStatus(MONDAY)).isEqualTo("ROUND1_DONE");

        long count = insertCount("CONFIRMED");
        addLine(count, 10);
        assertThat(freshStatus(MONDAY)).isEqualTo("ROUND2_PENDING");

        freshService.recordCheck(
                principal,
                campus,
                MONDAY,
                2,
                check(line(pork, FreshCheckResult.ENOUGH), line(veg, FreshCheckResult.ENOUGH)));
        assertThat(freshStatus(MONDAY)).isEqualTo("DONE");
        assertThat(freshStatus(MONDAY.plusDays(1))).isEqualTo("ROUND1_PENDING");

        // Thứ Bảy và ngày nghỉ của tuần
        assertThat(freshStatus(MONDAY.plusDays(5))).isEqualTo("DAY_OFF");
        setOff(FRIDAY);
        entityManager.flush();
        entityManager.clear();
        assertThat(freshStatus(FRIDAY)).isEqualTo("DAY_OFF");
        assertThat(board(vp, MONDAY).foodSampleStatus()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("NUT-12: bản dự trù lưu lượng hàng khô đặt PHT sửa; chỉ nhận thực phẩm kho khô, không trùng")
    void forecastStoresEditedDryOrder() {
        LocalDate today = LocalDate.now(clock);

        FreshForecastResponse saved = freshService.createForecast(
                principal, dryOrderRequest(today, dryOrder(rice, "1500"), dryOrder(oil, "0")));

        assertThat(freshService.getForecast(principal, saved.id()).dryOrder())
                .extracting(line -> line.food().id(), FreshForecastResponse.DryOrderLine::quantity)
                .containsExactly(tuple(rice, new BigDecimal("1500.00")));
        expectCode(
                () -> freshService.createForecast(principal, dryOrderRequest(today, dryOrder(pork, "100"))),
                ApiCode.STOCK_FOOD_NOT_DRY);
        expectCode(
                () -> freshService.createForecast(
                        principal, dryOrderRequest(today, dryOrder(rice, "100"), dryOrder(rice, "200"))),
                ApiCode.DRY_ORDER_INVALID);
    }

    @Test
    @DisplayName(
            "NUT-11/NUT-12: đợt hàng khô ⇒ phiếu đã xác nhận từ dòng đạt; đã nhận cộng dồn theo kỳ, hủy phiếu thì giảm")
    void dryIntakeCreatesConfirmedReceiptAndTracksReceived() {
        LocalDate today = LocalDate.now(clock);
        long forecastId = freshService
                .createForecast(principal, dryOrderRequest(today, dryOrder(rice, "3000")))
                .id();
        long supplier = supplier();

        FreshCheckResponse first = freshService.recordDryIntake(
                principal,
                campus,
                today,
                intake(
                        supplier,
                        intakeLine(rice, "L1", "1000", true, null),
                        intakeLine(oil, "D1", "500", true, null),
                        intakeLine(rice, "L2", "200", false, " Trả lại NCC ")));

        assertThat(first.dry().forecastId()).isEqualTo(forecastId);
        assertThat(first.dry().receipts()).singleElement().satisfies(receipt -> {
            assertThat(receipt.status()).isEqualTo(StockReceiptStatus.CONFIRMED);
            assertThat(receipt.fromIntake()).isTrue();
            assertThat(receipt.forecastId()).isEqualTo(forecastId);
            assertThat(receipt.lines())
                    .extracting(StockReceiptResponse.ReceiptLine::lotNo)
                    .containsExactlyInAnyOrder("L1", "D1");
            assertThat(receipt.rejectedLines()).singleElement().satisfies(rejected -> {
                assertThat(rejected.food().id()).isEqualTo(rice);
                assertThat(rejected.note()).isEqualTo("Trả lại NCC");
            });
        });
        assertThat(first.dry().orders())
                .extracting(o -> o.food().id(), FreshCheckResponse.DryOrderStatus::ordered, o -> o.received())
                .containsExactlyInAnyOrder(
                        tuple(rice, new BigDecimal("3000.00"), new BigDecimal("1000.00")),
                        tuple(oil, null, new BigDecimal("500.00")));
        assertThat(stockBalance(rice)).isEqualByComparingTo("1000");

        freshService.recordDryIntake(
                principal, campus, today, intake(supplier, intakeLine(rice, "L3", "2000", true, null)));
        assertThat(ricePart(freshService.getChecks(principal, campus, today)).received())
                .isEqualByComparingTo("3000");

        stockReceiptService.cancelReceipt(
                principal, first.dry().receipts().getFirst().id(), "Ghi nhầm lô");
        FreshCheckResponse afterCancel = freshService.getChecks(principal, campus, today);
        assertThat(ricePart(afterCancel).received()).isEqualByComparingTo("2000");
        assertThat(afterCancel.dry().receipts())
                .extracting(StockReceiptResponse::status)
                .containsExactly(StockReceiptStatus.CANCELLED, StockReceiptStatus.CONFIRMED);
    }

    @Test
    @DisplayName("NUT-11: dòng không đạt phải có biện pháp xử lý, chỉ ghi trong ngày, cần STOCK_MANAGE")
    void dryIntakeRejectsInvalidRequests() {
        LocalDate today = LocalDate.now(clock);
        long supplier = supplier();

        expectCode(
                () -> freshService.recordDryIntake(
                        principal, campus, today, intake(supplier, intakeLine(rice, "L1", "100", false, " "))),
                ApiCode.DRY_INTAKE_NOTE_REQUIRED);
        expectCode(
                () -> freshService.recordDryIntake(
                        principal,
                        campus,
                        today.minusDays(1),
                        intake(supplier, intakeLine(rice, "L1", "100", true, null))),
                ApiCode.DRY_INTAKE_NOT_TODAY);
        expectCode(
                () -> freshService.recordDryIntake(
                        withPermissions(vp, Set.of()),
                        campus,
                        today,
                        intake(supplier, intakeLine(rice, "L1", "100", true, null))),
                ApiCode.STOCK_ACCESS_DENIED);
        expectCode(
                () -> freshService.recordDryIntake(
                        principal, campus, today, intake(supplier, intakeLine(pork, "T1", "100", true, null))),
                ApiCode.STOCK_FOOD_NOT_DRY);
    }

    @Test
    @DisplayName("NUT-12: ngày không có bản dự trù vẫn nhận hàng khô, mọi dòng là ngoài đơn")
    void dryIntakeWithoutForecast() {
        LocalDate today = LocalDate.now(clock);

        FreshCheckResponse response = freshService.recordDryIntake(
                principal, campus, today, intake(supplier(), intakeLine(rice, "L1", "100", true, null)));

        assertThat(response.dry().forecastId()).isNull();
        assertThat(response.dry().orders()).isEmpty();
        assertThat(response.dry().receipts()).singleElement().satisfies(receipt -> {
            assertThat(receipt.forecastId()).isNull();
            assertThat(receipt.lines()).hasSize(1);
        });
    }

    private FreshCheckResponse.DryOrderStatus ricePart(FreshCheckResponse response) {
        return response.dry().orders().stream()
                .filter(o -> o.food().id().equals(rice))
                .findFirst()
                .orElseThrow();
    }

    private BigDecimal stockBalance(long foodId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(m.quantity), 0) FROM stock_movements m JOIN stock_receipt_lines l ON l.id = m.lot_id"
                        + " JOIN stock_receipts r ON r.id = l.receipt_id WHERE r.campus_id = ? AND l.food_id = ?",
                BigDecimal.class,
                campus,
                foodId);
    }

    private long supplier() {
        return insertId("INSERT INTO suppliers (name, phone, address, delivery_person)"
                + " VALUES ('NCC khô', '0901234567', '12 Lê Lợi', 'Anh Nam') RETURNING id");
    }

    private FreshForecastRequest dryOrderRequest(LocalDate from, FreshForecastRequest.DryOrderInput... order) {
        return new FreshForecastRequest(campus, from, from.plusDays(6), "", null, List.of(order));
    }

    private static FreshForecastRequest.DryOrderInput dryOrder(long foodId, String quantity) {
        return new FreshForecastRequest.DryOrderInput(foodId, new BigDecimal(quantity));
    }

    private static DryIntakeRequest intake(long supplierId, DryIntakeRequest.DryIntakeLineInput... lines) {
        return new DryIntakeRequest(supplierId, "", List.of(lines));
    }

    private DryIntakeRequest.DryIntakeLineInput intakeLine(
            long foodId, String lotNo, String quantity, boolean passed, String note) {
        return new DryIntakeRequest.DryIntakeLineInput(
                foodId,
                lotNo,
                LocalDate.now(clock).plusMonths(6),
                new BigDecimal(quantity),
                new BigDecimal("20000"),
                passed,
                note);
    }

    private String freshStatus(LocalDate date) {
        return board(principal, date).freshReceiptStatus();
    }

    private DayBoardResponse.CampusBoard board(CurrentUser user, LocalDate date) {
        return dayBoardService.getDayBoard(user, date, campus).campuses().stream()
                .filter(c -> c.campusId().equals(campus))
                .findFirst()
                .orElseThrow();
    }

    private FreshForecastRequest request(
            LocalDate from, LocalDate to, List<FreshForecastRequest.FreshForecastCountInput> counts) {
        return new FreshForecastRequest(campus, from, to, " Ghi chú ", counts);
    }

    private FreshForecastRequest.FreshForecastCountInput count(int expected) {
        return new FreshForecastRequest.FreshForecastCountInput(ag3, expected);
    }

    private static void today(LocalDate date) {
        CLOCK.set(date, LocalTime.of(9, 0));
    }

    /** Đồng hồ dời được; dùng chung cho cả lớp test nên mỗi test đặt lại ở {@code setUp}. */
    private static FreshCheckRequest check(FreshCheckRequest.FreshCheckLineInput... lines) {
        return new FreshCheckRequest("", List.of(lines), null);
    }

    private static FreshCheckRequest.FreshCheckLineInput line(long foodId, FreshCheckResult result) {
        return new FreshCheckRequest.FreshCheckLineInput(foodId, null, result, null, null);
    }

    private void setOff(LocalDate date) {
        jdbc.update(
                "UPDATE meal_plan_weeks w SET days = (SELECT jsonb_agg(CASE WHEN (e ->> 'date')::date = ?"
                        + " THEN jsonb_set(e, '{off}', 'true') ELSE e END) FROM jsonb_array_elements(w.days) e)"
                        + " WHERE w.days @> jsonb_build_array(jsonb_build_object('date', ?::text))",
                date,
                date.toString());
    }

    private long insertCount(String status) {
        return insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, aggregated_at,"
                + " confirmed_at) VALUES (" + campus + ", " + year + ", '" + MONDAY + "', 'LUNCH', '" + status
                + "', now(), " + ("CONFIRMED".equals(status) ? "now()" : "NULL") + ") RETURNING id");
    }

    private void addLine(long countId, int normal) {
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal,"
                        + " substitute) VALUES (?, ?, FALSE, ?, 0, 0, ?, 0)",
                countId,
                classroom,
                normal,
                normal);
    }

    /** Thứ Hai–Sáu: bữa trưa khối mẫu giáo gồm cơm, thịt rim, rau xào. */
    private void week(LocalDate start, String status, long... dishes) {
        long week = insertId("INSERT INTO meal_plan_weeks (week_start, status, published_at) VALUES ('" + start + "', '"
                + status + "', " + ("PUBLISHED".equals(status) ? "now()" : "NULL") + ") RETURNING id");
        for (int i = 0; i < 5; i++) {
            LocalDate day = start.plusDays(i);
            jdbc.update(
                    "UPDATE meal_plan_weeks SET days = days || jsonb_build_array("
                            + "jsonb_build_object('date', ?::date, 'off', false, 'note', '')) WHERE id = ?",
                    day,
                    week);
            for (long dish : dishes) {
                jdbc.update(
                        "INSERT INTO meal_plan_items (week_id, menu_date, session, menu_block, dish_id)"
                                + " VALUES (?, ?, 'LUNCH', 'KINDERGARTEN', ?)",
                        week,
                        day,
                        dish);
            }
        }
    }

    private long food(String code, String name, String storage, String wastePct, String origin) {
        long foodId = insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin) VALUES ('" + code
                        + "', '" + name + "', 'OTHER', 'g', 'kg', 1000, 20000, 100, 5, 1, 10, 'TEST', " + wastePct
                        + ", '"
                        + storage + "', '" + origin + "') RETURNING id");
        jdbc.update(
                "INSERT INTO food_prices (food_id, price, effective_from, reason)"
                        + " SELECT id, unit_price, DATE '2000-01-01', 'Giá ban đầu' FROM foods WHERE id = ?",
                foodId);
        return foodId;
    }

    private long dish(String code, String name, long foodId, int grams) {
        long id = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('" + code + "', '" + name
                + "', 'MAIN') RETURNING id");
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, ?)",
                id,
                foodId,
                ag3,
                grams);
        return id;
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
