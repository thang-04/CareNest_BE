package com.carenest.service.meals.impl;

import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.meals.MealAdjustRequest;
import com.carenest.dto.meals.MealCountDayResponse;
import com.carenest.dto.meals.MealCountResponse;
import com.carenest.dto.meals.MealCountResponse.Adjustment;
import com.carenest.dto.meals.MealCountResponse.Line;
import com.carenest.dto.meals.MealCountResponse.Totals;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealCountHistory;
import com.carenest.entity.meals.MealCountLine;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.MealCountHistoryRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ClassMealTally;
import com.carenest.service.meals.MealCountService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealCountStatus;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MealCountServiceImpl implements MealCountService {
    private static final String LOG_PREFIX = "[MealCountServiceImpl]";
    private static final String OPEN = "OPEN";

    private final MealCountRepository mealCountRepository;
    private final MealCountHistoryRepository historyRepository;
    private final AttendanceService attendanceService;
    private final ClassroomService classroomService;
    private final StaffAssignmentService staffAssignmentService;
    private final AccountService accountService;
    private final Clock clock;

    /** Phạm vi người xem: HT toàn trường; PHT, bếp theo điểm trường được phân công (AUTH-01, AUTH-02). */
    private record Viewer(CurrentUser user, boolean principal, Set<Long> leadCampuses, Set<Long> kitchenCampuses) {
        boolean sees(Long campusId) {
            return principal || leadCampuses.contains(campusId) || kitchenCampuses.contains(campusId);
        }

        // HT, PHT thấy cả số chờ xác nhận lại; bếp chỉ thấy số đã xác nhận
        boolean reviews(Long campusId) {
            return principal || leadCampuses.contains(campusId);
        }

        // NUT-02 (Cần chốt 27/10/2026): HT xác nhận được mọi điểm trường, PHT điểm trường mình
        boolean confirms(Long campusId) {
            return principal || leadCampuses.contains(campusId);
        }

        // NUT-02: điều chỉnh số suất vẫn chỉ PHT điểm trường
        boolean adjusts(Long campusId) {
            return leadCampuses.contains(campusId);
        }

        StaffRole confirmerRole() {
            return principal ? StaffRole.PRINCIPAL : StaffRole.VICE_PRINCIPAL;
        }
    }

    /** Lớp của một năm học: tên lớp, trạng thái năm. */
    private record Year(Long id, SchoolYearStatus status, List<ClassroomInfoResponse> classes) {
        boolean active() {
            return status == SchoolYearStatus.ACTIVE;
        }

        Map<Long, ClassroomInfoResponse> classById() {
            return classes.stream().collect(Collectors.toMap(ClassroomInfoResponse::id, Function.identity()));
        }

        List<MealSession> sessions(Long campusId) {
            Set<MealSession> served = classes.stream()
                    .filter(c -> c.campusId().equals(campusId))
                    .flatMap(c -> c.mealSessions().stream())
                    .collect(Collectors.toSet());
            return Arrays.stream(MealSession.values()).filter(served::contains).toList();
        }
    }

    @Override
    public MealCountDayResponse getMealCounts(CurrentUser currentUser, LocalDate date, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getMealCounts|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Viewer viewer = viewer(currentUser);
            LocalDate day = date == null ? LocalDate.now(clock) : date;
            DayInfoResponse info = attendanceService.getDayInfo(currentUser, day, null);
            Year year = year(schoolYearId);
            List<MealCountResponse> counts = new ArrayList<>();
            if (year != null && info.schoolDay() && !day.isAfter(info.today())) {
                List<Long> campusIds = year.classes().stream()
                        .map(ClassroomInfoResponse::campusId)
                        .distinct()
                        .filter(viewer::sees)
                        .toList();
                Map<String, MealCount> stored = new HashMap<>();
                mealCountRepository.findByCampusIdInAndDate(campusIds, day).stream()
                        .filter(c -> c.getSchoolYearId().equals(year.id()))
                        .forEach(c -> stored.put(key(c.getCampusId(), c.getSession()), c));
                // Qua giờ chốt: tổng hợp sổ của ngày một lần (NUT-01), sau đó số chỉ đổi qua xác nhận/điều chỉnh
                if (info.baseLocked() && year.active()) {
                    campusIds.forEach(campusId -> aggregateMissing(year, campusId, day, stored));
                }
                Map<Long, ClassroomInfoResponse> classById = year.classById();
                List<MealCount> shown = new ArrayList<>(stored.values());
                Map<Long, List<MealCountHistory>> history = history(shown);
                Map<Long, String> names = names(shown, history);
                for (Long campusId : campusIds) {
                    List<ClassMealTally> preview = null;
                    for (MealSession session : year.sessions(campusId)) {
                        MealCount count = stored.get(key(campusId, session));
                        if (count != null) {
                            if (viewer.reviews(campusId) || count.isConfirmed()) {
                                counts.add(toView(count, viewer, year, classById, names, history));
                            }
                        } else if (!info.baseLocked() && year.active() && viewer.reviews(campusId)) {
                            // Trước giờ chốt: số tạm tính để PHT theo dõi, chưa lưu
                            if (preview == null) {
                                preview = attendanceService.tallyMeals(day, campusId, year.id());
                            }
                            counts.add(toPreview(campusId, year.id(), day, session, preview, classById));
                        }
                    }
                }
            }
            MealCountDayResponse response = new MealCountDayResponse(
                    day, info.today(), info.cutoff(), info.schoolDay(), info.baseLocked(), counts);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MealCountResponse confirm(CurrentUser currentUser, Long mealCountId) {
        String logPrefix = LOG_PREFIX + "|confirm|userId=" + currentUser.userId() + "|mealCountId=" + mealCountId;
        log.info("{}|START", logPrefix);
        try {
            Viewer viewer = viewer(currentUser);
            MealCount count = mealCountRepository
                    .findById(mealCountId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.MEAL_COUNT_NOT_FOUND));
            if (!viewer.confirms(count.getCampusId())) {
                throw GlobalException.of(ApiCode.MEAL_COUNT_ACCESS_DENIED);
            }
            Year year = editableYear(count.getSchoolYearId());
            if (count.isConfirmed() && !count.hasPending()) {
                throw GlobalException.of(ApiCode.MEAL_COUNT_ALREADY_CONFIRMED);
            }
            Instant at = clock.instant();
            count.confirm(currentUser.userId(), viewer.confirmerRole(), at);
            historyRepository.save(
                    MealCountHistory.of(count.getId(), MealCountHistory.CONFIRMED, currentUser.userId(), at));
            mealCountRepository.flush();
            MealCountResponse response = view(count, viewer, year);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<MealCountResponse> adjust(CurrentUser currentUser, MealAdjustRequest request) {
        String logPrefix = LOG_PREFIX + "|adjust|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            Viewer viewer = viewer(currentUser);
            if (!viewer.adjusts(request.campusId())) {
                throw GlobalException.of(ApiCode.MEAL_COUNT_ACCESS_DENIED);
            }
            List<MealCount> counts =
                    mealCountRepository.findByCampusIdAndDate(request.campusId(), request.date()).stream()
                            .filter(c -> request.session() == null || c.getSession() == request.session())
                            .toList();
            if (counts.isEmpty()) {
                throw GlobalException.of(ApiCode.MEAL_COUNT_NOT_FOUND);
            }
            Year year = editableYear(counts.getFirst().getSchoolYearId());
            Instant at = clock.instant();
            String note = request.note().trim();
            int changed = 0;
            for (MealCount count : counts) {
                // NUT-03: sổ đã xác nhận không sửa ngầm, số mới chờ xác nhận lại
                int[] delta = count.adjust(request.classId(), request.normal(), request.substitute());
                if (delta[0] == 0 && delta[1] == 0) {
                    continue;
                }
                historyRepository.save(
                        MealCountHistory.of(count.getId(), MealCountHistory.ADJUSTED, currentUser.userId(), at)
                                .forClass(request.classId())
                                .change(delta[0], delta[1], note));
                changed++;
            }
            if (changed == 0) {
                throw GlobalException.of(ApiCode.MEAL_COUNT_UNCHANGED);
            }
            mealCountRepository.flush();
            List<MealCountResponse> response =
                    counts.stream().map(c -> view(c, viewer, year)).toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void markAttendanceChanged(LocalDate date, Long campusId, Long classroomId) {
        String logPrefix = LOG_PREFIX + "|markAttendanceChanged|campusId=" + campusId + "|classId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(campusId, date);
            if (!counts.isEmpty()) {
                ClassMealTally tally =
                        attendanceService
                                .tallyMeals(date, campusId, counts.getFirst().getSchoolYearId())
                                .stream()
                                .filter(t -> t.classroomId().equals(classroomId))
                                .findFirst()
                                .orElse(null);
                Instant at = clock.instant();
                for (MealCount count : counts) {
                    if (tally == null || !tally.sessions().contains(count.getSession())) {
                        continue;
                    }
                    boolean changed = count.replaceClassFigures(
                            classroomId,
                            tally.present(),
                            tally.absent(),
                            tally.missing(),
                            tally.meals().getOrDefault(count.getSession(), 0));
                    if (changed) {
                        historyRepository.save(
                                MealCountHistory.of(count.getId(), MealCountHistory.ATTENDANCE_CHANGED, null, at)
                                        .forClass(classroomId));
                    }
                }
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Viewer viewer(CurrentUser user) {
        LocalDate today = LocalDate.now(clock);
        boolean principal = user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today);
        List<StaffAssignmentSummaryResponse> assignments =
                principal ? List.of() : staffAssignmentService.findActiveAssignments(user.userId(), today);
        Set<Long> lead = campusesOf(assignments, user, CareNestRole.VICE_PRINCIPAL, StaffRole.VICE_PRINCIPAL);
        Set<Long> kitchen = campusesOf(assignments, user, CareNestRole.KITCHEN_STAFF, StaffRole.KITCHEN_STAFF);
        if (!principal && lead.isEmpty() && kitchen.isEmpty()) {
            throw GlobalException.of(ApiCode.MEAL_COUNT_ACCESS_DENIED);
        }
        return new Viewer(user, principal, lead, kitchen);
    }

    private static Set<Long> campusesOf(
            List<StaffAssignmentSummaryResponse> assignments,
            CurrentUser user,
            CareNestRole role,
            StaffRole staffRole) {
        if (!user.hasRole(role)) {
            return Set.of();
        }
        return assignments.stream()
                .filter(a -> a.staffRole() == staffRole && a.campusId() != null)
                .map(StaffAssignmentSummaryResponse::campusId)
                .collect(Collectors.toSet());
    }

    private Year year(Long schoolYearId) {
        List<ClassroomInfoResponse> classes = classroomService.findYearClassrooms(schoolYearId);
        if (classes.isEmpty()) {
            return null;
        }
        ClassroomInfoResponse first = classes.getFirst();
        return new Year(first.schoolYearId(), first.yearStatus(), classes);
    }

    // CFG-YR-01: năm đã kết thúc chỉ xem
    private Year editableYear(Long schoolYearId) {
        Year year = year(schoolYearId);
        if (year == null || !year.active()) {
            throw GlobalException.of(ApiCode.MEAL_COUNT_YEAR_READ_ONLY);
        }
        return year;
    }

    private void aggregateMissing(Year year, Long campusId, LocalDate date, Map<String, MealCount> stored) {
        List<MealSession> missing = year.sessions(campusId).stream()
                .filter(s -> !stored.containsKey(key(campusId, s)))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        List<ClassMealTally> tallies = attendanceService.tallyMeals(date, campusId, year.id());
        Instant at = clock.instant();
        for (MealSession session : missing) {
            MealCount count = MealCount.aggregate(campusId, year.id(), date, session, at);
            tallies.stream()
                    .filter(t -> t.sessions().contains(session))
                    .forEach(t -> count.addLine(
                            t.classroomId(),
                            t.present(),
                            t.absent(),
                            t.missing(),
                            t.meals().getOrDefault(session, 0)));
            MealCount saved = mealCountRepository.save(count);
            historyRepository.save(MealCountHistory.of(saved.getId(), MealCountHistory.AGGREGATED, null, at));
            stored.put(key(campusId, session), saved);
        }
    }

    private MealCountResponse view(MealCount count, Viewer viewer, Year year) {
        Map<Long, List<MealCountHistory>> history = history(List.of(count));
        return toView(count, viewer, year, year.classById(), names(List.of(count), history), history);
    }

    private Map<Long, List<MealCountHistory>> history(List<MealCount> counts) {
        List<Long> ids = counts.stream().map(MealCount::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return historyRepository.findByMealCountIdInOrderByAtAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(MealCountHistory::getMealCountId));
    }

    private Map<Long, String> names(List<MealCount> counts, Map<Long, List<MealCountHistory>> history) {
        Collection<Long> userIds = Stream.concat(
                        counts.stream().map(MealCount::getConfirmedBy),
                        history.values().stream().flatMap(List::stream).map(MealCountHistory::getUserId))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        return userIds.isEmpty() ? Map.of() : accountService.findFullNames(userIds);
    }

    private MealCountResponse toView(
            MealCount count,
            Viewer viewer,
            Year year,
            Map<Long, ClassroomInfoResponse> classById,
            Map<Long, String> names,
            Map<Long, List<MealCountHistory>> history) {
        boolean reviewer = viewer.reviews(count.getCampusId());
        List<Line> lines = count.visibleLines(reviewer).stream()
                .map(l -> toLine(l, classById))
                .toList();
        MealCountStatus status = reviewer ? count.reviewStatus() : count.getStatus();
        List<Adjustment> adjustments = history.getOrDefault(count.getId(), List.of()).stream()
                .filter(MealCountHistory::isAdjustment)
                .map(h -> new Adjustment(
                        h.getAt(),
                        h.getAction(),
                        h.getUserId(),
                        nameOf(names, h.getUserId()),
                        h.getClassroomId(),
                        h.getDeltaNormal(),
                        h.getDeltaSubstitute(),
                        h.getNote()))
                .toList();
        return new MealCountResponse(
                count.getId(),
                count.getCampusId(),
                count.getSchoolYearId(),
                count.getDate(),
                count.getSession(),
                status.name(),
                reviewer && count.hasPending(),
                count.getAggregatedAt(),
                count.getConfirmedBy(),
                nameOf(names, count.getConfirmedBy()),
                count.getConfirmedByRole(),
                count.getConfirmedAt(),
                lines,
                totals(lines),
                reviewer ? adjustments : List.of(),
                year.active() && viewer.confirms(count.getCampusId()) && status == MealCountStatus.PENDING_CONFIRMATION,
                year.active() && viewer.adjusts(count.getCampusId()));
    }

    private static MealCountResponse toPreview(
            Long campusId,
            Long schoolYearId,
            LocalDate date,
            MealSession session,
            List<ClassMealTally> tallies,
            Map<Long, ClassroomInfoResponse> classById) {
        List<Line> lines = tallies.stream()
                .filter(t -> t.sessions().contains(session))
                .map(t -> new Line(
                        t.classroomId(),
                        t.className(),
                        ageGroupOf(classById, t.classroomId()),
                        t.present(),
                        t.absent(),
                        t.missing(),
                        t.meals().getOrDefault(session, 0),
                        0))
                .toList();
        return new MealCountResponse(
                null,
                campusId,
                schoolYearId,
                date,
                session,
                OPEN,
                false,
                null,
                null,
                null,
                null,
                null,
                lines,
                totals(lines),
                List.of(),
                false,
                false);
    }

    private static Line toLine(MealCountLine l, Map<Long, ClassroomInfoResponse> classById) {
        ClassroomInfoResponse cls = classById.get(l.getClassroomId());
        return new Line(
                l.getClassroomId(),
                cls == null ? "" : cls.name(),
                cls == null ? null : cls.ageGroupId(),
                l.getPresent(),
                l.getAbsent(),
                l.getMissing(),
                l.getNormal(),
                l.getSubstitute());
    }

    private static Totals totals(List<Line> lines) {
        return new Totals(
                lines.stream().mapToInt(Line::present).sum(),
                lines.stream().mapToInt(Line::absent).sum(),
                lines.stream().mapToInt(Line::missing).sum(),
                lines.stream().mapToInt(Line::normal).sum(),
                lines.stream().mapToInt(Line::substitute).sum());
    }

    private static Long ageGroupOf(Map<Long, ClassroomInfoResponse> classById, Long classroomId) {
        ClassroomInfoResponse cls = classById.get(classroomId);
        return cls == null ? null : cls.ageGroupId();
    }

    // Map bất biến không nhận khóa null (người hủy/tổng hợp tự động không có user)
    private static String nameOf(Map<Long, String> names, Long userId) {
        return userId == null ? null : names.get(userId);
    }

    private static String key(Long campusId, MealSession session) {
        return campusId + ":" + session;
    }
}
