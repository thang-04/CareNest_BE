package com.carenest.service.health.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.HealthCheckClassResponse;
import com.carenest.dto.health.HealthCheckCloseResponse;
import com.carenest.dto.health.HealthCheckDetailResponse;
import com.carenest.dto.health.HealthCheckReopenRequest;
import com.carenest.dto.health.HealthCheckRequest;
import com.carenest.dto.health.HealthCheckResponse;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.health.HealthScheduleWarningResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.health.HealthCheckAbsence;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthCheckCampaignHistory;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckAbsenceRepository;
import com.carenest.repository.health.HealthCheckCampaignHistoryRepository;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.ChildService;
import com.carenest.service.health.HealthCheckService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.HealthBmiStatus;
import com.carenest.utils.enums.HealthCheckAction;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckResult;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthFollowUpLevel;
import com.carenest.utils.enums.HealthScheduleWarning;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HealthCheckServiceImpl implements HealthCheckService {
    private static final String LOG_PREFIX = "[HealthCheckServiceImpl]";
    private static final Collator VI = Collator.getInstance(Locale.forLanguageTag("vi"));

    private final HealthCheckRules rules;
    private final HealthRecords records;
    private final HealthCheckCampaignRepository campaignRepository;
    private final HealthCheckCampaignHistoryRepository historyRepository;
    private final HealthCheckAbsenceRepository absenceRepository;
    private final HealthMeasurementRepository measurementRepository;
    private final SchoolStructureService schoolStructureService;
    private final ChildService childService;
    private final AccountService accountService;

    @Override
    @Transactional(readOnly = true)
    public List<HealthCheckResponse> listHealthChecks(
            CurrentUser currentUser, Long campusId, HealthCheckKind kind, HealthCheckStatus status, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|listHealthChecks|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> visible = rules.visibleCampusIds(currentUser);
            List<HealthCheckCampaign> campaigns = (visible == null
                            ? campaignRepository.findAll(Sort.by(Sort.Direction.DESC, "startDate", "id"))
                            : campaignRepository.findByCampusIdInOrderByStartDateDescIdDesc(visible))
                    .stream()
                            .filter(c -> campusId == null || c.getCampusId().equals(campusId))
                            .filter(c -> kind == null || c.getKind() == kind)
                            .filter(c ->
                                    schoolYearId == null || schoolYearId.equals(rules.schoolYearIdOf(c.getStartDate())))
                            .filter(c -> status == null || c.statusOn(rules.today()) == status)
                            .toList();
            List<HealthCheckResponse> result = toViews(currentUser, campaigns).stream()
                    .sorted(Comparator.comparingInt((HealthCheckResponse c) -> statusRank(c.status()))
                            .thenComparing(HealthCheckResponse::startDate, Comparator.reverseOrder()))
                    .toList();
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HealthScheduleWarningResponse getScheduleWarnings(
            CurrentUser currentUser, Long campusId, HealthCheckKind kind, LocalDate startDate, Long excludeId) {
        String logPrefix = LOG_PREFIX + "|getScheduleWarnings|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            if (campusId == null || startDate == null) {
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "điểm trường, ngày bắt đầu");
            }
            if (!rules.canManage(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.HEALTH_CHECK_ACCESS_DENIED);
            }
            boolean exam = kind == null || kind == HealthCheckKind.EXAM;
            Long yearId = exam ? rules.schoolYearIdOf(startDate) : null;
            List<HealthScheduleWarningResponse.HealthScheduleWarningItem> warnings = new ArrayList<>();
            for (HealthCheckCampaign other : campaignRepository.findByCampusIdAndCancelledAtIsNull(campusId)) {
                if (other.getId().equals(excludeId)) {
                    continue;
                }
                // Cách dưới 2 tháng 15 ngày, xét cả đợt/kỳ ở trước lẫn ở sau (M1)
                if (other.getStartDate().isBefore(HealthCheckRules.gapEndOf(startDate))
                        && startDate.isBefore(HealthCheckRules.gapEndOf(other.getStartDate()))) {
                    warnings.add(warning(HealthScheduleWarning.TOO_CLOSE, other));
                }
                // Đợt khám mặc định 1 lần/năm học, không chặn (M2)
                if (yearId != null
                        && other.getKind() == HealthCheckKind.EXAM
                        && yearId.equals(rules.schoolYearIdOf(other.getStartDate()))) {
                    warnings.add(warning(HealthScheduleWarning.EXAM_IN_SCHOOL_YEAR, other));
                }
            }
            warnings.sort(Comparator.comparing(HealthScheduleWarningResponse.HealthScheduleWarningItem::startDate));
            log.info("{}|END", logPrefix);
            return new HealthScheduleWarningResponse(warnings);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HealthCheckDetailResponse getHealthCheck(CurrentUser currentUser, Long campaignId) {
        String logPrefix = LOG_PREFIX + "|getHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        try {
            HealthCheckCampaign campaign = rules.findVisible(currentUser, campaignId);
            Results results = results(List.of(campaign.getId()));
            boolean open = campaign.statusOn(rules.today()) == HealthCheckStatus.IN_PROGRESS;
            List<ClassroomInfoResponse> visibleClasses = rules.visibleClassesOf(currentUser, campaign);
            Map<Long, List<HealthCheckRules.Row>> rowsByClass = new LinkedHashMap<>();
            visibleClasses.forEach(cls -> rowsByClass.put(
                    cls.id(), rules.rosterOf(campaign, cls, results.measurements(), results.absences())));
            List<HealthCheckDetailResponse.HealthCheckClassProgress> classes = visibleClasses.stream()
                    .map(cls -> {
                        Progress p = Progress.of(rowsByClass.get(cls.id()), results);
                        return new HealthCheckDetailResponse.HealthCheckClassProgress(
                                cls.id(),
                                cls.name(),
                                cls.ageGroupId(),
                                p.total(),
                                p.recorded(),
                                p.notRecorded(),
                                p.inProgress(),
                                p.notConcluded(),
                                p.absent(),
                                p.watch(),
                                p.closeWatch(),
                                p.bmiWarning(),
                                open && rules.canRecord(currentUser, cls));
                    })
                    .toList();
            // Trẻ cần chú ý: đợt khám theo mức theo dõi (theo dõi sát trước), kỳ cân đo theo cảnh báo BMI (mức nặng
            // trước); rồi theo tên
            List<HealthCheckRules.Row> watchRows = rowsByClass.values().stream()
                    .flatMap(List::stream)
                    .filter(r -> isWatch(levelOf(r, results.levels())) || isBmiWarning(bmiOf(r, results.bmi())))
                    .toList();
            List<HealthCheckClassResponse.HealthCheckChild> watchList =
                    childRows(campaign, watchRows, visibleClasses).stream()
                            .sorted(Comparator.comparing(HealthCheckServiceImpl::attentionRank)
                                    .thenComparing(HealthCheckClassResponse.HealthCheckChild::fullName, VI))
                            .toList();
            HealthCheckDetailResponse detail =
                    new HealthCheckDetailResponse(toView(currentUser, campaign, results), classes, watchList);
            log.info("{}|END", logPrefix);
            return detail;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HealthCheckClassResponse getHealthCheckClass(CurrentUser currentUser, Long campaignId, Long classId) {
        String logPrefix = LOG_PREFIX + "|getHealthCheckClass|userId=" + currentUser.userId() + "|campaignId="
                + campaignId + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            HealthCheckCampaign campaign = rules.findVisible(currentUser, campaignId);
            ClassroomInfoResponse cls = rules.visibleClassesOf(currentUser, campaign).stream()
                    .filter(c -> c.id().equals(classId))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
            Results results = results(List.of(campaign.getId()));
            List<HealthCheckRules.Row> rows = rules.rosterOf(campaign, cls, results.measurements(), results.absences());
            List<HealthCheckClassResponse.HealthCheckChild> children = childRows(campaign, rows, List.of(cls)).stream()
                    .sorted(Comparator.comparing(HealthCheckClassResponse.HealthCheckChild::fullName, VI))
                    .toList();
            boolean canRecord = campaign.statusOn(rules.today()) == HealthCheckStatus.IN_PROGRESS
                    && rules.canRecord(currentUser, cls);
            HealthCheckClassResponse response = new HealthCheckClassResponse(
                    toView(currentUser, campaign, results),
                    new HealthCheckClassResponse.HealthCheckClassRef(cls.id(), cls.name()),
                    children,
                    canRecord);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthCheckResponse createHealthCheck(CurrentUser currentUser, HealthCheckRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createHealthCheck|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        try {
            if (!rules.canManage(currentUser, request.campusId())) {
                throw GlobalException.of(ApiCode.HEALTH_CHECK_ACCESS_DENIED);
            }
            if (schoolStructureService
                    .findCampusNames(Set.of(request.campusId()))
                    .isEmpty()) {
                throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
            }
            checkCreationDates(request);
            checkCampusFree(request.campusId(), null);
            HealthCheckCampaign campaign = campaignRepository.save(HealthCheckCampaign.builder()
                    .kind(request.kind() == null ? HealthCheckKind.EXAM : request.kind())
                    .campusId(request.campusId())
                    .name(request.name().trim())
                    .startDate(request.startDate())
                    .endDate(request.endDate())
                    .examiner(trimmed(request.examiner()))
                    .note(trimmed(request.note()))
                    .createdBy(currentUser.userId())
                    .createdAt(rules.now())
                    .build());
            record(campaign, HealthCheckAction.CREATED, currentUser, null);
            HealthCheckResponse created = toView(currentUser, campaign, results(List.of(campaign.getId())));
            log.info("{}|END", logPrefix);
            return created;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthCheckResponse updateHealthCheck(CurrentUser currentUser, Long campaignId, HealthCheckRequest request) {
        String logPrefix =
                LOG_PREFIX + "|updateHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        try {
            HealthCheckCampaign campaign = rules.findManageable(currentUser, campaignId);
            if (campaign.isEnded()) {
                throw GlobalException.of(ApiCode.HEALTH_CHECK_ENDED);
            }
            checkDates(request);
            Results results = results(List.of(campaign.getId()));
            // Đổi ngày không được để kết quả đã nhập nằm ngoài khoảng mới
            results.measurements().stream()
                    .map(HealthMeasurement::getDate)
                    .filter(d -> d.isBefore(request.startDate()) || d.isAfter(request.endDate()))
                    .min(Comparator.naturalOrder())
                    .ifPresent(d -> {
                        throw GlobalException.of(ApiCode.HEALTH_CHECK_RESULT_OUTSIDE_DATES, d);
                    });
            campaign.update(
                    request.name().trim(),
                    request.startDate(),
                    request.endDate(),
                    trimmed(request.examiner()),
                    trimmed(request.note()),
                    rules.now());
            record(campaign, HealthCheckAction.UPDATED, currentUser, null);
            HealthCheckResponse updated = toView(currentUser, campaign, results);
            log.info("{}|END", logPrefix);
            return updated;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthCheckResponse cancelHealthCheck(CurrentUser currentUser, Long campaignId) {
        String logPrefix =
                LOG_PREFIX + "|cancelHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        try {
            HealthCheckCampaign campaign = rules.findManageable(currentUser, campaignId);
            if (campaign.isEnded()) {
                throw GlobalException.of(ApiCode.HEALTH_CHECK_ENDED);
            }
            if (measurementRepository.existsByCampaignId(campaign.getId())) {
                throw GlobalException.of(ApiCode.HEALTH_CHECK_HAS_RESULTS);
            }
            campaign.cancel(rules.now());
            record(campaign, HealthCheckAction.CANCELLED, currentUser, null);
            HealthCheckResponse cancelled = toView(currentUser, campaign, results(List.of(campaign.getId())));
            log.info("{}|END", logPrefix);
            return cancelled;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthCheckCloseResponse closeHealthCheck(CurrentUser currentUser, Long campaignId) {
        String logPrefix =
                LOG_PREFIX + "|closeHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        try {
            HealthCheckCampaign campaign = rules.findManageable(currentUser, campaignId);
            if (campaign.statusOn(rules.today()) != HealthCheckStatus.IN_PROGRESS) {
                throw GlobalException.of(ApiCode.HEALTH_CHECK_NOT_IN_PROGRESS);
            }
            Closure closure = closeCampaign(campaign, currentUser.userId(), null);
            HealthCheckCloseResponse closed = new HealthCheckCloseResponse(
                    closure.absent(), closure.notConcluded(), toView(currentUser, campaign, closure.results()));
            log.info("{}|END", logPrefix);
            return closed;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Scheduled(cron = "0 0 0 * * *", zone = "${carenest.time-zone}")
    @EventListener(ApplicationReadyEvent.class)
    public void closeExpiredHealthChecks() {
        String logPrefix = LOG_PREFIX + "|closeExpiredHealthChecks";
        log.info("{}|START", logPrefix);
        try {
            // HLT-17: đợt khám và kỳ cân đo (từ 2026-11-04) cùng tự đóng khi qua ngày kết thúc theo giờ trường
            List<HealthCheckCampaign> expired =
                    campaignRepository.findByClosedAtIsNullAndCancelledAtIsNullAndEndDateBefore(rules.today());
            expired.forEach(c -> closeCampaign(c, null, autoCloseReason(c.getKind())));
            log.info("{}|count={}|END", logPrefix, expired.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private record Closure(int absent, int notConcluded, Results results) {}

    /** Lý do ghi vào lịch sử khi hệ thống tự đóng (hiện cho người dùng). */
    private static String autoCloseReason(HealthCheckKind kind) {
        return kind == HealthCheckKind.MEASUREMENT
                ? "Hệ thống tự đóng khi qua ngày kết thúc cân đo"
                : "Hệ thống tự đóng khi qua ngày kết thúc khám";
    }

    private Closure closeCampaign(HealthCheckCampaign campaign, Long userId, String reason) {
        Results before = results(List.of(campaign.getId()));
        List<HealthCheckRules.Row> rows = rules.classesOf(campaign).stream()
                .flatMap(cls -> rules.rosterOf(campaign, cls, before.measurements(), List.of()).stream())
                .toList();
        // Đóng tay và tự động cùng chốt trẻ vắng; phiếu nháp giữ "Chưa kết luận".
        List<HealthCheckAbsence> absences = rows.stream()
                .filter(r -> r.result() == HealthCheckResult.NOT_RECORDED)
                .map(r -> HealthCheckAbsence.of(campaign.getId(), r.childId(), r.classId()))
                .toList();
        int notConcluded = (int) rows.stream()
                .filter(r -> r.result() == HealthCheckResult.IN_PROGRESS)
                .count();
        absenceRepository.saveAll(absences);
        campaign.close(userId, rules.now());
        historyRepository.save(
                HealthCheckCampaignHistory.of(campaign.getId(), HealthCheckAction.CLOSED, userId, reason, rules.now()));
        return new Closure(
                absences.size(),
                notConcluded,
                new Results(before.measurements(), absences, before.levels(), before.bmi()));
    }

    @Override
    public HealthCheckResponse reopenHealthCheck(
            CurrentUser currentUser, Long campaignId, HealthCheckReopenRequest request) {
        String logPrefix =
                LOG_PREFIX + "|reopenHealthCheck|userId=" + currentUser.userId() + "|campaignId=" + campaignId;
        log.info("{}|START", logPrefix);
        try {
            HealthCheckCampaign campaign = rules.findManageable(currentUser, campaignId);
            if (campaign.statusOn(rules.today()) != HealthCheckStatus.CLOSED) {
                throw GlobalException.of(ApiCode.HEALTH_CHECK_NOT_CLOSED);
            }
            String reason = trimmed(request.reason());
            if (reason.isEmpty()) {
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "lý do mở lại");
            }
            // HLT-17: đợt/kỳ đã hết hạn phải có ngày kết thúc mới từ hôm nay, qua ngày đó lại tự đóng (cả hai loại)
            LocalDate endDate = request.endDate() != null ? request.endDate() : campaign.getEndDate();
            if (endDate.isBefore(rules.today()) || endDate.isBefore(campaign.getStartDate())) {
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA,
                        List.of(new FieldErrorResponse("endDate", "Chọn ngày kết thúc mới từ hôm nay")));
            }
            campaign.update(
                    campaign.getName(),
                    campaign.getStartDate(),
                    endDate,
                    campaign.getExaminer(),
                    campaign.getNote(),
                    rules.now());
            checkCampusFree(campaign.getCampusId(), campaign.getId());
            absenceRepository.deleteByCampaignId(campaign.getId());
            campaign.reopen(rules.now());
            record(campaign, HealthCheckAction.REOPENED, currentUser, reason);
            HealthCheckResponse reopened = toView(currentUser, campaign, results(List.of(campaign.getId())));
            log.info("{}|END", logPrefix);
            return reopened;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /**
     * Kết quả, trẻ vắng của các đợt/kỳ đọc một lần; {@code levels}: mức theo dõi của từng lần đo; {@code bmi}: mức BMI
     * của từng số đo kỳ cân đo xếp được mức (HLT-18).
     */
    private record Results(
            List<HealthMeasurement> measurements,
            List<HealthCheckAbsence> absences,
            Map<Long, HealthFollowUpLevel> levels,
            Map<Long, HealthBmiStatus> bmi) {}

    private record Progress(
            int total,
            int recorded,
            int notRecorded,
            int inProgress,
            int notConcluded,
            int absent,
            int watch,
            int closeWatch,
            Map<HealthBmiStatus, Integer> bmiCounts) {
        static Progress of(List<HealthCheckRules.Row> rows, Results results) {
            Map<HealthBmiStatus, Integer> bmiCounts = new EnumMap<>(HealthBmiStatus.class);
            rows.forEach(r -> {
                HealthBmiStatus status = bmiOf(r, results.bmi());
                if (status != null) {
                    bmiCounts.merge(status, 1, Integer::sum);
                }
            });
            return new Progress(
                    rows.size(),
                    count(rows, HealthCheckResult.RECORDED),
                    count(rows, HealthCheckResult.NOT_RECORDED),
                    count(rows, HealthCheckResult.IN_PROGRESS),
                    count(rows, HealthCheckResult.NOT_CONCLUDED),
                    count(rows, HealthCheckResult.ABSENT),
                    (int) rows.stream()
                            .filter(r -> levelOf(r, results.levels()) == HealthFollowUpLevel.WATCH)
                            .count(),
                    (int) rows.stream()
                            .filter(r -> levelOf(r, results.levels()) == HealthFollowUpLevel.CLOSE_WATCH)
                            .count(),
                    bmiCounts);
        }

        private static int count(List<HealthCheckRules.Row> rows, HealthCheckResult result) {
            return (int) rows.stream().filter(r -> r.result() == result).count();
        }

        int bmi(HealthBmiStatus status) {
            return bmiCounts.getOrDefault(status, 0);
        }

        int bmiWarning() {
            return bmiCounts.entrySet().stream()
                    .filter(e -> isBmiWarning(e.getKey()))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        }
    }

    private Results results(List<Long> campaignIds) {
        List<HealthMeasurement> measurements = measurementRepository.findByCampaignIdIn(campaignIds);
        Map<Long, HealthFollowUpLevel> levels = new HashMap<>();
        measurements.forEach(m -> levels.put(m.getId(), records.outcomeOf(m).level()));
        // Mức BMI cần ngày sinh, giới tính của trẻ có số đo kỳ cân đo
        Set<Long> measuredChildIds = measurements.stream()
                .filter(m -> !m.isExam())
                .map(HealthMeasurement::getChildId)
                .collect(Collectors.toSet());
        Map<Long, ChildBriefResponse> children =
                measuredChildIds.isEmpty() ? Map.of() : childService.findChildBriefs(measuredChildIds);
        Map<Long, HealthBmiStatus> bmi = new HashMap<>();
        measurements.forEach(m -> {
            ChildBriefResponse child = children.get(m.getChildId());
            WhoBmiForAge.Result r = child == null ? null : HealthRecords.bmiOf(m, child.dateOfBirth(), child.gender());
            if (r != null) {
                bmi.put(m.getId(), r.status());
            }
        });
        return new Results(measurements, absenceRepository.findByCampaignIdIn(campaignIds), levels, bmi);
    }

    private static HealthFollowUpLevel levelOf(HealthCheckRules.Row row, Map<Long, HealthFollowUpLevel> levels) {
        return row.measurement() == null
                ? HealthFollowUpLevel.UNAVAILABLE
                : levels.getOrDefault(row.measurement().getId(), HealthFollowUpLevel.UNAVAILABLE);
    }

    private static HealthBmiStatus bmiOf(HealthCheckRules.Row row, Map<Long, HealthBmiStatus> bmi) {
        return row.measurement() == null ? null : bmi.get(row.measurement().getId());
    }

    private static boolean isWatch(HealthFollowUpLevel level) {
        return level == HealthFollowUpLevel.WATCH || level == HealthFollowUpLevel.CLOSE_WATCH;
    }

    private static boolean isBmiWarning(HealthBmiStatus status) {
        return status != null && status != HealthBmiStatus.NORMAL;
    }

    // Thứ tự trẻ cần chú ý: theo dõi sát, BMI mức nặng trước; rồi cần theo dõi, gầy còm, thừa cân; rồi nguy cơ thừa cân
    private static int attentionRank(HealthCheckClassResponse.HealthCheckChild c) {
        if (c.followUpLevel() == HealthFollowUpLevel.CLOSE_WATCH
                || c.bmiStatus() == HealthBmiStatus.SEVERE_THINNESS
                || c.bmiStatus() == HealthBmiStatus.OBESE) {
            return 0;
        }
        return c.bmiStatus() == HealthBmiStatus.OVERWEIGHT_RISK ? 2 : 1;
    }

    private List<HealthCheckResponse> toViews(CurrentUser user, List<HealthCheckCampaign> campaigns) {
        Results all = results(campaigns.stream().map(HealthCheckCampaign::getId).toList());
        Map<Long, List<HealthMeasurement>> measurements =
                all.measurements().stream().collect(Collectors.groupingBy(HealthMeasurement::getCampaignId));
        Map<Long, List<HealthCheckAbsence>> absences =
                all.absences().stream().collect(Collectors.groupingBy(HealthCheckAbsence::getCampaignId));
        return campaigns.stream()
                .map(c -> toView(
                        user,
                        c,
                        new Results(
                                measurements.getOrDefault(c.getId(), List.of()),
                                absences.getOrDefault(c.getId(), List.of()),
                                all.levels(),
                                all.bmi())))
                .toList();
    }

    private HealthCheckResponse toView(CurrentUser user, HealthCheckCampaign c, Results results) {
        List<HealthCheckRules.Row> rows = rules.visibleClassesOf(user, c).stream()
                .flatMap(cls -> rules.rosterOf(c, cls, results.measurements(), results.absences()).stream())
                .toList();
        Progress p = Progress.of(rows, results);
        List<HealthCheckCampaignHistory> history =
                historyRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(c.getId());
        Map<Long, String> names = accountService.findFullNames(Stream.concat(
                        Stream.of(c.getCreatedBy(), c.getClosedBy()),
                        history.stream().map(HealthCheckCampaignHistory::getUserId))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return new HealthCheckResponse(
                c.getId(),
                c.getKind(),
                c.getName(),
                c.getCampusId(),
                schoolStructureService.findCampusNames(Set.of(c.getCampusId())).get(c.getCampusId()),
                c.getStartDate(),
                c.getEndDate(),
                c.getExaminer(),
                c.getNote(),
                c.statusOn(rules.today()),
                c.getCreatedBy(),
                names.get(c.getCreatedBy()),
                c.getCreatedAt(),
                c.getClosedBy(),
                c.getClosedBy() != null ? names.get(c.getClosedBy()) : c.getClosedAt() != null ? "Hệ thống" : null,
                c.getClosedAt(),
                c.getCancelledAt(),
                !results.measurements().isEmpty(),
                new HealthCheckResponse.HealthCheckProgress(
                        p.total(),
                        p.recorded(),
                        p.notRecorded(),
                        p.inProgress(),
                        p.notConcluded(),
                        p.absent(),
                        p.watch(),
                        p.closeWatch(),
                        new HealthCheckResponse.HealthBmiProgress(
                                p.bmi(HealthBmiStatus.SEVERE_THINNESS),
                                p.bmi(HealthBmiStatus.THINNESS),
                                p.bmi(HealthBmiStatus.OVERWEIGHT_RISK),
                                p.bmi(HealthBmiStatus.OVERWEIGHT),
                                p.bmi(HealthBmiStatus.OBESE))),
                history.stream()
                        .map(h -> new HealthCheckResponse.HealthCheckHistory(
                                h.getAction(),
                                h.getUserId(),
                                h.getUserId() == null ? "Hệ thống" : names.get(h.getUserId()),
                                h.getReason(),
                                h.getCreatedAt()))
                        .toList());
    }

    /** Dòng trẻ của danh sách lớp: kết quả, mức theo dõi, kết luận rút gọn, lần khám trước, mức BMI của kỳ cân đo. */
    private List<HealthCheckClassResponse.HealthCheckChild> childRows(
            HealthCheckCampaign campaign, List<HealthCheckRules.Row> rows, List<ClassroomInfoResponse> classes) {
        List<Long> childIds =
                rows.stream().map(HealthCheckRules.Row::childId).distinct().toList();
        Map<Long, ChildBriefResponse> briefs = childService.findChildBriefs(childIds);
        Map<Long, ClassroomInfoResponse> classById =
                classes.stream().collect(Collectors.toMap(ClassroomInfoResponse::id, Function.identity()));
        HealthRecords.Snapshot s = records.load(childIds);
        return rows.stream()
                .map(r -> toChild(r, classById.get(r.classId()), briefs.get(r.childId()), s, campaign))
                .toList();
    }

    private static HealthCheckClassResponse.HealthCheckChild toChild(
            HealthCheckRules.Row row,
            ClassroomInfoResponse cls,
            ChildBriefResponse brief,
            HealthRecords.Snapshot s,
            HealthCheckCampaign campaign) {
        HealthMeasurement m = row.measurement();
        // Phiếu khám nháp chưa là kết quả: chưa có mức, kết luận
        boolean hasResult = m != null && !m.isDraft();
        HealthExamOutcome.Outcome outcome = hasResult ? s.outcome(m) : HealthExamOutcome.UNAVAILABLE;
        WhoBmiForAge.Result bmi =
                hasResult && brief != null ? HealthRecords.bmiOf(m, brief.dateOfBirth(), brief.gender()) : null;
        return new HealthCheckClassResponse.HealthCheckChild(
                row.childId(),
                brief == null ? null : brief.code(),
                brief == null ? "" : brief.fullName(),
                brief == null ? null : brief.dateOfBirth(),
                cls.id(),
                cls.name(),
                row.result(),
                m == null ? null : m.getId(),
                m == null ? null : m.getDate(),
                m == null ? null : m.getHeightCm(),
                m == null ? null : m.getWeightKg(),
                hasResult ? HealthRecords.bmi(m) : null,
                bmi == null ? null : bmi.status(),
                m != null && m.getPublishedAt() != null,
                m == null ? null : m.getExamStatus(),
                m == null
                        ? null
                        : m.isExam()
                                ? HealthMeasurementResponse.SOURCE_EXAM
                                : HealthMeasurementResponse.SOURCE_MEASUREMENT_ONLY,
                hasResult ? outcome.level() : null,
                hasResult ? outcome.category() : null,
                m == null ? null : s.conclusion(m),
                s.previous(
                        row.childId(),
                        m == null ? null : m.getId(),
                        m != null ? m.getDate() : campaign.getStartDate()));
    }

    private static HealthScheduleWarningResponse.HealthScheduleWarningItem warning(
            HealthScheduleWarning code, HealthCheckCampaign c) {
        return new HealthScheduleWarningResponse.HealthScheduleWarningItem(
                code, c.getId(), c.getName(), c.getKind(), c.getStartDate());
    }

    private void checkDates(HealthCheckRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw GlobalException.of(ApiCode.HEALTH_CHECK_DATES_INVALID);
        }
    }

    private void checkCreationDates(HealthCheckRequest request) {
        checkDates(request);
        LocalDate today = rules.today();
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (request.startDate().isBefore(today)) {
            errors.add(new FieldErrorResponse("startDate", "Ngày bắt đầu không được trước hôm nay"));
        }
        if (request.endDate().isBefore(today)) {
            errors.add(new FieldErrorResponse("endDate", "Ngày kết thúc không được trước hôm nay"));
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
    }

    // Mỗi điểm trường một đợt/kỳ chưa đóng, tính chung hai loại (M3)
    private void checkCampusFree(Long campusId, Long exceptId) {
        campaignRepository
                .findFirstByCampusIdAndClosedAtIsNullAndCancelledAtIsNull(campusId)
                .filter(open -> !open.getId().equals(exceptId))
                .ifPresent(open -> {
                    throw GlobalException.of(
                            ApiCode.HEALTH_CHECK_CAMPUS_BUSY,
                            HealthCheckRules.kindLabel(open.getKind()),
                            open.getName());
                });
    }

    private void record(HealthCheckCampaign campaign, HealthCheckAction action, CurrentUser user, String reason) {
        historyRepository.save(
                HealthCheckCampaignHistory.of(campaign.getId(), action, user.userId(), reason, rules.now()));
    }

    private static int statusRank(HealthCheckStatus status) {
        return switch (status) {
            case IN_PROGRESS -> 0;
            case UPCOMING -> 1;
            default -> 2;
        };
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
