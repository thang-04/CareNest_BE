package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
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
class EducationReviewerAssignmentsIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    StaffAssignmentService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("EDU-54: batch trả cả HT khi có PHT và TL, bỏ hết hạn/tương lai/vai trò khác")
    void includesAllReviewerAssignmentsWithoutFallback() {
        LocalDate today = LocalDate.of(2026, 11, 6);
        long principal = account("review-ht");
        long vice = account("review-vp");
        long leader = account("review-tl");
        long expired = account("review-old");
        long future = account("review-future");
        long kitchen = account("review-kitchen");
        long campus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name) VALUES ('C-REVIEW','Campus thử') RETURNING id", Long.class);
        long age = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-4'", Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL',?)",
                principal,
                today.minusDays(1));
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,campus_id,valid_from) VALUES (?,'VICE_PRINCIPAL',?,?)",
                vice,
                campus,
                today);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,?)",
                leader,
                age,
                today);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from,valid_to) VALUES (?,'PRINCIPAL',?,?)",
                expired,
                today.minusDays(2),
                today.minusDays(1));
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL',?)",
                future,
                today.plusDays(1));
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,campus_id,valid_from) VALUES (?,'KITCHEN_STAFF',?,?)",
                kitchen,
                campus,
                today);
        var result = service.findActiveEducationReviewerAssignments(today);
        assertThat(result.keySet()).contains(principal, vice, leader).doesNotContain(expired, future, kitchen);
        assertThat(result.get(principal)).allMatch(a -> a.staffRole() == StaffRole.PRINCIPAL);
        assertThat(result.get(vice)).allMatch(a -> a.campusId().equals(campus));
        assertThat(result.get(leader)).allMatch(a -> a.ageGroupId().equals(age));
    }

    private long account(String name) {
        return jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES (?,?,?,'ACTIVE') RETURNING id",
                Long.class,
                name,
                name,
                "Người thử");
    }
}
