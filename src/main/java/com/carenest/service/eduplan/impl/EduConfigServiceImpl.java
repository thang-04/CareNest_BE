package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.dto.eduplan.ExpectedResultResponse;
import com.carenest.entity.eduplan.EduApprovalStep;
import com.carenest.mapper.eduplan.EduYearPlanMapper;
import com.carenest.repository.eduplan.EduApprovalStepRepository;
import com.carenest.repository.eduplan.EduDomainRepository;
import com.carenest.repository.eduplan.EduExpectedResultRepository;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.service.organization.SchoolStructureService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EduConfigServiceImpl implements EduConfigService {
    private static final String LOG_PREFIX = "[EduConfigServiceImpl]";
    private static final String DOC_TYPE_YEAR = "year";

    private final EduDomainRepository domainRepository;
    private final EduApprovalStepRepository approvalStepRepository;
    private final EduExpectedResultRepository expectedResultRepository;
    private final SchoolStructureService schoolStructureService;
    private final EduYearPlanMapper mapper;

    @Override
    public EduConfigResponse getEduConfig() {
        String logPrefix = LOG_PREFIX + "|getEduConfig";
        log.info("{}|START", logPrefix);
        try {
            EduConfigResponse config = new EduConfigResponse(
                    domainRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                            .map(mapper::toConfigDomain)
                            .toList(),
                    approvalStepRepository.findAllByDocTypeOrderByStepOrderAsc(DOC_TYPE_YEAR).stream()
                            .map(EduApprovalStep::getApproverRole)
                            .toList(),
                    schoolStructureService.findSchoolYears(),
                    schoolStructureService.findAgeGroups());
            log.info("{}|END", logPrefix);
            return config;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<ExpectedResultResponse> getExpectedResults(Long ageGroupId) {
        String logPrefix = LOG_PREFIX + "|getExpectedResults|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            List<ExpectedResultResponse> results =
                    expectedResultRepository.findAllByAgeGroupIdOrderByDomainIdAscSortOrderAsc(ageGroupId).stream()
                            .map(mapper::toExpectedResult)
                            .toList();
            log.info("{}|END", logPrefix);
            return results;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
