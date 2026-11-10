package com.carenest.repository.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.account.PermissionChangeLog;
import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserAccount;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.PermissionState;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Migration V7 trên PostgreSQL thật: seed giữ hành vi cũ, CHECK chặn dữ liệu leo quyền. Cần Docker. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PermissionSchemaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    @Autowired
    private PermissionChangeLogRepository permissionChangeLogRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @AfterEach
    void cleanUp() {
        permissionChangeLogRepository.deleteAll();
        userPermissionOverrideRepository.deleteAll();
        rolePermissionRepository.deleteAll(rolePermissionRepository.findAll().stream()
                .filter(row -> row.getUpdatedBy() != null)
                .toList());
        userAccountRepository.deleteAll();
    }

    @Test
    @DisplayName("AUTH-07: seed V7 khớp quyền mặc định của enum cho mọi role cấu hình được")
    void seedMatchesEnumDefaults() {
        for (CareNestRole role : Permission.CONFIGURABLE_ROLES) {
            Set<String> seeded = rolePermissionRepository.findByRole(role).stream()
                    .map(RolePermission::getPermissionCode)
                    .collect(Collectors.toSet());
            Set<String> expected = Arrays.stream(Permission.values())
                    .filter(permission -> permission.defaultRoles().contains(role))
                    .map(Permission::name)
                    .collect(Collectors.toSet());
            assertThat(seeded).as(role.name()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("AUTH-11: DB chặn ô ma trận cho role cố định")
    void fixedRoleRowIsRejected() {
        assertThatThrownBy(() -> rolePermissionRepository.saveAndFlush(
                        RolePermission.create(CareNestRole.PRINCIPAL, "CHILD_PROFILE_VIEW", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AUTH-12: DB chặn tự tạo ngoại lệ cho chính mình")
    void selfOverrideIsRejected() {
        UserAccount teacher = userAccountRepository.save(UserAccount.create("kc-t", "teacher.t", null, "GV"));

        assertThatThrownBy(() -> userPermissionOverrideRepository.saveAndFlush(UserPermissionOverride.create(
                        teacher.getId(), "CHILD_PROFILE_VIEW", PermissionEffect.GRANT, "tự cấp", teacher.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AUTH-14: lịch sử lọc theo người đích, mới nhất trước")
    void changeLogIsQueryableByTarget() {
        UserAccount principal = userAccountRepository.save(UserAccount.create("kc-p", "principal.p", null, "HT"));
        UserAccount teacher = userAccountRepository.save(UserAccount.create("kc-t", "teacher.t", null, "GV"));
        permissionChangeLogRepository.saveAndFlush(PermissionChangeLog.forUser(
                UUID.randomUUID(),
                principal.getId(),
                teacher.getId(),
                "CHILD_PROFILE_VIEW",
                PermissionState.INHERIT,
                PermissionState.REVOKE,
                "Tạm dừng"));
        permissionChangeLogRepository.saveAndFlush(PermissionChangeLog.forRole(
                UUID.randomUUID(),
                principal.getId(),
                CareNestRole.TEACHER,
                "CHILD_PROFILE_VIEW",
                PermissionState.ON,
                PermissionState.OFF,
                "Giảm quyền"));

        assertThat(permissionChangeLogRepository.findByTargetUserId(teacher.getId(), PageRequest.of(0, 10)))
                .singleElement()
                .extracting(PermissionChangeLog::getAfterState)
                .isEqualTo(PermissionState.REVOKE);
        assertThat(permissionChangeLogRepository.findByTargetRole(CareNestRole.TEACHER, PageRequest.of(0, 10)))
                .hasSize(1);
    }
}
