package com.carenest.service.health.impl;

import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamResponse;
import com.carenest.dto.health.HealthFollowUpResponse;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthFollowUp;
import com.carenest.entity.health.HealthFollowUpEvent;
import com.carenest.entity.health.HealthFollowUpImage;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.entity.health.HealthMeasurementEdit;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.repository.health.HealthFollowUpEventRepository;
import com.carenest.repository.health.HealthFollowUpImageRepository;
import com.carenest.repository.health.HealthFollowUpRepository;
import com.carenest.repository.health.HealthMeasurementEditRepository;
import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.service.account.AccountService;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.HealthFollowUpAction;
import com.carenest.utils.enums.HealthFollowUpEffect;
import com.carenest.utils.enums.HealthFollowUpLevel;
import com.carenest.utils.enums.HealthFollowUpOutcome;
import com.carenest.utils.enums.HealthLatestSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Dữ liệu sức khỏe của một nhóm trẻ đọc một lần (lần đo, đợt/kỳ, phiếu khám, phiếu theo dõi, dòng thời gian, ảnh) và các
 * giá trị tính từ đó: mức theo dõi, lần khám trước, mức hiện tại, tóm tắt phiếu theo dõi, lần đo trả về API.
 */
@Component
@RequiredArgsConstructor
class HealthRecords {

    // Vòng đầu chỉ đo khi trẻ chưa quá 60 tháng tại ngày đo
    static final int HEAD_CIRCUMFERENCE_MAX_MONTHS = 60;
    static final String HEAD_NOT_MEASURED = "NOT_MEASURED";
    private static final TypeReference<List<HealthMeasurementResponse.HealthMeasurementChange>> CHANGES =
            new TypeReference<>() {};

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final HealthMeasurementRepository measurementRepository;
    private final HealthMeasurementEditRepository editRepository;
    private final HealthCheckCampaignRepository campaignRepository;
    private final HealthFollowUpRepository followUpRepository;
    private final HealthFollowUpEventRepository eventRepository;
    private final HealthFollowUpImageRepository imageRepository;
    private final AccountService accountService;

    HealthExamForm readExam(String json) {
        return json == null ? null : objectMapper.readValue(json, HealthExamForm.class);
    }

    String writeExam(HealthExamForm exam) {
        return objectMapper.writeValueAsString(exam);
    }

    String writeChanges(List<HealthMeasurementResponse.HealthMeasurementChange> changes) {
        return objectMapper.writeValueAsString(changes);
    }

    /** Ngày cuối còn đo vòng đầu: trước ngày trẻ đủ 61 tháng. */
    static LocalDate headCircumferenceUntil(LocalDate dateOfBirth) {
        return dateOfBirth.plusMonths(HEAD_CIRCUMFERENCE_MAX_MONTHS + 1L).minusDays(1);
    }

    /** Mức theo dõi của một lần đo (đọc JSON phiếu khám khi đã kết luận). */
    HealthExamOutcome.Outcome outcomeOf(HealthMeasurement m) {
        return m.getExamStatus() == HealthExamStatus.CONCLUDED
                ? HealthExamOutcome.of(m, readExam(m.getExam()))
                : HealthExamOutcome.UNAVAILABLE;
    }

    /** Trạng thái phiếu theo dõi đang mở của các trẻ (đếm chờ trao đổi, quá hạn), theo trẻ. */
    Map<Long, HealthFollowUpState.State> openStates(Collection<Long> childIds) {
        if (childIds.isEmpty()) {
            return Map.of();
        }
        List<HealthFollowUp> open = followUpRepository.findByChildIdInAndClosedAtIsNull(childIds);
        if (open.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<HealthFollowUpEvent>> events =
                eventRepository
                        .findByFollowUpIdInOrderByIdAsc(
                                open.stream().map(HealthFollowUp::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(HealthFollowUpEvent::getFollowUpId));
        LocalDate today = LocalDate.now(clock);
        return open.stream()
                .collect(Collectors.toMap(
                        HealthFollowUp::getChildId,
                        f -> HealthFollowUpState.of(
                                f, events.getOrDefault(f.getId(), List.of()), today, clock.getZone())));
    }

    /** Mọi lần đo, phiếu theo dõi của các trẻ. */
    Snapshot load(Collection<Long> childIds) {
        if (childIds.isEmpty()) {
            return snapshot(List.of(), List.of());
        }
        return snapshot(measurementRepository.findByChildIdIn(childIds), followUpRepository.findByChildIdIn(childIds));
    }

    /** Snapshot từ lần đo, phiếu theo dõi đã đọc; đọc thêm đợt/kỳ, dòng thời gian, ảnh. */
    Snapshot snapshot(List<HealthMeasurement> measurements, List<HealthFollowUp> followUps) {
        Map<Long, HealthCheckCampaign> campaigns = campaignRepository
                .findAllById(measurements.stream()
                        .map(HealthMeasurement::getCampaignId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(HealthCheckCampaign::getId, Function.identity()));
        List<HealthFollowUpEvent> events = followUps.isEmpty()
                ? List.of()
                : eventRepository.findByFollowUpIdInOrderByIdAsc(
                        followUps.stream().map(HealthFollowUp::getId).toList());
        List<Long> externalIds = events.stream()
                .filter(e -> e.getAction() == HealthFollowUpAction.EXTERNAL_RESULT)
                .map(HealthFollowUpEvent::getId)
                .toList();
        List<HealthFollowUpImage> images = externalIds.isEmpty()
                ? List.of()
                : imageRepository.findByEventIdInOrderByEventIdAscPositionAsc(externalIds);
        Map<Long, HealthExamForm> exams = new HashMap<>();
        measurements.stream()
                .filter(HealthMeasurement::isExam)
                .forEach(m -> exams.put(m.getId(), readExam(m.getExam())));
        return new Snapshot(
                LocalDate.now(clock), clock.getZone(), measurements, campaigns, exams, followUps, events, images);
    }

    /** Lần đo trả về API; {@code withReview}: kèm điểm cần bác sĩ xem lại (API phiếu khám). */
    List<HealthMeasurementResponse> views(
            Snapshot s, List<HealthMeasurement> measurements, LocalDate dateOfBirth, boolean withReview) {
        Map<Long, List<HealthMeasurementEdit>> edits = measurements.isEmpty()
                ? Map.of()
                : editRepository
                        .findByMeasurementIdInOrderByCreatedAtAscIdAsc(measurements.stream()
                                .map(HealthMeasurement::getId)
                                .toList())
                        .stream()
                        .collect(Collectors.groupingBy(HealthMeasurementEdit::getMeasurementId));
        Map<Long, String> names = accountService.findFullNames(Stream.concat(
                        measurements.stream().map(HealthMeasurement::getMeasuredBy),
                        edits.values().stream().flatMap(List::stream).map(HealthMeasurementEdit::getUserId))
                .collect(Collectors.toSet()));
        return measurements.stream()
                .map(m -> view(s, m, dateOfBirth, edits.getOrDefault(m.getId(), List.of()), names, withReview))
                .toList();
    }

    private HealthMeasurementResponse view(
            Snapshot s,
            HealthMeasurement m,
            LocalDate dateOfBirth,
            List<HealthMeasurementEdit> edits,
            Map<Long, String> names,
            boolean withReview) {
        HealthCheckCampaign c = s.campaign(m.getCampaignId());
        int ageMonths = (int) ChronoUnit.MONTHS.between(dateOfBirth, m.getDate());
        HealthExamForm exam = s.exam(m);
        HealthExamOutcome.Outcome outcome = s.outcome(m);
        String headStatus = null;
        if (exam != null) {
            String doctor = exam.nutrition() == null
                    ? ""
                    : HealthExamOutcome.text(exam.nutrition().headStatus());
            headStatus =
                    ageMonths > HEAD_CIRCUMFERENCE_MAX_MONTHS ? HEAD_NOT_MEASURED : doctor.isEmpty() ? null : doctor;
        }
        HealthFollowUp followUp = s.followUpOf(m.getId());
        return new HealthMeasurementResponse(
                m.getId(),
                m.getChildId(),
                m.getCampaignId(),
                c == null ? null : c.getName(),
                c == null ? null : c.getKind(),
                m.getClassroomId(),
                m.getDate(),
                ageMonths,
                m.getHeightCm(),
                m.getWeightKg(),
                m.getHeadCircumferenceCm(),
                bmi(m),
                m.getNote(),
                m.isExam() ? HealthMeasurementResponse.SOURCE_EXAM : HealthMeasurementResponse.SOURCE_MEASUREMENT_ONLY,
                m.getPublishedAt() != null,
                m.getPublishedAt(),
                m.getMeasuredBy(),
                names.get(m.getMeasuredBy()),
                c == null || c.statusOn(s.today()) != HealthCheckStatus.IN_PROGRESS,
                edits.stream()
                        .map(e -> new HealthMeasurementResponse.HealthMeasurementEditView(
                                e.getCreatedAt(),
                                e.getUserId(),
                                names.get(e.getUserId()),
                                e.getReason(),
                                objectMapper.readValue(e.getChanges(), CHANGES)))
                        .toList(),
                m.getExamStatus(),
                exam,
                m.getConcludedAt(),
                headStatus,
                outcome.level(),
                outcome.category(),
                HealthExamOutcome.brief(m, exam),
                followUp == null ? null : followUp.getId(),
                s.effect(m),
                s.parentContact(m),
                withReview && exam != null
                        ? HealthExamReview.points(exam, HealthExamReview.reference(dateOfBirth, m.getDate()))
                        : null);
    }

    /** Kết quả chuyên khoa ở mọi phiếu theo dõi của trẻ, mới nhất trước. */
    List<ChildHealthRecordResponse.HealthExternalResult> externalResults(Snapshot s, Long childId) {
        List<HealthFollowUpEvent> events = s.externalEvents(childId);
        Map<Long, String> names = accountService.findFullNames(events.stream()
                .map(HealthFollowUpEvent::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return events.stream()
                .map(e -> new ChildHealthRecordResponse.HealthExternalResult(
                        e.getFollowUpId(),
                        e.getEventDate(),
                        e.getNote(),
                        HealthExamOutcome.text(e.getFacility()),
                        s.imageViews(e),
                        e.getNextVisitOn(),
                        e.getUserId() == null ? "" : names.getOrDefault(e.getUserId(), ""),
                        e.getCreatedAt()))
                .toList();
    }

    // BMI = cân nặng / (chiều cao m)^2, chỉ là số tính, không phân loại (HLT-10 phân loại hoãn, user 2026-10-31)
    private static BigDecimal bmi(HealthMeasurement m) {
        if (m.getHeightCm() == null
                || m.getWeightKg() == null
                || m.getHeightCm().signum() <= 0) {
            return null;
        }
        BigDecimal heightM = m.getHeightCm().movePointLeft(2);
        return m.getWeightKg().divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }

    /** Dữ liệu đọc một lần; mọi phép tính chỉ dùng dữ liệu này. */
    static final class Snapshot {
        private final LocalDate today;
        private final ZoneId zone;
        private final Map<Long, List<HealthMeasurement>> measurementsByChild;
        private final Map<Long, HealthMeasurement> measurementById;
        private final Map<Long, HealthCheckCampaign> campaigns;
        private final Map<Long, HealthExamForm> exams;
        private final Map<Long, List<HealthFollowUp>> followUpsByChild;
        private final Map<Long, HealthFollowUp> followUpById;
        private final Map<Long, List<HealthFollowUpEvent>> eventsByFollowUp;
        private final Map<Long, HealthFollowUpEvent> entryByMeasurement = new HashMap<>();
        private final Map<Long, List<HealthFollowUpImage>> imagesByEvent;
        private final Map<Long, HealthFollowUpState.State> states = new HashMap<>();

        private Snapshot(
                LocalDate today,
                ZoneId zone,
                List<HealthMeasurement> measurements,
                Map<Long, HealthCheckCampaign> campaigns,
                Map<Long, HealthExamForm> exams,
                List<HealthFollowUp> followUps,
                List<HealthFollowUpEvent> events,
                List<HealthFollowUpImage> images) {
            this.today = today;
            this.zone = zone;
            this.measurementsByChild = measurements.stream()
                    .sorted(Comparator.comparing(HealthMeasurement::getDate)
                            .thenComparing(HealthMeasurement::getId)
                            .reversed())
                    .collect(Collectors.groupingBy(HealthMeasurement::getChildId));
            this.measurementById =
                    measurements.stream().collect(Collectors.toMap(HealthMeasurement::getId, Function.identity()));
            this.campaigns = campaigns;
            this.exams = exams;
            this.followUpsByChild = followUps.stream()
                    .sorted(Comparator.comparing(HealthFollowUp::getOpenedAt).thenComparing(HealthFollowUp::getId))
                    .collect(Collectors.groupingBy(HealthFollowUp::getChildId));
            this.followUpById =
                    followUps.stream().collect(Collectors.toMap(HealthFollowUp::getId, Function.identity()));
            this.eventsByFollowUp = events.stream()
                    .sorted(Comparator.comparing(HealthFollowUpEvent::getId))
                    .collect(Collectors.groupingBy(HealthFollowUpEvent::getFollowUpId));
            events.stream()
                    .filter(HealthFollowUpEvent::isExamEntry)
                    .forEach(e -> entryByMeasurement.put(e.getMeasurementId(), e));
            this.imagesByEvent = images.stream().collect(Collectors.groupingBy(HealthFollowUpImage::getEventId));
        }

        LocalDate today() {
            return today;
        }

        ZoneId zone() {
            return zone;
        }

        HealthCheckCampaign campaign(Long campaignId) {
            return campaigns.get(campaignId);
        }

        HealthExamForm exam(HealthMeasurement m) {
            return exams.get(m.getId());
        }

        HealthExamOutcome.Outcome outcome(HealthMeasurement m) {
            return HealthExamOutcome.of(m, exam(m));
        }

        /** Nội dung hiển thị thay bản đã lưu (phiếu nháp điền lại hành chính, tiền sử; không lưu). */
        void showExam(Long measurementId, HealthExamForm exam) {
            exams.put(measurementId, exam);
        }

        HealthMeasurementResponse.HealthConclusionBrief conclusion(HealthMeasurement m) {
            return HealthExamOutcome.brief(m, exam(m));
        }

        /** Mọi lần đo của trẻ (cả nháp), mới nhất trước. */
        List<HealthMeasurement> measurements(Long childId) {
            return measurementsByChild.getOrDefault(childId, List.of());
        }

        HealthMeasurement measurement(Long measurementId) {
            return measurementById.get(measurementId);
        }

        /** Kết quả đã có (bỏ phiếu khám nháp), mới nhất trước. */
        List<HealthMeasurement> results(Long childId) {
            return measurements(childId).stream().filter(m -> !m.isDraft()).toList();
        }

        List<HealthFollowUp> followUps(Long childId) {
            return followUpsByChild.getOrDefault(childId, List.of());
        }

        HealthFollowUp followUp(Long followUpId) {
            return followUpById.get(followUpId);
        }

        List<HealthFollowUpEvent> events(HealthFollowUp f) {
            return eventsByFollowUp.getOrDefault(f.getId(), List.of());
        }

        List<HealthFollowUpImage> images(HealthFollowUpEvent e) {
            return imagesByEvent.getOrDefault(e.getId(), List.of());
        }

        List<HealthFollowUpResponse.HealthFollowUpImageView> imageViews(HealthFollowUpEvent e) {
            return images(e).stream()
                    .map(i -> new HealthFollowUpResponse.HealthFollowUpImageView(
                            i.getId(), imagePath(e.getFollowUpId(), i.getId())))
                    .toList();
        }

        HealthFollowUp openFollowUp(Long childId) {
            return followUps(childId).stream()
                    .filter(HealthFollowUp::isOpen)
                    .findFirst()
                    .orElse(null);
        }

        /** Phiếu theo dõi chứa lần khám; null khi lần khám không vào phiếu nào. */
        HealthFollowUp followUpOf(Long measurementId) {
            HealthFollowUpEvent entry = entryByMeasurement.get(measurementId);
            return entry == null ? null : followUpById.get(entry.getFollowUpId());
        }

        HealthFollowUpEvent entryOf(Long measurementId) {
            return entryByMeasurement.get(measurementId);
        }

        HealthFollowUpState.State state(HealthFollowUp f) {
            return states.computeIfAbsent(f.getId(), id -> HealthFollowUpState.of(f, events(f), today, zone));
        }

        /** Lần khám đã kết luận trong phiếu, theo ngày tăng dần. */
        List<HealthMeasurement> followUpResults(HealthFollowUp f) {
            return events(f).stream()
                    .filter(HealthFollowUpEvent::isExamEntry)
                    .map(e -> measurementById.get(e.getMeasurementId()))
                    .filter(m -> m != null && !m.isDraft())
                    .sorted(Comparator.comparing(HealthMeasurement::getDate).thenComparing(HealthMeasurement::getId))
                    .toList();
        }

        boolean tbRisk(HealthFollowUp f) {
            return followUpResults(f).stream().map(this::conclusion).anyMatch(c -> c != null && c.tbRisk());
        }

        /** Đề nghị gần nhất: lần khám đã kết luận mới nhất trong phiếu, bỏ lần khám chỉ ghi nhận. */
        HealthFollowUpResponse.HealthReferral referral(HealthFollowUp f) {
            return followUpResults(f).stream()
                    .filter(m -> conclusion(m) != null
                            && !Boolean.FALSE.equals(entryOf(m.getId()).getNewFinding()))
                    .reduce((a, b) -> b)
                    .map(m -> {
                        HealthMeasurementResponse.HealthConclusionBrief c = conclusion(m);
                        return new HealthFollowUpResponse.HealthReferral(
                                m.getDate(),
                                m.getId(),
                                c.doctorName(),
                                c.issueText(),
                                c.tbRisk(),
                                c.action(),
                                c.adviceText(),
                                c.specialty(),
                                c.issueKind(),
                                c.followUpFinding());
                    })
                    .orElse(null);
        }

        HealthFollowUpResponse.HealthExternalResultBrief externalBrief(HealthFollowUpEvent e, String byName) {
            return e == null
                    ? null
                    : new HealthFollowUpResponse.HealthExternalResultBrief(
                            e.getEventDate(),
                            e.getNote(),
                            HealthExamOutcome.text(e.getFacility()),
                            e.getNextVisitOn(),
                            byName);
        }

        HealthFollowUpResponse.HealthFollowUpBrief brief(HealthFollowUp f) {
            if (f == null) {
                return null;
            }
            HealthFollowUpState.State st = state(f);
            return new HealthFollowUpResponse.HealthFollowUpBrief(
                    f.getId(),
                    st.status(),
                    f.getOpenedAt(),
                    HealthFollowUpState.openedOn(f, events(f), zone),
                    st.round() == null ? null : st.round().dueOn(),
                    st.round() == null ? 0 : st.round().overdueDays(),
                    externalBrief(HealthFollowUpState.latestExternal(events(f)), null),
                    tbRisk(f),
                    referral(f));
        }

        /** Lần khám trước ngày {@code before} ở đợt khám (kỳ cân đo không tính), đã có kết quả; null khi chưa có. */
        HealthExamResponse.HealthPreviousExam previous(Long childId, Long excludeId, LocalDate before) {
            return results(childId).stream()
                    .filter(m -> !m.getId().equals(excludeId) && m.getDate().isBefore(before))
                    .filter(m -> {
                        HealthCheckCampaign c = campaign(m.getCampaignId());
                        return c == null || c.getKind() == HealthCheckKind.EXAM;
                    })
                    .findFirst()
                    .map(m -> {
                        HealthCheckCampaign c = campaign(m.getCampaignId());
                        return new HealthExamResponse.HealthPreviousExam(
                                m.getDate(),
                                c == null ? null : c.getName(),
                                outcome(m).level(),
                                conclusion(m));
                    })
                    .orElse(null);
        }

        /**
         * Mức hiện tại: lần khám gần nhất có kết luận; phiếu đang mở ⇒ theo dõi sát; phiếu đóng theo lời bác sĩ (bình
         * thường, vấn đề đã biết) từ ngày khám đó trở đi thắng; phiếu đóng khi chưa có kết quả chuyên khoa giữ mức.
         */
        ChildHealthRecordResponse.HealthLatest latestHealth(Long childId) {
            HealthMeasurement exam = results(childId).stream()
                    .filter(m -> outcome(m).level() != HealthFollowUpLevel.UNAVAILABLE)
                    .findFirst()
                    .orElse(null);
            String examCampaign = exam == null || campaign(exam.getCampaignId()) == null
                    ? null
                    : campaign(exam.getCampaignId()).getName();
            HealthFollowUp open = openFollowUp(childId);
            if (open != null) {
                return new ChildHealthRecordResponse.HealthLatest(
                        exam != null ? exam.getDate() : localDate(open.getOpenedAt()),
                        HealthFollowUpLevel.CLOSE_WATCH,
                        examCampaign,
                        HealthLatestSource.FOLLOW_UP_OPEN,
                        null,
                        null,
                        open.getId(),
                        null);
            }
            HealthFollowUp decided = latestClosed(
                    childId, Set.of(HealthFollowUpOutcome.DOCTOR_NORMAL, HealthFollowUpOutcome.KNOWN_CONDITION));
            if (decided != null
                    && (exam == null || !localDate(decided.getClosedAt()).isBefore(exam.getDate()))) {
                HealthFollowUpEvent ext = HealthFollowUpState.latestExternal(events(decided));
                boolean known = decided.getCloseOutcome() == HealthFollowUpOutcome.KNOWN_CONDITION;
                return new ChildHealthRecordResponse.HealthLatest(
                        localDate(decided.getClosedAt()),
                        known ? HealthFollowUpLevel.KNOWN_STABLE : HealthFollowUpLevel.NONE,
                        null,
                        HealthLatestSource.FOLLOW_UP_CLOSED,
                        decided.getCloseConditionText(),
                        ext == null ? null : ext.getEventDate(),
                        decided.getId(),
                        null);
            }
            if (exam == null) {
                return null;
            }
            HealthExamOutcome.Outcome outcome = outcome(exam);
            String issue = HealthExamOutcome.text(exam(exam).conclusion().issueText());
            String knownCondition =
                    outcome.level() == HealthFollowUpLevel.KNOWN_STABLE && !issue.isEmpty() ? issue : null;
            HealthFollowUp withoutResult = latestClosed(childId, Set.of(HealthFollowUpOutcome.NO_SPECIALIST_RESULT));
            boolean after = withoutResult != null
                    && !localDate(withoutResult.getClosedAt()).isBefore(exam.getDate());
            return new ChildHealthRecordResponse.HealthLatest(
                    exam.getDate(),
                    outcome.level(),
                    examCampaign,
                    HealthLatestSource.EXAM,
                    knownCondition,
                    null,
                    after ? withoutResult.getId() : null,
                    after ? localDate(withoutResult.getClosedAt()) : null);
        }

        private HealthFollowUp latestClosed(Long childId, Set<HealthFollowUpOutcome> outcomes) {
            return followUps(childId).stream()
                    .filter(f -> !f.isOpen() && outcomes.contains(f.getCloseOutcome()))
                    .max(Comparator.comparing(HealthFollowUp::getClosedAt).thenComparing(HealthFollowUp::getId))
                    .orElse(null);
        }

        /** Trao đổi phụ huynh về kết luận "theo dõi sát": tại buổi khám, hoặc lần GV ghi đầu tiên sau lần khám. */
        HealthMeasurementResponse.HealthParentContact parentContact(HealthMeasurement m) {
            if (m.getExamStatus() != HealthExamStatus.CONCLUDED
                    || outcome(m).level() != HealthFollowUpLevel.CLOSE_WATCH) {
                return null;
            }
            HealthExamForm exam = exam(m);
            if (HealthExamOutcome.companionOf(exam) != null) {
                return new HealthMeasurementResponse.HealthParentContact(
                        true,
                        true,
                        m.getDate(),
                        HealthExamOutcome.text(exam.conclusion().parentOpinion()));
            }
            HealthFollowUpEvent entry = entryOf(m.getId());
            HealthFollowUpEvent contact = entry == null
                    ? null
                    : events(followUpById.get(entry.getFollowUpId())).stream()
                            .filter(e ->
                                    e.getId() > entry.getId() && e.getAction() == HealthFollowUpAction.PARENT_CONTACTED)
                            .findFirst()
                            .orElse(null);
            return new HealthMeasurementResponse.HealthParentContact(
                    contact != null,
                    false,
                    contact == null
                            ? null
                            : contact.getEventDate() != null
                                    ? contact.getEventDate()
                                    : localDate(contact.getCreatedAt()),
                    "");
        }

        HealthFollowUpEffect effect(HealthMeasurement m) {
            HealthFollowUpEvent entry = entryOf(m.getId());
            return entry == null
                    ? null
                    : HealthFollowUpState.effect(entry, state(followUpById.get(entry.getFollowUpId())));
        }

        /**
         * Kết quả đóng chọn được: bình thường khi đã có kết quả chuyên khoa hoặc có lần khám có kết luận sau ngày mở
         * phiếu; vấn đề đã biết khi đã có kết quả chuyên khoa; chưa có kết quả chuyên khoa luôn chọn được.
         */
        List<HealthFollowUpOutcome> closeOptions(HealthFollowUp f) {
            boolean hasExternal =
                    events(f).stream().anyMatch(e -> e.getAction() == HealthFollowUpAction.EXTERNAL_RESULT);
            LocalDate openedOn = HealthFollowUpState.openedOn(f, events(f), zone);
            boolean examAfter = results(f.getChildId()).stream()
                    .anyMatch(m ->
                            m.getDate().isAfter(openedOn) && outcome(m).level() != HealthFollowUpLevel.UNAVAILABLE);
            List<HealthFollowUpOutcome> options = new ArrayList<>();
            if (hasExternal || examAfter) {
                options.add(HealthFollowUpOutcome.DOCTOR_NORMAL);
            }
            if (hasExternal) {
                options.add(HealthFollowUpOutcome.KNOWN_CONDITION);
            }
            options.add(HealthFollowUpOutcome.NO_SPECIALIST_RESULT);
            return options;
        }

        /** Kết quả chuyên khoa ở mọi phiếu của trẻ, mới nhất trước (theo ngày khám, rồi lúc ghi). */
        List<HealthFollowUpEvent> externalEvents(Long childId) {
            return followUps(childId).stream()
                    .flatMap(f -> events(f).stream())
                    .filter(e -> e.getAction() == HealthFollowUpAction.EXTERNAL_RESULT)
                    .sorted(Comparator.comparing(HealthFollowUpEvent::getEventDate)
                            .thenComparing(HealthFollowUpEvent::getId)
                            .reversed())
                    .toList();
        }

        /** Người dùng xuất hiện trên dòng thời gian các phiếu (để đọc tên một lần). */
        Set<Long> eventUserIds(Collection<HealthFollowUp> followUps) {
            Set<Long> ids = new HashSet<>();
            followUps.forEach(f -> events(f).stream()
                    .map(HealthFollowUpEvent::getUserId)
                    .filter(Objects::nonNull)
                    .forEach(ids::add));
            return ids;
        }

        LocalDate localDate(java.time.Instant at) {
            return HealthFollowUpState.localDate(at, zone);
        }
    }

    static String imagePath(Long followUpId, Long imageId) {
        return "/health-follow-ups/" + followUpId + "/images/" + imageId;
    }
}
