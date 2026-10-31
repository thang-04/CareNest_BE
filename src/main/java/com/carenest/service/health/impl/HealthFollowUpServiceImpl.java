package com.carenest.service.health.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.ChildMedicalHistory;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.dto.health.HealthFollowUpCloseRequest;
import com.carenest.dto.health.HealthFollowUpContactRequest;
import com.carenest.dto.health.HealthFollowUpExternalResultRequest;
import com.carenest.dto.health.HealthFollowUpListResponse;
import com.carenest.dto.health.HealthFollowUpResponse;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthFollowUp;
import com.carenest.entity.health.HealthFollowUpEvent;
import com.carenest.entity.health.HealthFollowUpImage;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.HealthImageStorage;
import com.carenest.repository.health.HealthFollowUpEventRepository;
import com.carenest.repository.health.HealthFollowUpImageRepository;
import com.carenest.repository.health.HealthFollowUpRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.health.HealthFollowUpService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.DeclarationState;
import com.carenest.utils.enums.HealthDeclarationAction;
import com.carenest.utils.enums.HealthFollowUpAction;
import com.carenest.utils.enums.HealthFollowUpOutcome;
import com.carenest.utils.enums.HealthFollowUpStatus;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
public class HealthFollowUpServiceImpl implements HealthFollowUpService {
    private static final String LOG_PREFIX = "[HealthFollowUpServiceImpl]";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int NOTE_MAX = 500;
    private static final int FACILITY_MAX = 150;
    private static final int REASON_MAX = 300;
    private static final int CONDITION_MAX = 100;
    private static final int SELF_HISTORY_MAX = 300;
    private static final int IMAGES_MAX = 3;
    private static final String OVERDUE = "OVERDUE";

    private final HealthCheckRules rules;
    private final HealthRecords records;
    private final HealthFollowUpRepository followUpRepository;
    private final HealthFollowUpEventRepository eventRepository;
    private final HealthFollowUpImageRepository imageRepository;
    private final HealthImageStorage imageStorage;
    private final ClassroomAccessPolicy classroomAccessPolicy;
    private final ChildService childService;
    private final ChildIntakeService childIntakeService;
    private final EnrollmentService enrollmentService;
    private final ClassroomService classroomService;
    private final SchoolStructureService schoolStructureService;
    private final AccountService accountService;

    /** Trẻ của phiếu với lớp, điểm trường đang học hôm nay (lớp null khi trẻ không còn lớp). */
    private record ChildRef(
            Long id, String fullName, String code, ClassroomInfoResponse cls, Long campusId, String campusName) {}

    @Override
    @Transactional(readOnly = true)
    public HealthFollowUpListResponse listFollowUps(
            CurrentUser currentUser, Long campusId, HealthFollowUpStatus status, boolean overdue, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|listFollowUps|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            List<Long> childIds = followUpRepository.findAll().stream()
                    .map(HealthFollowUp::getChildId)
                    .distinct()
                    .toList();
            HealthRecords.Snapshot s = records.load(childIds);
            List<HealthFollowUp> all =
                    childIds.stream().flatMap(id -> s.followUps(id).stream()).toList();
            Map<Long, ChildRef> children = childRefs(all);
            Set<Long> managed = rules.managedCampusIds(currentUser);
            Map<Long, Boolean> classScope = new HashMap<>();
            Map<Long, String> names = names(s, all);
            List<HealthFollowUpResponse> items = all.stream()
                    .filter(f -> canSee(currentUser, f, children.get(f.getChildId()), managed, classScope))
                    .filter(f -> inSchoolYear(f, s, schoolYearId))
                    .map(f -> toView(currentUser, s, f, children.get(f.getChildId()), names, false))
                    .filter(v -> campusId == null || campusId.equals(v.child().campusId()))
                    .toList();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (HealthFollowUpStatus st : HealthFollowUpStatus.values()) {
                counts.put(st.name(), (int)
                        items.stream().filter(v -> v.status() == st).count());
            }
            counts.put(OVERDUE, (int)
                    items.stream().filter(HealthFollowUpServiceImpl::isOverdue).count());
            List<HealthFollowUpResponse> filtered = items.stream()
                    .filter(v -> status == null || v.status() == status)
                    .filter(v -> !overdue || isOverdue(v))
                    .sorted(Comparator.comparingInt(
                                    (HealthFollowUpResponse v) -> v.status().ordinal())
                            .thenComparing(
                                    v -> v.currentRound() == null
                                            ? 0
                                            : v.currentRound().overdueDays(),
                                    Comparator.reverseOrder())
                            .thenComparing(HealthFollowUpResponse::updatedAt, Comparator.reverseOrder()))
                    .toList();
            log.info("{}|items={}|END", logPrefix, filtered.size());
            return new HealthFollowUpListResponse(filtered, counts);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HealthFollowUpResponse getFollowUp(CurrentUser currentUser, Long followUpId) {
        String logPrefix = LOG_PREFIX + "|getFollowUp|userId=" + currentUser.userId() + "|followUpId=" + followUpId;
        log.info("{}|START", logPrefix);
        try {
            Target t = visible(currentUser, followUpId);
            HealthFollowUpResponse view = detail(currentUser, t.followUp().getId(), t.child());
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthFollowUpResponse addParentContact(
            CurrentUser currentUser, Long followUpId, HealthFollowUpContactRequest request) {
        String logPrefix =
                LOG_PREFIX + "|addParentContact|userId=" + currentUser.userId() + "|followUpId=" + followUpId;
        log.info("{}|START", logPrefix);
        try {
            Target t = updatable(currentUser, followUpId);
            HealthFollowUpState.Round round = t.snapshot().state(t.followUp()).round();
            List<FieldErrorResponse> errors = new ArrayList<>();
            LocalDate on = request.contactedOn();
            if (on == null) {
                errors.add(error("contactedOn", "Chọn ngày trao đổi"));
            } else if (on.isAfter(rules.today())) {
                errors.add(error("contactedOn", "Ngày không được ở tương lai"));
            } else if (on.isBefore(round.contactFrom())) {
                errors.add(error(
                        "contactedOn", "Ngày trao đổi không được trước ngày khám " + DMY.format(round.contactFrom())));
            }
            String note = trimmed(request.note());
            if (note.isEmpty()) {
                errors.add(error("note", "Nhập nội dung đã trao đổi với phụ huynh"));
            } else if (note.length() > NOTE_MAX) {
                errors.add(error("note", "Nội dung tối đa 500 ký tự"));
            }
            throwIfAny(errors);
            Instant now = rules.now();
            eventRepository.save(
                    HealthFollowUpEvent.parentContact(t.followUp().getId(), on, note, null, currentUser.userId(), now));
            t.followUp().touch(now);
            HealthFollowUpResponse view = detail(currentUser, t.followUp().getId(), t.child());
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthFollowUpResponse addExternalResult(
            CurrentUser currentUser, Long followUpId, HealthFollowUpExternalResultRequest request) {
        String logPrefix =
                LOG_PREFIX + "|addExternalResult|userId=" + currentUser.userId() + "|followUpId=" + followUpId;
        log.info("{}|START", logPrefix);
        try {
            Target t = updatable(currentUser, followUpId);
            // Kết quả chuyên khoa ghi sau khi đã trao đổi phụ huynh về kết quả khám mới nhất
            if (!t.snapshot().state(t.followUp()).round().contacted()) {
                throw GlobalException.of(ApiCode.HEALTH_FOLLOW_UP_CONTACT_FIRST);
            }
            List<FieldErrorResponse> errors = new ArrayList<>();
            LocalDate resultOn = request.resultOn();
            if (resultOn == null) {
                errors.add(error("resultOn", "Chọn ngày khám"));
            } else if (resultOn.isAfter(rules.today())) {
                errors.add(error("resultOn", "Ngày không được ở tương lai"));
            }
            String note = trimmed(request.note());
            if (note.isEmpty()) {
                errors.add(error("note", "Nhập kết quả bác sĩ chuyên khoa đã báo lại"));
            } else if (note.length() > NOTE_MAX) {
                errors.add(error("note", "Nội dung tối đa 500 ký tự"));
            }
            String facility = trimmed(request.facility());
            if (facility.isEmpty()) {
                errors.add(error("facility", "Nhập chuyên khoa / nơi khám, ví dụ Mắt — Bệnh viện Nhi"));
            } else if (facility.length() > FACILITY_MAX) {
                errors.add(error("facility", "Tối đa 150 ký tự"));
            }
            List<String> rawImages = request.images() == null ? List.of() : request.images();
            List<HealthImageData> images =
                    rawImages.stream().map(HealthImageData::decode).toList();
            if (rawImages.size() > IMAGES_MAX) {
                errors.add(error("images", "Tối đa 3 ảnh"));
            } else if (images.stream().anyMatch(Objects::isNull)) {
                errors.add(error("images", "Ảnh phải là JPG, PNG hoặc WEBP, tối đa 1 MB mỗi ảnh"));
            }
            LocalDate nextVisitOn = request.nextVisitOn();
            if (nextVisitOn != null && resultOn != null && !nextVisitOn.isAfter(resultOn)) {
                errors.add(error("nextVisitOn", "Ngày hẹn khám lại phải sau ngày khám"));
            }
            throwIfAny(errors);
            Instant now = rules.now();
            List<String> keys = new ArrayList<>();
            try {
                HealthFollowUpEvent event = eventRepository.save(HealthFollowUpEvent.externalResult(
                        t.followUp().getId(), resultOn, note, facility, nextVisitOn, currentUser.userId(), now));
                for (int i = 0; i < images.size(); i++) {
                    HealthImageData image = images.get(i);
                    String key = imageStorage.store(image.content(), image.contentType());
                    keys.add(key);
                    imageRepository.save(HealthFollowUpImage.of(
                            event.getId(), i, key, image.contentType(), image.content().length, now));
                }
            } catch (RuntimeException ex) {
                keys.forEach(imageStorage::removeQuietly);
                throw ex;
            }
            t.followUp().touch(now);
            HealthFollowUpResponse view = detail(currentUser, t.followUp().getId(), t.child());
            log.info("{}|images={}|END", logPrefix, images.size());
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthFollowUpResponse closeFollowUp(
            CurrentUser currentUser, Long followUpId, HealthFollowUpCloseRequest request) {
        String logPrefix = LOG_PREFIX + "|closeFollowUp|userId=" + currentUser.userId() + "|followUpId=" + followUpId
                + "|outcome=" + request.outcome();
        log.info("{}|START", logPrefix);
        try {
            Target t = updatable(currentUser, followUpId);
            HealthFollowUpOutcome outcome = request.outcome();
            String conditionText = trimmed(request.conditionText());
            String reason = trimmed(request.reason());
            boolean known = outcome == HealthFollowUpOutcome.KNOWN_CONDITION;
            List<FieldErrorResponse> errors = new ArrayList<>();
            if (outcome == null) {
                errors.add(error("outcome", "Chọn kết quả theo lời bác sĩ"));
            } else if (!t.snapshot().closeOptions(t.followUp()).contains(outcome)) {
                errors.add(error(
                        "outcome",
                        known
                                ? "Ghi kết quả khám chuyên khoa vào phiếu trước khi chọn kết quả này"
                                : "Ghi kết quả khám chuyên khoa, hoặc chờ lần khám sau ngày mở phiếu, trước khi chọn"
                                        + " kết quả này"));
            }
            Optional<ChildMedicalHistory> history = Optional.empty();
            if (known) {
                history = childIntakeService.findMedicalHistory(t.followUp().getChildId());
                if (conditionText.isEmpty()) {
                    errors.add(error("conditionText", "Nhập tên vấn đề, ví dụ Cận thị – đeo kính"));
                } else if (conditionText.length() > CONDITION_MAX) {
                    errors.add(error("conditionText", "Tên vấn đề tối đa 100 ký tự"));
                } else if (selfHistoryWith(history.orElse(null), conditionText).length() > SELF_HISTORY_MAX) {
                    errors.add(error(
                            "conditionText",
                            "Tiền sử bản thân của trẻ đã dài; rút gọn tên vấn đề (cả tiền sử tối đa 300 ký tự)"));
                }
            }
            if (reason.isEmpty()) {
                errors.add(error("reason", "Nhập lý do đóng phiếu"));
            } else if (reason.length() > REASON_MAX) {
                errors.add(error("reason", "Lý do tối đa 300 ký tự"));
            }
            throwIfAny(errors);
            Instant now = rules.now();
            HealthFollowUp f = t.followUp();
            f.close(currentUser.userId(), reason, outcome, known ? conditionText : null, now);
            eventRepository.save(HealthFollowUpEvent.closed(
                    f.getId(), reason, outcome, known ? conditionText : null, null, currentUser.userId(), now));
            if (known) {
                recordKnownCondition(f.getChildId(), history, conditionText, currentUser, now);
            }
            HealthFollowUpResponse view = detail(currentUser, f.getId(), t.child());
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent getImage(CurrentUser currentUser, Long followUpId, Long imageId) {
        String logPrefix = LOG_PREFIX + "|getImage|userId=" + currentUser.userId() + "|followUpId=" + followUpId
                + "|imageId=" + imageId;
        log.info("{}|START", logPrefix);
        try {
            Target t = visible(currentUser, followUpId);
            HealthFollowUpImage image = t.snapshot().events(t.followUp()).stream()
                    .flatMap(e -> t.snapshot().images(e).stream())
                    .filter(i -> i.getId().equals(imageId))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_FOLLOW_UP_IMAGE_NOT_FOUND));
            ImageContent content = new ImageContent(imageStorage.load(image.getStorageKey()), image.getContentType());
            log.info("{}|END", logPrefix);
            return content;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Phiếu, trẻ, dữ liệu sức khỏe của trẻ đã đọc. */
    private record Target(HealthFollowUp followUp, ChildRef child, HealthRecords.Snapshot snapshot) {}

    /** Ngoài phạm vi trả 404 như không tồn tại. */
    private Target visible(CurrentUser user, Long followUpId) {
        HealthFollowUp found = followUpRepository
                .findById(followUpId)
                .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_FOLLOW_UP_NOT_FOUND));
        HealthRecords.Snapshot s = records.load(List.of(found.getChildId()));
        HealthFollowUp f = s.followUp(followUpId);
        ChildRef child = childRefs(List.of(f)).get(f.getChildId());
        if (child == null || !canSee(user, f, child, rules.managedCampusIds(user), new HashMap<>())) {
            throw GlobalException.of(ApiCode.HEALTH_FOLLOW_UP_NOT_FOUND);
        }
        return new Target(f, child, s);
    }

    /** GV đang phụ trách lớp hiện tại của trẻ cập nhật phiếu đang mở; BGH chỉ xem. */
    private Target updatable(CurrentUser user, Long followUpId) {
        Target t = visible(user, followUpId);
        if (t.child().cls() == null || !rules.teaches(user, t.child().cls().id())) {
            throw GlobalException.of(ApiCode.HEALTH_FOLLOW_UP_ACCESS_DENIED);
        }
        if (!t.followUp().isOpen()) {
            throw GlobalException.of(ApiCode.HEALTH_FOLLOW_UP_CLOSED);
        }
        return t;
    }

    /**
     * Theo lớp trẻ đang học ({@code ClassroomAccessPolicy}: HT mọi lớp, PHT lớp điểm trường mình, GV lớp mình); trẻ không
     * còn lớp ⇒ HT, PHT của điểm trường lúc mở phiếu.
     */
    private boolean canSee(
            CurrentUser user, HealthFollowUp f, ChildRef child, Set<Long> managed, Map<Long, Boolean> classScope) {
        if (child == null) {
            return false;
        }
        if (child.cls() != null) {
            return classScope.computeIfAbsent(
                    child.cls().id(), classId -> classroomAccessPolicy.canViewClassroom(user, classId));
        }
        return managed == null || managed.contains(f.getCampusId());
    }

    /**
     * Năm học: phiếu đang mở luôn hiện; phiếu đã đóng hiện khi mở trước năm học sau và đóng từ đầu năm học này. Năm học
     * không tồn tại ⇒ chỉ phiếu đang mở.
     */
    private boolean inSchoolYear(HealthFollowUp f, HealthRecords.Snapshot s, Long schoolYearId) {
        if (schoolYearId == null || f.isOpen()) {
            return true;
        }
        List<SchoolYearResponse> years = schoolStructureService.findSchoolYears().stream()
                .filter(y -> y.startDate() != null)
                .sorted(Comparator.comparing(SchoolYearResponse::startDate))
                .toList();
        SchoolYearResponse year = years.stream()
                .filter(y -> y.id().equals(schoolYearId))
                .findFirst()
                .orElse(null);
        if (year == null) {
            return false;
        }
        LocalDate nextStart = years.stream()
                .map(SchoolYearResponse::startDate)
                .filter(d -> d.isAfter(year.startDate()))
                .findFirst()
                .orElse(null);
        return (nextStart == null || s.localDate(f.getOpenedAt()).isBefore(nextStart))
                && !s.localDate(f.getClosedAt()).isBefore(year.startDate());
    }

    private Map<Long, ChildRef> childRefs(Collection<HealthFollowUp> followUps) {
        Set<Long> childIds = followUps.stream().map(HealthFollowUp::getChildId).collect(Collectors.toSet());
        if (childIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ChildBriefResponse> briefs = childService.findChildBriefs(childIds);
        Map<Long, Long> classIds = enrollmentService.findActiveClassroomIdByChildIds(childIds, rules.today());
        Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
        classIds.values().stream()
                .distinct()
                .forEach(id -> classroomService.findClassroomInfo(id).ifPresent(c -> classes.put(id, c)));
        Map<Long, Long> campusOfChild = followUps.stream()
                .collect(Collectors.toMap(HealthFollowUp::getChildId, HealthFollowUp::getCampusId, (a, b) -> b));
        Map<Long, ChildRef> refs = new HashMap<>();
        Set<Long> campusIds = new HashSet<>(campusOfChild.values());
        classes.values().forEach(c -> campusIds.add(c.campusId()));
        Map<Long, String> campusNames = schoolStructureService.findCampusNames(campusIds);
        for (Long childId : childIds) {
            ChildBriefResponse brief = briefs.get(childId);
            if (brief == null) {
                continue;
            }
            ClassroomInfoResponse cls = classes.get(classIds.get(childId));
            Long campusId = cls != null ? cls.campusId() : campusOfChild.get(childId);
            refs.put(
                    childId,
                    new ChildRef(childId, brief.fullName(), brief.code(), cls, campusId, campusNames.get(campusId)));
        }
        return refs;
    }

    private Map<Long, String> names(HealthRecords.Snapshot s, Collection<HealthFollowUp> followUps) {
        return accountService.findFullNames(Stream.concat(
                        s.eventUserIds(followUps).stream(),
                        followUps.stream().map(HealthFollowUp::getClosedBy).filter(Objects::nonNull))
                .collect(Collectors.toSet()));
    }

    /** Chi tiết phiếu đọc lại sau khi ghi. */
    private HealthFollowUpResponse detail(CurrentUser user, Long followUpId, ChildRef child) {
        HealthFollowUp found = followUpRepository.findById(followUpId).orElseThrow();
        HealthRecords.Snapshot s = records.load(List.of(found.getChildId()));
        HealthFollowUp f = s.followUp(followUpId);
        return toView(user, s, f, child, names(s, List.of(f)), true);
    }

    private HealthFollowUpResponse toView(
            CurrentUser user,
            HealthRecords.Snapshot s,
            HealthFollowUp f,
            ChildRef child,
            Map<Long, String> names,
            boolean detail) {
        HealthFollowUpState.State state = s.state(f);
        HealthFollowUpState.Round round = state.round();
        List<HealthFollowUpEvent> events = s.events(f);
        boolean canUpdate = f.isOpen()
                && child.cls() != null
                && rules.teaches(user, child.cls().id());
        HealthFollowUpEvent external = HealthFollowUpState.latestExternal(events);
        List<HealthMeasurement> results = s.followUpResults(f);
        LocalDate dateOfBirth = detail ? briefOf(child.id()).dateOfBirth() : null;
        return new HealthFollowUpResponse(
                f.getId(),
                state.status(),
                f.getOpenedAt(),
                f.getUpdatedAt(),
                f.getClosedAt(),
                f.getClosedBy() == null ? null : names.get(f.getClosedBy()),
                f.getCloseReason(),
                f.getCloseOutcome(),
                f.getCloseConditionText(),
                canUpdate ? s.closeOptions(f) : List.of(),
                new HealthFollowUpResponse.HealthFollowUpChild(
                        child.id(),
                        child.fullName(),
                        child.code(),
                        child.cls() == null ? null : child.cls().id(),
                        child.cls() == null ? null : child.cls().name(),
                        child.campusId(),
                        child.campusName()),
                results.isEmpty() ? null : measurementBrief(s, results.getLast()),
                s.tbRisk(f),
                s.referral(f),
                round == null
                        ? null
                        : new HealthFollowUpResponse.HealthFollowUpRound(
                                round.startedAt(),
                                round.contactFrom(),
                                round.dueOn(),
                                round.contacted(),
                                round.overdueDays()),
                s.externalBrief(external, external == null ? null : nameOf(names, external.getUserId())),
                canUpdate,
                canUpdate && round != null && round.contacted(),
                detail
                        ? s.results(f.getChildId()).stream()
                                .findFirst()
                                .map(m -> measurementBrief(s, m))
                                .orElse(null)
                        : null,
                detail ? timeline(s, f, state, names) : null,
                detail ? records.views(s, results, dateOfBirth, false) : null);
    }

    private List<HealthFollowUpResponse.HealthFollowUpEventView> timeline(
            HealthRecords.Snapshot s, HealthFollowUp f, HealthFollowUpState.State state, Map<Long, String> names) {
        return s.events(f).stream()
                .map(e -> {
                    boolean entry = e.isExamEntry();
                    HealthMeasurement m = e.getMeasurementId() == null ? null : s.measurement(e.getMeasurementId());
                    return new HealthFollowUpResponse.HealthFollowUpEventView(
                            e.getId(),
                            e.getAction(),
                            e.getCreatedAt(),
                            e.getUserId(),
                            nameOf(names, e.getUserId()),
                            e.getMeasurementId(),
                            entry ? e.getEventDate() : null,
                            e.getNewFinding(),
                            e.getAction() == HealthFollowUpAction.MEASUREMENT_ADDED
                                    && state.roundStartIds().contains(e.getId()),
                            entry ? null : e.getEventDate(),
                            e.getNote(),
                            e.isAtExam(),
                            e.getFacility(),
                            s.imageViews(e),
                            e.getNextVisitOn(),
                            e.getOutcome(),
                            e.getConditionText(),
                            e.getAutoReason(),
                            m == null ? null : measurementBrief(s, m));
                })
                .toList();
    }

    private static HealthFollowUpResponse.HealthFollowUpMeasurement measurementBrief(
            HealthRecords.Snapshot s, HealthMeasurement m) {
        HealthCheckCampaign c = s.campaign(m.getCampaignId());
        return new HealthFollowUpResponse.HealthFollowUpMeasurement(
                m.getId(),
                m.getDate(),
                c == null ? null : c.getName(),
                s.outcome(m).level(),
                m.isExam() ? HealthMeasurementResponse.SOURCE_EXAM : HealthMeasurementResponse.SOURCE_MEASUREMENT_ONLY,
                s.conclusion(m));
    }

    private ChildBriefResponse briefOf(Long childId) {
        ChildBriefResponse brief = childService.findChildBriefs(Set.of(childId)).get(childId);
        if (brief == null) {
            throw GlobalException.of(ApiCode.CHILD_NOT_FOUND);
        }
        return brief;
    }

    /** Đóng "vấn đề đã biết": thêm vấn đề vào tiền sử bản thân của khai báo (HLT-06) nếu chưa có. */
    private void recordKnownCondition(
            Long childId, Optional<ChildMedicalHistory> history, String condition, CurrentUser user, Instant now) {
        if (history.isEmpty()) {
            // Phiếu mở từ phiếu khám đã kết luận nên khai báo luôn có; thiếu thì không tự tạo tiền sử gia đình, lao
            log.warn("{}|recordKnownCondition|childId={}|declaration missing", LOG_PREFIX, childId);
            return;
        }
        ChildMedicalHistory h = history.get();
        String self = selfHistoryWith(h, condition);
        if (h.self() == DeclarationState.REPORTED && self.equals(h.selfText())) {
            return;
        }
        childIntakeService.updateMedicalHistory(
                childId,
                new ChildMedicalHistory(
                        DeclarationState.REPORTED, self, h.family(), h.familyText(), h.tbContact(), h.tbContactDate()),
                HealthDeclarationAction.UPDATED_FROM_FOLLOW_UP,
                "Kết quả đóng phiếu theo dõi ngày " + DMY.format(rules.today()),
                user.userId(),
                now);
    }

    /** Tiền sử bản thân sau khi thêm vấn đề; đã có (không phân biệt hoa thường, dấu) thì giữ nguyên. */
    static String selfHistoryWith(ChildMedicalHistory h, String condition) {
        String current = h != null && h.self() == DeclarationState.REPORTED ? trimmed(h.selfText()) : "";
        if (current.isEmpty()) {
            return condition;
        }
        String key = fold(condition);
        boolean known = Arrays.stream(current.split(";\\s*"))
                .anyMatch(part -> fold(part).equals(key));
        return known ? current : current + "; " + condition;
    }

    private static String fold(String value) {
        String noMarks = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private static boolean isOverdue(HealthFollowUpResponse v) {
        return v.currentRound() != null && v.currentRound().overdueDays() > 0;
    }

    private static String nameOf(Map<Long, String> names, Long userId) {
        return userId == null ? "" : names.getOrDefault(userId, "");
    }

    private static FieldErrorResponse error(String field, String message) {
        return new FieldErrorResponse(field, message);
    }

    private static void throwIfAny(List<FieldErrorResponse> errors) {
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
