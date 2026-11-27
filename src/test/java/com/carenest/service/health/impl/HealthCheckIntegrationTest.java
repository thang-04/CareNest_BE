package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthCheckClassResponse;
import com.carenest.dto.health.HealthCheckCloseResponse;
import com.carenest.dto.health.HealthCheckDetailResponse;
import com.carenest.dto.health.HealthCheckReopenRequest;
import com.carenest.dto.health.HealthCheckRequest;
import com.carenest.dto.health.HealthCheckResponse;
import com.carenest.dto.health.HealthExamRequest;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.health.HealthScheduleWarningResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthCheckCampaignHistory;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckCampaignHistoryRepository;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthCheckService;
import com.carenest.service.health.HealthExamService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthCheckAction;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckResult;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthScheduleWarning;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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

/** Đợt khám, kỳ cân đo, số đo trong kỳ (plan docs/plans/completed/2026-10-31-health-check-rounds.md). Dữ liệu giả. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class HealthCheckIntegrationTest {

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
    private HealthMeasurementService measurements;

    @Autowired
    private HealthExamService exams;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HealthCheckCampaignRepository campaignRepository;

    @Autowired
    private HealthCheckCampaignHistoryRepository historyRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private HealthCheckRules rules;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private long campusA;
    private long campusB;
    private long classA;
    private long classB;
    private long child1;
    private long child2;

    @BeforeEach
    void setUp() {
        testClock.setInstant(Instant.parse("2026-11-10T02:00:00Z"));
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('HC-A', 'Điểm A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('HC-B', 'Điểm B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        classB = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusB
                + ", " + year + ", 'Lớp B', " + ag3 + ") RETURNING id");
        child1 = child("An", "2023-01-01");
        child2 = child("Bình", "2023-02-01");
        enroll(child1, classA);
        enroll(child2, classA);

        principal = user("hc-principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("hc-vpa", CareNestRole.VICE_PRINCIPAL);
        vpB = user("hc-vpb", CareNestRole.VICE_PRINCIPAL);
        campusRole(vpA, campusA);
        campusRole(vpB, campusB);
        teacherA = user("hc-teachera", CareNestRole.TEACHER);
        teacherB = user("hc-teacherb", CareNestRole.TEACHER);
        teach(teacherA, classA);
        teach(teacherB, classB);
    }

    @Test
    void leadersManageTheirCampusesAndTeachersOnlyView() {
        HealthCheckResponse round = checks.createHealthCheck(
                vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2026-11-20"));
        assertThat(round.kind()).isEqualTo(HealthCheckKind.MEASUREMENT);
        assertThat(round.examiner()).isEmpty();
        assertThat(round.status()).isEqualTo(HealthCheckStatus.IN_PROGRESS);
        assertThat(round.campusName()).isEqualTo("Điểm A");
        expectError(
                () -> checks.createHealthCheck(vpA, request(HealthCheckKind.EXAM, campusB, "2026-11-09", "2026-11-12")),
                ApiCode.HEALTH_CHECK_ACCESS_DENIED);
        expectError(
                () -> checks.createHealthCheck(
                        teacherA, request(HealthCheckKind.EXAM, campusA, "2026-11-09", "2026-11-12")),
                ApiCode.HEALTH_CHECK_ACCESS_DENIED);
        HealthCheckResponse exam =
                checks.createHealthCheck(principal, request(null, campusB, "2026-11-10", "2026-11-12"));
        assertThat(exam.kind()).isEqualTo(HealthCheckKind.EXAM);
        assertThat(exam.examiner()).isEqualTo("Trạm Y tế (giả)");

        assertThat(checks.listHealthChecks(vpA, null, null, null, null))
                .extracting(HealthCheckResponse::id)
                .containsExactly(round.id());
        assertThat(checks.listHealthChecks(teacherA, null, null, null, null))
                .extracting(HealthCheckResponse::id)
                .containsExactly(round.id());
        assertThat(checks.listHealthChecks(principal, null, HealthCheckKind.EXAM, null, null))
                .extracting(HealthCheckResponse::id)
                .containsExactly(exam.id());
        expectError(() -> checks.getHealthCheck(vpB, round.id()), ApiCode.HEALTH_CHECK_NOT_FOUND);
        expectError(() -> checks.cancelHealthCheck(teacherA, round.id()), ApiCode.HEALTH_CHECK_ACCESS_DENIED);

        HealthCheckResponse renamed = checks.updateHealthCheck(
                vpA, round.id(), request(HealthCheckKind.EXAM, campusB, "2026-11-09", "2026-11-21"));
        assertThat(renamed.kind()).isEqualTo(HealthCheckKind.MEASUREMENT);
        assertThat(renamed.campusId()).isEqualTo(campusA);
        assertThat(renamed.startDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(renamed.endDate()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(renamed.history())
                .extracting(HealthCheckResponse.HealthCheckHistory::action)
                .extracting(Enum::name)
                .containsExactly("CREATED", "UPDATED");
    }

    @Test
    void warnsWhenStartIsWithinTwoMonthsFifteenDaysOfAnotherRound() {
        HealthCheckResponse first = historicalCampaign(
                principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-09-01", "2026-09-05"));
        checks.closeHealthCheck(principal, first.id());
        HealthCheckResponse cancelled = checks.createHealthCheck(
                principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-12-20", "2026-12-22"));
        checks.cancelHealthCheck(principal, cancelled.id());

        HealthScheduleWarningResponse close =
                checks.getScheduleWarnings(vpA, campusA, HealthCheckKind.MEASUREMENT, LocalDate.of(2026, 11, 15), null);
        assertThat(close.warnings())
                .extracting(
                        HealthScheduleWarningResponse.HealthScheduleWarningItem::code,
                        HealthScheduleWarningResponse.HealthScheduleWarningItem::campaignId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(HealthScheduleWarning.TOO_CLOSE, first.id()));
        assertThat(checks.getScheduleWarnings(
                                vpA, campusA, HealthCheckKind.MEASUREMENT, LocalDate.of(2026, 11, 16), null)
                        .warnings())
                .as("đúng ngưỡng 2 tháng 15 ngày thì không cảnh báo; đợt đã hủy không tính")
                .isEmpty();
        assertThat(checks.getScheduleWarnings(
                                vpA, campusA, HealthCheckKind.MEASUREMENT, LocalDate.of(2026, 6, 18), null)
                        .warnings())
                .as("đợt ở sau cũng tính")
                .hasSize(1);
        assertThat(checks.getScheduleWarnings(
                                vpA, campusA, HealthCheckKind.MEASUREMENT, LocalDate.of(2026, 9, 10), first.id())
                        .warnings())
                .as("sửa thì bỏ qua chính nó")
                .isEmpty();
        expectError(
                () -> checks.getScheduleWarnings(
                        vpB, campusA, HealthCheckKind.MEASUREMENT, LocalDate.of(2026, 11, 15), null),
                ApiCode.HEALTH_CHECK_ACCESS_DENIED);
    }

    @Test
    void secondExamInSchoolYearWarnsButCanBeCreated() {
        HealthCheckResponse exam =
                historicalCampaign(principal, request(HealthCheckKind.EXAM, campusA, "2026-09-15", "2026-09-18"));
        checks.closeHealthCheck(principal, exam.id());

        HealthScheduleWarningResponse second =
                checks.getScheduleWarnings(principal, campusA, HealthCheckKind.EXAM, LocalDate.of(2027, 3, 1), null);
        assertThat(second.warnings())
                .extracting(HealthScheduleWarningResponse.HealthScheduleWarningItem::code)
                .containsExactly(HealthScheduleWarning.EXAM_IN_SCHOOL_YEAR);
        assertThat(checks.getScheduleWarnings(
                                principal, campusA, HealthCheckKind.MEASUREMENT, LocalDate.of(2027, 3, 1), null)
                        .warnings())
                .isEmpty();
        assertThat(checks.createHealthCheck(
                                principal, request(HealthCheckKind.EXAM, campusA, "2027-03-01", "2027-03-03"))
                        .status())
                .isEqualTo(HealthCheckStatus.UPCOMING);
    }

    @Test
    void oneUnclosedRoundPerCampusAcrossKinds() {
        HealthCheckResponse first = historicalCampaign(
                principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-01", "2026-11-05"));
        expectError(
                () -> checks.createHealthCheck(vpA, request(HealthCheckKind.EXAM, campusA, "2026-11-20", "2026-11-21")),
                ApiCode.HEALTH_CHECK_CAMPUS_BUSY);
        checks.closeHealthCheck(principal, first.id());
        checks.createHealthCheck(vpA, request(HealthCheckKind.EXAM, campusA, "2026-11-20", "2026-11-21"));
        // Kỳ đã hết hạn mở lại cần ngày kết thúc mới (HLT-17); ngày hợp lệ vẫn vướng đợt khác chưa đóng
        expectError(
                () -> checks.reopenHealthCheck(
                        principal, first.id(), new HealthCheckReopenRequest("Nhập bù", LocalDate.of(2026, 11, 10))),
                ApiCode.HEALTH_CHECK_CAMPUS_BUSY);
    }

    @Test
    void teacherRecordsMeasurementsOnlyInsideOpenRound() {
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"));
        HealthCheckClassResponse before = checks.getHealthCheckClass(teacherA, round.id(), classA);
        assertThat(before.canRecord()).isTrue();
        assertThat(before.children())
                .extracting(HealthCheckClassResponse.HealthCheckChild::result)
                .containsOnly(HealthCheckResult.NOT_RECORDED);
        assertThat(checks.getHealthCheckClass(vpA, round.id(), classA).canRecord())
                .isFalse();

        expectError(
                () -> measurements.createMeasurement(teacherA, child1, measure(null)), ApiCode.HEALTH_ROUND_REQUIRED);
        expectError(
                () -> measurements.createMeasurement(teacherB, child1, measure(round.id())),
                ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);

        HealthMeasurementResponse saved = measurements.createMeasurement(teacherA, child1, measure(round.id()));
        assertThat(saved.campaignKind()).isEqualTo(HealthCheckKind.MEASUREMENT);
        assertThat(saved.source()).isEqualTo(HealthMeasurementResponse.SOURCE_MEASUREMENT_ONLY);
        assertThat(saved.published()).isTrue();
        // HLT-01 (user chốt 2026-11-04): ngày đo = ngày nhập, kỳ cân đo không có vòng đầu
        assertThat(saved.date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(saved.headCircumferenceCm()).isNull();
        assertThat(saved.bmi()).isEqualByComparingTo("15.7");
        expectError(
                () -> measurements.createMeasurement(teacherA, child1, measure(round.id())),
                ApiCode.HEALTH_MEASUREMENT_EXISTS);

        HealthCheckClassResponse after = checks.getHealthCheckClass(teacherA, round.id(), classA);
        HealthCheckClassResponse.HealthCheckChild row = after.children().stream()
                .filter(c -> c.childId().equals(child1))
                .findFirst()
                .orElseThrow();
        assertThat(row.result()).isEqualTo(HealthCheckResult.RECORDED);
        assertThat(row.heightCm()).isEqualByComparingTo("98.5");
        assertThat(after.campaign().hasResults()).isTrue();
        expectError(() -> checks.cancelHealthCheck(vpA, round.id()), ApiCode.HEALTH_CHECK_HAS_RESULTS);
        expectError(
                () -> checks.updateHealthCheck(
                        vpA, round.id(), request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-11", "2026-11-20")),
                ApiCode.HEALTH_CHECK_RESULT_OUTSIDE_DATES);

        ChildHealthRecordResponse record = measurements.getHealthRecord(teacherA, child1);
        assertThat(record.measurements())
                .extracting(HealthMeasurementResponse::id)
                .containsExactly(saved.id());
        assertThat(record.openCampaign().kind()).isEqualTo(HealthCheckKind.MEASUREMENT);
        assertThat(record.openCampaign().resultStatus()).isEqualTo("RECORDED");
        // AUTH-20 (user chốt 2026-11-15): PHT điểm trường khác xem được sổ sức khỏe (chỉ đọc)
        assertThat(measurements.getHealthRecord(vpB, child1).measurements())
                .extracting(HealthMeasurementResponse::id)
                .containsExactly(saved.id());

        HealthCheckCloseResponse closed = checks.closeHealthCheck(vpA, round.id());
        assertThat(closed.absentCount()).isEqualTo(1);
        assertThat(checks.getHealthCheckClass(teacherA, round.id(), classA).children())
                .extracting(
                        HealthCheckClassResponse.HealthCheckChild::childId,
                        HealthCheckClassResponse.HealthCheckChild::result)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(child1, HealthCheckResult.RECORDED),
                        org.assertj.core.groups.Tuple.tuple(child2, HealthCheckResult.ABSENT));
        expectError(
                () -> measurements.updateMeasurement(teacherA, child1, saved.id(), edit("99.0", "Đo lại")),
                ApiCode.HEALTH_ROUND_NOT_OPEN);
        checks.reopenHealthCheck(vpA, round.id(), new HealthCheckReopenRequest("Nhập bù cho trẻ vắng"));
        assertThat(checks.getHealthCheckClass(teacherA, round.id(), classA).children())
                .filteredOn(c -> c.childId().equals(child2))
                .extracting(HealthCheckClassResponse.HealthCheckChild::result)
                .containsExactly(HealthCheckResult.NOT_RECORDED);
    }

    @Test
    @DisplayName("HLT-01: kỳ chưa bắt đầu ⇒ không nhập (5018), không lưu số đo mang ngày tương lai")
    void upcomingRoundSavesNothing() {
        HealthCheckResponse round = checks.createHealthCheck(
                vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-11", "2026-11-20"));
        assertThat(round.status()).isEqualTo(HealthCheckStatus.UPCOMING);

        expectError(
                () -> measurements.createMeasurement(teacherA, child1, measure(round.id())),
                ApiCode.HEALTH_ROUND_NOT_OPEN);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM health_measurements WHERE campaign_id = ?", Long.class, round.id()))
                .isZero();
    }

    @Test
    @DisplayName("HLT-01: ngày đo không vượt ngày kết thúc khi kỳ quá hạn chưa được tự đóng; trùng ngày ⇒ báo HT/PHT")
    void measurementDateIsEntryDateClampedToRound() {
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-01", "2026-11-09"));
        // Chốt chặn: job tự đóng (HLT-17) chưa chạy nên kỳ quá hạn còn mở ⇒ ngày đo kẹp về ngày kết thúc 09/11
        assertThat(round.status()).isEqualTo(HealthCheckStatus.IN_PROGRESS);
        HealthMeasurementResponse late = measurements.createMeasurement(teacherA, child1, measure(round.id()));
        assertThat(late.date()).isEqualTo(LocalDate.of(2026, 11, 9));

        // HT/PHT sửa ngày kết thúc (vẫn sửa được) ⇒ lần nhập mới ghi hôm nay; lần đo đã có giữ ngày cũ
        checks.updateHealthCheck(
                vpA, round.id(), request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-01", "2026-11-12"));
        HealthMeasurementResponse onTime = measurements.createMeasurement(teacherA, child2, measure(round.id()));
        assertThat(onTime.date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(measurements.getHealthRecord(teacherA, child1).measurements())
                .extracting(HealthMeasurementResponse::date)
                .containsExactly(LocalDate.of(2026, 11, 9));

        // Kỳ đóng sáng nay, kỳ mới mở cùng ngày: ngày đo trùng kết quả đã có ⇒ GV không chọn được ngày khác, báo HT/PHT
        checks.closeHealthCheck(vpA, round.id());
        HealthCheckResponse next = checks.createHealthCheck(
                vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2026-11-20"));
        assertThatThrownBy(() -> measurements.createMeasurement(teacherA, child2, measure(next.id())))
                .isInstanceOf(GlobalException.class)
                .hasMessage("Ngày đo 10/11/2026 trẻ đã có kết quả đo khác. Báo Hiệu trưởng hoặc Phó hiệu trưởng")
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.HEALTH_MEASUREMENT_DATE_TAKEN);
        assertThat(measurements
                        .createMeasurement(teacherA, child1, measure(next.id()))
                        .date())
                .isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    @DisplayName(
            "HLT-01 (2026-11-24): ngày đo trẻ chỉ có phiếu khám ⇒ vẫn nhập số đo; đã có số đo kỳ khác ⇒ báo HT/PHT")
    void roundMeasurementAllowedOnDayWithOnlyExam() {
        long examCampaign = checks.createHealthCheck(
                        vpA, request(HealthCheckKind.EXAM, campusA, "2026-11-10", "2026-11-20"))
                .id();
        exams.saveExam(
                teacherA,
                examCampaign,
                child1,
                new HealthExamRequest(
                        new BigDecimal("98.0"),
                        new BigDecimal("15.0"),
                        null,
                        HealthExamCatalog.template(),
                        false,
                        "",
                        null));
        checks.closeHealthCheck(vpA, examCampaign);
        long round = checks.createHealthCheck(
                        vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2026-11-20"))
                .id();

        HealthMeasurementResponse measured = measurements.createMeasurement(teacherA, child1, measure(round));
        assertThat(measured.date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(rowOf(checks.getHealthCheckClass(teacherA, round, classA), child1)
                        .result())
                .isEqualTo(HealthCheckResult.RECORDED);

        // Kỳ cân đo khác mở cùng ngày: ngày đó đã có số đo kỳ cân đo (cùng phiếu khám) ⇒ vẫn chặn
        checks.closeHealthCheck(vpA, round);
        long next = checks.createHealthCheck(
                        vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2026-11-20"))
                .id();
        expectError(
                () -> measurements.createMeasurement(teacherA, child1, measure(next)),
                ApiCode.HEALTH_MEASUREMENT_DATE_TAKEN);
    }

    @Test
    @DisplayName(
            "HLT-13: sửa số đo phải có lý do, lưu lịch sử; chỉ đổi chiều cao, cân nặng, ghi chú, giữ ngày đo và vòng đầu")
    void editingMeasurementNeedsReasonAndKeepsHistory() {
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"));
        HealthMeasurementResponse saved = measurements.createMeasurement(teacherA, child1, measure(round.id()));
        // Số đo cũ (trước 04/11/2026) có vòng đầu: sửa không đụng tới; sửa ngày khác vẫn giữ ngày đo
        entityManager.flush();
        jdbc.update("UPDATE health_measurements SET head_circumference_cm = 48.5 WHERE id = ?", saved.id());
        entityManager.clear();
        testClock.setInstant(Instant.parse("2026-11-12T02:00:00Z"));

        expectError(
                () -> measurements.updateMeasurement(teacherA, child1, saved.id(), edit("99.0", " ")),
                ApiCode.HEALTH_EDIT_REASON_REQUIRED);
        HealthMeasurementResponse unchanged =
                measurements.updateMeasurement(teacherA, child1, saved.id(), edit("98.5", "Không đổi"));
        assertThat(unchanged.edits()).isEmpty();
        HealthMeasurementResponse edited =
                measurements.updateMeasurement(teacherA, child1, saved.id(), edit("99.0", " Đo lại "));
        assertThat(edited.heightCm()).isEqualByComparingTo("99.0");
        assertThat(edited.date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(edited.headCircumferenceCm()).isEqualByComparingTo("48.5");
        assertThat(edited.edits()).singleElement().satisfies(e -> {
            assertThat(e.reason()).isEqualTo("Đo lại");
            assertThat(e.userName()).isEqualTo("hc-teachera");
            assertThat(e.changes())
                    .singleElement()
                    .extracting(
                            HealthMeasurementResponse.HealthMeasurementChange::field,
                            HealthMeasurementResponse.HealthMeasurementChange::from,
                            HealthMeasurementResponse.HealthMeasurementChange::to)
                    .containsExactly("heightCm", "98.5", "99.0");
        });
        expectError(
                () -> measurements.updateMeasurement(teacherB, child1, saved.id(), edit("97.0", "Sửa")),
                ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);

        // Job tự đóng chưa chạy nên kỳ quá hạn còn mở: vẫn sửa được, ngày đo giữ nguyên
        testClock.setInstant(Instant.parse("2026-11-21T02:00:00Z"));
        HealthMeasurementResponse late =
                measurements.updateMeasurement(teacherA, child1, saved.id(), edit("99.5", "Đo lại lần hai (giả)"));
        assertThat(late.heightCm()).isEqualByComparingTo("99.5");
        assertThat(late.date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(late.edits()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({
        "EXAM, Hệ thống tự đóng khi qua ngày kết thúc khám",
        "MEASUREMENT, Hệ thống tự đóng khi qua ngày kết thúc cân đo"
    })
    @DisplayName("HLT-17: đợt khám, kỳ cân đo quá hạn tự đóng một lần, trẻ chưa có kết quả thành vắng, nhả điểm trường")
    void automaticallyClosesExpiredCampaignAndReleasesCampusOnlyOnce(HealthCheckKind kind, String reason) {
        HealthCheckResponse expired = historicalCampaign(principal, request(kind, campusA, "2026-11-07", "2026-11-09"));

        checks.closeExpiredHealthChecks();
        HealthCheckResponse closed = checks.getHealthCheck(vpA, expired.id()).campaign();
        assertThat(closed.status()).isEqualTo(HealthCheckStatus.CLOSED);
        assertThat(closed.closedAt()).isEqualTo(Instant.parse("2026-11-10T02:00:00Z"));
        assertThat(closed.closedBy()).isNull();
        assertThat(closed.closedByName()).isEqualTo("Hệ thống");
        assertThat(closed.progress().absent()).isEqualTo(2);
        assertThat(closed.history())
                .filteredOn(h -> h.action() == HealthCheckAction.CLOSED)
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.userId()).isNull();
                    assertThat(h.userName()).isEqualTo("Hệ thống");
                    assertThat(h.reason()).isEqualTo(reason);
                });
        assertThat(checks.getHealthCheckClass(teacherA, expired.id(), classA).canRecord())
                .isFalse();
        assertThat(checks.getHealthCheckClass(teacherA, expired.id(), classA).children())
                .extracting(HealthCheckClassResponse.HealthCheckChild::result)
                .containsOnly(HealthCheckResult.ABSENT);
        expectError(
                () -> checks.updateHealthCheck(vpA, expired.id(), request(kind, campusA, "2026-11-07", "2026-11-11")),
                ApiCode.HEALTH_CHECK_ENDED);

        checks.closeExpiredHealthChecks();
        campaignRepository.flush();
        assertThat(checks.getHealthCheck(vpA, expired.id()).campaign().history())
                .filteredOn(h -> h.action() == HealthCheckAction.CLOSED)
                .hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM health_check_absences WHERE campaign_id = ?", Long.class, expired.id()))
                .isEqualTo(2L);
        assertThat(checks.createHealthCheck(vpA, request(kind, campusA, "2026-11-10", "2026-11-12"))
                        .status())
                .isEqualTo(HealthCheckStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(HealthCheckKind.class)
    @DisplayName("HLT-17: đợt khám, kỳ cân đo kết thúc hôm nay chưa tự đóng")
    void doesNotAutoCloseOnEndDate(HealthCheckKind kind) {
        HealthCheckResponse campaign =
                checks.createHealthCheck(principal, request(kind, campusA, "2026-11-10", "2026-11-10"));

        checks.closeExpiredHealthChecks();

        HealthCheckResponse unchanged =
                checks.getHealthCheck(principal, campaign.id()).campaign();
        assertThat(unchanged.status()).isEqualTo(HealthCheckStatus.IN_PROGRESS);
        assertThat(unchanged.closedAt()).isNull();
        assertThat(unchanged.history()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(HealthCheckKind.class)
    @DisplayName("HLT-17: không đụng đợt khám, kỳ cân đo đã hủy hoặc đã đóng tay (đóng sớm vẫn được)")
    void doesNotTouchCancelledOrManuallyClosedCampaign(HealthCheckKind kind) {
        HealthCheckResponse cancelled =
                historicalCampaign(principal, request(kind, campusA, "2026-11-07", "2026-11-09"));
        HealthCheckResponse closed = historicalCampaign(principal, request(kind, campusB, "2026-11-07", "2026-11-09"));
        checks.cancelHealthCheck(principal, cancelled.id());
        HealthCheckResponse manual =
                checks.closeHealthCheck(principal, closed.id()).campaign();

        checks.closeExpiredHealthChecks();

        HealthCheckResponse cancelledAfter =
                checks.getHealthCheck(principal, cancelled.id()).campaign();
        assertThat(cancelledAfter.status()).isEqualTo(HealthCheckStatus.CANCELLED);
        assertThat(cancelledAfter.closedAt()).isNull();
        assertThat(cancelledAfter.history())
                .extracting(HealthCheckResponse.HealthCheckHistory::action)
                .containsExactly(HealthCheckAction.CREATED, HealthCheckAction.CANCELLED);
        HealthCheckResponse manualAfter =
                checks.getHealthCheck(principal, closed.id()).campaign();
        assertThat(manualAfter.closedBy()).isEqualTo(principal.userId());
        assertThat(manualAfter.closedAt()).isEqualTo(manual.closedAt());
        assertThat(manualAfter.history()).isEqualTo(manual.history());
    }

    @ParameterizedTest
    @EnumSource(HealthCheckKind.class)
    @DisplayName("HLT-17: đợt khám, kỳ cân đo mở lại với ngày kết thúc mới lại tự đóng sau ngày đó")
    void reopenedCampaignClosesAgainAfterItsNewEndDate(HealthCheckKind kind) {
        HealthCheckResponse expired = historicalCampaign(principal, request(kind, campusA, "2026-11-07", "2026-11-09"));
        checks.closeExpiredHealthChecks();
        checks.reopenHealthCheck(
                vpA, expired.id(), new HealthCheckReopenRequest("Nhập bù cho trẻ vắng", LocalDate.of(2026, 11, 10)));

        checks.closeExpiredHealthChecks();

        HealthCheckResponse reopened = checks.getHealthCheck(vpA, expired.id()).campaign();
        assertThat(reopened.status()).isEqualTo(HealthCheckStatus.IN_PROGRESS);
        assertThat(reopened.closedAt()).isNull();
        assertThat(reopened.endDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(reopened.progress().notRecorded()).isEqualTo(2);
        assertThat(reopened.history())
                .extracting(HealthCheckResponse.HealthCheckHistory::action)
                .containsExactly(HealthCheckAction.CREATED, HealthCheckAction.CLOSED, HealthCheckAction.REOPENED);

        testClock.setInstant(Instant.parse("2026-11-10T17:00:00Z"));
        checks.closeExpiredHealthChecks();
        checks.closeExpiredHealthChecks();

        HealthCheckResponse closedAgain =
                checks.getHealthCheck(vpA, expired.id()).campaign();
        assertThat(closedAgain.status()).isEqualTo(HealthCheckStatus.CLOSED);
        assertThat(closedAgain.closedAt()).isEqualTo(Instant.parse("2026-11-10T17:00:00Z"));
        assertThat(closedAgain.closedBy()).isNull();
        assertThat(closedAgain.progress().absent()).isEqualTo(2);
        assertThat(closedAgain.history())
                .extracting(HealthCheckResponse.HealthCheckHistory::action)
                .containsExactly(
                        HealthCheckAction.CREATED,
                        HealthCheckAction.CLOSED,
                        HealthCheckAction.REOPENED,
                        HealthCheckAction.CLOSED);
    }

    @ParameterizedTest
    @EnumSource(HealthCheckKind.class)
    @DisplayName("HLT-17: mở lại đợt khám, kỳ cân đo đã hết hạn bắt buộc ngày kết thúc mới từ hôm nay")
    void reopeningExpiredCampaignRequiresAnEndDateFromToday(HealthCheckKind kind) {
        HealthCheckResponse expired = historicalCampaign(principal, request(kind, campusA, "2026-11-07", "2026-11-09"));
        checks.closeExpiredHealthChecks();

        for (HealthCheckReopenRequest invalid : new HealthCheckReopenRequest[] {
            new HealthCheckReopenRequest("Nhập bù"), new HealthCheckReopenRequest("Nhập bù", LocalDate.of(2026, 11, 9))
        }) {
            assertThatThrownBy(() -> checks.reopenHealthCheck(vpA, expired.id(), invalid))
                    .isInstanceOfSatisfying(GlobalException.class, ex -> {
                        assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                        assertThat(ex.getFieldErrors())
                                .extracting(
                                        com.carenest.dto.common.FieldErrorResponse::field,
                                        com.carenest.dto.common.FieldErrorResponse::message)
                                .containsExactly(tuple("endDate", "Chọn ngày kết thúc mới từ hôm nay"));
                    });
        }
        assertThat(checks.getHealthCheck(vpA, expired.id()).campaign().status()).isEqualTo(HealthCheckStatus.CLOSED);

        HealthCheckResponse reopened = checks.reopenHealthCheck(
                vpA, expired.id(), new HealthCheckReopenRequest("Nhập bù", LocalDate.of(2026, 11, 10)));
        assertThat(reopened.status()).isEqualTo(HealthCheckStatus.IN_PROGRESS);
        assertThat(reopened.endDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    @DisplayName(
            "HLT-17: kỳ cân đo tự đóng sau ngày kết thúc ⇒ trẻ chưa cân thành vắng, không nhập, không sửa số đo (5018)")
    void expiredMeasurementRoundClosesAndRejectsEntry() {
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-01", "2026-11-09"));
        // An cân ngày 09/11, ngày cuối của kỳ
        testClock.setInstant(Instant.parse("2026-11-09T02:00:00Z"));
        HealthMeasurementResponse saved = measurements.createMeasurement(teacherA, child1, measure(round.id()));
        assertThat(saved.date()).isEqualTo(LocalDate.of(2026, 11, 9));
        testClock.setInstant(Instant.parse("2026-11-10T02:00:00Z"));

        checks.closeExpiredHealthChecks();

        HealthCheckResponse closed = checks.getHealthCheck(vpA, round.id()).campaign();
        assertThat(closed.status()).isEqualTo(HealthCheckStatus.CLOSED);
        assertThat(closed.progress().recorded()).isEqualTo(1);
        assertThat(closed.progress().absent()).isEqualTo(1);
        assertThat(checks.getHealthCheckClass(teacherA, round.id(), classA).children())
                .extracting(
                        HealthCheckClassResponse.HealthCheckChild::childId,
                        HealthCheckClassResponse.HealthCheckChild::result)
                .containsExactlyInAnyOrder(
                        tuple(child1, HealthCheckResult.RECORDED), tuple(child2, HealthCheckResult.ABSENT));
        expectError(
                () -> measurements.createMeasurement(teacherA, child2, measure(round.id())),
                ApiCode.HEALTH_ROUND_NOT_OPEN);
        expectError(
                () -> measurements.updateMeasurement(teacherA, child1, saved.id(), edit("99.0", "Đo lại (giả)")),
                ApiCode.HEALTH_ROUND_NOT_OPEN);
        assertThat(measurements.getHealthRecord(teacherA, child1).openCampaign())
                .isNull();
    }

    @Test
    @DisplayName(
            "HLT-17: kỳ cân đo kết thúc hôm nay vẫn nhập được, ngày đo là hôm nay; qua 00:00 giờ trường thì tự đóng")
    void measurementRoundEndingTodayAcceptsEntryUntilMidnight() {
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-01", "2026-11-10"));
        checks.closeExpiredHealthChecks();

        HealthMeasurementResponse saved = measurements.createMeasurement(teacherA, child1, measure(round.id()));
        assertThat(saved.date()).isEqualTo(LocalDate.of(2026, 11, 10));

        // 00:00 ngày 11/11 ở trường; ngày UTC vẫn là 10/11
        testClock.setInstant(Instant.parse("2026-11-10T17:00:00Z"));
        checks.closeExpiredHealthChecks();
        assertThat(checks.getHealthCheck(vpA, round.id()).campaign().status()).isEqualTo(HealthCheckStatus.CLOSED);
        expectError(
                () -> measurements.createMeasurement(teacherA, child2, measure(round.id())),
                ApiCode.HEALTH_ROUND_NOT_OPEN);
    }

    // G94-180/HLT-08 (user chốt 2026-11-22, cách B): năm của đợt lưu lúc tạo = năm đang hoạt động
    private long activeYearId() {
        return jdbc.queryForObject("SELECT id FROM school_years WHERE status = 'ACTIVE'", Long.class);
    }

    // Giả kích hoạt năm mới: năm cũ đóng, năm mới (bắt đầu 05/11/2026) hoạt động và có lớp ở điểm trường A
    private long activateYearStartingNov5() {
        jdbc.update("UPDATE school_years SET status = 'CLOSED' WHERE status = 'ACTIVE'");
        long next = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2026-11-05', '2027-05-31', 'ACTIVE') RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA + ", "
                + next + ", 'Lớp A năm mới', " + ag3 + ") RETURNING id");
        // Năm học đã nạp vào persistence context của transaction test trước khi đổi trạng thái bằng SQL
        entityManager.flush();
        entityManager.clear();
        return next;
    }

    @Test
    @DisplayName("G94-180/HLT-08: đợt lưu năm đang hoạt động lúc tạo; kích hoạt năm mới vẫn giữ lớp của năm đó")
    void campaignKeepsItsYearAfterActivation() {
        long year = activeYearId();
        HealthCheckResponse round = checks.createHealthCheck(
                principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2026-11-20"));
        assertThat(jdbc.queryForObject(
                        "SELECT school_year_id FROM health_check_campaigns WHERE id = ?", Long.class, round.id()))
                .isEqualTo(year);

        activateYearStartingNov5();

        assertThat(checks.getHealthCheck(principal, round.id()).classes())
                .extracting(c -> c.className())
                .containsExactly("Lớp A");
    }

    @Test
    @DisplayName("G94-180/HLT-08: lọc đợt theo năm học dùng năm đã lưu")
    void listFiltersByStoredYear() {
        long year = activeYearId();
        HealthCheckResponse round = checks.createHealthCheck(
                principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2026-11-20"));
        long next = activateYearStartingNov5();

        assertThat(checks.listHealthChecks(principal, null, null, null, year))
                .extracting(HealthCheckResponse::id)
                .contains(round.id());
        assertThat(checks.listHealthChecks(principal, null, null, null, next))
                .extracting(HealthCheckResponse::id)
                .doesNotContain(round.id());
    }

    @Test
    @DisplayName("HLT-08: cảnh báo đã có đợt khám trong năm so theo năm đã lưu của đợt cũ")
    void examWarningComparesStoredYears() {
        checks.createHealthCheck(principal, request(HealthCheckKind.EXAM, campusA, "2026-11-10", "2026-11-12"));
        activateYearStartingNov5();

        assertThat(checks.getScheduleWarnings(principal, campusA, HealthCheckKind.EXAM, LocalDate.of(2027, 2, 10), null)
                        .warnings())
                .extracting(w -> w.code())
                .doesNotContain(HealthScheduleWarning.EXAM_IN_SCHOOL_YEAR);
    }

    @Test
    @DisplayName("G94-180/HLT-08: tạo đợt có ngày ngoài năm đang hoạt động ⇒ lỗi ô ngày")
    void createRejectsDatesOutsideActiveYear() {
        insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2027-2028', '2027-09-01', '2028-05-31') RETURNING id");
        expectField(
                () -> checks.createHealthCheck(
                        principal, request(HealthCheckKind.EXAM, campusA, "2027-08-30", "2027-09-02")),
                "endDate",
                "Ngày khám phải trong năm học 2026-2027 (01/09/2026 – 31/08/2027)");

        testClock.setInstant(Instant.parse("2026-08-25T02:00:00Z"));
        expectField(
                () -> checks.createHealthCheck(
                        principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-08-26", "2026-09-03")),
                "startDate",
                "Ngày cân đo phải trong năm học 2026-2027 (01/09/2026 – 31/08/2027)");
    }

    @Test
    @DisplayName("G94-180/HLT-08: sửa đợt sang ngày ngoài năm đã lưu ⇒ lỗi ô ngày")
    void updateRejectsDatesOutsideStoredYear() {
        HealthCheckResponse round = checks.createHealthCheck(
                principal, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2026-11-20"));
        insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2027-2028', '2027-09-01', '2028-05-31') RETURNING id");

        expectField(
                () -> checks.updateHealthCheck(
                        principal,
                        round.id(),
                        request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-10", "2027-09-05")),
                "endDate",
                "Ngày cân đo phải trong năm học 2026-2027 (01/09/2026 – 31/08/2027)");
    }

    // ---------- G94-180/HLT-09 (user chốt 2026-11-24, Q2 = A): trẻ chuyển lớp khi kỳ đang mở ----------

    @Test
    @DisplayName(
            "G94-180/HLT-09: chuyển lớp trong kỳ ⇒ lớp mới thấy số đo, sửa được; lớp cũ chỉ xem, không tính tiến độ")
    void transferWithinRoundMovesEditingToNewClass() {
        long classA2 = classroom(campusA, "Lớp A2");
        CurrentUser teacherA2 = user("hc-teachera2", CareNestRole.TEACHER);
        teach(teacherA2, classA2);
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"));
        HealthMeasurementResponse saved = measurements.createMeasurement(teacherA, child1, measure(round.id()));
        // An cân ngày 10/11 ở Lớp A, chuyển sang Lớp A2 hôm nay (12/11)
        testClock.setInstant(Instant.parse("2026-11-12T02:00:00Z"));
        transfer(child1, classA, classA2, "2026-11-12");

        HealthCheckClassResponse.HealthCheckChild inNew =
                rowOf(checks.getHealthCheckClass(teacherA2, round.id(), classA2), child1);
        assertThat(inNew.result()).isEqualTo(HealthCheckResult.RECORDED);
        assertThat(inNew.measurementId()).isEqualTo(saved.id());
        assertThat(inNew.canEdit()).isTrue();
        assertThat(inNew.movedTo()).isNull();
        assertThat(inNew.measuredIn())
                .isEqualTo(new HealthCheckClassResponse.MeasuredIn("Lớp A", "Điểm A", LocalDate.of(2026, 11, 10)));

        HealthCheckClassResponse old = checks.getHealthCheckClass(teacherA, round.id(), classA);
        HealthCheckClassResponse.HealthCheckChild inOld = rowOf(old, child1);
        assertThat(inOld.result()).isEqualTo(HealthCheckResult.RECORDED);
        assertThat(inOld.movedTo())
                .isEqualTo(new HealthCheckClassResponse.MovedTo("Lớp A2", "Điểm A", LocalDate.of(2026, 11, 12)));
        assertThat(inOld.canEdit()).isFalse();
        assertThat(inOld.measuredIn()).isNull();
        // Bình còn ở Lớp A, chưa cân: GV Lớp A vẫn nhập cho Bình
        assertThat(rowOf(old, child2).canEdit()).isTrue();

        HealthCheckDetailResponse detail = checks.getHealthCheck(vpA, round.id());
        assertThat(detail.classes())
                .extracting(
                        HealthCheckDetailResponse.HealthCheckClassProgress::className,
                        HealthCheckDetailResponse.HealthCheckClassProgress::total,
                        HealthCheckDetailResponse.HealthCheckClassProgress::recorded,
                        HealthCheckDetailResponse.HealthCheckClassProgress::notRecorded)
                .containsExactly(tuple("Lớp A", 1, 0, 1), tuple("Lớp A2", 1, 1, 0));
        assertThat(detail.campaign().progress().total()).isEqualTo(2);
        assertThat(detail.campaign().progress().recorded()).isEqualTo(1);
        assertThat(checks.listHealthChecks(principal, campusA, null, null, null))
                .contains(detail.campaign());
    }

    @Test
    @DisplayName(
            "G94-180/HLT-09: chuyển điểm trường ⇒ kỳ cùng loại, cùng năm, giao ngày ở điểm trường mới có số đo đã đo")
    void transferAcrossCampusesShowsMeasurementFromLinkedRound() {
        LinkedTransfer t = measuredAtCampusBThenMovedToA();

        HealthCheckClassResponse.HealthCheckChild inNew =
                rowOf(checks.getHealthCheckClass(teacherA, t.roundA(), classA), t.child());
        assertThat(inNew.result()).isEqualTo(HealthCheckResult.RECORDED);
        assertThat(inNew.measurementId()).isEqualTo(t.measurementId());
        assertThat(inNew.heightCm()).isEqualByComparingTo("98.5");
        assertThat(inNew.canEdit()).isTrue();
        assertThat(inNew.measuredIn())
                .isEqualTo(new HealthCheckClassResponse.MeasuredIn("Lớp B", "Điểm B", LocalDate.of(2026, 11, 10)));
        HealthCheckResponse roundA = checks.getHealthCheck(vpA, t.roundA()).campaign();
        assertThat(checks.listHealthChecks(principal, campusA, null, null, null))
                .contains(roundA);
        assertThat(roundA.progress().total()).isEqualTo(3);
        assertThat(roundA.progress().recorded()).isEqualTo(1);
        // Số đo vẫn thuộc kỳ của điểm B: kỳ điểm A chưa có kết quả của mình (hủy được)
        assertThat(roundA.hasResults()).isFalse();

        HealthCheckClassResponse.HealthCheckChild inOld =
                rowOf(checks.getHealthCheckClass(teacherB, t.roundB(), classB), t.child());
        assertThat(inOld.movedTo())
                .isEqualTo(new HealthCheckClassResponse.MovedTo("Lớp A", "Điểm A", LocalDate.of(2026, 11, 12)));
        assertThat(inOld.canEdit()).isFalse();
        HealthCheckResponse roundB = checks.getHealthCheck(vpB, t.roundB()).campaign();
        assertThat(checks.listHealthChecks(principal, campusB, null, null, null))
                .contains(roundB);
        assertThat(roundB.progress().total()).isZero();
        assertThat(roundB.hasResults()).isTrue();

        // Sổ sức khỏe: kỳ đang mở ở điểm A tính là đã cân, không mời nhập lần hai
        ChildHealthRecordResponse.HealthOpenCampaign open =
                measurements.getHealthRecord(teacherA, t.child()).openCampaign();
        assertThat(open.id()).isEqualTo(t.roundA());
        assertThat(open.resultMeasurementId()).isEqualTo(t.measurementId());
        assertThat(open.resultStatus()).isEqualTo("RECORDED");
    }

    @Test
    @DisplayName(
            "G94-180/HLT-09, HLT-13: GV lớp mới sửa số đo đo ở lớp cũ ⇒ thành người đo, có lịch sử, số đo ở lại nơi đo")
    void newClassTeacherEditsMovedMeasurement() {
        LinkedTransfer t = measuredAtCampusBThenMovedToA();

        HealthMeasurementResponse edited = measurements.updateMeasurement(
                teacherA, t.child(), t.measurementId(), edit("99.0", "Đo lại ở lớp mới (giả)"));

        assertThat(edited.heightCm()).isEqualByComparingTo("99.0");
        assertThat(edited.measuredBy()).isEqualTo(teacherA.userId());
        assertThat(edited.date()).isEqualTo(LocalDate.of(2026, 11, 10));
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM health_measurement_edits WHERE measurement_id = ? AND user_id = ?",
                        Long.class,
                        t.measurementId(),
                        teacherA.userId()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForMap(
                        "SELECT campaign_id, classroom_id, measured_by FROM health_measurements WHERE id = ?",
                        t.measurementId()))
                .containsEntry("campaign_id", t.roundB())
                .containsEntry("classroom_id", classB)
                .containsEntry("measured_by", teacherA.userId());
    }

    @Test
    @DisplayName("G94-180/HLT-09: GV lớp cũ không sửa được số đo của trẻ đã chuyển lớp (5023)")
    void oldClassTeacherCannotEditMovedMeasurement() {
        LinkedTransfer t = measuredAtCampusBThenMovedToA();

        expectError(
                () -> measurements.updateMeasurement(teacherB, t.child(), t.measurementId(), edit("99.0", "Sửa (giả)")),
                ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);
    }

    @Test
    @DisplayName(
            "G94-180/HLT-09: đã đo ở kỳ liên quan ⇒ không nhập lần hai ở kỳ điểm trường mới (5019, câu nêu kỳ, ngày đo)")
    void secondMeasurementInLinkedRoundRejected() {
        LinkedTransfer t = measuredAtCampusBThenMovedToA();

        assertThatThrownBy(() -> measurements.createMeasurement(teacherA, t.child(), measure(t.roundA())))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.HEALTH_MEASUREMENT_EXISTS);
                    assertThat(ex.getFieldErrors())
                            .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                            .containsExactly(tuple(
                                    "campaignId",
                                    "Trẻ đã có số đo trong kỳ «Cân đo tháng 11 điểm B (giả)» (Điểm B) ngày 10/11/2026."
                                            + " Sửa số đo đó."));
                });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM health_measurements WHERE child_id = ?", Long.class, t.child()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName(
            "G94-180/HLT-09: lịch chuyển lớp hẹn ngày ⇒ tới ngày chuyển lớp mới nhận số đo; trước đó lớp cũ vẫn sửa")
    void scheduledTransferKeepsOldClassUntilEffectiveDate() {
        long classA2 = classroom(campusA, "Lớp A2");
        CurrentUser teacherA2 = user("hc-teachera2", CareNestRole.TEACHER);
        teach(teacherA2, classA2);
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"));
        HealthMeasurementResponse saved = measurements.createMeasurement(teacherA, child1, measure(round.id()));
        // Hôm nay 10/11 hẹn chuyển An sang Lớp A2 từ 13/11
        transfer(child1, classA, classA2, "2026-11-13");

        HealthCheckClassResponse.HealthCheckChild before =
                rowOf(checks.getHealthCheckClass(teacherA, round.id(), classA), child1);
        assertThat(before.movedTo()).isNull();
        assertThat(before.canEdit()).isTrue();
        assertThat(checks.getHealthCheckClass(teacherA2, round.id(), classA2).children())
                .isEmpty();
        expectError(
                () -> measurements.updateMeasurement(teacherA2, child1, saved.id(), edit("99.0", "Sửa (giả)")),
                ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);
        assertThat(measurements
                        .updateMeasurement(teacherA, child1, saved.id(), edit("99.0", "Đo lại (giả)"))
                        .measuredBy())
                .isEqualTo(teacherA.userId());

        testClock.setInstant(Instant.parse("2026-11-13T02:00:00Z"));
        assertThat(rowOf(checks.getHealthCheckClass(teacherA, round.id(), classA), child1)
                        .movedTo())
                .isEqualTo(new HealthCheckClassResponse.MovedTo("Lớp A2", "Điểm A", LocalDate.of(2026, 11, 13)));
        assertThat(rowOf(checks.getHealthCheckClass(teacherA2, round.id(), classA2), child1)
                        .canEdit())
                .isTrue();
        expectError(
                () -> measurements.updateMeasurement(teacherA, child1, saved.id(), edit("99.5", "Sửa (giả)")),
                ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);
        assertThat(measurements
                        .updateMeasurement(teacherA2, child1, saved.id(), edit("99.5", "Đo lại ở lớp mới (giả)"))
                        .measuredBy())
                .isEqualTo(teacherA2.userId());
    }

    @Test
    @DisplayName("G94-180/HLT-09: GV dạy thay lớp mới sửa được số đo của trẻ chuyển đến, thành người đo")
    void substituteOfNewClassEditsMovedMeasurement() {
        LinkedTransfer t = measuredAtCampusBThenMovedToA();
        CurrentUser substitute = user("hc-substitute", CareNestRole.TEACHER);
        substitute(substitute, classA, "2026-11-12", "2026-11-14");

        assertThat(rowOf(checks.getHealthCheckClass(substitute, t.roundA(), classA), t.child())
                        .canEdit())
                .isTrue();
        HealthMeasurementResponse edited =
                measurements.updateMeasurement(substitute, t.child(), t.measurementId(), edit("99.0", "Đo lại (giả)"));
        assertThat(edited.measuredBy()).isEqualTo(substitute.userId());
        assertThat(edited.edits())
                .singleElement()
                .satisfies(e -> assertThat(e.userId()).isEqualTo(substitute.userId()));
    }

    // ---------- HLT-01 (user chốt 2026-11-24): trẻ vắng theo điểm danh ngày đo ----------

    @Test
    @DisplayName("HLT-01: thứ 2–6 trẻ điểm danh vắng (có phép, không phép) ⇒ không nhập số đo mới; dòng có absentToday")
    void absentChildCannotBeMeasuredOnSchoolDay() {
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"));
        long child3 = child("Cường", "2023-03-01");
        enroll(child3, classA);
        // Hôm nay thứ 3 10/11: An vắng không phép, Cường nghỉ có phép, Bình có mặt
        attendance(child1, "2026-11-10", "UNEXCUSED");
        attendance(child3, "2026-11-10", "EXCUSED");
        attendance(child2, "2026-11-10", "PRESENT");

        for (long absent : new long[] {child1, child3}) {
            assertThatThrownBy(() -> measurements.createMeasurement(teacherA, absent, measure(round.id())))
                    .isInstanceOfSatisfying(GlobalException.class, ex -> {
                        assertThat(ex.getApiCode()).isEqualTo(ApiCode.HEALTH_CHILD_ABSENT);
                        assertThat(ex.getMessage())
                                .isEqualTo("Trẻ vắng ngày 10/11/2026 (điểm danh). Nhập khi trẻ có mặt.");
                    });
        }
        HealthCheckClassResponse roster = checks.getHealthCheckClass(teacherA, round.id(), classA);
        assertThat(roster.children())
                .extracting(
                        HealthCheckClassResponse.HealthCheckChild::childId,
                        HealthCheckClassResponse.HealthCheckChild::absentToday)
                .containsExactlyInAnyOrder(tuple(child1, true), tuple(child2, false), tuple(child3, true));
        assertThat(measurements
                        .createMeasurement(teacherA, child2, measure(round.id()))
                        .date())
                .isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM health_measurements WHERE campaign_id = ?", Long.class, round.id()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("HLT-01: thứ 7, chủ nhật không xét điểm danh ⇒ nhập được số đo")
    void weekendMeasurementAllowedWithoutAttendance() {
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"));
        // Thứ 7 14/11: An không có điểm danh; Bình có dòng vắng (dữ liệu lạ) vẫn không chặn
        testClock.setInstant(Instant.parse("2026-11-14T02:00:00Z"));
        attendance(child2, "2026-11-14", "UNEXCUSED");

        assertThat(checks.getHealthCheckClass(teacherA, round.id(), classA).children())
                .extracting(HealthCheckClassResponse.HealthCheckChild::absentToday)
                .containsOnly(false);
        assertThat(measurements
                        .createMeasurement(teacherA, child1, measure(round.id()))
                        .date())
                .isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(measurements
                        .createMeasurement(teacherA, child2, measure(round.id()))
                        .date())
                .isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    @DisplayName("HLT-01: ngày học chưa điểm danh ⇒ nhập được số đo, dòng không đánh dấu vắng")
    void unmarkedAttendanceAllowsMeasurement() {
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"));
        // An có dòng vắng hôm qua, hôm nay (thứ 3) chưa điểm danh
        attendance(child1, "2026-11-09", "UNEXCUSED");

        assertThat(rowOf(checks.getHealthCheckClass(teacherA, round.id(), classA), child1)
                        .absentToday())
                .isFalse();
        assertThat(measurements
                        .createMeasurement(teacherA, child1, measure(round.id()))
                        .date())
                .isEqualTo(LocalDate.of(2026, 11, 10));
    }

    private void attendance(long childId, String date, String status) {
        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, recorded_by,"
                        + " recorded_at) VALUES (?, ?, ?, ?, ?, ?, now())",
                childId,
                classA,
                campusA,
                LocalDate.parse(date),
                status,
                teacherA.userId());
    }

    private record LinkedTransfer(long roundA, long roundB, long child, long measurementId) {}

    // Kỳ cân đo cùng ngày ở hai điểm trường; Cường cân ngày 10/11 ở Lớp B (điểm B), chuyển sang Lớp A (điểm A) từ 12/11
    private LinkedTransfer measuredAtCampusBThenMovedToA() {
        long roundA = historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"))
                .id();
        long roundB = historicalCampaign(
                        vpB,
                        new HealthCheckRequest(
                                HealthCheckKind.MEASUREMENT,
                                "Cân đo tháng 11 điểm B (giả)",
                                campusB,
                                LocalDate.of(2026, 11, 9),
                                LocalDate.of(2026, 11, 20),
                                "",
                                ""))
                .id();
        long child = child("Cường", "2023-03-01");
        enroll(child, classB);
        long measurementId =
                measurements.createMeasurement(teacherB, child, measure(roundB)).id();
        testClock.setInstant(Instant.parse("2026-11-12T02:00:00Z"));
        transfer(child, classB, classA, "2026-11-12");
        return new LinkedTransfer(roundA, roundB, child, measurementId);
    }

    private long classroom(long campusId, String name) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) SELECT " + campusId
                + ", id, '" + name + "', (SELECT id FROM age_groups WHERE code = 'ag-3') FROM school_years"
                + " WHERE status = 'ACTIVE' RETURNING id");
    }

    // Chuyển lớp từ ngày date: lớp cũ học tới hết ngày trước đó (như xếp lớp, G94-180/CHD-14)
    private void transfer(long childId, long fromClassId, long toClassId, String date) {
        entityManager.flush();
        LocalDate from = LocalDate.parse(date);
        jdbc.update(
                "UPDATE enrollments SET valid_to = ? WHERE child_id = ? AND classroom_id = ? AND valid_to IS NULL",
                from.minusDays(1),
                childId,
                fromClassId);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, ?)",
                childId,
                toClassId,
                from);
        entityManager.clear();
    }

    private void substitute(CurrentUser user, long classroomId, String from, String to) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_substitute)"
                        + " VALUES (?, 'TEACHER', ?, ?, ?, TRUE)",
                user.userId(),
                classroomId,
                LocalDate.parse(from),
                LocalDate.parse(to));
    }

    private static HealthCheckClassResponse.HealthCheckChild rowOf(HealthCheckClassResponse roster, long childId) {
        return roster.children().stream()
                .filter(c -> c.childId().equals(childId))
                .findFirst()
                .orElseThrow();
    }

    private static void expectField(ThrowingCallable call, String field, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlobalException.class, ex -> {
            assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
            assertThat(ex.getFieldErrors()).anySatisfy(e -> {
                assertThat(e.field()).isEqualTo(field);
                assertThat(e.message()).isEqualTo(message);
            });
        });
    }

    // Đợt lịch sử là dữ liệu đã có; API tạo mới chỉ nhận ngày từ hôm nay.
    @Test
    void linkedCampaignBatchMatchesSingleLookupAndUsesOneQuery() {
        long year = jdbc.queryForObject("SELECT school_year_id FROM classrooms WHERE id = ?", Long.class, classA);
        HealthCheckCampaign a =
                batchCampaign(HealthCheckKind.MEASUREMENT, campusA, year, "2026-11-01", "2026-11-15", false);
        HealthCheckCampaign overlap =
                batchCampaign(HealthCheckKind.MEASUREMENT, campusB, year, "2026-11-15", "2026-11-25", false);
        HealthCheckCampaign legacy =
                batchCampaign(HealthCheckKind.MEASUREMENT, campusB, null, "2026-11-03", "2026-11-05", false);
        batchCampaign(HealthCheckKind.MEASUREMENT, campusA, year, "2026-11-01", "2026-11-15", false);
        batchCampaign(HealthCheckKind.MEASUREMENT, campusB, year, "2026-11-01", "2026-11-15", true);
        batchCampaign(HealthCheckKind.EXAM, campusB, year, "2026-11-01", "2026-11-15", false);
        batchCampaign(HealthCheckKind.MEASUREMENT, campusB, year, "2026-12-01", "2026-12-05", false);
        long nextYear = insertId(
                "INSERT INTO school_years (name, start_date, end_date) VALUES ('2027-2028', '2027-09-01', '2028-05-31') RETURNING id");
        batchCampaign(HealthCheckKind.MEASUREMENT, campusB, nextYear, "2026-11-01", "2026-11-15", false);
        entityManager.flush();
        var sources = List.of(a, overlap, legacy);
        var batched = rules.linkedCampaigns(sources);
        for (HealthCheckCampaign source : sources) {
            assertThat(batched.get(source.getId()))
                    .extracting(HealthCheckCampaign::getId)
                    .containsExactlyInAnyOrderElementsOf(rules.linkedCampaigns(source).stream()
                            .map(HealthCheckCampaign::getId)
                            .toList());
        }
        assertThat(batched.get(a.getId()))
                .extracting(HealthCheckCampaign::getId)
                .containsExactlyInAnyOrder(overlap.getId(), legacy.getId());
    }

    @Test
    void linkedCampaignCandidateQueryCountDoesNotGrowWithSourceRounds() {
        long year = jdbc.queryForObject("SELECT school_year_id FROM classrooms WHERE id = ?", Long.class, classA);
        var rounds = new java.util.ArrayList<HealthCheckCampaign>();
        for (int i = 0; i < 8; i++) {
            rounds.add(batchCampaign(
                    HealthCheckKind.MEASUREMENT,
                    i % 2 == 0 ? campusA : campusB,
                    year,
                    "2026-11-01",
                    "2026-11-20",
                    false));
        }
        entityManager.flush();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        stats.clear();
        rules.linkedCampaigns(rounds.subList(0, 1));
        long one = stats.getPrepareStatementCount();
        entityManager.clear();
        stats.clear();
        rules.linkedCampaigns(rounds);
        assertThat(one).isEqualTo(1);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(one);
        stats.clear();
        rounds.forEach(rules::linkedCampaigns);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(8);
        stats.clear();
        rules.linkedCampaigns(List.<HealthCheckCampaign>of());
        assertThat(stats.getPrepareStatementCount()).isZero();
    }

    @Test
    void rosterBatchQueriesOncePerReferenceDateAndKeepsTransferredRows() {
        long year = jdbc.queryForObject("SELECT school_year_id FROM classrooms WHERE id = ?", Long.class, classA);
        long newClass = classroom(campusA, "Lớp lô mới");
        transfer(child1, classA, newClass, "2026-11-09");
        var campaigns = new java.util.ArrayList<HealthCheckCampaign>();
        var linked = new java.util.HashMap<Long, List<HealthCheckCampaign>>();
        var measured = new java.util.ArrayList<HealthMeasurement>();
        for (int i = 0; i < 8; i++) {
            HealthCheckCampaign campaign =
                    batchCampaign(HealthCheckKind.MEASUREMENT, campusA, year, "2026-11-01", "2026-11-20", false);
            campaigns.add(campaign);
            linked.put(campaign.getId(), List.of());
            measured.add(batchMeasurement(campaign, child1, "2026-11-02"));
        }
        HealthCheckCampaign past =
                batchCampaign(HealthCheckKind.MEASUREMENT, campusA, year, "2026-11-01", "2026-11-05", false);
        HealthCheckCampaign future =
                batchCampaign(HealthCheckKind.MEASUREMENT, campusA, year, "2026-12-01", "2026-12-05", false);
        entityManager.flush();
        entityManager.clear();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        rules.rostersOf(campaigns.subList(0, 1), linked, measured, List.of());
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        entityManager.clear();
        stats.clear();
        var batched = rules.rostersOf(campaigns, linked, measured, List.of());
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        stats.clear();
        campaigns.forEach(c -> rules.rosterOf(c, List.of(), measured, List.of()));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(8);
        campaigns.add(past);
        campaigns.add(future);
        linked.put(past.getId(), List.of());
        linked.put(future.getId(), List.of());
        measured.add(batchMeasurement(past, child1, "2026-11-02"));
        measured.add(batchMeasurement(future, child2, "2026-12-01"));
        entityManager.clear();
        stats.clear();
        batched = rules.rostersOf(campaigns, linked, measured, List.of());
        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
        for (HealthCheckCampaign campaign : campaigns) {
            var original = rules.rosterOf(campaign, List.of(), measured, List.of());
            for (var cls : rules.visibleClassesOf(principal, campaign)) {
                assertThat(batched.get(campaign.getId()).rowsOf(cls)).isEqualTo(original.rowsOf(cls));
            }
        }
        var currentClasses = rules.visibleClassesOf(principal, campaigns.getFirst());
        var oldClass = currentClasses.stream()
                .filter(c -> c.id().equals(classA))
                .findFirst()
                .orElseThrow();
        var destination = currentClasses.stream()
                .filter(c -> c.id().equals(newClass))
                .findFirst()
                .orElseThrow();
        assertThat(batched.get(campaigns.getFirst().getId())
                        .rowsOf(oldClass)
                        .getFirst()
                        .movedTo())
                .isNotNull();
        assertThat(batched.get(campaigns.getFirst().getId())
                        .rowsOf(destination)
                        .getFirst()
                        .measuredIn())
                .isNotNull();
        assertThat(batched.get(past.getId()).rowsOf(oldClass).getFirst().movedTo())
                .isNull();
        stats.clear();
        rules.rostersOf(campaigns, linked, List.of(), List.of());
        assertThat(stats.getPrepareStatementCount()).isZero();
    }

    private HealthMeasurement batchMeasurement(HealthCheckCampaign campaign, long childId, String date) {
        return HealthMeasurement.builder()
                .childId(childId)
                .campaignId(campaign.getId())
                .classroomId(classA)
                .date(LocalDate.parse(date))
                .heightCm(new BigDecimal("98.5"))
                .weightKg(new BigDecimal("15.2"))
                .note("")
                .measuredBy(teacherA.userId())
                .createdAt(testClock.instant())
                .build();
    }

    private HealthCheckCampaign batchCampaign(
            HealthCheckKind kind, long campus, Long year, String start, String end, boolean cancelled) {
        HealthCheckCampaign c = HealthCheckCampaign.builder()
                .kind(kind)
                .campusId(campus)
                .schoolYearId(year)
                .name("Đợt kiểm thử lô")
                .startDate(LocalDate.parse(start))
                .endDate(LocalDate.parse(end))
                .examiner("")
                .note("")
                .createdBy(principal.userId())
                .createdAt(testClock.instant())
                .build();
        if (cancelled) c.cancel(testClock.instant());
        else c.close(principal.userId(), testClock.instant());
        return campaignRepository.save(c);
    }

    private HealthCheckResponse historicalCampaign(CurrentUser creator, HealthCheckRequest request) {
        Instant createdAt =
                request.startDate().atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        HealthCheckCampaign campaign = campaignRepository.save(HealthCheckCampaign.builder()
                .kind(request.kind())
                .campusId(request.campusId())
                .name(request.name())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .examiner(request.examiner())
                .note(request.note())
                .createdBy(creator.userId())
                .createdAt(createdAt)
                .build());
        historyRepository.save(HealthCheckCampaignHistory.of(
                campaign.getId(), HealthCheckAction.CREATED, creator.userId(), null, createdAt));
        return checks.getHealthCheck(creator, campaign.getId()).campaign();
    }

    private HealthCheckRequest request(HealthCheckKind kind, long campusId, String start, String end) {
        return new HealthCheckRequest(
                kind,
                kind == HealthCheckKind.MEASUREMENT ? "Cân đo thử (giả)" : "Khám thử (giả)",
                campusId,
                LocalDate.parse(start),
                LocalDate.parse(end),
                "Trạm Y tế (giả)",
                "");
    }

    // Ngày đo do hệ thống ghi (hôm nay), kỳ cân đo không đo vòng đầu
    private static HealthMeasurementRequest measure(Long campaignId) {
        return new HealthMeasurementRequest(
                campaignId, new BigDecimal("98.5"), new BigDecimal("15.2"), "Cân đo tại lớp (giả)", null);
    }

    private static HealthMeasurementRequest edit(String height, String reason) {
        return new HealthMeasurementRequest(
                null, new BigDecimal(height), new BigDecimal("15.2"), "Cân đo tại lớp (giả)", reason);
    }

    private long child(String name, String dateOfBirth) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                + "'HC' || substr(md5(random()::text), 1, 12), '" + name + "', '" + dateOfBirth
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
