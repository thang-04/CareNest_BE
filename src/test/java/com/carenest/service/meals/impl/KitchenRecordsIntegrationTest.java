package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.meals.DayBoardResponse;
import com.carenest.dto.meals.FoodSampleDisposeRequest;
import com.carenest.dto.meals.FoodSampleRequest;
import com.carenest.dto.meals.FoodSampleResponse;
import com.carenest.dto.meals.KitchenDayResponse;
import com.carenest.dto.meals.MealPhotoResponse;
import com.carenest.dto.meals.MealPhotoWindowResponse;
import com.carenest.dto.meals.MealPhotoWindowsRequest;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.StorageService;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.DayBoardService;
import com.carenest.service.meals.FoodSampleService;
import com.carenest.service.meals.KitchenDayService;
import com.carenest.service.meals.MealPhotoService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FoodSampleDisposalStatus;
import com.carenest.utils.enums.FoodSampleStatus;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Bếp hôm nay, lưu/hủy mẫu thức ăn (NUT-21), khung giờ và ảnh bữa (NUT-16), trạng thái lưu mẫu trên trang Bán trú hôm nay. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class KitchenRecordsIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate DRAFT_MONDAY = MONDAY.plusWeeks(1);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // 10:30 sáng thứ Hai; đồng hồ cố định để khung giờ ảnh và mốc 24 giờ không phụ thuộc giờ chạy test
    private static final Instant NOW = MONDAY.atTime(10, 30).atZone(ZONE).toInstant();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock kitchenTestClock() {
            return Clock.fixed(NOW, ZONE);
        }
    }

    @MockitoBean
    private StorageService storageService;

    @Autowired
    private KitchenDayService kitchenDayService;

    @Autowired
    private FoodSampleService foodSampleService;

    @Autowired
    private MealPhotoService mealPhotoService;

    @Autowired
    private DayBoardService dayBoardService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vp;
    private CurrentUser vpApprover;
    private CurrentUser kitchen;
    private CurrentUser otherKitchen;
    private long campus;
    private long otherCampus;
    private long year;
    private long classroom;
    private long ag3;
    private long rice;
    private long pork;
    private long com;
    private long thit;

    @BeforeEach
    void setUp() {
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-K', 'Cơ sở K', TRUE) RETURNING id");
        otherCampus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-O', 'Cơ sở O', FALSE) RETURNING id");
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'Lớp K', " + ag3 + ") RETURNING id");
        long child = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Trẻ Bí Mật', '2022-06-01', 'MALE', 'ACTIVE', CURRENT_DATE)"
                        + " RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                child,
                classroom);
        rice = food("GAO", "Gạo", "DRY", "PLANT");
        pork = food("THIT", "Thịt", "FRESH", "ANIMAL");
        com = dish("COM", "Cơm", rice, 60);
        thit = dish("THITRIM", "Thịt rim", pork, 40);
        week(MONDAY, "PUBLISHED", com, thit);
        week(DRAFT_MONDAY, "DRAFT", com, thit);

        principal = user("principal", CareNestRole.PRINCIPAL, null);
        vp = user("vp", CareNestRole.VICE_PRINCIPAL, campus);
        vpApprover = withPermissions(vp, Set.of(Permission.FOOD_SAFETY_APPROVE));
        kitchen = user("kitchen", CareNestRole.KITCHEN_STAFF, campus);
        otherKitchen = user("kitchen-o", CareNestRole.KITCHEN_STAFF, otherCampus);
        when(storageService.store(any(), anyLong()))
                .thenAnswer(call -> UUID.randomUUID().toString());
        when(storageService.load(anyString())).thenReturn(new ByteArrayResource(new byte[] {1, 2, 3}));
    }

    @Test
    @DisplayName("NUT-03, AUTH-05: bếp thấy suất đã xác nhận theo lớp, không tên trẻ; số chờ xác nhận lại không hiện")
    void kitchenSeesConfirmedServingsOnly() {
        long count = insertCount(MONDAY, "LUNCH", "CONFIRMED");
        addLine(count, false, 10);
        // Số mới đang chờ xác nhận lại: bếp vẫn dùng 10
        addLine(count, true, 14);
        // Bữa sáng chưa xác nhận: không hiện
        addLine(insertCount(MONDAY, "MORNING", "PENDING_CONFIRMATION"), false, 7);

        KitchenDayResponse day = kitchenDayService.getKitchenDay(kitchen, MONDAY, null);

        assertThat(day.campusId()).isEqualTo(campus);
        assertThat(day.menuStatus()).isEqualTo("PUBLISHED");
        assertThat(day.countStatus()).isEqualTo("PARTIAL");
        assertThat(day.confirmedAt()).isNotNull();
        assertThat(day.servings()).singleElement().satisfies(s -> {
            assertThat(s.session()).isEqualTo(MealSession.LUNCH);
            assertThat(s.classroomName()).isEqualTo("Lớp K");
            assertThat(s.normal()).isEqualTo(10);
        });
        assertThat(day.menu())
                .singleElement()
                .satisfies(m ->
                        assertThat(m.dishes()).extracting(d -> d.code()).containsExactlyInAnyOrder("COM", "THITRIM"));
        // 10 suất × 60 g gạo (khô), 10 × 40 g thịt (tươi), thải bỏ 0%
        assertThat(day.foods())
                .extracting(
                        f -> f.food().code(),
                        f -> f.storage(),
                        f -> f.quantity().intValue())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("GAO", "DRY", 600),
                        org.assertj.core.groups.Tuple.tuple("THIT", "FRESH", 400));
        assertThat(day.toString()).doesNotContain("Trẻ Bí Mật");
    }

    @Test
    @DisplayName("Bếp hôm nay: tuần nháp không hiện món; HT/PHT cần campusId; bếp điểm trường khác bị chặn")
    void draftMenuAndScopes() {
        KitchenDayResponse draft = kitchenDayService.getKitchenDay(kitchen, DRAFT_MONDAY, null);
        assertThat(draft.menuStatus()).isEqualTo("DRAFT");
        assertThat(draft.menu()).isEmpty();
        assertThat(draft.foods()).isEmpty();
        assertThat(draft.countStatus()).isEqualTo("NONE");

        expectCode(() -> kitchenDayService.getKitchenDay(vp, MONDAY, null), ApiCode.REQUIRED_FIELD_MISSING);
        assertThat(kitchenDayService.getKitchenDay(vp, MONDAY, campus).campusId())
                .isEqualTo(campus);
        assertThat(kitchenDayService
                        .getKitchenDay(principal, MONDAY, otherCampus)
                        .campusId())
                .isEqualTo(otherCampus);
        expectCode(() -> kitchenDayService.getKitchenDay(kitchen, MONDAY, otherCampus), ApiCode.KITCHEN_CAMPUS_DENIED);
        // Ngày nghỉ cuối tuần
        assertThat(kitchenDayService
                        .getKitchenDay(kitchen, MONDAY.plusDays(5), null)
                        .dayOff())
                .isTrue();
    }

    @Test
    @DisplayName("NUT-21: ghi lưu mẫu ⇒ RECORDED; PHT có mã duyệt ⇒ APPROVED; bếp sửa bản đã duyệt ⇒ 409")
    void recordApproveAndLockSample() {
        FoodSampleResponse created = foodSampleService.createSample(kitchen, sampleRequest(null));

        assertThat(created.status()).isEqualTo(FoodSampleStatus.RECORDED);
        assertThat(created.campusId()).isEqualTo(campus);
        assertThat(created.dishes()).extracting(d -> d.code()).containsExactly("COM", "THITRIM");
        assertThat(created.sampledAt()).isEqualTo(NOW);
        assertThat(created.canApprove()).isFalse();
        assertThat(board(vp, MONDAY).foodSampleStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(board(vp, MONDAY).foodSamplePendingCount()).isEqualTo(1);

        FoodSampleResponse edited = foodSampleService.updateSample(
                kitchen,
                created.id(),
                new FoodSampleRequest(MONDAY, MealSession.LUNCH, List.of(com), null, "  đổi món "));
        assertThat(edited.dishes()).extracting(d -> d.code()).containsExactly("COM");
        assertThat(edited.note()).isEqualTo("đổi món");

        FoodSampleResponse approved = foodSampleService.approveSample(vpApprover, created.id());
        assertThat(approved.status()).isEqualTo(FoodSampleStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo(vpApprover.userId());
        assertThat(board(vp, MONDAY).foodSampleStatus()).isEqualTo("UP_TO_DATE");
        assertThat(board(vp, MONDAY).foodSamplePendingCount()).isZero();
        assertThat(board(vp, MONDAY.plusDays(1)).foodSampleStatus()).isEqualTo("NONE");

        expectCode(
                () -> foodSampleService.updateSample(kitchen, created.id(), sampleRequest(null)),
                ApiCode.FOOD_SAMPLE_LOCKED);
        expectCode(() -> foodSampleService.approveSample(principal, created.id()), ApiCode.FOOD_SAMPLE_STATE_INVALID);
        // Bếp điểm trường khác không thấy bản này
        expectCode(
                () -> foodSampleService.updateSample(otherKitchen, created.id(), sampleRequest(null)),
                ApiCode.FOOD_SAMPLE_NOT_FOUND);
    }

    @Test
    @DisplayName(
            "NUT-21, AC-9: ghi khối lượng, dụng cụ chứa, nhiệt độ từng món; bản không gửi ⇒ rỗng; sửa không gửi ⇒ giữ")
    void sampleDishDetails() {
        // Client cũ không gửi ghi chú từng món: đọc về rỗng, không lỗi
        FoodSampleResponse plain = foodSampleService.createSample(kitchen, sampleRequest(null));
        assertThat(plain.dishes())
                .allSatisfy(d -> assertThat(List.of(d.weight(), d.container(), d.temperature()))
                        .containsOnly(""));

        FoodSampleResponse detailed = foodSampleService.createSample(
                kitchen,
                new FoodSampleRequest(
                        MONDAY,
                        MealSession.AFTERNOON,
                        List.of(com, thit),
                        null,
                        null,
                        List.of(
                                new FoodSampleRequest.DishDetailInput(com, " 100 g ", "Hộp nhựa có nắp", "2-8 °C"),
                                new FoodSampleRequest.DishDetailInput(thit, "80 g", null, "2-8 °C"))));
        assertThat(detailed.dishes())
                .extracting(d -> d.code(), d -> d.weight(), d -> d.container(), d -> d.temperature())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("COM", "100 g", "Hộp nhựa có nắp", "2-8 °C"),
                        org.assertj.core.groups.Tuple.tuple("THITRIM", "80 g", "", "2-8 °C"));
        assertThat(jdbc.queryForObject(
                        "SELECT jsonb_array_length(dish_details) FROM food_samples WHERE id = ?",
                        Integer.class,
                        detailed.id()))
                .isEqualTo(2);

        // Sửa không gửi ghi chú ⇒ giữ; bỏ món ⇒ ghi chú món đó bỏ theo
        FoodSampleResponse kept = foodSampleService.updateSample(
                kitchen,
                detailed.id(),
                new FoodSampleRequest(MONDAY, MealSession.AFTERNOON, List.of(com), null, "ghi chú"));
        assertThat(kept.dishes())
                .singleElement()
                .satisfies(d -> assertThat(d.weight()).isEqualTo("100 g"));

        // Gửi mảng rỗng ⇒ xóa hết ghi chú
        FoodSampleResponse cleared = foodSampleService.updateSample(
                kitchen,
                detailed.id(),
                new FoodSampleRequest(MONDAY, MealSession.AFTERNOON, List.of(com), null, null, List.of()));
        assertThat(cleared.dishes())
                .singleElement()
                .satisfies(d -> assertThat(d.weight()).isEmpty());

        // Món không nằm trong mẫu hoặc ghi trùng một món ⇒ 400
        expectCode(
                () -> foodSampleService.createSample(
                        kitchen,
                        new FoodSampleRequest(
                                MONDAY,
                                MealSession.LUNCH,
                                List.of(com),
                                null,
                                null,
                                List.of(new FoodSampleRequest.DishDetailInput(thit, "80 g", null, null)))),
                ApiCode.INVALID_VALUE);
        expectCode(
                () -> foodSampleService.createSample(
                        kitchen,
                        new FoodSampleRequest(
                                MONDAY,
                                MealSession.LUNCH,
                                List.of(com),
                                null,
                                null,
                                List.of(
                                        new FoodSampleRequest.DishDetailInput(com, "1", null, null),
                                        new FoodSampleRequest.DishDetailInput(com, "2", null, null)))),
                ApiCode.INVALID_VALUE);
    }

    @Test
    @DisplayName("NUT-21: hủy mẫu trước 24 giờ ⇒ 409; sau 24 giờ ⇒ RECORDED; PHT có mã duyệt ⇒ APPROVED")
    void disposeAfter24Hours() {
        FoodSampleResponse fresh =
                foodSampleService.createSample(kitchen, sampleRequest(NOW.minus(Duration.ofHours(23))));
        expectCode(
                () -> foodSampleService.disposeSample(kitchen, fresh.id(), null),
                ApiCode.FOOD_SAMPLE_DISPOSAL_TOO_EARLY);
        expectCode(() -> foodSampleService.approveDisposal(principal, fresh.id()), ApiCode.FOOD_SAMPLE_STATE_INVALID);

        FoodSampleResponse old =
                foodSampleService.createSample(kitchen, sampleRequest(NOW.minus(Duration.ofHours(25))));
        FoodSampleResponse disposed =
                foodSampleService.disposeSample(kitchen, old.id(), new FoodSampleDisposeRequest(null));
        assertThat(disposed.disposalStatus()).isEqualTo(FoodSampleDisposalStatus.RECORDED);
        assertThat(disposed.disposedAt()).isEqualTo(NOW);
        // Đã ghi hủy ⇒ bếp không sửa, không ghi hủy lần hai
        expectCode(
                () -> foodSampleService.updateSample(kitchen, old.id(), sampleRequest(null)),
                ApiCode.FOOD_SAMPLE_LOCKED);
        expectCode(() -> foodSampleService.disposeSample(kitchen, old.id(), null), ApiCode.FOOD_SAMPLE_STATE_INVALID);
        // Hai việc chờ duyệt: lưu mẫu của hai bản + hủy mẫu của bản cũ
        assertThat(board(vp, MONDAY).foodSamplePendingCount()).isEqualTo(3);

        FoodSampleResponse approved = foodSampleService.approveDisposal(vpApprover, old.id());
        assertThat(approved.disposalStatus()).isEqualTo(FoodSampleDisposalStatus.APPROVED);
        assertThat(approved.disposalApprovedBy()).isEqualTo(vpApprover.userId());
        assertThat(board(vp, MONDAY).foodSamplePendingCount()).isEqualTo(2);

        assertThat(foodSampleService.getSamples(vp, campus, MONDAY, MONDAY))
                .hasSize(2)
                .allSatisfy(s -> assertThat(s.canApprove()).isFalse());
        assertThat(foodSampleService.getSamples(vpApprover, campus, MONDAY, MONDAY))
                .allSatisfy(s -> assertThat(s.canApprove()).isTrue());
        assertThat(foodSampleService.getSamples(kitchen, null, MONDAY, MONDAY)).hasSize(2);
    }

    @Test
    @DisplayName("AUTH-20: PHT không có FOOD_SAFETY_APPROVE (hoặc ngoài điểm trường) không duyệt được; món lạ bị chặn")
    void approvalNeedsPermissionAndValidDishes() {
        FoodSampleResponse sample =
                foodSampleService.createSample(kitchen, sampleRequest(NOW.minus(Duration.ofHours(30))));
        foodSampleService.disposeSample(kitchen, sample.id(), null);

        expectCode(() -> foodSampleService.approveSample(vp, sample.id()), ApiCode.FOOD_SAFETY_ACCESS_DENIED);
        expectCode(() -> foodSampleService.approveDisposal(vp, sample.id()), ApiCode.FOOD_SAFETY_ACCESS_DENIED);
        // Có mã nhưng chỉ ở điểm trường được phân công
        long outsideVpId = insertUserId("vp-out");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                outsideVpId,
                otherCampus);
        CurrentUser outsideVp = new CurrentUser(
                outsideVpId,
                "kc-vp-out",
                "vp-out",
                "vp-out",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.FOOD_SAFETY_APPROVE));
        expectCode(() -> foodSampleService.approveSample(outsideVp, sample.id()), ApiCode.FOOD_SAFETY_ACCESS_DENIED);

        assertThat(foodSampleService.approveSample(principal, sample.id()).status())
                .isEqualTo(FoodSampleStatus.APPROVED);

        expectCode(
                () -> foodSampleService.createSample(
                        kitchen, new FoodSampleRequest(MONDAY, MealSession.LUNCH, List.of(-1L), null, null)),
                ApiCode.FOOD_SAMPLE_DISH_INVALID);
        expectCode(() -> foodSampleService.createSample(vp, sampleRequest(null)), ApiCode.KITCHEN_CAMPUS_DENIED);
        expectCode(
                () -> foodSampleService.createSample(
                        kitchen,
                        new FoodSampleRequest(MONDAY.plusDays(1), MealSession.LUNCH, List.of(com), null, null)),
                ApiCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("NUT-16: chưa cấu hình hoặc ngoài khung giờ ⇒ 409; trong khung giờ ⇒ lưu, đăng lại thì thay ảnh")
    void uploadWithinWindowAndReplace() {
        MockMultipartFile jpeg = jpeg();
        expectCode(
                () -> mealPhotoService.uploadPhoto(kitchen, MealSession.LUNCH, jpeg),
                ApiCode.MEAL_PHOTO_WINDOW_NOT_CONFIGURED);

        mealPhotoService.updateWindows(principal, windows(MealSession.LUNCH, "12:00", "13:00"));
        expectCode(
                () -> mealPhotoService.uploadPhoto(kitchen, MealSession.LUNCH, jpeg), ApiCode.MEAL_PHOTO_OUT_OF_WINDOW);

        mealPhotoService.updateWindows(principal, windows(MealSession.LUNCH, "10:00", "11:00"));
        MealPhotoResponse first = mealPhotoService.uploadPhoto(kitchen, MealSession.LUNCH, jpeg);
        assertThat(first.date()).isEqualTo(MONDAY);
        assertThat(first.campusId()).isEqualTo(campus);
        assertThat(first.contentType()).isEqualTo("image/jpeg");
        String firstKey = storageKeyOf(first.id());

        MealPhotoResponse second = mealPhotoService.uploadPhoto(kitchen, MealSession.LUNCH, jpeg);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(storageKeyOf(second.id())).isNotEqualTo(firstKey);
        verify(storageService, times(2)).store(any(), anyLong());
        assertThat(mealPhotoService.getPhotos(vp, campus, MONDAY))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.session()).isEqualTo(MealSession.LUNCH);
                    assertThat(p.id()).isEqualTo(first.id());
                });
        assertThat(mealPhotoService.getPhotos(kitchen, null, null)).hasSize(1);
        assertThat(mealPhotoService.getPhotoContent(vp, first.id()).contentType())
                .isEqualTo("image/jpeg");
        // Bếp điểm trường khác không xem được ảnh
        expectCode(() -> mealPhotoService.getPhotoContent(otherKitchen, first.id()), ApiCode.KITCHEN_CAMPUS_DENIED);
        expectCode(() -> mealPhotoService.getPhotoContent(vp, -1L), ApiCode.MEAL_PHOTO_NOT_FOUND);
    }

    @Test
    @DisplayName("NUT-16: ảnh sai loại hoặc quá 10 MB ⇒ 400; HT sửa khung giờ, PHT ⇒ 403; khung giờ sai ⇒ 400")
    void photoValidationAndWindowPermissions() {
        mealPhotoService.updateWindows(principal, windows(MealSession.LUNCH, "10:00", "11:00"));
        expectCode(
                () -> mealPhotoService.uploadPhoto(
                        kitchen,
                        MealSession.LUNCH,
                        new MockMultipartFile("file", "a.txt", "image/jpeg", "hello world!".getBytes())),
                ApiCode.MEAL_PHOTO_INVALID);
        expectCode(
                () -> mealPhotoService.uploadPhoto(
                        kitchen, MealSession.LUNCH, new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0])),
                ApiCode.MEAL_PHOTO_INVALID);
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        expectCode(
                () -> mealPhotoService.uploadPhoto(
                        kitchen, MealSession.LUNCH, new MockMultipartFile("file", "big.jpg", "image/jpeg", big)),
                ApiCode.MEAL_PHOTO_INVALID);
        expectCode(() -> mealPhotoService.uploadPhoto(vp, MealSession.LUNCH, jpeg()), ApiCode.KITCHEN_CAMPUS_DENIED);

        expectCode(
                () -> mealPhotoService.updateWindows(vp, windows(MealSession.LUNCH, "09:00", "10:00")),
                ApiCode.MEAL_PHOTO_WINDOW_FORBIDDEN);
        expectCode(
                () -> mealPhotoService.updateWindows(principal, windows(MealSession.LUNCH, "11:00", "11:00")),
                ApiCode.MEAL_PHOTO_WINDOW_INVALID);

        List<MealPhotoWindowResponse> updated =
                mealPhotoService.updateWindows(principal, windows(MealSession.MORNING, "07:00", "08:30"));
        assertThat(updated)
                .extracting(w -> w.session(), w -> w.startTime(), w -> w.endTime())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                MealSession.MORNING, LocalTime.of(7, 0), LocalTime.of(8, 30)),
                        org.assertj.core.groups.Tuple.tuple(
                                MealSession.LUNCH, LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertThat(mealPhotoService.getWindows(kitchen)).hasSize(2);
        assertThat(mealPhotoService.getWindows(vp)).hasSize(2);
    }

    private FoodSampleRequest sampleRequest(Instant sampledAt) {
        return new FoodSampleRequest(MONDAY, MealSession.LUNCH, List.of(com, thit), sampledAt, null);
    }

    private static MealPhotoWindowsRequest windows(MealSession session, String start, String end) {
        return new MealPhotoWindowsRequest(List.of(
                new MealPhotoWindowsRequest.WindowInput(session, LocalTime.parse(start), LocalTime.parse(end))));
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile(
                "file", "bua.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16
                });
    }

    private String storageKeyOf(long photoId) {
        return jdbc.queryForObject("SELECT storage_key FROM meal_photos WHERE id = ?", String.class, photoId);
    }

    private DayBoardResponse.CampusBoard board(CurrentUser user, LocalDate date) {
        return dayBoardService.getDayBoard(user, date, campus).campuses().stream()
                .filter(c -> c.campusId().equals(campus))
                .findFirst()
                .orElseThrow();
    }

    private long insertCount(LocalDate date, String session, String status) {
        return insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, aggregated_at,"
                + " confirmed_at) VALUES (" + campus + ", " + year + ", '" + date + "', '" + session + "', '" + status
                + "', now(), " + ("CONFIRMED".equals(status) ? "now()" : "NULL") + ") RETURNING id");
    }

    private void addLine(long countId, boolean pending, int normal) {
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal,"
                        + " substitute) VALUES (?, ?, ?, ?, 0, 0, ?, 0)",
                countId,
                classroom,
                pending,
                normal,
                normal);
    }

    /** Thứ Hai–Sáu: bữa trưa khối mẫu giáo gồm các món. */
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

    private long food(String code, String name, String storage, String origin) {
        return insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin) VALUES ('" + code
                        + "', '" + name + "', 'OTHER', 'g', 'kg', 1000, 20000, 100, 5, 1, 10, 'TEST', 0, '" + storage
                        + "', '" + origin + "') RETURNING id");
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

    private long insertUserId(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
    }

    /** Tạo tài khoản và phân công còn hiệu lực (campusId null ⇒ Hiệu trưởng toàn trường). */
    private CurrentUser user(String username, CareNestRole role, Long campusId) {
        long id = insertUserId(username);
        if (role == CareNestRole.PRINCIPAL) {
            jdbc.update(
                    "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                    id);
        } else {
            jdbc.update(
                    "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) VALUES (?, ?, ?, '2026-01-01')",
                    id,
                    role.name(),
                    campusId);
        }
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
