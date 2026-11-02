package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.observation.RewardDtos.HistoryRow;
import com.carenest.dto.observation.RewardDtos.RewardChildRow;
import com.carenest.dto.observation.RewardDtos.RewardClassDetail;
import com.carenest.dto.observation.RewardDtos.RewardClassRow;
import com.carenest.dto.observation.RewardDtos.RewardDecisionRequest;
import com.carenest.dto.observation.RewardDtos.RewardItemRequest;
import com.carenest.dto.observation.RewardDtos.RewardListRequest;
import com.carenest.dto.observation.RewardDtos.Step;
import com.carenest.dto.observation.RewardDtos.YearEndRef;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.BehaviourTicket;
import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.entity.observation.RewardList;
import com.carenest.entity.observation.RewardListEvent;
import com.carenest.entity.observation.RewardListItem;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.BehaviourTicketRepository;
import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.repository.observation.RewardListEventRepository;
import com.carenest.repository.observation.RewardListItemRepository;
import com.carenest.repository.observation.RewardListRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ClassChildDay;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.observation.RewardListService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.RewardAction;
import com.carenest.utils.enums.RewardStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.TicketType;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RewardListServiceImpl implements RewardListService {

    private static final String LOG_PREFIX = "[RewardListServiceImpl]";
    static final String DILIGENT = "Bé chăm";
    static final String GOOD = "Bé ngoan";
    static final String ALL_ROUND = "Bé ngoan toàn diện";
    static final String PROGRAM = "Hoàn thành chương trình GDMN";
    /** Danh hiệu như FE REWARD_TITLES; 2 danh hiệu cuối GV tự chọn, không gợi ý. */
    static final List<String> TITLES =
            List.of(DILIGENT, GOOD, ALL_ROUND, PROGRAM, "Bé khỏe – Bé đẹp", "Bé có năng khiếu nghệ thuật");
    // Bé chăm: đi học từ 90% số ngày học của lớp (như báo cáo chất lượng tháng)
    static final int DILIGENT_PERCENT = 90;
    static final int NOTE_MAX = 300;
    static final int COMMENT_MAX = 500;

    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final AgeGroupService ageGroupService;
    private final EnrollmentService enrollmentService;
    private final AccountService accountService;
    private final AttendanceService attendanceService;
    private final RewardListRepository listRepository;
    private final RewardListItemRepository itemRepository;
    private final RewardListEventRepository eventRepository;
    private final ChildEvaluationRepository evaluationRepository;
    private final BehaviourTicketRepository ticketRepository;

    @Override
    public List<RewardClassRow> classes(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|classes|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            List<ClassroomInfoResponse> visible = classroomService.findYearClassrooms(schoolYearId).stream()
                    .filter(c -> canView(currentUser, c))
                    .toList();
            if (visible.isEmpty()) {
                log.info("{}|rows=0|END", logPrefix);
                return List.of();
            }
            Map<Long, RewardList> lists =
                    listRepository
                            .findByClassroomIdIn(visible.stream()
                                    .map(ClassroomInfoResponse::id)
                                    .toList())
                            .stream()
                            .collect(Collectors.toMap(RewardList::getClassroomId, l -> l));
            Map<Long, List<RewardListItem>> items = lists.isEmpty()
                    ? Map.of()
                    : itemRepository
                            .findByListIdIn(lists.values().stream()
                                    .map(RewardList::getId)
                                    .toList())
                            .stream()
                            .collect(Collectors.groupingBy(RewardListItem::getListId));
            List<RewardClassRow> rows = visible.stream()
                    .map(c -> {
                        RewardList l = lists.get(c.id());
                        boolean hidden = l == null || !seesItems(currentUser, c, l);
                        return new RewardClassRow(
                                c.id(),
                                c.name(),
                                c.campusId(),
                                l == null ? null : l.getId(),
                                l == null ? null : l.getStatus(),
                                enrollmentService
                                        .findChildrenInClassroom(c.id(), at(c))
                                        .size(),
                                hidden
                                        ? null
                                        : items.getOrDefault(l.getId(), List.of())
                                                .size(),
                                l == null ? null : l.getSubmittedAt(),
                                l == null ? null : l.getUpdatedAt());
                    })
                    .toList();
            log.info("{}|rows={}|END", logPrefix, rows.size());
            return rows;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public RewardClassDetail detail(CurrentUser currentUser, Long classId) {
        String logPrefix = LOG_PREFIX + "|detail|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!canView(currentUser, cls)) {
                throw GlobalException.of(ApiCode.REWARD_VIEW_FORBIDDEN);
            }
            RewardClassDetail detail = toDetail(
                    currentUser, cls, listRepository.findByClassroomId(classId).orElse(null));
            log.info("{}|END", logPrefix);
            return detail;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public RewardClassDetail save(CurrentUser currentUser, Long classId, RewardListRequest request, boolean submit) {
        String logPrefix =
                LOG_PREFIX + "|save|userId=" + currentUser.userId() + "|classId=" + classId + "|submit=" + submit;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            if (!rules.canRecord(currentUser, cls)) {
                throw GlobalException.of(ApiCode.REWARD_EDIT_FORBIDDEN);
            }
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
            }
            RewardList existing = listRepository.findByClassroomId(classId).orElse(null);
            if (existing != null && !existing.isEditable()) {
                throw GlobalException.of(ApiCode.REWARD_STATUS_CHANGED);
            }
            List<RewardItemRequest> items = checkItems(cls, request == null ? null : request.items());
            if (submit && items.isEmpty()) {
                throw GlobalException.of(ApiCode.REWARD_LIST_EMPTY);
            }
            Instant at = rules.now();
            boolean returned = existing != null && existing.getStatus() == RewardStatus.RETURNED;
            RewardList list = existing == null
                    ? RewardList.draft(cls.id(), cls.campusId(), cls.schoolYearId(), currentUser.userId())
                    : existing;
            list.touch(currentUser.userId());
            if (submit) {
                list.submit(at);
            }
            list = listRepository.saveAndFlush(list);
            if (existing != null) {
                itemRepository.deleteByListId(list.getId());
                itemRepository.flush();
            }
            Long listId = list.getId();
            itemRepository.saveAll(items.stream()
                    .map(i -> RewardListItem.of(listId, i.childId(), i.titles(), i.note()))
                    .toList());
            if (existing == null) {
                eventRepository.save(RewardListEvent.of(listId, RewardAction.CREATED, currentUser.userId(), at, null));
            } else if (!submit) {
                eventRepository.save(RewardListEvent.of(listId, RewardAction.UPDATED, currentUser.userId(), at, null));
            }
            if (submit) {
                eventRepository.save(RewardListEvent.of(
                        listId,
                        returned ? RewardAction.RESUBMITTED : RewardAction.SUBMITTED,
                        currentUser.userId(),
                        at,
                        null));
            }
            log.info("{}|listId={}|items={}|status={}|END", logPrefix, listId, items.size(), list.getStatus());
            return toDetail(currentUser, cls, list);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public RewardClassDetail review(CurrentUser currentUser, Long listId, RewardDecisionRequest request) {
        String logPrefix = LOG_PREFIX + "|review|userId=" + currentUser.userId() + "|listId=" + listId;
        log.info("{}|START", logPrefix);
        try {
            RewardList list = find(listId);
            ClassroomInfoResponse cls = rules.findClass(list.getClassroomId());
            if (!canReview(currentUser, list)) {
                if (list.getStatus() != RewardStatus.PENDING_VP && isReviewer(currentUser, list)) {
                    throw GlobalException.of(ApiCode.REWARD_STATUS_CHANGED);
                }
                throw GlobalException.of(ApiCode.REWARD_REVIEW_FORBIDDEN);
            }
            String decision = request == null ? null : request.decision();
            if (!"FORWARD".equals(decision) && !"RETURN".equals(decision)) {
                throw GlobalException.of(ApiCode.REWARD_DECISION_INVALID);
            }
            boolean forward = "FORWARD".equals(decision);
            String comment = checkComment(request.comment(), !forward);
            Instant at = rules.now();
            list.review(forward, currentUser.userId(), at, comment);
            listRepository.saveAndFlush(list);
            eventRepository.save(RewardListEvent.of(
                    list.getId(),
                    forward ? RewardAction.FORWARDED : RewardAction.RETURNED,
                    currentUser.userId(),
                    at,
                    comment));
            log.info("{}|decision={}|END", logPrefix, decision);
            return toDetail(currentUser, cls, list);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public RewardClassDetail decide(CurrentUser currentUser, Long listId, RewardDecisionRequest request) {
        String logPrefix = LOG_PREFIX + "|decide|userId=" + currentUser.userId() + "|listId=" + listId;
        log.info("{}|START", logPrefix);
        try {
            RewardList list = find(listId);
            ClassroomInfoResponse cls = rules.findClass(list.getClassroomId());
            if (!currentUser.hasRole(CareNestRole.PRINCIPAL)) {
                throw GlobalException.of(ApiCode.REWARD_REVIEW_FORBIDDEN);
            }
            if (!canDecide(currentUser, list)) {
                throw GlobalException.of(ApiCode.REWARD_STATUS_CHANGED);
            }
            String decision = request == null ? null : request.decision();
            if (!"APPROVE".equals(decision) && !"RETURN".equals(decision)) {
                throw GlobalException.of(ApiCode.REWARD_DECISION_INVALID);
            }
            boolean approve = "APPROVE".equals(decision);
            String comment = checkComment(request.comment(), !approve);
            Instant at = rules.now();
            list.decide(approve, currentUser.userId(), at, comment);
            listRepository.saveAndFlush(list);
            eventRepository.save(RewardListEvent.of(
                    list.getId(),
                    approve ? RewardAction.APPROVED : RewardAction.RETURNED,
                    currentUser.userId(),
                    at,
                    comment));
            log.info("{}|decision={}|END", logPrefix, decision);
            return toDetail(currentUser, cls, list);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // ---------- quyền ----------

    private RewardList find(Long id) {
        return listRepository.findById(id).orElseThrow(() -> GlobalException.of(ApiCode.REWARD_LIST_NOT_FOUND));
    }

    private boolean teaches(CurrentUser user, ClassroomInfoResponse cls) {
        return user.hasRole(CareNestRole.TEACHER) && (rules.canRecord(user, cls) || rules.taughtClosedYear(user, cls));
    }

    private boolean canView(CurrentUser user, ClassroomInfoResponse cls) {
        return teaches(user, cls) || rules.leadsCampus(user, cls.campusId());
    }

    /** Bản nháp chỉ GV lớp thấy danh hiệu; PHT / HT thấy khi đã gửi. */
    private boolean seesItems(CurrentUser user, ClassroomInfoResponse cls, RewardList list) {
        return teaches(user, cls) || list.getStatus() != RewardStatus.DRAFT;
    }

    private boolean canEdit(CurrentUser user, ClassroomInfoResponse cls, RewardList list) {
        return rules.canRecord(user, cls)
                && cls.yearStatus() == SchoolYearStatus.ACTIVE
                && (list == null || list.isEditable());
    }

    private boolean isReviewer(CurrentUser user, RewardList list) {
        return user.hasRole(CareNestRole.VICE_PRINCIPAL) && rules.leadsCampus(user, list.getCampusId());
    }

    private boolean canReview(CurrentUser user, RewardList list) {
        return list != null && list.getStatus() == RewardStatus.PENDING_VP && isReviewer(user, list);
    }

    // HT toàn quyền: phê duyệt được cả danh sách đang chờ PHT (điểm trường chưa có PHT)
    private boolean canDecide(CurrentUser user, RewardList list) {
        return list != null
                && user.hasRole(CareNestRole.PRINCIPAL)
                && (list.getStatus() == RewardStatus.PENDING_PRINCIPAL || list.getStatus() == RewardStatus.PENDING_VP);
    }

    // ---------- kiểm tra ----------

    /** Bỏ dòng không có danh hiệu và ghi chú; trả các dòng hợp lệ (danh hiệu không trùng, giữ thứ tự TITLES). */
    private List<RewardItemRequest> checkItems(ClassroomInfoResponse cls, List<RewardItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Long, ClassChildResponse> children = enrollmentService.findChildrenInClassroom(cls.id(), at(cls)).stream()
                .collect(Collectors.toMap(ClassChildResponse::id, c -> c));
        Map<Long, ChildEvaluation> yearEnds = confirmedYearEnds(cls);
        Set<Long> seen = new HashSet<>();
        List<RewardItemRequest> result = new ArrayList<>();
        for (RewardItemRequest item : items) {
            if (item == null || item.childId() == null || !children.containsKey(item.childId())) {
                throw GlobalException.of(ApiCode.REWARD_CHILD_NOT_IN_CLASS);
            }
            if (!seen.add(item.childId())) {
                throw GlobalException.of(ApiCode.REWARD_CHILD_DUPLICATE);
            }
            String name = children.get(item.childId()).fullName();
            Set<String> picked = new LinkedHashSet<>();
            for (String t : item.titles() == null ? List.<String>of() : item.titles()) {
                String title = t == null ? "" : t.trim();
                if (!TITLES.contains(title)) {
                    throw GlobalException.of(ApiCode.REWARD_TITLE_INVALID, title);
                }
                picked.add(title);
            }
            String note = item.note() == null ? "" : item.note().trim();
            if (note.length() > NOTE_MAX) {
                throw GlobalException.of(ApiCode.REWARD_NOTE_TOO_LONG, name, NOTE_MAX);
            }
            String lower = note.toLowerCase(Locale.ROOT);
            DailyAssessmentServiceImpl.DIAGNOSTIC_TERMS.stream()
                    .filter(lower::contains)
                    .findFirst()
                    .ifPresent(term -> {
                        throw GlobalException.of(ApiCode.REWARD_NOTE_DIAGNOSTIC, name, term);
                    });
            if (picked.isEmpty()) {
                // Không có danh hiệu thì không lưu dòng (ghi chú đi kèm danh hiệu)
                continue;
            }
            ChildEvaluation ye = yearEnds.get(item.childId());
            if (ye == null) {
                throw GlobalException.of(ApiCode.REWARD_YEAR_END_REQUIRED, name);
            }
            if (picked.contains(PROGRAM) && !Boolean.TRUE.equals(ye.getProgramCompleted())) {
                throw GlobalException.of(ApiCode.REWARD_PROGRAM_TITLE_INVALID, name);
            }
            List<String> ordered = TITLES.stream().filter(picked::contains).toList();
            result.add(new RewardItemRequest(item.childId(), ordered, note.isEmpty() ? null : note));
        }
        return result;
    }

    static String checkComment(String value, boolean required) {
        String text = value == null ? "" : value.trim();
        if (required && text.isEmpty()) {
            throw GlobalException.of(ApiCode.REWARD_COMMENT_REQUIRED);
        }
        if (text.length() > COMMENT_MAX) {
            throw GlobalException.of(ApiCode.REWARD_COMMENT_TOO_LONG, COMMENT_MAX);
        }
        return text.isEmpty() ? null : text;
    }

    // ---------- dữ liệu ----------

    private LocalDate at(ClassroomInfoResponse cls) {
        LocalDate today = rules.today();
        LocalDate d = today.isAfter(cls.yearEnd()) ? cls.yearEnd() : today;
        return d.isBefore(cls.yearStart()) ? cls.yearStart() : d;
    }

    private Map<Long, ChildEvaluation> confirmedYearEnds(ClassroomInfoResponse cls) {
        return evaluationRepository
                .findByClassroomIdInAndPeriodType(List.of(cls.id()), EvaluationPeriodType.YEAR)
                .stream()
                .filter(ChildEvaluation::isConfirmed)
                .collect(Collectors.toMap(ChildEvaluation::getChildId, e -> e, (a, b) -> a));
    }

    private boolean isProgramClass(ClassroomInfoResponse cls) {
        if (cls.ageGroupId() == null) {
            return false;
        }
        return ageGroupService.getAgeGroups().stream()
                .filter(g -> cls.ageGroupId().equals(g.id()))
                .map(AgeGroupDetailResponse::ageFromMonths)
                .filter(Objects::nonNull)
                .anyMatch(m -> m >= ChildEvaluationServiceImpl.PROGRAM_AGE_FROM_MONTHS);
    }

    private RewardClassDetail toDetail(CurrentUser user, ClassroomInfoResponse cls, RewardList list) {
        LocalDate today = rules.today();
        LocalDate to = cls.yearEnd().isAfter(today) ? today : cls.yearEnd();
        List<ClassChildResponse> children = enrollmentService.findChildrenInClassroom(cls.id(), at(cls));
        List<Long> childIds = children.stream().map(ClassChildResponse::id).toList();

        // Ngày học của lớp = thứ 2–6 có điểm danh (như báo cáo chất lượng tháng)
        Set<LocalDate> schoolDays = new HashSet<>();
        Map<Long, Integer> present = new HashMap<>();
        if (!to.isBefore(cls.yearStart())) {
            for (ClassChildDay d : attendanceService.findClassDays(List.of(cls.id()), cls.yearStart(), to)) {
                if (!ObservationDayRules.isSchoolDay(d.date())) {
                    continue;
                }
                schoolDays.add(d.date());
                if (d.status() == AttendanceStatus.PRESENT) {
                    present.merge(d.childId(), 1, Integer::sum);
                }
            }
        }
        long weeks = schoolDays.stream()
                .map(d -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .distinct()
                .count();
        long months = schoolDays.stream().map(YearMonth::from).distinct().count();
        Map<Long, List<BehaviourTicket>> tickets = childIds.isEmpty()
                ? Map.of()
                : ticketRepository
                        .findByChildIdInAndPeriodStartBetween(childIds, cls.yearStart(), cls.yearEnd())
                        .stream()
                        .collect(Collectors.groupingBy(BehaviourTicket::getChildId));
        Map<Long, ChildEvaluation> yearEnds = confirmedYearEnds(cls);
        boolean programClass = isProgramClass(cls);
        boolean showItems = list != null && seesItems(user, cls, list);
        Map<Long, RewardListItem> items = showItems
                ? itemRepository.findByListIdIn(List.of(list.getId())).stream()
                        .collect(Collectors.toMap(RewardListItem::getChildId, i -> i))
                : Map.of();

        List<RewardChildRow> rows = children.stream()
                .map(c -> {
                    int presentDays = present.getOrDefault(c.id(), 0);
                    Integer rate = schoolDays.isEmpty() ? null : Math.round(presentDays * 100f / schoolDays.size());
                    List<BehaviourTicket> ts = tickets.getOrDefault(c.id(), List.of());
                    int weekly = (int) ts.stream()
                            .filter(t -> t.getType() == TicketType.WEEKLY)
                            .count();
                    int monthly = (int) ts.stream()
                            .filter(t -> t.getType() == TicketType.MONTHLY)
                            .count();
                    ChildEvaluation ye = yearEnds.get(c.id());
                    List<String> suggested = new ArrayList<>();
                    if (ye != null) {
                        if (rate != null && rate >= DILIGENT_PERCENT) {
                            suggested.add(DILIGENT);
                        }
                        // Bé ngoan: phiếu tuần từ nửa số tuần học; BNTD: phiếu tháng từ nửa số tháng học
                        if (weeks > 0 && weekly * 2L >= weeks) {
                            suggested.add(GOOD);
                        }
                        if (months > 0 && monthly * 2L >= months) {
                            suggested.add(ALL_ROUND);
                        }
                        if (programClass && Boolean.TRUE.equals(ye.getProgramCompleted())) {
                            suggested.add(PROGRAM);
                        }
                    }
                    RewardListItem item = items.get(c.id());
                    return new RewardChildRow(
                            c.id(),
                            c.fullName(),
                            c.dateOfBirth(),
                            schoolDays.size(),
                            presentDays,
                            rate,
                            weekly,
                            monthly,
                            ye == null ? null : new YearEndRef(ye.getId(), ye.getFinalContent(), ye.getConfirmedAt()),
                            ye == null ? null : ye.getProgramCompleted(),
                            suggested,
                            item == null ? List.of() : item.titleList(),
                            item == null ? null : item.getNote());
                })
                .toList();

        List<RewardListEvent> events =
                list == null ? List.of() : eventRepository.findByListIdOrderByAtAscIdAsc(list.getId());
        Map<Long, String> names = list == null
                ? Map.of()
                : accountService.findFullNames(Stream.concat(
                                Stream.of(
                                        list.getCreatedBy(),
                                        list.getUpdatedBy(),
                                        list.getReviewBy(),
                                        list.getDecisionBy()),
                                events.stream().map(RewardListEvent::getUserId))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        return new RewardClassDetail(
                list == null ? null : list.getId(),
                cls.id(),
                cls.name(),
                cls.campusId(),
                cls.schoolYearId(),
                programClass,
                list == null ? null : list.getStatus(),
                list == null ? null : list.getCreatedBy(),
                list == null ? null : names.get(list.getCreatedBy()),
                list == null ? null : list.getUpdatedBy(),
                list == null ? null : names.get(list.getUpdatedBy()),
                list == null ? null : list.getSubmittedAt(),
                list == null || list.getReviewBy() == null
                        ? null
                        : new Step(
                                list.getReviewBy(),
                                names.get(list.getReviewBy()),
                                list.getReviewAt(),
                                list.getReviewDecision(),
                                list.getReviewComment()),
                list == null || list.getDecisionBy() == null
                        ? null
                        : new Step(
                                list.getDecisionBy(),
                                names.get(list.getDecisionBy()),
                                list.getDecisionAt(),
                                list.getDecision(),
                                list.getDecisionComment()),
                programClass
                        ? TITLES
                        : TITLES.stream().filter(t -> !PROGRAM.equals(t)).toList(),
                rows,
                events.stream()
                        .map(e -> new HistoryRow(
                                e.getId(),
                                e.getAction(),
                                e.getUserId(),
                                names.get(e.getUserId()),
                                e.getAt(),
                                e.getNote()))
                        .toList(),
                canEdit(user, cls, list),
                canReview(user, list),
                canDecide(user, list));
    }
}
