package com.carenest.service.observation.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.observation.EvaluationDtos.GoalCount;
import com.carenest.dto.observation.EvaluationDtos.StructuredSummary;
import com.carenest.dto.observation.WeeklyCommentDtos.ParentWeeklyCommentResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeekData;
import com.carenest.dto.observation.WeeklyCommentDtos.WeekOption;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentBoardResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentChildRow;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentDraftRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentReopenRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentReopenResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSaveRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSendRequest;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.BehaviourTicket;
import com.carenest.entity.observation.WeeklyComment;
import com.carenest.entity.observation.WeeklyCommentReopen;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.BehaviourTicketRepository;
import com.carenest.repository.observation.WeeklyCommentReopenRepository;
import com.carenest.repository.observation.WeeklyCommentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.observation.WeeklyCommentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.TicketType;
import com.carenest.utils.enums.WeeklyCommentSource;
import com.carenest.utils.enums.WeeklyCommentStatus;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyCommentServiceImpl implements WeeklyCommentService {
    private static final String LOG_PREFIX = "[WeeklyCommentServiceImpl]";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    static final int CONTENT_MAX = 2000;
    static final int REOPEN_REASON_MAX = 300;
    private static final int SCHOOL_WEEK_DAYS = 4;

    private final ObservationDayRules rules;
    private final EvaluationSources sources;
    private final EduLessonQueryService lessonQueryService;
    private final EnrollmentService enrollmentService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final NotificationService notificationService;
    private final AccountService accountService;
    private final ClassroomService classroomService;
    private final StaffAssignmentService staffAssignmentService;
    private final WeeklyCommentRepository commentRepository;
    private final WeeklyCommentReopenRepository reopenRepository;
    private final ChildService childService;
    private final BehaviourTicketRepository ticketRepository;
    private final TemplateWeeklyCommentDraftGenerator templateGenerator;
    private final AiWeeklyCommentDraftGenerator aiGenerator;

    @Override
    @Transactional
    public WeeklyCommentBoardResponse getBoard(CurrentUser currentUser, Long classId, LocalDate weekStart) {
        String logPrefix = LOG_PREFIX + "|getBoard|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!rules.canView(currentUser, cls) && !rules.leadsClass(currentUser, cls)) {
                throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
            }
            LocalDate week = weekOf(cls, weekStart);
            // GV mở tuần đã tới ngày học cuối: tạo nháp còn thiếu (như việc chạy sáng hôm đó)
            int created = 0;
            if (cls.yearStatus() == SchoolYearStatus.ACTIVE
                    && currentUser.hasPermission(Permission.ASSESSMENT_RECORD)
                    && rules.canRecord(currentUser, cls)
                    && weekRange(cls, week).open()) {
                created = createMissingDrafts(cls, week, currentUser.userId());
            }
            WeeklyCommentBoardResponse response = board(currentUser, cls, week, false);
            log.info("{}|created={}|END", logPrefix, created);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public WeeklyCommentBoardResponse writeDrafts(CurrentUser currentUser, WeeklyCommentDraftRequest request) {
        String logPrefix = LOG_PREFIX + "|writeDrafts|userId=" + currentUser.userId() + "|classId=" + request.classId()
                + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = editableClass(currentUser, request.classId());
            LocalDate week = weekOf(cls, request.weekStart());
            checkWeekOpen(cls, week);
            rules.lockClassForUpdate(cls.id());
            Map<Long, ClassChildResponse> roster = roster(cls, week);
            List<Long> targets = request.childId() == null ? List.copyOf(roster.keySet()) : List.of(request.childId());
            targets.forEach(id -> checkInClass(roster, id));
            Map<Long, ChildWeek> data = weekData(cls, roster.keySet(), week);
            Map<Long, WeeklyComment> existing = comments(roster.keySet(), week);
            boolean ai = request.ai() && aiGenerator.isEnabled();
            boolean aiFailed = false;
            for (Long childId : targets) {
                WeeklyComment comment = existing.get(childId);
                if (comment != null && comment.isSent()) {
                    if (request.childId() != null) {
                        throw GlobalException.of(ApiCode.WEEKLY_COMMENT_ALREADY_SENT);
                    }
                    continue;
                }
                checkSameClass(cls, comment);
                ChildWeek week1 = data.get(childId);
                boolean hasText = comment != null && !comment.getContent().isBlank();
                // Bỏ bản cô đã viết (trừ khi cô chọn viết lại); trẻ nghỉ cả tuần vẫn có nháp hỏi thăm theo mẫu câu
                if (hasText && !request.overwrite()) {
                    continue;
                }
                String text = null;
                WeeklyCommentSource source = WeeklyCommentSource.TEMPLATE;
                if (ai && week1.facts().presentDays() > 0) {
                    try {
                        text = aiGenerator.write(week1.facts());
                        source = WeeklyCommentSource.AI;
                    } catch (RuntimeException ex) {
                        log.warn(
                                "{}|aiFallback|childId={}|type={}",
                                logPrefix,
                                childId,
                                ex.getClass().getSimpleName());
                        aiFailed = true;
                    }
                }
                if (text == null) {
                    text = templateGenerator.write(week1.facts());
                }
                if (comment == null) {
                    comment = WeeklyComment.create(
                            childId,
                            cls.id(),
                            cls.campusId(),
                            cls.schoolYearId(),
                            week,
                            currentUser.userId(),
                            rules.now());
                }
                comment.write(cut(text), source, currentUser.userId(), rules.now());
                commentRepository.save(comment);
            }
            commentRepository.flush();
            WeeklyCommentBoardResponse response = board(currentUser, cls, week, aiFailed);
            log.info("{}|targets={}|aiFallback={}|END", logPrefix, targets.size(), aiFailed);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public WeeklyCommentBoardResponse saveDraft(CurrentUser currentUser, WeeklyCommentSaveRequest request) {
        String logPrefix = LOG_PREFIX + "|saveDraft|userId=" + currentUser.userId() + "|classId=" + request.classId()
                + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = editableClass(currentUser, request.classId());
            LocalDate week = weekOf(cls, request.weekStart());
            checkWeekOpen(cls, week);
            String text = checkContent(request.content(), false);
            rules.lockClassForUpdate(cls.id());
            checkInClass(roster(cls, week), request.childId());
            WeeklyComment comment = editableComment(currentUser, cls, request.childId(), week);
            comment.write(text, comment.getDraftSource(), currentUser.userId(), rules.now());
            commentRepository.saveAndFlush(comment);
            WeeklyCommentBoardResponse response = board(currentUser, cls, week, false);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public WeeklyCommentBoardResponse send(CurrentUser currentUser, WeeklyCommentSendRequest request) {
        String logPrefix = LOG_PREFIX + "|send|userId=" + currentUser.userId() + "|classId=" + request.classId()
                + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = editableClass(currentUser, request.classId());
            LocalDate week = weekOf(cls, request.weekStart());
            checkWeekOpen(cls, week);
            rules.lockClassForUpdate(cls.id());
            Map<Long, ClassChildResponse> roster = roster(cls, week);
            List<WeeklyComment> toSend = new ArrayList<>();
            if (request.childId() != null) {
                checkInClass(roster, request.childId());
                WeeklyComment comment = editableComment(currentUser, cls, request.childId(), week);
                String text = request.content() == null ? comment.getContent() : request.content();
                comment.write(checkContent(text, true), comment.getDraftSource(), currentUser.userId(), rules.now());
                toSend.add(comment);
            } else {
                comments(roster.keySet(), week).values().stream()
                        .filter(c -> !c.isSent() && cls.id().equals(c.getClassroomId()))
                        .filter(c -> !c.getContent().isBlank())
                        .forEach(toSend::add);
                if (toSend.isEmpty()) {
                    throw GlobalException.of(ApiCode.WEEKLY_COMMENT_NOTHING_TO_SEND);
                }
                toSend.forEach(c -> checkContent(c.getContent(), true));
            }
            for (WeeklyComment comment : toSend) {
                comment.send(currentUser.userId(), rules.now());
                commentRepository.save(comment);
                boolean updated = reopenRepository.existsByWeeklyCommentId(comment.getId());
                notifyParents(comment, roster.get(comment.getChildId()), weekRange(cls, week), updated);
            }
            commentRepository.flush();
            WeeklyCommentBoardResponse response = board(currentUser, cls, week, false);
            log.info("{}|sent={}|END", logPrefix, toSend.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParentWeeklyCommentResponse> listForParent(CurrentUser currentUser, Long childId) {
        String logPrefix = LOG_PREFIX + "|listForParent|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-04: phụ huynh chỉ xem trẻ đã liên kết với tài khoản mình
            if (!currentUser.hasRole(CareNestRole.PARENT)
                    || !guardianChildLinkService.isGuardianOf(currentUser.userId(), childId)) {
                throw GlobalException.of(ApiCode.WEEKLY_COMMENT_PARENT_FORBIDDEN);
            }
            List<WeeklyComment> sent =
                    commentRepository.findByChildIdAndStatusOrderByWeekStartDesc(childId, WeeklyCommentStatus.SENT);
            Map<Long, String> teachers = accountService.findFullNames(
                    sent.stream().map(WeeklyComment::getSentBy).distinct().toList());
            Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
            sent.stream()
                    .map(WeeklyComment::getClassroomId)
                    .distinct()
                    .forEach(id -> classroomService.findClassroomInfo(id).ifPresent(c -> classes.put(id, c)));
            List<ParentWeeklyCommentResponse> response = sent.stream()
                    .map(c -> {
                        ClassroomInfoResponse cls = classes.get(c.getClassroomId());
                        // OBS-29: cùng tuần giáo án như phía GV (ngày học đầu – cuối có giáo án đã duyệt)
                        WeekOption range = cls == null ? null : weekRange(cls, c.getWeekStart());
                        return new ParentWeeklyCommentResponse(
                                c.getId(),
                                c.getChildId(),
                                c.getWeekStart(),
                                range == null ? c.getWeekStart() : range.from(),
                                range == null ? c.getWeekStart().plusDays(SCHOOL_WEEK_DAYS) : range.to(),
                                range == null ? null : range.label(),
                                cls == null ? null : cls.name(),
                                c.getContent(),
                                c.getSentAt(),
                                teachers.get(c.getSentBy()));
                    })
                    .toList();
            log.info("{}|count={}|END", logPrefix, response.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public List<WeeklyCommentReopenResponse> reopen(CurrentUser currentUser, WeeklyCommentReopenRequest request) {
        String logPrefix = LOG_PREFIX + "|reopen|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(request.classId());
            // OBS-29: như mở lại ngày đánh giá — HT hoặc PHT có quyền mở lại ở điểm trường của lớp, năm đang hoạt động
            if (!currentUser.hasPermission(Permission.ASSESSMENT_UNLOCK)
                    || !rules.leadsCampus(currentUser, Permission.ASSESSMENT_UNLOCK, cls.campusId())) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
            }
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_YEAR_NOT_ACTIVE);
            }
            String reason = checkReason(request.reason());
            LocalDate week = weekOf(cls, request.weekStart());
            rules.lockClassForUpdate(cls.id());
            Map<Long, ClassChildResponse> roster = roster(cls, week);
            Map<Long, WeeklyComment> existing = comments(roster.keySet(), week);
            List<WeeklyComment> targets = new ArrayList<>();
            if (request.childIds() == null || request.childIds().isEmpty()) {
                existing.values().stream()
                        .filter(c -> c.isSent() && cls.id().equals(c.getClassroomId()))
                        .forEach(targets::add);
                if (targets.isEmpty()) {
                    throw GlobalException.of(ApiCode.WEEKLY_COMMENT_NOT_SENT);
                }
            } else {
                for (Long childId : request.childIds().stream().distinct().toList()) {
                    checkInClass(roster, childId);
                    WeeklyComment comment = existing.get(childId);
                    if (comment == null || !comment.isSent()) {
                        throw GlobalException.of(ApiCode.WEEKLY_COMMENT_NOT_SENT);
                    }
                    checkSameClass(cls, comment);
                    targets.add(comment);
                }
            }
            Instant now = rules.now();
            List<WeeklyCommentReopen> created = new ArrayList<>();
            for (WeeklyComment comment : targets) {
                created.add(reopenRepository.save(WeeklyCommentReopen.of(comment, reason, currentUser.userId(), now)));
                comment.reopen(currentUser.userId(), now);
                commentRepository.save(comment);
            }
            commentRepository.flush();
            Map<Long, ClassroomInfoResponse> known = new HashMap<>();
            known.put(cls.id(), cls);
            List<WeeklyCommentReopenResponse> response = reopenViews(created, known);
            log.info("{}|reopened={}|END", logPrefix, created.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyCommentReopenResponse> getReopens(
            CurrentUser currentUser, Long campusId, Long classId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getReopens|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            if (from != null && to != null && to.isBefore(from)) {
                throw GlobalException.of(ApiCode.INVALID_DATE_RANGE);
            }
            List<WeeklyCommentReopen> all = reopenRepository.findAllByOrderByReopenedAtDescIdDesc();
            Map<Long, WeeklyComment> byId = commentsById(all);
            Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
            Map<Long, Boolean> allowed = new HashMap<>();
            List<WeeklyCommentReopen> visible = all.stream()
                    .filter(r -> {
                        WeeklyComment c = byId.get(r.getWeeklyCommentId());
                        if (c == null
                                || (campusId != null && !campusId.equals(c.getCampusId()))
                                || (classId != null && !classId.equals(c.getClassroomId()))
                                || (from != null && c.getWeekStart().isBefore(from))
                                || (to != null && c.getWeekStart().isAfter(to))) {
                            return false;
                        }
                        ClassroomInfoResponse cls = classOf(classes, c.getClassroomId());
                        // BGH: điểm trường trong phạm vi quyền mở lại hoặc quyền xem; GV: lớp đang dạy
                        return cls != null
                                && allowed.computeIfAbsent(
                                        cls.id(),
                                        id -> rules.leadsCampus(
                                                        currentUser, Permission.ASSESSMENT_UNLOCK, cls.campusId())
                                                || rules.readsCampus(currentUser, cls.campusId())
                                                || rules.canRecord(currentUser, cls));
                    })
                    .toList();
            List<WeeklyCommentReopenResponse> response = reopenViews(visible, classes);
            log.info("{}|count={}|END", logPrefix, response.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Map<Long, WeeklyComment> commentsById(List<WeeklyCommentReopen> reopens) {
        return commentRepository
                .findAllById(reopens.stream()
                        .map(WeeklyCommentReopen::getWeeklyCommentId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(WeeklyComment::getId, Function.identity()));
    }

    private ClassroomInfoResponse classOf(Map<Long, ClassroomInfoResponse> cache, Long classId) {
        if (!cache.containsKey(classId)) {
            cache.put(classId, classroomService.findClassroomInfo(classId).orElse(null));
        }
        return cache.get(classId);
    }

    private List<WeeklyCommentReopenResponse> reopenViews(
            List<WeeklyCommentReopen> reopens, Map<Long, ClassroomInfoResponse> classes) {
        if (reopens.isEmpty()) {
            return List.of();
        }
        Map<Long, WeeklyComment> comments = commentsById(reopens);
        Map<Long, ChildBriefResponse> children = childService.findChildBriefs(comments.values().stream()
                .map(WeeklyComment::getChildId)
                .distinct()
                .toList());
        Collection<Long> users = reopens.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getReopenedBy(), r.getPreviousSentBy()))
                .distinct()
                .toList();
        Map<Long, String> names = accountService.findFullNames(List.copyOf(users));
        Map<String, WeekOption> weeks = new HashMap<>();
        return reopens.stream()
                .map(r -> {
                    WeeklyComment c = comments.get(r.getWeeklyCommentId());
                    ClassroomInfoResponse cls = classOf(classes, c.getClassroomId());
                    WeekOption week = cls == null
                            ? null
                            : weeks.computeIfAbsent(
                                    cls.id() + ":" + c.getWeekStart(), k -> weekRange(cls, c.getWeekStart()));
                    ChildBriefResponse child = children.get(c.getChildId());
                    return new WeeklyCommentReopenResponse(
                            r.getId(),
                            c.getId(),
                            c.getChildId(),
                            child == null ? null : child.fullName(),
                            c.getClassroomId(),
                            cls == null ? null : cls.name(),
                            c.getCampusId(),
                            c.getWeekStart(),
                            week == null ? c.getWeekStart() : week.from(),
                            week == null ? c.getWeekStart().plusDays(SCHOOL_WEEK_DAYS) : week.to(),
                            r.getReason(),
                            r.getReopenedBy(),
                            names.get(r.getReopenedBy()),
                            r.getReopenedAt(),
                            r.getPreviousContent(),
                            r.getPreviousSentAt(),
                            names.get(r.getPreviousSentBy()));
                })
                .toList();
    }

    // Lý do mở lại bắt buộc, tối đa 300 ký tự (như mở lại ngày đánh giá)
    private static String checkReason(String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw GlobalException.of(ApiCode.WEEKLY_COMMENT_REOPEN_REASON_REQUIRED);
        }
        if (text.length() > REOPEN_REASON_MAX) {
            throw GlobalException.of(ApiCode.WEEKLY_COMMENT_REOPEN_REASON_TOO_LONG, REOPEN_REASON_MAX);
        }
        return text;
    }

    @Override
    @Transactional
    // OBS-29: 06:00 thứ Hai–Sáu, tuần mở từ ngày học cuối ⇒ tạo nháp theo mẫu câu cho trẻ chưa có nhận xét; chạy lại
    // không tạo trùng, không đè bản nháp hay bản đã gửi
    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "${carenest.time-zone}")
    public void createOpenWeekDrafts() {
        String logPrefix = LOG_PREFIX + "|createOpenWeekDrafts";
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = rules.today();
            LocalDate week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            List<ClassroomInfoResponse> classes = classroomService.findActiveYearClassrooms().stream()
                    .filter(c -> c.yearStatus() == SchoolYearStatus.ACTIVE)
                    .filter(c -> !week.plusDays(SCHOOL_WEEK_DAYS).isBefore(c.yearStart()) && !week.isAfter(c.yearEnd()))
                    .toList();
            Map<Long, Long> homeroom = staffAssignmentService.findHomeroomTeacherIds(
                    classes.stream().map(ClassroomInfoResponse::id).toList(), today);
            Map<Long, List<Long>> teachers = staffAssignmentService.findCurrentTeacherIds(
                    classes.stream().map(ClassroomInfoResponse::id).toList(), today);
            int created = 0;
            for (ClassroomInfoResponse cls : classes) {
                Long author = homeroom.getOrDefault(
                        cls.id(),
                        teachers.getOrDefault(cls.id(), List.of()).stream()
                                .findFirst()
                                .orElse(null));
                if (author == null || !weekRange(cls, week).open()) {
                    continue;
                }
                created += createMissingDrafts(cls, week, author);
            }
            log.info("{}|classes={}|created={}|END", logPrefix, classes.size(), created);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Nháp mẫu câu cho mọi trẻ của lớp trong tuần chưa có nhận xét (trẻ nghỉ cả tuần: lời hỏi thăm); không đụng bản đã
    // có (nháp hay đã gửi)
    private int createMissingDrafts(ClassroomInfoResponse cls, LocalDate week, Long author) {
        Map<Long, ClassChildResponse> roster = roster(cls, week);
        if (roster.isEmpty() || comments(roster.keySet(), week).size() == roster.size()) {
            return 0;
        }
        rules.lockClassForUpdate(cls.id());
        Map<Long, WeeklyComment> existing = comments(roster.keySet(), week);
        Map<Long, ChildWeek> data = weekData(cls, roster.keySet(), week);
        int created = 0;
        for (Long childId : roster.keySet()) {
            ChildWeek w = data.get(childId);
            if (existing.containsKey(childId)) {
                continue;
            }
            WeeklyComment comment = WeeklyComment.create(
                    childId, cls.id(), cls.campusId(), cls.schoolYearId(), week, author, rules.now());
            comment.write(cut(templateGenerator.write(w.facts())), WeeklyCommentSource.TEMPLATE, author, rules.now());
            commentRepository.save(comment);
            created++;
        }
        commentRepository.flush();
        return created;
    }

    // AUTH-03: chỉ GV đang dạy lớp ghi; năm học phải đang hoạt động (CFG-YR-01)
    private ClassroomInfoResponse editableClass(CurrentUser user, Long classId) {
        ClassroomInfoResponse cls = rules.findClass(classId);
        if (!user.hasPermission(Permission.ASSESSMENT_RECORD) || !rules.canRecord(user, cls)) {
            throw GlobalException.of(ApiCode.EVALUATION_EDIT_FORBIDDEN);
        }
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        }
        return cls;
    }

    // OBS-29: tuần thứ Hai–thứ Sáu, có ngày trong năm học của lớp, đã bắt đầu (không tuần tương lai)
    private LocalDate weekOf(ClassroomInfoResponse cls, LocalDate weekStart) {
        LocalDate today = rules.today();
        LocalDate week =
                (weekStart == null ? today : weekStart).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (week.plusDays(SCHOOL_WEEK_DAYS).isBefore(cls.yearStart()) || week.isAfter(cls.yearEnd())) {
            throw GlobalException.of(
                    ApiCode.WEEKLY_COMMENT_WEEK_INVALID, DMY.format(cls.yearStart()), DMY.format(cls.yearEnd()));
        }
        if (week.isAfter(today)) {
            throw GlobalException.of(ApiCode.WEEKLY_COMMENT_FUTURE_WEEK);
        }
        return week;
    }

    private WeeklyComment editableComment(CurrentUser user, ClassroomInfoResponse cls, Long childId, LocalDate week) {
        WeeklyComment comment =
                commentRepository.findByChildIdAndWeekStart(childId, week).orElse(null);
        if (comment == null) {
            return WeeklyComment.create(
                    childId, cls.id(), cls.campusId(), cls.schoolYearId(), week, user.userId(), rules.now());
        }
        if (comment.isSent()) {
            throw GlobalException.of(ApiCode.WEEKLY_COMMENT_ALREADY_SENT);
        }
        checkSameClass(cls, comment);
        return comment;
    }

    // Trẻ chuyển lớp giữa tuần: bản nhận xét thuộc lớp đã viết, GV lớp khác không sửa
    private static void checkSameClass(ClassroomInfoResponse cls, WeeklyComment comment) {
        if (comment != null && !cls.id().equals(comment.getClassroomId())) {
            throw GlobalException.of(ApiCode.EVALUATION_EDIT_FORBIDDEN);
        }
    }

    private static void checkInClass(Map<Long, ClassChildResponse> roster, Long childId) {
        if (childId == null || !roster.containsKey(childId)) {
            throw GlobalException.of(ApiCode.WEEKLY_COMMENT_CHILD_NOT_IN_CLASS);
        }
    }

    // Gửi phụ huynh cần nội dung; lưu nháp cho phép rỗng. Tối đa 2000 ký tự, không từ chẩn đoán (như đánh giá định kỳ)
    static String checkContent(String content, boolean required) {
        String text = content == null ? "" : content.trim();
        if (required && text.isEmpty()) {
            throw GlobalException.of(ApiCode.EVALUATION_CONTENT_REQUIRED);
        }
        if (text.length() > CONTENT_MAX) {
            throw GlobalException.of(ApiCode.EVALUATION_CONTENT_TOO_LONG, CONTENT_MAX);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        DailyAssessmentServiceImpl.DIAGNOSTIC_TERMS.stream()
                .filter(lower::contains)
                .findFirst()
                .ifPresent(term -> {
                    throw GlobalException.of(ApiCode.EVALUATION_DIAGNOSTIC_TERM, term);
                });
        return text;
    }

    private static String cut(String text) {
        String t = text.trim();
        return t.length() > CONTENT_MAX ? t.substring(0, CONTENT_MAX) : t;
    }

    private Map<Long, ClassChildResponse> roster(ClassroomInfoResponse cls, LocalDate week) {
        WeekOption range = weekRange(cls, week);
        LocalDate from = range.from();
        LocalDate to = min(range.to(), rules.today());
        if (to.isBefore(from)) {
            return Map.of();
        }
        Map<Long, ClassChildResponse> roster = new java.util.LinkedHashMap<>();
        enrollmentService.findChildrenInClassroomDuring(cls.id(), from, to).forEach(c -> roster.putIfAbsent(c.id(), c));
        return roster;
    }

    private Map<Long, WeeklyComment> comments(Set<Long> childIds, LocalDate week) {
        return childIds.isEmpty()
                ? Map.of()
                : commentRepository.findByChildIdInAndWeekStart(childIds, week).stream()
                        .collect(Collectors.toMap(WeeklyComment::getChildId, Function.identity()));
    }

    private record ChildWeek(WeeklyCommentDraftGenerator.WeekFacts facts, WeekData data) {}

    private Map<Long, ChildWeek> weekData(ClassroomInfoResponse cls, Set<Long> childIds, LocalDate week) {
        WeekOption range = weekRange(cls, week);
        LocalDate from = range.from();
        LocalDate to = min(range.to(), rules.today());
        int schoolDays = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (rules.isSchoolDay(d)) {
                schoolDays++;
            }
        }
        Map<Long, EvaluationSources.ChildData> loaded = sources.load(cls, childIds, from, to, false);
        Set<Long> ticketed = childIds.isEmpty()
                ? Set.of()
                : ticketRepository.findByChildIdInAndPeriodStartBetween(childIds, week, week).stream()
                        .filter(t -> t.getType() == TicketType.WEEKLY)
                        .map(BehaviourTicket::getChildId)
                        .collect(Collectors.toSet());
        Map<Long, ChildWeek> out = new HashMap<>();
        for (Long childId : childIds) {
            EvaluationSources.ChildData d = loaded.get(childId);
            StructuredSummary s = d == null ? null : d.structured();
            Map<EatingLevel, Integer> lunch =
                    s == null ? Map.of() : s.meals().getOrDefault(MealSession.LUNCH, Map.of());
            int lunchDays = lunch.values().stream().mapToInt(Integer::intValue).sum();
            int lunchFinished = lunch.getOrDefault(EatingLevel.AN_HET, 0);
            Map<String, GoalCount> goals =
                    s == null || s.goals() == null ? Map.of() : s.goals().byGoal();
            List<String> met = new ArrayList<>();
            List<String> unmet = new ArrayList<>();
            goals.forEach((name, c) -> {
                // Đạt khi số lần đạt không ít hơn số lần cố gắng / chưa đạt trong tuần
                if (c.dat() > 0 && c.dat() >= c.canCoGang() + c.chuaDat()) {
                    met.add(name);
                } else {
                    unmet.add(name);
                }
            });
            int present = d == null ? 0 : d.presentDays();
            int flags = d == null ? 0 : d.flagCount();
            boolean ticket = ticketed.contains(childId);
            WeeklyCommentDraftGenerator.WeekFacts facts = new WeeklyCommentDraftGenerator.WeekFacts(
                    present,
                    schoolDays,
                    s == null ? Map.of() : s.emotion(),
                    s == null ? Map.of() : s.participation(),
                    s == null ? Map.of() : s.nap(),
                    lunchFinished,
                    lunchDays,
                    met,
                    unmet,
                    flags,
                    ticket);
            out.put(
                    childId,
                    new ChildWeek(
                            facts,
                            new WeekData(
                                    present,
                                    schoolDays,
                                    met.size(),
                                    goals.size(),
                                    List.copyOf(unmet),
                                    flags,
                                    ticket,
                                    lunchFinished,
                                    lunchDays)));
        }
        return out;
    }

    private WeeklyCommentBoardResponse board(
            CurrentUser user, ClassroomInfoResponse cls, LocalDate week, boolean aiFallback) {
        Map<Long, ClassChildResponse> roster = roster(cls, week);
        Map<Long, ChildWeek> data = weekData(cls, roster.keySet(), week);
        Map<Long, WeeklyComment> comments = comments(roster.keySet(), week);
        Set<Long> reopened = comments.isEmpty()
                ? Set.of()
                : reopenRepository
                        .findByWeeklyCommentIdInOrderByReopenedAtDescIdDesc(comments.values().stream()
                                .map(WeeklyComment::getId)
                                .toList())
                        .stream()
                        .map(WeeklyCommentReopen::getWeeklyCommentId)
                        .collect(Collectors.toSet());
        boolean teacher = rules.canRecord(user, cls);
        boolean canEdit = teacher
                && user.hasPermission(Permission.ASSESSMENT_RECORD)
                && cls.yearStatus() == SchoolYearStatus.ACTIVE;
        Map<Long, String> senders = accountService.findFullNames(comments.values().stream()
                .map(WeeklyComment::getSentBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<WeeklyCommentChildRow> rows = roster.values().stream()
                .map(child -> {
                    WeeklyComment c = comments.get(child.id());
                    ChildWeek w = data.get(child.id());
                    boolean sent = c != null && c.isSent();
                    boolean visible = c != null && (teacher || sent);
                    return new WeeklyCommentChildRow(
                            child.id(),
                            child.fullName(),
                            c == null ? "NONE" : c.getStatus().name(),
                            w.data().presentDays() == 0,
                            visible ? c.getContent() : null,
                            visible ? c.getDraftSource() : null,
                            sent ? c.getSentAt() : null,
                            sent ? senders.get(c.getSentBy()) : null,
                            c == null ? null : c.getUpdatedAt(),
                            c != null && !sent && reopened.contains(c.getId()),
                            w.data());
                })
                .toList();
        List<WeekOption> weeks = weeks(cls);
        WeekOption range = weeks.stream()
                .filter(w -> w.weekStart().equals(week))
                .findFirst()
                .orElseGet(() -> weekRange(cls, week));
        return new WeeklyCommentBoardResponse(
                cls.id(),
                cls.name(),
                week,
                range.to(),
                range,
                weeks,
                aiGenerator.isEnabled(),
                canEdit && range.open(),
                aiFallback,
                rows);
    }

    // OBS-29: tuần theo giáo án tuần đã duyệt của lớp (ngày học có kế hoạch ngày); tuần chưa có giáo án ⇒ thứ Hai–Sáu
    private WeekOption weekRange(ClassroomInfoResponse cls, LocalDate week) {
        LocalDate from = max(week, cls.yearStart());
        LocalDate to = min(week.plusDays(SCHOOL_WEEK_DAYS), cls.yearEnd());
        return option(cls, week, lessonWeeks(cls, from, to).get(week));
    }

    // Các tuần của năm học tới tuần hiện tại, mới nhất trước
    private List<WeekOption> weeks(ClassroomInfoResponse cls) {
        LocalDate last = min(rules.today(), cls.yearEnd());
        if (last.isBefore(cls.yearStart())) {
            return List.of();
        }
        LocalDate first = cls.yearStart().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, LessonWeek> byWeek =
                lessonWeeks(cls, cls.yearStart(), min(last.plusDays(SCHOOL_WEEK_DAYS), cls.yearEnd()));
        List<WeekOption> out = new ArrayList<>();
        for (LocalDate monday = last.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                !monday.isBefore(first);
                monday = monday.minusWeeks(1)) {
            out.add(option(cls, monday, byWeek.get(monday)));
        }
        return out;
    }

    // Mở viết từ ngày học cuối của tuần (giáo án tuần kết thúc sớm vì nghỉ lễ ⇒ mở từ ngày đó)
    private WeekOption option(ClassroomInfoResponse cls, LocalDate monday, LessonWeek lesson) {
        LocalDate from;
        LocalDate to;
        String label = null;
        String source;
        if (lesson != null) {
            from = lesson.days().getFirst();
            to = lesson.days().getLast();
            label = lesson.label();
            source = "LESSON_PLAN";
        } else {
            from = max(monday, cls.yearStart());
            to = min(monday.plusDays(SCHOOL_WEEK_DAYS), cls.yearEnd());
            source = "CALENDAR";
        }
        return new WeekOption(
                monday, from, to, label, source, to, !rules.today().isBefore(to));
    }

    private record LessonWeek(List<LocalDate> days, String label) {}

    // Ngày học có kế hoạch ngày đã duyệt, gom theo thứ Hai của giáo án tuần (bỏ ngày nghỉ)
    private Map<LocalDate, LessonWeek> lessonWeeks(ClassroomInfoResponse cls, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return Map.of();
        }
        Map<LocalDate, List<ApprovedDayLessonResponse>> grouped = new TreeMap<>();
        lessonQueryService.findApprovedDayLessons(cls.id(), from, to).values().stream()
                .filter(l -> rules.isSchoolDay(l.date()))
                .sorted(Comparator.comparing(ApprovedDayLessonResponse::date))
                .forEach(l -> grouped.computeIfAbsent(l.weekStart(), k -> new ArrayList<>())
                        .add(l));
        Map<LocalDate, LessonWeek> out = new HashMap<>();
        grouped.forEach((monday, lessons) -> out.put(
                monday,
                new LessonWeek(
                        lessons.stream().map(ApprovedDayLessonResponse::date).toList(),
                        lessons.getFirst().weekLabel())));
        return out;
    }

    // OBS-29: chưa tới ngày học cuối của tuần thì chỉ xem, chưa viết / gửi nhận xét
    private void checkWeekOpen(ClassroomInfoResponse cls, LocalDate week) {
        WeekOption range = weekRange(cls, week);
        if (!range.open()) {
            throw GlobalException.of(ApiCode.WEEKLY_COMMENT_WEEK_NOT_ENDED, DMY.format(range.openFrom()));
        }
    }

    // Gửi lại sau khi BGH mở lại: báo phụ huynh "đã cập nhật" thay cho "đã gửi"
    private void notifyParents(WeeklyComment comment, ClassChildResponse child, WeekOption week, boolean updated) {
        List<Long> parents = guardianChildLinkService.findLinksOfChild(comment.getChildId()).stream()
                .map(GuardianLinkResponse::userId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (parents.isEmpty()) {
            return;
        }
        String name = child == null ? "con" : child.fullName();
        String range = DM.format(week.from()) + " – " + DMY.format(week.to());
        notificationService.notifyUsers(
                parents,
                NotificationType.WEEKLY_COMMENT_SENT,
                updated ? "Nhận xét tuần của con đã cập nhật" : "Nhận xét tuần của con",
                (updated ? "Cô đã cập nhật nhận xét tuần " : "Cô đã gửi nhận xét tuần ") + range + " của " + name
                        + ". Mở mục Nhận xét tuần để xem.",
                comment.getChildId());
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
