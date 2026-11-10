package com.carenest.service.observation.impl;

import com.carenest.dto.child.ChildIdentity;
import com.carenest.dto.knowledge.RetrievalQuery;
import com.carenest.dto.observation.ChildSummaryResponse;
import com.carenest.dto.observation.CreateSummaryDraftRequest;
import com.carenest.dto.observation.UpdateSummaryContentRequest;
import com.carenest.entity.observation.ChildSummary;
import com.carenest.entity.observation.CriteriaOption;
import com.carenest.entity.observation.Observation;
import com.carenest.entity.observation.ObservationCriteria;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClient;
import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.dto.SummaryDraftInput;
import com.carenest.integration.ai.dto.SummaryDraftInput.CriteriaFact;
import com.carenest.integration.ai.dto.SummaryDraftInput.ReferenceSnippet;
import com.carenest.integration.ai.dto.SummaryDraftResult;
import com.carenest.mapper.observation.ChildSummaryMapper;
import com.carenest.repository.observation.ChildSummaryRepository;
import com.carenest.repository.observation.CriteriaOptionRepository;
import com.carenest.repository.observation.ObservationCriteriaRepository;
import com.carenest.repository.observation.ObservationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.knowledge.KnowledgeService;
import com.carenest.service.observation.ChildSummaryService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AiDocumentType;
import com.carenest.utils.enums.SummaryPeriodType;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Luồng nhận xét: facts (code) → ẩn danh → RAG → AI nháp → guard → lưu DRAFT → GV sửa → GV duyệt (ADR-0008).
 * createDraft không mở transaction để không giữ kết nối DB trong lúc chờ AI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildSummaryServiceImpl implements ChildSummaryService {
    private static final String LOG_PREFIX = "[ChildSummaryServiceImpl]";

    static final long MAX_WEEK_DAYS = 6;
    static final long MAX_TERM_DAYS = 184;
    static final String CHILD_REF = "TRE-A";
    // Nguồn dữ liệu chưa có module: báo cho AI để không nhắc tới (cập nhật khi các module này có dữ liệu)
    static final List<String> DATA_NOT_AVAILABLE = List.of("attendance", "health", "meals");
    private static final Set<AiDocumentType> REFERENCE_TYPES =
            Set.of(AiDocumentType.CHILD_DEVELOPMENT_STANDARD, AiDocumentType.SCHOOL_SOP);

    private final ChildSummaryRepository summaryRepository;
    private final ObservationRepository observationRepository;
    private final ObservationCriteriaRepository criteriaRepository;
    private final CriteriaOptionRepository optionRepository;
    private final ChildAccessPolicy childAccessPolicy;
    private final ChildService childService;
    private final EnrollmentService enrollmentService;
    private final KnowledgeService knowledgeService;
    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final ChildSummaryMapper summaryMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public ChildSummaryResponse createDraft(CurrentUser currentUser, Long childId, CreateSummaryDraftRequest request) {
        String logPrefix = LOG_PREFIX + "|createDraft|userId=" + currentUser.userId() + "|childId=" + childId
                + "|period=" + request.periodType();
        log.info("{}|START", logPrefix);
        try {
            validatePeriod(request);
            childAccessPolicy.checkCanViewChild(currentUser, childId);
            ChildSummary existing = summaryRepository
                    .findByChildIdAndPeriodTypeAndPeriodFrom(childId, request.periodType(), request.periodFrom())
                    .orElse(null);
            if (existing != null) {
                log.info("{}|END|existing={}", logPrefix, existing.getId());
                return summaryMapper.toResponse(existing, null);
            }
            List<Observation> observations =
                    observationRepository.findByChildInPeriod(childId, request.periodFrom(), request.periodTo());
            if (observations.isEmpty()) {
                throw new GlobalException(
                        ApiCode.BAD_REQUEST, "AI_CONTEXT_INSUFFICIENT: no observations in this period");
            }

            DraftContext context = buildContext(childId, request, observations);
            ChildSummary summary;
            String aiErrorCode = null;
            try {
                SummaryDraftResult result = aiClient.draftChildSummary(context.input());
                summary = ChildSummary.fromAi(
                        childId,
                        request.periodType(),
                        request.periodFrom(),
                        request.periodTo(),
                        objectMapper.writeValueAsString(result.content()),
                        SummaryContentRenderer.fromDraft(result.content()),
                        result.provider(),
                        result.model(),
                        result.promptVersion(),
                        Instant.now(clock),
                        currentUser.userId());
            } catch (AiClientException ex) {
                // AI không dùng được: GV vẫn có bản template để sửa, nghiệp vụ không dừng (OBS-04)
                aiErrorCode = "AI_" + ex.getReason().name();
                summary = ChildSummary.fromTemplate(
                        childId,
                        request.periodType(),
                        request.periodFrom(),
                        request.periodTo(),
                        SummaryContentRenderer.fromCounts(
                                request.periodFrom(),
                                request.periodTo(),
                                observations.size(),
                                context.input().criteria()),
                        Instant.now(clock),
                        currentUser.userId());
            }
            ChildSummary saved = saveOrReturnExisting(summary);
            log.info(
                    "{}|END|summaryId={}|source={}|aiError={}",
                    logPrefix,
                    saved.getId(),
                    saved.getSource(),
                    aiErrorCode);
            return summaryMapper.toResponse(saved, saved == summary ? aiErrorCode : null);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChildSummaryResponse> listSummaries(CurrentUser currentUser, Long childId) {
        String logPrefix = LOG_PREFIX + "|listSummaries|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            childAccessPolicy.checkCanViewChild(currentUser, childId);
            List<ChildSummaryResponse> summaries =
                    summaryRepository.findByChildIdOrderByPeriodFromDesc(childId).stream()
                            .map(summary -> summaryMapper.toResponse(summary, null))
                            .toList();
            log.info("{}|END|count={}", logPrefix, summaries.size());
            return summaries;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ChildSummaryResponse updateContent(
            CurrentUser currentUser, Long summaryId, UpdateSummaryContentRequest request) {
        String logPrefix = LOG_PREFIX + "|updateContent|userId=" + currentUser.userId() + "|summaryId=" + summaryId;
        log.info("{}|START", logPrefix);
        try {
            ChildSummary summary = findDraftInScope(currentUser, summaryId);
            summary.editContent(request.content().strip());
            log.info("{}|END", logPrefix);
            return summaryMapper.toResponse(summary, null);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ChildSummaryResponse approve(CurrentUser currentUser, Long summaryId) {
        String logPrefix = LOG_PREFIX + "|approve|userId=" + currentUser.userId() + "|summaryId=" + summaryId;
        log.info("{}|START", logPrefix);
        try {
            ChildSummary summary = findDraftInScope(currentUser, summaryId);
            summary.approve(currentUser.userId(), Instant.now(clock));
            log.info("{}|END", logPrefix);
            return summaryMapper.toResponse(summary, null);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private DraftContext buildContext(Long childId, CreateSummaryDraftRequest request, List<Observation> observations) {
        Set<Long> classroomIds =
                observations.stream().map(Observation::getClassroomId).collect(Collectors.toSet());
        Set<Long> childIds = new HashSet<>();
        childIds.add(childId);
        classroomIds.forEach(
                classroomId -> childIds.addAll(enrollmentService.findActiveChildIds(classroomId, request.periodTo())));
        Map<Long, ChildIdentity> identities = childService.findIdentities(childIds).stream()
                .collect(Collectors.toMap(ChildIdentity::id, Function.identity()));
        ChildIdentity child = identities.get(childId);
        if (child == null) {
            throw new GlobalException(ApiCode.NOT_FOUND, "Child not found");
        }
        NotePseudonymizer pseudonymizer = new NotePseudonymizer(
                child.fullName(),
                identities.values().stream()
                        .filter(identity -> !identity.id().equals(childId))
                        .map(ChildIdentity::fullName)
                        .toList());

        List<ObservationCriteria> criteria = criteriaRepository.findAll();
        List<CriteriaOption> options = optionRepository.findByCriteriaIdInOrderBySortOrderAscIdAsc(
                criteria.stream().map(ObservationCriteria::getId).toList());
        SummaryFactsAggregator aggregator = new SummaryFactsAggregator(criteria, options);
        int ageMonths = SummaryFactsAggregator.ageInMonths(child.dateOfBirth(), request.periodTo());
        List<CriteriaFact> criteriaFacts = aggregator.criteriaFacts(observations);

        List<ReferenceSnippet> references = knowledgeService
                .retrieve(new RetrievalQuery(
                        ragQueryText(ageMonths, aggregator.lowScoringCriteriaNames(observations), criteriaFacts),
                        REFERENCE_TYPES,
                        aiProperties.rag().region(),
                        request.periodTo()))
                .stream()
                .map(chunk -> new ReferenceSnippet(chunk.chunkId(), chunk.title(), chunk.content()))
                .toList();

        SummaryDraftInput input = new SummaryDraftInput(
                CHILD_REF,
                ageMonths,
                request.periodType().name(),
                request.periodFrom(),
                request.periodTo(),
                observations.size(),
                criteriaFacts,
                request.periodType() == SummaryPeriodType.TERM ? aggregator.weeklyFacts(observations) : List.of(),
                SummaryFactsAggregator.noteFacts(observations, pseudonymizer),
                DATA_NOT_AVAILABLE,
                references);
        return new DraftContext(input);
    }

    // Truy vấn RAG chỉ gồm nhóm tuổi + tên tiêu chí (không có ghi chú/tên trẻ) vì được gửi tới embedding API
    static String ragQueryText(int ageMonths, List<String> lowCriteria, List<CriteriaFact> criteriaFacts) {
        List<String> focus = lowCriteria.isEmpty()
                ? criteriaFacts.stream().map(CriteriaFact::name).toList()
                : lowCriteria;
        return "Chỉ số phát triển và cách nhận xét trẻ " + SummaryFactsAggregator.ageGroupLabel(ageMonths) + ": "
                + String.join(", ", focus);
    }

    private ChildSummary saveOrReturnExisting(ChildSummary summary) {
        try {
            return summaryRepository.save(summary);
        } catch (DataIntegrityViolationException ex) {
            // Hai yêu cầu tạo nháp cùng kỳ chạy song song: trả bản đã lưu trước
            return summaryRepository
                    .findByChildIdAndPeriodTypeAndPeriodFrom(
                            summary.getChildId(), summary.getPeriodType(), summary.getPeriodFrom())
                    .orElseThrow(() -> ex);
        }
    }

    // Id không tồn tại và id ngoài phạm vi cùng trả 403 (không lộ id để dò)
    private ChildSummary findDraftInScope(CurrentUser currentUser, Long summaryId) {
        ChildSummary summary = summaryRepository
                .findById(summaryId)
                .filter(found -> childAccessPolicy.canViewChild(currentUser, found.getChildId()))
                .orElseThrow(() -> new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this summary"));
        if (summary.isApproved()) {
            throw new GlobalException(ApiCode.CONFLICT, "Summary is already approved");
        }
        return summary;
    }

    private static void validatePeriod(CreateSummaryDraftRequest request) {
        long days = ChronoUnit.DAYS.between(request.periodFrom(), request.periodTo());
        long maxDays = request.periodType() == SummaryPeriodType.WEEK ? MAX_WEEK_DAYS : MAX_TERM_DAYS;
        if (days < 0 || days > maxDays) {
            throw new GlobalException(ApiCode.BAD_REQUEST, "Invalid period for " + request.periodType());
        }
    }

    private record DraftContext(SummaryDraftInput input) {}
}
