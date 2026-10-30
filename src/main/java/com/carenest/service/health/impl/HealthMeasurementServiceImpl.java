package com.carenest.service.health.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.entity.health.HealthMeasurementEdit;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.repository.health.HealthMeasurementEditRepository;
import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.health.HealthMeasurementService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    // Vòng đầu chỉ đo tới khi trẻ đủ 60 tháng tại ngày đo
    private static final int HEAD_CIRCUMFERENCE_MAX_MONTHS = 60;
    private static final String RECORDED = "RECORDED";
    private static final TypeReference<List<HealthMeasurementResponse.HealthMeasurementChange>> CHANGES =
            new TypeReference<>() {};

    private final HealthCheckRules rules;
    private final HealthMeasurementRepository measurementRepository;
    private final HealthMeasurementEditRepository editRepository;
    private final HealthCheckCampaignRepository campaignRepository;
    private final ChildAccessPolicy childAccessPolicy;
    private final ChildService childService;
    private final EnrollmentService enrollmentService;
    private final ClassroomService classroomService;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ChildHealthRecordResponse getHealthRecord(CurrentUser currentUser, Long childId) {
        String logPrefix = LOG_PREFIX + "|getHealthRecord|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            if (!childAccessPolicy.canViewChild(currentUser, childId)) {
                throw GlobalException.of(ApiCode.HEALTH_RECORD_ACCESS_DENIED);
            }
            ChildBriefResponse child = brief(childId);
            Optional<ClassroomInfoResponse> cls = classOn(childId, rules.today());
            // Đợt/kỳ đang diễn ra ở điểm trường của lớp trẻ đang học
            HealthCheckCampaign open = cls.flatMap(c ->
                            campaignRepository.findFirstByCampusIdAndClosedAtIsNullAndCancelledAtIsNull(c.campusId()))
                    .filter(c -> c.statusOn(rules.today()) == HealthCheckStatus.IN_PROGRESS)
                    .orElse(null);
            ChildHealthRecordResponse.HealthOpenCampaign openView = null;
            if (open != null) {
                Long resultId = measurementRepository
                        .findByChildIdAndCampaignId(childId, open.getId())
                        .map(HealthMeasurement::getId)
                        .orElse(null);
                openView = new ChildHealthRecordResponse.HealthOpenCampaign(
                        open.getId(),
                        open.getKind(),
                        open.getName(),
                        open.getStartDate(),
                        open.getEndDate(),
                        resultId,
                        resultId == null ? null : RECORDED);
            }
            ChildHealthRecordResponse record = new ChildHealthRecordResponse(
                    childId,
                    child.dateOfBirth().plusMonths(HEAD_CIRCUMFERENCE_MAX_MONTHS),
                    cls.map(c -> rules.canRecord(currentUser, c)).orElse(false),
                    openView,
                    toViews(measurementRepository.findByChildIdOrderByDateDescIdDesc(childId), child));
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
            ClassroomInfoResponse cls =
                    classOn(childId, request.date()).orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_IN_CLASS));
            if (!cls.campusId().equals(round.getCampusId())) {
                throw GlobalException.of(ApiCode.HEALTH_ROUND_REQUIRED);
            }
            if (!rules.canRecord(currentUser, cls)) {
                throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);
            }
            ChildBriefResponse child = brief(childId);
            checkOpenAndDate(round, request.date(), child);
            if (measurementRepository.existsByChildIdAndCampaignId(childId, round.getId())) {
                throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_EXISTS);
            }
            if (measurementRepository
                    .findByChildIdAndDate(childId, request.date())
                    .isPresent()) {
                throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_DATE_TAKEN);
            }
            HealthMeasurement saved = measurementRepository.save(HealthMeasurement.builder()
                    .childId(childId)
                    .campaignId(round.getId())
                    .classroomId(cls.id())
                    .date(request.date())
                    .heightCm(request.heightCm())
                    .weightKg(request.weightKg())
                    .headCircumferenceCm(headOf(request, child))
                    .note(trimmed(request.note()))
                    .measuredBy(currentUser.userId())
                    .createdAt(rules.now())
                    .build());
            HealthMeasurementResponse view = toViews(List.of(saved), child).getFirst();
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
            ClassroomInfoResponse cls = classroomService
                    .findClassroomInfo(m.getClassroomId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
            if (!rules.canRecord(currentUser, cls)) {
                throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_ACCESS_DENIED);
            }
            HealthCheckCampaign round = campaignRepository
                    .findById(m.getCampaignId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_CHECK_NOT_FOUND));
            ChildBriefResponse child = brief(childId);
            checkOpenAndDate(round, request.date(), child);
            measurementRepository
                    .findByChildIdAndDate(childId, request.date())
                    .filter(other -> !other.getId().equals(m.getId()))
                    .ifPresent(other -> {
                        throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_DATE_TAKEN);
                    });
            BigDecimal head = headOf(request, child);
            String note = trimmed(request.note());
            List<HealthMeasurementResponse.HealthMeasurementChange> changes = new ArrayList<>();
            change(changes, "date", m.getDate(), request.date());
            change(changes, "heightCm", m.getHeightCm(), request.heightCm());
            change(changes, "weightKg", m.getWeightKg(), request.weightKg());
            change(changes, "headCircumferenceCm", m.getHeadCircumferenceCm(), head);
            change(changes, "note", m.getNote(), note);
            if (!changes.isEmpty()) {
                // HLT-13: sửa số đo đã công bố phải ghi lý do, lưu lịch sử; lưu là công bố lại
                String reason = trimmed(request.reason());
                if (reason.isEmpty()) {
                    throw GlobalException.of(ApiCode.HEALTH_EDIT_REASON_REQUIRED);
                }
                editRepository.save(HealthMeasurementEdit.of(
                        m.getId(),
                        currentUser.userId(),
                        reason,
                        objectMapper.writeValueAsString(changes),
                        rules.now()));
                m.update(request.date(), request.heightCm(), request.weightKg(), head, note, rules.now());
            }
            HealthMeasurementResponse view = toViews(List.of(m), child).getFirst();
            log.info("{}|END", logPrefix);
            return view;
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

    private Optional<ClassroomInfoResponse> classOn(Long childId, LocalDate date) {
        return enrollmentService.findActiveClassroomIds(childId, date).stream()
                .findFirst()
                .flatMap(classroomService::findClassroomInfo);
    }

    // Kỳ đang diễn ra; ngày đo trong khoảng của kỳ, không ở tương lai, không trước ngày sinh
    private void checkOpenAndDate(HealthCheckCampaign round, LocalDate date, ChildBriefResponse child) {
        if (round.statusOn(rules.today()) != HealthCheckStatus.IN_PROGRESS) {
            throw GlobalException.of(ApiCode.HEALTH_ROUND_NOT_OPEN);
        }
        if (date.isBefore(round.getStartDate())
                || date.isAfter(round.getEndDate())
                || date.isAfter(rules.today())
                || date.isBefore(child.dateOfBirth())) {
            throw GlobalException.of(ApiCode.HEALTH_MEASUREMENT_DATE_OUTSIDE);
        }
    }

    private static BigDecimal headOf(HealthMeasurementRequest request, ChildBriefResponse child) {
        boolean applies = !request.date().isAfter(child.dateOfBirth().plusMonths(HEAD_CIRCUMFERENCE_MAX_MONTHS));
        return applies ? request.headCircumferenceCm() : null;
    }

    private static void change(
            List<HealthMeasurementResponse.HealthMeasurementChange> changes, String field, Object from, Object to) {
        boolean same = from instanceof BigDecimal a && to instanceof BigDecimal b
                ? a.compareTo(b) == 0
                : Objects.equals(from, to);
        if (!same) {
            changes.add(new HealthMeasurementResponse.HealthMeasurementChange(field, text(from), text(to)));
        }
    }

    private static String text(Object value) {
        return value == null ? null : value instanceof BigDecimal d ? d.toPlainString() : value.toString();
    }

    private List<HealthMeasurementResponse> toViews(List<HealthMeasurement> measurements, ChildBriefResponse child) {
        Map<Long, HealthCheckCampaign> campaigns = campaignRepository
                .findAllById(measurements.stream()
                        .map(HealthMeasurement::getCampaignId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(HealthCheckCampaign::getId, Function.identity()));
        Map<Long, List<HealthMeasurementEdit>> edits = editRepository
                .findByMeasurementIdInOrderByCreatedAtAscIdAsc(
                        measurements.stream().map(HealthMeasurement::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(HealthMeasurementEdit::getMeasurementId));
        Map<Long, String> names = accountService.findFullNames(Stream.concat(
                        measurements.stream().map(HealthMeasurement::getMeasuredBy),
                        edits.values().stream().flatMap(List::stream).map(HealthMeasurementEdit::getUserId))
                .collect(Collectors.toSet()));
        return measurements.stream()
                .map(m -> {
                    HealthCheckCampaign c = campaigns.get(m.getCampaignId());
                    return new HealthMeasurementResponse(
                            m.getId(),
                            m.getChildId(),
                            m.getCampaignId(),
                            c == null ? null : c.getName(),
                            c == null ? null : c.getKind(),
                            m.getClassroomId(),
                            m.getDate(),
                            (int) ChronoUnit.MONTHS.between(child.dateOfBirth(), m.getDate()),
                            m.getHeightCm(),
                            m.getWeightKg(),
                            m.getHeadCircumferenceCm(),
                            bmi(m),
                            m.getNote(),
                            HealthMeasurementResponse.SOURCE_MEASUREMENT_ONLY,
                            true,
                            m.getPublishedAt(),
                            m.getMeasuredBy(),
                            names.get(m.getMeasuredBy()),
                            c == null || c.statusOn(rules.today()) != HealthCheckStatus.IN_PROGRESS,
                            edits.getOrDefault(m.getId(), List.of()).stream()
                                    .map(e -> new HealthMeasurementResponse.HealthMeasurementEditView(
                                            e.getCreatedAt(),
                                            e.getUserId(),
                                            names.get(e.getUserId()),
                                            e.getReason(),
                                            objectMapper.readValue(e.getChanges(), CHANGES)))
                                    .toList());
                })
                .toList();
    }

    // BMI = cân nặng / (chiều cao m)^2, chỉ là số tính, không phân loại (HLT-10 phân loại hoãn, user 2026-10-31)
    private static BigDecimal bmi(HealthMeasurement m) {
        BigDecimal heightM = m.getHeightCm().movePointLeft(2);
        return m.getWeightKg().divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
