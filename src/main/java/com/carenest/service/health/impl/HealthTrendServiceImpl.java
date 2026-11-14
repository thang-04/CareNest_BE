package com.carenest.service.health.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.health.HealthTrendAnalysisResponse;
import com.carenest.dto.health.HealthTrendApproveRequest;
import com.carenest.dto.health.HealthTrendContent;
import com.carenest.dto.health.PublishedHealthResponse.GrowthPoint;
import com.carenest.dto.health.PublishedHealthResponse.TrendWarning;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthInterpretation;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClient;
import com.carenest.integration.ai.AiException;
import com.carenest.repository.health.HealthInterpretationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.ChildService;
import com.carenest.service.health.HealthTrendService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthTrendFollowUpType;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Không log số đo, nội dung prompt/response (AI-05) — chỉ id, số lượng, kết quả. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthTrendServiceImpl implements HealthTrendService {
    private static final String LOG_PREFIX = "[HealthTrendServiceImpl]";
    private static final int MAX_SUMMARY = 2000;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Ngày đo trong câu cảnh báo của HealthGrowth ("lần đo ngày dd/MM/yyyy", "ngày … và dd/MM/yyyy")
    private static final Pattern MEASUREMENT_DATE = Pattern.compile("(?:ngày )?(\\d{2}/\\d{2}/\\d{4})");
    private static final int MAX_TOKENS = 1200;
    // HLT-21: AI chỉ viết lời theo khuôn JSON; cảnh báo và loại gợi ý do hệ thống quyết định (HLT-02, HLT-04)
    private static final String SYSTEM_PROMPT = String.join(
            "\n",
            "Bạn viết giải thích ngắn về số đo tăng trưởng của một trẻ mầm non cho giáo viên, dựa trên số liệu được cho"
                    + " và chuẩn WHO. Chỉ trả về một đối tượng JSON đúng dạng:",
            "{\"overview\": \"...\", \"attentionPoints\": [{\"warningCode\": \"MÃ\", \"text\": \"...\"}],"
                    + " \"positives\": [\"...\"], \"followUps\": [{\"type\": \"LOẠI\", \"text\": \"...\"}],"
                    + " \"limitations\": \"...\"}",
            "- overview: 1 đến 2 câu tổng quan xu hướng cân nặng, chiều cao, BMI.",
            "- attentionPoints: mỗi cảnh báo có mã (trừ NORMAL) đúng một mục, giải thích kèm số liệu; chỉ dùng mã đã cho,"
                    + " không thêm cảnh báo mới; không có cảnh báo thì để mảng rỗng.",
            "- positives: tối đa 3 điểm tích cực có trong số liệu; không có thì để mảng rỗng.",
            "- followUps: chỉ dùng loại trong danh sách được phép, mỗi loại tối đa một mục.",
            "- limitations: một câu về giới hạn của dữ liệu (ít lần đo, khoảng thời gian ngắn) hoặc null.",
            "Mỗi mục tối đa 1 câu tiếng Việt, giọng trung tính, dễ hiểu với phụ huynh; z-score chỉ ghi trong ngoặc.",
            "Không chẩn đoán bệnh hay rối loạn phát triển, không khuyên điều trị hoặc dùng thuốc, không đoán nguyên nhân,"
                    + " không gợi ý đưa trẻ đi khám hay chuyển y tế, không nhắc tên trẻ.");
    private static final Map<HealthTrendFollowUpType, String> FOLLOW_UP_LABELS = Map.of(
            HealthTrendFollowUpType.RECHECK_NEXT_ROUND, "theo dõi ở kỳ cân đo sau",
            HealthTrendFollowUpType.TALK_TO_PARENT, "trao đổi với phụ huynh",
            HealthTrendFollowUpType.REMEASURE, "đo lại để xác nhận trong kỳ cân đo đang diễn ra");

    private final ChildAccessPolicy childAccessPolicy;
    private final ChildService childService;
    private final HealthRecords records;
    private final HealthCheckRules rules;
    private final HealthInterpretationRepository interpretationRepository;
    private final AccountService accountService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public HealthTrendAnalysisResponse analyze(CurrentUser actor, Long childId) {
        String logPrefix = LOG_PREFIX + "|analyze|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkCanView(actor, childId);
            ChildBriefResponse child = brief(childId);
            HealthRecords.Snapshot s = records.load(List.of(childId));
            List<HealthMeasurementResponse> published =
                    records.views(s, s.results(childId), child.dateOfBirth(), child.gender(), false);
            List<GrowthPoint> points = HealthGrowth.points(published, child.gender());
            if (points.size() < 2) {
                log.info("{}|points={}|notEnoughData|END", logPrefix, points.size());
                return HealthTrendAnalysisResponse.notEnoughData();
            }
            List<TrendWarning> warnings = HealthGrowth.warnings(points);
            Set<HealthTrendFollowUpType> allowed =
                    HealthGrowth.allowedFollowUps(warnings, remeasurePossible(s, published, points.getLast(), childId));
            if (!aiClient.isEnabled()) {
                throw GlobalException.of(ApiCode.AI_SERVICE_UNAVAILABLE);
            }
            AiClient.AiResult result;
            try {
                result = aiClient.complete(new AiClient.AiRequest(
                        "health-trend",
                        SYSTEM_PROMPT,
                        userPrompt(child.gender(), points, warnings, allowed),
                        MAX_TOKENS,
                        true));
            } catch (AiException ex) {
                throw GlobalException.withCause(
                        ex.getReason() == AiException.Reason.TIMEOUT
                                ? ApiCode.AI_TIMEOUT
                                : ApiCode.AI_SERVICE_UNAVAILABLE,
                        ex);
            }
            HealthTrendContent content = HealthTrendContentRules.fromAi(result.text(), warnings, allowed)
                    .orElse(null);
            if (content == null) {
                log.warn("{}|blocked=invalidOutput", logPrefix);
                throw GlobalException.of(ApiCode.AI_SERVICE_UNAVAILABLE);
            }
            HealthInterpretation saved = interpretationRepository.save(HealthInterpretation.draft(
                    childId,
                    HealthTrendContentRules.render(content),
                    objectMapper.writeValueAsString(content),
                    objectMapper.writeValueAsString(warnings),
                    objectMapper.writeValueAsString(
                            points.stream().map(GrowthPoint::measurementId).toList()),
                    result.model(),
                    actor.userId(),
                    rules.now()));
            HealthTrendAnalysisResponse response = toResponse(saved);
            log.info("{}|points={}|warnings={}|id={}|END", logPrefix, points.size(), warnings.size(), saved.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HealthTrendAnalysisResponse latest(CurrentUser actor, Long childId) {
        String logPrefix = LOG_PREFIX + "|latest|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkCanView(actor, childId);
            HealthTrendAnalysisResponse response = interpretationRepository
                    .findFirstByChildIdOrderByGeneratedAtDescIdDesc(childId)
                    .map(this::toResponse)
                    .orElse(null);
            log.info("{}|found={}|END", logPrefix, response != null);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public HealthTrendAnalysisResponse approve(
            CurrentUser actor, Long childId, Long interpretationId, HealthTrendApproveRequest request) {
        String logPrefix =
                LOG_PREFIX + "|approve|userId=" + actor.userId() + "|childId=" + childId + "|id=" + interpretationId;
        log.info("{}|START", logPrefix);
        try {
            checkCanView(actor, childId);
            HealthInterpretation interpretation = interpretationRepository
                    .findByIdAndChildId(interpretationId, childId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_INTERPRETATION_NOT_FOUND));
            if (!interpretation.isApproved()) {
                String edited = request == null || request.summary() == null
                        ? null
                        : request.summary().strip();
                if (edited != null && edited.isEmpty()) {
                    edited = null;
                }
                if (edited != null && edited.length() > MAX_SUMMARY) {
                    throw GlobalException.withFieldErrors(
                            ApiCode.INVALID_REQUEST_DATA,
                            List.of(new FieldErrorResponse("summary", "Nội dung tối đa " + MAX_SUMMARY + " ký tự")));
                }
                interpretation.approve(edited, actor.userId(), rules.now());
                interpretationRepository.save(interpretation);
            }
            HealthTrendAnalysisResponse response = toResponse(interpretation);
            log.info("{}|source={}|END", logPrefix, interpretation.getSource());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Cảnh báo đã duyệt đọc lại từ JSON lưu kèm (dùng chung cho màn phụ huynh). */
    List<TrendWarning> warningsOf(HealthInterpretation interpretation) {
        return objectMapper.readValue(interpretation.getWarnings(), new TypeReference<List<TrendWarning>>() {});
    }

    HealthTrendContent contentOf(HealthInterpretation interpretation) {
        return interpretation.getContent() == null
                ? null
                : objectMapper.readValue(interpretation.getContent(), HealthTrendContent.class);
    }

    /**
     * HLT-21: gợi ý đo lại chỉ khi lần đo mới nhất thuộc kỳ cân đo đang diễn ra (còn sửa được số đo — HLT-13) và chưa
     * bản giải thích nào gợi ý đo lại chính lần đo đó (đo lại vẫn thế thì không lặp câu thừa).
     */
    private boolean remeasurePossible(
            HealthRecords.Snapshot s, List<HealthMeasurementResponse> published, GrowthPoint latest, Long childId) {
        HealthMeasurementResponse measurement = published.stream()
                .filter(m -> m.id().equals(latest.measurementId()))
                .findFirst()
                .orElse(null);
        if (measurement == null || measurement.campaignKind() != HealthCheckKind.MEASUREMENT) {
            return false;
        }
        HealthCheckCampaign round = s.campaign(measurement.campaignId());
        if (round == null || round.statusOn(rules.today()) != HealthCheckStatus.IN_PROGRESS) {
            return false;
        }
        return interpretationRepository.findByChildIdOrderByGeneratedAtDescIdDesc(childId).stream()
                .noneMatch(i -> suggestedRemeasure(i, measurement.id()));
    }

    private boolean suggestedRemeasure(HealthInterpretation interpretation, Long measurementId) {
        HealthTrendContent content = contentOf(interpretation);
        if (content == null
                || content.followUps().stream().noneMatch(f -> f.type() == HealthTrendFollowUpType.REMEASURE)) {
            return false;
        }
        return objectMapper
                .readValue(interpretation.getMeasurementIds(), new TypeReference<List<Long>>() {})
                .contains(measurementId);
    }

    private HealthTrendAnalysisResponse toResponse(HealthInterpretation i) {
        String approvedByName = i.getApprovedBy() == null
                ? null
                : accountService.findFullNames(Set.of(i.getApprovedBy())).get(i.getApprovedBy());
        return new HealthTrendAnalysisResponse(
                i.getId(),
                HealthTrendAnalysisResponse.OK,
                !i.isApproved(),
                i.getGeneratedAt(),
                i.getSummary(),
                contentOf(i),
                warningsOf(i),
                i.getSource(),
                i.getModel(),
                i.getApprovedBy(),
                approvedByName,
                i.getApprovedAt());
    }

    /**
     * AI-04: chỉ giới tính, tuổi tháng và số đo; không tên, ngày sinh, mã trẻ, không ngày đo (ngày đo cùng tuổi tháng suy
     * ra được tháng sinh) — câu cảnh báo đổi "ngày dd/MM/yyyy" thành tuổi tháng của lần đo đó.
     */
    static String userPrompt(
            Gender gender,
            List<GrowthPoint> points,
            List<TrendWarning> warnings,
            Set<HealthTrendFollowUpType> allowed) {
        Map<String, Integer> ageByDate = new HashMap<>();
        points.forEach(p -> ageByDate.put(DMY.format(p.date()), p.ageMonths()));
        StringBuilder sb = new StringBuilder();
        sb.append("Giới tính: ").append(gender == Gender.MALE ? "nam" : "nữ").append(".\n");
        sb.append("Các lần đo (cũ đến mới):\n");
        for (GrowthPoint p : points) {
            sb.append("- ")
                    .append(p.ageMonths())
                    .append(" tháng tuổi: chiều cao ")
                    .append(text(p.heightCm()))
                    .append(" cm, cân nặng ")
                    .append(text(p.weightKg()))
                    .append(" kg, BMI ")
                    .append(text(p.bmi()))
                    .append("; z-score cân nặng/tuổi ")
                    .append(text(p.weightForAgeZ()))
                    .append(", chiều cao/tuổi ")
                    .append(text(p.heightForAgeZ()))
                    .append(", BMI/tuổi ")
                    .append(text(p.bmiZScore()))
                    .append('\n');
        }
        GrowthPoint first = points.getFirst();
        GrowthPoint last = points.getLast();
        sb.append("Số lần đo: ")
                .append(points.size())
                .append("; từ ")
                .append(first.ageMonths())
                .append(" đến ")
                .append(last.ageMonths())
                .append(" tháng tuổi (")
                .append(last.ageMonths() - first.ageMonths())
                .append(" tháng).\n");
        sb.append("Cảnh báo đã tính từ số liệu (mã trong ngoặc vuông):\n");
        warnings.forEach(w -> sb.append("- [")
                .append(w.code())
                .append("] ")
                .append(MEASUREMENT_DATE.matcher(w.text()).replaceAll(match -> {
                    Integer age = ageByDate.get(match.group(1));
                    return age == null ? "trước đó" : "lúc " + age + " tháng tuổi";
                }))
                .append('\n'));
        sb.append("Loại gợi ý theo dõi được dùng:\n");
        allowed.stream()
                .sorted()
                .forEach(t -> sb.append("- ")
                        .append(t)
                        .append(": ")
                        .append(FOLLOW_UP_LABELS.get(t))
                        .append('\n'));
        return sb.toString();
    }

    private static String text(Object value) {
        return value == null ? "không có" : value.toString();
    }

    private void checkCanView(CurrentUser actor, Long childId) {
        if (!childAccessPolicy.canViewChild(actor, childId)) {
            throw GlobalException.of(ApiCode.HEALTH_RECORD_ACCESS_DENIED);
        }
    }

    private ChildBriefResponse brief(Long childId) {
        ChildBriefResponse child = childService.findChildBriefs(Set.of(childId)).get(childId);
        if (child == null) {
            throw GlobalException.of(ApiCode.CHILD_NOT_FOUND);
        }
        return child;
    }
}
