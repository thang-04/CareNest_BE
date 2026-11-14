package com.carenest.service.observation.impl;

import com.carenest.dto.observation.ClassRatingDtos.ClassRatingItem;
import com.carenest.dto.observation.ClassRatingDtos.ClassRatingResponse;
import com.carenest.dto.observation.ClassRatingDtos.ClassRatingRow;
import com.carenest.dto.observation.ClassRatingDtos.ClassRatingSaveRequest;
import com.carenest.dto.observation.ClassRatingDtos.Levels;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.entity.observation.ClassRating;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.ClassRatingRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.observation.ClassRatingService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ClassRatingLevel;
import com.carenest.utils.enums.ClassRatingPeriod;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassRatingServiceImpl implements ClassRatingService {
    private static final String LOG_PREFIX = "[ClassRatingServiceImpl]";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final AgeGroupService ageGroupService;
    private final SchoolYearService schoolYearService;
    private final AccountService accountService;
    private final ClassRatingRepository ratingRepository;

    @Override
    @Transactional(readOnly = true)
    public ClassRatingResponse list(
            CurrentUser currentUser, Long schoolYearId, ClassRatingPeriod period, Long campusId) {
        String logPrefix = LOG_PREFIX + "|list|userId=" + currentUser.userId() + "|period=" + period;
        log.info("{}|START", logPrefix);
        try {
            ClassRatingResponse response = listOf(currentUser, schoolYearId, period, campusId);
            log.info("{}|rows={}|END", logPrefix, response.rows().size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ClassRatingResponse save(CurrentUser currentUser, ClassRatingSaveRequest request) {
        String logPrefix = LOG_PREFIX + "|save|userId=" + currentUser.userId() + "|period=" + request.period()
                + "|items=" + request.items().size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, ClassroomInfoResponse> classes = request.items().stream()
                    .map(ClassRatingItem::classId)
                    .distinct()
                    .collect(Collectors.toMap(Function.identity(), rules::findClass));
            Long yearId = null;
            for (ClassroomInfoResponse cls : classes.values()) {
                if (!canRate(currentUser, cls)) {
                    throw GlobalException.of(ApiCode.CLASS_RATING_FORBIDDEN, cls.name());
                }
                // CFG-YR-01: chỉ xếp loại lớp của năm đang hoạt động; năm sắp tới / đã kết thúc chỉ xem
                if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                    throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
                }
                if (yearId != null && !yearId.equals(cls.schoolYearId())) {
                    throw GlobalException.of(ApiCode.CLASS_RATING_MIXED_YEARS);
                }
                yearId = cls.schoolYearId();
            }
            LocalDate openFrom = openFrom(currentUser, yearId, request.period());
            if (openFrom == null || rules.today().isBefore(openFrom)) {
                throw GlobalException.of(
                        ApiCode.CLASS_RATING_NOT_OPEN,
                        openFrom == null ? "đặt ở Cấu hình thời gian" : DATE.format(openFrom));
            }
            for (ClassRatingItem item : request.items()) {
                Levels l = item.levels();
                boolean anyCriterion = Stream.of(
                                l.records(),
                                l.program(),
                                l.theme(),
                                l.environment(),
                                l.routine(),
                                l.health(),
                                l.parents())
                        .anyMatch(Objects::nonNull);
                if (anyCriterion && l.overall() == null) {
                    throw GlobalException.of(
                            ApiCode.CLASS_RATING_OVERALL_REQUIRED,
                            classes.get(item.classId()).name());
                }
            }
            Map<Long, ClassRating> existing =
                    ratingRepository.findByClassroomIdInAndPeriod(classes.keySet(), request.period()).stream()
                            .collect(Collectors.toMap(ClassRating::getClassroomId, r -> r));
            Instant at = rules.now();
            for (ClassRatingItem item : request.items()) {
                ClassroomInfoResponse cls = classes.get(item.classId());
                ClassRating rating = existing.computeIfAbsent(
                        cls.id(), id -> ClassRating.create(id, cls.campusId(), cls.schoolYearId(), request.period()));
                Levels l = item.levels();
                rating.rate(
                        new ClassRatingLevel[] {
                            l.records(),
                            l.program(),
                            l.theme(),
                            l.environment(),
                            l.routine(),
                            l.health(),
                            l.parents(),
                            l.overall()
                        },
                        blankToNull(item.note()),
                        currentUser.userId(),
                        at);
            }
            try {
                ratingRepository.saveAllAndFlush(existing.values());
            } catch (DataIntegrityViolationException ex) {
                // Hai người cùng lưu lần đầu một lớp: ràng buộc (lớp, kỳ) chặn bản thứ hai
                throw GlobalException.withCause(ApiCode.CONCURRENT_UPDATE, ex);
            }
            ClassRatingResponse response = listOf(currentUser, yearId, request.period(), null);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private ClassRatingResponse listOf(CurrentUser user, Long schoolYearId, ClassRatingPeriod period, Long campusId) {
        Set<Long> ledGroups = rules.teamLeaderAgeGroupIds(user, schoolYearId);
        List<ClassroomInfoResponse> visible = classroomService.findYearClassrooms(schoolYearId).stream()
                .filter(c -> canRead(user, c) || ledGroups.contains(c.ageGroupId()))
                .toList();
        List<Long> campusIds = visible.stream()
                .map(ClassroomInfoResponse::campusId)
                .distinct()
                .sorted()
                .toList();
        // Như sổ: nhóm tuổi nhỏ đến lớn (thứ tự cấu hình), rồi tên lớp
        Map<Long, Integer> order = ageGroupService.getAgeGroups().stream()
                .collect(Collectors.toMap(
                        AgeGroupDetailResponse::id, g -> g.sortOrder() == null ? Integer.MAX_VALUE : g.sortOrder()));
        List<ClassroomInfoResponse> shown = visible.stream()
                .filter(c -> campusId == null || campusId.equals(c.campusId()))
                .sorted(Comparator.comparing(
                                (ClassroomInfoResponse c) -> order.getOrDefault(c.ageGroupId(), Integer.MAX_VALUE))
                        .thenComparing(ClassroomInfoResponse::name))
                .toList();
        Map<Long, ClassRating> ratings = shown.isEmpty()
                ? Map.of()
                : ratingRepository
                        .findByClassroomIdInAndPeriod(
                                shown.stream().map(ClassroomInfoResponse::id).toList(), period)
                        .stream()
                        .collect(Collectors.toMap(ClassRating::getClassroomId, r -> r));
        Map<Long, String> names = accountService.findFullNames(ratings.values().stream()
                .map(ClassRating::getRatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<ClassRatingRow> rows = shown.stream()
                .map(c -> rowOf(user, c, ratings.get(c.id()), names))
                .toList();
        Long yearId = shown.isEmpty() ? schoolYearId : shown.getFirst().schoolYearId();
        LocalDate openFrom = yearId == null ? null : openFrom(user, yearId, period);
        boolean open = openFrom != null && !rules.today().isBefore(openFrom);
        List<ClassRatingRow> result = open
                ? rows
                : rows.stream()
                        .map(r -> new ClassRatingRow(
                                r.classId(),
                                r.className(),
                                r.campusId(),
                                r.ageGroupId(),
                                r.levels(),
                                r.note(),
                                r.ratedByName(),
                                r.ratedAt(),
                                false))
                        .toList();
        List<String> principals = accountService.findActiveFullNamesByRole(CareNestRole.PRINCIPAL);
        return new ClassRatingResponse(
                period, yearId, openFrom, campusIds, result, principals.size() == 1 ? principals.getFirst() : null);
    }

    private ClassRatingRow rowOf(CurrentUser user, ClassroomInfoResponse cls, ClassRating r, Map<Long, String> names) {
        boolean canEdit = cls.yearStatus() == SchoolYearStatus.ACTIVE && canRate(user, cls);
        Levels levels = r == null
                ? new Levels(null, null, null, null, null, null, null, null)
                : new Levels(
                        r.getRecords(),
                        r.getProgram(),
                        r.getTheme(),
                        r.getEnvironment(),
                        r.getRoutine(),
                        r.getHealth(),
                        r.getParents(),
                        r.getOverall());
        return new ClassRatingRow(
                cls.id(),
                cls.name(),
                cls.campusId(),
                cls.ageGroupId(),
                levels,
                r == null ? null : r.getNote(),
                r == null ? null : names.get(r.getRatedBy()),
                r == null ? null : r.getRatedAt(),
                canEdit);
    }

    // Kỳ mở chấm (OBS-24): HK I, HK II từ ngày bắt đầu học kỳ; cuối năm từ ngày 1 tháng cuối năm học
    private LocalDate openFrom(CurrentUser user, Long schoolYearId, ClassRatingPeriod period) {
        SchoolYearDetailResponse year = schoolYearService.getSchoolYear(user, schoolYearId);
        return switch (period) {
            case SEMESTER_1 -> year.semester1Start() == null ? year.startDate() : year.semester1Start();
            case SEMESTER_2 -> year.semester2Start();
            case YEAR -> year.endDate().withDayOfMonth(1);
        };
    }

    // Chấm: HT mọi lớp, PHT lớp điểm trường mình (AUTH-01, AUTH-02)
    private boolean canRate(CurrentUser user, ClassroomInfoResponse cls) {
        return user.hasPermission(Permission.ASSESSMENT_CLASS_REVIEW)
                && rules.leadsCampus(user, Permission.ASSESSMENT_CLASS_REVIEW, cls.campusId());
    }

    // Xem: HT mọi điểm trường, PHT điểm trường mình hoặc cả trường theo quyền HT cấp (AUTH-20); GV lớp đang dạy hoặc
    // từng dạy ở năm đã kết thúc; tổ trưởng thêm các lớp
    // nhóm tuổi mình (xem ở listOf, Cần chốt 04/11)
    private boolean canRead(CurrentUser user, ClassroomInfoResponse cls) {
        return rules.readsCampus(user, cls.campusId())
                || rules.canRecord(user, cls)
                || rules.taughtClosedYear(user, cls);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
