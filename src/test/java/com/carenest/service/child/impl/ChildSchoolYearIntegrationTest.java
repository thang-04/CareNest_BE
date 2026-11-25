package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.child.ChildSchoolYearResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.ChildSchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * Năm học của trẻ cho bộ chọn năm App phụ huynh: chỉ phụ huynh liên kết (AUTH-04), năm trẻ có học lớp, năm PLANNED từ
 * ngày chuẩn bị khi trẻ đã được xếp lớp (CFG-YR-05). Hôm nay = 10/11/2026.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ChildSchoolYearIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private ChildSchoolYearService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser parent;
    private CurrentUser otherParent;
    private long child;
    private long oldYear;
    private long activeYear;
    private long plannedYear;
    private long unrelatedYear;
    private long plannedClass;

    @BeforeEach
    void setUp() {
        long parentId = account("0904000001", "PARENT");
        long otherParentId = account("0904000002", "PARENT");
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CSY-" + SEQ.incrementAndGet()
                + "', 'Cơ sở CSY', TRUE) RETURNING id");
        unrelatedYear = year("2024-2025", "2024-09-01", "2025-05-31", "CLOSED", "2024-08-01");
        oldYear = year("2025-2026", "2025-09-01", "2026-05-31", "CLOSED", "2025-08-01");
        activeYear = year("2026-2027", "2026-09-01", "2027-05-31", "ACTIVE", "2026-08-01");
        plannedYear = year("2027-2028", "2027-09-01", "2028-05-31", "PLANNED", "2027-08-01");
        classroom(campus, unrelatedYear, "Lớp không học");
        long oldClass = classroom(campus, oldYear, "Nhà trẻ 1");
        long activeClass = classroom(campus, activeYear, "Mầm 1");
        plannedClass = classroom(campus, plannedYear, "Chồi 1");
        child = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) "
                + "VALUES ('CSY" + SEQ.incrementAndGet() + "', 'Bé Năm Học', '2023-03-01', 'MALE', 'ACTIVE', "
                + "'2025-09-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) "
                        + "VALUES (?, ?, '2025-09-01', '2026-05-31')",
                child,
                oldClass);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                child,
                activeClass);
        jdbc.update(
                "INSERT INTO class_placements (school_year_id, child_id, from_classroom_id, action, to_classroom_id) "
                        + "VALUES (?, ?, ?, 'PROMOTE', ?)",
                plannedYear,
                child,
                activeClass,
                plannedClass);
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Bố (giả)', 'FATHER', '0900000019', '000000000019', now())",
                parentId,
                child);
        parent = user(parentId);
        otherParent = user(otherParentId);
    }

    @Test
    @DisplayName("AUTH-04: phụ huynh không liên kết với trẻ ⇒ 4108")
    void otherParentIsForbidden() {
        assertThatThrownBy(() -> service.getChildSchoolYears(otherParent, child))
                .isInstanceOf(GlobalException.class)
                .extracting(e -> ((GlobalException) e).getApiCode())
                .isEqualTo(ApiCode.PARENT_CHILD_FORBIDDEN);
    }

    @Test
    @DisplayName("AUTH-03, CFG-YR-05: năm trẻ có học, mới trước; năm PLANNED chưa tới ngày chuẩn bị bị ẩn")
    void listsYearsChildStudied() {
        List<ChildSchoolYearResponse> years = service.getChildSchoolYears(parent, child);

        assertThat(years).extracting(ChildSchoolYearResponse::schoolYearId).containsExactly(activeYear, oldYear);
        assertThat(years.get(0))
                .isEqualTo(new ChildSchoolYearResponse(
                        activeYear,
                        "2026-2027",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2027, 5, 31),
                        SchoolYearStatus.ACTIVE,
                        "Mầm 1"));
        assertThat(years.get(1).className()).isEqualTo("Nhà trẻ 1");
        assertThat(years.get(1).status()).isEqualTo(SchoolYearStatus.CLOSED);
    }

    @Test
    @DisplayName("CFG-YR-05: từ ngày chuẩn bị, năm PLANNED đã xếp lớp hiện với lớp được xếp")
    void plannedYearShownFromPrepDate() {
        jdbc.update("UPDATE school_years SET prep_date = '2026-11-01' WHERE id = ?", plannedYear);

        List<ChildSchoolYearResponse> years = service.getChildSchoolYears(parent, child);

        assertThat(years).extracting(ChildSchoolYearResponse::schoolYearId)
                .containsExactly(plannedYear, activeYear, oldYear);
        assertThat(years.get(0).className()).isEqualTo("Chồi 1");
        assertThat(years.get(0).status()).isEqualTo(SchoolYearStatus.PLANNED);
    }

    @Test
    @DisplayName("CFG-YR-05: trẻ ra trường ở năm PLANNED (không có lớp đích) ⇒ không có năm đó")
    void plannedYearWithoutClassHidden() {
        jdbc.update("UPDATE school_years SET prep_date = '2026-11-01' WHERE id = ?", plannedYear);
        jdbc.update(
                "UPDATE class_placements SET action = 'GRADUATE', to_classroom_id = NULL WHERE child_id = ?", child);

        assertThat(service.getChildSchoolYears(parent, child))
                .extracting(ChildSchoolYearResponse::schoolYearId)
                .doesNotContain(plannedYear);
    }

    @Test
    @DisplayName("AUTH-04: năm trẻ không học hoặc không tồn tại ⇒ 2001 khi đòi theo năm")
    void requireYearRejectsYearNotStudied() {
        assertThat(service.requireChildYear(parent, child, oldYear).className()).isEqualTo("Nhà trẻ 1");
        for (long yearId : List.of(unrelatedYear, -1L)) {
            assertThatThrownBy(() -> service.requireChildYear(parent, child, yearId))
                    .isInstanceOf(GlobalException.class)
                    .extracting(e -> ((GlobalException) e).getApiCode())
                    .isEqualTo(ApiCode.SCHOOL_YEAR_NOT_FOUND);
        }
        assertThatThrownBy(() -> service.requireChildYear(otherParent, child, oldYear))
                .isInstanceOf(GlobalException.class)
                .extracting(e -> ((GlobalException) e).getApiCode())
                .isEqualTo(ApiCode.PARENT_CHILD_FORBIDDEN);
    }

    private long year(String name, String start, String end, String status, String prep) {
        return insertId("INSERT INTO school_years (name, start_date, end_date, status, prep_date) VALUES ('" + name
                + "', '" + start + "', '" + end + "', '" + status + "', '" + prep + "') RETURNING id");
    }

    private long classroom(long campus, long year, String name) {
        long ageGroup = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        int seq = SEQ.incrementAndGet();
        long room = insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campus
                + ", 'CSY" + seq + "', 'Phòng CSY " + seq + "', 'CLASS', 30) RETURNING id");
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity) "
                + "VALUES (" + campus + ", " + year + ", '" + name + "', " + ageGroup + ", " + room
                + ", 25) RETURNING id");
    }

    private long account(String username, String role) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role) VALUES ('kc-"
                + username + "', '" + username + "', '" + username + "', '" + role + "') RETURNING id");
    }

    private static CurrentUser user(long id) {
        Set<Permission> permissions = TestPermissions.defaultsOf(CareNestRole.PARENT);
        return new CurrentUser(id, "kc-" + id, "user" + id, "User " + id, null, Set.of(CareNestRole.PARENT), permissions);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
