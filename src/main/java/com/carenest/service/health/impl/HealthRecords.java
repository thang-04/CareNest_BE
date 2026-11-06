package com.carenest.service.health.impl;

import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamResponse;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.entity.health.HealthMeasurementEdit;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.repository.health.HealthMeasurementEditRepository;
import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.service.account.AccountService;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.HealthFollowUpLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Dữ liệu sức khỏe của một nhóm trẻ đọc một lần (lần đo, đợt/kỳ, phiếu khám) và các giá trị tính từ đó: cảnh báo của
 * từng lần đo (mức theo dõi theo kết luận bác sĩ, mức BMI của kỳ cân đo — HLT-19), lần khám trước, cảnh báo hiện tại, lần
 * đo trả về API.
 */
@Component
@RequiredArgsConstructor
class HealthRecords {

    // Vòng đầu chỉ đo ở phiếu khám, khi trẻ chưa quá 60 tháng tại ngày khám
    static final int HEAD_CIRCUMFERENCE_MAX_MONTHS = 60;
    static final String HEAD_NOT_MEASURED = "NOT_MEASURED";
    private static final TypeReference<List<HealthMeasurementResponse.HealthMeasurementChange>> CHANGES =
            new TypeReference<>() {};

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final HealthMeasurementRepository measurementRepository;
    private final HealthMeasurementEditRepository editRepository;
    private final HealthCheckCampaignRepository campaignRepository;
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

    /** Tuổi theo tháng tròn tại ngày đo. */
    static int ageMonths(LocalDate dateOfBirth, LocalDate date) {
        return (int) ChronoUnit.MONTHS.between(dateOfBirth, date);
    }

    /**
     * Mức BMI theo tuổi của số đo kỳ cân đo (HLT-18); null với phiếu khám (bác sĩ kết luận) hoặc khi không xếp được mức.
     */
    static WhoBmiForAge.Result bmiOf(HealthMeasurement m, LocalDate dateOfBirth, Gender gender) {
        if (m.isExam() || dateOfBirth == null) {
            return null;
        }
        return WhoBmiForAge.classify(gender, ageMonths(dateOfBirth, m.getDate()), m.getHeightCm(), m.getWeightKg());
    }

    /** Mức theo dõi của một lần đo (đọc JSON phiếu khám khi đã kết luận). */
    HealthExamOutcome.Outcome outcomeOf(HealthMeasurement m) {
        return m.getExamStatus() == HealthExamStatus.CONCLUDED
                ? HealthExamOutcome.of(m, readExam(m.getExam()))
                : HealthExamOutcome.UNAVAILABLE;
    }

    /** Mọi lần đo của các trẻ. */
    Snapshot load(Collection<Long> childIds) {
        List<HealthMeasurement> measurements =
                childIds.isEmpty() ? List.of() : measurementRepository.findByChildIdIn(childIds);
        Map<Long, HealthCheckCampaign> campaigns = campaignRepository
                .findAllById(measurements.stream()
                        .map(HealthMeasurement::getCampaignId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(HealthCheckCampaign::getId, Function.identity()));
        Map<Long, HealthExamForm> exams = new HashMap<>();
        measurements.stream()
                .filter(HealthMeasurement::isExam)
                .forEach(m -> exams.put(m.getId(), readExam(m.getExam())));
        return new Snapshot(LocalDate.now(clock), measurements, campaigns, exams);
    }

    /** Lần đo trả về API; {@code withReview}: kèm điểm cần bác sĩ xem lại (API phiếu khám). */
    List<HealthMeasurementResponse> views(
            Snapshot s,
            List<HealthMeasurement> measurements,
            LocalDate dateOfBirth,
            Gender gender,
            boolean withReview) {
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
                .map(m -> view(s, m, dateOfBirth, gender, edits.getOrDefault(m.getId(), List.of()), names, withReview))
                .toList();
    }

    private HealthMeasurementResponse view(
            Snapshot s,
            HealthMeasurement m,
            LocalDate dateOfBirth,
            Gender gender,
            List<HealthMeasurementEdit> edits,
            Map<Long, String> names,
            boolean withReview) {
        HealthCheckCampaign c = s.campaign(m.getCampaignId());
        int ageMonths = ageMonths(dateOfBirth, m.getDate());
        HealthExamForm exam = s.exam(m);
        HealthExamOutcome.Outcome outcome = s.outcome(m);
        WhoBmiForAge.Result bmiStatus = bmiOf(m, dateOfBirth, gender);
        String headStatus = null;
        if (exam != null) {
            String doctor = exam.nutrition() == null
                    ? ""
                    : HealthExamOutcome.text(exam.nutrition().headStatus());
            headStatus =
                    ageMonths > HEAD_CIRCUMFERENCE_MAX_MONTHS ? HEAD_NOT_MEASURED : doctor.isEmpty() ? null : doctor;
        }
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
                bmiStatus == null ? null : bmiStatus.status(),
                bmiStatus == null ? null : bmiStatus.zScore(),
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
                withReview && exam != null
                        ? HealthExamReview.points(exam, HealthExamReview.reference(dateOfBirth, m.getDate()))
                        : null);
    }

    // BMI = cân nặng / (chiều cao m)^2 làm tròn 1 chữ số để hiển thị; mức BMI tính từ số chưa làm tròn (WhoBmiForAge)
    static BigDecimal bmi(HealthMeasurement m) {
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
        private final Map<Long, List<HealthMeasurement>> measurementsByChild;
        private final Map<Long, HealthCheckCampaign> campaigns;
        private final Map<Long, HealthExamForm> exams;

        private Snapshot(
                LocalDate today,
                List<HealthMeasurement> measurements,
                Map<Long, HealthCheckCampaign> campaigns,
                Map<Long, HealthExamForm> exams) {
            this.today = today;
            this.measurementsByChild = measurements.stream()
                    .sorted(Comparator.comparing(HealthMeasurement::getDate)
                            .thenComparing(HealthMeasurement::getId)
                            .reversed())
                    .collect(Collectors.groupingBy(HealthMeasurement::getChildId));
            this.campaigns = campaigns;
            this.exams = exams;
        }

        LocalDate today() {
            return today;
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

        /** Mọi lần đo của trẻ (cả phiếu nháp), mới nhất trước. */
        List<HealthMeasurement> measurements(Long childId) {
            return measurementsByChild.getOrDefault(childId, List.of());
        }

        /** Kết quả đã công bố (bỏ phiếu khám nháp), mới nhất trước. */
        List<HealthMeasurement> results(Long childId) {
            return measurements(childId).stream().filter(m -> !m.isDraft()).toList();
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

        /** Cảnh báo hiện tại theo bác sĩ: lần khám gần nhất có kết luận (HLT-10, HLT-19); null khi chưa có. */
        ChildHealthRecordResponse.HealthLatestExam latestExam(Long childId) {
            return results(childId).stream()
                    .filter(m -> outcome(m).level() != HealthFollowUpLevel.UNAVAILABLE)
                    .findFirst()
                    .map(m -> {
                        HealthExamOutcome.Outcome outcome = outcome(m);
                        HealthCheckCampaign c = campaign(m.getCampaignId());
                        return new ChildHealthRecordResponse.HealthLatestExam(
                                m.getId(),
                                m.getCampaignId(),
                                c == null ? null : c.getName(),
                                m.getDate(),
                                outcome.level(),
                                outcome.category(),
                                conclusion(m));
                    })
                    .orElse(null);
        }

        /** Cảnh báo hiện tại theo BMI: lần đo gần nhất của kỳ cân đo có mức BMI (HLT-18, HLT-19); null khi chưa có. */
        ChildHealthRecordResponse.HealthLatestBmi latestBmi(Long childId, LocalDate dateOfBirth, Gender gender) {
            for (HealthMeasurement m : results(childId)) {
                WhoBmiForAge.Result r = bmiOf(m, dateOfBirth, gender);
                if (r != null) {
                    HealthCheckCampaign c = campaign(m.getCampaignId());
                    return new ChildHealthRecordResponse.HealthLatestBmi(
                            m.getId(),
                            m.getCampaignId(),
                            c == null ? null : c.getName(),
                            m.getDate(),
                            ageMonths(dateOfBirth, m.getDate()),
                            bmi(m),
                            r.status(),
                            r.zScore());
                }
            }
            return null;
        }
    }
}
