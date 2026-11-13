package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.VicePrincipalAssignmentRequest;
import com.carenest.dto.organization.VicePrincipalAssignmentResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.VicePrincipalAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Phân công PHT vào điểm trường theo năm học trên PostgreSQL thật. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class VicePrincipalAssignmentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private VicePrincipalAssignmentService service;

    @Autowired
    private com.carenest.service.organization.StaffAssignmentService staffAssignmentService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long vp1;
    private long vp2;
    private long teacher;
    private long campusA;
    private long campusB;
    private long campusC;
    private long activeYear;
    private long plannedYear;

    @BeforeEach
    void setUp() {
        long principalId = user("principal");
        vp1 = user("vp1");
        vp2 = user("vp2");
        teacher = user("teacher");
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Campus A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name) VALUES ('CS-B', 'Campus B') RETURNING id");
        campusC = insertId("INSERT INTO campuses (code, name) VALUES ('CS-C', 'Campus C') RETURNING id");
        activeYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        plannedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2027-08-01', '2028-05-31', 'PLANNED') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vp1,
                campusA);
        // vp2 từng phụ trách B ở năm trước, năm nay chưa có
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from, valid_to) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2025-09-01', '2026-05-31')",
                vp2,
                campusB);
        principal =
                new CurrentUser(principalId, "kc-principal", "principal", "P", null, Set.of(CareNestRole.PRINCIPAL));
    }

    @Test
    void listsVicePrincipalsWithTheirCampusForTheYear() {
        Map<Long, Long> campuses = campusByUser(service.getAssignments(principal, activeYear));

        assertThat(campuses).containsOnlyKeys(vp1, vp2);
        assertThat(campuses.get(vp1)).isEqualTo(campusA);
        assertThat(campuses.get(vp2)).isNull();
    }

    @Test
    void plannedYearAssignmentStartsWithTheYearAndKeepsTheCurrentOne() {
        Map<Long, Long> planned =
                campusByUser(service.assign(principal, plannedYear, vp1, new VicePrincipalAssignmentRequest(campusB)));

        assertThat(planned.get(vp1)).isEqualTo(campusB);
        assertThat(campusByUser(service.getAssignments(principal, activeYear)).get(vp1))
                .isEqualTo(campusA);
        // Phân công PHT giữ qua các năm: dòng năm nay dừng trước ngày phân công năm sau có hiệu lực, dòng năm sau không
        // hạn
        assertThat(validTo(vp1, campusA)).isEqualTo(validFrom(vp1, campusB).minusDays(1));
        assertThat(validTo(vp1, campusB)).isNull();
    }

    @Test
    void changingTheActiveYearEndsTheOldCampusYesterdayAndKeepsNextYear() {
        service.assign(principal, plannedYear, vp1, new VicePrincipalAssignmentRequest(campusB));

        service.assign(principal, activeYear, vp1, new VicePrincipalAssignmentRequest(campusC));

        assertThat(validTo(vp1, campusA)).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(validTo(vp1, campusC)).isEqualTo(validFrom(vp1, campusB).minusDays(1));
        assertThat(campusByUser(service.getAssignments(principal, plannedYear)).get(vp1))
                .isEqualTo(campusB);
    }

    @Test
    void sameCampusIsUnchangedAndNullRemoves() {
        service.assign(principal, activeYear, vp1, new VicePrincipalAssignmentRequest(campusA));
        assertThat(count(vp1)).isEqualTo(1);

        Map<Long, Long> campuses =
                campusByUser(service.assign(principal, activeYear, vp1, new VicePrincipalAssignmentRequest(null)));

        assertThat(campuses.get(vp1)).isNull();
        assertThat(validTo(vp1, campusA)).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    void rejectsNonVicePrincipalInactiveCampusAndClosedYear() {
        assertCode(
                () -> service.assign(principal, activeYear, teacher, new VicePrincipalAssignmentRequest(campusA)),
                ApiCode.VICE_PRINCIPAL_INVALID);
        jdbc.update("UPDATE campuses SET status = 'INACTIVE' WHERE id = ?", campusC);
        assertCode(
                () -> service.assign(principal, activeYear, vp2, new VicePrincipalAssignmentRequest(campusC)),
                ApiCode.CAMPUS_INACTIVE);
        // Năm đã kết thúc chỉ đọc
        long closedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        assertCode(
                () -> service.assign(principal, closedYear, vp2, new VicePrincipalAssignmentRequest(campusA)),
                ApiCode.SCHOOL_YEAR_CLOSED);
    }

    @Test
    void campusHoldsAtMostOneVicePrincipal() {
        // AUTH-26: A đang có vp1 ⇒ không xếp thêm vp2, dữ liệu giữ nguyên
        assertCode(
                () -> service.assign(principal, activeYear, vp2, new VicePrincipalAssignmentRequest(campusA)),
                ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN);
        assertThat(count(vp2)).isEqualTo(1);

        // B chỉ có phân công cũ đã kết thúc của chính vp2 ⇒ được
        assertThat(campusByUser(service.assign(principal, activeYear, vp2, new VicePrincipalAssignmentRequest(campusB)))
                        .get(vp2))
                .isEqualTo(campusB);

        // vp1 chuyển đi thì A trống từ hôm nay ⇒ vp2 chuyển sang A được
        service.assign(principal, activeYear, vp1, new VicePrincipalAssignmentRequest(campusC));
        assertThat(campusByUser(service.assign(principal, activeYear, vp2, new VicePrincipalAssignmentRequest(campusA)))
                        .get(vp2))
                .isEqualTo(campusA);
    }

    @Test
    void swapExchangesTwoVicePrincipalsAndKeepsOnePerCampus() {
        service.assign(principal, activeYear, vp2, new VicePrincipalAssignmentRequest(campusB));

        // AUTH-26: không gửi swap thì vẫn chặn như cũ
        assertCode(
                () -> service.assign(principal, activeYear, vp1, new VicePrincipalAssignmentRequest(campusB, false)),
                ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN);

        Map<Long, Long> campuses = campusByUser(
                service.assign(principal, activeYear, vp1, new VicePrincipalAssignmentRequest(campusB, true)));

        assertThat(campuses.get(vp1)).isEqualTo(campusB);
        assertThat(campuses.get(vp2)).isEqualTo(campusA);
        // Phân công cũ của vp1 giữ lịch sử, kết thúc hôm qua; mỗi điểm trường còn đúng một PHT từ hôm nay
        assertThat(jdbc.queryForObject(
                        "SELECT valid_to FROM staff_assignments WHERE user_id = ? AND campus_id = ? AND valid_from = '2026-01-01'",
                        LocalDate.class,
                        vp1,
                        campusA))
                .isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(openVicePrincipals(campusA)).containsExactly(vp2);
        assertThat(openVicePrincipals(campusB)).containsExactly(vp1);
    }

    @Test
    void swapInPlannedYearKeepsTheActiveYearAssignments() {
        service.assign(principal, activeYear, vp2, new VicePrincipalAssignmentRequest(campusB));
        service.assign(principal, plannedYear, vp1, new VicePrincipalAssignmentRequest(campusC));
        service.assign(principal, plannedYear, vp2, new VicePrincipalAssignmentRequest(campusA));

        Map<Long, Long> planned = campusByUser(
                service.assign(principal, plannedYear, vp1, new VicePrincipalAssignmentRequest(campusA, true)));

        assertThat(planned.get(vp1)).isEqualTo(campusA);
        assertThat(planned.get(vp2)).isEqualTo(campusC);
        Map<Long, Long> active = campusByUser(service.getAssignments(principal, activeYear));
        assertThat(active.get(vp1)).isEqualTo(campusA);
        assertThat(active.get(vp2)).isEqualTo(campusB);
    }

    @Test
    void swapNeedsACampusToGiveBack() {
        // vp2 chưa phụ trách điểm trường nào trong năm ⇒ không có gì để đổi, vẫn chặn
        assertCode(
                () -> service.assign(principal, activeYear, vp2, new VicePrincipalAssignmentRequest(campusA, true)),
                ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN);
        assertThat(count(vp2)).isEqualTo(1);
        assertThat(campusByUser(service.getAssignments(principal, activeYear)).get(vp1))
                .isEqualTo(campusA);
    }

    @Test
    void vicePrincipalCannotAssignVicePrincipalsEvenInOwnCampus() {
        CurrentUser vice = new CurrentUser(vp1, "kc-vp1", "vp1", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));

        // AUTH-16: PHT chỉ phân công GV/bếp trong campus mình; phân công PHT do HT
        assertCode(() -> service.getAssignments(vice, activeYear), ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED);
        assertCode(
                () -> service.assign(vice, activeYear, vp2, new VicePrincipalAssignmentRequest(campusA)),
                ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED);
        assertThat(count(vp2)).isEqualTo(1);
    }

    @Test
    @DisplayName("Phân công PHT giữ qua các năm học: không tự hết hạn, chỉ đổi khi HT phân công lại")
    void vicePrincipalAssignmentCarriesAcrossSchoolYears() {
        service.assign(principal, activeYear, vp2, new VicePrincipalAssignmentRequest(campusB));
        assertThat(validTo(vp2, campusB)).isNull();

        // Sang năm học sau: phân công để trống ngày kết thúc vẫn còn phạm vi điểm trường
        LocalDate nextYear = LocalDate.of(2027, 8, 15);
        assertThat(staffAssignmentService.hasActiveCampusAssignment(vp1, StaffRole.VICE_PRINCIPAL, campusA, nextYear))
                .isTrue();
        assertThat(staffAssignmentService.findActiveCampusIds(vp2, StaffRole.VICE_PRINCIPAL, nextYear))
                .containsExactly(campusB);
        // Bước Phó hiệu trưởng của năm sau hiện sẵn phân công cũ, HT chỉ cần xem lại
        Map<Long, Long> planned = campusByUser(service.getAssignments(principal, plannedYear));
        assertThat(planned.get(vp1)).isEqualTo(campusA);
        assertThat(planned.get(vp2)).isEqualTo(campusB);

        // Điểm trường A vẫn do vp1 giữ ở năm sau ⇒ không xếp thêm vp2 vào A
        assertCode(
                () -> service.assign(principal, plannedYear, vp2, new VicePrincipalAssignmentRequest(campusA)),
                ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN);

        // CFG-YR-05: đổi điểm trường cho năm sau có hiệu lực từ ngày mở chuẩn bị (mặc định 1 tháng trước 01/08/2027)
        service.assign(principal, plannedYear, vp2, new VicePrincipalAssignmentRequest(campusC));
        LocalDate prepDay = LocalDate.of(2027, 7, 1);
        assertThat(staffAssignmentService.hasActiveCampusAssignment(vp2, StaffRole.VICE_PRINCIPAL, campusC, prepDay))
                .isTrue();
        assertThat(staffAssignmentService.hasActiveCampusAssignment(
                        vp2, StaffRole.VICE_PRINCIPAL, campusC, prepDay.minusDays(1)))
                .isFalse();
        assertThat(staffAssignmentService.hasActiveCampusAssignment(
                        vp2, StaffRole.VICE_PRINCIPAL, campusB, prepDay.minusDays(1)))
                .isTrue();
    }

    private Map<Long, Long> campusByUser(VicePrincipalAssignmentResponse response) {
        Map<Long, Long> out = new java.util.HashMap<>();
        response.rows()
                .forEach(r -> out.put(
                        r.user().id(),
                        r.assignment() == null ? null : r.assignment().campusId()));
        return out;
    }

    private List<Long> openVicePrincipals(long campusId) {
        return jdbc.queryForList(
                "SELECT user_id FROM staff_assignments WHERE staff_role = 'VICE_PRINCIPAL' AND campus_id = ?"
                        + " AND valid_from <= '2026-10-22' AND (valid_to IS NULL OR valid_to >= '2026-10-22')",
                Long.class,
                campusId);
    }

    private LocalDate validFrom(long userId, long campusId) {
        List<LocalDate> rows = jdbc.queryForList(
                "SELECT valid_from FROM staff_assignments WHERE user_id = ? AND campus_id = ? ORDER BY id",
                LocalDate.class,
                userId,
                campusId);
        return rows.get(rows.size() - 1);
    }

    private LocalDate validTo(long userId, long campusId) {
        List<LocalDate> rows = jdbc.queryForList(
                "SELECT valid_to FROM staff_assignments WHERE user_id = ? AND campus_id = ? ORDER BY id",
                LocalDate.class,
                userId,
                campusId);
        return rows.get(rows.size() - 1);
    }

    private int count(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM staff_assignments WHERE user_id = ?", Integer.class, userId);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(code);
    }

    private long user(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + " IT') RETURNING id");
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
