package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.meals.DayBalanceRequest;
import com.carenest.dto.meals.DayBalanceResponse;
import com.carenest.dto.meals.DayBalanceStatusResponse;
import com.carenest.dto.meals.FreshCheckRequest;
import com.carenest.dto.meals.FreshCheckResponse;
import com.carenest.dto.meals.FreshForecastRequest;
import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.DayBalanceService;
import com.carenest.service.meals.FreshGoodsService;
import com.carenest.service.meals.MealPlanService;
import com.carenest.support.MovableClock;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FreshCheckHandling;
import com.carenest.utils.enums.FreshCheckResult;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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

/** Bản cân đối khẩu phần ngày: dựng từ công thức, tính thử, làm tròn, lưu, nhận biết thực đơn đã đổi. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DayBalanceIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private DayBalanceService dayBalanceService;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private FreshGoodsService freshGoodsService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser kitchen;
    private long campus1;
    private long campus2;
    private long ag3;
    private long rice;
    private long pork;
    private long milk;
    private long com;
    private long thit;
    private long sua;

    private static final MovableClock CLOCK = new MovableClock();

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
        CLOCK.set(MONDAY, LocalTime.of(7, 0));
        campus1 = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Cơ sở A', TRUE) RETURNING id");
        campus2 = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'Cơ sở B', FALSE) RETURNING id");
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        // Đợt nghỉ khai báo trước mọi lần đọc: ngày học được đọc một lần cho mỗi transaction
        jdbc.update(
                "INSERT INTO school_holidays (school_year_id, start_date, end_date, name) VALUES (?, ?, ?, ?)",
                year,
                MONDAY.plusDays(2),
                MONDAY.plusDays(2),
                "Nghỉ lễ thử nghiệm");
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address,"
                        + " phone, status) SELECT ?, id, code, name, short_name, address, phone, status FROM campuses",
                year);
        for (long campus : List.of(campus1, campus2)) {
            long classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                    + campus + ", " + year + ", 'Lớp " + campus + "', " + ag3 + ") RETURNING id");
            // Trẻ đang học có báo ăn: điểm trường A 31 trẻ, điểm trường B 22 trẻ (trần của số trẻ dự kiến, NUT-31)
            enrollChildren(classroom, campus == campus1 ? 31 : 22);
        }
        // Ngân sách 6000đ đúng bằng chi phí công thức: cơm 1200 + thịt 800 + sữa 4000
        jdbc.update(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,"
                        + " protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max, glucid_pct_min, glucid_pct_max)"
                        + " VALUES (?, '2026-09-01', 6000, 300, 600, 12, 25, 15, 35, 40, 70)",
                ag3);
        jdbc.update("UPDATE age_group_meal_configs SET budget_tolerance_pct = 40");
        rice = food("GAO", "Gạo", "DRY", "PLANT", "350", "7", "1", "77");
        pork = food("THIT", "Thịt", "FRESH", "ANIMAL", "250", "20", "18", "0");
        milk = food("SUA", "Sữa", "FRESH", "ANIMAL", "60", "3", "3", "5");
        com = dish("COM", "Cơm", "CARB", rice, 60);
        thit = dish("THITRIM", "Thịt rim", "MAIN", pork, 40);
        sua = dish("SUACHUA", "Sữa", "SNACK", milk, 200);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        kitchen = user("kitchen", CareNestRole.KITCHEN_STAFF);
        mealPlanService.saveMealPlan(principal, MONDAY, menu(thit));
    }

    @Test
    @DisplayName("Chưa lưu: dựng từ công thức, đủ hai điểm trường, chấm đạt khi lượng, chất, tiền trong khoảng")
    void seedsFromRecipe() {
        DayBalanceResponse balance = dayBalanceService.getBalance(principal, MONDAY, 1);

        assertThat(balance.saved()).isFalse();
        assertThat(balance.version()).isNull();
        assertThat(balance.canEdit()).isTrue();
        assertThat(balance.campuses())
                .extracting(DayBalanceResponse.Campus::campusId)
                .containsExactly(campus1, campus2);
        // Chưa lưu: số trẻ dự kiến lấy theo trẻ đang học; kèm tổng trẻ đang học làm trần khi nhập
        assertThat(balance.counts())
                .extracting(DayBalanceResponse.Count::children, DayBalanceResponse.Count::enrolled)
                .containsExactly(tuple(31, 31), tuple(22, 22));
        assertThat(balance.lines()).hasSize(3);
        assertThat(balance.passed()).isTrue();
        assertThat(balance.groups()).singleElement().satisfies(g -> {
            assertThat(g.evaluation().moneyStatus()).isEqualTo("OK");
            assertThat(g.evaluation().cost()).isEqualByComparingTo("6000");
        });
    }

    @Test
    @DisplayName("Tính thử rồi lưu: làm tròn thực phẩm tươi trên tổng toàn trường, chia hai điểm trường khớp tổng")
    void previewRoundsAndSavePersists() {
        DayBalanceResponse preview = dayBalanceService.previewBalance(principal, MONDAY, 1, request(null, true));

        // Thịt 40 g × (31 + 22) = 2120 g ⇒ 2,2 kg; gạo là hàng khô giữ 3180 g
        DayBalanceResponse.SummaryRow porkRow = summary(preview, pork);
        assertThat(porkRow.purchaseTotal()).isEqualByComparingTo("2200");
        assertThat(porkRow.campuses().stream()
                        .map(DayBalanceResponse.CampusShare::purchaseTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("2200");
        assertThat(summary(preview, rice).purchaseTotal()).isEqualByComparingTo("3180");
        assertThat(dayBalanceService.getBalance(principal, MONDAY, 1).saved()).isFalse();

        DayBalanceResponse saved = dayBalanceService.saveBalance(principal, MONDAY, 1, request(null, true));

        assertThat(saved.saved()).isTrue();
        assertThat(saved.roundingStep()).isEqualByComparingTo("0.1");
        assertThat(summary(saved, pork).purchaseTotal()).isEqualByComparingTo("2200");
        assertThat(saved.counts())
                .extracting(DayBalanceResponse.Count::children)
                .containsExactly(31, 22);
        DayBalanceResponse reread = dayBalanceService.getBalance(principal, MONDAY, 1);
        assertThat(reread.version()).isEqualTo(saved.version());
        assertThat(reread.lines()).containsExactlyElementsOf(saved.lines());
        assertThat(dayBalanceService.getStatuses(principal, MONDAY, MONDAY))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.saved()).isTrue();
                    assertThat(s.passed()).isTrue();
                });
    }

    @Test
    @DisplayName("Chưa làm tròn: lượng mua theo đơn vị của các điểm trường cộng đúng số toàn trường")
    void campusQuantitiesAddUpToSchoolTotal() {
        DayBalanceRequest edit = new DayBalanceRequest(
                null,
                request(null, false).counts(),
                null,
                List.of(new DayBalanceRequest.EditInput(ag3, pork, new BigDecimal("33.3333"), null)),
                false,
                null);

        DayBalanceResponse preview = dayBalanceService.previewBalance(principal, MONDAY, 1, edit);

        assertThat(preview.summary())
                .allSatisfy(row -> assertThat(row.campuses().stream()
                                .map(DayBalanceResponse.CampusShare::purchaseQuantity)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .isEqualByComparingTo(row.purchaseQuantity()));
    }

    @Test
    @DisplayName("Số trẻ hoặc sửa gam cho dòng không có trong ngày bị từ chối; không gửi cờ thì hiểu là tắt")
    void rejectsUnknownRowsAndDefaultsFlags() {
        DayBalanceRequest unknownFood = new DayBalanceRequest(
                null,
                null,
                null,
                List.of(new DayBalanceRequest.EditInput(ag3, 999_999L, BigDecimal.TEN, null)),
                false,
                null);
        DayBalanceRequest unknownCampus = new DayBalanceRequest(
                null, List.of(new DayBalanceRequest.CountInput(999_999L, ag3, 5)), null, null, false, null);

        expectCode(
                () -> dayBalanceService.previewBalance(principal, MONDAY, 1, unknownFood),
                ApiCode.DAY_BALANCE_INPUT_INVALID);
        expectCode(
                () -> dayBalanceService.previewBalance(principal, MONDAY, 1, unknownCampus),
                ApiCode.DAY_BALANCE_INPUT_INVALID);
        DayBalanceRequest noFlags = new DayBalanceRequest(null, null, null, null, null, null, null);
        assertThat(noFlags.applyRounding()).isFalse();
        assertThat(noFlags.fromRecipe()).isFalse();
        assertThat(dayBalanceService
                        .previewBalance(principal, MONDAY, 1, noFlags)
                        .lines())
                .hasSize(3);
    }

    @Test
    @DisplayName(
            "NUT-35: số trẻ dự kiến vượt tổng trẻ đang học của nhóm tuổi tại điểm trường thì từ chối, bằng tổng thì nhận")
    void expectedChildrenCannotExceedEnrolled() {
        DayBalanceRequest over = new DayBalanceRequest(
                null, List.of(new DayBalanceRequest.CountInput(campus2, ag3, 23)), null, null, false, null);
        DayBalanceRequest exact = new DayBalanceRequest(
                null, List.of(new DayBalanceRequest.CountInput(campus2, ag3, 22)), null, null, false, null);

        expectCode(
                () -> dayBalanceService.previewBalance(principal, MONDAY, 1, over), ApiCode.DAY_BALANCE_INPUT_INVALID);
        expectCode(() -> dayBalanceService.saveBalance(principal, MONDAY, 1, over), ApiCode.DAY_BALANCE_INPUT_INVALID);
        assertThat(dayBalanceService.previewBalance(principal, MONDAY, 1, exact).counts())
                .extracting(DayBalanceResponse.Count::children)
                .containsExactly(31, 22);
    }

    @Test
    @DisplayName("Sửa gam làm ngày không đạt tiền: trạng thái nêu nhóm tuổi và tiêu chí")
    void editingGramsFailsMoney() {
        DayBalanceRequest edit = new DayBalanceRequest(
                null,
                null,
                null,
                List.of(new DayBalanceRequest.EditInput(ag3, milk, new BigDecimal("400"), null)),
                false,
                null);

        DayBalanceResponse saved = dayBalanceService.saveBalance(principal, MONDAY, 1, edit);

        assertThat(saved.passed()).isFalse();
        DayBalanceStatusResponse status =
                dayBalanceService.getStatuses(principal, MONDAY, MONDAY).getFirst();
        assertThat(status.problems()).anySatisfy(p -> assertThat(p).contains("tiền"));
    }

    @Test
    @DisplayName("Đổi món sau khi lưu: bản cân đối cũ, món còn lại giữ số đã sửa, món mới lấy theo công thức")
    void menuChangeMarksStale() {
        dayBalanceService.saveBalance(
                principal,
                MONDAY,
                1,
                new DayBalanceRequest(
                        null,
                        null,
                        null,
                        List.of(new DayBalanceRequest.EditInput(ag3, rice, new BigDecimal("70"), null)),
                        false,
                        null));
        long egg = food("TRUNG", "Trứng", "FRESH", "ANIMAL", "150", "13", "11", "1");
        long trung = dish("TRUNGRAN", "Trứng rán", "MAIN", egg, 30);

        mealPlanService.saveMealPlan(principal, MONDAY, menu(trung));

        DayBalanceResponse balance = dayBalanceService.getBalance(principal, MONDAY, 1);
        assertThat(balance.stale()).isTrue();
        assertThat(balance.lines())
                .extracting(DayBalanceResponse.Line::foodId)
                .containsExactlyInAnyOrder(rice, egg, milk);
        assertThat(balance.lines().stream()
                        .filter(l -> l.foodId() == rice)
                        .findFirst()
                        .orElseThrow()
                        .grams())
                .isEqualByComparingTo("70");
        assertThat(dayBalanceService
                        .getStatuses(principal, MONDAY, MONDAY)
                        .getFirst()
                        .passed())
                .isFalse();
    }

    @Test
    @DisplayName("Lưu bằng phiên bản cũ, ngày đã qua, ngày chưa có món hoặc thiếu quyền đều bị từ chối")
    void rejectsInvalidSaves() {
        dayBalanceService.saveBalance(principal, MONDAY, 1, request(null, false));

        expectCode(
                () -> dayBalanceService.saveBalance(principal, MONDAY, 1, request(null, false)),
                ApiCode.DAY_BALANCE_CONFLICT);
        expectCode(
                () -> dayBalanceService.saveBalance(principal, MONDAY.plusDays(1), 1, request(null, false)),
                ApiCode.DAY_BALANCE_NO_MENU);
        expectCode(() -> dayBalanceService.saveBalance(kitchen, MONDAY, 1, request(null, false)), ApiCode.FORBIDDEN);
        CLOCK.set(MONDAY.plusDays(1), LocalTime.of(7, 0));
        expectCode(
                () -> dayBalanceService.saveBalance(principal, MONDAY, 1, request(0L, false)),
                ApiCode.DAY_BALANCE_LOCKED);
    }

    @Test
    @DisplayName("Về công thức: bỏ gam đã sửa nhưng giữ số trẻ đã lưu")
    void resetsToRecipe() {
        dayBalanceService.saveBalance(principal, MONDAY, 1, request(null, false));
        dayBalanceService.saveBalance(
                principal,
                MONDAY,
                1,
                new DayBalanceRequest(
                        dayBalanceService.getBalance(principal, MONDAY, 1).version(),
                        null,
                        null,
                        List.of(new DayBalanceRequest.EditInput(ag3, rice, new BigDecimal("70"), null)),
                        false,
                        null));

        DayBalanceResponse reset = dayBalanceService.previewBalance(
                principal, MONDAY, 1, new DayBalanceRequest(null, null, null, null, false, null, true));

        assertThat(reset.lines().stream()
                        .filter(l -> l.foodId() == rice)
                        .findFirst()
                        .orElseThrow()
                        .grams())
                .isEqualByComparingTo("60");
        assertThat(reset.counts())
                .extracting(DayBalanceResponse.Count::children)
                .containsExactly(31, 22);
    }

    @Test
    @DisplayName("Lần 2 chỉ mở khi mọi điểm trường chốt suất; giữ gam lần 1, tính lại theo suất chốt và làm tròn")
    void secondRoundFollowsConfirmedCounts() {
        dayBalanceService.saveBalance(principal, MONDAY, 1, request(null, true));

        assertThat(dayBalanceService.getBalance(principal, MONDAY, 2).blockedReason())
                .contains("xác nhận suất ăn");
        expectCode(
                () -> dayBalanceService.saveBalance(principal, MONDAY, 2, request(null, true)),
                ApiCode.DAY_BALANCE_LOCKED);

        confirm(campus1, 25);
        confirm(campus2, 22);
        DayBalanceResponse seeded = dayBalanceService.getBalance(principal, MONDAY, 2);

        assertThat(seeded.canEdit()).isTrue();
        assertThat(seeded.saved()).isFalse();
        assertThat(seeded.counts())
                .extracting(DayBalanceResponse.Count::children)
                .containsExactly(25, 22);
        assertThat(seeded.roundingStep()).isEqualByComparingTo("0.1");
        // Thịt 40 g × 47 = 1880 g ⇒ 1,9 kg
        assertThat(summary(seeded, pork).purchaseTotal()).isEqualByComparingTo("1900");
        assertThat(seeded.lines())
                .allSatisfy(l -> assertThat(l.baseGrams())
                        .isEqualByComparingTo(l.foodId() == rice ? "60" : l.foodId() == pork ? "40" : "200"));
    }

    @Test
    @DisplayName("Kiểm nhận lần 2: dòng lệch phải chọn gọi thêm, trả lại hoặc giữ dùng; BE tính số thực nhận")
    void secondReceiptHandlesDifferences() {
        publishableWeek();
        dayBalanceService.saveBalance(principal, MONDAY, 1, request(null, true));
        mealPlanService.publishMealPlan(principal, MONDAY);
        freshGoodsService.createForecast(principal, new FreshForecastRequest(campus1, MONDAY, MONDAY, "", null, null));
        FreshCheckResponse.FreshCheckRound first =
                freshGoodsService.getChecks(principal, campus1, MONDAY).rounds().getFirst();
        freshGoodsService.recordCheck(
                principal,
                campus1,
                MONDAY,
                1,
                new FreshCheckRequest(
                        "",
                        first.lines().stream()
                                .map(l -> new FreshCheckRequest.FreshCheckLineInput(
                                        l.food().id(), null, FreshCheckResult.ENOUGH, null, null))
                                .toList(),
                        null));
        confirm(campus1, 25);
        confirm(campus2, 22);

        assertThat(round2(campus1).available()).isFalse();
        assertThat(round2(campus1).unavailableReason()).contains("lần 2");

        dayBalanceService.saveBalance(principal, MONDAY, 2, new DayBalanceRequest(null, null, null, null, false, null));
        FreshCheckResponse.FreshCheckRound second = round2(campus1);

        assertThat(second.handlingRequired()).isTrue();
        FreshCheckResponse.FreshCheckLine porkLine = line(second, pork);
        assertThat(porkLine.round1Qty()).isEqualByComparingTo("1300");
        assertThat(porkLine.expectedQty()).isEqualByComparingTo("1000");
        expectCode(
                () -> freshGoodsService.recordCheck(principal, campus1, MONDAY, 2, handling(second, false)),
                ApiCode.FRESH_CHECK_HANDLING_INVALID);

        freshGoodsService.recordCheck(principal, campus1, MONDAY, 2, handling(second, true));

        FreshCheckResponse.FreshCheckLine saved = line(round2(campus1), pork);
        assertThat(saved.handlingAction()).isEqualTo(FreshCheckHandling.RETURN);
        assertThat(saved.handlingQty()).isEqualByComparingTo("300");
        assertThat(saved.receivedQty()).isEqualByComparingTo("1000");
        assertThat(saved.result()).isEqualTo(FreshCheckResult.ENOUGH);

        // Chốt lại suất ⇒ bản lần 2 cũ, phải cân đối lại trước khi kiểm
        jdbc.update("UPDATE meal_count_lines SET present = 20, normal = 20 WHERE normal = 25");
        // Sửa thẳng DB trong cùng transaction test: bỏ bản đã nạp để đọc lại số mới
        entityManager.clear();
        assertThat(dayBalanceService.getBalance(principal, MONDAY, 2).stale()).isTrue();
        assertThat(round2(campus1).available()).isFalse();
    }

    // Chỉ thứ Hai ăn, các ngày còn lại nghỉ để tuần công bố được sau khi cân đối thứ Hai
    private void publishableWeek() {
        List<MealPlanRequest.DayInput> days = new ArrayList<>(menu(thit).days());
        for (int i = 1; i < 5; i++) {
            days.add(new MealPlanRequest.DayInput(MONDAY.plusDays(i), true, "Nghỉ", List.of()));
        }
        mealPlanService.saveMealPlan(principal, MONDAY, new MealPlanRequest(days));
    }

    @Test
    @DisplayName(
            "NUT-31: ngày trong đợt nghỉ của trường là ngày nghỉ kèm tên đợt, không cân đối được dù thực đơn có món")
    void schoolHolidayIsOffDay() {
        LocalDate wednesday = MONDAY.plusDays(2);
        List<MealPlanRequest.DayInput> days = new ArrayList<>(menu(thit).days());
        days.add(new MealPlanRequest.DayInput(wednesday, false, "", days.get(0).items()));
        mealPlanService.saveMealPlan(principal, MONDAY, new MealPlanRequest(days));

        DayBalanceStatusResponse status = dayBalanceService.getStatuses(principal, MONDAY, MONDAY.plusDays(4)).stream()
                .filter(d -> d.date().equals(wednesday))
                .findFirst()
                .orElseThrow();
        assertThat(status.dayOff()).isTrue();
        assertThat(status.offReason()).isEqualTo("Nghỉ lễ thử nghiệm");
        DayBalanceResponse balance = dayBalanceService.getBalance(principal, wednesday, 1);
        assertThat(balance.dayOff()).isTrue();
        assertThat(balance.canEdit()).isFalse();
        assertThat(balance.offReason()).isEqualTo("Nghỉ lễ thử nghiệm");
        expectCode(
                () -> dayBalanceService.saveBalance(principal, wednesday, 1, request(null, false)),
                ApiCode.DAY_BALANCE_LOCKED);
        assertThat(dayBalanceService
                        .getStatuses(principal, MONDAY, MONDAY)
                        .getFirst()
                        .dayOff())
                .isFalse();
    }

    private FreshCheckResponse.FreshCheckRound round2(long campus) {
        return freshGoodsService.getChecks(principal, campus, MONDAY).rounds().get(1);
    }

    private static FreshCheckResponse.FreshCheckLine line(FreshCheckResponse.FreshCheckRound round, long foodId) {
        return round.lines().stream()
                .filter(l -> l.food().id() == foodId)
                .findFirst()
                .orElseThrow();
    }

    private static FreshCheckRequest handling(FreshCheckResponse.FreshCheckRound round, boolean choose) {
        return new FreshCheckRequest(
                "",
                round.lines().stream()
                        .map(l -> {
                            int compared = l.round1Qty().compareTo(l.expectedQty());
                            FreshCheckHandling action = !choose || compared == 0
                                    ? null
                                    : compared > 0 ? FreshCheckHandling.RETURN : FreshCheckHandling.ORDER_MORE;
                            BigDecimal quantity = action == null
                                    ? null
                                    : l.round1Qty().subtract(l.expectedQty()).abs();
                            return new FreshCheckRequest.FreshCheckLineInput(
                                    l.food().id(), null, null, null, null, action, quantity);
                        })
                        .toList(),
                null);
    }

    private void confirm(long campus, int servings) {
        long year = jdbc.queryForObject("SELECT id FROM school_years WHERE status = 'ACTIVE'", Long.class);
        long classroom = jdbc.queryForObject("SELECT id FROM classrooms WHERE campus_id = " + campus, Long.class);
        for (String session : List.of("LUNCH", "AFTERNOON")) {
            long count = insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status,"
                    + " aggregated_at, confirmed_at) VALUES (" + campus + ", " + year + ", '" + MONDAY + "', '"
                    + session + "', 'CONFIRMED', now(), now()) RETURNING id");
            jdbc.update(
                    "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing,"
                            + " normal, substitute) VALUES (?, ?, FALSE, ?, 0, 0, ?, 0)",
                    count,
                    classroom,
                    servings,
                    servings);
        }
    }

    private void enrollChildren(long classroom, int count) {
        jdbc.update(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, meal_registered)"
                        + " SELECT 'HSB" + classroom + "-' || n, 'Trẻ thử', DATE '2023-01-01', 'MALE', 'ACTIVE',"
                        + " DATE '2026-09-01', TRUE FROM generate_series(1, " + count + ") n");
        jdbc.update("INSERT INTO enrollments (child_id, classroom_id, valid_from)" + " SELECT id, " + classroom
                + ", DATE '2026-09-01' FROM children WHERE code LIKE 'HSB" + classroom + "-%'");
    }

    private DayBalanceRequest request(Long version, boolean round) {
        return new DayBalanceRequest(
                version,
                List.of(
                        new DayBalanceRequest.CountInput(campus1, ag3, 31),
                        new DayBalanceRequest.CountInput(campus2, ag3, 22)),
                null,
                null,
                round,
                new BigDecimal("0.1"));
    }

    private static DayBalanceResponse.SummaryRow summary(DayBalanceResponse balance, long foodId) {
        return balance.summary().stream()
                .filter(r -> r.foodId() == foodId)
                .findFirst()
                .orElseThrow();
    }

    private MealPlanRequest menu(long mainDish) {
        List<MealPlanRequest.ItemInput> items = new ArrayList<>();
        items.add(new MealPlanRequest.ItemInput(MealSession.LUNCH, MenuBlock.KINDERGARTEN, com));
        items.add(new MealPlanRequest.ItemInput(MealSession.LUNCH, MenuBlock.KINDERGARTEN, mainDish));
        items.add(new MealPlanRequest.ItemInput(MealSession.AFTERNOON, MenuBlock.KINDERGARTEN, sua));
        return new MealPlanRequest(List.of(new MealPlanRequest.DayInput(MONDAY, false, "", items)));
    }

    private long food(
            String code, String name, String storage, String origin, String kcal, String p, String l, String g) {
        long foodId = insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, storage, origin) VALUES ('" + code + "', '"
                        + name
                        + "', 'OTHER', 'g', 'kg', 1000, 20000, " + kcal + ", " + p + ", " + l + ", " + g + ", 'TEST', '"
                        + storage + "', '" + origin + "') RETURNING id");
        jdbc.update(
                "INSERT INTO food_prices (food_id, price, effective_from, reason)"
                        + " SELECT id, unit_price, DATE '2000-01-01', 'Giá ban đầu' FROM foods WHERE id = ?",
                foodId);
        return foodId;
    }

    private long dish(String code, String name, String type, long foodId, int grams) {
        long id = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('" + code + "', '" + name + "', '" + type
                + "') RETURNING id");
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
