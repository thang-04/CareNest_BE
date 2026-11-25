package com.carenest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doCallRealMethod;

import com.carenest.service.account.PermissionService;
import com.carenest.service.eduplan.EduWorkflowService;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduRoleMatrixMigrationRunnerIntegrationTest {

    private static final String REASON = "EDU-110 chuyển cấu hình";
    private static final String EDU_CODES =
            "permission_code LIKE 'EDU\\_%' ESCAPE '\\' AND permission_code <> 'EDU_PLAN_CONFIG'";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private PermissionService permissionService;

    @MockitoSpyBean
    private EduWorkflowService workflowService;

    @Autowired
    private JdbcTemplate jdbc;

    private final Instant now = Instant.now();

    @Test
    @DisplayName("EDU-110 WF2: không có HT hiệu lực ⇒ bỏ qua, không ghi gì")
    void skipsWithoutPrincipal() {
        jdbc.update("DELETE FROM permission_change_log WHERE reason=?", REASON);
        jdbc.update("UPDATE staff_assignments SET valid_to='2000-01-01' WHERE staff_role='PRINCIPAL'");
        assertThat(permissionService.migrateEduRoleMatrix(now)).isEqualTo("SKIPPED_NO_PRINCIPAL");
        assertThat(migrationLogs()).isZero();
    }

    @Test
    @DisplayName("EDU-110 BR4.1/4.2: chuyển một lần, ô tick khớp chuỗi đang chạy, chuỗi không đổi; lần 2 không đổi")
    void migratesOnceAndKeepsPolicy() {
        long principalId = resetAndGivenPrincipal();
        var policiesBefore = workflowService.loadAllPolicies();
        long revisionBefore = workflowService.getPolicyRevision();

        assertThat(permissionService.migrateEduRoleMatrix(now)).startsWith("migrated=");

        assertThat(workflowService.loadAllPolicies()).isEqualTo(policiesBefore);
        assertThat(workflowService.getPolicyRevision()).isEqualTo(revisionBefore);
        assertThat(migrationLogs()).isPositive();
        assertThat(jdbc.queryForList(
                        "SELECT DISTINCT actor_user_id FROM permission_change_log WHERE reason=?", Long.class, REASON))
                .containsExactly(principalId);
        Map<String, Set<String>> ticks = eduTicks();
        for (var entry : Map.of(
                        "theme",
                        "EDU_THEME_PLAN",
                        "branch",
                        "EDU_SEMESTER_PACKAGE",
                        "year",
                        "EDU_TEAM_PLAN",
                        "schoolYear",
                        "EDU_SCHOOL_YEAR_PLAN")
                .entrySet()) {
            var policy = policiesBefore.get(entry.getKey());
            assertThat(ticks.getOrDefault(policy.creators().getFirst().name(), Set.of()))
                    .contains(entry.getValue() + "_EDIT");
            policy.reviewers().forEach(r -> assertThat(ticks.get(r.name())).contains(entry.getValue() + "_APPROVE"));
        }
        assertThat(ticks.getOrDefault("TEACHER", Set.of())).noneMatch(code -> code.endsWith("_APPROVE"));
        assertThat(permissionService.getEduWorkflowIssues().issues()).isEmpty();

        long logs = migrationLogs();
        assertThat(permissionService.migrateEduRoleMatrix(now)).isEqualTo("ALREADY_MIGRATED");
        assertThat(migrationLogs()).isEqualTo(logs);
    }

    @Test
    @DisplayName("EDU-110 NFR1.4: lỗi giữa chừng ⇒ ném ra (giao dịch hoàn tác); runner bắt lỗi, không chặn khởi động")
    void failurePropagatesAndRunnerSwallows() {
        resetAndGivenPrincipal();
        // Lần đọc đầu (tính ô tick) chạy thật, lần sau (ghi chuỗi) lỗi
        doCallRealMethod()
                .doThrow(new IllegalStateException("giả lỗi"))
                .when(workflowService)
                .loadAllPolicies();
        assertThatThrownBy(() -> permissionService.migrateEduRoleMatrix(now)).isInstanceOf(IllegalStateException.class);

        doCallRealMethod()
                .doThrow(new IllegalStateException("giả lỗi"))
                .when(workflowService)
                .loadAllPolicies();
        assertThatCode(() -> new EduRoleMatrixMigrationRunner(permissionService, java.time.Clock.systemUTC()).run(null))
                .doesNotThrowAnyException();
    }

    /** Xóa dấu chuyển và ô EDU (khởi động test có thể đã chuyển), rồi tạo HT hiệu lực duy nhất. */
    private long resetAndGivenPrincipal() {
        jdbc.update("DELETE FROM permission_change_log WHERE reason=?", REASON);
        jdbc.update("DELETE FROM permission_grants WHERE " + EDU_CODES);
        long id = jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES (?,?,?,?) RETURNING id",
                Long.class,
                "mig-ht",
                "mig-ht",
                "HT thử",
                "ACTIVE");
        jdbc.update("UPDATE staff_assignments SET valid_to='2000-01-01' WHERE staff_role='PRINCIPAL'");
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')", id);
        return id;
    }

    private long migrationLogs() {
        return jdbc.queryForObject("SELECT count(*) FROM permission_change_log WHERE reason=?", Long.class, REASON);
    }

    private Map<String, Set<String>> eduTicks() {
        return jdbc.queryForList("SELECT role, permission_code FROM permission_grants WHERE " + EDU_CODES).stream()
                .collect(Collectors.groupingBy(
                        row -> (String) row.get("role"),
                        Collectors.mapping(row -> (String) row.get("permission_code"), Collectors.toSet())));
    }
}
