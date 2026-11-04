package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthCheckClassResponse;
import com.carenest.dto.health.HealthCheckCloseResponse;
import com.carenest.dto.health.HealthCheckDetailResponse;
import com.carenest.dto.health.HealthCheckRequest;
import com.carenest.dto.health.HealthCheckResponse;
import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamRequest;
import com.carenest.dto.health.HealthExamResponse;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthCheckService;
import com.carenest.service.health.HealthExamService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthBmiStatus;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckResult;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthConclusionCategory;
import com.carenest.utils.enums.HealthConclusionMode;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.HealthFollowUpLevel;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Phiếu khám Phụ lục II, cảnh báo ở sổ sức khỏe: kết luận bác sĩ và mức BMI của kỳ cân đo (HLT-18, HLT-19; plan
 * docs/plans/active/2026-11-04-health-warnings-book.md). Dữ liệu giả.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class HealthExamIntegrationTest {

    // Hôm nay 2026-11-10 (giờ trường)
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        HealthTestClock fixedClock() {
            return new HealthTestClock(Instant.parse("2026-11-10T02:00:00Z"));
        }
    }

    @Autowired
    private HealthCheckService checks;

    @Autowired
    private HealthTestClock testClock;

    @Autowired
    private HealthExamService exams;

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
        testClock.setInstant(Instant.parse("2026-11-10T02:00:00Z"));
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
    @DisplayName("HLT-08: GV lưu nháp, kết luận đủ phiếu, sửa phiếu đã kết luận phải có lý do")
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
        assertThat(beforeRecord.latestExam()).isNull();

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
        // Lần đo của đợt khám theo kết luận bác sĩ, không xếp mức BMI (HLT-18)
        assertThat(concluded.measurement().bmi()).isNotNull();
        assertThat(concluded.measurement().bmiStatus()).isNull();
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
                        principal, request(HealthCheckKind.MEASUREMENT, campusB, "2026-11-10", "2026-11-20"))
                .id();
        expectError(() -> exams.getExam(teacherB, round, child3), ApiCode.HEALTH_EXAM_ROUND_REQUIRED);
    }

    @Test
    @DisplayName("HLT-19: kết luận chuyển khám chuyên khoa là cảnh báo ở sổ sức khỏe, không mở phiếu theo dõi")
    void referralIsAHealthBookWarningWithoutFollowUpForm() {
        long campaign = examCampaign(campusA);
        HealthExamResponse concluded =
                exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, referral()), true, ""));
        assertThat(concluded.measurement().followUpLevel()).isEqualTo(HealthFollowUpLevel.CLOSE_WATCH);
        assertThat(concluded.measurement().conclusionCategory()).isEqualTo(HealthConclusionCategory.REFERRAL);

        ChildHealthRecordResponse record = measurements.getHealthRecord(teacherA, child1);
        assertThat(record.latestExam()).satisfies(latest -> {
            assertThat(latest.measurementId()).isEqualTo(concluded.measurement().id());
            assertThat(latest.campaignId()).isEqualTo(campaign);
            assertThat(latest.campaignName()).isEqualTo("Khám thử (giả)");
            assertThat(latest.date()).isEqualTo(LocalDate.of(2026, 11, 10));
            assertThat(latest.followUpLevel()).isEqualTo(HealthFollowUpLevel.CLOSE_WATCH);
            assertThat(latest.conclusionCategory()).isEqualTo(HealthConclusionCategory.REFERRAL);
            assertThat(latest.conclusion().specialty()).isEqualTo("Mắt");
        });
        assertThat(record.latestBmi()).isNull();
        // Không có phiếu, không có việc GV phải xử lý; tiền sử chỉ ghi từ phiếu khám
        assertThat(jdbc.queryForObject("SELECT count(*) FROM health_follow_ups", Integer.class))
                .isZero();
        assertThat(declarationActions(child1)).containsExactly("UPDATED_FROM_EXAM");
        // Lần khám sau vẫn dùng mẫu kết luận thường, kèm lần khám trước để bác sĩ so sánh
        HealthExamResponse next = exams.getExam(teacherA, campaign, child2);
        assertThat(next.conclusionMode()).isEqualTo(HealthConclusionMode.STANDARD);
    }

    @Test
    @DisplayName("HLT-19: đợt khám đếm trẻ theo dõi sát và liệt kê trẻ cần chú ý, không còn đếm phiếu theo dõi")
    void examCampaignListsChildrenNeedingAttention() {
        long campaign = examCampaign(campusA);
        HealthExamForm tbRisk = withHistory(
                exam("YES", 0, tbRiskAdvice()),
                new HealthExamForm.HealthExamHistory("NO", "", "NO", "", "YES", LocalDate.of(2026, 10, 1)));
        HealthExamResponse withCompanion = exams.saveExam(teacherA, campaign, child2, save(tbRisk, true, ""));
        assertThat(withCompanion.measurement().exam().admin().companion().fullName())
                .isEqualTo("Mẹ Bình (giả)");
        assertThat(withCompanion.measurement().conclusionCategory()).isEqualTo(HealthConclusionCategory.TB_RISK);
        exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, referral()), true, ""));

        HealthCheckDetailResponse detail = checks.getHealthCheck(vpA, campaign);
        assertThat(detail.campaign().progress().closeWatch()).isEqualTo(2);
        assertThat(detail.campaign().progress().bmi())
                .isEqualTo(new HealthCheckResponse.HealthBmiProgress(0, 0, 0, 0, 0));
        assertThat(detail.classes()).singleElement().satisfies(c -> {
            assertThat(c.closeWatch()).isEqualTo(2);
            assertThat(c.bmiWarning()).isZero();
        });
        assertThat(detail.watchList())
                .extracting(HealthCheckClassResponse.HealthCheckChild::childId)
                .containsExactly(child1, child2);

        HealthCheckCloseResponse closed = checks.closeHealthCheck(vpA, campaign);
        assertThat(closed.notConcludedCount()).isZero();
        assertThat(closed.absentCount()).isZero();
    }

    @Test
    @DisplayName(
            "HLT-10: phiếu không hỏi vấn đề mới hay đã biết ⇒ có vấn đề là cần theo dõi; phiếu cũ đã trả lời giữ mức")
    void healthIssueIsWatchedWithoutAskingIfKnown() {
        jdbc.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state, "
                        + "self_history_state, self_history_text, family_history_state, tb_contact_state, declared_by) "
                        + "VALUES (?, 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'REPORTED', 'Hen suyễn (giả)', "
                        + "'NONE_REPORTED', 'NONE_REPORTED', ?)",
                child1,
                principal.userId());
        long campaign = examCampaign(campusA);
        // Trẻ có tiền sử đã khai, phiếu vẫn lưu được mà không chọn mới hay đã biết; gửi kèm "KNOWN" cũng bị bỏ
        HealthExamForm asthma = withHistory(
                exam("NO", null, knownIssue("KNOWN")),
                new HealthExamForm.HealthExamHistory("YES", "Hen suyễn (giả)", "NO", "", "NO", null));
        HealthExamResponse concluded = exams.saveExam(teacherA, campaign, child1, save(asthma, true, ""));
        assertThat(concluded.measurement().followUpLevel()).isEqualTo(HealthFollowUpLevel.WATCH);
        assertThat(concluded.measurement().exam().conclusion().issueKind()).isEmpty();
        assertThat(checks.getHealthCheck(vpA, campaign).watchList())
                .extracting(HealthCheckClassResponse.HealthCheckChild::childId)
                .containsExactly(child1);

        // Dữ liệu cũ: phiếu đã trả lời "vấn đề đã biết" giữ mức ổn định khi sửa
        long id = concluded.measurement().id();
        entityManager.flush();
        jdbc.update(
                "UPDATE health_measurements SET exam = jsonb_set(exam, '{conclusion,issueKind}', '\"KNOWN\"') "
                        + "WHERE id = ?",
                id);
        entityManager.clear();
        HealthExamResponse legacy = exams.getExam(teacherA, campaign, child1);
        assertThat(legacy.measurement().followUpLevel()).isEqualTo(HealthFollowUpLevel.KNOWN_STABLE);
        HealthExamResponse edited = exams.saveExam(
                teacherA,
                campaign,
                child1,
                save(withVitals(legacy.measurement().exam(), feverVitals()), true, "Sửa nhiệt độ (giả)"));
        assertThat(edited.measurement().followUpLevel()).isEqualTo(HealthFollowUpLevel.KNOWN_STABLE);
        assertThat(edited.measurement().exam().conclusion().issueKind()).isEqualTo("KNOWN");
    }

    @Test
    @DisplayName("HLT-16: phiếu đã kết luận theo mẫu theo dõi cũ giữ mẫu đó; phiếu mới dùng mẫu thường")
    void legacyFollowUpConclusionKeepsItsMode() {
        long campaign = examCampaign(campusA);
        long id = exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, normal()), true, ""))
                .measurement()
                .id();
        // Dữ liệu cũ: phiếu kết luận theo mẫu FOLLOW_UP khi trẻ còn phiếu theo dõi
        entityManager.flush();
        jdbc.update(
                "UPDATE health_measurements SET exam = jsonb_set(jsonb_set(jsonb_set(exam, "
                        + "'{conclusion,followUpFinding}', '\"ADVICE\"'), '{conclusion,issueText}', "
                        + "'\"Viêm họng tái diễn (giả)\"'), '{conclusion,adviceText}', '\"Uống đủ nước (giả)\"') "
                        + "WHERE id = ?",
                id);
        entityManager.clear();

        HealthExamResponse legacy = exams.getExam(teacherA, campaign, child1);
        assertThat(legacy.conclusionMode()).isEqualTo(HealthConclusionMode.FOLLOW_UP);
        assertThat(legacy.measurement().followUpLevel()).isEqualTo(HealthFollowUpLevel.CLOSE_WATCH);
        HealthExamForm legacyForm = legacy.measurement().exam();
        expectError(
                () -> exams.saveExam(
                        teacherA, campaign, child1, save(legacyForm, true, "Sửa (giả)", HealthConclusionMode.STANDARD)),
                ApiCode.HEALTH_EXAM_MODE_CHANGED);
        HealthExamResponse edited = exams.saveExam(
                teacherA,
                campaign,
                child1,
                save(
                        withVitals(legacyForm, feverVitals()),
                        true,
                        "Sửa nhiệt độ (giả)",
                        HealthConclusionMode.FOLLOW_UP));
        assertThat(edited.conclusionMode()).isEqualTo(HealthConclusionMode.FOLLOW_UP);
        assertThat(edited.measurement().conclusion().followUpFinding()).isEqualTo("ADVICE");
        assertThat(edited.measurement().edits()).hasSize(1);
        assertThat(exams.getExam(teacherA, campaign, child2).conclusionMode()).isEqualTo(HealthConclusionMode.STANDARD);
    }

    @Test
    @DisplayName("HLT-18: kỳ cân đo xếp mức BMI theo tuổi chuẩn WHO, lưu vào sổ sức khỏe và tính lại khi sửa số đo")
    void measurementRoundWarnsByBmiForAge() {
        long round = checks.createHealthCheck(
                        principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2026-11-20"))
                .id();
        // An 46 tháng: BMI 20,5 / 0,985² ≈ 21,1 > +3SD (19,8) ⇒ béo phì; Bình 45 tháng: BMI ≈ 15,7 ⇒ bình thường
        HealthMeasurementResponse obese = measurements.createMeasurement(teacherA, child1, measure(round, "20.5", ""));
        assertThat(obese.ageMonths()).isEqualTo(46);
        assertThat(obese.bmi()).isEqualByComparingTo("21.1");
        assertThat(obese.bmiStatus()).isEqualTo(HealthBmiStatus.OBESE);
        assertThat(obese.bmiZScore()).isGreaterThan(new BigDecimal("3"));
        assertThat(obese.followUpLevel()).isEqualTo(HealthFollowUpLevel.UNAVAILABLE);
        HealthMeasurementResponse normal = measurements.createMeasurement(teacherA, child2, measure(round, "15.2", ""));
        assertThat(normal.bmiStatus()).isEqualTo(HealthBmiStatus.NORMAL);

        ChildHealthRecordResponse record = measurements.getHealthRecord(teacherA, child1);
        assertThat(record.latestExam()).isNull();
        assertThat(record.latestBmi()).satisfies(latest -> {
            assertThat(latest.measurementId()).isEqualTo(obese.id());
            assertThat(latest.campaignName()).isEqualTo("Cân đo thử (giả)");
            assertThat(latest.ageMonths()).isEqualTo(46);
            assertThat(latest.bmiStatus()).isEqualTo(HealthBmiStatus.OBESE);
            assertThat(latest.bmiZScore()).isEqualByComparingTo(obese.bmiZScore());
        });
        assertThat(record.measurements())
                .singleElement()
                .satisfies(m -> assertThat(m.bmiStatus()).isEqualTo(HealthBmiStatus.OBESE));

        assertThat(rowOf(round, child1).bmiStatus()).isEqualTo(HealthBmiStatus.OBESE);
        assertThat(rowOf(round, child1).bmi()).isEqualByComparingTo("21.1");
        assertThat(rowOf(round, child2).bmiStatus()).isEqualTo(HealthBmiStatus.NORMAL);
        HealthCheckDetailResponse detail = checks.getHealthCheck(vpA, round);
        assertThat(detail.campaign().progress().bmi())
                .isEqualTo(new HealthCheckResponse.HealthBmiProgress(0, 0, 0, 0, 1));
        assertThat(detail.classes())
                .singleElement()
                .satisfies(c -> assertThat(c.bmiWarning()).isEqualTo(1));
        assertThat(detail.watchList())
                .extracting(HealthCheckClassResponse.HealthCheckChild::childId)
                .containsExactly(child1);

        // Sửa số đo (bắt buộc lý do — HLT-13) ⇒ mức tính lại: BMI ≈ 17,5 trong (+1SD, +2SD] ⇒ nguy cơ thừa cân
        HealthMeasurementResponse fixed =
                measurements.updateMeasurement(teacherA, child1, obese.id(), measure(round, "17.0", "Cân nhầm (giả)"));
        assertThat(fixed.bmiStatus()).isEqualTo(HealthBmiStatus.OVERWEIGHT_RISK);
        assertThat(measurements.getHealthRecord(teacherA, child1).latestBmi().bmiStatus())
                .isEqualTo(HealthBmiStatus.OVERWEIGHT_RISK);
        assertThat(checks.getHealthCheck(vpA, round).campaign().progress().bmi())
                .isEqualTo(new HealthCheckResponse.HealthBmiProgress(0, 0, 1, 0, 0));
    }

    @Test
    @DisplayName("HLT-17: đợt khám tự đóng lúc 00:00 giờ trường, khóa kết quả, giữ phiếu nháp và cảnh báo ở sổ")
    void autoCloseAtSchoolMidnightLocksResultsAndPreservesDrafts() {
        long campaign = checks.createHealthCheck(
                        principal, request(HealthCheckKind.EXAM, campusA, "2026-11-10", "2026-11-10"))
                .id();
        exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, referral()), true, ""));
        exams.saveExam(teacherA, campaign, child2, save(HealthExamCatalog.template(), false, ""));
        long absentChild = child("Dũng", "2023-04-01");
        enroll(absentChild, classA);

        testClock.setInstant(Instant.parse("2026-11-10T16:59:59Z"));
        checks.closeExpiredHealthChecks();
        assertThat(checks.getHealthCheck(vpA, campaign).campaign().status()).isEqualTo(HealthCheckStatus.IN_PROGRESS);

        // 00:00 ngày 11/11 ở trường; ngày UTC vẫn là 10/11.
        testClock.setInstant(Instant.parse("2026-11-10T17:00:00Z"));
        checks.closeExpiredHealthChecks();

        HealthCheckResponse closed = checks.getHealthCheck(vpA, campaign).campaign();
        assertThat(closed.status()).isEqualTo(HealthCheckStatus.CLOSED);
        assertThat(closed.closedAt()).isEqualTo(Instant.parse("2026-11-10T17:00:00Z"));
        assertThat(closed.progress().recorded()).isEqualTo(1);
        assertThat(closed.progress().notConcluded()).isEqualTo(1);
        assertThat(closed.progress().absent()).isEqualTo(1);
        assertThat(rowOf(campaign, child1).result()).isEqualTo(HealthCheckResult.RECORDED);
        assertThat(rowOf(campaign, child2).result()).isEqualTo(HealthCheckResult.NOT_CONCLUDED);
        assertThat(rowOf(campaign, absentChild).result()).isEqualTo(HealthCheckResult.ABSENT);
        assertThat(exams.getExam(teacherA, campaign, child1).canEdit()).isFalse();
        assertThat(exams.getExam(teacherA, campaign, child2).measurement().examStatus())
                .isEqualTo(HealthExamStatus.DRAFT);
        assertThat(exams.getExam(teacherA, campaign, child2).measurement().published())
                .isFalse();
        // Cảnh báo của lần khám vẫn ở sổ sức khỏe sau khi đợt đóng
        assertThat(measurements.getHealthRecord(teacherA, child1).latestExam().followUpLevel())
                .isEqualTo(HealthFollowUpLevel.CLOSE_WATCH);
        expectError(
                () -> exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, normal()), true, "Nhập bù")),
                ApiCode.HEALTH_EXAM_LOCKED);
        expectError(
                () -> exams.saveExam(teacherA, campaign, child2, save(HealthExamCatalog.template(), false, "")),
                ApiCode.HEALTH_EXAM_LOCKED);
    }

    private long examCampaign(long campusId) {
        return checks.createHealthCheck(principal, request(HealthCheckKind.EXAM, campusId, "2026-11-10", "2026-11-20"))
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

    // Số đo kỳ cân đo ngày 2026-11-10, cao 98,5 cm, vòng đầu 49 cm
    private static HealthMeasurementRequest measure(long round, String weightKg, String reason) {
        return new HealthMeasurementRequest(
                round,
                LocalDate.of(2026, 11, 10),
                new BigDecimal("98.5"),
                new BigDecimal(weightKg),
                new BigDecimal("49.0"),
                "",
                reason);
    }

    private static HealthExamRequest save(HealthExamForm exam, boolean conclude, String reason) {
        return save(exam, conclude, reason, null);
    }

    private static HealthExamRequest save(
            HealthExamForm exam, boolean conclude, String reason, HealthConclusionMode mode) {
        return new HealthExamRequest(
                LocalDate.of(2026, 11, 10),
                new BigDecimal("98.5"),
                new BigDecimal("15.2"),
                new BigDecimal("48.5"),
                exam,
                conclude,
                reason,
                mode);
    }

    private static HealthExamForm.HealthExamVitals feverVitals() {
        return new HealthExamForm.HealthExamVitals(
                new BigDecimal("38.2"), "FEVER", new BigDecimal("100"), "NORMAL", new BigDecimal("25"), "NORMAL");
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

    // Có vấn đề sức khỏe, tư vấn; issueKind "KNOWN" = vấn đề đã biết, "" = chưa chọn
    private static HealthExamForm.HealthExamConclusion knownIssue(String issueKind) {
        return new HealthExamForm.HealthExamConclusion(
                false,
                false,
                true,
                issueKind,
                "",
                "ADVICE",
                "Hen suyễn tái phát nhẹ (giả)",
                "Tránh khói bụi (giả)",
                "",
                "BS Khám (giả)",
                "");
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
