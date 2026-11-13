package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.observation.EvaluationDtos.GoalCount;
import com.carenest.dto.observation.EvaluationDtos.StructuredSummary;
import com.carenest.dto.observation.WeeklyCommentDtos.ParentWeeklyCommentResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeekData;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentBoardResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentChildRow;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentDraftRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSaveRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSendRequest;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.BehaviourTicket;
import com.carenest.entity.observation.WeeklyComment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.BehaviourTicketRepository;
import com.carenest.repository.observation.WeeklyCommentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.observation.WeeklyCommentService;
import com.carenest.service.organization.ClassroomService;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
public class WeeklyCommentServiceImpl implements WeeklyCommentService {
    private static final String LOG_PREFIX = "[WeeklyCommentServiceImpl]";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    static final int CONTENT_MAX = 2000;
    private static final int SCHOOL_WEEK_DAYS = 4;

    private final ObservationDayRules rules;
    private final EvaluationSources sources;
    private final EnrollmentService enrollmentService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final NotificationService notificationService;
    private final AccountService accountService;
    private final ClassroomService classroomService;
    private final WeeklyCommentRepository commentRepository;
    private final BehaviourTicketRepository ticketRepository;
    private final TemplateWeeklyCommentDraftGenerator templateGenerator;
    private final AiWeeklyCommentDraftGenerator aiGenerator;

    @Override
    @Transactional(readOnly = true)
    public WeeklyCommentBoardResponse getBoard(CurrentUser currentUser, Long classId, LocalDate weekStart) {
        String logPrefix = LOG_PREFIX + "|getBoard|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!rules.canView(currentUser, cls) && !rules.leadsClass(currentUser, cls)) {
                throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
            }
            WeeklyCommentBoardResponse response = board(currentUser, cls, weekOf(cls, weekStart), false);
            log.info("{}|END", logPrefix);
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
                // Cả lớp: bỏ trẻ nghỉ cả tuần và bản cô đã viết (trừ khi cô chọn viết lại)
                if ((request.childId() == null && week1.facts().presentDays() == 0)
                        || (hasText && !request.overwrite())) {
                    continue;
                }
                String text = null;
                WeeklyCommentSource source = WeeklyCommentSource.TEMPLATE;
                if (ai) {
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
                notifyParents(comment, roster.get(comment.getChildId()));
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
            Map<Long, String> classNames = new HashMap<>();
            sent.stream()
                    .map(WeeklyComment::getClassroomId)
                    .distinct()
                    .forEach(id -> classroomService.findClassroomInfo(id).ifPresent(c -> classNames.put(id, c.name())));
            List<ParentWeeklyCommentResponse> response = sent.stream()
                    .map(c -> new ParentWeeklyCommentResponse(
                            c.getId(),
                            c.getChildId(),
                            c.getWeekStart(),
                            c.getWeekStart().plusDays(SCHOOL_WEEK_DAYS),
                            classNames.get(c.getClassroomId()),
                            c.getContent(),
                            c.getSentAt(),
                            teachers.get(c.getSentBy())))
                    .toList();
            log.info("{}|count={}|END", logPrefix, response.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
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
        LocalDate from = max(week, cls.yearStart());
        LocalDate to = min(min(week.plusDays(SCHOOL_WEEK_DAYS), cls.yearEnd()), rules.today());
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
        LocalDate from = max(week, cls.yearStart());
        LocalDate to = min(min(week.plusDays(SCHOOL_WEEK_DAYS), cls.yearEnd()), rules.today());
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
                            w.data());
                })
                .toList();
        return new WeeklyCommentBoardResponse(
                cls.id(),
                cls.name(),
                week,
                week.plusDays(SCHOOL_WEEK_DAYS),
                aiGenerator.isEnabled(),
                canEdit,
                aiFallback,
                rows);
    }

    private void notifyParents(WeeklyComment comment, ClassChildResponse child) {
        List<Long> parents = guardianChildLinkService.findLinksOfChild(comment.getChildId()).stream()
                .map(GuardianLinkResponse::userId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (parents.isEmpty()) {
            return;
        }
        String name = child == null ? "con" : child.fullName();
        String range = DM.format(comment.getWeekStart()) + " – "
                + DMY.format(comment.getWeekStart().plusDays(SCHOOL_WEEK_DAYS));
        notificationService.notifyUsers(
                parents,
                NotificationType.WEEKLY_COMMENT_SENT,
                "Nhận xét tuần của con",
                "Cô đã gửi nhận xét tuần " + range + " của " + name + ". Mở mục Nhận xét tuần để xem.",
                comment.getChildId());
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
