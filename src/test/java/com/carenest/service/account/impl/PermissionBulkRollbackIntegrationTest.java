package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import com.carenest.dto.account.BulkUpdateRolePermissionsRequest;
import com.carenest.dto.account.BulkUpdateRolePermissionsRequest.RoleItem;
import com.carenest.exception.GlobalException;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.PermissionService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** EDU-110 BR2.3: lưu hàng loạt là một giao dịch thật (test không bọc @Transactional để thấy hoàn tác). */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PermissionBulkRollbackIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private PermissionService permissionService;

    @MockitoSpyBean
    private EduWorkflowService workflowService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Lỗi khi ghi chuỗi/đối chiếu ⇒ ô tick, lịch sử, revision hoàn tác; vi phạm luật ⇒ 7202, DB không đổi")
    void failureRollsBackEverything() {
        long htId = jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status)"
                        + " VALUES ('rb-ht','rb-ht','HT','ACTIVE') RETURNING id",
                Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')",
                htId);
        var principal = TestPermissions.userWithDefaults(htId, CareNestRole.PRINCIPAL);
        // Lúc khởi động test chưa có HT nên runner bỏ qua; chuyển cấu hình để ma trận khớp chuỗi đang chạy
        permissionService.migrateEduRoleMatrix(java.time.Instant.now());
        assertThat(permissionService.getEduWorkflowIssues().issues()).isEmpty();
        long grantsBefore = count("SELECT count(*) FROM permission_grants WHERE role IS NOT NULL");
        long logsBefore = count("SELECT count(*) FROM permission_change_log");
        long revisionBefore = workflowService.getPolicyRevision();

        // Đảo ô HT duyệt chủ đề: nếu đang có thì bỏ (TT/PHT vẫn duyệt), nếu chưa thì thêm ⇒ policy chủ đề đổi
        List<Permission> htCodes =
                new ArrayList<>(permissionService.getRolePermissions(CareNestRole.PRINCIPAL).permissions().stream()
                        .filter(Permission.EDU_WORKFLOW::contains)
                        .toList());
        if (!htCodes.remove(Permission.EDU_THEME_PLAN_APPROVE)) htCodes.add(Permission.EDU_THEME_PLAN_APPROVE);
        var request = new BulkUpdateRolePermissionsRequest(
                "thử hoàn tác", List.of(new RoleItem(CareNestRole.PRINCIPAL, htCodes)));

        doThrow(new IllegalStateException("giả lỗi đối chiếu"))
                .when(workflowService)
                .applyPolicy(any(), anyLong(), any());
        assertThatThrownBy(() -> permissionService.updateRolePermissionsBulk(principal, request))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("SELECT count(*) FROM permission_grants WHERE role IS NOT NULL"))
                .isEqualTo(grantsBefore);
        assertThat(count("SELECT count(*) FROM permission_change_log")).isEqualTo(logsBefore);
        assertThat(workflowService.getPolicyRevision()).isEqualTo(revisionBefore);

        // Bỏ hết người duyệt ⇒ NO_REVIEWER, không ghi gì
        var violating = new BulkUpdateRolePermissionsRequest(
                "thử vi phạm",
                List.of(
                        new RoleItem(CareNestRole.PRINCIPAL, List.of()),
                        new RoleItem(CareNestRole.VICE_PRINCIPAL, List.of()),
                        new RoleItem(CareNestRole.TEAM_LEADER, List.of())));
        assertThatThrownBy(() -> permissionService.updateRolePermissionsBulk(principal, violating))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.SIGNERS_INVALID);
        assertThat(count("SELECT count(*) FROM permission_grants WHERE role IS NOT NULL"))
                .isEqualTo(grantsBefore);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
