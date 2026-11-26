package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.meals.AllergenResponse;
import com.carenest.dto.meals.ParentMenuResponse;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuDay;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuDish;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuMeal;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.integration.storage.InMemoryS3;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.MealPhotoService;
import com.carenest.service.meals.ParentMenuService;
import com.carenest.support.MovableClock;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FileCategory;
import com.carenest.utils.enums.MealSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Thực đơn tuần phụ huynh xem về con (PAR-01, PAR-14): quyền, khối và bữa theo lớp từng ngày, ngày trống, dị ứng. */
@SpringBootTest(properties = "carenest.storage.bucket=test-bucket")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ParentMenuIntegrationTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate PAST_MONDAY = MONDAY.minusWeeks(1);
    private static final LocalDate DRAFT_MONDAY = MONDAY.plusWeeks(1);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    private static final MovableClock CLOCK = new MovableClock();

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock parentMenuTestClock() {
            return CLOCK;
        }

        // Ký liên kết chạy cục bộ, không gọi mạng; khóa giả chỉ để ký, máy chạy test không cần credential thật
        @Bean
        @Primary
        S3Presigner parentMenuTestPresigner() {
            return S3Presigner.builder()
                    .region(Region.AP_SOUTHEAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                    .build();
        }
    }

    // Kho thật trên bucket giả trong bộ nhớ: đọc ảnh đi qua đúng tên tệp và cấu hình kho
    @MockitoBean(answers = org.mockito.Answers.CALLS_REAL_METHODS)
    private software.amazon.awssdk.services.s3.S3Client s3;

    @Autowired
    private ParentMenuService parentMenuService;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private JdbcTemplate jdbc;

    private long campusA;
    private long campusB;
    private long year;
    private long kinderClass;
    private long nurseryClass;
    private long child;
    private CurrentUser parent;

    @BeforeEach
    void setUp() {
        InMemoryS3.install(s3);
        CLOCK.set(MONDAY.atTime(10, 30).atZone(ZONE).toInstant());
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Cơ sở A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'Cơ sở B', FALSE) RETURNING id");
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address,"
                        + " phone, status) SELECT ?, id, code, name, short_name, address, phone, status FROM campuses",
                year);
        jdbc.update(
                "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name,"
                        + " age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note)"
                        + " SELECT ?, id, name, short_name, age_from_months, age_to_months,"
                        + " CASE code WHEN 'ag-2' THEN 'MORNING,LUNCH' ELSE 'LUNCH,AFTERNOON' END,"
                        + " max_class_size, nutrition_note FROM age_groups",
                year);
        kinderClass = classroom(campusA, "Lớp Mẫu giáo A", "ag-3");
        nurseryClass = classroom(campusB, "Nhà trẻ B", "ag-2");

        long rice = food("PM-GAO", "Gạo", "");
        long fish = food("PM-CA", "Cá", "FISH");
        long com = dish("PM-COM", "Cơm", rice);
        long caKho = dish("PM-CAKHO", "Cá kho", fish);
        long chao = dish("PM-CHAO", "Cháo", rice);
        for (LocalDate monday : List.of(PAST_MONDAY, MONDAY, DRAFT_MONDAY)) {
            long week = week(monday, monday.equals(DRAFT_MONDAY) ? "DRAFT" : "PUBLISHED");
            for (int i = 0; i < 5; i++) {
                LocalDate date = monday.plusDays(i);
                item(week, date, "LUNCH", "KINDERGARTEN", com, 0);
                item(week, date, "LUNCH", "KINDERGARTEN", caKho, 1);
                item(week, date, "AFTERNOON", "KINDERGARTEN", com, 0);
                item(week, date, "MORNING", "NURSERY", chao, 0);
                item(week, date, "LUNCH", "NURSERY", chao, 0);
            }
        }

        child = child();
        enroll(child, kinderClass, "2026-09-01", null);
        parent = parentOf(child, "phu-huynh");
    }

    @Test
    @DisplayName("AUTH-04: không liên kết, nhân sự kiêm phụ huynh của trẻ khác, không có role phụ huynh ⇒ 4108")
    void onlyLinkedParentCanRead() {
        CurrentUser stranger = user("nguoi-la", Set.of(CareNestRole.PARENT));
        CurrentUser teacherParent = user("gv-ph", Set.of(CareNestRole.TEACHER, CareNestRole.PARENT));
        // Có tên trong danh sách người giám hộ nhưng token không mang role phụ huynh
        CurrentUser teacherOnly = user("gv", Set.of(CareNestRole.TEACHER));
        long otherChild = child();
        enroll(otherChild, kinderClass, "2026-09-01", null);
        link(teacherOnly.userId(), otherChild);
        expectCode(() -> parentMenuService.getWeek(teacherOnly, otherChild, MONDAY), ApiCode.PARENT_CHILD_FORBIDDEN);

        for (CurrentUser denied : List.of(stranger, teacherParent, teacherOnly)) {
            expectCode(() -> parentMenuService.getWeek(denied, child, MONDAY), ApiCode.PARENT_CHILD_FORBIDDEN);
        }
    }

    @Test
    @DisplayName("CHD-04: con đã rời trường ⇒ phụ huynh mất quyền xem ngay")
    void parentOfLeftChildIsRejected() {
        jdbc.update("UPDATE enrollments SET valid_to = '2026-10-30' WHERE child_id = ?", child);

        expectCode(() -> parentMenuService.getWeek(parent, child, MONDAY), ApiCode.PARENT_CHILD_FORBIDDEN);
    }

    @Test
    @DisplayName("PAR-01: bỏ trống ⇒ tuần hiện tại; ngày giữa tuần đưa về thứ Hai; luôn đủ 5 ngày")
    void weekDefaultsToCurrentAndStartsOnMonday() {
        for (LocalDate input : new LocalDate[] {null, MONDAY.plusDays(3)}) {
            ParentMenuResponse week = parentMenuService.getWeek(parent, child, input);

            assertThat(week.childId()).isEqualTo(child);
            assertThat(week.weekStart()).isEqualTo(MONDAY);
            assertThat(week.days())
                    .extracting(ParentMenuDay::date)
                    .containsExactly(
                            MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3), MONDAY.plusDays(4));
        }
    }

    @Test
    @DisplayName("NUT-08, PAR-14: tuần nháp hoặc chưa lập ⇒ không món, không ảnh, không cảnh báo dị ứng")
    void unpublishedWeekShowsNothing() {
        photo(campusA, DRAFT_MONDAY, "LUNCH");
        photo(campusA, MONDAY.plusWeeks(2), "LUNCH");
        allergies(child, "Dâu tây");

        for (LocalDate monday : List.of(DRAFT_MONDAY, MONDAY.plusWeeks(2))) {
            ParentMenuResponse week = parentMenuService.getWeek(parent, child, monday);

            assertThat(week.published()).isFalse();
            assertThat(week.unmatchedAllergies()).isEmpty();
            assertThat(week.days())
                    .hasSize(5)
                    .allSatisfy(day -> assertThat(day.meals()).isEmpty());
        }
    }

    @Test
    @DisplayName("NUT-13, NUT-17: mỗi ngày theo khối, bữa và điểm trường của lớp con học ngày đó")
    void eachDayFollowsClassOfThatDay() {
        jdbc.update("UPDATE enrollments SET valid_to = ? WHERE child_id = ?", MONDAY.plusDays(1), child);
        enroll(child, nurseryClass, MONDAY.plusDays(2).toString(), null);
        long photoA = photo(campusA, MONDAY, "LUNCH");
        photo(campusB, MONDAY, "LUNCH");
        photo(campusA, MONDAY.plusDays(2), "LUNCH");
        long photoB = photo(campusB, MONDAY.plusDays(2), "LUNCH");
        // Bữa không thuộc lớp của con thì ảnh của bữa đó cũng không hiện
        photo(campusA, MONDAY, "MORNING");

        ParentMenuResponse week = parentMenuService.getWeek(parent, child, MONDAY);

        assertThat(week.published()).isTrue();
        ParentMenuDay monday = week.days().get(0);
        assertThat(monday.meals())
                .extracting(ParentMenuMeal::session)
                .containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        assertThat(monday.meals().get(0).dishes())
                .extracting(ParentMenuDish::name)
                .containsExactly("Cơm", "Cá kho");
        assertThat(monday.meals().get(0).photo().photoId()).isEqualTo(photoA);
        assertThat(monday.meals().get(0).photo().uploadedAt()).isNotNull();
        assertThat(monday.meals().get(1).photo()).isNull();
        ParentMenuDay wednesday = week.days().get(2);
        assertThat(wednesday.meals())
                .extracting(ParentMenuMeal::session)
                .containsExactly(MealSession.MORNING, MealSession.LUNCH);
        assertThat(wednesday.meals().get(1).dishes())
                .extracting(ParentMenuDish::name)
                .containsExactly("Cháo");
        assertThat(wednesday.meals().get(1).photo().photoId()).isEqualTo(photoB);
    }

    @Test
    @DisplayName("SCH-05, PAR-14: ngày nghỉ trường, ngày nghỉ trong kế hoạch ⇒ dayOff, không món, không ảnh")
    void daysOffAreEmpty() {
        jdbc.update(
                "INSERT INTO school_holidays (school_year_id, start_date, end_date, name) VALUES (?, ?, ?, 'Nghỉ')",
                year,
                MONDAY.plusDays(2),
                MONDAY.plusDays(2));
        jdbc.update(
                "UPDATE meal_plan_weeks SET days = jsonb_set(days, '{3,off}', 'true') WHERE week_start = ?", MONDAY);
        photo(campusA, MONDAY.plusDays(2), "LUNCH");

        List<ParentMenuDay> days =
                parentMenuService.getWeek(parent, child, MONDAY).days();

        assertThat(days)
                .extracting(ParentMenuDay::dayOff, day -> day.meals().isEmpty())
                .containsExactly(
                        tuple(false, false),
                        tuple(false, false),
                        tuple(true, true),
                        tuple(true, true),
                        tuple(false, false));
    }

    @Test
    @DisplayName("PAR-14: ngày trước nhập học và ngoài năm học ⇒ chưa học, không món, không ảnh, không báo lỗi")
    void daysWithoutClassAreMarked() {
        jdbc.update("UPDATE enrollments SET valid_from = ? WHERE child_id = ?", MONDAY.plusDays(1), child);
        photo(campusA, MONDAY, "LUNCH");

        List<ParentMenuDay> days =
                parentMenuService.getWeek(parent, child, MONDAY).days();

        assertThat(days).extracting(ParentMenuDay::enrolled).containsExactly(false, true, true, true, true);
        assertThat(days.get(0).meals()).isEmpty();
        // Xếp lớp chưa đóng không kéo sang sau khi năm học kết thúc
        assertThat(parentMenuService
                        .getWeek(parent, child, LocalDate.of(2027, 7, 5))
                        .days())
                .allSatisfy(day -> {
                    assertThat(day.enrolled()).isFalse();
                    assertThat(day.meals()).isEmpty();
                });
    }

    @Test
    @DisplayName("CHD-13, PAR-14: không báo ăn ⇒ trống từ tuần hiện tại; tuần đã qua vẫn xem, không đánh dấu dị ứng")
    void childWithoutMealsSeesOnlyPastWeeks() {
        allergies(child, "Cá biển", "Dâu tây");
        photo(campusA, MONDAY, "LUNCH");
        photo(campusA, PAST_MONDAY, "LUNCH");
        jdbc.update("UPDATE children SET meal_registered = FALSE WHERE id = ?", child);

        for (LocalDate monday : List.of(MONDAY, DRAFT_MONDAY)) {
            ParentMenuResponse hidden = parentMenuService.getWeek(parent, child, monday);
            assertThat(hidden.mealsHidden()).isTrue();
            assertThat(hidden.unmatchedAllergies()).isEmpty();
            assertThat(hidden.days()).allSatisfy(day -> assertThat(day.meals()).isEmpty());
        }

        ParentMenuResponse past = parentMenuService.getWeek(parent, child, PAST_MONDAY);
        assertThat(past.mealsHidden()).isFalse();
        assertThat(past.unmatchedAllergies()).isEmpty();
        ParentMenuMeal lunch = past.days().get(0).meals().get(0);
        assertThat(lunch.photo()).isNotNull();
        assertThat(lunch.dishes())
                .isNotEmpty()
                .allSatisfy(dish -> assertThat(dish.childAllergens()).isEmpty());
    }

    @Test
    @DisplayName("NUT-23, PAR-14: đánh dấu chất con đã khai, liệt kê chữ không khớp, không lấy dị ứng của trẻ khác")
    void allergiesOfTheChildOnly() {
        allergies(child, "Cá biển", "Dâu tây", " dâu  tây ");
        long other = child();
        enroll(other, kinderClass, "2026-09-01", null);
        allergies(other, "Sữa", "Xoài");

        ParentMenuResponse week = parentMenuService.getWeek(parent, child, MONDAY);

        assertThat(week.unmatchedAllergies()).containsExactly("Dâu tây");
        List<ParentMenuDish> lunch = week.days().get(0).meals().get(0).dishes();
        assertThat(lunch.get(0).allergens()).isEmpty();
        assertThat(lunch.get(0).childAllergens()).isEmpty();
        assertThat(lunch.get(1).allergens()).extracting(AllergenResponse::code).containsExactly("FISH");
        assertThat(lunch.get(1).childAllergens())
                .extracting(AllergenResponse::code)
                .containsExactly("FISH");
    }

    @Test
    @DisplayName("NUT-16: ảnh đang hiện trong tuần của con ⇒ trả được nội dung và liên kết, kể cả tuần đã qua")
    void visiblePhotoIsServed() throws IOException {
        for (LocalDate monday : List.of(MONDAY, PAST_MONDAY)) {
            long photo = storedPhoto(campusA, monday, "LUNCH");

            MealPhotoService.Content content = parentMenuService.getPhotoContent(parent, child, photo);
            FileUrlResponse url = parentMenuService.getPhotoUrl(parent, child, photo);

            assertThat(content).isNotNull();
            assertThat(content.contentType()).isEqualTo("image/jpeg");
            assertThat(content.resource().getContentAsByteArray()).isEqualTo(JPEG);
            assertThat(url).isNotNull();
            assertThat(url.url()).isNotBlank();
            assertThat(url.expiresAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("NUT-16, PAR-14: ảnh không hiện trong tuần của con ⇒ 6068, như ảnh không tồn tại")
    void photosOutsideChildWeekAreNotFound() {
        jdbc.update(
                "INSERT INTO school_holidays (school_year_id, start_date, end_date, name) VALUES (?, ?, ?, 'Nghỉ')",
                year,
                MONDAY.plusDays(2),
                MONDAY.plusDays(2));
        jdbc.update(
                "UPDATE meal_plan_weeks SET days = jsonb_set(days, '{3,off}', 'true') WHERE week_start = ?", MONDAY);
        jdbc.update("UPDATE enrollments SET valid_from = ? WHERE child_id = ?", MONDAY, child);

        assertPhotoNotFound(storedPhoto(campusB, MONDAY, "LUNCH"));
        assertPhotoNotFound(storedPhoto(campusA, MONDAY, "MORNING"));
        assertPhotoNotFound(storedPhoto(campusA, MONDAY.plusDays(2), "LUNCH"));
        assertPhotoNotFound(storedPhoto(campusA, MONDAY.plusDays(3), "LUNCH"));
        assertPhotoNotFound(storedPhoto(campusA, MONDAY.plusDays(5), "LUNCH"));
        assertPhotoNotFound(storedPhoto(campusA, DRAFT_MONDAY, "LUNCH"));
        assertPhotoNotFound(storedPhoto(campusA, PAST_MONDAY, "LUNCH"));
        assertPhotoNotFound(-1L);
    }

    @Test
    @DisplayName("CHD-13, PAR-14: không báo ăn ⇒ ảnh từ tuần hiện tại không xem được, tuần đã qua vẫn xem")
    void photosFollowMealRegistration() {
        long current = storedPhoto(campusA, MONDAY, "LUNCH");
        long past = storedPhoto(campusA, PAST_MONDAY, "LUNCH");
        jdbc.update("UPDATE children SET meal_registered = FALSE WHERE id = ?", child);

        assertPhotoNotFound(current);
        assertThat(parentMenuService.getPhotoContent(parent, child, past)).isNotNull();
    }

    @Test
    @DisplayName("AUTH-04, CHD-04: không liên kết hoặc con đã rời trường ⇒ 4108, không lộ ảnh có tồn tại hay không")
    void photoNeedsLinkedParent() {
        long photo = storedPhoto(campusA, MONDAY, "LUNCH");
        CurrentUser stranger = user("nguoi-la", Set.of(CareNestRole.PARENT));
        for (long id : new long[] {photo, -1L}) {
            expectCode(() -> parentMenuService.getPhotoContent(stranger, child, id), ApiCode.PARENT_CHILD_FORBIDDEN);
            expectCode(() -> parentMenuService.getPhotoUrl(stranger, child, id), ApiCode.PARENT_CHILD_FORBIDDEN);
        }

        jdbc.update("UPDATE enrollments SET valid_to = '2026-10-30' WHERE child_id = ?", child);

        expectCode(() -> parentMenuService.getPhotoContent(parent, child, photo), ApiCode.PARENT_CHILD_FORBIDDEN);
    }

    private void assertPhotoNotFound(long photoId) {
        expectCode(() -> parentMenuService.getPhotoContent(parent, child, photoId), ApiCode.MEAL_PHOTO_NOT_FOUND);
        expectCode(() -> parentMenuService.getPhotoUrl(parent, child, photoId), ApiCode.MEAL_PHOTO_NOT_FOUND);
    }

    /** Ảnh có tệp thật trong kho giả, để đọc lại được nội dung. */
    private long storedPhoto(long campusId, LocalDate date, String session) {
        long id = photo(campusId, date, session);
        String key = fileStorage.store(FileCategory.MEAL, campusId, JPEG).key();
        jdbc.update("UPDATE meal_photos SET storage_key = ? WHERE id = ?", key, id);
        return id;
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(apiCode);
    }

    private long classroom(long campusId, String name, String ageGroupCode) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) SELECT " + campusId
                + ", " + year + ", '" + name + "', id FROM age_groups WHERE code = '" + ageGroupCode
                + "' RETURNING id");
    }

    private long child() {
        return insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' ||"
                        + " substr(md5(random()::text), 1, 12), 'Trẻ Thử', '2022-06-01', 'MALE', 'ACTIVE', CURRENT_DATE)"
                        + " RETURNING id");
    }

    private void enroll(long childId, long classroomId, String validFrom, String validTo) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, ?::date, ?::date)",
                childId,
                classroomId,
                validFrom,
                validTo);
    }

    private CurrentUser user(String username, Set<CareNestRole> roles) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        CareNestRole first = roles.iterator().next();
        return new CurrentUser(
                id, "kc-" + username, username, username, null, roles, TestPermissions.defaultsOf(first));
    }

    private CurrentUser parentOf(long childId, String username) {
        CurrentUser user = user(username, Set.of(CareNestRole.PARENT));
        link(user.userId(), childId);
        return user;
    }

    private void link(long userId, long childId) {
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Mẹ (giả)', 'MOTHER', '0900000009', '000000000009', now())",
                userId,
                childId);
    }

    private void allergies(long childId, String... names) {
        CurrentUser declarer = user("khai-" + childId, Set.of(CareNestRole.PARENT));
        jdbc.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state,"
                        + " self_history_state, family_history_state, tb_contact_state, declared_by)"
                        + " VALUES (?, 'REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED',"
                        + " 'NONE_REPORTED', 'NONE_REPORTED', ?)",
                childId,
                declarer.userId());
        for (String name : names) {
            jdbc.update("INSERT INTO child_allergies (child_id, name) VALUES (?, ?)", childId, name);
        }
    }

    private long food(String code, String name, String allergenCodes) {
        long foodId = insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin, allergen_codes)"
                        + " VALUES ('" + code + "', '" + name + "', 'OTHER', 'g', 'kg', 1000, 20000, 100, 5, 1, 10,"
                        + " 'TEST', 0, 'FRESH', 'PLANT', '" + allergenCodes + "') RETURNING id");
        return foodId;
    }

    private long dish(String code, String name, long foodId) {
        long id = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('" + code + "', '" + name
                + "', 'MAIN') RETURNING id");
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child)"
                        + " SELECT ?, ?, id, 50 FROM age_groups WHERE code = 'ag-3'",
                id,
                foodId);
        return id;
    }

    private long week(LocalDate monday, String status) {
        long week = insertId("INSERT INTO meal_plan_weeks (week_start, status, published_at) VALUES ('" + monday
                + "', '" + status + "', " + ("PUBLISHED".equals(status) ? "now()" : "NULL") + ") RETURNING id");
        for (int i = 0; i < 5; i++) {
            jdbc.update(
                    "UPDATE meal_plan_weeks SET days = days || jsonb_build_array("
                            + "jsonb_build_object('date', ?::date, 'off', false, 'note', '')) WHERE id = ?",
                    monday.plusDays(i),
                    week);
        }
        return week;
    }

    private void item(long week, LocalDate date, String session, String block, long dish, int order) {
        jdbc.update(
                "INSERT INTO meal_plan_items (week_id, menu_date, session, menu_block, dish_id, sort_order)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                week,
                date,
                session,
                block,
                dish,
                order);
    }

    private long photo(long campusId, LocalDate date, String session) {
        return jdbc.queryForObject(
                "INSERT INTO meal_photos (campus_id, photo_date, session, storage_key, content_type, size_bytes,"
                        + " uploaded_at) VALUES (?, ?, ?, ?, 'image/jpeg', 10, ?::timestamptz) RETURNING id",
                Long.class,
                campusId,
                date,
                session,
                "meals/" + campusId + "/" + date + "-" + session,
                Instant.parse("2026-11-09T04:00:00Z").toString());
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
