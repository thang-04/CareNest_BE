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
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

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
        // HLT-11, PAR-03: phụ huynh liên kết nhận thông báo khi kết luận (công bố) và khi sửa phiếu đã kết luận
        long parentId = user("he-parent", CareNestRole.PARENT).userId();
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Mẹ (giả)', 'MOTHER', '0900000009', '000000000009', now())",
                parentId,
                child1);
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
        assertThat(noticesOf(parentId)).isEmpty();

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
        assertThat(noticesOf(parentId)).containsExactly("HEALTH_RESULT_PUBLISHED");
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
        assertThat(noticesOf(parentId)).containsExactly("HEALTH_RESULT_PUBLISHED", "HEALTH_RESULT_UPDATED");
        // Không cân đo lẻ bằng API số đo cho phiếu khám
        expectError(
                () -> measurements.updateMeasurement(
                        teacherA,
                        child1,
                        edited.measurement().id(),
                        new HealthMeasurementRequest(null, new BigDecimal("99.0"), new BigDecimal("15.2"), "", "Sửa")),
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

    @Test
    @DisplayName(
            "HLT-08: ngày khám hệ thống ghi ở lần lưu đầu; kết luận, sửa sau đó giữ ngày, gợi ý theo tuổi tại ngày đó")
    void examDateIsRecordedOnFirstSaveAndKept() {
        // Hai trẻ đủ 60 tháng ngày 10/11/2026 (còn đo vòng đầu), 61 tháng từ 10/12/2026 (không đo)
        long drafted = child("Dương", "2021-11-10");
        long fresh = child("Giang", "2021-11-10");
        enroll(drafted, classA);
        enroll(fresh, classA);
        long campaign = checks.createHealthCheck(
                        principal, request(HealthCheckKind.EXAM, campusA, "2026-11-10", "2026-12-20"))
                .id();
        exams.saveExam(teacherA, campaign, drafted, save(HealthExamCatalog.template(), false, ""));

        testClock.setInstant(Instant.parse("2026-12-11T02:00:00Z"));
        // Phiếu đã lưu: gợi ý theo tuổi tại ngày khám đã ghi (có vòng đầu); phiếu chưa lưu: theo hôm nay
        assertThat(exams.reviewPreview(teacherA, campaign, drafted, save(exam("NO", null, normal()), false, ""))
                        .reviewPoints()
                        .combined()
                        .total())
                .isEqualTo(3);
        assertThat(exams.reviewPreview(teacherA, campaign, fresh, save(exam("NO", null, normal()), false, ""))
                        .reviewPoints()
                        .combined()
                        .total())
                .isEqualTo(2);

        HealthExamResponse concluded =
                exams.saveExam(teacherA, campaign, drafted, save(exam("NO", null, normal()), true, ""));
        assertThat(concluded.measurement().date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(concluded.reference().headApplies()).isTrue();
        assertThat(concluded.measurement().headCircumferenceCm()).isEqualByComparingTo("48.5");

        testClock.setInstant(Instant.parse("2026-12-12T02:00:00Z"));
        HealthExamResponse edited = exams.saveExam(
                teacherA,
                campaign,
                drafted,
                save(withVitals(exam("NO", null, normal()), feverVitals()), true, "Sửa nhiệt độ (giả)"));
        assertThat(edited.measurement().date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(edited.measurement().edits())
                .singleElement()
                .satisfies(e -> assertThat(e.changes())
                        .extracting(HealthMeasurementResponse.HealthMeasurementChange::field)
                        .isNotEmpty()
                        .doesNotContain("date"));

        // Phiếu mới lưu hôm nay: ngày khám là hôm nay, trẻ đã quá 60 tháng nên không ghi vòng đầu
        HealthExamResponse first =
                exams.saveExam(teacherA, campaign, fresh, save(exam("NO", null, normal()), true, ""));
        assertThat(first.measurement().date()).isEqualTo(LocalDate.of(2026, 12, 12));
        assertThat(first.reference().headApplies()).isFalse();
        assertThat(first.measurement().headCircumferenceCm()).isNull();
    }

    @Test
    @DisplayName("HLT-08 (2026-11-24): ngày khám trẻ đã có số đo kỳ cân đo ⇒ vẫn lưu nháp, kết luận; sổ giữ cả hai")
    void examSavesOnDayWithRoundMeasurement() {
        long round = checks.createHealthCheck(
                        principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2026-11-20"))
                .id();
        long measured = measurements
                .createMeasurement(teacherA, child1, measure(round, "15.2", ""))
                .id();
        checks.closeHealthCheck(principal, round);
        long campaign = examCampaign(campusA);

        HealthExamResponse draft =
                exams.saveExam(teacherA, campaign, child1, save(HealthExamCatalog.template(), false, ""));
        assertThat(draft.measurement().date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(draft.measurement().examStatus()).isEqualTo(HealthExamStatus.DRAFT);
        HealthExamResponse concluded =
                exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, normal()), true, ""));
        assertThat(concluded.measurement().examStatus()).isEqualTo(HealthExamStatus.CONCLUDED);
        assertThat(rowOf(campaign, child1).result()).isEqualTo(HealthCheckResult.RECORDED);
        assertThat(measurements.getHealthRecord(teacherA, child1).measurements())
                .extracting(HealthMeasurementResponse::id, HealthMeasurementResponse::date)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(measured, LocalDate.of(2026, 11, 10)),
                        org.assertj.core.groups.Tuple.tuple(
                                concluded.measurement().id(), LocalDate.of(2026, 11, 10)));
    }

    @Test
    @DisplayName("HLT-08 (2026-11-24): phiếu khám mới điền sẵn chiều cao, cân nặng của số đo kỳ cân đo cùng ngày")
    void examPrefillsHeightWeightFromSameDayMeasurement() {
        // Bình cân ngày 09/11, An cân ngày 10/11 (hôm nay, cùng ngày khám)
        testClock.setInstant(Instant.parse("2026-11-09T02:00:00Z"));
        long round = checks.createHealthCheck(
                        principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"))
                .id();
        measurements.createMeasurement(teacherA, child2, measure(round, "14.8", ""));
        testClock.setInstant(Instant.parse("2026-11-10T02:00:00Z"));
        measurements.createMeasurement(teacherA, child1, measure(round, "15.6", ""));
        checks.closeHealthCheck(principal, round);
        long campaign = examCampaign(campusA);

        HealthExamResponse.HealthExamDraft prefilled =
                exams.getExam(teacherA, campaign, child1).draft();
        assertThat(prefilled.date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(prefilled.heightCm()).isEqualByComparingTo("98.5");
        assertThat(prefilled.weightKg()).isEqualByComparingTo("15.6");
        assertThat(prefilled.headCircumferenceCm()).isNull();
        // Số đo ngày khác không điền
        HealthExamResponse.HealthExamDraft blank =
                exams.getExam(teacherA, campaign, child2).draft();
        assertThat(blank.heightCm()).isNull();
        assertThat(blank.weightKg()).isNull();
    }

    @Test
    @DisplayName("HLT-08: phiếu mới trùng ngày khám với phiếu khám của đợt khám khác ⇒ lỗi ô date kèm ngày, báo HT/PHT")
    void newExamOnDayOfAnotherExamAsksLeaders() {
        long first = examCampaign(campusA);
        exams.saveExam(teacherA, first, child1, save(HealthExamCatalog.template(), false, ""));
        checks.closeHealthCheck(principal, first);
        long campaign = examCampaign(campusA);

        assertThatThrownBy(
                        () -> exams.saveExam(teacherA, campaign, child1, save(HealthExamCatalog.template(), false, "")))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> {
                    GlobalException g = (GlobalException) ex;
                    assertThat(g.getApiCode()).isEqualTo(ApiCode.HEALTH_EXAM_INCOMPLETE);
                    assertThat(g.getFieldErrors()).singleElement().satisfies(e -> {
                        assertThat(e.field()).isEqualTo("date");
                        assertThat(e.message())
                                .isEqualTo("Ngày khám 10/11/2026 trẻ đã có kết quả đo khác. "
                                        + "Báo Hiệu trưởng hoặc Phó hiệu trưởng.");
                    });
                });
        // Trẻ khác chưa có phiếu khám hôm nay ⇒ lưu được
        assertThat(exams.saveExam(teacherA, campaign, child2, save(HealthExamCatalog.template(), false, ""))
                        .measurement()
                        .date())
                .isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    @DisplayName("G94-180/HLT-09: trẻ chuyển lớp trong đợt khám ⇒ phiếu hiện ở lớp mới, GV lớp mới sửa; lớp cũ chỉ xem")
    void examOfTransferredChildFollowsCurrentClass() {
        long classA2 = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) SELECT "
                + campusA + ", school_year_id, 'Lớp A2', age_group_id FROM classrooms WHERE id = " + classA
                + " RETURNING id");
        CurrentUser teacherA2 = user("he-teachera2", CareNestRole.TEACHER);
        teach(teacherA2, classA2);
        long campaign = examCampaign(campusA);
        long examId = exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, normal()), true, ""))
                .measurement()
                .id();
        // An khám ngày 10/11 ở Lớp A, chuyển sang Lớp A2 từ 12/11
        testClock.setInstant(Instant.parse("2026-11-12T02:00:00Z"));
        entityManager.flush();
        jdbc.update(
                "UPDATE enrollments SET valid_to = '2026-11-11' WHERE child_id = ? AND classroom_id = ?",
                child1,
                classA);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-11-12')",
                child1,
                classA2);
        entityManager.clear();

        HealthCheckClassResponse.HealthCheckChild inNew = rowIn(campaign, teacherA2, classA2, child1);
        assertThat(inNew.measurementId()).isEqualTo(examId);
        assertThat(inNew.result()).isEqualTo(HealthCheckResult.RECORDED);
        assertThat(inNew.canEdit()).isTrue();
        assertThat(inNew.measuredIn())
                .isEqualTo(new HealthCheckClassResponse.MeasuredIn("Lớp A", "Điểm A", LocalDate.of(2026, 11, 10)));
        HealthCheckClassResponse.HealthCheckChild inOld = rowIn(campaign, teacherA, classA, child1);
        assertThat(inOld.movedTo())
                .isEqualTo(new HealthCheckClassResponse.MovedTo("Lớp A2", "Điểm A", LocalDate.of(2026, 11, 12)));
        assertThat(inOld.canEdit()).isFalse();
        assertThat(checks.getHealthCheck(vpA, campaign).classes())
                .extracting(c -> c.className(), c -> c.total(), c -> c.recorded())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Lớp A", 1, 0),
                        org.assertj.core.groups.Tuple.tuple("Lớp A2", 1, 1));
        // Phiếu khám theo lớp hiện tại của trẻ: GV lớp mới sửa được, GV lớp cũ không còn mở phiếu
        assertThat(exams.getExam(teacherA2, campaign, child1).canEdit()).isTrue();
        expectError(
                () -> exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, normal()), true, "Sửa (giả)")),
                ApiCode.HEALTH_EXAM_NOT_FOUND);
    }

    @Test
    @DisplayName(
            "G94-180/HLT-09: GV lớp mới lưu phiếu khám của trẻ chuyển lớp ⇒ phiếu ở lại lớp lúc khám, lớp cũ vẫn có"
                    + " dòng đã chuyển")
    void savingExamOfTransferredChildKeepsClassWhereTaken() {
        long classA2 = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) SELECT "
                + campusA + ", school_year_id, 'Lớp A2', age_group_id FROM classrooms WHERE id = " + classA
                + " RETURNING id");
        CurrentUser teacherA2 = user("he-teachera2", CareNestRole.TEACHER);
        teach(teacherA2, classA2);
        long campaign = examCampaign(campusA);
        long examId = exams.saveExam(teacherA, campaign, child1, save(HealthExamCatalog.template(), false, ""))
                .measurement()
                .id();
        // An khám dở (nháp) ngày 10/11 ở Lớp A, chuyển sang Lớp A2 từ 12/11; GV Lớp A2 khám tiếp và kết luận
        testClock.setInstant(Instant.parse("2026-11-12T02:00:00Z"));
        entityManager.flush();
        jdbc.update(
                "UPDATE enrollments SET valid_to = '2026-11-11' WHERE child_id = ? AND classroom_id = ?",
                child1,
                classA);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-11-12')",
                child1,
                classA2);
        entityManager.clear();

        exams.saveExam(teacherA2, campaign, child1, save(exam("NO", null, normal()), true, ""));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT classroom_id FROM health_measurements WHERE id = ?", Long.class, examId))
                .isEqualTo(classA);
        HealthCheckClassResponse.HealthCheckChild inOld = rowIn(campaign, teacherA, classA, child1);
        assertThat(inOld.result()).isEqualTo(HealthCheckResult.RECORDED);
        assertThat(inOld.movedTo())
                .isEqualTo(new HealthCheckClassResponse.MovedTo("Lớp A2", "Điểm A", LocalDate.of(2026, 11, 12)));
        HealthCheckClassResponse.HealthCheckChild inNew = rowIn(campaign, teacherA2, classA2, child1);
        assertThat(inNew.measuredIn())
                .isEqualTo(new HealthCheckClassResponse.MeasuredIn("Lớp A", "Điểm A", LocalDate.of(2026, 11, 10)));
        assertThat(inNew.canEdit()).isTrue();
    }

    @Test
    @DisplayName("G94-180/HLT-09: trẻ đã nghỉ học hoặc sang điểm trường khác ⇒ dòng phiếu khám ở lớp cũ chỉ xem (phiếu"
            + " chỉ mở ở lớp hiện tại cùng điểm trường của đợt)")
    void examRowOfChildWhoLeftOrChangedCampusIsReadOnly() {
        long campaign = examCampaign(campusA);
        exams.saveExam(teacherA, campaign, child1, save(exam("NO", null, normal()), true, ""));
        exams.saveExam(teacherA, campaign, child2, save(HealthExamCatalog.template(), false, ""));
        long child4 = child("Dũng", "2023-04-01");
        enroll(child4, classA);
        long classB = jdbc.queryForObject("SELECT id FROM classrooms WHERE name = 'Lớp B'", Long.class);
        // Từ 12/11: An sang Lớp B (điểm B), Bình nghỉ học
        testClock.setInstant(Instant.parse("2026-11-12T02:00:00Z"));
        entityManager.flush();
        jdbc.update(
                "UPDATE enrollments SET valid_to = '2026-11-11' WHERE child_id IN (?, ?) AND classroom_id = ?",
                child1,
                child2,
                classA);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-11-12')",
                child1,
                classB);
        jdbc.update(
                "UPDATE children SET status = 'LEFT', left_on = '2026-11-12', left_reason = 'Chuyển trường (giả)'"
                        + " WHERE id = ?",
                child2);
        entityManager.clear();

        for (long gone : new long[] {child1, child2}) {
            HealthCheckClassResponse.HealthCheckChild row = rowOf(campaign, gone);
            assertThat(row.movedTo()).isNull();
            assertThat(row.canEdit()).isFalse();
            expectError(() -> exams.getExam(teacherA, campaign, gone), ApiCode.HEALTH_EXAM_NOT_FOUND);
        }
        assertThat(rowOf(campaign, child4).canEdit()).isTrue();
    }

    @Test
    @DisplayName(
            "HLT-01, HLT-08: trẻ điểm danh vắng ngày khám (thứ 2–6) ⇒ không mở phiếu khám mới; phiếu đã có vẫn lưu")
    void absentChildCannotStartExam() {
        long campaign = examCampaign(campusA);
        // Bình đã có phiếu nháp sáng nay rồi về sớm, điểm danh vắng; An vắng không phép cả ngày (thứ 3 10/11)
        exams.saveExam(teacherA, campaign, child2, save(HealthExamCatalog.template(), false, ""));
        for (long childId : new long[] {child1, child2}) {
            jdbc.update(
                    "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, recorded_by,"
                            + " recorded_at) VALUES (?, ?, ?, '2026-11-10', 'UNEXCUSED', ?, now())",
                    childId,
                    classA,
                    campusA,
                    teacherA.userId());
        }

        assertThatThrownBy(
                        () -> exams.saveExam(teacherA, campaign, child1, save(HealthExamCatalog.template(), false, "")))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.HEALTH_CHILD_ABSENT);
                    assertThat(ex.getMessage()).isEqualTo("Trẻ vắng ngày 10/11/2026 (điểm danh). Nhập khi trẻ có mặt.");
                });
        assertThat(rowOf(campaign, child1).absentToday()).isTrue();
        assertThat(rowOf(campaign, child1).result()).isEqualTo(HealthCheckResult.NOT_RECORDED);
        assertThat(exams.saveExam(teacherA, campaign, child2, save(exam("NO", null, normal()), true, ""))
                        .measurement()
                        .examStatus())
                .isEqualTo(HealthExamStatus.CONCLUDED);
    }

    private long examCampaign(long campusId) {
        return checks.createHealthCheck(principal, request(HealthCheckKind.EXAM, campusId, "2026-11-10", "2026-11-20"))
                .id();
    }

    private HealthCheckClassResponse.HealthCheckChild rowOf(long campaign, long childId) {
        return rowIn(campaign, teacherA, classA, childId);
    }

    private HealthCheckClassResponse.HealthCheckChild rowIn(
            long campaign, CurrentUser user, long classId, long childId) {
        return checks.getHealthCheckClass(user, campaign, classId).children().stream()
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

    // Số đo kỳ cân đo, cao 98,5 cm; ngày đo hệ thống ghi (hôm nay)
    private static HealthMeasurementRequest measure(long round, String weightKg, String reason) {
        return new HealthMeasurementRequest(round, new BigDecimal("98.5"), new BigDecimal(weightKg), "", reason);
    }

    @Test
    @DisplayName("HLT-08: nhiệt độ gồm 2 chữ số nguyên, tối đa 2 chữ số sau dấu phẩy (nháp và kết luận)")
    void temperatureFormatIsCheckedOnDraftAndConclude() {
        long campaign = examCampaign(campusA);
        for (String bad : List.of("3115345", "9.5", "36.555")) {
            HealthExamForm form = withTemperature(HealthExamCatalog.template(), new BigDecimal(bad));
            assertThatThrownBy(() -> exams.saveExam(teacherA, campaign, child1, save(form, false, "")))
                    .as("nháp " + bad)
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getFieldErrors())
                            .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                            .contains(org.assertj.core.groups.Tuple.tuple(
                                    "exam.vitals.temperatureC",
                                    "Nhập nhiệt độ gồm 2 chữ số, tối đa 2 chữ số sau dấu phẩy (vd 36,5)")));
        }
        // Kết luận 33.5: chỉ còn lỗi khoảng như cũ
        HealthExamForm low = withTemperature(exam("NO", null, normal()), new BigDecimal("33.5"));
        assertThatThrownBy(() -> exams.saveExam(teacherA, campaign, child1, save(low, true, "")))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getFieldErrors())
                        .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                        .contains(org.assertj.core.groups.Tuple.tuple(
                                "exam.vitals.temperatureC", "Nhập nhiệt độ từ 34 đến 42 °C")));

        HealthExamResponse one = exams.saveExam(
                teacherA,
                campaign,
                child1,
                save(withTemperature(HealthExamCatalog.template(), new BigDecimal("36.5")), false, ""));
        assertThat(one.measurement().exam().vitals().temperatureC()).isEqualByComparingTo("36.5");
        HealthExamResponse two = exams.saveExam(
                teacherA,
                campaign,
                child1,
                save(withTemperature(HealthExamCatalog.template(), new BigDecimal("36.55")), false, ""));
        assertThat(two.measurement().exam().vitals().temperatureC()).isEqualByComparingTo("36.55");
        assertThat(exams.getExam(teacherA, campaign, child1)
                        .measurement()
                        .exam()
                        .vitals()
                        .temperatureC())
                .isEqualByComparingTo("36.55");
    }

    private static HealthExamForm withTemperature(HealthExamForm f, BigDecimal temperature) {
        return withVitals(f, new HealthExamForm.HealthExamVitals(temperature, null, null, null, null, null));
    }

    private static HealthExamRequest save(HealthExamForm exam, boolean conclude, String reason) {
        return save(exam, conclude, reason, null);
    }

    // Ngày khám hệ thống ghi ở lần lưu đầu
    private static HealthExamRequest save(
            HealthExamForm exam, boolean conclude, String reason, HealthConclusionMode mode) {
        return new HealthExamRequest(
                new BigDecimal("98.5"), new BigDecimal("15.2"), new BigDecimal("48.5"), exam, conclude, reason, mode);
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

    private List<String> noticesOf(long userId) {
        entityManager.flush();
        return jdbc.queryForList("SELECT type FROM notifications WHERE user_id = ? ORDER BY id", String.class, userId);
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
