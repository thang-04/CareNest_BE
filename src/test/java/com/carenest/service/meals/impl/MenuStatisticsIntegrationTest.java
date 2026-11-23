package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.meals.MenuStatisticsRequest;
import com.carenest.dto.meals.MenuStatisticsResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.MealPlanService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
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

/** Thống kê một ngày thực đơn cho N trẻ: lượng ăn, lượng mua có thải bỏ (NUT-07), tiền, bảng kiểm dinh dưỡng. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class MenuStatisticsIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private ClassroomService classroomService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long ag3;
    private long com;
    private long sua;

    @BeforeEach
    void setUp() {
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        jdbc.update(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,"
                        + " protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max, glucid_pct_min, glucid_pct_max)"
                        + " VALUES (?, '2026-09-01', 30000, 300, 600, 12, 25, 15, 35, 40, 70)",
                ag3);
        // Gạo 18.000đ/kg, thải bỏ 10%; sữa 8.000đ/hộp 180 ml, không thải bỏ
        long rice = food("GAO", "Gạo tẻ", "g", "kg", "1000", "18000", "10", "350", "7", "1", "77");
        long milk = food("SUA", "Sữa tươi", "ml", "hộp 180 ml", "180", "8000", "0", "60", "3", "3", "5");
        com = dish("COM", "Cơm", "CARB", rice, 60);
        sua = dish("SUACHUA", "Sữa", "SNACK", milk, 180);
        long id = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-p', 'p', 'p') RETURNING id",
                Long.class);
        principal = new CurrentUser(
                id,
                "kc-p",
                "p",
                "p",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                TestPermissions.defaultsOf(CareNestRole.PRINCIPAL));
    }

    @Test
    @DisplayName(
            "NUT-07: 80 trẻ × 60 g gạo = 4,8 kg ăn ⇒ mua 5,33 kg (÷ 0,9), tiền theo lượng mua; bữa chiều sữa đủ hộp")
    void quantitiesAndCostForInputChildren() {
        MenuStatisticsResponse stats = mealPlanService.getMenuStatistics(
                principal,
                new MenuStatisticsRequest(
                        ag3,
                        DAY,
                        null,
                        80,
                        List.of(
                                new MenuStatisticsRequest.Item(MealSession.LUNCH, com),
                                new MenuStatisticsRequest.Item(MealSession.AFTERNOON, sua),
                                // Bữa sáng nhóm ag-3 không phục vụ ⇒ bỏ qua
                                new MenuStatisticsRequest.Item(MealSession.MORNING, com))));

        assertThat(stats.children()).isEqualTo(80);
        assertThat(stats.childrenSource()).isEqualTo("INPUT");
        assertThat(stats.dishes())
                .extracting(MenuStatisticsResponse.Dish::dishName)
                .containsExactly("Cơm", "Sữa");
        MenuStatisticsResponse.Ingredient rice = stats.ingredients().stream()
                .filter(i -> i.foodName().equals("Gạo tẻ"))
                .findFirst()
                .orElseThrow();
        assertThat(rice.eatenTotal()).isEqualByComparingTo("4800");
        assertThat(rice.purchaseTotal()).isEqualByComparingTo("5333.33");
        assertThat(rice.purchaseQty()).isEqualByComparingTo("5.333");
        assertThat(rice.cost()).isEqualByComparingTo("96000");
        MenuStatisticsResponse.Ingredient milk = stats.ingredients().stream()
                .filter(i -> i.foodName().equals("Sữa tươi"))
                .findFirst()
                .orElseThrow();
        assertThat(milk.purchaseQty()).isEqualByComparingTo("80");
        assertThat(milk.cost()).isEqualByComparingTo("640000");
        assertThat(stats.totalCost()).isEqualByComparingTo("736000");
        assertThat(stats.mealPrice()).isEqualByComparingTo("30000");
        assertThat(stats.evaluation().totals().kcal()).isEqualByComparingTo("318");
        assertThat(stats.evaluation().sessions())
                .extracting(r -> r.session())
                .containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
    }

    @Test
    @DisplayName("NUT-10: mỗi thực phẩm mang storage; tổng hàng tươi + hàng khô = tổng tiền")
    void splitsCostByStorage() {
        jdbc.update("UPDATE foods SET storage = 'FRESH' WHERE code = 'SUA'");

        MenuStatisticsResponse stats = mealPlanService.getMenuStatistics(
                principal,
                new MenuStatisticsRequest(
                        ag3,
                        DAY,
                        null,
                        80,
                        List.of(
                                new MenuStatisticsRequest.Item(MealSession.LUNCH, com),
                                new MenuStatisticsRequest.Item(MealSession.AFTERNOON, sua))));

        assertThat(stats.ingredients())
                .extracting(MenuStatisticsResponse.Ingredient::foodName, MenuStatisticsResponse.Ingredient::storage)
                .containsExactlyInAnyOrder(tuple("Gạo tẻ", FoodStorage.DRY), tuple("Sữa tươi", FoodStorage.FRESH));
        assertThat(stats.freshCost()).isEqualByComparingTo("640000");
        assertThat(stats.dryCost()).isEqualByComparingTo("96000");
        assertThat(stats.freshCost().add(stats.dryCost())).isEqualByComparingTo(stats.totalCost());
    }

    @Test
    @DisplayName("NUT-27: giá và NCC lấy tại ngày thống kê; ngày trước mức mới giữ giá cũ")
    void priceAndSupplierFollowEffectiveDate() {
        long supplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, phone, contract_no, contract_start, contract_end)"
                        + " VALUES ('NCC Gạo', '0900000001', 'HĐ-01', '2026-09-01', '2027-08-31') RETURNING id",
                Long.class);
        jdbc.update(
                "INSERT INTO food_prices (food_id, supplier_id, price, effective_from, reason)"
                        + " SELECT id, ?, 20000, ?, 'Phụ lục 1' FROM foods WHERE code = 'GAO'",
                supplier,
                DAY);

        MenuStatisticsResponse before = riceStats(DAY.minusDays(3), null);
        MenuStatisticsResponse after = riceStats(DAY, null);

        assertThat(before.ingredients()).singleElement().satisfies(rice -> {
            assertThat(rice.unitPrice()).isEqualByComparingTo("18000");
            assertThat(rice.cost()).isEqualByComparingTo("96000");
            assertThat(rice.supplierName()).isNull();
            assertThat(rice.priceSource()).isEqualTo("CATALOG");
        });
        // 5.333,33 g × 20.000 ÷ 1.000
        assertThat(after.ingredients()).singleElement().satisfies(rice -> {
            assertThat(rice.unitPrice()).isEqualByComparingTo("20000");
            assertThat(rice.cost()).isEqualByComparingTo("106667");
            assertThat(rice.supplierId()).isEqualTo(supplier);
            assertThat(rice.supplierName()).isEqualTo("NCC Gạo");
        });
        assertThat(after.supplierCosts()).singleElement().satisfies(s -> {
            assertThat(s.supplierName()).isEqualTo("NCC Gạo");
            assertThat(s.cost()).isEqualByComparingTo("106667");
        });
    }

    @Test
    @DisplayName("NUT-27: hàng khô có điểm trường tính giá lô FEFO, phần thiếu kho theo giá danh mục và gắn cờ")
    void dryStockUsesLotPriceThenCatalog() {
        long campus = jdbc.queryForObject(
                "INSERT INTO campuses (code, name, is_main) VALUES ('CS-L', 'L', TRUE) RETURNING id", Long.class);
        long supplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, phone) VALUES ('NCC kho', '0900000002') RETURNING id", Long.class);
        long receipt = jdbc.queryForObject(
                "INSERT INTO stock_receipts (campus_id, supplier_id, received_date, status)"
                        + " VALUES (?, ?, '2026-11-02', 'CONFIRMED') RETURNING id",
                Long.class,
                campus,
                supplier);
        long rice = jdbc.queryForObject("SELECT id FROM foods WHERE code = 'GAO'", Long.class);
        // Lô 3 kg giá 15.000đ/kg; lô hết hạn trước ngày thực đơn không được dùng
        lot(receipt, campus, rice, "L1", "2027-03-01", "3000", "15000");
        lot(receipt, campus, rice, "L0", "2026-11-01", "5000", "10000");

        MenuStatisticsResponse stats = riceStats(DAY, campus);

        // 3.000 g × 15.000 + 2.333,33 g × 18.000, chia 1.000
        assertThat(stats.ingredients()).singleElement().satisfies(item -> {
            assertThat(item.priceSource()).isEqualTo("MIXED");
            assertThat(item.stockShortQty()).isEqualByComparingTo("2333.33");
            assertThat(item.cost()).isEqualByComparingTo("87000");
        });
        assertThat(stats.dryCost()).isEqualByComparingTo("87000");
    }

    private MenuStatisticsResponse riceStats(LocalDate date, Long campusId) {
        return mealPlanService.getMenuStatistics(
                principal,
                new MenuStatisticsRequest(
                        ag3, date, campusId, 80, List.of(new MenuStatisticsRequest.Item(MealSession.LUNCH, com))));
    }

    private void lot(long receipt, long campus, long food, String lotNo, String expiry, String grams, String price) {
        long lot = jdbc.queryForObject(
                "INSERT INTO stock_receipt_lines (receipt_id, food_id, lot_no, expiry_date, quantity, unit_price)"
                        + " VALUES (?, ?, ?, ?::date, ?::numeric, ?::numeric) RETURNING id",
                Long.class,
                receipt,
                food,
                lotNo,
                expiry,
                grams,
                price);
        jdbc.update(
                "INSERT INTO stock_movements (lot_id, campus_id, food_id, movement_type, quantity)"
                        + " VALUES (?, ?, ?, 'RECEIPT', ?::numeric)",
                lot,
                campus,
                food,
                grams);
    }

    @Test
    @DisplayName("NUT-12: mua theo ĐVT đếm được làm tròn lên nguyên hộp; tiền ăn vẫn theo lượng thực dùng")
    void countablePurchaseQtyRoundsUp() {
        jdbc.update("UPDATE foods SET purchase_unit = 'hộp 110 ml', purchase_unit_size = 110 WHERE code = 'SUA'");

        MenuStatisticsResponse stats = mealPlanService.getMenuStatistics(
                principal,
                new MenuStatisticsRequest(
                        ag3, DAY, null, 80, List.of(new MenuStatisticsRequest.Item(MealSession.AFTERNOON, sua))));

        // 80 × 180 ml = 14.400 ml ÷ 110 = 130,9 ⇒ 131 hộp; tiền = 14.400 × 8.000 ÷ 110
        assertThat(stats.ingredients()).singleElement().satisfies(milk -> {
            assertThat(milk.purchaseQty()).isEqualByComparingTo("131");
            assertThat(milk.cost()).isEqualByComparingTo("1047273");
        });
    }

    @Test
    @DisplayName("Không nhập số trẻ, không có sổ suất ⇒ lấy sĩ số đang học (chưa có lớp = 0)")
    void defaultsToEnrolledChildren() {
        MenuStatisticsResponse stats = mealPlanService.getMenuStatistics(
                principal,
                new MenuStatisticsRequest(
                        ag3, DAY, null, null, List.of(new MenuStatisticsRequest.Item(MealSession.LUNCH, com))));
        assertThat(stats.childrenSource()).isEqualTo("ENROLLED");
        assertThat(stats.children()).isZero();
        assertThat(stats.ingredients())
                .singleElement()
                .satisfies(i -> assertThat(i.cost()).isEqualByComparingTo("0"));
    }

    @Test
    @DisplayName("NUT-07/CHD-13: sĩ số mặc định không đếm trẻ không báo ăn, sĩ số lớp vẫn đủ")
    void enrolledHeadcountSkipsChildrenWithoutMeals() {
        long campus = jdbc.queryForObject(
                "INSERT INTO campuses (code, name, is_main) VALUES ('CS-H', 'H', TRUE) RETURNING id", Long.class);
        long year = jdbc.queryForObject(
                "INSERT INTO school_years (name, start_date, end_date, status)"
                        + " VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id",
                Long.class);
        long classroom = jdbc.queryForObject(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'Lớp H', ?)"
                        + " RETURNING id",
                Long.class,
                campus,
                year,
                ag3);
        for (int i = 0; i < 3; i++) {
            long child = jdbc.queryForObject(
                    "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, meal_registered)"
                            + " VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Trẻ ' || ?, '2022-06-01',"
                            + " 'MALE', 'ACTIVE', CURRENT_DATE, ?) RETURNING id",
                    Long.class,
                    String.valueOf(i),
                    i != 2);
            jdbc.update(
                    "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                    child,
                    classroom);
        }

        MenuStatisticsResponse stats = mealPlanService.getMenuStatistics(
                principal,
                new MenuStatisticsRequest(
                        ag3, DAY, campus, null, List.of(new MenuStatisticsRequest.Item(MealSession.LUNCH, com))));

        assertThat(stats.childrenSource()).isEqualTo("ENROLLED");
        assertThat(stats.children()).isEqualTo(2);
        assertThat(classroomService.countActiveChildren(List.of(classroom), DAY))
                .containsEntry(classroom, 3L);
    }

    @Test
    @DisplayName("Cơ sở–ngày đã chốt suất nhưng nhóm tuổi vắng hết ⇒ dùng số chốt 0, không quay về sĩ số")
    void confirmedZeroServingsIsNotReplacedByEnrolment() {
        long campus = jdbc.queryForObject(
                "INSERT INTO campuses (code, name, is_main) VALUES ('CS-S', 'S', TRUE) RETURNING id", Long.class);
        long year = jdbc.queryForObject(
                "INSERT INTO school_years (name, start_date, end_date, status)"
                        + " VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id",
                Long.class);
        long classroom = jdbc.queryForObject(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'Lớp S', ?)"
                        + " RETURNING id",
                Long.class,
                campus,
                year,
                ag3);
        long count = jdbc.queryForObject(
                "INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, aggregated_at,"
                        + " confirmed_at) VALUES (?, ?, ?, 'LUNCH', 'CONFIRMED', now(), now()) RETURNING id",
                Long.class,
                campus,
                year,
                DAY);
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal,"
                        + " substitute) VALUES (?, ?, FALSE, 0, 2, 0, 0, 0)",
                count,
                classroom);

        MenuStatisticsResponse stats = mealPlanService.getMenuStatistics(
                principal,
                new MenuStatisticsRequest(
                        ag3, DAY, campus, null, List.of(new MenuStatisticsRequest.Item(MealSession.LUNCH, com))));

        assertThat(stats.childrenSource()).isEqualTo("CONFIRMED");
        assertThat(stats.children()).isZero();
        assertThat(stats.totalCost()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Tất cả nhóm tuổi chỉ lấy món đúng khối, cộng suất và chi phí từng nhóm")
    void allAgeGroupsPreserveBlocksAndAggregate() {
        long ag2 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-2'", Long.class);
        long ag4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child)"
                        + " SELECT ?, id, ?, 100 FROM foods WHERE code = 'GAO'",
                com,
                ag4);
        jdbc.update(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price)"
                        + " VALUES (?, '2026-09-01', 35000)",
                ag4);
        long campus = jdbc.queryForObject(
                "INSERT INTO campuses (code, name, is_main) VALUES ('CS-ALL', 'All', TRUE) RETURNING id", Long.class);
        long year = jdbc.queryForObject(
                "INSERT INTO school_years (name, start_date, end_date, status)"
                        + " VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id",
                Long.class);
        long class3 = jdbc.queryForObject(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id)"
                        + " VALUES (?, ?, 'Lớp 3', ?) RETURNING id",
                Long.class,
                campus,
                year,
                ag3);
        long class4 = jdbc.queryForObject(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id)"
                        + " VALUES (?, ?, 'Lớp 4', ?) RETURNING id",
                Long.class,
                campus,
                year,
                ag4);
        long count = jdbc.queryForObject(
                "INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, aggregated_at,"
                        + " confirmed_at) VALUES (?, ?, ?, 'LUNCH', 'CONFIRMED', now(), now()) RETURNING id",
                Long.class,
                campus,
                year,
                DAY);
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing,"
                        + " normal, substitute) VALUES (?, ?, FALSE, ?, 0, 0, ?, 0)",
                count,
                class3,
                2,
                2);
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing,"
                        + " normal, substitute) VALUES (?, ?, FALSE, ?, 0, 0, ?, 0)",
                count,
                class4,
                3,
                3);

        MenuStatisticsResponse stats = mealPlanService.getMenuStatistics(
                principal,
                new MenuStatisticsRequest(
                        null,
                        DAY,
                        campus,
                        null,
                        List.of(new MenuStatisticsRequest.Item(MealSession.LUNCH, MenuBlock.KINDERGARTEN, com))));

        assertThat(stats.groups())
                .extracting(MenuStatisticsResponse::ageGroupId)
                .contains(ag3, ag4)
                .doesNotContain(ag2);
        assertThat(stats.groups()).extracting(MenuStatisticsResponse::children).contains(2, 3);
        assertThat(stats.groups()).allSatisfy(group -> {
            assertThat(group.childrenSource()).isEqualTo("CONFIRMED");
            assertThat(group.dishes())
                    .extracting(MenuStatisticsResponse.Dish::dishName)
                    .containsExactly("Cơm");
        });
        assertThat(stats.children()).isEqualTo(5);
        assertThat(stats.totalCost())
                .isEqualByComparingTo(stats.groups().stream()
                        .map(MenuStatisticsResponse::totalCost)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        assertThat(stats.mealPrice()).isEqualByComparingTo("35000");
        assertThat(stats.costPerChild()).isEqualByComparingTo(stats.totalCost().divide(new java.math.BigDecimal(5)));
    }

    private long food(
            String code,
            String name,
            String unit,
            String purchaseUnit,
            String size,
            String price,
            String waste,
            String kcal,
            String p,
            String l,
            String g) {
        long foodId = jdbc.queryForObject(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " waste_pct, kcal, protein, lipid, glucid, nutrient_source, storage, origin)"
                        + " VALUES (?, ?, 'OTHER', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'TEST', 'DRY', 'PLANT') RETURNING id",
                Long.class,
                code,
                name,
                unit,
                purchaseUnit,
                new java.math.BigDecimal(size),
                new java.math.BigDecimal(price),
                new java.math.BigDecimal(waste),
                new java.math.BigDecimal(kcal),
                new java.math.BigDecimal(p),
                new java.math.BigDecimal(l),
                new java.math.BigDecimal(g));
        jdbc.update(
                "INSERT INTO food_prices (food_id, price, effective_from, reason)"
                        + " SELECT id, unit_price, DATE '2000-01-01', 'Giá ban đầu' FROM foods WHERE id = ?",
                foodId);
        return foodId;
    }

    private long dish(String code, String name, String type, long foodId, int grams) {
        long id = jdbc.queryForObject(
                "INSERT INTO dishes (code, name, dish_type) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                code,
                name,
                type);
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, ?)",
                id,
                foodId,
                ag3,
                grams);
        return id;
    }
}
