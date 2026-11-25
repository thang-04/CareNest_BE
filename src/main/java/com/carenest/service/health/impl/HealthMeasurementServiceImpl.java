package com.carenest.service.health.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.health.HealthTrendContent;
import com.carenest.dto.health.ParentHealthExamResponse;
import com.carenest.dto.health.PublishedHealthResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthInterpretation;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.entity.health.HealthMeasurementEdit;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.repository.health.HealthInterpretationRepository;
import com.carenest.repository.health.HealthMeasurementEditRepository;
import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.InterpretationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HealthMeasurementServiceImpl implements HealthMeasurementService {
    private static final String LOG_PREFIX = "[HealthMeasurementServiceImpl]";
    private static final String RECORDED = "RECORDED";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HealthCheckRules rules;
    private final HealthRecords records;
    private final HealthMeasurementRepository measurementRepository;
    private final HealthMeasurementEditRepository editRepository;
    private final HealthCheckCampaignRepository campaignRepository;
    private final ChildAccessPolicy childAccessPolicy;
    private final ChildService childService;
    private final EnrollmentService enrollmentService;
    private final ClassroomService classroomService;
    private final HealthNotifier healthNotifier;
    private final HealthInterpretationRepository interpretationRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ChildHealthRecordResponse getHealthRecord(CurrentUser currentUser, Long childId) {
        String logPrefix = LOG_PREFIX + "|getHealthRecord|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            if (!childAccessPolicy.canReadChild(currentUser, childId)) {
                throw GlobalException.of(ApiCode.HEALTH_RECORD_ACCESS_DENIED);
            }
            ChildBriefResponse child = brief(childId);
            Optional<ClassroomInfoResponse> cls = classOn(childId, rules.today());
            // Đợt/kỳ đang diễn ra ở điểm trường của lớp trẻ đang học
            HealthCheckCampaign open = cls.flatMap(c ->
                            campaignRepository.findFirstByCampusIdAndClosedAtIsNullAndCancelledAtIsNull(c.campusId()))
                    .filter(c -> c.statusOn(rules.today()) == HealthCheckStatus.IN_PROGRESS)
                    .orElse(null);
            HealthRecords.Snapshot s = records.load(List.of(childId));
            ChildHealthRecordResponse.HealthOpenCampaign openView = null;
            if (open != null) {
                // G94-180/HLT-09: đã đo ở kỳ liên quan (điểm trường cũ) tính là kết quả của kỳ đang mở
                HealthMeasurement result = s.measurements(childId).stream()
                        .filter(m -> m.getCampaignId().equals(open.getId()))
                        .findFirst()
                        .or(() -> rules.linkedResultOf(open, rules.linkedCampaigns(open), s.measurements(childId)))
                        .orElse(null);
                openView = new ChildHealthRecordResponse.HealthOpenCampaign(
                        open.getId(),
                        open.getKind(),
                        open.getName(),
                        open.getStartDate(),
                        open.getEndDate(),
                        result == null ? null : result.getId(),
                        resultStatus(result));
            }
            // Phiếu khám nháp chưa công bố, không vào sổ; cảnh báo hiện tại theo bác sĩ và theo BMI (HLT-19)
            ChildHealthRecordResponse record = new ChildHealthRecordResponse(
                    childId,
                    cls.map(c -> rules.canRecord(currentUser, c)).orElse(false),
                    openView,
                    records.views(s, s.results(childId), child.dateOfBirth(), child.gender(), false),
                    s.latestExam(childId),
                    s.latestBmi(childId, child.dateOfBirth(), child.gender()));
            log.info("{}|END", logPrefix);
            return record;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthMeasurementResponse createMeasurement(
            CurrentUser currentUser, Long childId, HealthMeasurementRequest request) {
        String logPrefix = LOG_PREFIX + "|createMeasurement|userId=" + currentUser.userId() + "|childId=" + childId
                + "|campaignId=" + request.campaignId();
        log.info("{}|START", logPrefix);
        try {
            // Số đo chỉ nhập trong kỳ cân đo (user chốt 2026-10-31); đợt khám nhập bằng phiếu khám
            HealthCheckCampaign round = Optional.ofNullable(request.campaignId())
                    .flatMap(campaignRepository::findById)
                    .filter(c -> c.getKind() == HealthCheckKind.MEASUREMENT)
                    .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_ROUND_REQUIRED));
            // Ngày đo hệ thống ghi, GV không chọn (HLT-01): ngày nhập, vì kỳ tự đóng sau ngày kết thúc (HLT-17); kẹp
            // trong kỳ chỉ là chốt chặn khi job chưa chạy. Trẻ có lớp tại ngày đo nên ngày đo không trước ngày sinh
            LocalDate date = rules.referenceDate(round);
            ClassroomInfoResponse cls =
                    classOn(childId, date).orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_IN_CLASS));
            if (!cls.campusId().equals(round.getCampusId())) {
                throw GlobalException.of(ApiCode.HEALTH_ROUND_REQUIRED);
            }
            if (!rules.canRecord(currentUser, cls)) {
                throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);
            }
            ChildBriefResponse child = brief(childId);
            checkOpen(round);
            if (measurementRepository.existsByChildIdAndCampaignId(childId, round.getId())) {
                throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_EXISTS);
            }
            checkNotMeasuredInLinkedRound(childId, round);
            rules.checkPresent(childId, date);
            // HLT-01 (user chốt 2026-11-24): mỗi ngày một số đo kỳ cân đo; ngày trẻ chỉ có phiếu khám vẫn nhập được
            if (measurementRepository.findByChildIdAndDate(childId, date).stream()
                    .anyMatch(other -> !other.isExam())) {
                throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_DATE_TAKEN, date);
            }
            HealthMeasurement saved = measurementRepository.save(HealthMeasurement.builder()
                    .childId(childId)
                    .campaignId(round.getId())
                    .classroomId(cls.id())
                    .date(date)
                    .heightCm(request.heightCm())
                    .weightKg(request.weightKg())
                    .note(trimmed(request.note()))
                    .measuredBy(currentUser.userId())
                    .createdAt(rules.now())
                    .build());
            // HLT-11: lưu là công bố ⇒ báo phụ huynh
            healthNotifier.resultPublished(childId, child.fullName(), false);
            HealthMeasurementResponse view = view(saved, child);
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthMeasurementResponse updateMeasurement(
            CurrentUser currentUser, Long childId, Long measurementId, HealthMeasurementRequest request) {
        String logPrefix = LOG_PREFIX + "|updateMeasurement|userId=" + currentUser.userId() + "|childId=" + childId
                + "|measurementId=" + measurementId;
        log.info("{}|START", logPrefix);
        try {
            HealthMeasurement m = measurementRepository
                    .findById(measurementId)
                    .filter(x -> x.getChildId().equals(childId))
                    .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_MEASUREMENT_NOT_FOUND));
            if (m.isExam()) {
                throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_IS_EXAM);
            }
            HealthCheckCampaign round = campaignRepository
                    .findById(m.getCampaignId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_CHECK_NOT_FOUND));
            // G94-180/HLT-09: GV lớp hiện tại của trẻ sửa, kể cả số đo đo ở lớp cũ; lớp cũ chỉ xem
            boolean allowed = rules.editingClassOf(m, round)
                    .filter(cls -> rules.canRecord(currentUser, cls))
                    .isPresent();
            if (!allowed) {
                throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);
            }
            ChildBriefResponse child = brief(childId);
            checkOpen(round);
            // Sửa chỉ đổi chiều cao, cân nặng, ghi chú; giữ ngày đo, vòng đầu cũ (user chốt 2026-11-04)
            String note = trimmed(request.note());
            List<HealthMeasurementResponse.HealthMeasurementChange> changes = new ArrayList<>();
            change(changes, "heightCm", "Chiều cao (cm)", m.getHeightCm(), request.heightCm());
            change(changes, "weightKg", "Cân nặng (kg)", m.getWeightKg(), request.weightKg());
            change(changes, "note", "Ghi chú khám", m.getNote(), note);
            if (!changes.isEmpty()) {
                // HLT-13: sửa số đo đã công bố phải ghi lý do, lưu lịch sử; lưu là công bố lại
                String reason = trimmed(request.reason());
                if (reason.isEmpty()) {
                    throw GlobalException.of(ApiCode.HEALTH_EDIT_REASON_REQUIRED);
                }
                editRepository.save(HealthMeasurementEdit.of(
                        m.getId(), currentUser.userId(), reason, records.writeChanges(changes), rules.now()));
                m.update(request.heightCm(), request.weightKg(), note, rules.now());
                m.reassignRecorder(currentUser.userId());
                healthNotifier.resultPublished(childId, child.fullName(), true);
            }
            HealthMeasurementResponse view = view(m, child);
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private PublishedHealthResponse.ApprovedInterpretation approvedForParent(
            HealthInterpretation i, List<PublishedHealthResponse.GrowthPoint> growth) {
        HealthTrendContent content = i.getContent() == null
                ? null
                : objectMapper
                        .readValue(i.getContent(), HealthTrendContent.class)
                        .forParent();
        return new PublishedHealthResponse.ApprovedInterpretation(
                i.getId(),
                i.getPeriod(),
                content == null ? i.getSummary() : HealthTrendContentRules.render(content),
                content,
                objectMapper.readValue(
                        i.getWarnings(), new TypeReference<List<PublishedHealthResponse.TrendWarning>>() {}),
                HealthGrowth.stale(
                        objectMapper.readValue(i.getMeasurementIds(), new TypeReference<List<Long>>() {}),
                        HealthGrowth.within(growth, i.getPeriod(), rules.today())),
                i.getApprovedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedHealthResponse getPublishedHealth(CurrentUser currentUser, Long childId) {
        String logPrefix = LOG_PREFIX + "|getPublishedHealth|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-04, CHD-04: phụ huynh chỉ trẻ có liên kết và chưa nghỉ học; nhân sự theo phạm vi lớp
            childAccessPolicy.checkCanReadChild(currentUser, childId);
            ChildBriefResponse child = brief(childId);
            HealthRecords.Snapshot s = records.load(List.of(childId));
            List<HealthMeasurementResponse> published =
                    records.views(s, s.results(childId), child.dateOfBirth(), child.gender(), false);
            List<PublishedHealthResponse.PublishedHealthResult> results = published.stream()
                    .map(m -> new PublishedHealthResponse.PublishedHealthResult(
                            m.id(),
                            m.date(),
                            m.ageMonths(),
                            m.source(),
                            m.campaignKind(),
                            m.campaignName(),
                            m.heightCm(),
                            m.weightKg(),
                            m.headCircumferenceCm(),
                            m.bmi(),
                            m.bmiStatus(),
                            m.bmiZScore(),
                            m.note(),
                            m.followUpLevel(),
                            m.conclusionCategory(),
                            m.conclusion(),
                            m.publishedAt()))
                    .toList();
            List<PublishedHealthResponse.GrowthPoint> growth = HealthGrowth.points(published, child.gender());
            // HLT-05, PAR-03: phụ huynh chỉ thấy giải thích đã duyệt; HLT-21: không có gợi ý theo dõi (việc nội bộ)
            PublishedHealthResponse.ApprovedInterpretation interpretation = interpretationRepository
                    .findFirstByChildIdAndStatusOrderByApprovedAtDescIdDesc(childId, InterpretationStatus.APPROVED)
                    .map(i -> approvedForParent(i, growth))
                    .orElse(null);
            PublishedHealthResponse response = new PublishedHealthResponse(
                    childId,
                    results,
                    growth,
                    s.latestExam(childId),
                    s.latestBmi(childId, child.dateOfBirth(), child.gender()),
                    interpretation);
            log.info("{}|results={}|END", logPrefix, results.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ParentHealthExamResponse getPublishedExam(CurrentUser currentUser, Long childId, Long resultId) {
        String logPrefix = LOG_PREFIX + "|getPublishedExam|userId=" + currentUser.userId() + "|childId=" + childId
                + "|resultId=" + resultId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-04, CHD-04: cùng phạm vi với Sổ sức khỏe của phụ huynh
            childAccessPolicy.checkCanReadChild(currentUser, childId);
            ChildBriefResponse child = brief(childId);
            HealthRecords.Snapshot s = records.load(List.of(childId));
            // HLT-11: phiếu khám công bố khi kết luận; nháp và số đo kỳ cân đo không có phiếu
            HealthMeasurement m = s.results(childId).stream()
                    .filter(r -> r.getId().equals(resultId)
                            && r.isExam()
                            && r.getExamStatus() == HealthExamStatus.CONCLUDED)
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_EXAM_NOT_FOUND));
            HealthMeasurementResponse view =
                    records.views(s, List.of(m), child.dateOfBirth(), child.gender(), false)
                            .getFirst();
            HealthExamForm exam = s.exam(m);
            HealthCheckCampaign campaign = s.campaign(m.getCampaignId());
            // Không trả phần hành chính (số định danh, địa chỉ, người đi cùng) cho phụ huynh
            ParentHealthExamResponse response = new ParentHealthExamResponse(
                    m.getId(),
                    childId,
                    view.campaignName(),
                    campaign == null ? null : campaign.getExaminer(),
                    m.getDate(),
                    view.ageMonths(),
                    m.getHeightCm(),
                    m.getWeightKg(),
                    m.getHeadCircumferenceCm(),
                    view.followUpLevel(),
                    view.conclusionCategory(),
                    exam == null ? null : exam.history(),
                    exam == null ? null : exam.vitals(),
                    exam == null ? null : exam.nutrition(),
                    exam == null ? null : exam.development(),
                    exam == null ? null : exam.immunization(),
                    exam == null ? null : exam.clinical(),
                    exam == null ? null : exam.conclusion(),
                    m.getPublishedAt());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private ChildBriefResponse brief(Long childId) {
        ChildBriefResponse child = childService.findChildBriefs(Set.of(childId)).get(childId);
        if (child == null) {
            throw GlobalException.of(ApiCode.CHILD_NOT_FOUND);
        }
        return child;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LatestMeasurement> findLatestMeasurement(Long childId, LocalDate onOrBefore) {
        String logPrefix = LOG_PREFIX + "|findLatestMeasurement|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            // OBS-28, HLT-11: bỏ phiếu khám nháp; cùng ngày lấy phiếu khám rồi bản lưu sau (như HLT-08)
            Optional<LatestMeasurement> latest =
                    measurementRepository.findByChildIdOrderByDateDescIdDesc(childId).stream()
                            .filter(m -> !m.isDraft())
                            .filter(m -> !m.getDate().isAfter(onOrBefore))
                            .filter(m -> m.getWeightKg() != null || m.getHeightCm() != null)
                            .max(Comparator.comparing(HealthMeasurement::getDate)
                                    .thenComparing(HealthMeasurement::isExam)
                                    .thenComparing(HealthMeasurement::getId))
                            .map(m -> new LatestMeasurement(m.getDate(), m.getWeightKg(), m.getHeightCm()));
            log.info("{}|END", logPrefix);
            return latest;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Optional<ClassroomInfoResponse> classOn(Long childId, LocalDate date) {
        return enrollmentService.findActiveClassroomIds(childId, date).stream()
                .findFirst()
                .flatMap(classroomService::findClassroomInfo);
    }

    // G94-180/HLT-09: đã đo ở kỳ liên quan (trẻ chuyển điểm trường) thì không đo lần hai; câu lỗi nêu kỳ, ngày đo
    private void checkNotMeasuredInLinkedRound(Long childId, HealthCheckCampaign round) {
        List<HealthCheckCampaign> linked = rules.linkedCampaigns(round);
        if (linked.isEmpty()) {
            return;
        }
        rules.linkedResultOf(round, linked, measurementRepository.findByChildIdOrderByDateDescIdDesc(childId))
                .ifPresent(m -> {
                    HealthCheckCampaign earlier = linked.stream()
                            .filter(l -> l.getId().equals(m.getCampaignId()))
                            .findFirst()
                            .orElseThrow();
                    throw GlobalException.withFieldErrors(
                            ApiCode.HEALTH_MEASUREMENT_EXISTS,
                            List.of(new FieldErrorResponse(
                                    "campaignId",
                                    "Trẻ đã có số đo trong kỳ «" + earlier.getName() + "» ("
                                            + rules.campusName(earlier.getCampusId()) + ") ngày "
                                            + DMY.format(m.getDate()) + ". Sửa số đo đó.")));
                });
    }

    // Kỳ đã bắt đầu, chưa đóng, chưa hủy
    private void checkOpen(HealthCheckCampaign round) {
        if (round.statusOn(rules.today()) != HealthCheckStatus.IN_PROGRESS) {
            throw GlobalException.of(ApiCode.HEALTH_ROUND_NOT_OPEN);
        }
    }

    /** RECORDED với số đo của kỳ cân đo; DRAFT/CONCLUDED theo phiếu khám; null khi chưa có. */
    private static String resultStatus(HealthMeasurement m) {
        if (m == null) {
            return null;
        }
        return m.isExam() ? m.getExamStatus().name() : RECORDED;
    }

    private HealthMeasurementResponse view(HealthMeasurement m, ChildBriefResponse child) {
        HealthRecords.Snapshot s = records.load(List.of(child.id()));
        return records.views(s, List.of(m), child.dateOfBirth(), child.gender(), false)
                .getFirst();
    }

    private static void change(
            List<HealthMeasurementResponse.HealthMeasurementChange> changes,
            String field,
            String label,
            Object from,
            Object to) {
        boolean same = from instanceof BigDecimal a && to instanceof BigDecimal b
                ? a.compareTo(b) == 0
                : Objects.equals(from, to);
        if (!same) {
            changes.add(new HealthMeasurementResponse.HealthMeasurementChange(field, label, text(from), text(to)));
        }
    }

    private static String text(Object value) {
        return value == null ? null : value instanceof BigDecimal d ? d.toPlainString() : value.toString();
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
