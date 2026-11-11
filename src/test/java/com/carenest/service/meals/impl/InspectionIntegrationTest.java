package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.carenest.dto.common.ImageContent;
import com.carenest.dto.meals.FoodSampleRequest;
import com.carenest.dto.meals.FoodSampleResponse;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.dto.meals.InspectionReopenRequest;
import com.carenest.dto.meals.InspectionSaveRequest;
import com.carenest.dto.meals.InspectionSheetResponse;
import com.carenest.exception.GlobalException;
import com.carenest.utils.enums.FileCategory;
import com.carenest.integration.storage.FileStorage;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.FoodSampleService;
import com.carenest.service.meals.InspectionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.InspectionStatus;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Permission;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Biên bản kiểm thực (INS-01..04) trên DB thật: khung dòng từ thực đơn/suất, jsonb, ký, khóa, sửa lại, ảnh chữ ký. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class InspectionIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = MONDAY.atTime(10, 30).atZone(ZONE).toInstant();
    private static final String SIGNATURE_KEY = "11111111-1111-1111-1111-111111111111";
    private static final String COPY_KEY = "33333333-3333-3333-3333-333333333333";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock inspectionTestClock() {
            return Clock.fixed(NOW, ZONE);
        }
    }

    // Spy kho thật: chỉ chặn phần gọi S3 của ảnh chữ ký
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private FileStorage fileStorage;

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private FoodSampleService foodSampleService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser approver;
    private CurrentUser plainVp;
    private CurrentUser kitchen;
    private long campus;
    private long otherCampus;
    private long year;
    private long kindergartenClass;
    private long nurseryClass;
    private long com;
    private long thit;
    private long rice;

    @BeforeEach
    void setUp() {
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-I', 'Cơ sở I', TRUE) RETURNING id");
        otherCampus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-J', 'Cơ sở J', FALSE) RETURNING id");
        long ag2 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-2'", Long.class);
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        kindergartenClass = classroom("Lớp MG", ag3);
        nurseryClass = classroom("Lớp NT", ag2);
        rice = food("GAO", "Gạo", "DRY");
        com = dish("COM", "Cơm", rice, ag3);
        thit = dish("THITRIM", "Thịt rim", rice, ag3);
        week(com, thit);

        CurrentUser vp = user("vp-i", CareNestRole.VICE_PRINCIPAL, campus);
        plainVp = vp;
        approver = withPermissions(vp, Set.of(Permission.FOOD_SAFETY_APPROVE));
        kitchen = user("kitchen-i", CareNestRole.KITCHEN_STAFF, campus);
        jdbc.update(
                "INSERT INTO user_signatures (user_id, storage_key, content_type, size_bytes, is_default)"
                        + " VALUES (?, ?, 'image/png', 12, TRUE)",
                approver.userId(),
                SIGNATURE_KEY);
        org.mockito.Mockito.doReturn(PNG).when(fileStorage).loadBytes(FileCategory.USER_SIGNATURE, SIGNATURE_KEY);
        org.mockito.Mockito.doReturn(new FileStorage.StoredFile(COPY_KEY, "image/png", PNG.length))
                .when(fileStorage)
                .store(org.mockito.ArgumentMatchers.eq(FileCategory.INSPECTION_SIGNATURE), any(), any(byte[].class));
        org.mockito.Mockito.doReturn(PNG).when(fileStorage).loadBytes(FileCategory.INSPECTION_SIGNATURE, COPY_KEY);
    }

    @Test
    @DisplayName("AC-1, INS-01: M2 dựng từ thực đơn công bố, suất đã xác nhận tách Nhà trẻ / Mẫu giáo")
    void processSkeletonSplitsServingsByBlock() {
        confirmedCount(kindergartenClass, 10);
        confirmedCount(nurseryClass, 6);

        List<Map<String, Object>> rows = sheet(approver, InspectionTab.PROCESS).rows();

        assertThat(rows)
                .extracting(r -> r.get("sessionLabel"), r -> r.get("dishName"), r -> r.get("servings"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Bữa trưa · Nhà trẻ", "Cơm", 6),
                        org.assertj.core.groups.Tuple.tuple("Bữa trưa · Mẫu giáo", "Cơm", 10),
                        org.assertj.core.groups.Tuple.tuple("Bữa trưa · Mẫu giáo", "Thịt rim", 10));
        assertThat(inspectionService.getDay(approver, campus, MONDAY).canEdit()).isTrue();
        assertThat(inspectionService.getDay(plainVp, campus, MONDAY).canEdit()).isFalse();
    }

    @Test
    @DisplayName("M1 tươi: dựng từ kiểm nhận hàng tươi kèm nhà cung cấp, khối lượng theo kg")
    void freshSkeletonFromCheckWithSupplier() {
        long pork = food("THIT", "Thịt", "FRESH");
        long check = insertId("INSERT INTO fresh_checks (campus_id, check_date, round, checked_by, checked_at,"
                + " supplier_id) VALUES (" + campus + ", '" + MONDAY + "', 2, " + approver.userId() + ", '" + NOW
                + "', " + supplier() + ") RETURNING id");
        jdbc.update(
                "INSERT INTO fresh_check_lines (check_id, food_id, expected_qty, received_qty, result)"
                        + " VALUES (?, ?, 2000, 2500, 'ENOUGH')",
                check,
                pork);

        assertThat(sheet(approver, InspectionTab.FRESH).rows()).singleElement().satisfies(row -> {
            assertThat(row)
                    .containsEntry("key", "food:" + pork)
                    .containsEntry("name", "Thịt")
                    .containsEntry("unit", "kg")
                    .containsEntry("quantity", 2.5)
                    .containsEntry("receivedAt", "10:30")
                    .containsEntry("supplierName", "Cơ sở Hòa Phát")
                    .containsEntry("supplierContact", "12 Lê Lợi, 0901234567")
                    .containsEntry("deliveryPerson", "Anh Nam");
        });
    }

    @Test
    @DisplayName("M1 khô: dựng từ lô đã trừ kho cho bữa ăn của ngày kèm nhà cung cấp, ngày nhập, hạn dùng")
    void drySkeletonFromIssuedLots() {
        long receipt = insertId("INSERT INTO stock_receipts (campus_id, supplier_id, received_date, status)"
                + " VALUES (" + campus + ", " + supplier() + ", '2026-11-02', 'CONFIRMED') RETURNING id");
        long lot = insertId("INSERT INTO stock_receipt_lines (receipt_id, food_id, lot_no, expiry_date, quantity,"
                + " unit_price) VALUES (" + receipt + ", " + rice + ", 'L1', '2027-03-01', 50000, 20) RETURNING id");
        jdbc.update(
                "INSERT INTO stock_movements (lot_id, campus_id, food_id, movement_type, quantity, meal_date)"
                        + " VALUES (?, ?, ?, 'DEDUCT', -1200, ?)",
                lot,
                campus,
                rice,
                MONDAY);
        // Ngày khác không vào khung
        jdbc.update(
                "INSERT INTO stock_movements (lot_id, campus_id, food_id, movement_type, quantity, meal_date)"
                        + " VALUES (?, ?, ?, 'DEDUCT', -900, ?)",
                lot,
                campus,
                rice,
                MONDAY.plusDays(1));

        assertThat(sheet(approver, InspectionTab.DRY).rows()).singleElement().satisfies(row -> {
            assertThat(row)
                    .containsEntry("key", "lot:" + lot)
                    .containsEntry("name", "Gạo")
                    .containsEntry("quantity", 1.2)
                    .containsEntry("receivedAt", "2026-11-02")
                    .containsEntry("expiryDate", "2027-03-01")
                    .containsEntry("supplierName", "Cơ sở Hòa Phát");
        });
    }

    private long supplier() {
        return insertId("INSERT INTO suppliers (name, phone, address, delivery_person)"
                + " VALUES ('Cơ sở Hòa Phát', '0901234567', '12 Lê Lợi', 'Anh Nam') RETURNING id");
    }

    @Test
    @DisplayName("M5 dựng từ bản lưu mẫu của bếp")
    void sampleSkeletonFromKitchenRecord() {
        confirmedCount(kindergartenClass, 10);
        FoodSampleResponse sample = foodSampleService.createSample(
                kitchen,
                new FoodSampleRequest(
                        MONDAY,
                        MealSession.LUNCH,
                        List.of(com, thit),
                        null,
                        null,
                        List.of(
                                new FoodSampleRequest.DishDetailInput(com, "100 g", "Hộp nhựa", "2-8 °C"),
                                new FoodSampleRequest.DishDetailInput(thit, "80 g", "Hộp nhựa", "2-8 °C"))));

        List<Map<String, Object>> rows = sheet(approver, InspectionTab.SAMPLE).rows();

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("key", "sample:" + sample.id());
            assertThat(row).containsEntry("dishName", "Cơm, Thịt rim").containsEntry("servings", 10);
            // Mẫu 5: giá trị khác nhau ghi kèm tên món, giống nhau ghi một lần
            assertThat(row)
                    .containsEntry("weight", "Cơm: 100 g; Thịt rim: 80 g")
                    .containsEntry("container", "Hộp nhựa")
                    .containsEntry("temperature", "2-8 °C");
            assertThat(row.get("takenAt")).isEqualTo(NOW.toString());
        });
    }

    @Test
    @DisplayName("AC-2..AC-5, AC-7: lưu nháp qua jsonb, ký chép ảnh và chụp dòng, sửa trực tiếp 409, sửa lại có lý do")
    void saveSignReopenRoundTrip() {
        confirmedCount(kindergartenClass, 10);
        List<Map<String, Object>> skeleton =
                sheet(approver, InspectionTab.SERVE).rows();
        List<Map<String, Object>> input = skeleton.stream()
                .map(row -> {
                    // Client chỉ gửi khóa dòng và ô PHT nhập; ô hệ thống do BE gộp từ nguồn
                    Map<String, Object> filled = new LinkedHashMap<>();
                    filled.put("key", row.get("key"));
                    filled.put("servedAt", "10:45");
                    filled.put("sensory", "PASS");
                    return filled;
                })
                .toList();

        InspectionSheetResponse saved = inspectionService.save(
                approver,
                campus,
                MONDAY,
                InspectionTab.SERVE,
                new InspectionSaveRequest(0, Map.of("place", "Bếp ăn"), input));
        assertThat(saved.version()).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();

        InspectionSheetResponse reloaded = sheet(approver, InspectionTab.SERVE);
        assertThat(reloaded.saved()).isTrue();
        assertThat(reloaded.header()).containsEntry("place", "Bếp ăn");
        // Ba dòng: món nhà trẻ (chưa có suất chốt ⇒ 0) và hai món mẫu giáo
        assertThat(reloaded.rows()).hasSize(3).allSatisfy(r -> assertThat(r).containsEntry("servedAt", "10:45"));

        InspectionSheetResponse signed = inspectionService.sign(approver, campus, MONDAY, InspectionTab.SERVE, null);
        entityManager.flush();
        entityManager.clear();
        assertThat(signed.status()).isEqualTo(InspectionStatus.SIGNED);
        assertThat(jdbc.queryForObject(
                        "SELECT signature ->> 'imageKey' FROM inspection_sheets WHERE campus_id = ? AND tab = 'SERVE'",
                        String.class,
                        campus))
                .isEqualTo(COPY_KEY);

        // Bản chụp có đủ ô hệ thống dù client chỉ gửi ô PHT nhập
        assertThat(signed.rows())
                .extracting(r -> r.get("dishName"))
                .containsExactlyInAnyOrder("Cơm", "Cơm", "Thịt rim");

        // Nguồn đổi sau khi ký: bản chụp không đổi
        jdbc.update("UPDATE meal_count_lines SET normal = 99, present = 99");
        assertThat(sheet(approver, InspectionTab.SERVE).rows())
                .extracting(r -> r.get("servings"))
                .containsExactlyInAnyOrder(0, 10, 10);

        expectCode(
                () -> inspectionService.save(
                        approver, campus, MONDAY, InspectionTab.SERVE, new InspectionSaveRequest(1, Map.of(), input)),
                ApiCode.INSPECTION_SIGNED);

        InspectionSheetResponse reopened = inspectionService.reopen(
                approver, campus, MONDAY, InspectionTab.SERVE, new InspectionReopenRequest("Nhập sai giờ chia món"));
        entityManager.flush();
        entityManager.clear();
        assertThat(reopened.status()).isEqualTo(InspectionStatus.DRAFT);
        assertThat(reopened.history()).hasSize(2);
        // Nháp lấy lại số suất mới từ nguồn, ô PHT đã nhập giữ nguyên
        assertThat(sheet(approver, InspectionTab.SERVE).rows())
                .extracting(r -> r.get("servings"))
                .containsExactlyInAnyOrder(0, 99, 99);
        assertThat(sheet(approver, InspectionTab.SERVE).rows())
                .allSatisfy(r -> assertThat(r).containsEntry("servedAt", "10:45"));
    }

    @Test
    @DisplayName("AC-6: PHT không có mã duyệt hoặc ở điểm trường khác không ghi được")
    void writeNeedsPermissionOnCampus() {
        InspectionSaveRequest request = new InspectionSaveRequest(0, Map.of(), List.of());

        expectCode(
                () -> inspectionService.save(plainVp, campus, MONDAY, InspectionTab.PROCESS, request),
                ApiCode.INSPECTION_ACCESS_DENIED);
        expectCode(
                () -> inspectionService.save(approver, otherCampus, MONDAY, InspectionTab.PROCESS, request),
                ApiCode.INSPECTION_ACCESS_DENIED);
    }

    @Test
    @DisplayName("AC-8: ảnh trên biên bản chỉ tải được ở đúng điểm trường chứa nó")
    void signatureImageScopedToCampus() {
        List<Map<String, Object>> rows = List.of(row("a"));
        inspectionService.save(
                approver, campus, MONDAY, InspectionTab.SAMPLE, new InspectionSaveRequest(0, Map.of(), rows));
        inspectionService.sign(approver, campus, MONDAY, InspectionTab.SAMPLE, null);
        entityManager.flush();
        entityManager.clear();

        ImageContent image = inspectionService.getSignatureImage(approver, campus, COPY_KEY);
        assertThat(image.contentType()).isEqualTo("image/png");

        // Lịch sử giữ ảnh của lần ký bị thay: vẫn tải được sau khi sửa lại
        inspectionService.reopen(
                approver, campus, MONDAY, InspectionTab.SAMPLE, new InspectionReopenRequest("Soát lại số liệu"));
        entityManager.flush();
        entityManager.clear();
        assertThat(inspectionService
                        .getSignatureImage(approver, campus, COPY_KEY)
                        .content())
                .isEqualTo(PNG);

        CurrentUser otherCampusUser = user("vp-j", CareNestRole.VICE_PRINCIPAL, otherCampus);
        expectCode(
                () -> inspectionService.getSignatureImage(otherCampusUser, otherCampus, COPY_KEY),
                ApiCode.SIGNATURE_NOT_FOUND);
    }

    private static Map<String, Object> row(String key) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        return row;
    }

    private InspectionSheetResponse sheet(CurrentUser user, InspectionTab tab) {
        InspectionDayResponse day = inspectionService.getDay(user, campus, MONDAY);
        return day.sheets().stream().filter(s -> s.tab() == tab).findFirst().orElseThrow();
    }

    private void confirmedCount(long classroom, int normal) {
        // Mỗi (điểm trường, ngày, bữa) chỉ có một sổ báo ăn; các lớp là các dòng của sổ đó
        List<Long> existing = jdbc.queryForList(
                "SELECT id FROM meal_counts WHERE campus_id = ? AND date = ? AND session = 'LUNCH'",
                Long.class,
                campus,
                MONDAY);
        long count = existing.isEmpty()
                ? insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status,"
                        + " aggregated_at, confirmed_at) VALUES (" + campus + ", " + year + ", '" + MONDAY
                        + "', 'LUNCH', 'CONFIRMED', now(), now()) RETURNING id")
                : existing.getFirst();
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal,"
                        + " substitute) VALUES (?, ?, FALSE, ?, 0, 0, ?, 0)",
                count,
                classroom,
                normal,
                normal);
    }

    private long classroom(String name, long ageGroup) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", '" + name + "', " + ageGroup + ") RETURNING id");
    }

    /** Thứ Hai công bố: bữa trưa khối mẫu giáo đủ hai món, khối nhà trẻ chỉ món đầu. */
    private void week(long first, long second) {
        long week = insertId("INSERT INTO meal_plan_weeks (week_start, status, published_at)" + " VALUES ('" + MONDAY
                + "', 'PUBLISHED', now()) RETURNING id");
        jdbc.update(
                "UPDATE meal_plan_weeks SET days = jsonb_build_array("
                        + "jsonb_build_object('date', ?::date, 'off', false, 'note', '')) WHERE id = ?",
                MONDAY,
                week);
        item(week, "KINDERGARTEN", first);
        item(week, "KINDERGARTEN", second);
        item(week, "NURSERY", first);
    }

    private void item(long week, String block, long dish) {
        jdbc.update(
                "INSERT INTO meal_plan_items (week_id, menu_date, session, menu_block, dish_id)"
                        + " VALUES (?, ?, 'LUNCH', ?, ?)",
                week,
                MONDAY,
                block,
                dish);
    }

    private long food(String code, String name, String storage) {
        return insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin) VALUES ('" + code
                        + "', '" + name + "', 'OTHER', 'g', 'kg', 1000, 20000, 100, 5, 1, 10, 'TEST', 0, '" + storage
                        + "', 'PLANT') RETURNING id");
    }

    private long dish(String code, String name, long foodId, long ageGroup) {
        long id = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('" + code + "', '" + name
                + "', 'MAIN') RETURNING id");
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, 50)",
                id,
                foodId,
                ageGroup);
        return id;
    }

    /** Tài khoản có phân công còn hiệu lực ở điểm trường. */
    private CurrentUser user(String username, CareNestRole role, long campusId) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) VALUES (?, ?, ?, '2026-01-01')",
                id,
                role.name(),
                campusId);
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
