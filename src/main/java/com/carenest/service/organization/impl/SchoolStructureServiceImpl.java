package com.carenest.service.organization.impl;

import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.ClassroomResponse;
import com.carenest.entity.organization.Campus;
import com.carenest.mapper.organization.StaffAssignmentMapper;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.organization.SchoolStructureService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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

    private final CampusRepository campusRepository;
    private final ClassroomRepository classroomRepository;
    private final StaffAccessPolicy staffAccessPolicy;
    private final StaffAssignmentMapper staffAssignmentMapper;
    private final Clock clock;

    @Override
    public List<CampusResponse> listCampuses(CurrentUser actor) {
        String logPrefix = LOG_PREFIX + "|listCampuses|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            List<Campus> visible;
            if (staffAccessPolicy.seesAllCampuses(actor)) {
                visible = campusRepository.findAllByOrderByCodeAsc();
            } else {
                Set<Long> managed = staffAccessPolicy.managedCampusIds(actor);
                visible = managed.isEmpty() ? List.of() : campusRepository.findByIdInOrderByCodeAsc(managed);
            }
            List<CampusResponse> campuses = visible.stream()
                    .map(staffAssignmentMapper::toCampusResponse)
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
