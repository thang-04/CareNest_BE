package com.carenest.repository.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.entity.account.UserAccount;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserAccountSpecifications;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Query phân công/danh sách nhân sự (V2 + V8) trên PostgreSQL thật. Cần Docker. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class StaffDirectoryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StaffAssignmentRepository assignments;

    @Autowired
    private ClassroomRepository classrooms;

    @Autowired
    private UserAccountRepository accounts;

    private Long campusA;
    private Long campusB;
    private Long classA;
    private Long oldClassA;
    private Long teacher;
    private Long kitchen;
    private Long vice;

    @BeforeEach
    void seed() {
        campusA = insert("INSERT INTO campuses (code, name) VALUES ('T-A', 'A') RETURNING id");
        campusB = insert("INSERT INTO campuses (code, name) VALUES ('T-B', 'B') RETURNING id");
        Long thisYear = insert(
                "INSERT INTO school_years (name, start_date, end_date) VALUES ('T-26', '2026-09-01', '2027-05-31') RETURNING id");
        Long lastYear = insert(
                "INSERT INTO school_years (name, start_date, end_date) VALUES ('T-25', '2025-09-01', '2026-05-31') RETURNING id");
        classA = insert(
                "INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'A1') RETURNING id",
                campusA,
                thisYear);
        oldClassA = insert(
                "INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'A1') RETURNING id",
                campusA,
                lastYear);
        teacher = accounts.save(UserAccount.create("kc-t", "teacher.lan", null, "Nguyễn Thị Lan", CareNestRole.TEACHER))
                .getId();
        kitchen = accounts.save(UserAccount.create("kc-k", "kitchen.b", null, "Bếp B", CareNestRole.KITCHEN_STAFF))
                .getId();
        vice = accounts.save(UserAccount.create("kc-v", "vice.a", null, "PHT A", CareNestRole.VICE_PRINCIPAL))
                .getId();
        assignments.save(StaffAssignment.create(teacher, StaffRole.TEACHER, null, classA, TODAY.minusDays(30), null));
        assignments.save(
                StaffAssignment.create(kitchen, StaffRole.KITCHEN_STAFF, campusB, null, TODAY.minusDays(30), null));
        assignments.save(
                StaffAssignment.create(vice, StaffRole.VICE_PRINCIPAL, campusA, null, TODAY.minusDays(30), null));
    }

    @AfterEach
    void cleanUp() {
        assignments.deleteAll();
        accounts.deleteAll();
        jdbc.update("DELETE FROM classrooms");
        jdbc.update("DELETE FROM school_years");
        jdbc.update("DELETE FROM campuses");
    }

    @Test
    @DisplayName("AUTH-08: nhân sự đang phân công theo campus (GV qua lớp, PHT/bếp qua campus)")
    void staffOfCampus() {
        assertThat(assignments.findActiveTeacherUserIds(Set.of(campusA), TODAY)).containsExactly(teacher);
        assertThat(assignments.findActiveCampusStaffUserIds(Set.of(campusA), TODAY))
                .containsExactly(vice);
        assertThat(assignments.findActiveCampusStaffUserIds(Set.of(campusB), TODAY))
                .containsExactly(kitchen);
        assertThat(assignments.findActiveTeacherCampusIds(teacher, TODAY)).containsExactly(campusA);
        assertThat(assignments.findActiveCampusIds(vice, StaffRole.VICE_PRINCIPAL, TODAY))
                .containsExactly(campusA);
    }

    @Test
    void overlapDetectionTreatsNullScopeColumnsAsEqual() {
        assertThat(assignments.existsOverlapping(teacher, StaffRole.TEACHER, null, classA, TODAY))
                .isTrue();
        assertThat(assignments.existsOverlapping(teacher, StaffRole.TEACHER, null, oldClassA, TODAY))
                .isFalse();
        assertThat(assignments.existsOverlapping(kitchen, StaffRole.KITCHEN_STAFF, campusB, null, TODAY))
                .isTrue();
    }

    @Test
    @DisplayName("AUTH-23: GV chưa có phân công tổ trưởng thì không là tổ trưởng; ma trận nhận cột TEAM_LEADER (V10)")
    void teamLeaderLookupAndMatrixColumn() {
        assertThat(assignments.existsActiveTeamLeader(teacher, TODAY)).isFalse();

        jdbc.update("INSERT INTO role_permissions (role, permission_code) VALUES ('TEAM_LEADER', 'TEST_CODE')");
        try {
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM role_permissions WHERE role = 'TEAM_LEADER'", Long.class))
                    .isEqualTo(1L);
        } finally {
            jdbc.update("DELETE FROM role_permissions WHERE role = 'TEAM_LEADER'");
        }
    }

    @Test
    void currentClassroomsOnlyFromRunningSchoolYear() {
        assertThat(classrooms.findCurrentByCampusId(campusA, TODAY))
                .extracting("id")
                .containsExactly(classA);
    }

    @Test
    void accountSearchByKeywordRoleAndIds() {
        Specification<UserAccount> spec = Specification.allOf(
                UserAccountSpecifications.matches("LAN"),
                UserAccountSpecifications.hasRole(CareNestRole.TEACHER),
                UserAccountSpecifications.idIn(List.of(teacher, kitchen)));

        assertThat(accounts.findAll(spec, PageRequest.of(0, 10)))
                .extracting("id")
                .containsExactly(teacher);
        assertThat(accounts.findAll(UserAccountSpecifications.idIn(List.of()), PageRequest.of(0, 10)))
                .isEmpty();
    }

    private Long insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
