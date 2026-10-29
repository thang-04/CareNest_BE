package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.CurriculumContentResponse;
import com.carenest.dto.eduplan.CurriculumGoalRef;
import com.carenest.dto.eduplan.CurriculumGoalResponse;
import com.carenest.dto.eduplan.CurriculumSourceResponse;
import com.carenest.entity.eduplan.EduCurriculumGoal;
import com.carenest.entity.eduplan.EduCurriculumGoalContent;
import com.carenest.entity.eduplan.EduDomain;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduCurriculumContentRepository;
import com.carenest.repository.eduplan.EduCurriculumGoalContentRepository;
import com.carenest.repository.eduplan.EduCurriculumGoalRepository;
import com.carenest.repository.eduplan.EduCurriculumSourceRepository;
import com.carenest.repository.eduplan.EduDomainRepository;
import com.carenest.service.eduplan.EduCurriculumService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EduCurriculumServiceImpl implements EduCurriculumService {
    private static final String LOG_PREFIX = "[EduCurriculumServiceImpl]";

    private final EduCurriculumSourceRepository sourceRepository;
    private final EduCurriculumGoalRepository goalRepository;
    private final EduCurriculumGoalContentRepository goalContentRepository;
    private final EduCurriculumContentRepository contentRepository;
    private final EduDomainRepository domainRepository;
    private final SchoolStructureService schoolStructureService;

    @Override
    public List<CurriculumSourceResponse> getSources() {
        String logPrefix = LOG_PREFIX + "|getSources";
        log.info("{}|START", logPrefix);
        try {
            List<CurriculumSourceResponse> sources =
                    sourceRepository.findAllByActiveTrueOrderByIssuedDateDesc().stream()
                            .map(s -> new CurriculumSourceResponse(
                                    s.getId(),
                                    s.getCode(),
                                    s.getTitle(),
                                    s.getDocumentNo(),
                                    s.getIssuedDate(),
                                    s.getIssuer(),
                                    s.getOfficialUrl(),
                                    s.getRetrievedFromUrl(),
                                    s.getRetrievedAt(),
                                    s.getScopeNote()))
                            .toList();
            log.info("{}|END", logPrefix);
            return sources;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<CurriculumGoalResponse> getGoals(Long ageGroupId) {
        String logPrefix = LOG_PREFIX + "|getGoals|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            requireAgeGroup(ageGroupId);
            List<EduCurriculumGoal> goals =
                    goalRepository.findAllByAgeGroupIdOrderBySourceIdAscSortOrderAsc(ageGroupId);
            Map<Long, List<String>> contents = goals.isEmpty()
                    ? Map.of()
                    : goalContentRepository
                            .findAllByCurriculumGoalIdInOrderByCurriculumGoalIdAscSortOrderAsc(
                                    goals.stream().map(EduCurriculumGoal::getId).toList())
                            .stream()
                            .collect(Collectors.groupingBy(
                                    EduCurriculumGoalContent::getCurriculumGoalId,
                                    Collectors.mapping(EduCurriculumGoalContent::getText, Collectors.toList())));
            Map<Long, String> domainNames = domainNames();
            List<CurriculumGoalResponse> result = goals.stream()
                    .map(g -> new CurriculumGoalResponse(
                            g.getId(),
                            g.getSourceId(),
                            g.getDomainId(),
                            domainNames.get(g.getDomainId()),
                            g.getSubDomain(),
                            g.getGroupNo(),
                            g.getGroupText(),
                            g.getGoalNo(),
                            g.getGoalText(),
                            g.getSectionRef(),
                            g.getSortOrder(),
                            contents.getOrDefault(g.getId(), List.of())))
                    .toList();
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<CurriculumContentResponse> getContents(Long ageGroupId) {
        String logPrefix = LOG_PREFIX + "|getContents|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            requireAgeGroup(ageGroupId);
            Map<Long, String> domainNames = domainNames();
            List<CurriculumContentResponse> result =
                    contentRepository.findAllByAgeGroupIdOrderBySourceIdAscSortOrderAsc(ageGroupId).stream()
                            .map(c -> new CurriculumContentResponse(
                                    c.getId(),
                                    c.getSourceId(),
                                    c.getDomainId(),
                                    domainNames.get(c.getDomainId()),
                                    c.getSubDomain(),
                                    c.getCategoryNo(),
                                    c.getCategory(),
                                    c.getLabel(),
                                    c.getText(),
                                    c.getSectionRef(),
                                    c.getSortOrder()))
                            .toList();
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Map<Long, CurriculumGoalRef> findGoalRefs(Collection<Long> ids) {
        String logPrefix = LOG_PREFIX + "|findGoalRefs|count=" + ids.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, CurriculumGoalRef> refs = ids.isEmpty()
                    ? Map.of()
                    : goalRepository.findAllById(ids).stream()
                            .map(g -> new CurriculumGoalRef(
                                    g.getId(), g.getAgeGroupId(), g.getGoalText(), g.getSectionRef()))
                            .collect(Collectors.toMap(CurriculumGoalRef::id, Function.identity()));
            log.info("{}|END", logPrefix);
            return refs;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void requireAgeGroup(Long ageGroupId) {
        if (ageGroupId == null
                || schoolStructureService.findAgeGroup(ageGroupId).isEmpty()) {
            throw GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND);
        }
    }

    private Map<Long, String> domainNames() {
        return domainRepository.findAllByOrderBySortOrderAsc().stream()
                .collect(Collectors.toMap(EduDomain::getId, EduDomain::getName));
    }
}
