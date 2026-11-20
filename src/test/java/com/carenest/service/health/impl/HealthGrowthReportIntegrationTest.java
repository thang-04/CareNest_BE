package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.health.HealthGrowthReportResponse;
import com.carenest.dto.health.HealthGrowthReportResponse.HealthGrowthCampus;
import com.carenest.dto.health.HealthGrowthReportResponse.HealthGrowthClass;
import com.carenest.dto.health.HealthGrowthReportResponse.HealthGrowthCounts;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthCheckCampaignHistory;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckCampaignHistoryRepository;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthCheckService;
import com.carenest.service.health.HealthGrowthReportService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthCheckAction;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthGrowthLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
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
 * Báo cáo tăng trưởng theo tháng, mẫu 5.1 (HLT-20; plan docs/plans/active/2026-11-04-health-growth-report.md). Số đo
 * chọn sẵn bằng bảng WHO, cách ngưỡng ≥ 0,2 SD; dữ liệu giả.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class HealthGrowthReportIntegrationTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    // Hôm nay 2026-10-15 (giờ trường)
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        HealthTestClock fixedClock() {
            return new HealthTestClock(Instant.parse("2026-10-15T02:00:00Z"));
        }
    }

    @Autowired
    private HealthGrowthReportService reports;

    @Autowired
    private HealthCheckService checks;

    @Autowired
    private HealthTestClock testClock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HealthCheckCampaignRepository campaignRepository;

    @Autowired
    private HealthCheckCampaignHistoryRepository historyRepository;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser teacher;
    private long campusA;
    private long campusB;
    private long campusC;
    private long fiveA;
    private long fiveB;
    private long round;
    private long exam;
    private long roundB;
    private long octoberB;

    @BeforeEach
    void setUp() {
        testClock.setInstant(Instant.parse("2026-10-15T02:00:00Z"));
        campusA = insertId(
                "INSERT INTO campuses (code, name, is_main) VALUES ('GR-A', 'Điểm A (giả)', TRUE) RETURNING id");
        campusB = insertId(
                "INSERT INTO campuses (code, name, is_main) VALUES ('GR-B', 'Điểm B (giả)', FALSE) RETURNING id");
        // Điểm trường ngừng hoạt động, không có đợt/kỳ: không hiện trừ khi lọc đúng điểm trường đó
        campusC = insertId("INSERT INTO campuses (code, name, is_main, status) "
                + "VALUES ('GR-C', 'Điểm C ngừng hoạt động (giả)', FALSE, 'INACTIVE') RETURNING id");
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        // Thêm lớp theo thứ tự lộn để kiểm thứ tự trong báo cáo
        long mixed = classroom(campusA, year, "Lớp ghép (giả)", null);
        long nursery = classroom(campusA, year, "Nhà trẻ A", "ag-2");
        long fourA = classroom(campusA, year, "4 tuổi A", "ag-4");
        fiveB = classroom(campusA, year, "5 tuổi B", "ag-5");
        fiveA = classroom(campusA, year, "5 tuổi A", "ag-5");
        long fiveC = classroom(campusB, year, "5 tuổi C", "ag-5");

        principal = user("gr-principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("gr-vpa", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vpA.userId(),
                campusA);
        teacher = user("gr-teacher", CareNestRole.TEACHER);

        // Tuổi tại 10/09/2026: 66 tháng (sinh 10/03/2021), 54 tháng (2022), 30 tháng (2024), 106 tháng (2017)
        long normal = child("Bé thường (giả)", "2021-03-10", "MALE", fiveA);
        long stunted = child("Bé thấp còi (giả)", "2021-03-10", "MALE", fiveA);
        long obese = child("Bé béo phì (giả)", "2021-03-10", "MALE", fiveA);
        child("Bé vắng (giả)", "2021-03-10", "FEMALE", fiveA);
        long overweight = child("Bé thừa cân (giả)", "2021-03-10", "FEMALE", fiveB);
        long underweight = child("Bé nhẹ cân (giả)", "2021-03-10", "MALE", fiveB);
        long draftOnly = child("Bé phiếu nháp (giả)", "2021-03-10", "FEMALE", fiveB);
        long wasted = child("Bé gầy còm (giả)", "2022-03-10", "FEMALE", fourA);
        long examined = child("Bé khám (giả)", "2024-03-10", "MALE", nursery);
        long risk = child("Bé nguy cơ (giả)", "2024-03-10", "FEMALE", nursery);
        long older = child("Bé quá tuổi (giả)", "2017-11-10", "MALE", mixed);
        long campusBChild = child("Bé điểm B (giả)", "2021-03-10", "FEMALE", fiveC);

        // Đợt/kỳ đã hủy trong tháng không tính
        long cancelled = campaign(HealthCheckKind.MEASUREMENT, campusA, "2026-09-01", "2026-09-03");
        checks.cancelHealthCheck(principal, cancelled);
        campaignRepository.flush();

        // Kỳ cân đo tháng 9 điểm A: z ở chú thích là W/A, H/A, BMI theo WHO
        round = campaign(HealthCheckKind.MEASUREMENT, campusA, "2026-09-05", "2026-09-20");
        measure(normal, round, fiveA, "2026-09-10", "112.9", "19.5"); // 0,04 · 0 · 0,03
        measure(stunted, round, fiveA, "2026-09-10", "100.6", "15.4"); // −1,80 · −2,59 · −0,04
        measure(obese, round, fiveA, "2026-09-10", "112.9", "24.9"); // 1,84 · 0 · 2,61 (béo phì từ 61 tháng)
        measure(overweight, round, fiveB, "2026-09-10", "112.2", "22.5"); // 1,07 · 0,01 · 1,48 (thừa cân)
        measure(underweight, round, fiveB, "2026-09-11", "105.8", "14.0"); // −2,58 · −1,50 · −2,55
        measure(wasted, round, fourA, "2026-09-10", "106.2", "13.6"); // −1,68 · 0,01 · −2,64
        measure(examined, round, nursery, "2026-09-10", "91.9", "17.9"); // béo phì, nhưng phiếu khám sau mới hơn
        measure(risk, round, nursery, "2026-09-10", "90.7", "14.6"); // BMI 1,52: nguy cơ thừa cân ⇒ cột BT
        measure(older, round, mixed, "2026-09-10", "130.0", "28.0"); // 106 tháng: không xếp được
        // Đóng kỳ ⇒ trẻ chưa cân (bé vắng, bé phiếu nháp) thành vắng
        checks.closeHealthCheck(principal, round);
        campaignRepository.flush();

        // Đợt khám tháng 9 điểm A: phiếu kết luận tính (mới nhất của bé khám), phiếu nháp không tính
        exam = campaign(HealthCheckKind.EXAM, campusA, "2026-09-12", "2026-09-25");
        examResult(examined, exam, nursery, "2026-09-15", "91.9", "13.3", true); // 0 · −0,01 · −0,04
        examResult(draftOnly, exam, fiveB, "2026-09-14", "112.2", "19.2", false);

        // Điểm B: kỳ tháng 9 (đã đóng) và kỳ tháng 10
        roundB = campaign(HealthCheckKind.MEASUREMENT, campusB, "2026-09-08", "2026-09-15");
        measure(campusBChild, roundB, fiveC, "2026-09-10", "112.2", "19.2"); // 0,03 · 0,01 · 0
        checks.closeHealthCheck(principal, roundB);
        campaignRepository.flush();
        octoberB = campaign(HealthCheckKind.MEASUREMENT, campusB, "2026-10-01", "2026-10-10");
        measure(campusBChild, octoberB, fiveC, "2026-10-05", "112.2", "19.2");
    }

    @Test
    @DisplayName("HLT-20: HT xem mọi điểm trường; đợt/kỳ của tháng, đếm theo lớp, ánh xạ cột, thứ tự lớp, tổng MG/NT")
    void principalSeesEveryCampusForTheMonth() {
        HealthGrowthReportResponse report = reports.getGrowthReport(principal, SEPTEMBER, null);

        assertThat(report.month()).isEqualTo("2026-09");
        assertThat(report.campuses()).extracting(HealthGrowthCampus::campusId).containsExactly(campusA, campusB);
        HealthGrowthCampus a = report.campuses().getFirst();
        assertThat(a.campusName()).isEqualTo("Điểm A (giả)");
        // Kỳ đã hủy không tính; theo ngày bắt đầu
        assertThat(a.campaigns())
                .extracting(c -> c.id(), c -> c.kind(), c -> c.status())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                round, HealthCheckKind.MEASUREMENT, HealthCheckStatus.CLOSED),
                        org.assertj.core.groups.Tuple.tuple(exam, HealthCheckKind.EXAM, HealthCheckStatus.IN_PROGRESS));
        // MG theo nhóm tuổi lớn trước rồi tên, NT, lớp chưa có nhóm tuổi cuối
        assertThat(a.classes())
                .extracting(HealthGrowthClass::className, HealthGrowthClass::level, HealthGrowthClass::ageGroupName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "5 tuổi A", HealthGrowthLevel.PRESCHOOL, ageGroupName("ag-5")),
                        org.assertj.core.groups.Tuple.tuple(
                                "5 tuổi B", HealthGrowthLevel.PRESCHOOL, ageGroupName("ag-5")),
                        org.assertj.core.groups.Tuple.tuple(
                                "4 tuổi A", HealthGrowthLevel.PRESCHOOL, ageGroupName("ag-4")),
                        org.assertj.core.groups.Tuple.tuple(
                                "Nhà trẻ A", HealthGrowthLevel.NURSERY, ageGroupName("ag-2")),
                        org.assertj.core.groups.Tuple.tuple("Lớp ghép (giả)", null, null));
        // enrolled, measured, W/A BT, SDDNC, H/A BT, SDDTC, BMI BT, gầy còm, thừa cân, béo phì, chưa xếp được
        assertThat(a.classes())
                .extracting(HealthGrowthClass::counts)
                .containsExactly(
                        counts(4, 3, 3, 0, 2, 1, 2, 0, 0, 1, 0), // bé vắng không tính là đã cân đo
                        counts(3, 2, 1, 1, 2, 0, 0, 1, 1, 0, 0), // bé chỉ có phiếu nháp không tính
                        counts(1, 1, 1, 0, 1, 0, 0, 1, 0, 0, 0),
                        counts(2, 2, 2, 0, 2, 0, 2, 0, 0, 0, 0), // phiếu khám mới nhất thay số đo béo phì
                        counts(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1));
        assertThat(a.preschool()).isEqualTo(counts(8, 6, 5, 1, 5, 1, 2, 2, 1, 1, 0));
        assertThat(a.nursery()).isEqualTo(counts(2, 2, 2, 0, 2, 0, 2, 0, 0, 0, 0));
        // Tổng điểm trường gồm cả lớp chưa có nhóm tuổi
        assertThat(a.total()).isEqualTo(counts(11, 9, 7, 1, 7, 1, 4, 2, 1, 1, 1));

        HealthGrowthCampus b = report.campuses().get(1);
        assertThat(b.campaigns()).extracting(c -> c.id()).containsExactly(roundB);
        assertThat(b.classes()).singleElement().satisfies(c -> {
            assertThat(c.className()).isEqualTo("5 tuổi C");
            assertThat(c.counts()).isEqualTo(counts(1, 1, 1, 0, 1, 0, 1, 0, 0, 0, 0));
        });
        assertThat(b.nursery()).isEqualTo(counts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        assertThat(report.preschool()).isEqualTo(counts(9, 7, 6, 1, 6, 1, 3, 2, 1, 1, 0));
        assertThat(report.nursery()).isEqualTo(counts(2, 2, 2, 0, 2, 0, 2, 0, 0, 0, 0));
        assertThat(report.total()).isEqualTo(counts(12, 10, 8, 1, 8, 1, 5, 2, 1, 1, 1));
    }

    @Test
    @DisplayName("HLT-20: chọn tháng theo ngày bắt đầu; điểm trường không có đợt/kỳ vẫn trả, số 0")
    void campusWithoutCampaignIsListedWithZeros() {
        HealthGrowthReportResponse report = reports.getGrowthReport(principal, OCTOBER, null);

        HealthGrowthCampus a = report.campuses().getFirst();
        assertThat(a.campusId()).isEqualTo(campusA);
        assertThat(a.campaigns()).isEmpty();
        assertThat(a.classes()).isEmpty();
        assertThat(a.total()).isEqualTo(counts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        HealthGrowthCampus b = report.campuses().get(1);
        assertThat(b.campaigns()).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(octoberB);
            assertThat(c.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(c.endDate()).isEqualTo(LocalDate.of(2026, 10, 10));
        });
        assertThat(b.total()).isEqualTo(counts(1, 1, 1, 0, 1, 0, 1, 0, 0, 0, 0));
        assertThat(report.total()).isEqualTo(counts(1, 1, 1, 0, 1, 0, 1, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("HLT-20: PHT chỉ thấy điểm trường được phân công; điểm trường khác, vai trò khác ⇒ 403")
    void vicePrincipalSeesOnlyAssignedCampus() {
        HealthGrowthReportResponse report = reports.getGrowthReport(vpA, SEPTEMBER, null);
        assertThat(report.campuses()).extracting(HealthGrowthCampus::campusId).containsExactly(campusA);
        assertThat(report.total()).isEqualTo(counts(11, 9, 7, 1, 7, 1, 4, 2, 1, 1, 1));
        assertThat(reports.getGrowthReport(vpA, SEPTEMBER, campusA).campuses()).hasSize(1);

        expectError(() -> reports.getGrowthReport(vpA, SEPTEMBER, campusB), ApiCode.HEALTH_CHECK_ACCESS_DENIED);
        // PHT hỏi điểm trường không tồn tại cũng 403 như ngoài phạm vi (không lộ điểm trường có hay không)
        expectError(() -> reports.getGrowthReport(vpA, SEPTEMBER, 9_999_999L), ApiCode.HEALTH_CHECK_ACCESS_DENIED);
        expectError(() -> reports.getGrowthReport(teacher, SEPTEMBER, null), ApiCode.HEALTH_CHECK_ACCESS_DENIED);
    }

    @Test
    @DisplayName("HLT-20: HT lọc một điểm trường (kể cả điểm ngừng hoạt động); điểm trường không tồn tại ⇒ 404")
    void principalFiltersByCampus() {
        HealthGrowthReportResponse report = reports.getGrowthReport(principal, SEPTEMBER, campusB);
        assertThat(report.campuses()).extracting(HealthGrowthCampus::campusId).containsExactly(campusB);
        assertThat(report.total()).isEqualTo(counts(1, 1, 1, 0, 1, 0, 1, 0, 0, 0, 0));
        // Không lọc thì điểm ngừng hoạt động không có đợt/kỳ bị ẩn (các test trên: chỉ A, B); lọc đúng nó thì hiện số 0
        assertThat(reports.getGrowthReport(principal, SEPTEMBER, campusC).campuses())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.campusId()).isEqualTo(campusC);
                    assertThat(c.classes()).isEmpty();
                    assertThat(c.total()).isEqualTo(counts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
                });

        expectError(() -> reports.getGrowthReport(principal, SEPTEMBER, 9_999_999L), ApiCode.CAMPUS_NOT_FOUND);
    }

    @Test
    @DisplayName("HLT-20: trẻ chuyển lớp trong tháng tính một lần, ở lớp của lần đo được tính mới nhất")
    void childMovingClassIsCountedOnceInClassOfLatestRecord() {
        // Cân ở 5 tuổi A ngày 10/09 rồi chuyển sang 5 tuổi B từ 13/09: đợt khám sau còn liệt kê trẻ ở 5 tuổi B
        long mover = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) "
                        + "VALUES ('GR-MOVER', 'Bé chuyển lớp (giả)', '2021-03-10', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) "
                        + "VALUES (?, ?, '2026-09-01', '2026-09-12')",
                mover,
                fiveA);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-13')",
                mover,
                fiveB);
        measure(mover, round, fiveA, "2026-09-10", "112.9", "19.5"); // bình thường

        HealthGrowthCampus a = reports.getGrowthReport(principal, SEPTEMBER, campusA)
                .campuses()
                .getFirst();
        assertThat(a.classes().get(0).className()).isEqualTo("5 tuổi A");
        assertThat(a.classes().get(0).counts()).isEqualTo(counts(5, 4, 4, 0, 3, 1, 3, 0, 0, 1, 0));
        assertThat(a.classes().get(1).className()).isEqualTo("5 tuổi B");
        assertThat(a.classes().get(1).counts()).isEqualTo(counts(3, 2, 1, 1, 2, 0, 0, 1, 1, 0, 0));
        assertThat(a.total().enrolled()).isEqualTo(12);
        assertThat(a.total().measured()).isEqualTo(10);
    }

    private static HealthGrowthCounts counts(
            int enrolled,
            int measured,
            int weightNormal,
            int underweight,
            int heightNormal,
            int stunted,
            int bmiNormal,
            int wasted,
            int overweight,
            int obese,
            int unclassified) {
        return new HealthGrowthCounts(
                enrolled,
                measured,
                weightNormal,
                underweight,
                heightNormal,
                stunted,
                bmiNormal,
                wasted,
                overweight,
                obese,
                unclassified);
    }

    private String ageGroupName(String code) {
        return jdbc.queryForObject("SELECT name FROM age_groups WHERE code = ?", String.class, code);
    }

    // Đợt/kỳ lịch sử là dữ liệu đã có; API tạo mới chỉ nhận ngày từ hôm nay.
    private long campaign(HealthCheckKind kind, long campusId, String start, String end) {
        Instant createdAt = LocalDate.parse(start)
                .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toInstant();
        HealthCheckCampaign campaign = campaignRepository.save(HealthCheckCampaign.builder()
                .kind(kind)
                .campusId(campusId)
                .name(kind == HealthCheckKind.MEASUREMENT ? "Cân đo (giả)" : "Khám (giả)")
                .startDate(LocalDate.parse(start))
                .endDate(LocalDate.parse(end))
                .examiner("Trạm Y tế (giả)")
                .note("")
                .createdBy(principal.userId())
                .createdAt(createdAt)
                .build());
        historyRepository.save(HealthCheckCampaignHistory.of(
                campaign.getId(), HealthCheckAction.CREATED, principal.userId(), null, createdAt));
        return campaign.getId();
    }

    private void measure(long child, long campaign, long classroom, String date, String height, String weight) {
        jdbc.update(
                "INSERT INTO health_measurements (child_id, campaign_id, classroom_id, date, height_cm, weight_kg, note, "
                        + "measured_by, published_at, published_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, '', ?, now(), ?, now(), now())",
                child,
                campaign,
                classroom,
                LocalDate.parse(date),
                new BigDecimal(height),
                new BigDecimal(weight),
                teacher.userId(),
                teacher.userId());
    }

    private void examResult(
            long child, long campaign, long classroom, String date, String height, String weight, boolean concluded) {
        jdbc.update(
                "INSERT INTO health_measurements (child_id, campaign_id, classroom_id, date, height_cm, weight_kg, note, "
                        + "measured_by, published_at, published_by, exam, exam_status, concluded_at, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, ?, '', ?, "
                        + (concluded
                                ? "now(), ?, '{}'::jsonb, 'CONCLUDED', now()"
                                : "NULL, ?, '{}'::jsonb, 'DRAFT', NULL")
                        + ", now(), now())",
                child,
                campaign,
                classroom,
                LocalDate.parse(date),
                new BigDecimal(height),
                new BigDecimal(weight),
                teacher.userId(),
                teacher.userId());
    }

    private long classroom(long campusId, long yearId, String name, String ageGroupCode) {
        Long ageGroupId = ageGroupCode == null
                ? null
                : jdbc.queryForObject("SELECT id FROM age_groups WHERE code = ?", Long.class, ageGroupCode);
        return jdbc.queryForObject(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                campusId,
                yearId,
                name,
                ageGroupId);
    }

    private long child(String name, String dateOfBirth, String gender, long classroomId) {
        long id = jdbc.queryForObject(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                        + "'GR' || substr(md5(random()::text), 1, 12), ?, ?, ?, 'ACTIVE', '2026-09-01') RETURNING id",
                Long.class,
                name,
                LocalDate.parse(dateOfBirth),
                gender);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                id,
                classroomId);
        return id;
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role));
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
