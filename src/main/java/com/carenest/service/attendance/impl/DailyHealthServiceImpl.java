package com.carenest.service.attendance.impl;

import com.carenest.dto.attendance.DailyHealthEntryResponse;
import com.carenest.dto.attendance.DailyHealthRequest;
import com.carenest.dto.attendance.DailyHealthResponse.Attention;
import com.carenest.dto.attendance.DailyHealthResponse.ChildDay;
import com.carenest.dto.attendance.DailyHealthResponse.ClassOption;
import com.carenest.dto.attendance.DailyHealthResponse.ClassRow;
import com.carenest.dto.attendance.DailyHealthResponse.Option;
import com.carenest.dto.attendance.DailyHealthResponse.Options;
import com.carenest.dto.attendance.DailyHealthResponse.Overview;
import com.carenest.dto.attendance.DailyHealthResponse.Totals;
import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.entity.attendance.DailyHealthEntry;
import com.carenest.exception.GlobalException;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.DailyHealthEntryRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthAction;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DailyHealthServiceImpl implements DailyHealthService {
    private static final String LOG_PREFIX = "[DailyHealthServiceImpl]";

    private final DailyHealthEntryRepository entryRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceDayRules rules;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final SchoolStructureService schoolStructureService;
    private final AccountService accountService;

    @Override
    @Transactional(readOnly = true)
    public ChildDay getClassChildHealth(CurrentUser currentUser, Long classId, Long childId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getClassChildHealth|userId=" + currentUser.userId() + "|classId=" + classId
                + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate day = date == null ? rules.today() : date;
            ClassroomInfoResponse cls = viewableClass(currentUser, classId);
            ClassChildResponse child = childInClass(cls.id(), childId, day);
            List<DailyHealthEntryResponse> entries =
                    toViews(entryRepository.findByChildIdAndDateOrderByRecordedAtDescIdDesc(childId, day).stream()
                            .filter(e -> e.getClassroomId().equals(cls.id()))
                            .toList());
            ChildDay result = new ChildDay(
                    cls.id(),
                    cls.name(),
                    child.id(),
                    child.fullName(),
                    day,
                    canRecordFor(currentUser, cls, childId, day),
                    entries);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public DailyHealthEntryResponse addEntry(CurrentUser currentUser, DailyHealthRequest request) {
        String logPrefix = LOG_PREFIX + "|addEntry|userId=" + currentUser.userId() + "|classId=" + request.classId()
                + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        try {
            // Gửi lặp cùng requestId ⇒ trả lại lần đã lưu, không tạo dòng mới
            var previous = entryRepository.findByRecordedByAndRequestId(currentUser.userId(), request.requestId());
            if (previous.isPresent()) {
                DailyHealthEntryResponse same = toViews(List.of(previous.get())).getFirst();
                log.info("{}|END", logPrefix);
                return same;
            }
            ClassroomInfoResponse cls = classroomService
                    .findClassroomInfo(request.classId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
            if (!rules.canRecord(currentUser, cls)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.HEALTH_YEAR_NOT_ACTIVE);
            }
            childInClass(cls.id(), request.childId(), request.date());
            // HLT-07: ghi cả sau giờ chốt, khóa khi hết ngày
            // TODO(Cần): khi có BE Đón trẻ, khóa từ lúc trẻ được trả cho phụ huynh (HLT-07)
            if (!request.date().equals(rules.today())) {
                throw GlobalException.of(ApiCode.HEALTH_TODAY_ONLY);
            }
            if (!isPresent(request.childId(), cls.id(), request.date())) {
                throw GlobalException.of(ApiCode.HEALTH_CHILD_NOT_PRESENT);
            }
            boolean noted = request.observation() == HealthObservation.NOTED;
            List<HealthSymptom> symptoms = noted ? distinct(request.symptoms()) : List.of();
            List<HealthAction> actions = distinct(request.actions());
            String note = request.note() == null ? "" : request.note().trim();
            if (noted && symptoms.isEmpty() && note.isEmpty()) {
                throw GlobalException.of(ApiCode.HEALTH_NOTE_REQUIRED);
            }
            if (!noted && !actions.isEmpty()) {
                throw GlobalException.of(ApiCode.HEALTH_ACTIONS_NOT_ALLOWED);
            }
            DailyHealthRequest.Referral referral = actions.contains(HealthAction.REFERRAL) ? request.referral() : null;
            if (actions.contains(HealthAction.REFERRAL)
                    && (referral == null || blank(referral.place()) || referral.time() == null)) {
                throw GlobalException.of(ApiCode.HEALTH_REFERRAL_INCOMPLETE);
            }
            DailyHealthEntry entry = entryRepository.save(DailyHealthEntry.builder()
                    .requestId(request.requestId())
                    .childId(request.childId())
                    .classroomId(cls.id())
                    .campusId(cls.campusId())
                    .date(request.date())
                    .temperature(request.temperature())
                    .observation(request.observation())
                    .symptoms(symptoms)
                    .note(note)
                    .actions(actions)
                    .referralPlace(referral == null ? null : referral.place().trim())
                    .referralTime(referral == null ? null : referral.time())
                    .referralEscort(
                            referral == null || blank(referral.escort())
                                    ? null
                                    : referral.escort().trim())
                    .recordedBy(currentUser.userId())
                    .recordedAt(rules.now())
                    .build());
            DailyHealthEntryResponse saved = toViews(List.of(entry)).getFirst();
            log.info("{}|END", logPrefix);
            return saved;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Overview getOverview(
            CurrentUser currentUser, LocalDate date, Long schoolYearId, Long campusId, Long ageGroupId, Long classId) {
        String logPrefix = LOG_PREFIX + "|getOverview|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            checkLeader(currentUser);
            if (date == null) {
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "ngày");
            }
            if (campusId != null && !rules.canUnlock(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            List<ClassroomInfoResponse> scoped = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> campusId == null || c.campusId().equals(campusId))
                    .filter(c -> rules.canView(currentUser, c))
                    .toList();
            Set<Long> scopedAgeGroups =
                    scoped.stream().map(ClassroomInfoResponse::ageGroupId).collect(Collectors.toSet());
            Options options = new Options(
                    schoolStructureService.findAgeGroups().stream()
                            .filter(g -> scopedAgeGroups.contains(g.id()))
                            .map(g -> new Option(g.id(), g.name()))
                            .toList(),
                    scoped.stream()
                            .map(c -> new ClassOption(c.id(), c.name(), c.ageGroupId(), c.campusId()))
                            .toList());
            List<ClassroomInfoResponse> classes = scoped.stream()
                    .filter(c -> ageGroupId == null || ageGroupId.equals(c.ageGroupId()))
                    .filter(c -> classId == null || classId.equals(c.id()))
                    .toList();
            List<Long> classIds =
                    classes.stream().map(ClassroomInfoResponse::id).toList();
            Map<Long, List<AttendanceRecord>> presentByClass =
                    recordRepository.findByClassroomIdInAndDate(classIds, date).stream()
                            .filter(r -> r.getStatus() == AttendanceStatus.PRESENT)
                            .collect(Collectors.groupingBy(AttendanceRecord::getClassroomId));
            List<DailyHealthEntryResponse> dayEntries =
                    toViews(entryRepository.findByClassroomIdInAndDateOrderByRecordedAtDescIdDesc(classIds, date));

            int[] totals = new int[3];
            List<ClassRow> rows = new ArrayList<>();
            List<Attention> attention = new ArrayList<>();
            for (ClassroomInfoResponse cls : classes) {
                Map<Long, ClassChildResponse> children =
                        enrollmentService.findChildrenInClassroom(cls.id(), date).stream()
                                .collect(Collectors.toMap(ClassChildResponse::id, Function.identity()));
                Set<Long> present = presentByClass.getOrDefault(cls.id(), List.of()).stream()
                        .map(AttendanceRecord::getChildId)
                        .collect(Collectors.toSet());
                // Lần mới nhất của mỗi trẻ (dayEntries đã sắp mới nhất trước)
                Map<Long, List<DailyHealthEntryResponse>> byChild = new LinkedHashMap<>();
                dayEntries.stream()
                        .filter(e -> e.classId().equals(cls.id()))
                        .forEach(e -> byChild.computeIfAbsent(e.childId(), k -> new ArrayList<>())
                                .add(e));
                int checked =
                        (int) present.stream().filter(byChild::containsKey).count();
                int noted = 0;
                for (Map.Entry<Long, List<DailyHealthEntryResponse>> e : byChild.entrySet()) {
                    DailyHealthEntryResponse latest = e.getValue().getFirst();
                    if (latest.observation() != HealthObservation.NOTED) {
                        continue;
                    }
                    noted++;
                    ClassChildResponse child = children.get(e.getKey());
                    List<DailyHealthEntryResponse> readings = new ArrayList<>(e.getValue());
                    Collections.reverse(readings);
                    attention.add(new Attention(
                            e.getKey(),
                            child == null ? null : child.fullName(),
                            cls.id(),
                            cls.name(),
                            cls.campusId(),
                            latest,
                            readings));
                }
                totals[0] += present.size();
                totals[1] += checked;
                totals[2] += noted;
                rows.add(new ClassRow(
                        cls.id(), cls.name(), cls.campusId(), children.size(), present.size(), checked, noted));
            }
            attention.sort(Comparator.comparing((Attention a) -> a.latest().recordedAt())
                    .reversed());
            Overview overview = new Overview(
                    date,
                    campusId,
                    AttendanceDayRules.isSchoolDay(date),
                    options,
                    // Chưa có module thông báo phụ huynh ⇒ notified = 0
                    new Totals(totals[0], totals[1], totals[2], 0),
                    rows,
                    attention);
            log.info("{}|END", logPrefix);
            return overview;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChildDay getChildHistory(CurrentUser currentUser, Long childId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getChildHistory|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkLeader(currentUser);
            if (date == null) {
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "ngày");
            }
            List<DailyHealthEntry> entries =
                    entryRepository.findByChildIdAndDateOrderByRecordedAtDescIdDesc(childId, date);
            Long classId = entries.isEmpty()
                    ? enrollmentService.findActiveClassroomIds(childId, date).stream()
                            .findFirst()
                            .orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_FOUND))
                    : entries.getFirst().getClassroomId();
            ClassroomInfoResponse cls = viewableClass(currentUser, classId);
            String childName = enrollmentService.findChildrenInClassroom(classId, date).stream()
                    .filter(c -> c.id().equals(childId))
                    .map(ClassChildResponse::fullName)
                    .findFirst()
                    .orElse(null);
            ChildDay result = new ChildDay(cls.id(), cls.name(), childId, childName, date, false, toViews(entries));
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static void checkLeader(CurrentUser user) {
        if (!user.hasRole(CareNestRole.PRINCIPAL) && !user.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            throw GlobalException.of(ApiCode.HEALTH_OVERVIEW_ACCESS_DENIED);
        }
    }

    private ClassroomInfoResponse viewableClass(CurrentUser user, Long classId) {
        ClassroomInfoResponse cls = classroomService
                .findClassroomInfo(classId)
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
        if (!rules.canView(user, cls)) {
            throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
        }
        return cls;
    }

    private ClassChildResponse childInClass(Long classId, Long childId, LocalDate date) {
        return enrollmentService.findChildrenInClassroom(classId, date).stream()
                .filter(c -> c.id().equals(childId))
                .findFirst()
                .orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_IN_CLASS));
    }

    private boolean isPresent(Long childId, Long classId, LocalDate date) {
        return recordRepository.findByChildIdInAndDate(List.of(childId), date).stream()
                .anyMatch(r -> r.getClassroomId().equals(classId) && r.getStatus() == AttendanceStatus.PRESENT);
    }

    private boolean canRecordFor(CurrentUser user, ClassroomInfoResponse cls, Long childId, LocalDate date) {
        return cls.yearStatus() == SchoolYearStatus.ACTIVE
                && date.equals(rules.today())
                && rules.canRecord(user, cls)
                && isPresent(childId, cls.id(), date);
    }

    private List<DailyHealthEntryResponse> toViews(List<DailyHealthEntry> entries) {
        Map<Long, String> names = accountService.findFullNames(
                entries.stream().map(DailyHealthEntry::getRecordedBy).collect(Collectors.toSet()));
        return entries.stream()
                .map(e -> new DailyHealthEntryResponse(
                        e.getId(),
                        e.getRequestId(),
                        e.getClassroomId(),
                        e.getChildId(),
                        e.getDate(),
                        e.getTemperature(),
                        e.getObservation(),
                        e.getSymptoms(),
                        e.getNote(),
                        e.getActions(),
                        e.getReferralPlace() == null
                                ? null
                                : new DailyHealthEntryResponse.Referral(
                                        e.getReferralPlace(), e.getReferralTime(), e.getReferralEscort()),
                        e.getNotificationStatus(),
                        e.getRecordedBy(),
                        names.get(e.getRecordedBy()),
                        e.getRecordedAt()))
                .toList();
    }

    private static <E extends Enum<E>> List<E> distinct(List<E> values) {
        return values == null
                ? List.of()
                : values.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChildDaySigns> findLatestDaySigns(Collection<Long> childIds, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|findLatestDaySigns|children=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            // Danh sách xếp mới nhất trước: lấy lần ghi nhận đầu tiên của mỗi trẻ / ngày
            Map<String, ChildDaySigns> latest = new LinkedHashMap<>();
            if (!childIds.isEmpty()) {
                entryRepository
                        .findByChildIdInAndDateBetweenOrderByRecordedAtDescIdDesc(childIds, from, to)
                        .forEach(e -> latest.putIfAbsent(
                                e.getChildId() + "|" + e.getDate(),
                                new ChildDaySigns(
                                        e.getChildId(),
                                        e.getDate(),
                                        e.getObservation() == HealthObservation.NOTED ? e.getSymptoms() : List.of(),
                                        e.getNote())));
            }
            List<ChildDaySigns> result = List.copyOf(latest.values());
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
