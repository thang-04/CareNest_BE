package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.YearSetupResponse;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.Campus;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.Room;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.AttendanceCutoffRepository;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.service.organization.YearSetupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
public class YearSetupServiceImpl implements YearSetupService {
    private static final String LOG_PREFIX = "[YearSetupServiceImpl]";

    private final SchoolYearRepository schoolYearRepository;
    private final ClassroomRepository classroomRepository;
    private final RoomRepository roomRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final CampusRepository campusRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final AttendanceCutoffRepository attendanceCutoffRepository;
    private final SchoolConfigAccess schoolConfigAccess;
    private final SchoolStructureMapper schoolStructureMapper;

    @Override
    public YearSetupResponse getYearSetup(Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getYearSetup|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            SchoolYear year = schoolYearRepository
                    .findById(schoolYearId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
            SchoolYear source = schoolYearRepository
                    .findFirstByStatus(SchoolYearStatus.ACTIVE)
                    .filter(active -> !active.getId().equals(year.getId()))
                    .orElse(null);
            LocalDate today = schoolConfigAccess.today();
            List<AgeGroupDetailResponse> ageGroups = ageGroupRepository.findAll().stream()
                    .sorted(Comparator.comparing((AgeGroup g) ->
                                    g.getAgeFromMonths() == null ? Short.MAX_VALUE : g.getAgeFromMonths())
                            .thenComparing(AgeGroup::getSortOrder))
                    .map(schoolStructureMapper::toDetailResponse)
                    .toList();
            List<Campus> campuses = campusRepository.findAllByOrderByMainDescCodeAsc();
            LocalDate leaderFrom = today.isAfter(year.getStartDate()) ? today : year.getStartDate();
            // Năm sắp tới: PHT phụ trách tính tại ngày bắt đầu năm học (phân công cho năm mới bắt đầu từ ngày đó)
            Set<Long> campusesWithVicePrincipal = campusRepository.findActiveCampusStaff(leaderFrom).stream()
                    .filter(s -> StaffRole.VICE_PRINCIPAL.name().equals(s.getStaffRole()))
                    .map(CampusRepository.CampusStaff::getCampusId)
                    .collect(Collectors.toSet());
            Set<Long> ageGroupsWithLeader =
                    staffAssignmentRepository
                            .findCurrentTeamLeaders(year.getId(), year.getEndDate(), leaderFrom)
                            .stream()
                            .map(StaffAssignment::getAgeGroupId)
                            .collect(Collectors.toSet());
            YearSetupResponse response = new YearSetupResponse(
                    toYear(year),
                    source == null ? null : toYear(source),
                    setupClasses(year.getId(), today),
                    source == null ? List.of() : setupClasses(source.getId(), today),
                    ageGroups,
                    campuses.stream()
                            .map(c -> new YearSetupResponse.CampusBrief(c.getId(), c.getShortName()))
                            .toList(),
                    campuses.stream()
                            .map(Campus::getId)
                            .filter(id -> !campusesWithVicePrincipal.contains(id))
                            .toList(),
                    ageGroupsWithLeader,
                    !attendanceCutoffRepository
                            .findAllBySchoolYearIdOrderByIdDesc(year.getId())
                            .isEmpty());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<YearSetupResponse.SetupClass> setupClasses(Long schoolYearId, LocalDate today) {
        List<Classroom> classes = classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(schoolYearId);
        if (classes.isEmpty()) {
            return List.of();
        }
        Map<Long, Room> rooms = roomRepository
                .findAllById(classes.stream()
                        .map(Classroom::getRoomId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));
        Map<Long, List<StaffAssignment>> teachers =
                staffAssignmentRepository
                        .findCurrentTeachers(
                                classes.stream().map(Classroom::getId).toList(), today)
                        .stream()
                        .collect(Collectors.groupingBy(StaffAssignment::getClassroomId));
        return classes.stream()
                .map(c -> {
                    Room room = c.getRoomId() == null ? null : rooms.get(c.getRoomId());
                    List<StaffAssignment> rows = teachers.getOrDefault(c.getId(), List.of());
                    return new YearSetupResponse.SetupClass(
                            c.getId(),
                            c.getName(),
                            c.getCampusId(),
                            c.getAgeGroupId(),
                            c.getCapacity() == null ? null : c.getCapacity().intValue(),
                            c.getRoomId(),
                            room == null || room.getCapacity() == null
                                    ? null
                                    : room.getCapacity().intValue(),
                            (int) rows.stream()
                                    .map(StaffAssignment::getUserId)
                                    .distinct()
                                    .count(),
                            rows.stream().anyMatch(StaffAssignment::isHomeroom));
                })
                .toList();
    }

    private static YearSetupResponse.Year toYear(SchoolYear year) {
        return new YearSetupResponse.Year(
                year.getId(), year.getName(), year.getStatus(), year.getStartDate(), year.getEndDate());
    }
}
