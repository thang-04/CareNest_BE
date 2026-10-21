package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.service.organization.SchoolStructureService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolStructureServiceImpl implements SchoolStructureService {
    private static final String LOG_PREFIX = "[SchoolStructureServiceImpl]";

    private final SchoolYearRepository schoolYearRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final SchoolStructureMapper schoolStructureMapper;

    @Override
    public List<SchoolYearResponse> findSchoolYears() {
        String logPrefix = LOG_PREFIX + "|findSchoolYears";
        log.info("{}|START", logPrefix);
        try {
            List<SchoolYearResponse> schoolYears = schoolYearRepository.findAllByOrderByStartDateDesc().stream()
                    .map(schoolStructureMapper::toResponse)
                    .toList();
            log.info("{}|END", logPrefix);
            return schoolYears;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Optional<SchoolYearResponse> findSchoolYear(Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|findSchoolYear|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Optional<SchoolYearResponse> schoolYear =
                    schoolYearRepository.findById(schoolYearId).map(schoolStructureMapper::toResponse);
            log.info("{}|END", logPrefix);
            return schoolYear;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<AgeGroupResponse> findAgeGroups() {
        String logPrefix = LOG_PREFIX + "|findAgeGroups";
        log.info("{}|START", logPrefix);
        try {
            List<AgeGroupResponse> ageGroups = ageGroupRepository.findAllByOrderBySortOrderAsc().stream()
                    .map(schoolStructureMapper::toResponse)
                    .toList();
            log.info("{}|END", logPrefix);
            return ageGroups;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Optional<AgeGroupResponse> findAgeGroup(Long ageGroupId) {
        String logPrefix = LOG_PREFIX + "|findAgeGroup|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            Optional<AgeGroupResponse> ageGroup =
                    ageGroupRepository.findById(ageGroupId).map(schoolStructureMapper::toResponse);
            log.info("{}|END", logPrefix);
            return ageGroup;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
