package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Role;
import com.carenest.service.eduplan.impl.EduWorkflowPolicy.RolePolicy;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduWorkflowCandidatesIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private EduWorkflowService workflowService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("EDU-54: resolveActiveCandidates chỉ nhận tài khoản ACTIVE và đúng scope phân công")
    void resolvesOnlyActiveAndInScopeCandidates() {
        LocalDate today = LocalDate.of(2026, 11, 6);
        long activeHt = createAccount("candidate-ht-active", "ACTIVE");
        long inactiveHt = createAccount("candidate-ht-inactive", "INACTIVE");
        long activeTl1 = createAccount("candidate-tl-1", "ACTIVE");
        long activeTl2 = createAccount("candidate-tl-2", "ACTIVE");

        long campus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name) VALUES ('C-CAND','Campus thử') RETURNING id", Long.class);
        long ageGroup1 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-3'", Long.class);
        long ageGroup2 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-4'", Long.class);

        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL',?)",
                activeHt,
                today.minusDays(1));
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL',?)",
                inactiveHt,
                today.minusDays(1));
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,?)",
                activeTl1,
                ageGroup1,
                today);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,?)",
                activeTl2,
                ageGroup2,
                today);

        RolePolicy policy = new RolePolicy(List.of(Role.TEACHER), List.of(Role.TEAM_LEADER, Role.PRINCIPAL));

        Map<Role, Set<Long>> candidatesForAgeGroup1 =
                workflowService.resolveActiveCandidates(policy, ageGroup1, null, null, today);

        assertThat(candidatesForAgeGroup1.get(Role.PRINCIPAL))
                .contains(activeHt)
                .doesNotContain(inactiveHt);

        assertThat(candidatesForAgeGroup1.get(Role.TEAM_LEADER))
                .contains(activeTl1)
                .doesNotContain(activeTl2);

        long otherCampus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name) VALUES ('C-CAND-B','Campus B') RETURNING id", Long.class);
        long year = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('candidate-year','2026-01-01','2026-12-31') RETURNING id",
                Long.class);
        long classroom = jdbc.queryForObject(
                "INSERT INTO classrooms(name,campus_id,school_year_id,age_group_id) VALUES ('Candidate class',?,?,?) RETURNING id",
                Long.class,
                otherCampus,
                year,
                ageGroup1);
        long vpA = createAccount("candidate-vp-a", "ACTIVE");
        long vpB = createAccount("candidate-vp-b", "ACTIVE");
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,campus_id,valid_from) VALUES (?,'VICE_PRINCIPAL',?,?)",
                vpA,
                campus,
                today);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,campus_id,valid_from) VALUES (?,'VICE_PRINCIPAL',?,?)",
                vpB,
                otherCampus,
                today);
        var classPolicy = new RolePolicy(List.of(Role.TEACHER), List.of(Role.TEAM_LEADER, Role.VICE_PRINCIPAL));
        var candidatesForClass = workflowService.resolveActiveCandidates(classPolicy, null, null, classroom, today);
        assertThat(candidatesForClass.get(Role.VICE_PRINCIPAL)).contains(vpB).doesNotContain(vpA);
        assertThat(candidatesForClass.get(Role.TEAM_LEADER)).contains(activeTl1).doesNotContain(activeTl2);
    }

    private long createAccount(String username, String status) {
        return jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES (?,?,?,?) RETURNING id",
                Long.class,
                username,
                username,
                "Người thử",
                status);
    }
}
