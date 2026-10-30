package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.EduConfigRequest;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
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

/** Cấu hình kế hoạch giáo dục (EDU-11) qua service + policy thật trên PostgreSQL. Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduConfigServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private EduConfigService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser vicePrincipal;
    private CurrentUser principal;
    private CurrentUser teacher;

    @BeforeEach
    void setUp() {
        vicePrincipal = user("vp", CareNestRole.VICE_PRINCIPAL);
        principal = user("principal", CareNestRole.PRINCIPAL);
        teacher = user("teacher", CareNestRole.TEACHER);
    }

    @Test
    @DisplayName("EDU-21: cấu hình trả họ tên Hiệu trưởng, Phó HT đang hoạt động để in dưới chỗ ký tay")
    void signerNamesOfActivePrincipalAndVicePrincipals() {
        account("ht-ky", "Hiệu trưởng Ký Thử", "PRINCIPAL", "ACTIVE");
        account("pht-ky-a", "Phó HT Ký A", "VICE_PRINCIPAL", "ACTIVE");
        account("pht-ky-b", "Phó HT Ký B", "VICE_PRINCIPAL", "ACTIVE");
        account("pht-nghi", "Phó HT Đã Nghỉ", "VICE_PRINCIPAL", "INACTIVE");
        account("gv-ky", "Giáo viên Không Ký", "TEACHER", "ACTIVE");
        long leader = account("tt-ky", "Tổ trưởng Ký Thử", "TEACHER", "ACTIVE");
        long ag4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, ?)",
                leader,
                ag4,
                java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(1)));

        String currentYear = "9001-9002";
        jdbc.update(
                "INSERT INTO school_years (name, start_date, end_date) VALUES (?, ?, ?)",
                currentYear,
                java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(30)),
                java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(300)));

        EduConfigResponse.SignerNames names = service.getEduConfig().signerNames();

        assertThat(names.principals()).contains("Hiệu trưởng Ký Thử").doesNotContain("Giáo viên Không Ký");
        assertThat(names.vicePrincipals())
                .contains("Phó HT Ký A", "Phó HT Ký B")
                .doesNotContain("Phó HT Đã Nghỉ", "Giáo viên Không Ký");
        // Phân công bắt đầu hôm qua: có trong năm học chứa hôm nay
        assertThat(names.teamLeaders().get(currentYear).get("ag-4")).contains("Tổ trưởng Ký Thử");
        assertThat(names.teamLeaders().values().stream()
                        .flatMap(m -> m.values().stream())
                        .flatMap(List::stream))
                .doesNotContain("Giáo viên Không Ký");
    }

    @Test
    @DisplayName("EDU-11: cấu hình mặc định có 5 lĩnh vực chuẩn, chuỗi TT → PHT, 8 dòng hoạt động tuần")
    void defaultConfig() {
        EduConfigResponse config = service.getEduConfig();

        assertThat(config.domains()).hasSize(5).allMatch(EduConfigResponse.Domain::standard);
        assertThat(config.signers().branch()).containsExactly("TEAM_LEADER", "VICE_PRINCIPAL");
        assertThat(config.weekRows()).hasSize(8);
        assertThat(config.weekRows())
                .filteredOn(r -> r.key().equals("ngoai_troi"))
                .singleElement()
                .satisfies(r -> assertThat(r.extraLabel()).isEqualTo("Chơi tự do (cả tuần)"));
    }

    @Test
    @DisplayName("EDU-11: Phó HT đổi tên lĩnh vực, thêm/bỏ lĩnh vực, đổi người ký và dòng tuần")
    void vicePrincipalUpdatesConfig() {
        String physical = service.getEduConfig().domains().getFirst().name();

        EduConfigResponse saved = service.updateEduConfig(
                vicePrincipal,
                request(
                        Map.of(physical, "Thể chất (đổi tên)", "Lĩnh vực mới", "Tên hiển thị mới"),
                        List.of("Lĩnh vực mới"),
                        List.of("TEAM_LEADER", "VICE_PRINCIPAL", "PRINCIPAL"),
                        List.of(row("hoc", "Hoạt động học", false, false), row("don", "Đón trẻ", false, true))));

        assertThat(saved.domains()).hasSize(6);
        assertThat(saved.domains().getFirst().displayLabel()).isEqualTo("Thể chất (đổi tên)");
        assertThat(saved.domains().getLast())
                .satisfies(d -> assertThat(d.name()).isEqualTo("Lĩnh vực mới"))
                .satisfies(d -> assertThat(d.displayLabel()).isEqualTo("Tên hiển thị mới"))
                .satisfies(d -> assertThat(d.standard()).isFalse());
        assertThat(saved.signers().week()).containsExactly("TEAM_LEADER", "VICE_PRINCIPAL", "PRINCIPAL");
        // Mặc định cả tuần thì dòng đó cũng được gộp
        assertThat(saved.weekRows()).extracting(EduConfigResponse.WeekRow::key).containsExactly("hoc", "don");
        assertThat(saved.weekRows().getLast().canMerge()).isTrue();

        // Bỏ lĩnh vực thêm: ẩn khỏi cấu hình; tên trống thì dùng lại tên gốc
        EduConfigResponse removed = service.updateEduConfig(
                principal,
                request(
                        Map.of(physical, ""),
                        List.of(),
                        List.of("TEAM_LEADER", "VICE_PRINCIPAL"),
                        List.of(row("hoc", "Hoạt động học", false, false))));
        assertThat(removed.domains()).hasSize(5);
        assertThat(removed.domains().getFirst().displayLabel()).isNull();
        assertThat(service.getEduConfig().weekRows()).hasSize(1);
    }

    @Test
    @DisplayName("EDU-11: giáo viên không sửa được; kế hoạch năm phải có PHT ký; không bỏ dòng Hoạt động học")
    void rejectsInvalidConfig() {
        List<EduConfigRequest.WeekRow> rows = List.of(row("hoc", "Hoạt động học", false, false));
        List<String> chain = List.of("TEAM_LEADER", "VICE_PRINCIPAL");

        assertApiError(
                () -> service.updateEduConfig(teacher, request(Map.of(), List.of(), chain, rows)), ApiCode.FORBIDDEN);
        assertApiError(
                () -> service.updateEduConfig(
                        vicePrincipal,
                        new EduConfigRequest(
                                Map.of(),
                                List.of(),
                                new EduConfigRequest.Signers(List.of("TEAM_LEADER"), chain, chain),
                                rows)),
                ApiCode.BAD_REQUEST);
        assertApiError(
                () -> service.updateEduConfig(
                        vicePrincipal, request(Map.of(), List.of(), chain, List.of(row("don", "Đón trẻ", true, true)))),
                ApiCode.BAD_REQUEST);
        String physical = service.getEduConfig().domains().getFirst().name();
        assertApiError(
                () -> service.updateEduConfig(
                        vicePrincipal, request(Map.of(), List.of(physical.toUpperCase()), chain, rows)),
                ApiCode.DOMAIN_NAME_EXISTS);
    }

    private static EduConfigRequest request(
            Map<String, String> labels, List<String> extras, List<String> chain, List<EduConfigRequest.WeekRow> rows) {
        return new EduConfigRequest(labels, extras, new EduConfigRequest.Signers(chain, chain, chain), rows);
    }

    private static EduConfigRequest.WeekRow row(String key, String label, boolean canMerge, boolean allWeek) {
        return new EduConfigRequest.WeekRow(key, label, canMerge, allWeek, null);
    }

    private static void assertApiError(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode().getHttpStatus())
                        .isEqualTo(code.getHttpStatus()));
    }

    private long account(String username, String fullName, String role, String status) {
        return jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role, status) VALUES (?, ?, ?, ?, ?)"
                        + " RETURNING id",
                Long.class,
                "kc-" + username,
                username,
                fullName,
                role,
                status);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "kc-" + username,
                username,
                "Demo " + username);
        return new CurrentUser(id, "kc-" + username, username, "Demo " + username, null, Set.of(role));
    }
}
