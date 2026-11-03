package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthCheckClassResponse;
import com.carenest.dto.health.HealthCheckCloseResponse;
import com.carenest.dto.health.HealthCheckReopenRequest;
import com.carenest.dto.health.HealthCheckRequest;
import com.carenest.dto.health.HealthCheckResponse;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.health.HealthScheduleWarningResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthCheckCampaignHistory;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckCampaignHistoryRepository;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthCheckService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthCheckAction;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckResult;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthScheduleWarning;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Đợt khám, kỳ cân đo, số đo trong kỳ (plan docs/plans/completed/2026-10-31-health-check-rounds.md). Dữ liệu giả. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class HealthCheckIntegrationTest {

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
    private HealthMeasurementService measurements;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HealthCheckCampaignRepository campaignRepository;

    @Autowired
    private HealthCheckCampaignHistoryRepository historyRepository;

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
        long classB = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campusB + ", " + year + ", 'Lớp B', " + ag3 + ") RETURNING id");
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
        expectError(
                () -> checks.reopenHealthCheck(principal, first.id(), new HealthCheckReopenRequest("Nhập bù")),
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
                () -> measurements.createMeasurement(teacherA, child1, measure(round.id(), "2026-11-08")),
                ApiCode.HEALTH_MEASUREMENT_DATE_OUTSIDE);
        expectError(
                () -> measurements.createMeasurement(teacherA, child1, measure(round.id(), "2026-11-11")),
                ApiCode.HEALTH_MEASUREMENT_DATE_OUTSIDE);
        expectError(
                () -> measurements.createMeasurement(teacherA, child1, measure(null, "2026-11-10")),
                ApiCode.HEALTH_ROUND_REQUIRED);
        expectError(
                () -> measurements.createMeasurement(teacherB, child1, measure(round.id(), "2026-11-10")),
                ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);

        HealthMeasurementResponse saved =
                measurements.createMeasurement(teacherA, child1, measure(round.id(), "2026-11-10"));
        assertThat(saved.campaignKind()).isEqualTo(HealthCheckKind.MEASUREMENT);
        assertThat(saved.source()).isEqualTo(HealthMeasurementResponse.SOURCE_MEASUREMENT_ONLY);
        assertThat(saved.published()).isTrue();
        assertThat(saved.headCircumferenceCm()).isEqualByComparingTo("48.5");
        assertThat(saved.bmi()).isEqualByComparingTo("15.7");
        expectError(
                () -> measurements.createMeasurement(teacherA, child1, measure(round.id(), "2026-11-09")),
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
        // Vòng đầu đo khi trẻ chưa quá 60 tháng: tới trước ngày đủ 61 tháng (sinh 2023-01-01)
        assertThat(record.headCircumferenceUntil()).isEqualTo(LocalDate.of(2028, 1, 31));
        expectError(() -> measurements.getHealthRecord(vpB, child1), ApiCode.HEALTH_RECORD_ACCESS_DENIED);

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
                () -> measurements.updateMeasurement(
                        teacherA, child1, saved.id(), edit("2026-11-10", "99.0", "Đo lại")),
                ApiCode.HEALTH_ROUND_NOT_OPEN);
        checks.reopenHealthCheck(vpA, round.id(), new HealthCheckReopenRequest("Nhập bù cho trẻ vắng"));
        assertThat(checks.getHealthCheckClass(teacherA, round.id(), classA).children())
                .filteredOn(c -> c.childId().equals(child2))
                .extracting(HealthCheckClassResponse.HealthCheckChild::result)
                .containsExactly(HealthCheckResult.NOT_RECORDED);
    }

    @Test
    void editingMeasurementNeedsReasonAndKeepsHistory() {
        HealthCheckResponse round =
                historicalCampaign(vpA, request(HealthCheckKind.MEASUREMENT, campusA, "2026-11-09", "2026-11-20"));
        HealthMeasurementResponse saved =
                measurements.createMeasurement(teacherA, child1, measure(round.id(), "2026-11-10"));

        expectError(
                () -> measurements.updateMeasurement(teacherA, child1, saved.id(), edit("2026-11-10", "99.0", " ")),
                ApiCode.HEALTH_EDIT_REASON_REQUIRED);
        HealthMeasurementResponse unchanged =
                measurements.updateMeasurement(teacherA, child1, saved.id(), edit("2026-11-10", "98.5", "Không đổi"));
        assertThat(unchanged.edits()).isEmpty();
        HealthMeasurementResponse edited =
                measurements.updateMeasurement(teacherA, child1, saved.id(), edit("2026-11-10", "99.0", " Đo lại "));
        assertThat(edited.heightCm()).isEqualByComparingTo("99.0");
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
                () -> measurements.updateMeasurement(teacherB, child1, saved.id(), edit("2026-11-10", "97.0", "Sửa")),
                ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);
    }

    @Test
    void automaticallyClosesExpiredExamAndReleasesCampusOnlyOnce() {
        HealthCheckResponse expired =
                historicalCampaign(principal, request(HealthCheckKind.EXAM, campusA, "2026-11-07", "2026-11-09"));

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
                });
        assertThat(checks.getHealthCheckClass(teacherA, expired.id(), classA).canRecord())
                .isFalse();
        assertThat(checks.getHealthCheckClass(teacherA, expired.id(), classA).children())
                .extracting(HealthCheckClassResponse.HealthCheckChild::result)
                .containsOnly(HealthCheckResult.ABSENT);
        expectError(
                () -> checks.updateHealthCheck(
                        vpA, expired.id(), request(HealthCheckKind.EXAM, campusA, "2026-11-07", "2026-11-11")),
                ApiCode.HEALTH_CHECK_ENDED);

        checks.closeExpiredHealthChecks();
        campaignRepository.flush();
        assertThat(checks.getHealthCheck(vpA, expired.id()).campaign().history())
                .filteredOn(h -> h.action() == HealthCheckAction.CLOSED)
                .hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM health_check_absences WHERE campaign_id = ?", Long.class, expired.id()))
                .isEqualTo(2L);
        assertThat(checks.createHealthCheck(vpA, request(HealthCheckKind.EXAM, campusA, "2026-11-10", "2026-11-12"))
                        .status())
                .isEqualTo(HealthCheckStatus.IN_PROGRESS);
    }

    @Test
    void doesNotAutoCloseOnEndDateOrForExpiredMeasurementRound() {
        HealthCheckResponse exam =
                checks.createHealthCheck(principal, request(HealthCheckKind.EXAM, campusA, "2026-11-10", "2026-11-10"));
        HealthCheckResponse round = historicalCampaign(
                principal, request(HealthCheckKind.MEASUREMENT, campusB, "2026-11-07", "2026-11-09"));

        checks.closeExpiredHealthChecks();

        for (Long id : Set.of(exam.id(), round.id())) {
            HealthCheckResponse unchanged = checks.getHealthCheck(principal, id).campaign();
            assertThat(unchanged.status()).isEqualTo(HealthCheckStatus.IN_PROGRESS);
            assertThat(unchanged.closedAt()).isNull();
            assertThat(unchanged.history()).hasSize(1);
        }
    }

    @Test
    void doesNotTouchCancelledOrManuallyClosedExam() {
        HealthCheckResponse cancelled =
                historicalCampaign(principal, request(HealthCheckKind.EXAM, campusA, "2026-11-07", "2026-11-09"));
        HealthCheckResponse closed =
                historicalCampaign(principal, request(HealthCheckKind.EXAM, campusB, "2026-11-07", "2026-11-09"));
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

    @Test
    void reopenedExamClosesAgainAfterItsNewEndDate() {
        HealthCheckResponse expired =
                historicalCampaign(principal, request(HealthCheckKind.EXAM, campusA, "2026-11-07", "2026-11-09"));
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

    @Test
    void reopeningExpiredExamRequiresAnEndDateFromToday() {
        HealthCheckResponse expired =
                historicalCampaign(principal, request(HealthCheckKind.EXAM, campusA, "2026-11-07", "2026-11-09"));
        checks.closeExpiredHealthChecks();

        for (HealthCheckReopenRequest invalid : new HealthCheckReopenRequest[] {
            new HealthCheckReopenRequest("Nhập bù"), new HealthCheckReopenRequest("Nhập bù", LocalDate.of(2026, 11, 9))
        }) {
            assertThatThrownBy(() -> checks.reopenHealthCheck(vpA, expired.id(), invalid))
                    .isInstanceOfSatisfying(GlobalException.class, ex -> {
                        assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                        assertThat(ex.getFieldErrors())
                                .extracting(com.carenest.dto.common.FieldErrorResponse::field)
                                .contains("endDate");
                    });
        }
        assertThat(checks.getHealthCheck(vpA, expired.id()).campaign().status()).isEqualTo(HealthCheckStatus.CLOSED);
    }

    // Đợt lịch sử là dữ liệu đã có; API tạo mới chỉ nhận ngày từ hôm nay.
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

    private static HealthMeasurementRequest measure(Long campaignId, String date) {
        return new HealthMeasurementRequest(
                campaignId,
                LocalDate.parse(date),
                new BigDecimal("98.5"),
                new BigDecimal("15.2"),
                new BigDecimal("48.5"),
                "Cân đo tại lớp (giả)",
                null);
    }

    private static HealthMeasurementRequest edit(String date, String height, String reason) {
        return new HealthMeasurementRequest(
                null,
                LocalDate.parse(date),
                new BigDecimal(height),
                new BigDecimal("15.2"),
                new BigDecimal("48.5"),
                "Cân đo tại lớp (giả)",
                reason);
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
