package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.carenest.dto.attendance.DailyHealthRequest;
import com.carenest.dto.attendance.PickupBoardResponse;
import com.carenest.dto.attendance.PickupChildResponse;
import com.carenest.dto.attendance.PickupRecordResponse;
import com.carenest.dto.attendance.PickupRequest;
import com.carenest.dto.notification.DeviceTokenRequest;
import com.carenest.dto.notification.NotificationListResponse;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.AccountPhotoStorage;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.account.ParentPhotoService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.attendance.PickupService;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DevicePlatform;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.PickupOutcome;
import com.carenest.utils.enums.PickupStatus;
import com.carenest.utils.enums.PickupVerification;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Đón trẻ, ảnh phụ huynh, thông báo khi trẻ được đón (plan docs/plans/completed/2026-10-31-child-pickup.md). Dữ liệu giả. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class PickupIntegrationTest {

    // Hôm nay thứ Ba 2026-11-10, 16:00 giờ trường
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final String PHOTO_KEY = "0f8fad5b-d9cb-469f-a165-70867728950e";
    // Ảnh PNG 1x1 (giả)
    private static final byte[] PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T09:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @MockitoBean
    private AccountPhotoStorage photoStorage;

    @Autowired
    private PickupService pickups;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private ParentPhotoService photos;

    @Autowired
    private AccountService accounts;

    @Autowired
    private DailyHealthService dailyHealth;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private CurrentUser parent;
    private long classA;
    private long campus;
    private long present;
    private long absent;
    private long unmarked;

    @BeforeEach
    void setUp() {
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('PK-A', 'Điểm A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        long classB = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campus + ", " + year + ", 'Lớp B', " + ag3 + ") RETURNING id");
        teacherA = user("pk-teachera", CareNestRole.TEACHER);
        teacherB = user("pk-teacherb", CareNestRole.TEACHER);
        teach(teacherA, classA);
        teach(teacherB, classB);
        parent = user("pk-parent", CareNestRole.PARENT);

        present = child("An", classA);
        absent = child("Bình", classA);
        unmarked = child("Chi", classA);
        attendance(present, "PRESENT");
        attendance(absent, "UNEXCUSED");
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, user_id) "
                        + "VALUES (?, 0, 'Mẹ An (giả)', 'MOTHER', '0900000001', '000000000001', ?)",
                present,
                parent.userId());
        jdbc.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'MOTHER')",
                parent.userId(),
                present);
    }

    @Test
    void teacherHandsOverPresentChildAndParentIsNotified() {
        PickupBoardResponse board = pickups.getBoard(teacherA, classA, null);
        assertThat(board.rows())
                .extracting(r -> r.child().id(), PickupBoardResponse.PickupRow::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(present, PickupStatus.WAITING),
                        org.assertj.core.groups.Tuple.tuple(absent, PickupStatus.ABSENT),
                        org.assertj.core.groups.Tuple.tuple(unmarked, PickupStatus.UNMARKED));
        expectError(() -> pickups.getBoard(teacherB, classA, null), ApiCode.PICKUP_ACCESS_DENIED);
        expectError(() -> pickups.getBoard(teacherA, classA, TODAY.plusDays(1)), ApiCode.PICKUP_DATE_INVALID);
        expectError(() -> pickups.getBoard(teacherA, classA, LocalDate.of(2026, 8, 31)), ApiCode.PICKUP_DATE_INVALID);
        expectError(() -> pickups.getChild(teacherB, present), ApiCode.PICKUP_CHILD_NOT_FOUND);

        PickupChildResponse context = pickups.getChild(teacherA, present);
        assertThat(context.guardians()).singleElement().satisfies(g -> {
            assertThat(g.fullName()).isEqualTo("Mẹ An (giả)");
            assertThat(g.photoUrl()).isNull();
        });

        // Gọi phụ huynh không trả: bắt buộc nội dung cuộc gọi, vẫn lưu nhưng không tính là đã trả
        expectError(
                () -> pickups.recordResult(
                        teacherA, request(present, PickupVerification.PHONE_DECLINED, null, null, "")),
                ApiCode.INVALID_REQUEST_DATA);
        PickupRecordResponse declined = pickups.recordResult(
                teacherA,
                request(present, PickupVerification.PHONE_DECLINED, null, null, "Phụ huynh báo người lạ (giả)"));
        assertThat(declined.outcome()).isEqualTo(PickupOutcome.NOT_HANDED_OVER);
        assertThat(declined.notifiedParents()).isZero();

        expectError(
                () -> pickups.recordResult(
                        teacherA, request(present, PickupVerification.PHOTO_MATCH, 5, LocalTime.of(15, 30), "")),
                ApiCode.INVALID_REQUEST_DATA);
        expectError(
                () -> pickups.recordResult(
                        teacherA, request(present, PickupVerification.PHOTO_MATCH, 0, LocalTime.of(17, 0), "")),
                ApiCode.INVALID_REQUEST_DATA);
        PickupRecordResponse handed = pickups.recordResult(
                teacherA, request(present, PickupVerification.PHOTO_MATCH, 0, LocalTime.of(15, 30), ""));
        assertThat(handed.outcome()).isEqualTo(PickupOutcome.HANDED_OVER);
        assertThat(handed.pickupPersonName()).isEqualTo("Mẹ An (giả)");
        assertThat(handed.pickupPersonRelationship()).isEqualTo(GuardianRelationship.MOTHER);
        assertThat(handed.handedOverAt()).isEqualTo(Instant.parse("2026-11-10T08:30:00Z"));
        assertThat(handed.notifiedParents()).isEqualTo(1);

        expectError(
                () -> pickups.recordResult(
                        teacherA, request(present, PickupVerification.PHOTO_MATCH, 0, LocalTime.of(15, 40), "")),
                ApiCode.PICKUP_ALREADY_HANDED_OVER);
        expectError(
                () -> pickups.recordResult(
                        teacherA, request(absent, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(15, 0), "Bố")),
                ApiCode.PICKUP_CHILD_ABSENT);
        expectError(
                () -> pickups.recordResult(
                        teacherA,
                        request(unmarked, PickupVerification.PHONE_CONFIRMED, null, LocalTime.of(15, 0), "Bố")),
                ApiCode.PICKUP_CHILD_NOT_PRESENT);

        assertThat(pickups.getBoard(teacherA, classA, TODAY).rows().getFirst()).satisfies(r -> {
            assertThat(r.status()).isEqualTo(PickupStatus.PICKED_UP);
            assertThat(r.attempts()).hasSize(2);
        });
        assertThat(pickups.listResults(teacherA, null))
                .extracting(PickupRecordResponse::id)
                .containsExactly(handed.id(), declined.id());

        // Phụ huynh nhận thông báo trẻ đã được đón
        NotificationListResponse inbox = notifications.listMyNotifications(parent, 50);
        assertThat(inbox.unreadCount()).isEqualTo(1);
        assertThat(inbox.items()).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(NotificationType.CHILD_PICKED_UP);
            assertThat(n.childId()).isEqualTo(present);
            assertThat(n.body()).contains("15:30").contains("Mẹ An (giả)");
        });
        expectError(
                () -> notifications.markRead(teacherA, inbox.items().getFirst().id()), ApiCode.NOTIFICATION_NOT_FOUND);
        notifications.markRead(parent, inbox.items().getFirst().id());
        assertThat(notifications.listMyNotifications(parent, 50).unreadCount()).isZero();

        // HLT-15: trẻ đã được trả thì không ghi thêm sức khỏe trong ngày
        expectError(
                () -> dailyHealth.addEntry(
                        teacherA,
                        new DailyHealthRequest(
                                "pk-1",
                                classA,
                                present,
                                TODAY,
                                null,
                                HealthObservation.NO_CONCERN,
                                List.of(),
                                "",
                                List.of(),
                                null)),
                ApiCode.HEALTH_CHILD_PICKED_UP);
    }

    @Test
    void parentUploadsPhotoThatClassTeacherSees() {
        when(photoStorage.store(any(), anyString())).thenReturn(PHOTO_KEY);
        when(photoStorage.load(PHOTO_KEY)).thenReturn(PNG);
        assertThat(accounts.getMyAccount(parent).profileUpdateRequired()).isTrue();
        assertThat(accounts.getMyAccount(teacherA).profileUpdateRequired()).isFalse();

        expectError(
                () -> photos.updateMyPhoto(
                        parent, new MockMultipartFile("file", "note.txt", "image/png", "không phải ảnh".getBytes())),
                ApiCode.ACCOUNT_PHOTO_INVALID);
        assertThat(photos.updateMyPhoto(parent, new MockMultipartFile("file", "me.png", "image/png", PNG))
                        .photoUploaded())
                .isTrue();
        assertThat(accounts.getMyAccount(parent).profileUpdateRequired()).isFalse();

        PickupChildResponse context = pickups.getChild(teacherA, present);
        assertThat(context.guardians().getFirst().photoUrl())
                .isEqualTo("/children/" + present + "/guardians/" + parent.userId() + "/photo");
        assertThat(photos.getGuardianPhoto(teacherA, present, parent.userId()).contentType())
                .isEqualTo("image/png");
        expectError(() -> photos.getGuardianPhoto(teacherB, present, parent.userId()), ApiCode.ACCOUNT_PHOTO_NOT_FOUND);
        expectError(() -> photos.getGuardianPhoto(teacherA, absent, parent.userId()), ApiCode.ACCOUNT_PHOTO_NOT_FOUND);
    }

    @Test
    void deviceTokenFollowsTheSignedInUser() {
        String token = "fcm-token-gia";
        notifications.registerDevice(parent, new DeviceTokenRequest(token, DevicePlatform.ANDROID));
        assertThat(deviceOwner(token)).isEqualTo(parent.userId());
        // Máy đăng nhập tài khoản khác ⇒ token chuyển sang người đó
        notifications.registerDevice(teacherA, new DeviceTokenRequest(token, DevicePlatform.ANDROID));
        assertThat(deviceOwner(token)).isEqualTo(teacherA.userId());
        notifications.unregisterDevice(parent, new DeviceTokenRequest(token, null));
        assertThat(deviceOwner(token)).isEqualTo(teacherA.userId());
        notifications.unregisterDevice(teacherA, new DeviceTokenRequest(token, null));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_tokens WHERE token = ?", Long.class, token))
                .isZero();
        expectError(
                () -> notifications.registerDevice(parent, new DeviceTokenRequest(token, null)),
                ApiCode.INVALID_REQUEST_DATA);
    }

    private static PickupRequest request(
            long childId, PickupVerification verification, Integer guardian, LocalTime time, String note) {
        return new PickupRequest(childId, verification, guardian, time, note);
    }

    // Đọc bằng JDBC nên ghi các thay đổi của Hibernate xuống trước
    private Long deviceOwner(String token) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT user_id FROM device_tokens WHERE token = ?", Long.class, token);
    }

    private long child(String name, long classroomId) {
        long id = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                + "'PK' || substr(md5(random()::text), 1, 12), '" + name
                + "', '2023-01-01', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                id,
                classroomId);
        return id;
    }

    private void attendance(long childId, String status) {
        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, recorded_by, "
                        + "recorded_at) VALUES (?, ?, ?, ?, ?, ?, now())",
                childId,
                classA,
                campus,
                TODAY,
                status,
                teacherA.userId());
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role));
    }

    private void teach(CurrentUser user, long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                user.userId(),
                classroomId);
    }

    private static void expectError(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
