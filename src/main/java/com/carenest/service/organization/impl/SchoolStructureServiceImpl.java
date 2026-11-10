package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.CampusOptionResponse;
import com.carenest.dto.organization.ClassroomResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.organization.Campus;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.mapper.organization.StaffAssignmentMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.organization.SchoolStructureService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final CampusRepository campusRepository;
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
    public Map<Long, String> findCampusNames(Collection<Long> campusIds) {
        String logPrefix = LOG_PREFIX + "|findCampusNames|count=" + campusIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, String> names = campusRepository.findAllById(campusIds).stream()
                    .collect(Collectors.toMap(Campus::getId, Campus::getName));
            log.info("{}|END", logPrefix);
            return names;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<CampusOptionResponse> listCampuses(CurrentUser actor) {
        String logPrefix = LOG_PREFIX + "|listCampuses|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            List<Campus> visible;
            if (staffAccessPolicy.seesAllCampuses(actor)) {
                visible = campusRepository.findAllByOrderByMainDescCodeAsc();
            } else {
                Set<Long> managed = staffAccessPolicy.managedCampusIds(actor);
                visible = managed.isEmpty() ? List.of() : campusRepository.findAllByIdInOrderByMainDescCodeAsc(managed);
            }
            List<CampusOptionResponse> campuses = visible.stream()
                    .map(staffAssignmentMapper::toCampusOptionResponse)
                    .toList();
            log.info("{}|END", logPrefix);
            return campuses;
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
