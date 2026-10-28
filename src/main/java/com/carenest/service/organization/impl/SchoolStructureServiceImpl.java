package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.ClassroomResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.mapper.organization.StaffAssignmentMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.organization.SchoolStructureService;
import java.time.Clock;
import java.time.LocalDate;
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
    private final ClassroomRepository classroomRepository;
    private final StaffAccessPolicy staffAccessPolicy;
    private final StaffAssignmentMapper staffAssignmentMapper;
    private final Clock clock;

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

    @Override
    public List<ClassroomResponse> listCurrentClassrooms(CurrentUser actor, Long campusId) {
        String logPrefix = LOG_PREFIX + "|listCurrentClassrooms|userId=" + actor.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            // Kiểm phạm vi trước khi đọc để campus ngoài phạm vi và campus không tồn tại cùng 403
            staffAccessPolicy.checkCanViewCampus(actor, campusId);
            List<ClassroomResponse> classrooms =
                    classroomRepository.findCurrentByCampusId(campusId, LocalDate.now(clock)).stream()
                            .map(staffAssignmentMapper::toClassroomResponse)
                            .toList();
            log.info("{}|END", logPrefix);
            return classrooms;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
