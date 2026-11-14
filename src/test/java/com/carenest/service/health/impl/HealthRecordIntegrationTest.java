package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.carenest.dto.child.AllergiesInput;
import com.carenest.dto.child.ChildMedicalHistory;
import com.carenest.dto.child.DeclarationAnswerInput;
import com.carenest.dto.child.HealthDeclarationInput;
import com.carenest.dto.child.HealthDeclarationRequest;
import com.carenest.dto.child.HealthDeclarationResponse;
import com.carenest.dto.child.MedicalHistoryInput;
import com.carenest.dto.child.TbContactInput;
import com.carenest.dto.health.HealthCheckRequest;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.dto.health.HealthTrendAnalysisResponse;
import com.carenest.dto.health.PublishedHealthResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClient;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.service.health.HealthCheckService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.service.health.HealthTrendService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DeclarationState;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthDeclarationAction;
import com.carenest.utils.enums.Permission;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Hồ sơ sức khỏe trên PostgreSQL thật (V88): sửa khai báo + thông báo lao (CHD-06, CHD-07), phụ huynh xem kết quả đã công
 * bố + thông báo (PAR-03, HLT-11), giải thích xu hướng AI nháp → duyệt (HLT-05). AI giả lập. Hôm nay = 10/11/2026.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class HealthRecordIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @MockitoBean
    private AiClient aiClient;

    @Autowired
    private ChildRecordService recordService;

    @Autowired
    private ChildIntakeService intakeService;

    @Autowired
    private HealthMeasurementService measurementService;

    @Autowired
    private HealthTrendService trendService;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private HealthCheckCampaignRepository campaignRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser teacher;
    private CurrentUser parent;
    private CurrentUser otherParent;
    private long principalId;
    private long vicePrincipalId;
    private long teacherId;
    private long campus;
    private long classA;
    private long child;

    @BeforeEach
    void setUp() {
        principalId = staff("ht.health", "PRINCIPAL");
        vicePrincipalId = staff("pht.health", "VICE_PRINCIPAL");
        teacherId = staff("gv.health", "TEACHER");
        long parentId = staff("0903000001", "PARENT");
        long otherParentId = staff("0903000002", "PARENT");
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('HR-" + SEQ.incrementAndGet()
                + "', 'Cơ sở HR', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long room = insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campus
                + ", 'HR" + SEQ.incrementAndGet() + "', 'Phòng HR', 'CLASS', 30) RETURNING id");
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity) "
                + "VALUES (" + campus + ", " + year + ", 'Lớp HR', " + ag3 + ", " + room + ", 25) RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vicePrincipalId,
                campus);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacherId,
                classA);
        child = insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) "
                + "VALUES ('HR" + SEQ.incrementAndGet() + "', 'Bé Sức Khỏe', '2022-03-01', 'FEMALE', 'ACTIVE', "
                + "'2026-09-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                child,
                classA);
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Mẹ (giả)', 'MOTHER', '0900000009', '000000000009', now())",
                parentId,
                child);
        jdbc.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state,"
                        + " self_history_state, family_history_state, tb_contact_state, declared_by, declared_at)"
                        + " VALUES (?, 'NONE_REPORTED', 'NONE_REPORTED', 'NOT_PROVIDED', 'NONE_REPORTED',"
                        + " 'NONE_REPORTED', 'NONE_REPORTED', ?, now())",
                child,
                principalId);

        principal = user(principalId, CareNestRole.PRINCIPAL);
        teacher = user(teacherId, CareNestRole.TEACHER);
        parent = user(parentId, CareNestRole.PARENT);
        otherParent = user(otherParentId, CareNestRole.PARENT);
    }

    @Test
    @DisplayName(
            "CHD-06, CHD-07: sửa khai báo Có tiếp xúc lao ⇒ dị ứng thay, lịch sử JSONB, báo HT, PHT, GV lớp, người lưu")
    void declarationEditNotifiesTbContact() {
        HealthDeclarationResponse saved = recordService.saveDeclaration(
                principal,
                child,
                new HealthDeclarationRequest(declaration(List.of("Tôm", "Sữa bò"), "2026-11-01"), "Phụ huynh bổ sung"));
        entityManager.flush();

        assertThat(saved.allergies().items()).containsExactly("Tôm", "Sữa bò");
        assertThat(saved.medicalHistory().tbContact().contactDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(saved.history()).singleElement().satisfies(h -> {
            assertThat(h.action()).isEqualTo(HealthDeclarationAction.UPDATED);
            assertThat(h.allergyChange().to().items()).contains("Tôm");
            assertThat(h.historyChange().from().tbContact().state()).isEqualTo(DeclarationState.NONE_REPORTED);
        });
        assertThat(count("SELECT count(*) FROM child_allergies WHERE child_id = ?", child))
                .isEqualTo(2);
        for (long recipient : List.of(principalId, vicePrincipalId, teacherId)) {
            assertThat(count(
                            "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'CHILD_TB_CONTACT'",
                            recipient))
                    .isEqualTo(1);
        }

        // Đổi ngày từ phiếu khám ⇒ báo đổi ngày cho HT, PHT, người lưu (không GV)
        intakeService.updateMedicalHistory(
                child,
                new ChildMedicalHistory(
                        DeclarationState.NONE_REPORTED,
                        null,
                        DeclarationState.NONE_REPORTED,
                        null,
                        DeclarationState.REPORTED,
                        LocalDate.of(2026, 11, 5)),
                HealthDeclarationAction.UPDATED_FROM_EXAM,
                "Cập nhật từ phiếu khám ngày 10/11/2026",
                "phiếu khám ngày 10/11/2026",
                teacherId,
                Instant.parse("2026-11-10T03:00:00Z"));
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM notifications WHERE type = 'CHILD_TB_CONTACT_DATE_CHANGED'"
                        + " AND body LIKE '%phiếu khám ngày 10/11/2026%'"))
                .isEqualTo(3);
    }

    @Test
    @DisplayName(
            "HLT-11, HLT-13, PAR-03: GV lưu, sửa số đo ⇒ phụ huynh nhận thông báo, xem kết quả + biểu đồ; phụ huynh khác 403")
    void parentSeesPublishedResults() {
        measure(round("Cân đo tháng 9 (giả)", "2026-09-10", "2026-09-20"), "2026-09-15", "98.0", "15.0");
        long round = round("Cân đo tháng 11 (giả)", "2026-11-01", "2026-11-20");
        long measurementId = measurementService
                .createMeasurement(
                        teacher,
                        child,
                        new HealthMeasurementRequest(round, new BigDecimal("99.5"), new BigDecimal("15.4"), "", null))
                .id();
        measurementService.updateMeasurement(
                teacher,
                child,
                measurementId,
                new HealthMeasurementRequest(null, new BigDecimal("99.6"), new BigDecimal("15.4"), "", "Đo lại (giả)"));
        entityManager.flush();

        for (String type : List.of("HEALTH_RESULT_PUBLISHED", "HEALTH_RESULT_UPDATED")) {
            assertThat(count(
                            "SELECT count(*) FROM notifications n JOIN child_guardians l ON l.user_id = n.user_id"
                                    + " WHERE l.child_id = ? AND n.type = ? AND n.body NOT LIKE '%cm%'",
                            child, type))
                    .isEqualTo(1);
        }
        PublishedHealthResponse view = measurementService.getPublishedHealth(parent, child);
        assertThat(view.results()).hasSize(2);
        assertThat(view.growth())
                .hasSize(2)
                .allSatisfy(p -> assertThat(p.weightForAgeZ()).isNotNull());
        assertThat(view.interpretation()).isNull();
        assertThatThrownBy(() -> measurementService.getPublishedHealth(otherParent, child))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_ACCESS_DENIED));
    }

    @Test
    @DisplayName("HLT-05: AI viết nháp (lưu V88) ⇒ phụ huynh chưa thấy; GV duyệt ⇒ phụ huynh thấy bản duyệt")
    void interpretationVisibleToParentOnlyAfterApproval() {
        measure(round("Cân đo tháng 9 (giả)", "2026-09-10", "2026-09-20"), "2026-09-15", "98.0", "15.0");
        measure(round("Cân đo tháng 11 (giả)", "2026-11-01", "2026-11-20"), "2026-11-09", "99.5", "15.6");
        given(aiClient.isEnabled()).willReturn(true);
        given(aiClient.complete(any()))
                .willReturn(new AiClient.AiResult(
                        "{\"overview\": \"Chiều cao, cân nặng tăng đều.\", \"attentionPoints\": [],"
                                + " \"positives\": [], \"followUps\": [], \"limitations\": null}",
                        "model-x"));

        HealthTrendAnalysisResponse draft = trendService.analyze(teacher, child);
        entityManager.flush();
        assertThat(draft.draft()).isTrue();
        assertThat(count("SELECT count(*) FROM health_interpretations WHERE child_id = ? AND status = 'DRAFT'", child))
                .isEqualTo(1);
        assertThat(measurementService.getPublishedHealth(parent, child).interpretation())
                .isNull();

        trendService.approve(teacher, child, draft.id(), null);
        entityManager.flush();
        PublishedHealthResponse view = measurementService.getPublishedHealth(parent, child);
        assertThat(view.interpretation()).isNotNull();
        assertThat(view.interpretation().summary()).isEqualTo("Tổng quan: Chiều cao, cân nặng tăng đều.");
        assertThat(view.interpretation().warnings()).isNotEmpty();
    }

    @Test
    @DisplayName("HLT-08: HT mở đợt khám ⇒ GV các lớp của điểm trường và PHT điểm trường nhận HEALTH_CHECK_OPENED")
    void openingCampaignNotifiesTeachersAndVicePrincipal() {
        healthCheckService.createHealthCheck(
                principal,
                new HealthCheckRequest(
                        HealthCheckKind.EXAM,
                        "Khám sức khỏe đầu năm (giả)",
                        campus,
                        LocalDate.of(2026, 11, 12),
                        LocalDate.of(2026, 11, 20),
                        "Trạm y tế (giả)",
                        ""));
        entityManager.flush();

        assertThat(jdbc.queryForList(
                        "SELECT user_id FROM notifications WHERE type = 'HEALTH_CHECK_OPENED'", Long.class))
                .containsExactlyInAnyOrder(teacherId, vicePrincipalId);
    }

    // ---------- dữ liệu ----------

    private static HealthDeclarationInput declaration(List<String> allergies, String tbDate) {
        DeclarationAnswerInput none = new DeclarationAnswerInput("NONE_REPORTED", null);
        return new HealthDeclarationInput(
                new AllergiesInput("REPORTED", allergies),
                none,
                new DeclarationAnswerInput("NOT_PROVIDED", null),
                new MedicalHistoryInput(none, none, new TbContactInput("REPORTED", tbDate)));
    }

    /** Kỳ cân đo ở điểm trường (mỗi kỳ một lần đo/trẻ); kỳ đã qua thì đóng — mỗi điểm trường chỉ một kỳ mở. */
    private long round(String name, String start, String end) {
        HealthCheckCampaign campaign = HealthCheckCampaign.builder()
                .kind(HealthCheckKind.MEASUREMENT)
                .campusId(campus)
                .name(name)
                .startDate(LocalDate.parse(start))
                .endDate(LocalDate.parse(end))
                .examiner("Trường (giả)")
                .note("")
                .createdBy(principalId)
                .createdAt(Instant.parse(start + "T01:00:00Z"))
                .build();
        if (LocalDate.parse(end).isBefore(LocalDate.of(2026, 11, 10))) {
            campaign.close(principalId, Instant.parse(end + "T10:00:00Z"));
        }
        return campaignRepository.saveAndFlush(campaign).getId();
    }

    private void measure(long campaign, String date, String height, String weight) {
        jdbc.update(
                "INSERT INTO health_measurements (child_id, campaign_id, classroom_id, date, height_cm, weight_kg, note,"
                        + " measured_by, published_at, published_by, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, '', ?, now(), ?, now(), now())",
                child,
                campaign,
                classA,
                LocalDate.parse(date),
                new BigDecimal(height),
                new BigDecimal(weight),
                teacherId,
                teacherId);
    }

    private long staff(String username, String role) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role) VALUES ('kc-"
                + username + "', '" + username + "', '" + username + "', '" + role + "') RETURNING id");
    }

    private static CurrentUser user(long id, CareNestRole role) {
        Set<Permission> permissions = TestPermissions.defaultsOf(role);
        return new CurrentUser(id, "kc-" + id, "user" + id, "User " + id, null, Set.of(role), permissions);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
