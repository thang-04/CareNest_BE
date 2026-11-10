package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.observation.RewardDtos.RewardClassDetail;
import com.carenest.dto.observation.RewardDtos.RewardDecisionRequest;
import com.carenest.dto.observation.RewardDtos.RewardItemRequest;
import com.carenest.dto.observation.RewardDtos.RewardListRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.RewardListService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.RewardStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Đề xuất khen thưởng cuối năm (OBS-14) trên PostgreSQL thật: GV lập → PHT điểm trường xét → HT duyệt. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class RewardListIntegrationTest {

    private static final String GOOD = "Bé ngoan";
    private static final String EXCELLENT = "Bé chăm ngoan xuất sắc";
    private static final String UNCLE_HO = "Cháu ngoan Bác Hồ";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2027-05-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private RewardListService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private long classA;
    private long kid1;
    private long kid2;

    @BeforeEach
    void setUp() {
        long campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-RA', 'A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-RB', 'B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA = classroom(campusA, year, "Lớp RA", ag3);
        long classB = classroom(campusB, year, "Lớp RB", ag3);
        principal = user("principal-r", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("vp-ra", CareNestRole.VICE_PRINCIPAL);
        vpB = user("vp-rb", CareNestRole.VICE_PRINCIPAL);
        campusRole(vpA, campusA);
        campusRole(vpB, campusB);
        teacherA = user("teacher-ra", CareNestRole.TEACHER);
        teacherB = user("teacher-rb", CareNestRole.TEACHER);
        teach(teacherA, classA);
        teach(teacherB, classB);
        kid1 = child("An");
        kid2 = child("Bình");
        enroll(kid1, classA);
        enroll(kid2, classA);
        // Chỉ An có đánh giá cuối năm đã xác nhận
        jdbc.update(
                "INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id, period_type,"
                        + " period_start, period_end, status, final_content, confirmed_by, confirmed_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, 'YEAR', '2026-09-01', '2027-05-31', 'CONFIRMED',"
                        + " 'Trẻ phát triển tốt', ?, now(), now(), now())",
                kid1,
                classA,
                campusA,
                year,
                teacherA.userId());
    }

    @Test
    void teacherSubmitsVicePrincipalReturnsThenForwardsAndPrincipalApproves() {
        RewardClassDetail saved = service.save(teacherA, classA, list(item(kid1, GOOD, UNCLE_HO)), false);
        assertThat(saved.status()).isEqualTo(RewardStatus.DRAFT);
        RewardClassDetail sent = service.save(teacherA, classA, list(item(kid1, GOOD, UNCLE_HO)), true);
        assertThat(sent.status()).isEqualTo(RewardStatus.PENDING_VP);
        long listId = sent.listId();

        // PHT điểm trường khác không xét; trả lại phải có lý do
        expectCode(
                () -> service.review(vpB, listId, new RewardDecisionRequest("FORWARD", null)),
                ApiCode.REWARD_REVIEW_FORBIDDEN);
        assertThatThrownBy(() -> service.review(vpA, listId, new RewardDecisionRequest("RETURN", " ")))
                .isInstanceOf(GlobalException.class);
        assertThat(service.review(vpA, listId, new RewardDecisionRequest("RETURN", "Xem lại danh hiệu"))
                        .status())
                .isEqualTo(RewardStatus.RETURNED);

        // GV sửa và gửi lại; PHT chuyển HT; HT duyệt
        assertThat(service.save(teacherA, classA, list(item(kid1, EXCELLENT)), true)
                        .status())
                .isEqualTo(RewardStatus.PENDING_VP);
        assertThat(service.review(vpA, listId, new RewardDecisionRequest("FORWARD", null))
                        .status())
                .isEqualTo(RewardStatus.PENDING_PRINCIPAL);
        RewardClassDetail approved = service.decide(principal, listId, new RewardDecisionRequest("APPROVE", null));
        assertThat(approved.status()).isEqualTo(RewardStatus.APPROVED);
        assertThat(approved.children())
                .filteredOn(c -> c.childId() == kid1)
                .singleElement()
                .satisfies(c -> assertThat(c.titles()).containsExactly(EXCELLENT));

        // Đã duyệt: GV không sửa được nữa
        expectCode(() -> service.save(teacherA, classA, list(item(kid1, GOOD)), false), ApiCode.REWARD_STATUS_CHANGED);
    }

    @Test
    void titlesNeedConfirmedYearEndAndOneGeneralTitle() {
        expectCode(
                () -> service.save(teacherA, classA, list(item(kid2, GOOD)), false), ApiCode.REWARD_YEAR_END_REQUIRED);
        expectCode(
                () -> service.save(teacherA, classA, list(item(kid1, GOOD, EXCELLENT)), false),
                ApiCode.REWARD_TITLE_CONFLICT);
        expectCode(() -> service.save(teacherA, classA, list(), true), ApiCode.REWARD_LIST_EMPTY);
        // GV lớp khác không lập danh sách lớp này
        expectCode(() -> service.save(teacherB, classA, list(item(kid1, GOOD)), false), ApiCode.REWARD_EDIT_FORBIDDEN);
    }

    @Test
    void schoolDaysFollowCalendarEvenWhenAttendanceWasNotTaken() {
        // Lớp chưa điểm danh ngày nào: ngày học vẫn theo lịch năm học (thứ 2–6, trừ ngày nghỉ), báo ngày chưa điểm danh
        long year = jdbc.queryForObject("SELECT school_year_id FROM classrooms WHERE id = ?", Long.class, classA);
        jdbc.update(
                "INSERT INTO school_holidays (school_year_id, start_date, end_date, name) VALUES (?, '2026-11-20',"
                        + " '2026-11-20', 'Nhà giáo')",
                year);
        RewardClassDetail detail = service.detail(teacherA, classA);
        var an = detail.children().stream()
                .filter(c -> c.childId() == kid1)
                .findFirst()
                .orElseThrow();
        assertThat(an.schoolDays()).isPositive();
        assertThat(an.presentDays()).isZero();
        assertThat(detail.unrecordedDays())
                .isNotEmpty()
                .allSatisfy(d -> assertThat(d.getDayOfWeek().getValue()).isLessThan(6))
                .doesNotContain(java.time.LocalDate.of(2026, 11, 20))
                .contains(java.time.LocalDate.of(2026, 11, 19));
        // Hôm nay là ngày học nhưng chưa tới lúc nhắc (chỉ nhắc ngày đã qua)
        assertThat(an.schoolDays()).isEqualTo(detail.unrecordedDays().size() + 1);
    }

    @Test
    void principalDecidesListStillWaitingForVicePrincipal() {
        long listId =
                service.save(teacherA, classA, list(item(kid1, GOOD)), true).listId();
        assertThat(service.decide(principal, listId, new RewardDecisionRequest("APPROVE", null))
                        .status())
                .isEqualTo(RewardStatus.APPROVED);
        // PHT không phê duyệt thay HT
        expectCode(
                () -> service.review(vpA, listId, new RewardDecisionRequest("FORWARD", null)),
                ApiCode.REWARD_STATUS_CHANGED);
    }

    private static RewardListRequest list(RewardItemRequest... items) {
        return new RewardListRequest(List.of(items));
    }

    private static RewardItemRequest item(long childId, String... titles) {
        return new RewardItemRequest(childId, List.of(titles), null);
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusId
                + ", " + yearId + ", '" + name + "', " + ageGroupId + ") RETURNING id");
    }

    private long child(String name) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES"
                + " ('IT' || substr(md5(random()::text), 1, 12), '" + name
                + "', '2023-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
    }

    private void enroll(long childId, long classroomId) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childId,
                classroomId);
    }

    private void campusRole(CurrentUser user, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                user.userId(),
                campusId);
    }

    private void teach(CurrentUser user, long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                user.userId(),
                classroomId);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case PRINCIPAL -> Set.of(Permission.ASSESSMENT_VIEW);
                    case VICE_PRINCIPAL -> Set.of(Permission.ASSESSMENT_VIEW, Permission.REWARD_REVIEW);
                    case TEACHER, TEAM_LEADER -> Set.of(Permission.ASSESSMENT_VIEW, Permission.ASSESSMENT_RECORD);
                    default -> Set.of();
                };
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
