package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.AppearanceRequest;
import com.carenest.dto.organization.AppearanceResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.SchoolAppearanceService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AppearanceMode;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Tranh nền giao diện chung của trường trên PostgreSQL thật (V204). Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class SchoolAppearanceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private SchoolAppearanceService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;

    @BeforeEach
    void setUp() {
        long userId = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                        + "VALUES ('kc-ap', 'principal.ap', 'Principal AP') RETURNING id",
                Long.class);
        principal = new CurrentUser(userId, "kc-ap", "principal.ap", "P", null, Set.of(CareNestRole.PRINCIPAL));
    }

    @Test
    void neverSavedReturnsDefaultWithEveryScene() {
        AppearanceResponse response = service.getAppearance(true);

        assertThat(response.mode()).isEqualTo(AppearanceMode.AUTO);
        assertThat(response.enabledSceneIds()).hasSize(20);
        assertThat(response.updatedAt()).isNull();
        assertThat(response.updatedBy()).isNull();
    }

    @Test
    void saveThenReadReturnsSavedConfigWithUpdaterName() {
        service.saveAppearance(
                principal, new AppearanceRequest(AppearanceMode.FIXED, "T3", List.of("T3", "T4", "T3", "ZZ")));
        AppearanceResponse response = service.getAppearance(true);
        assertThat(service.getAppearance(false).updatedBy()).isNull();

        assertThat(response.mode()).isEqualTo(AppearanceMode.FIXED);
        assertThat(response.pinnedSceneId()).isEqualTo("T3");
        // Bỏ mã trùng và mã không có trong bộ tranh
        assertThat(response.enabledSceneIds()).containsExactly("T3", "T4");
        assertThat(response.updatedBy()).isEqualTo("Principal AP");
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void saveAgainReplacesTheSingleRow() {
        service.saveAppearance(principal, new AppearanceRequest(AppearanceMode.AUTO, null, List.of("T0", "T1")));
        service.saveAppearance(principal, new AppearanceRequest(AppearanceMode.DAILY_RANDOM, null, List.of("T5")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM school_appearance", Integer.class))
                .isEqualTo(1);
        assertThat(service.getAppearance(true).enabledSceneIds()).containsExactly("T5");
    }

    @Test
    void noValidSceneIsRejected() {
        assertThatThrownBy(() -> service.saveAppearance(
                        principal, new AppearanceRequest(AppearanceMode.AUTO, null, List.of("ZZ"))))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.INVALID_REQUEST_DATA);
    }

    @Test
    void fixedModeNeedsPinnedSceneThatIsEnabled() {
        assertThatThrownBy(() -> service.saveAppearance(
                        principal, new AppearanceRequest(AppearanceMode.FIXED, "T9", List.of("T0", "T1"))))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.INVALID_REQUEST_DATA);
        assertThatThrownBy(() -> service.saveAppearance(
                        principal, new AppearanceRequest(AppearanceMode.FIXED, null, List.of("T0"))))
                .isInstanceOf(GlobalException.class);
    }
}
