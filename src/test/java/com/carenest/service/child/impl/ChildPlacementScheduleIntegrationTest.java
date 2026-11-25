package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.ChildListItemResponse;
import com.carenest.dto.child.ChildNextClassResponse;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.PlaceChildRequest;
import com.carenest.dto.child.WithdrawChildRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.child.Enrollment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildListService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
 * Ngày hiệu lực xếp / chuyển lớp (G94-180/CHD-14) trên PostgreSQL thật: xếp lần đầu từ ngày bắt đầu đi học, lịch
 * chuyển lớp (trẻ ở lớp cũ tới hết ngày trước ngày chuyển), thay / hủy lịch, cho nghỉ khi có lịch, nhãn tới ngày đi học.
 * Hôm nay = thứ 3, 10/11/2026.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ChildPlacementScheduleIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 14);
    private static final Set<ChildStatus> ALL = EnumSet.allOf(ChildStatus.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private ChildRecordService recordService;

    @Autowired
    private ChildListService listService;

    @Autowired
    private ChildIntakeService intakeService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private long teacherA1;
    private long teacherA2;
    private long yearActive;
    private long campusA;
    private long classA1;
    private long classA2;
    private long classB1;
    private long an;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        long principalId = staff("ht.sch" + n, "PRINCIPAL");
        teacherA1 = staff("gv.a1.sch" + n, "TEACHER");
        teacherA2 = staff("gv.a2.sch" + n, "TEACHER");
        campusA = insertId(
                "INSERT INTO campuses (code, name, is_main) VALUES ('SCA" + n + "', 'Cơ sở A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name) VALUES ('SCB" + n + "', 'Cơ sở B') RETURNING id");
        yearActive = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA1 = classroom(campusA, "Chồi A1", null);
        classA2 = classroom(campusA, "Chồi A2", null);
        classB1 = classroom(campusB, "Chồi B1", null);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2025-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE), (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacherA1,
                classA1,
                teacherA2,
                classA2);
        an = child("Nguyễn An Lịch", "ACTIVE", null, null, principalId);
        enroll(an, classA1, "2026-09-01");
        principal = new CurrentUser(
                principalId,
                "kc-" + principalId,
                "user" + principalId,
                "User " + principalId,
                null,
                Set.of(CareNestRole.PRINCIPAL),
                TestPermissions.defaultsOf(CareNestRole.PRINCIPAL));
    }

    @Test
    @DisplayName(
            "G94-180/CHD-14: xếp lần đầu không ghi ngày ⇒ lớp từ ngày bắt đầu đi học; trước ngày đó, ngày nghỉ, sau cuối năm ⇒ lỗi")
    void firstPlacementStartsOnStartDate() {
        long binh = child("Trần Bình Chờ", "PENDING_PLACEMENT", campusA, MONDAY, null);

        expectField(
                () -> recordService.place(principal, binh, new PlaceChildRequest(classA1, null, TODAY)),
                "effectiveDate",
                "Ngày vào lớp không được trước ngày bắt đầu đi học (16/11/2026)");
        expectField(
                () -> recordService.place(principal, binh, new PlaceChildRequest(classA1, null, MONDAY.plusDays(5))),
                "effectiveDate",
                "Ngày vào lớp 21/11/2026 là thứ Bảy. Chọn ngày từ thứ 2 tới thứ 6.");
        expectField(
                () -> recordService.place(
                        principal, binh, new PlaceChildRequest(classA1, null, LocalDate.of(2027, 6, 1))),
                "effectiveDate",
                "Ngày vào lớp không được sau ngày kết thúc năm học (31/05/2027)");

        ChildViewResponse view = recordService.place(principal, binh, new PlaceChildRequest(classA1, null));
        entityManager.flush();

        assertThat(validFrom(binh, classA1)).isEqualTo(MONDAY);
        assertThat(view.status()).isEqualTo(ChildStatus.ACTIVE);
        assertThat(view.classId()).isEqualTo(classA1);
        assertThat(view.nextClass()).isEqualTo(new ChildNextClassResponse(classA1, "Chồi A1", MONDAY));
        assertThat(rosterIds(classA1, TODAY)).doesNotContain(binh);
        assertThat(rosterIds(classA1, MONDAY)).contains(binh);
        assertThat(recordService.findPlacements(binh)).singleElement().satisfies(p -> {
            assertThat(p.fromClassId()).isNull();
            assertThat(p.effectiveFrom()).isEqualTo(MONDAY);
            assertThat(p.cancelledAt()).isNull();
        });
        assertThat(bodies(teacherA1)).contains("Trần Bình Chờ đã được xếp vào Chồi A1 từ ngày 16/11/2026.");
    }

    @Test
    @DisplayName("G94-180/CHD-14: xếp lại trẻ có lớp chưa bắt đầu ⇒ thay lớp và ngày, không cần lý do, dòng cũ đã hủy")
    void replacingNotStartedClassNeedsNoReason() {
        long binh = child("Trần Bình Đổi", "PENDING_PLACEMENT", campusA, MONDAY, null);
        recordService.place(principal, binh, new PlaceChildRequest(classA1, null));
        entityManager.flush();

        recordService.place(principal, binh, new PlaceChildRequest(classA2, null, MONDAY.plusDays(1)));
        entityManager.flush();

        assertThat(enrollmentRepository.findOpenByChildId(binh, TODAY))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getClassroomId()).isEqualTo(classA2);
                    assertThat(e.getValidFrom()).isEqualTo(MONDAY.plusDays(1));
                });
        assertThat(recordService.findPlacements(binh))
                .hasSize(2)
                .satisfiesExactly(
                        newest -> {
                            assertThat(newest.fromClassId()).isNull();
                            assertThat(newest.toClassId()).isEqualTo(classA2);
                            assertThat(newest.cancelledAt()).isNull();
                        },
                        oldest -> {
                            assertThat(oldest.toClassId()).isEqualTo(classA1);
                            assertThat(oldest.cancelledAt()).isNotNull();
                        });
        assertThat(bodies(teacherA1)).contains("Trần Bình Đổi không vào Chồi A1 từ ngày 16/11/2026 nữa.");
    }

    @Test
    @DisplayName("G94-180/CHD-14: chuyển lớp ngày tương lai ⇒ trẻ ở lớp cũ tới hết ngày trước đó, lớp mới từ ngày đó")
    void scheduledTransferKeepsOldClassUntilTheDay() {
        recordService.place(principal, an, new PlaceChildRequest(classA2, "Đổi lớp theo nguyện vọng", MONDAY));
        entityManager.flush();

        assertThat(validTo(an, classA1)).isEqualTo(MONDAY.minusDays(1));
        assertThat(validFrom(an, classA2)).isEqualTo(MONDAY);
        assertThat(rosterIds(classA1, LocalDate.of(2026, 11, 13))).contains(an);
        assertThat(rosterIds(classA2, LocalDate.of(2026, 11, 13))).doesNotContain(an);
        assertThat(rosterIds(classA2, MONDAY)).contains(an);
        assertThat(rosterIds(classA1, MONDAY)).doesNotContain(an);

        ChildNextClassResponse next = new ChildNextClassResponse(classA2, "Chồi A2", MONDAY);
        ChildViewResponse view = intakeService.getChildView(an);
        assertThat(view.classId()).isEqualTo(classA1);
        assertThat(view.nextClass()).isEqualTo(next);
        assertThat(item(an).classId()).isEqualTo(classA1);
        assertThat(item(an).nextClass()).isEqualTo(next);
        assertThat(recordService.findClass(an).id()).isEqualTo(classA1);
        assertThat(recordService.findPlacements(an)).first().satisfies(p -> {
            assertThat(p.fromClassId()).isEqualTo(classA1);
            assertThat(p.toClassId()).isEqualTo(classA2);
            assertThat(p.effectiveFrom()).isEqualTo(MONDAY);
            assertThat(p.reason()).isEqualTo("Đổi lớp theo nguyện vọng");
        });
        assertThat(bodies(teacherA1)).contains("Nguyễn An Lịch chuyển sang Chồi A2 từ ngày 16/11/2026.");
        assertThat(bodies(teacherA2)).contains("Nguyễn An Lịch chuyển vào lớp từ ngày 16/11/2026.");
    }

    @Test
    @DisplayName(
            "G94-180/CHD-14: ngày chuyển lớp trước hôm nay, ngày nghỉ, sau cuối năm ⇒ lỗi ô effectiveDate; vẫn cần lý do")
    void transferDateMustBeSchoolDayWithinYear() {
        expectField(
                () -> recordService.place(principal, an, new PlaceChildRequest(classA2, "Đổi", TODAY.minusDays(1))),
                "effectiveDate",
                "Ngày chuyển lớp không được trước hôm nay");
        expectField(
                () -> recordService.place(principal, an, new PlaceChildRequest(classA2, "Đổi", SATURDAY)),
                "effectiveDate",
                "Ngày chuyển lớp 14/11/2026 là thứ Bảy. Chọn ngày từ thứ 2 tới thứ 6.");
        jdbc.update(
                "INSERT INTO school_holidays (school_year_id, start_date, end_date, name) "
                        + "VALUES (?, '2026-11-20', '2026-11-20', 'Nghỉ lễ')",
                yearActive);
        expectField(
                () -> recordService.place(
                        principal, an, new PlaceChildRequest(classA2, "Đổi", LocalDate.of(2026, 11, 20))),
                "effectiveDate",
                "Ngày chuyển lớp 20/11/2026 trùng ngày nghỉ của trường: Nghỉ lễ (20/11/2026). Chọn ngày học khác.");
        expectField(
                () -> recordService.place(
                        principal, an, new PlaceChildRequest(classA2, "Đổi", LocalDate.of(2027, 6, 1))),
                "effectiveDate",
                "Ngày chuyển lớp không được sau ngày kết thúc năm học (31/05/2027)");
        expectField(
                () -> recordService.place(principal, an, new PlaceChildRequest(classA2, " ", MONDAY)),
                "reason",
                "Nhập lý do chuyển lớp");
    }

    @Test
    @DisplayName("G94-180/CHD-14: thẻ lớp trả occupiedCount gồm trẻ hẹn vào từ ngày chọn; bỏ qua trẻ đang xếp")
    void placementClassesCountScheduledArrivals() {
        long cap3 = classroom(campusA, "Chồi A4", 3);
        long c1 = child("Lê Một", "ACTIVE", null, null, null);
        long c2 = child("Lê Hai", "ACTIVE", null, null, null);
        long arriving = child("Lê Hẹn Vào", "ACTIVE", null, null, null);
        enroll(c1, cap3, "2026-09-01");
        enroll(c2, cap3, "2026-09-01");
        enroll(arriving, cap3, "2026-11-11");

        var card = intakeService.listPlacementClasses(principal, null, null, null).stream()
                .filter(c -> c.id().equals(cap3))
                .findFirst()
                .orElseThrow();
        assertThat(card.enrolledCount()).isEqualTo(2);
        assertThat(card.occupiedCount()).isEqualTo(3);

        var excluded = intakeService.listPlacementClasses(principal, null, TODAY, arriving).stream()
                .filter(c -> c.id().equals(cap3))
                .findFirst()
                .orElseThrow();
        assertThat(excluded.occupiedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("G94-180/CHD-14: sĩ số lớp đích tính cả trẻ đã hẹn vào lớp sau ngày chuyển")
    void capacityCountsChildrenJoiningLater() {
        long smallClass = classroom(campusA, "Chồi A3", 1);
        long later = child("Lê Hẹn Sau", "ACTIVE", null, null, null);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-11-20')",
                later,
                smallClass);

        expectField(
                () -> recordService.place(principal, an, new PlaceChildRequest(smallClass, "Đổi lớp", MONDAY)),
                "classId",
                "Chồi A3 đã đủ 1 trẻ. Chọn lớp khác.");
    }

    @Test
    @DisplayName("Mục 2: xếp lớp lần đầu cho trẻ chờ xếp lớp chưa có ngày bắt đầu ⇒ ngày bắt đầu = ngày vào lớp")
    void firstPlacementFillsMissingStartDate() {
        long binh = child("Trần Bình Chưa Ngày", "PENDING_PLACEMENT", campusA, null, null);

        recordService.place(principal, binh, new PlaceChildRequest(classA1, null, MONDAY));
        entityManager.flush();

        assertThat(startDateOf(binh)).isEqualTo(MONDAY);
    }

    @Test
    @DisplayName("Mục 2: trẻ chờ xếp lớp đã có ngày bắt đầu ⇒ xếp lớp giữ nguyên ngày bắt đầu")
    void firstPlacementKeepsExistingStartDate() {
        long binh = child("Trần Bình Có Ngày", "PENDING_PLACEMENT", campusA, MONDAY, null);

        recordService.place(principal, binh, new PlaceChildRequest(classA1, null, MONDAY.plusDays(1)));
        entityManager.flush();

        assertThat(startDateOf(binh)).isEqualTo(MONDAY);
    }

    @Test
    @DisplayName("Mục 2: chuyển lớp trẻ đang học không đổi ngày bắt đầu (kể cả khi chưa có)")
    void transferKeepsStartDate() {
        recordService.place(principal, an, new PlaceChildRequest(classA2, "Đổi lớp", MONDAY));
        entityManager.flush();

        assertThat(startDateOf(an)).isNull();
    }

    @Test
    @DisplayName("Mục 2: thay lịch vào lớp đầu tiên chưa bắt đầu ⇒ ngày bắt đầu theo ngày vào lớp mới")
    void replacingFirstPlacementMovesStartDate() {
        long binh = child("Trần Bình Thay Lịch", "PENDING_PLACEMENT", campusA, null, null);
        recordService.place(principal, binh, new PlaceChildRequest(classA1, null, MONDAY));
        entityManager.flush();
        assertThat(startDateOf(binh)).isEqualTo(MONDAY);

        recordService.place(principal, binh, new PlaceChildRequest(classA2, null, MONDAY.plusDays(1)));
        entityManager.flush();

        assertThat(startDateOf(binh)).isEqualTo(MONDAY.plusDays(1));
    }

    @Test
    @DisplayName("Mục 2: hủy lịch vào lớp đầu tiên ⇒ ngày bắt đầu về trống, xếp lớp lại ghi ngày mới")
    void cancellingFirstPlacementClearsStartDate() {
        long binh = child("Trần Bình Hủy Lịch", "PENDING_PLACEMENT", campusA, null, null);
        recordService.place(principal, binh, new PlaceChildRequest(classA1, null, MONDAY));
        entityManager.flush();

        recordService.cancelScheduledPlacement(principal, binh);
        entityManager.flush();
        assertThat(startDateOf(binh)).isNull();

        recordService.place(principal, binh, new PlaceChildRequest(classA1, null, MONDAY.plusDays(2)));
        entityManager.flush();
        assertThat(startDateOf(binh)).isEqualTo(MONDAY.plusDays(2));
    }

    @Test
    @DisplayName("Mục 2: ngày bắt đầu chọn khi tiếp nhận khác ngày vào lớp ⇒ thay / hủy lịch không đổi")
    void intakeStartDateDifferentFromPlacementIsUntouched() {
        long binh = child("Trần Bình Ngày Riêng", "PENDING_PLACEMENT", campusA, MONDAY, null);
        recordService.place(principal, binh, new PlaceChildRequest(classA1, null, MONDAY.plusDays(1)));
        entityManager.flush();

        recordService.place(principal, binh, new PlaceChildRequest(classA2, null, MONDAY.plusDays(2)));
        entityManager.flush();
        assertThat(startDateOf(binh)).isEqualTo(MONDAY);

        recordService.cancelScheduledPlacement(principal, binh);
        entityManager.flush();
        assertThat(startDateOf(binh)).isEqualTo(MONDAY);
    }

    @Test
    @DisplayName("Mục 2: trẻ tiếp nhận lại đã từng học ở lần trước ⇒ thay lịch vào lớp đầu tiên của lần này không đổi"
            + " ngày bắt đầu (đã từng đi học)")
    void replacingFirstPlacementOfReadmittedChildKeepsStartDate() {
        long binh = child("Trần Bình Học Lại", "PENDING_PLACEMENT", campusA, null, null);
        // Lần học trước: lớp A1 từ đầu năm tới 30/09, rồi nghỉ và mở lại hồ sơ
        enroll(binh, classA1, "2026-09-01");
        jdbc.update("UPDATE enrollments SET valid_to = '2026-09-30' WHERE child_id = ?", binh);
        recordService.place(principal, binh, new PlaceChildRequest(classA2, null, MONDAY));
        entityManager.flush();
        assertThat(startDateOf(binh)).isEqualTo(MONDAY);

        recordService.place(principal, binh, new PlaceChildRequest(classA1, null, MONDAY.plusDays(1)));
        entityManager.flush();

        assertThat(startDateOf(binh)).isEqualTo(MONDAY);
    }

    private LocalDate startDateOf(long childId) {
        return jdbc.queryForObject("SELECT start_date FROM children WHERE id = ?", LocalDate.class, childId);
    }

    @Test
    @DisplayName("G94-180/CHD-14: chuyển lần nữa thay lịch cũ; hủy lịch ⇒ ở lại lớp cũ; không còn lịch ⇒ 400")
    void transferReplacesAndCancelsSchedule() {
        recordService.place(principal, an, new PlaceChildRequest(classA2, "Đổi lớp", MONDAY));
        entityManager.flush();

        recordService.place(principal, an, new PlaceChildRequest(classB1, "Chuyển điểm trường", MONDAY.plusDays(2)));
        entityManager.flush();
        assertThat(enrollmentRepository.findOpenByChildId(an, TODAY))
                .extracting(Enrollment::getClassroomId, Enrollment::getValidFrom, Enrollment::getValidTo)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(classA1, LocalDate.of(2026, 9, 1), MONDAY.plusDays(1)),
                        org.assertj.core.groups.Tuple.tuple(classB1, MONDAY.plusDays(2), null));
        assertThat(bodies(teacherA2)).contains("Nguyễn An Lịch không vào Chồi A2 từ ngày 16/11/2026 nữa.");

        ChildViewResponse view = recordService.cancelScheduledPlacement(principal, an);
        entityManager.flush();
        assertThat(view.classId()).isEqualTo(classA1);
        assertThat(view.nextClass()).isNull();
        assertThat(enrollmentRepository.findOpenByChildId(an, TODAY))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getClassroomId()).isEqualTo(classA1);
                    assertThat(e.getValidTo()).isNull();
                });
        assertThat(recordService.findPlacements(an))
                .filteredOn(p -> p.toClassId().equals(classA2) || p.toClassId().equals(classB1))
                .allSatisfy(p -> assertThat(p.cancelledAt()).isNotNull());
        assertThat(bodies(teacherA1)).contains("Nguyễn An Lịch ở lại lớp, không chuyển lớp nữa.");

        expectField(
                () -> recordService.cancelScheduledPlacement(principal, an),
                "classId",
                "Trẻ không có lịch vào lớp hoặc chuyển lớp chưa tới ngày");
    }

    @Test
    @DisplayName("G94-180/CHD-14: hủy lớp chưa bắt đầu của trẻ chưa học lớp nào ⇒ về Chờ xếp lớp ở điểm trường đó")
    void cancellingFirstFuturePlacementReturnsToWaiting() {
        long binh = child("Trần Bình Hủy", "PENDING_PLACEMENT", campusA, MONDAY, null);
        recordService.place(principal, binh, new PlaceChildRequest(classA1, null));
        entityManager.flush();

        ChildViewResponse view = recordService.cancelScheduledPlacement(principal, binh);
        entityManager.flush();

        assertThat(view.status()).isEqualTo(ChildStatus.PENDING_PLACEMENT);
        assertThat(view.campusId()).isEqualTo(campusA);
        assertThat(enrollmentRepository.findOpenByChildId(binh, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("G94-180/CHD-03: cho nghỉ học khi có lịch chuyển ⇒ xóa lịch, lớp hiện tại kết thúc trước ngày nghỉ")
    void withdrawRemovesSchedule() {
        recordService.place(principal, an, new PlaceChildRequest(classA2, "Đổi lớp", MONDAY));
        entityManager.flush();

        recordService.withdraw(principal, an, new WithdrawChildRequest(TODAY.toString(), "Gia đình chuyển nhà"));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT status FROM children WHERE id = ?", String.class, an))
                .isEqualTo("LEFT");
        assertThat(jdbc.queryForList(
                        "SELECT classroom_id FROM enrollments WHERE child_id = ? ORDER BY valid_from", Long.class, an))
                .containsExactly(classA1);
        assertThat(validTo(an, classA1)).isEqualTo(TODAY.minusDays(1));
        assertThat(recordService.findPlacements(an))
                .filteredOn(p -> p.toClassId().equals(classA2))
                .singleElement()
                .satisfies(p -> assertThat(p.cancelledAt()).isNotNull());
        assertThat(bodies(teacherA2)).contains("Nguyễn An Lịch nghỉ học từ ngày 10/11/2026.");
    }

    @Test
    @DisplayName(
            "G94-180/CHD-14: trẻ chờ xếp lớp đã tới ngày bắt đầu đi học ⇒ startDue; ngày bắt đầu tương lai ⇒ không")
    void startDueFlagsWaitingChildrenFromTheirStartDate() {
        long due = child("Trẻ Tới Ngày", "PENDING_PLACEMENT", campusA, TODAY, null);
        long later = child("Trẻ Chưa Tới", "PENDING_PLACEMENT", campusA, MONDAY, null);

        assertThat(intakeService.getChildView(due).startDue()).isTrue();
        assertThat(intakeService.getChildView(due).startDate()).isEqualTo(TODAY);
        assertThat(intakeService.getChildView(later).startDue()).isFalse();
        assertThat(item(due).startDue()).isTrue();
        assertThat(item(later).startDue()).isFalse();
        assertThat(item(an).startDue()).isFalse();
    }

    @Test
    @DisplayName("G94-180/CHD-13: chuyển lớp trong năm sang lớp nhóm tuổi khác ⇒ vẫn nhận (nhóm tuổi chỉ gợi ý)")
    void transferToClassOfOtherAgeGroupIsAccepted() {
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        jdbc.update(
                "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note) "
                        + "SELECT ?, id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note FROM age_groups WHERE id = ? ON CONFLICT DO NOTHING",
                yearActive,
                ag3);
        long younger = jdbc.queryForObject(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'Mầm A', ?) RETURNING id",
                Long.class,
                campusA,
                yearActive,
                ag3);

        // An sinh 01/03/2022 (MG 4–5) chuyển sang lớp MG 3–4
        recordService.place(principal, an, new PlaceChildRequest(younger, "Theo nguyện vọng gia đình", TODAY));
        entityManager.flush();

        assertThat(validFrom(an, younger)).isEqualTo(TODAY);
        assertThat(validTo(an, classA1)).isEqualTo(TODAY.minusDays(1));
    }

    private ChildListItemResponse item(long childId) {
        return listService
                .searchChildren(principal, new ChildListFilter(yearActive, null, null, ALL, null, 0, 100))
                .items()
                .stream()
                .filter(i -> i.id().equals(childId))
                .findFirst()
                .orElseThrow();
    }

    private List<Long> rosterIds(long classroomId, LocalDate date) {
        return enrollmentRepository.findActiveByClassroomIds(List.of(classroomId), date).stream()
                .map(Enrollment::getChildId)
                .toList();
    }

    private LocalDate validFrom(long childId, long classroomId) {
        return jdbc.queryForObject(
                "SELECT valid_from FROM enrollments WHERE child_id = ? AND classroom_id = ?",
                LocalDate.class,
                childId,
                classroomId);
    }

    private LocalDate validTo(long childId, long classroomId) {
        return jdbc.queryForObject(
                "SELECT valid_to FROM enrollments WHERE child_id = ? AND classroom_id = ?",
                LocalDate.class,
                childId,
                classroomId);
    }

    private List<String> bodies(long userId) {
        entityManager.flush();
        return jdbc.queryForList(
                "SELECT body FROM notifications WHERE type IN ('CHILD_PLACED', 'CHILD_WITHDRAWN') AND user_id = ?",
                String.class,
                userId);
    }

    private static void expectField(ThrowingCallable call, String field, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlobalException.class, ex -> {
            assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
            Map<String, String> errors = ex.getFieldErrors().stream()
                    .collect(Collectors.toMap(FieldErrorResponse::field, FieldErrorResponse::message, (a, b) -> a));
            assertThat(errors).containsEntry(field, message);
        });
    }

    private long classroom(long campusId, String name, Integer capacity) {
        long ageGroup = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        jdbc.update(
                "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note) "
                        + "SELECT ?, id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note FROM age_groups WHERE id = ? ON CONFLICT DO NOTHING",
                yearActive,
                ageGroup);
        return jdbc.queryForObject(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, capacity)"
                        + " VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                campusId,
                yearActive,
                name,
                ageGroup,
                capacity);
    }

    /** Trẻ sinh 01/03/2022 (nhóm ag-4 tại đầu năm 2026-2027), có khai báo ăn và sức khỏe. */
    private long child(String name, String status, Long intakeCampusId, LocalDate startDate, Long declaredBy) {
        long id = jdbc.queryForObject(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, intake_campus_id,"
                        + " start_date) VALUES (?, ?, '2022-03-01', 'FEMALE', ?, '2026-09-01', ?, ?) RETURNING id",
                Long.class,
                "SC" + SEQ.incrementAndGet(),
                name,
                status,
                intakeCampusId,
                startDate);
        long by =
                declaredBy != null ? declaredBy : jdbc.queryForObject("SELECT min(id) FROM user_accounts", Long.class);
        jdbc.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state,"
                        + " self_history_state, family_history_state, tb_contact_state, declared_by, declared_at)"
                        + " VALUES (?, 'NONE_REPORTED', 'NONE_REPORTED', 'NOT_PROVIDED', 'NONE_REPORTED',"
                        + " 'NONE_REPORTED', 'NONE_REPORTED', ?, now())",
                id,
                by);
        return id;
    }

    private void enroll(long childId, long classroomId, String from) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, CAST(? AS DATE))",
                childId,
                classroomId,
                from);
    }

    private long staff(String username, String role) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role) VALUES ('kc-"
                + username + "', '" + username + "', '" + username + "', '" + role + "') RETURNING id");
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
