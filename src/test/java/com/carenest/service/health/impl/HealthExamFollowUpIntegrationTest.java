package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthCheckClassResponse;
import com.carenest.dto.health.HealthCheckCloseResponse;
import com.carenest.dto.health.HealthCheckRequest;
import com.carenest.dto.health.HealthCheckResponse;
import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamRequest;
import com.carenest.dto.health.HealthExamResponse;
import com.carenest.dto.health.HealthFollowUpCloseRequest;
import com.carenest.dto.health.HealthFollowUpContactRequest;
import com.carenest.dto.health.HealthFollowUpExternalResultRequest;
import com.carenest.dto.health.HealthFollowUpListResponse;
import com.carenest.dto.health.HealthFollowUpResponse;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.HealthImageStorage;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthCheckService;
import com.carenest.service.health.HealthExamService;
import com.carenest.service.health.HealthFollowUpService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckResult;
import com.carenest.utils.enums.HealthConclusionCategory;
import com.carenest.utils.enums.HealthConclusionMode;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.HealthFollowUpAction;
import com.carenest.utils.enums.HealthFollowUpEffect;
import com.carenest.utils.enums.HealthFollowUpLevel;
import com.carenest.utils.enums.HealthFollowUpOutcome;
import com.carenest.utils.enums.HealthFollowUpStatus;
import com.carenest.utils.enums.HealthLatestSource;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
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

/** Phiếu khám Phụ lục II, phiếu theo dõi (plan docs/plans/completed/2026-10-31-health-exam-follow-ups.md). Dữ liệu giả. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class HealthExamFollowUpIntegrationTest {

    // Hôm nay 2026-11-10 (giờ trường)
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    // Ảnh PNG 1x1 (giả)
    private static final String PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @MockitoBean
    private HealthImageStorage imageStorage;

    @Autowired
    private HealthCheckService checks;

    @Autowired
    private HealthExamService exams;

    @Autowired
    private HealthFollowUpService followUps;

    @Autowired
    private HealthMeasurementService measurements;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private long campusA;
    private long campusB;
    private long classA;
    private long child1;
    private long child2;
    private long child3;

    @BeforeEach
    void setUp() {
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('HE-A', 'Điểm A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('HE-B', 'Điểm B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        long classB = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campusB + ", " + year + ", 'Lớp B', " + ag3 + ") RETURNING id");
        child1 = child("An", "2023-01-01");
        child2 = child("Bình", "2023-02-01");
        child3 = child("Chi", "2023-03-01");
        enroll(child1, classA);
        enroll(child2, classA);
        enroll(child3, classB);
        jdbc.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id) "
                        + "VALUES (?, 0, 'Mẹ Bình (giả)', 'MOTHER', '0900000001', '000000000001')",
                child2);

        principal = user("he-principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("he-vpa", CareNestRole.VICE_PRINCIPAL);
        vpB = user("he-vpb", CareNestRole.VICE_PRINCIPAL);
        campusRole(vpA, campusA);
        campusRole(vpB, campusB);
        teacherA = user("he-teachera", CareNestRole.TEACHER);
        teacherB = user("he-teacherb", CareNestRole.TEACHER);
        teach(teacherA, classA);
        teach(teacherB, classB);
    }

    @Test
    void teacherDraftsThenConcludesAndEditsWithReason() {
        long campaign = examCampaign(campusA);
        HealthExamResponse fresh = exams.getExam(teacherA, campaign, child1);
        assertThat(fresh.measurement()).isNull();
        assertThat(fresh.draft().date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(fresh.conclusionMode()).isEqualTo(HealthConclusionMode.STANDARD);
        assertThat(fresh.reference().headApplies()).isTrue();
        assertThat(fresh.canEdit()).isTrue();
        assertThat(exams.getExam(vpA, campaign, child1).canEdit()).isFalse();
        expectError(() -> exams.getExam(vpB, campaign, child1), ApiCode.HEALTH_EXAM_NOT_FOUND);
        expectError(
                () -> exams.saveExam(vpA, campaign, child1, save(exam("NO", null, normal()), false, "")),
                ApiCode.HEALTH_EXAM_ACCESS_DENIED);

        // Nháp: chưa công bố, chưa là kết quả
        HealthExamResponse draft =
                exams.saveExam(teacherA, campaign, child1, save(HealthExamCatalog.template(), false, ""));
        assertThat(draft.measurement().examStatus()).isEqualTo(HealthExamStatus.DRAFT);
        assertThat(draft.measurement().published()).isFalse();
        assertThat(draft.draft()).isNull();
        assertThat(rowOf(campaign, child1).result()).isEqualTo(HealthCheckResult.IN_PROGRESS);
        ChildHealthRecordResponse beforeRecord = measurements.getHealthRecord(teacherA, child1);
        assertThat(beforeRecord.measurements()).isEmpty();
        assertThat(beforeRecord.openCampaign().resultStatus()).isEqualTo("DRAFT");

        // Kết luận thiếu sinh tồn: lỗi theo ô
        HealthExamForm missingVitals = withVitals(exam("NO", null, normal()), null);
        assertThatThrownBy(() -> exams.saveExam(teacherA, campaign, child1, save(missingVitals, true, "")))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> {
                    GlobalException g = (GlobalException) ex;
                    assertThat(g.getApiCode()).isEqualTo(ApiCode.HEALTH_EXAM_INCOMPLETE);
                    assertThat(g.getFieldErrors())
                            .extracting(FieldErrorResponse::field)
                            .contains("exam.vitals.temperatureC");
                });

        HealthExamResponse concluded =
                exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, normal()), true, ""));
        assertThat(concluded.measurement().examStatus()).isEqualTo(HealthExamStatus.CONCLUDED);
        assertThat(concluded.measurement().published()).isTrue();
        assertThat(concluded.measurement().followUpLevel()).isEqualTo(HealthFollowUpLevel.NONE);
        assertThat(concluded.measurement().conclusionCategory()).isEqualTo(HealthConclusionCategory.NORMAL);
        assertThat(concluded.measurement().followUpId()).isNull();
        // Tiền sử ghi về khai báo (trẻ chưa có khai báo ⇒ tạo)
        assertThat(jdbc.queryForObject(
                        "SELECT self_history_state FROM child_health_declarations WHERE child_id = ?",
                        String.class,
                        child1))
                .isEqualTo("NONE_REPORTED");
        assertThat(declarationActions(child1)).containsExactly("UPDATED_FROM_EXAM");
        assertThat(rowOf(campaign, child1).result()).isEqualTo(HealthCheckResult.RECORDED);
        assertThat(measurements.getHealthRecord(teacherA, child1).measurements())
                .singleElement()
                .satisfies(m -> assertThat(m.source()).isEqualTo("EXAM"));

        expectError(
                () -> exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, normal()), false, "")),
                ApiCode.HEALTH_EXAM_CONCLUDED);
        HealthExamForm fever = withVitals(
                exam("NO", null, normal()),
                new HealthExamForm.HealthExamVitals(
                        new BigDecimal("38.2"),
                        "FEVER",
                        new BigDecimal("100"),
                        "NORMAL",
                        new BigDecimal("25"),
                        "NORMAL"));
        expectError(
                () -> exams.saveExam(teacherA, campaign, child1, save(fever, true, "")),
                ApiCode.HEALTH_EXAM_INCOMPLETE);
        HealthExamResponse edited =
                exams.saveExam(teacherA, campaign, child1, save(fever, true, "Nhập nhầm nhiệt độ (giả)"));
        assertThat(edited.measurement().edits()).singleElement().satisfies(e -> {
            assertThat(e.reason()).isEqualTo("Nhập nhầm nhiệt độ (giả)");
            assertThat(e.changes()).isNotEmpty();
        });
        assertThat(edited.measurement().reviewPoints().vitals()).isNotEmpty();
        // Không cân đo lẻ bằng API số đo cho phiếu khám
        expectError(
                () -> measurements.updateMeasurement(
                        teacherA,
                        child1,
                        edited.measurement().id(),
                        new HealthMeasurementRequest(
                                null,
                                LocalDate.of(2026, 11, 10),
                                new BigDecimal("99.0"),
                                new BigDecimal("15.2"),
                                null,
                                "",
                                "Sửa")),
                ApiCode.HEALTH_MEASUREMENT_IS_EXAM);

        // Kỳ cân đo không dùng phiếu khám
        long round = checks.createHealthCheck(
                        principal, request(HealthCheckKind.MEASUREMENT, campusB, "2026-11-09", "2026-11-20"))
                .id();
        expectError(() -> exams.getExam(teacherB, round, child3), ApiCode.HEALTH_EXAM_ROUND_REQUIRED);
    }

    @Test
    void referralOpensFollowUpThatTheClassTeacherWorksThrough() {
        long campaign = examCampaign(campusA);
        HealthExamResponse concluded =
                exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, referral()), true, ""));
        assertThat(concluded.measurement().followUpLevel()).isEqualTo(HealthFollowUpLevel.CLOSE_WATCH);
        assertThat(concluded.measurement().conclusionCategory()).isEqualTo(HealthConclusionCategory.REFERRAL);
        assertThat(concluded.measurement().followUpEffect()).isEqualTo(HealthFollowUpEffect.OPENED);
        assertThat(concluded.measurement().parentContact().contacted()).isFalse();
        Long followUpId = concluded.measurement().followUpId();
        assertThat(followUpId).isNotNull();

        HealthFollowUpListResponse list = followUps.listFollowUps(teacherA, null, null, false, null);
        assertThat(list.items()).singleElement().satisfies(f -> {
            assertThat(f.status()).isEqualTo(HealthFollowUpStatus.WAITING_PARENT_CONTACT);
            assertThat(f.currentRound().dueOn()).isEqualTo(LocalDate.of(2026, 11, 17));
            assertThat(f.child().className()).isEqualTo("Lớp A");
            assertThat(f.canUpdate()).isTrue();
        });
        assertThat(list.counts()).containsEntry("WAITING_PARENT_CONTACT", 1).containsEntry("OVERDUE", 0);
        assertThat(followUps.listFollowUps(vpA, null, null, false, null).items())
                .singleElement()
                .satisfies(f -> assertThat(f.canUpdate()).isFalse());
        assertThat(followUps.listFollowUps(vpB, null, null, false, null).items())
                .isEmpty();
        expectError(() -> followUps.getFollowUp(vpB, followUpId), ApiCode.HEALTH_FOLLOW_UP_NOT_FOUND);
        assertThat(followUps.getFollowUp(teacherA, followUpId).closeOutcomeOptions())
                .containsExactly(HealthFollowUpOutcome.NO_SPECIALIST_RESULT);

        expectError(
                () -> followUps.addExternalResult(teacherA, followUpId, external(List.of())),
                ApiCode.HEALTH_FOLLOW_UP_CONTACT_FIRST);
        expectError(
                () -> followUps.addParentContact(vpA, followUpId, contact("2026-11-10")),
                ApiCode.HEALTH_FOLLOW_UP_ACCESS_DENIED);
        expectError(
                () -> followUps.addParentContact(teacherA, followUpId, contact("2026-11-09")),
                ApiCode.INVALID_REQUEST_DATA);
        HealthFollowUpResponse contacted = followUps.addParentContact(teacherA, followUpId, contact("2026-11-10"));
        assertThat(contacted.status()).isEqualTo(HealthFollowUpStatus.PARENT_CONTACTED);
        assertThat(contacted.canRecordExternal()).isTrue();

        expectError(
                () -> followUps.addExternalResult(teacherA, followUpId, external(List.of("not-an-image"))),
                ApiCode.INVALID_REQUEST_DATA);
        HealthFollowUpResponse withResult = followUps.addExternalResult(teacherA, followUpId, external(List.of()));
        assertThat(withResult.status()).isEqualTo(HealthFollowUpStatus.EXTERNAL_RESULT_RECORDED);
        assertThat(withResult.latestExternalResult().facility()).isEqualTo("Mắt — Bệnh viện Nhi (giả)");
        assertThat(withResult.closeOutcomeOptions())
                .containsExactly(
                        HealthFollowUpOutcome.DOCTOR_NORMAL,
                        HealthFollowUpOutcome.KNOWN_CONDITION,
                        HealthFollowUpOutcome.NO_SPECIALIST_RESULT);

        expectError(
                () -> followUps.closeFollowUp(
                        teacherA,
                        followUpId,
                        new HealthFollowUpCloseRequest(HealthFollowUpOutcome.KNOWN_CONDITION, "", "Đã khám mắt")),
                ApiCode.INVALID_REQUEST_DATA);
        HealthFollowUpResponse closed = followUps.closeFollowUp(
                teacherA,
                followUpId,
                new HealthFollowUpCloseRequest(
                        HealthFollowUpOutcome.KNOWN_CONDITION, "Cận thị (giả)", "Bác sĩ chuyên khoa đã kết luận"));
        assertThat(closed.status()).isEqualTo(HealthFollowUpStatus.CLOSED);
        assertThat(closed.closeOutcomeOptions()).isEmpty();
        assertThat(closed.timeline())
                .extracting(HealthFollowUpResponse.HealthFollowUpEventView::action)
                .containsExactly(
                        HealthFollowUpAction.OPENED,
                        HealthFollowUpAction.PARENT_CONTACTED,
                        HealthFollowUpAction.EXTERNAL_RESULT,
                        HealthFollowUpAction.CLOSED);
        expectError(
                () -> followUps.addParentContact(teacherA, followUpId, contact("2026-11-10")),
                ApiCode.HEALTH_FOLLOW_UP_CLOSED);

        ChildHealthRecordResponse record = measurements.getHealthRecord(teacherA, child1);
        assertThat(record.openFollowUp()).isNull();
        assertThat(record.externalResults()).hasSize(1);
        assertThat(record.latestHealth().source()).isEqualTo(HealthLatestSource.FOLLOW_UP_CLOSED);
        assertThat(record.latestHealth().followUpLevel()).isEqualTo(HealthFollowUpLevel.KNOWN_STABLE);
        assertThat(record.latestHealth().knownCondition()).isEqualTo("Cận thị (giả)");
        // Vấn đề đã biết thêm vào tiền sử bản thân của khai báo
        assertThat(jdbc.queryForObject(
                        "SELECT self_history_text FROM child_health_declarations WHERE child_id = ?",
                        String.class,
                        child1))
                .isEqualTo("Cận thị (giả)");
        assertThat(declarationActions(child1)).containsExactly("UPDATED_FROM_EXAM", "UPDATED_FROM_FOLLOW_UP");
    }

    @Test
    void companionHearsConclusionAtExamAndOverdueIsComputedOnRead() {
        long campaign = examCampaign(campusA);
        HealthExamForm tbRisk = withHistory(
                exam("YES", 0, tbRiskAdvice()),
                new HealthExamForm.HealthExamHistory("NO", "", "NO", "", "YES", LocalDate.of(2026, 10, 1)));
        HealthExamResponse withCompanion = exams.saveExam(teacherA, campaign, child2, save(tbRisk, true, ""));
        assertThat(withCompanion.measurement().exam().admin().companion().fullName())
                .isEqualTo("Mẹ Bình (giả)");
        assertThat(withCompanion.measurement().exam().admin().companion().relation())
                .isEqualTo("Mẹ");
        assertThat(withCompanion.measurement().conclusionCategory()).isEqualTo(HealthConclusionCategory.TB_RISK);
        assertThat(withCompanion.measurement().parentContact().atExam()).isTrue();
        assertThat(followUps
                        .getFollowUp(teacherA, withCompanion.measurement().followUpId())
                        .status())
                .isEqualTo(HealthFollowUpStatus.PARENT_CONTACTED);

        HealthExamResponse waiting =
                exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, referral()), true, ""));
        // Kết quả lưu 10 ngày trước: hạn trao đổi 7 ngày đã qua 3 ngày
        entityManager.flush();
        jdbc.update(
                "UPDATE health_follow_up_events SET created_at = created_at - interval '10 days' WHERE follow_up_id = ?",
                waiting.measurement().followUpId());
        entityManager.clear();
        HealthFollowUpListResponse overdue = followUps.listFollowUps(teacherA, null, null, true, null);
        assertThat(overdue.items()).singleElement().satisfies(f -> {
            assertThat(f.child().id()).isEqualTo(child1);
            assertThat(f.currentRound().overdueDays()).isEqualTo(3);
        });
        assertThat(overdue.counts()).containsEntry("OVERDUE", 1).containsEntry("PARENT_CONTACTED", 1);
        HealthCheckResponse view = checks.getHealthCheck(vpA, campaign).campaign();
        assertThat(view.waitingFollowUpCount()).isEqualTo(1);
        assertThat(view.overdueFollowUpCount()).isEqualTo(1);
        assertThat(view.progress().closeWatch()).isEqualTo(2);
        assertThat(checks.getHealthCheck(vpA, campaign).watchList())
                .extracting(HealthCheckClassResponse.HealthCheckChild::childId)
                .containsExactly(child1, child2);

        HealthCheckCloseResponse closed = checks.closeHealthCheck(vpA, campaign);
        assertThat(closed.waitingFollowUpCount()).isEqualTo(1);
        assertThat(closed.notConcludedCount()).isZero();
    }

    @Test
    void externalResultKeepsUpToThreeImagesForInScopeStaff() {
        when(imageStorage.store(any(), anyString())).thenReturn("0f8fad5b-d9cb-469f-a165-70867728950e");
        when(imageStorage.load("0f8fad5b-d9cb-469f-a165-70867728950e"))
                .thenReturn(Base64.getDecoder().decode(PNG.substring(PNG.indexOf(',') + 1)));
        long campaign = examCampaign(campusA);
        Long followUpId = exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, referral()), true, ""))
                .measurement()
                .followUpId();
        followUps.addParentContact(teacherA, followUpId, contact("2026-11-10"));
        expectError(
                () -> followUps.addExternalResult(teacherA, followUpId, external(List.of(PNG, PNG, PNG, PNG))),
                ApiCode.INVALID_REQUEST_DATA);
        HealthFollowUpResponse saved = followUps.addExternalResult(teacherA, followUpId, external(List.of(PNG)));
        HealthFollowUpResponse.HealthFollowUpImageView image = saved.timeline().stream()
                .filter(e -> e.action() == HealthFollowUpAction.EXTERNAL_RESULT)
                .findFirst()
                .orElseThrow()
                .images()
                .getFirst();
        assertThat(image.path()).isEqualTo("/health-follow-ups/" + followUpId + "/images/" + image.id());
        assertThat(followUps.getImage(vpA, followUpId, image.id()).contentType())
                .isEqualTo("image/png");
        expectError(() -> followUps.getImage(vpB, followUpId, image.id()), ApiCode.HEALTH_FOLLOW_UP_NOT_FOUND);
    }

    private long examCampaign(long campusId) {
        return checks.createHealthCheck(principal, request(HealthCheckKind.EXAM, campusId, "2026-11-09", "2026-11-20"))
                .id();
    }

    private HealthCheckClassResponse.HealthCheckChild rowOf(long campaign, long childId) {
        return checks.getHealthCheckClass(teacherA, campaign, classA).children().stream()
                .filter(c -> c.childId().equals(childId))
                .findFirst()
                .orElseThrow();
    }

    private List<String> declarationActions(long childId) {
        return jdbc.queryForList(
                "SELECT action FROM child_health_declaration_history WHERE child_id = ? ORDER BY id",
                String.class,
                childId);
    }

    private static HealthExamRequest save(HealthExamForm exam, boolean conclude, String reason) {
        return new HealthExamRequest(
                LocalDate.of(2026, 11, 10),
                new BigDecimal("98.5"),
                new BigDecimal("15.2"),
                new BigDecimal("48.5"),
                exam,
                conclude,
                reason,
                null);
    }

    /** Phiếu đủ thông tin, mọi mục bình thường, kết luận theo tham số. */
    private static HealthExamForm exam(
            String hasCompanion, Integer guardianIndex, HealthExamForm.HealthExamConclusion conclusion) {
        return new HealthExamForm(
                2,
                new HealthExamForm.HealthExamAdmin(
                        "",
                        "",
                        null,
                        "",
                        "",
                        "",
                        "",
                        null,
                        hasCompanion,
                        new HealthExamForm.HealthExamCompanion(guardianIndex, "", "", "", "")),
                new HealthExamForm.HealthExamHistory("NO", "", "NO", "", "NO", null),
                new HealthExamForm.HealthExamVitals(
                        new BigDecimal("36.8"),
                        "NORMAL",
                        new BigDecimal("100"),
                        "NORMAL",
                        new BigDecimal("25"),
                        "NORMAL"),
                new HealthExamForm.HealthExamNutrition(null, null, "NORMAL", null, List.of("NORMAL")),
                new HealthExamForm.HealthExamDevelopment("YES", "YES", ""),
                new HealthExamForm.HealthExamImmunization("YES", "YES", "YES"),
                HealthExamCatalog.template().clinical(),
                conclusion);
    }

    private static HealthExamForm withVitals(HealthExamForm f, HealthExamForm.HealthExamVitals vitals) {
        return new HealthExamForm(
                f.step(),
                f.admin(),
                f.history(),
                vitals,
                f.nutrition(),
                f.development(),
                f.immunization(),
                f.clinical(),
                f.conclusion());
    }

    private static HealthExamForm withHistory(HealthExamForm f, HealthExamForm.HealthExamHistory history) {
        return new HealthExamForm(
                f.step(),
                f.admin(),
                history,
                f.vitals(),
                f.nutrition(),
                f.development(),
                f.immunization(),
                f.clinical(),
                f.conclusion());
    }

    private static HealthExamForm.HealthExamConclusion normal() {
        return new HealthExamForm.HealthExamConclusion(
                true, false, false, "", "", "NONE", "", "", "", "BS Khám (giả)", "");
    }

    private static HealthExamForm.HealthExamConclusion referral() {
        return new HealthExamForm.HealthExamConclusion(
                false, false, true, "", "", "REFERRAL", "Nghi cận thị (giả)", "", "Mắt", "BS Khám (giả)", "");
    }

    private static HealthExamForm.HealthExamConclusion tbRiskAdvice() {
        return new HealthExamForm.HealthExamConclusion(
                false,
                true,
                false,
                "",
                "",
                "ADVICE",
                "",
                "Theo dõi ho kéo dài (giả)",
                "",
                "BS Khám (giả)",
                "Gia đình đồng ý theo dõi (giả)");
    }

    private static HealthFollowUpContactRequest contact(String on) {
        return new HealthFollowUpContactRequest(LocalDate.parse(on), "Đã báo phụ huynh đưa trẻ đi khám (giả)");
    }

    private static HealthFollowUpExternalResultRequest external(List<String> images) {
        return new HealthFollowUpExternalResultRequest(
                LocalDate.of(2026, 11, 10), "Cận thị nhẹ, đeo kính (giả)", "Mắt — Bệnh viện Nhi (giả)", images, null);
    }

    private HealthCheckRequest request(HealthCheckKind kind, long campusId, String start, String end) {
        return new HealthCheckRequest(
                kind,
                kind == HealthCheckKind.MEASUREMENT ? "Cân đo thử (giả)" : "Khám thử (giả)",
                campusId,
                LocalDate.parse(start),
                LocalDate.parse(end),
                kind == HealthCheckKind.MEASUREMENT ? "" : "Trạm Y tế (giả)",
                "");
    }

    private long child(String name, String dateOfBirth) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                + "'HE' || substr(md5(random()::text), 1, 12), '" + name + "', '" + dateOfBirth
                + "', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
    }

    private void enroll(long childId, long classroomId) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childId,
                classroomId);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role));
    }

    private void campusRole(CurrentUser user, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                user.userId(),
                campusId);
    }

    private void teach(CurrentUser user, long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                user.userId(),
                classroomId);
    }

    private static void expectError(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
