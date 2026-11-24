package com.carenest.service.health.impl;

import com.carenest.dto.health.HealthCheckClassResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.health.HealthCheckAbsence;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckResult;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Quy tắc dùng chung của đợt khám/kỳ cân đo: ngày, phạm vi xem/quản lý, lớp của đợt, danh sách trẻ và kết quả. */
@Component
@RequiredArgsConstructor
class HealthCheckRules {

    // Ngày bắt đầu cách đợt/kỳ khác dưới 2 tháng 15 ngày là "sát nhau" (user chốt 2026-10-31)
    private static final int MIN_GAP_MONTHS = 2;
    private static final int MIN_GAP_DAYS = 15;
    private static final Comparator<HealthMeasurement> LATEST =
            Comparator.comparing(HealthMeasurement::getDate).thenComparing(HealthMeasurement::getId);

    private final Clock clock;
    private final CampusAccessPolicy campusAccessPolicy;
    private final ClassroomAccessPolicy classroomAccessPolicy;
    private final StaffAssignmentService staffAssignmentService;
    private final ClassroomService classroomService;
    private final SchoolStructureService schoolStructureService;
    private final EnrollmentService enrollmentService;
    private final HealthCheckCampaignRepository campaignRepository;
    private final SchoolYearService schoolYearService;
    private final AttendanceService attendanceService;

    /**
     * Một trẻ trong danh sách lớp của đợt/kỳ; {@code measurement} null khi chưa có kết quả. {@code movedTo}: trẻ đã chuyển
     * sang lớp nhận kết quả (dòng chỉ xem, không tính tiến độ); {@code measuredIn}: kết quả đo ở lớp khác (G94-180/HLT-09).
     */
    record Row(
            Long childId,
            Long classId,
            HealthCheckResult result,
            HealthMeasurement measurement,
            HealthCheckClassResponse.MovedTo movedTo,
            HealthCheckClassResponse.MeasuredIn measuredIn) {}

    LocalDate today() {
        return LocalDate.now(clock);
    }

    Instant now() {
        return clock.instant();
    }

    /** HT mọi điểm trường, PHT điểm trường được phân công (user chốt 2026-10-31). */
    boolean canManage(CurrentUser user, Long campusId) {
        return (user.hasRole(CareNestRole.PRINCIPAL) || user.hasRole(CareNestRole.VICE_PRINCIPAL))
                && campusAccessPolicy.canViewCampus(user, campusId);
    }

    /** Điểm trường người dùng xem được đợt/kỳ; null = mọi điểm trường (HT). GV: điểm trường của lớp mình. */
    Set<Long> visibleCampusIds(CurrentUser user) {
        LocalDate today = today();
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today)) {
            return null;
        }
        Set<Long> campusIds = new HashSet<>();
        if (user.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            campusIds.addAll(
                    staffAssignmentService.findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today));
        }
        if (user.hasRole(CareNestRole.TEACHER)) {
            campusIds.addAll(staffAssignmentService.findActiveCampusIds(user.userId(), StaffRole.TEACHER, today));
        }
        return campusIds;
    }

    /** Ngoài phạm vi trả 404 như không tồn tại (không lộ đợt của điểm trường khác). */
    HealthCheckCampaign findVisible(CurrentUser user, Long campaignId) {
        HealthCheckCampaign campaign = campaignRepository
                .findById(campaignId)
                .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_CHECK_NOT_FOUND));
        Set<Long> visible = visibleCampusIds(user);
        if (visible != null && !visible.contains(campaign.getCampusId())) {
            throw GlobalException.of(ApiCode.HEALTH_CHECK_NOT_FOUND);
        }
        return campaign;
    }

    HealthCheckCampaign findManageable(CurrentUser user, Long campaignId) {
        HealthCheckCampaign campaign = findVisible(user, campaignId);
        if (!canManage(user, campaign.getCampusId())) {
            throw GlobalException.of(ApiCode.HEALTH_CHECK_ACCESS_DENIED);
        }
        return campaign;
    }

    /** G94-180/HLT-08: một ngày thuộc năm học gần nhất đã bắt đầu tính tới ngày đó; null khi trước mọi năm học. */
    Long schoolYearIdOf(LocalDate date) {
        return schoolStructureService.findSchoolYears().stream()
                .filter(y -> y.startDate() != null && !y.startDate().isAfter(date))
                .max(Comparator.comparing(SchoolYearResponse::startDate))
                .map(SchoolYearResponse::id)
                .orElse(null);
    }

    /** G94-180/HLT-08 (user chốt 2026-11-22): năm lưu lúc tạo; đợt chưa có (seed, test chèn thẳng) ⇒ theo ngày bắt đầu. */
    Long yearOf(HealthCheckCampaign campaign) {
        return campaign.getSchoolYearId() != null
                ? campaign.getSchoolYearId()
                : schoolYearIdOf(campaign.getStartDate());
    }

    /** Khoảng ngày của một năm: từ ngày bắt đầu tới trước ngày bắt đầu năm kế tiếp (until null = chưa có năm sau). */
    record YearWindow(Long yearId, String name, LocalDate from, LocalDate until) {

        boolean contains(LocalDate date) {
            return !date.isBefore(from) && (until == null || date.isBefore(until));
        }

        String message() {
            return "Ngày khám phải trong năm học " + name + " ("
                    + (until == null
                            ? "từ " + DMY.format(from)
                            : DMY.format(from) + " – " + DMY.format(until.minusDays(1)))
                    + ")";
        }
    }

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Năm đang hoạt động; null khi chưa có (đợt mới khi đó thuộc năm của ngày bắt đầu, không giới hạn ngày). */
    YearWindow activeYearWindow() {
        List<SchoolYearResponse> years = sortedYears();
        for (int i = 0; i < years.size(); i++) {
            boolean active = schoolYearService
                    .findSchoolYearDetail(years.get(i).id())
                    .map(d -> d.status() == SchoolYearStatus.ACTIVE)
                    .orElse(false);
            if (active) {
                return windowAt(years, i);
            }
        }
        return null;
    }

    /** Khoảng ngày của năm đã lưu trên đợt; null khi năm không còn. */
    YearWindow windowOf(Long yearId) {
        List<SchoolYearResponse> years = sortedYears();
        for (int i = 0; i < years.size(); i++) {
            if (years.get(i).id().equals(yearId)) {
                return windowAt(years, i);
            }
        }
        return null;
    }

    private List<SchoolYearResponse> sortedYears() {
        return schoolStructureService.findSchoolYears().stream()
                .filter(y -> y.startDate() != null)
                .sorted(Comparator.comparing(SchoolYearResponse::startDate))
                .toList();
    }

    private static YearWindow windowAt(List<SchoolYearResponse> years, int i) {
        SchoolYearResponse year = years.get(i);
        LocalDate until = i + 1 < years.size() ? years.get(i + 1).startDate() : null;
        return new YearWindow(year.id(), year.name(), year.startDate(), until);
    }

    /** Lớp của điểm trường trong năm học của đợt/kỳ (năm đã lưu, xem {@link #yearOf}). */
    List<ClassroomInfoResponse> classesOf(HealthCheckCampaign campaign) {
        Long yearId = yearOf(campaign);
        if (yearId == null) {
            return List.of();
        }
        return classroomService.findYearClassrooms(yearId).stream()
                .filter(c -> c.campusId().equals(campaign.getCampusId()))
                .sorted(Comparator.comparing(ClassroomInfoResponse::name))
                .toList();
    }

    List<ClassroomInfoResponse> visibleClassesOf(CurrentUser user, HealthCheckCampaign campaign) {
        return classesOf(campaign).stream()
                .filter(c -> classroomAccessPolicy.canViewClassroom(user, c.id()))
                .toList();
    }

    /** HLT-09: GV đang được phân công lớp nhập kết quả cho lớp mình. */
    boolean canRecord(CurrentUser user, ClassroomInfoResponse cls) {
        return teaches(user, cls.id());
    }

    /** GV đang được phân công lớp (nhập số đo, phiếu khám cho trẻ lớp mình). */
    boolean teaches(CurrentUser user, Long classId) {
        return classId != null
                && user.hasRole(CareNestRole.TEACHER)
                && staffAssignmentService.hasActiveTeacherAssignment(user.userId(), classId, today());
    }

    /**
     * HLT-01 (user chốt 2026-11-24): trẻ có điểm danh vắng (có phép, không phép) ngày {@code date} từ thứ 2 tới thứ 6; thứ
     * 7, chủ nhật và ngày chưa điểm danh không tính vắng.
     */
    Set<Long> absentOn(Collection<Long> childIds, LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (childIds.isEmpty() || day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return Set.of();
        }
        return attendanceService.findDayStatuses(childIds, date, date).stream()
                .filter(s -> s.status().isAbsent())
                .map(AttendanceService.ChildDayStatus::childId)
                .collect(Collectors.toSet());
    }

    /** HLT-01: trẻ vắng ngày đo, ngày khám thì không nhập số đo mới, không mở phiếu khám mới. */
    void checkPresent(Long childId, LocalDate date) {
        if (absentOn(List.of(childId), date).contains(childId)) {
            throw GlobalException.of(ApiCode.HEALTH_CHILD_ABSENT, date);
        }
    }

    /**
     * G94-180/HLT-09 (user chốt 2026-11-24): kỳ liên quan của kỳ cân đo — cùng loại, cùng năm học, ở điểm trường khác,
     * chưa hủy, khoảng ngày giao nhau. Trẻ chuyển điểm trường đã đo ở kỳ liên quan (ngày đo trong khoảng của kỳ) tính là
     * đã có kết quả của kỳ.
     */
    List<HealthCheckCampaign> linkedCampaigns(HealthCheckCampaign campaign) {
        // Đợt khám không nối: phiếu khám mở theo đợt của điểm trường trẻ đang học, trẻ sang điểm trường khác khám ở đợt
        // ở đó
        if (campaign.getKind() != HealthCheckKind.MEASUREMENT) {
            return List.of();
        }
        Long yearId = yearOf(campaign);
        return campaignRepository
                .findLinkable(
                        campaign.getKind(), campaign.getCampusId(), campaign.getStartDate(), campaign.getEndDate())
                .stream()
                .filter(c -> Objects.equals(yearOf(c), yearId))
                .toList();
    }

    /** Lần đo của trẻ ở kỳ liên quan có ngày đo trong khoảng của đợt/kỳ (mới nhất); rỗng khi không có. */
    Optional<HealthMeasurement> linkedResultOf(
            HealthCheckCampaign campaign, List<HealthCheckCampaign> linked, List<HealthMeasurement> childMeasurements) {
        Set<Long> linkedIds = linked.stream().map(HealthCheckCampaign::getId).collect(Collectors.toSet());
        return childMeasurements.stream()
                .filter(m -> linkedIds.contains(m.getCampaignId()) && covers(campaign, m.getDate()))
                .max(LATEST);
    }

    /**
     * G94-180/HLT-09 (user chốt 2026-11-24): lớp được sửa số đo là lớp hiện tại của trẻ (tại {@link #referenceDate} của kỳ)
     * khi lớp đó nhận số đo — cùng điểm trường của kỳ, hoặc điểm trường có kỳ liên quan đang mở chứa ngày đo; không thì
     * lớp đã đo. Rỗng: trẻ đã sang điểm trường có kỳ liên quan đã đóng, không ai sửa.
     */
    Optional<ClassroomInfoResponse> editingClassOf(HealthMeasurement m, HealthCheckCampaign round) {
        ClassroomInfoResponse measured = classroomService
                .findClassroomInfo(m.getClassroomId())
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
        ClassroomInfoResponse current =
                enrollmentService.findActiveClassroomIds(m.getChildId(), referenceDate(round)).stream()
                        .findFirst()
                        .flatMap(classroomService::findClassroomInfo)
                        .orElse(null);
        if (current == null || current.id().equals(measured.id())) {
            return Optional.of(measured);
        }
        if (current.campusId().equals(round.getCampusId())) {
            return Optional.of(current);
        }
        List<HealthCheckCampaign> there = linkedCampaigns(round).stream()
                .filter(l -> l.getCampusId().equals(current.campusId()) && covers(l, m.getDate()))
                .toList();
        if (there.isEmpty()) {
            return Optional.of(measured);
        }
        return there.stream().anyMatch(l -> l.statusOn(today()) == HealthCheckStatus.IN_PROGRESS)
                ? Optional.of(current)
                : Optional.empty();
    }

    String campusName(Long campusId) {
        return schoolStructureService.findCampusNames(Set.of(campusId)).get(campusId);
    }

    /** Danh sách các lớp của đợt/kỳ; {@code measurements}: lần đo của đợt/kỳ và của các kỳ liên quan. */
    Roster rosterOf(
            HealthCheckCampaign campaign,
            List<HealthCheckCampaign> linked,
            List<HealthMeasurement> measurements,
            List<HealthCheckAbsence> absences) {
        return new Roster(campaign, linked, measurements, absences);
    }

    /**
     * Dựng danh sách từng lớp của một đợt/kỳ. Kết quả của trẻ: lần đo của đợt/kỳ (lớp nào cũng được), không có thì lần đo ở
     * kỳ liên quan có ngày đo trong khoảng của đợt/kỳ; lớp của trẻ tính tại {@link #referenceDate} (G94-180/HLT-09).
     */
    final class Roster {
        private final HealthCheckCampaign campaign;
        private final List<HealthCheckCampaign> linked;
        private final List<HealthCheckAbsence> absences;
        private final boolean closed;
        private final LocalDate referenceDate;
        private final Map<Long, HealthMeasurement> resultByChild;
        private final Map<Long, Long> classIdByChild;
        private final Map<Long, Optional<ClassroomInfoResponse>> classes = new HashMap<>();
        private final Map<Long, String> campusNames = new HashMap<>();

        private Roster(
                HealthCheckCampaign campaign,
                List<HealthCheckCampaign> linked,
                List<HealthMeasurement> measurements,
                List<HealthCheckAbsence> absences) {
            this.campaign = campaign;
            this.linked = linked;
            this.absences = absences;
            this.closed = campaign.statusOn(today()) == HealthCheckStatus.CLOSED;
            this.referenceDate = referenceDate(campaign);
            Set<Long> linkedIds =
                    linked.stream().map(HealthCheckCampaign::getId).collect(Collectors.toSet());
            Map<Long, HealthMeasurement> results = new HashMap<>();
            measurements.stream()
                    .filter(m -> linkedIds.contains(m.getCampaignId()) && covers(campaign, m.getDate()))
                    .sorted(LATEST)
                    .forEach(m -> results.put(m.getChildId(), m));
            // Lần đo của chính đợt/kỳ được ưu tiên hơn kỳ liên quan
            measurements.stream()
                    .filter(m -> m.getCampaignId().equals(campaign.getId()))
                    .forEach(m -> results.put(m.getChildId(), m));
            this.resultByChild = results;
            this.classIdByChild = results.isEmpty()
                    ? Map.of()
                    : enrollmentService.findActiveClassroomIdByChildIds(results.keySet(), referenceDate);
        }

        /**
         * Trẻ của lớp: chưa đóng ⇒ trẻ đang học lớp và kết quả của trẻ; đã đóng ⇒ trẻ của lớp có kết quả và trẻ ghi vắng
         * lúc đóng. Trẻ đo ở lớp này nay học lớp nhận số đo ⇒ dòng {@code movedTo}; nay không có lớp nhận (nghỉ học, điểm
         * trường mới không có kỳ liên quan) ⇒ kết quả vẫn của lớp này.
         */
        List<Row> rowsOf(ClassroomInfoResponse cls) {
            Set<Long> absent = absences.stream()
                    .filter(a -> a.getCampaignId().equals(campaign.getId())
                            && a.getClassroomId().equals(cls.id()))
                    .map(HealthCheckAbsence::getChildId)
                    .collect(Collectors.toSet());
            Map<Long, Row> rows = new LinkedHashMap<>();
            if (!closed) {
                enrollmentService
                        .findChildrenInClassroom(cls.id(), referenceDate)
                        .forEach(c -> rows.put(c.id(), memberRow(c.id(), cls, absent)));
            }
            resultByChild.forEach((childId, m) -> {
                if (rows.containsKey(childId)) {
                    return;
                }
                if (cls.id().equals(classIdByChild.get(childId))) {
                    rows.put(childId, memberRow(childId, cls, absent));
                } else if (m.getCampaignId().equals(campaign.getId())
                        && m.getClassroomId().equals(cls.id())) {
                    rows.put(childId, new Row(childId, cls.id(), resultOf(m, closed), m, movedTo(m), null));
                }
            });
            if (closed) {
                absent.forEach(
                        id -> rows.putIfAbsent(id, new Row(id, cls.id(), HealthCheckResult.ABSENT, null, null, null)));
            }
            return List.copyOf(rows.values());
        }

        private Row memberRow(Long childId, ClassroomInfoResponse cls, Set<Long> absent) {
            HealthMeasurement m = resultByChild.get(childId);
            HealthCheckResult result = m != null
                    ? resultOf(m, closed)
                    : absent.contains(childId) ? HealthCheckResult.ABSENT : HealthCheckResult.NOT_RECORDED;
            boolean elsewhere = m != null && !m.getClassroomId().equals(cls.id());
            return new Row(childId, cls.id(), result, m, null, elsewhere ? measuredIn(m) : null);
        }

        private HealthCheckClassResponse.MeasuredIn measuredIn(HealthMeasurement m) {
            ClassroomInfoResponse measured = classInfo(m.getClassroomId());
            return new HealthCheckClassResponse.MeasuredIn(
                    measured == null ? null : measured.name(),
                    measured == null ? null : campusOf(measured),
                    m.getDate());
        }

        // Lớp hiện tại nhận số đo: cùng điểm trường của đợt/kỳ, hoặc điểm trường có kỳ liên quan chứa ngày đo
        private HealthCheckClassResponse.MovedTo movedTo(HealthMeasurement m) {
            ClassroomInfoResponse current = classInfo(classIdByChild.get(m.getChildId()));
            boolean takesOver = current != null
                    && (current.campusId().equals(campaign.getCampusId())
                            || linked.stream()
                                    .anyMatch(
                                            l -> l.getCampusId().equals(current.campusId()) && covers(l, m.getDate())));
            return takesOver
                    ? new HealthCheckClassResponse.MovedTo(
                            current.name(), campusOf(current), joinedOn(m.getChildId(), current.id(), m.getDate()))
                    : null;
        }

        // Ngày vào lớp hiện tại: đoạn học cuối ở lớp đó (trẻ còn ở lớp cũ ngày đo nên đoạn này bắt đầu sau ngày đo)
        private LocalDate joinedOn(Long childId, Long classId, LocalDate measuredOn) {
            LocalDate from = measuredOn.isBefore(referenceDate) ? measuredOn : referenceDate;
            return enrollmentService.findChildStays(childId, from, referenceDate).stream()
                    .filter(s -> s.classroomId().equals(classId))
                    .map(EnrollmentService.ChildClassStay::from)
                    .reduce((first, last) -> last)
                    .orElse(referenceDate);
        }

        private ClassroomInfoResponse classInfo(Long classId) {
            return classId == null
                    ? null
                    : classes.computeIfAbsent(classId, classroomService::findClassroomInfo)
                            .orElse(null);
        }

        private String campusOf(ClassroomInfoResponse cls) {
            return campusNames.computeIfAbsent(cls.campusId(), HealthCheckRules.this::campusName);
        }
    }

    private static boolean covers(HealthCheckCampaign campaign, LocalDate date) {
        return !date.isBefore(campaign.getStartDate()) && !date.isAfter(campaign.getEndDate());
    }

    /** Phiếu khám còn nháp chưa là kết quả: đang khám, đợt đã đóng thì chưa kết luận. */
    private static HealthCheckResult resultOf(HealthMeasurement m, boolean closed) {
        if (m.isDraft()) {
            return closed ? HealthCheckResult.NOT_CONCLUDED : HealthCheckResult.IN_PROGRESS;
        }
        return HealthCheckResult.RECORDED;
    }

    /**
     * Hôm nay, kẹp trong khoảng ngày của đợt/kỳ: ngày lấy sĩ số lớp, cũng là ngày đo/ngày khám hệ thống ghi khi nhập mới
     * (HLT-01, HLT-08) để trẻ trong danh sách lớp khớp lớp của lần đo.
     */
    LocalDate referenceDate(HealthCheckCampaign campaign) {
        LocalDate today = today();
        if (today.isBefore(campaign.getStartDate())) {
            return campaign.getStartDate();
        }
        return today.isAfter(campaign.getEndDate()) ? campaign.getEndDate() : today;
    }

    /** Ngày bắt đầu sớm nhất không còn "sát" ngày date. */
    static LocalDate gapEndOf(LocalDate date) {
        return date.plusMonths(MIN_GAP_MONTHS).plusDays(MIN_GAP_DAYS);
    }

    static String kindLabel(HealthCheckKind kind) {
        return kind == HealthCheckKind.MEASUREMENT ? "kỳ cân đo" : "đợt khám";
    }
}
