package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.carenest.dto.observation.SaveDailyGeneralRequest;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.AssessmentDayUnlock;
import com.carenest.entity.observation.ClassDailyComment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.AssessmentDayUnlockRepository;
import com.carenest.repository.observation.ClassDailyCommentRepository;
import com.carenest.repository.observation.DailyActivityResultRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ClassCommentServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
    private final CurrentUser teacher = user(1L, CareNestRole.TEACHER);
    private final CurrentUser principal = user(2L, CareNestRole.PRINCIPAL);
    private final CurrentUser vp = user(3L, CareNestRole.VICE_PRINCIPAL);
    private final ClassroomService classes = mock(ClassroomService.class);
    private final StaffAssignmentService staff = mock(StaffAssignmentService.class);
    private final ClassroomAccessPolicy classPolicy = mock(ClassroomAccessPolicy.class);
    private final CampusAccessPolicy campusPolicy = mock(CampusAccessPolicy.class);
    private final AccountService accounts = mock(AccountService.class);
    private final AssessmentDayUnlockRepository unlocks = mock(AssessmentDayUnlockRepository.class);
    private final ClassDailyCommentRepository comments = mock(ClassDailyCommentRepository.class);
    private final DailyObservationRepository observations = mock(DailyObservationRepository.class);
    private final DailyActivityResultRepository results = mock(DailyActivityResultRepository.class);
    private final EnrollmentService enrollments = mock(EnrollmentService.class);
    private final AttendanceService attendance = mock(AttendanceService.class);
    private final DailyHealthService health = mock(DailyHealthService.class);
    private final EduLessonQueryService lessons = mock(EduLessonQueryService.class);
    private final Map<String, ClassDailyComment> stored = new HashMap<>();
    private ClassroomInfoResponse classroom = info(10L, 100L, SchoolYearStatus.ACTIVE);
    private final ClassroomInfoResponse other = info(20L, 200L, SchoolYearStatus.ACTIVE);
    private DailyAssessmentServiceImpl service;
    private ClassAssessmentOverviewServiceImpl overview;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        var rules = new ObservationDayRules(clock, unlocks, classes, staff, classPolicy, campusPolicy, accounts);
        service = new DailyAssessmentServiceImpl(
                rules,
                observations,
                results,
                enrollments,
                attendance,
                health,
                lessons,
                mock(ObjectMapper.class),
                comments);
        overview = new ClassAssessmentOverviewServiceImpl(
                rules, classes, enrollments, attendance, health, observations, results, unlocks, comments, lessons);
        when(classes.findClassroomInfo(anyLong()))
                .thenAnswer(i -> Optional.of(i.<Long>getArgument(0) == 10L ? classroom : other));
        when(classes.findYearClassrooms(any())).thenAnswer(i -> List.of(classroom, other));
        when(staff.hasActiveTeacherAssignment(eq(1L), eq(10L), eq(TODAY))).thenReturn(true);
        when(classPolicy.canViewClassroom(teacher, 10L)).thenReturn(true);
        when(campusPolicy.canViewCampus(eq(principal), anyLong())).thenReturn(true);
        when(campusPolicy.canViewCampus(vp, 100L)).thenReturn(true);
        when(accounts.findFullNames(anyList())).thenReturn(Map.of(1L, "Review Teacher"));
        when(comments.findByClassroomIdAndDate(anyLong(), any()))
                .thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0) + "|" + i.getArgument(1))));
        when(comments.save(any(ClassDailyComment.class))).thenAnswer(i -> {
            ClassDailyComment note = i.getArgument(0);
            stored.put(note.getClassroomId() + "|" + note.getDate(), note);
            return note;
        });
        when(comments.findByClassroomIdInAndDateBetweenOrderByDateDesc(anyCollection(), any(), any()))
                .thenAnswer(i -> {
                    List<Long> ids = i.getArgument(0);
                    LocalDate from = i.getArgument(1);
                    LocalDate to = i.getArgument(2);
                    return stored.values().stream()
                            .filter(n -> ids.contains(n.getClassroomId())
                                    && !n.getDate().isBefore(from)
                                    && !n.getDate().isAfter(to))
                            .sorted(java.util.Comparator.comparing(ClassDailyComment::getDate)
                                    .reversed())
                            .toList();
                });
    }

    @Test
    @DisplayName("OBS-19: save and update one class comment without child results")
    void noteOnlyPersistsMetadataWithoutFabricatingChildAssessment() {
        assertThat(service.saveGeneral(teacher, 10L, request(TODAY, "  Class note  "))
                        .saved())
                .isZero();
        var sheet = service.getDailySheet(teacher, 10L, TODAY);
        assertThat(sheet.rows()).isEmpty();
        assertThat(sheet.classComment().text()).isEqualTo("Class note");
        assertThat(sheet.classComment().recordedByName()).isEqualTo("Review Teacher");
        assertThat(sheet.classComment().updatedAt()).isEqualTo(NOW);
        service.saveGeneral(teacher, 10L, request(TODAY, "Updated"));
        assertThat(stored).hasSize(1);
        assertThat(stored.values().iterator().next().getText()).isEqualTo("Updated");
        verify(observations, never()).save(any());
        verify(results, never()).save(any());
    }

    @Test
    @DisplayName("OBS-19: class comments remain class-scoped in week and month views")
    void overviewScopesAndFiltersDaysWithoutCountingChildren() {
        service.saveGeneral(teacher, 10L, request(TODAY, "Today"));
        var yesterday = ClassDailyComment.create(10L, 100L, TODAY.minusDays(1));
        yesterday.record("Yesterday", 1L, NOW);
        stored.put("10|" + yesterday.getDate(), yesterday);
        var outside = ClassDailyComment.create(10L, 100L, LocalDate.of(2026, 9, 10));
        outside.record("Outside month", 1L, NOW);
        stored.put("10|" + outside.getDate(), outside);
        var foreign = ClassDailyComment.create(20L, 200L, TODAY);
        foreign.record("Other campus", 1L, NOW);
        stored.put("20|" + TODAY, foreign);
        for (String period : List.of("WEEK", "MONTH")) {
            var board = overview.getOverview(vp, period, 1L, null, null, null);
            assertThat(board.rows()).hasSize(1);
            assertThat(board.rows().getFirst().assessed()).isZero();
            assertThat(board.rows().getFirst().flags()).isZero();
            assertThat(board.rows().getFirst().classComments())
                    .extracting(c -> c.text())
                    .containsExactly("Today", "Yesterday");
            assertThat(overview.getClassDetail(vp, 10L, period).classComments()).hasSize(2);
        }
        assertThat(overview.getOverview(principal, "DAY", 1L, null, null, null).rows())
                .hasSize(2);
        assertThatThrownBy(() -> overview.getClassDetail(vp, 20L, "DAY")).isInstanceOf(GlobalException.class);
    }

    @Test
    @DisplayName("OBS-15: closed years and locked days reject class comments until reopened")
    void sameDayAndYearLocksApplyToClassComment() {
        when(unlocks.findFirstByClassroomIdAndDateAndValidUntilAfterOrderByIdDesc(10L, TODAY.minusDays(1), NOW))
                .thenReturn(Optional.empty());
        assertCode(
                () -> service.saveGeneral(teacher, 10L, request(TODAY.minusDays(1), "Blocked")),
                ApiCode.ASSESSMENT_DAY_LOCKED);
        when(unlocks.findFirstByClassroomIdAndDateAndValidUntilAfterOrderByIdDesc(10L, TODAY.minusDays(1), NOW))
                .thenReturn(Optional.of(mock(AssessmentDayUnlock.class)));
        service.saveGeneral(teacher, 10L, request(TODAY.minusDays(1), "Reopened"));
        classroom = info(10L, 100L, SchoolYearStatus.CLOSED);
        assertCode(
                () -> service.saveGeneral(teacher, 10L, request(TODAY, "Closed")), ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        assertThat(stored).hasSize(1);
    }

    @Test
    @DisplayName("OBS-17: leaders and teachers from other classes cannot write class comments")
    void permissionCheckedBeforeNotesAreWritten() {
        assertThatThrownBy(() -> service.saveGeneral(vp, 10L, request(TODAY, "Leader")))
                .isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> service.saveGeneral(teacher, 20L, request(TODAY, "Other class")))
                .isInstanceOf(GlobalException.class);
        verifyNoInteractions(comments);
    }

    @Test
    @DisplayName("OBS-19: clear is explicit and invalid text/no-op does not replace a comment")
    void validatesAndClearsWithoutImplicitDeletion() {
        service.saveGeneral(teacher, 10L, request(TODAY, "Keep"));
        service.saveGeneral(teacher, 10L, request(TODAY, "x".repeat(10000)));
        assertThat(stored.values().iterator().next().getText()).hasSize(10000);
        service.saveGeneral(teacher, 10L, request(TODAY, "Keep"));
        assertCode(
                () -> service.saveGeneral(teacher, 10L, request(TODAY, "tự kỷ")),
                ApiCode.CLASS_COMMENT_DIAGNOSTIC_TERM);
        assertCode(
                () -> service.saveGeneral(teacher, 10L, new SaveDailyGeneralRequest(TODAY, List.of())),
                ApiCode.ASSESSMENT_NOTHING_TO_SAVE);
        assertThat(stored.values().iterator().next().getText()).isEqualTo("Keep");
        service.saveGeneral(teacher, 10L, request(TODAY, ""));
        assertThat(service.getDailySheet(teacher, 10L, TODAY).classComment().text())
                .isEmpty();
        assertThat(overview.getClassDetail(vp, 10L, "DAY").classComments()).isEmpty();
    }

    private static SaveDailyGeneralRequest request(LocalDate day, String text) {
        return new SaveDailyGeneralRequest(day, List.of(), text);
    }

    private static ClassroomInfoResponse info(Long id, Long campusId, SchoolYearStatus status) {
        return new ClassroomInfoResponse(
                id,
                "Review Class",
                campusId,
                1L,
                status,
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2027, 5, 31),
                1L,
                List.of(MealSession.LUNCH));
    }

    private static CurrentUser user(Long id, CareNestRole role) {
        return new CurrentUser(id, "review-" + id, "review-" + id, "Review User", null, Set.of(role));
    }

    private static void assertCode(Runnable action, ApiCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(code));
    }
}
