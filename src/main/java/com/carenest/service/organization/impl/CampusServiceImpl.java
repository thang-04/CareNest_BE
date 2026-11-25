package com.carenest.service.organization.impl;

import com.carenest.dto.organization.CampusDetailResponse;
import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.StaffBriefResponse;
import com.carenest.entity.organization.Campus;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.CampusMapper;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.organization.CampusService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.DisplayNames;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.RoomType;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CampusServiceImpl implements CampusService {
    private static final String LOG_PREFIX = "[CampusServiceImpl]";
    private static final Map<String, String> STAFF_LABELS = Map.of(
            "VICE_PRINCIPAL",
            "PHT",
            "TEACHER",
            "GV",
            "KITCHEN_STAFF",
            "nhân viên bếp",
            "AGE_GROUP_TEAM_LEADER",
            "tổ trưởng");

    private final CampusRepository campusRepository;
    private final ClassroomRepository classroomRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final RoomRepository roomRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final SchoolStructureMapper schoolStructureMapper;
    private final StaffAssignmentService staffAssignmentService;
    private final CampusMapper campusMapper;
    private final YearSnapshotReader yearSnapshotReader;
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CampusResponse> getCampuses(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getCampuses|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            // AUTH-20: HT và PHT còn phân công xem mọi điểm trường
            List<Campus> campuses = campusAccessPolicy.canReadAllCampuses(currentUser)
                    ? campusRepository.findAllByOrderByMainDescCodeAsc()
                    : List.of();
            Long yearId = resolveSchoolYearId(schoolYearId);
            Map<Long, Long> classCounts =
                    yearId == null ? Map.of() : toMap(campusRepository.countClassesByCampus(yearId));
            Map<Long, Long> childCounts = yearId == null
                    ? Map.of()
                    : toMap(campusRepository.countChildrenByCampus(yearId, countDate(yearId, today)));
            Map<Long, List<StaffBriefResponse>> vicePrincipals = campusStaff(today, StaffRole.VICE_PRINCIPAL);
            Map<Long, SchoolYearRepository.CampusSnapshot> snapshot = campusSnapshot(yearId);
            // V151: chỉ điểm trường thuộc năm đang xem.
            Set<Long> yearCampuses = yearCampusIds(yearId);
            Map<Long, CampusRepository.YearCampusInfo> yearInfo = yearInfo(yearId, snapshot);
            Long yearMainId = yearId == null ? null : yearMainId(yearId);
            List<CampusResponse> response = campuses.stream()
                    .filter(c -> yearCampuses == null || yearCampuses.contains(c.getId()))
                    // Năm đã kết thúc: chỉ điểm trường có lúc kết thúc năm, thông tin như bản chụp
                    .filter(c -> snapshot == null || snapshot.containsKey(c.getId()))
                    .map(c -> withSnapshot(
                                    withYearInfo(
                                            campusMapper.toResponse(
                                                    c,
                                                    classCounts.getOrDefault(c.getId(), 0L),
                                                    childCounts.getOrDefault(c.getId(), 0L),
                                                    vicePrincipals.getOrDefault(c.getId(), List.of())),
                                            yearInfo),
                                    snapshot)
                            .withDeleteBlockedReason(deleteBlockedReason(c, yearId, yearMainId)))
                    // Điểm trường chính của năm đang xem đứng đầu
                    .sorted(Comparator.comparing((CampusResponse r) -> !r.isMain()))
                    .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CampusDetailResponse getCampusDetail(CurrentUser currentUser, Long campusId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getCampusDetail|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Campus campus = findCampus(campusId);
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            Long yearId = resolveSchoolYearId(schoolYearId);
            if (yearId != null && !yearCampusIds(yearId).contains(campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_NOT_IN_YEAR);
            }
            // CFG-YR-02: năm đã qua ngày kết thúc ⇒ trẻ, GV tính tại ngày kết thúc năm
            LocalDate countDate = yearId == null ? today : countDate(yearId, today);
            List<Classroom> yearClasses = yearId == null
                    ? List.of()
                    : classroomRepository.findAllByCampusIdAndSchoolYearIdOrderByNameAsc(campusId, yearId);
            Map<Long, Long> classChildren = yearClasses.isEmpty()
                    ? Map.of()
                    : classroomRepository
                            .countActiveChildren(
                                    yearClasses.stream().map(Classroom::getId).toList(), countDate)
                            .stream()
                            .collect(Collectors.toMap(
                                    ClassroomRepository.ClassroomCount::getClassroomId,
                                    ClassroomRepository.ClassroomCount::getTotal));
            // Chủ nhiệm từng lớp (kèm tên, để năm đã kết thúc vẫn hiện đúng người)
            Map<Long, Long> homerooms = yearClasses.isEmpty()
                    ? Map.of()
                    : staffAssignmentRepository
                            .findCurrentTeachers(
                                    yearClasses.stream().map(Classroom::getId).toList(), countDate)
                            .stream()
                            .filter(StaffAssignment::isHomeroom)
                            .collect(Collectors.toMap(
                                    StaffAssignment::getClassroomId, StaffAssignment::getUserId, (a, b) -> a));
            Map<Long, String> homeroomNames = homerooms.isEmpty()
                    ? Map.of()
                    : roomRepository.findUserNames(Set.copyOf(homerooms.values())).stream()
                            .collect(Collectors.toMap(
                                    RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
            List<CampusDetailResponse.ClassBrief> classes = yearClasses.stream()
                    .map(c -> new CampusDetailResponse.ClassBrief(
                            c.getId(),
                            c.getName(),
                            c.getAgeGroupId(),
                            c.getCapacity() == null ? null : c.getCapacity().intValue(),
                            c.getRoomId(),
                            classChildren.getOrDefault(c.getId(), 0L),
                            homerooms.get(c.getId()),
                            homerooms.containsKey(c.getId()) ? homeroomNames.get(homerooms.get(c.getId())) : null))
                    .toList();
            List<CampusDetailResponse.KitchenRoom> kitchenRooms = kitchenRooms(yearId, campusId);
            long childCount = yearId == null
                    ? 0
                    : toMap(campusRepository.countChildrenByCampus(yearId, countDate))
                            .getOrDefault(campusId, 0L);
            List<StaffBriefResponse> vicePrincipals =
                    campusStaff(today, StaffRole.VICE_PRINCIPAL).getOrDefault(campusId, List.of());
            List<StaffBriefResponse> kitchenStaff =
                    campusStaff(today, StaffRole.KITCHEN_STAFF).getOrDefault(campusId, List.of());
            List<StaffBriefResponse> teachers = yearId == null
                    ? List.of()
                    : campusRepository.findActiveClassroomStaff(campusId, yearId, countDate).stream()
                            .filter(s -> StaffRole.TEACHER.name().equals(s.getStaffRole()))
                            .map(s -> new StaffBriefResponse(s.getUserId(), s.getFullName()))
                            .distinct()
                            .toList();
            CampusDetailResponse response = new CampusDetailResponse(
                    withSnapshot(
                            withYearInfo(
                                    campusMapper.toResponse(campus, classes.size(), childCount, vicePrincipals),
                                    yearInfo(yearId, campusSnapshot(yearId))),
                            campusSnapshot(yearId)),
                    yearId,
                    classes,
                    yearSnapshotReader.yearAgeGroups(yearId),
                    vicePrincipals,
                    teachers,
                    new CampusDetailResponse.Kitchen(kitchenRooms, kitchenStaff));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CampusResponse createCampus(CurrentUser currentUser, CampusRequest request, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|createCampus|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            Long yearId = openYearId(schoolYearId);
            CampusRequest data = normalize(request);
            if (campusRepository.existsByCodeIgnoreCase(data.code())) {
                throw GlobalException.of(ApiCode.CAMPUS_CODE_EXISTS);
            }
            if (campusRepository.existsByNameFolded(data.name())) {
                throw GlobalException.of(ApiCode.CAMPUS_NAME_EXISTS);
            }
            if (campusRepository.existsShortName(data.shortName(), 0L)) {
                throw GlobalException.of(ApiCode.CAMPUS_SHORT_NAME_EXISTS);
            }
            Campus campus = Campus.create(data.code(), data.name(), data.shortName(), data.address(), data.phone());
            // Mỗi năm có đúng một điểm trường chính (V180); điểm trường đầu tiên của năm luôn là điểm chính
            boolean hasMain =
                    yearId == null ? campusRepository.findFirstByMainTrue().isPresent() : yearMainId(yearId) != null;
            campus = campusRepository.save(campus);
            // Cần chốt 22/10/2026: thêm ở năm nào chỉ năm đó có; năm tạo sau chép từ năm này
            if (yearId != null) {
                campusRepository.addToYear(yearId, campus.getId());
            }
            if (Boolean.TRUE.equals(data.isMain()) || !hasMain) {
                markMain(campus, yearId);
            }
            log.info("{}|campusId={}|END", logPrefix, campus.getId());
            return campusMapper.toResponse(campus, 0, 0, List.of());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CampusResponse updateCampus(
            CurrentUser currentUser, Long campusId, CampusRequest request, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|updateCampus|userId=" + currentUser.userId() + "|campusId=" + campusId
                + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            Campus campus = findCampus(campusId);
            CampusRequest data = normalize(request);
            Long yearId = openYearId(schoolYearId);
            boolean inYear = yearId != null
                    && Set.copyOf(campusRepository.findYearCampusIds(yearId)).contains(campusId);
            if (schoolYearId != null && !inYear) {
                throw GlobalException.of(ApiCode.CAMPUS_NOT_IN_YEAR);
            }
            // Mã, tên và tên ngắn là định danh toàn trường, kể cả điểm trường đang ngừng hoạt động.
            // Không cho trùng trong bản thiết lập năm học, vì khi kích hoạt lại sẽ không thể phân biệt.
            if (campusRepository.existsByCodeIgnoreCaseAndIdNot(data.code(), campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_CODE_EXISTS);
            }
            if (campusRepository.existsByNameFoldedAndIdNot(data.name(), campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_NAME_EXISTS);
            }
            if (campusRepository.existsShortName(data.shortName(), campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_SHORT_NAME_EXISTS);
            }
            // Cần chốt 22/10/2026: sửa ở năm nào chỉ năm đó đổi. Các snapshot trong cùng
            // năm cũng phải khác nhau, vì đây là dữ liệu người dùng nhìn thấy ở năm đó.
            if (inYear) {
                if (campusRepository.existsCodeInYear(yearId, campusId, data.code())) {
                    throw GlobalException.of(ApiCode.CAMPUS_CODE_EXISTS);
                }
                if (campusRepository.existsNameInYear(yearId, campusId, data.name())) {
                    throw GlobalException.of(ApiCode.CAMPUS_NAME_EXISTS);
                }
                if (campusRepository.existsShortNameInYear(yearId, campusId, data.shortName())) {
                    throw GlobalException.of(ApiCode.CAMPUS_SHORT_NAME_EXISTS);
                }
                campusRepository.updateYearInfo(
                        yearId, campusId, data.code(), data.name(), data.shortName(), data.address(), data.phone());
            }
            boolean main = inYear ? campusId.equals(yearMainId(yearId)) : campus.isMain();
            if (main && Boolean.FALSE.equals(data.isMain())) {
                throw GlobalException.of(ApiCode.MAIN_CAMPUS_REQUIRED);
            }
            // Thông tin chung (module khác đọc) theo năm đang hoạt động; sửa ở năm sắp diễn ra không đổi năm này
            boolean activeYear = yearId == null
                    || schoolYearRepository
                            .findById(yearId)
                            .map(y -> y.getStatus() == SchoolYearStatus.ACTIVE)
                            .orElse(true);
            if (!inYear || activeYear) {
                campus.changeInfo(data.code(), data.name(), data.shortName(), data.address(), data.phone());
            }
            // Đổi điểm chính ở năm nào chỉ năm đó đổi; bảng chung theo năm đang hoạt động
            if (Boolean.TRUE.equals(data.isMain()) && !main) {
                markMain(campus, inYear ? yearId : null);
            }
            campusRepository.flush();
            CampusResponse response = withYearInfo(toResponseWithStats(campus), yearInfo(yearId, null));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteCampus(CurrentUser currentUser, Long campusId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|deleteCampus|userId=" + currentUser.userId() + "|campusId=" + campusId
                + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            Campus campus = findCampus(campusId);
            Long yearId = openYearId(schoolYearId);
            if (campus.isMain() || (yearId != null && campusId.equals(yearMainId(yearId)))) {
                throw GlobalException.of(ApiCode.MAIN_CAMPUS_NOT_DELETABLE);
            }
            if (schoolYearId != null
                    && (yearId == null || !yearCampusIds(yearId).contains(campusId))) {
                throw GlobalException.of(ApiCode.CAMPUS_NOT_IN_YEAR);
            }
            // Điểm trường còn ở năm khác: chỉ bỏ khỏi năm đang chọn (năm khác không đổi)
            if (yearId != null && usedInOtherYears(campusId, yearId)) {
                String yearBlockers = yearBlockers(campusId, yearId);
                if (yearBlockers != null) {
                    throw GlobalException.of(ApiCode.CAMPUS_NOT_EMPTY, campus.getName(), yearBlockers);
                }
                campusRepository.removeFromYear(yearId, campusId);
                log.info("{}|removedFromYear|END", logPrefix);
                return;
            }
            // Cần chốt 19/11/2026: chỉ xóa điểm trường chưa có dữ liệu; còn gì thì báo đủ để HT biết
            String blockers = dataBlockers(campusId);
            if (blockers != null) {
                throw GlobalException.of(ApiCode.CAMPUS_NOT_EMPTY, campus.getName(), blockers);
            }
            try {
                campusRepository.delete(campus);
                campusRepository.flush();
            } catch (DataIntegrityViolationException integrityEx) {
                // Bảng khác (phòng, bếp...) còn tham chiếu điểm trường này
                throw GlobalException.withCause(ApiCode.CAMPUS_HAS_DEPENDENTS, integrityEx, "dữ liệu liên quan");
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CampusResponse changeCampusStatus(
            CurrentUser currentUser, Long campusId, CampusStatus status, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|changeCampusStatus|userId=" + currentUser.userId() + "|campusId=" + campusId
                + "|status=" + status + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            Campus campus = findCampus(campusId);
            Long yearId = openYearId(schoolYearId);
            if (yearId == null) {
                CampusResponse response = changeCampusStatus(currentUser, campusId, status);
                log.info("{}|END", logPrefix);
                return response;
            }
            Map<Long, CampusRepository.YearCampusInfo> info = campusRepository.findYearCampusInfo(yearId).stream()
                    .collect(Collectors.toMap(CampusRepository.YearCampusInfo::getCampusId, i -> i));
            if (!info.containsKey(campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_NOT_IN_YEAR);
            }
            String current = info.get(campusId).getStatus();
            boolean activeNow = current == null
                    ? campus.isActive()
                    : CampusStatus.ACTIVE.name().equals(current);
            // Cần chốt 19/11/2026: ngừng / hoạt động lại theo năm; chặn như cũ nhưng xét lớp, PHT của năm đó
            if (status == CampusStatus.INACTIVE && activeNow) {
                if (campusId.equals(yearMainId(yearId))) {
                    throw GlobalException.of(ApiCode.MAIN_CAMPUS_NOT_DEACTIVATABLE);
                }
                if (campusRepository.countClassesInYear(campusId, yearId) > 0) {
                    throw GlobalException.of(ApiCode.CAMPUS_HAS_ACTIVE_CLASSES);
                }
                List<String> vicePrincipals = campusRepository.findYearVicePrincipalNames(campusId, yearId);
                if (!vicePrincipals.isEmpty()) {
                    throw GlobalException.of(ApiCode.CAMPUS_HAS_VICE_PRINCIPALS, String.join(", ", vicePrincipals));
                }
            }
            campusRepository.updateYearStatus(yearId, campusId, status.name());
            boolean activeYear = schoolYearRepository
                    .findById(yearId)
                    .map(y -> y.getStatus() == SchoolYearStatus.ACTIVE)
                    .orElse(false);
            // Năm đang hoạt động: bảng chung theo cùng (module khác đọc campuses.status)
            if (activeYear) {
                if (status == CampusStatus.INACTIVE) {
                    campus.deactivate(clock.instant());
                } else {
                    campus.activate();
                }
            }
            campusRepository.flush();
            CampusResponse response = withYearInfo(toResponseWithStats(campus), yearInfo(yearId, null));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CampusResponse changeCampusStatus(CurrentUser currentUser, Long campusId, CampusStatus status) {
        String logPrefix = LOG_PREFIX + "|changeCampusStatus|userId=" + currentUser.userId() + "|campusId=" + campusId
                + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            Campus campus = findCampus(campusId);
            if (status == CampusStatus.INACTIVE && campus.isActive()) {
                if (campus.isMain()) {
                    throw GlobalException.of(ApiCode.MAIN_CAMPUS_NOT_DEACTIVATABLE);
                }
                if (campusRepository.countOpenYearClasses(campusId) > 0) {
                    throw GlobalException.of(ApiCode.CAMPUS_HAS_ACTIVE_CLASSES);
                }
                // Còn PHT phụ trách (hôm nay hoặc sắp tới) ⇒ Hiệu trưởng phải gỡ phân công trước (Cần chốt 22/10)
                List<String> vicePrincipals =
                        staffAssignmentRepository.findOpenVicePrincipalNames(campusId, LocalDate.now(clock));
                if (!vicePrincipals.isEmpty()) {
                    throw GlobalException.of(ApiCode.CAMPUS_HAS_VICE_PRINCIPALS, String.join(", ", vicePrincipals));
                }
                campus.deactivate(clock.instant());
            } else if (status == CampusStatus.ACTIVE && !campus.isActive()) {
                campus.activate();
            }
            // Không kèm năm: năm đang hoạt động đổi theo bảng chung
            schoolYearRepository
                    .findFirstByStatus(SchoolYearStatus.ACTIVE)
                    .ifPresent(y -> campusRepository.updateYearStatus(
                            y.getId(), campusId, campus.getStatus().name()));
            campusRepository.flush();
            CampusResponse response = toResponseWithStats(campus);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Optional<CampusStatus> findCampusStatus(Long campusId) {
        String logPrefix = LOG_PREFIX + "|findCampusStatus|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            Optional<CampusStatus> status = campusRepository.findById(campusId).map(Campus::getStatus);
            log.info("{}|END", logPrefix);
            return status;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private boolean isPrincipal(CurrentUser currentUser, LocalDate today) {
        return currentUser.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(currentUser.userId(), today);
    }

    // AUTH-01: ghi cấu hình điểm trường chỉ Hiệu trưởng có phân công còn hiệu lực
    private void checkPrincipal(CurrentUser currentUser) {
        if (!isPrincipal(currentUser, LocalDate.now(clock))) {
            throw GlobalException.of(ApiCode.PRINCIPAL_ONLY, "quản lý điểm trường");
        }
    }

    // Không truyền năm học ⇒ dùng năm đang hoạt động; chưa có năm nào ⇒ số liệu theo năm bằng 0
    // CFG-YR-02: năm đã kết thúc ⇒ thông tin điểm trường theo bản chụp lúc kết thúc năm; null = năm còn mở
    private Map<Long, SchoolYearRepository.CampusSnapshot> campusSnapshot(Long yearId) {
        if (yearId == null
                || schoolYearRepository
                        .findById(yearId)
                        .map(y -> y.getStatus() != SchoolYearStatus.CLOSED)
                        .orElse(true)) {
            return null;
        }
        return schoolYearRepository.findCampusSnapshot(yearId).stream()
                .collect(Collectors.toMap(SchoolYearRepository.CampusSnapshot::getCampusId, s -> s));
    }

    private static CampusResponse withSnapshot(
            CampusResponse r, Map<Long, SchoolYearRepository.CampusSnapshot> snapshot) {
        SchoolYearRepository.CampusSnapshot s = snapshot == null ? null : snapshot.get(r.id());
        if (s == null) {
            return r;
        }
        return new CampusResponse(
                r.id(),
                s.getCode(),
                s.getName(),
                s.getShortName(),
                s.getAddress(),
                s.getPhone(),
                Boolean.TRUE.equals(s.getMain()),
                CampusStatus.valueOf(s.getStatus()),
                r.classCount(),
                r.childCount(),
                r.vicePrincipals());
    }

    private LocalDate countDate(Long yearId, LocalDate today) {
        return schoolYearRepository
                .findById(yearId)
                .map(SchoolYear::getEndDate)
                .filter(end -> end.isBefore(today))
                .orElse(today);
    }

    private Long resolveSchoolYearId(Long schoolYearId) {
        if (schoolYearId != null) {
            if (!schoolYearRepository.existsById(schoolYearId)) {
                throw GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND);
            }
            return schoolYearId;
        }
        return schoolYearRepository
                .findFirstByStatus(SchoolYearStatus.ACTIVE)
                .map(SchoolYear::getId)
                .orElse(null);
    }

    private Map<Long, List<StaffBriefResponse>> campusStaff(LocalDate today, StaffRole staffRole) {
        return campusRepository.findActiveCampusStaff(today).stream()
                .filter(s -> staffRole.name().equals(s.getStaffRole()))
                .collect(Collectors.groupingBy(
                        CampusRepository.CampusStaff::getCampusId,
                        Collectors.mapping(
                                s -> new StaffBriefResponse(s.getUserId(), s.getFullName()), Collectors.toList())));
    }

    private List<CampusDetailResponse.KitchenRoom> kitchenRooms(Long yearId, Long campusId) {
        if (yearId == null) {
            return List.of();
        }
        if (yearSnapshotReader.isClosed(yearId)) {
            return schoolYearRepository.findRoomSnapshot(yearId).stream()
                    .filter(r -> campusId.equals(r.getCampusId()))
                    .filter(r -> RoomType.KITCHEN.name().equals(r.getRoomType()))
                    .map(r -> new CampusDetailResponse.KitchenRoom(
                            r.getRoomId(),
                            r.getCode(),
                            r.getName(),
                            r.getCapacity() == null ? null : r.getCapacity().intValue(),
                            RoomStatus.valueOf(r.getStatus())))
                    .toList();
        }
        return roomRepository.findYearRoomInfo(yearId).stream()
                .filter(r -> campusId.equals(r.getCampusId()))
                .filter(r -> RoomType.KITCHEN.name().equals(r.getRoomType()))
                .map(r -> new CampusDetailResponse.KitchenRoom(
                        r.getRoomId(),
                        r.getCode(),
                        r.getName(),
                        r.getCapacity() == null ? null : r.getCapacity().intValue(),
                        RoomStatus.valueOf(r.getStatus())))
                .toList();
    }

    /** Lý do chưa xóa được điểm trường khỏi năm đang xem (để web giải thích); null = xóa được. */
    private String deleteBlockedReason(Campus campus, Long yearId, Long yearMainId) {
        if (campus.isMain() || campus.getId().equals(yearMainId)) {
            return "Điểm trường chính, không xóa được";
        }
        return yearId != null && usedInOtherYears(campus.getId(), yearId)
                ? yearBlockers(campus.getId(), yearId)
                : dataBlockers(campus.getId());
    }

    // Thông tin riêng của năm còn mở (V152); năm đã đóng dùng bản chụp
    private Map<Long, CampusRepository.YearCampusInfo> yearInfo(
            Long yearId, Map<Long, SchoolYearRepository.CampusSnapshot> snapshot) {
        if (yearId == null || snapshot != null) {
            return Map.of();
        }
        return campusRepository.findYearCampusInfo(yearId).stream()
                .filter(i -> i.getName() != null)
                .collect(Collectors.toMap(CampusRepository.YearCampusInfo::getCampusId, i -> i));
    }

    private static CampusResponse withYearInfo(CampusResponse r, Map<Long, CampusRepository.YearCampusInfo> info) {
        CampusRepository.YearCampusInfo i = info.get(r.id());
        if (i == null) {
            return r;
        }
        return new CampusResponse(
                r.id(),
                i.getCode(),
                i.getName(),
                i.getShortName(),
                i.getAddress(),
                i.getPhone(),
                // V180: điểm trường chính theo năm; năm chưa đặt ⇒ theo bảng chung
                info.values().stream().anyMatch(x -> Boolean.TRUE.equals(x.getMain()))
                        ? Boolean.TRUE.equals(i.getMain())
                        : r.isMain(),
                // V154: trạng thái theo năm
                i.getStatus() == null ? r.status() : CampusStatus.valueOf(i.getStatus()),
                r.classCount(),
                r.childCount(),
                r.vicePrincipals(),
                r.deleteBlockedReason());
    }

    // Danh sách điểm trường của năm (V151). Migration đã backfill các năm cũ;
    // tập rỗng là trạng thái hợp lệ, chỉ null khi không chọn năm.
    private Set<Long> yearCampusIds(Long yearId) {
        if (yearId == null) {
            return null;
        }
        return Set.copyOf(campusRepository.findYearCampusIds(yearId));
    }

    // Năm đã kết thúc thì chỉ xem (CFG-YR-01)
    private Long openYearId(Long schoolYearId) {
        Long yearId = resolveSchoolYearId(schoolYearId);
        if (yearId != null) {
            SchoolYearRules.checkOpen(schoolYearRepository.findById(yearId).orElseThrow());
        }
        return yearId;
    }

    private boolean usedInOtherYears(Long campusId, Long yearId) {
        Set<Long> inYear = yearCampusIds(yearId);
        long years = campusRepository.countYears(campusId);
        return inYear == null || inYear.contains(campusId) ? years > 1 : years > 0;
    }

    // Bỏ khỏi một năm: chặn khi năm đó còn lớp hoặc PHT phụ trách
    // Bỏ khỏi 1 năm: chặn khi năm đó còn lớp (kéo theo trẻ, điểm danh, đánh giá, sức khỏe), nhân sự, phòng, trẻ đã
    // tiếp nhận chưa vào lớp, sổ suất ăn / kho bếp; null = trống (BUG-43)
    private String yearBlockers(Long campusId, Long yearId) {
        List<String> parts = new ArrayList<>();
        long classes = campusRepository.countClassesInYear(campusId, yearId);
        if (classes > 0) {
            parts.add(classes + " lớp năm này");
        }
        List<String> admitted = campusRepository.findYearAdmissionNames(campusId, yearId);
        if (!admitted.isEmpty()) {
            parts.add(admitted.size() + " trẻ đã tiếp nhận (" + firstNames(admitted) + ")");
        }
        addStaff(parts, campusRepository.findYearStaffNames(campusId, yearId));
        long rooms = campusRepository.countYearRooms(campusId, yearId);
        if (rooms > 0) {
            parts.add(rooms + " phòng năm này");
        }
        long kitchen = campusRepository.countYearKitchenData(campusId, yearId);
        if (kitchen > 0) {
            parts.add("dữ liệu suất ăn, kho bếp năm này");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    // Nhân sự theo vai trò, mỗi vai trò tối đa 3 tên
    private static void addStaff(List<String> parts, List<CampusRepository.StaffName> people) {
        Map<String, List<String>> staff = new LinkedHashMap<>();
        people.forEach(s -> staff.computeIfAbsent(
                        STAFF_LABELS.getOrDefault(s.getStaffRole(), "nhân sự"), k -> new ArrayList<>())
                .add(s.getFullName()));
        staff.forEach((role, names) -> parts.add(role + " " + firstNames(names)));
    }

    private static String firstNames(List<String> names) {
        return names.size() > 3
                ? String.join(", ", names.subList(0, 3)) + " và " + (names.size() - 3) + " người khác"
                : String.join(", ", names);
    }

    // Lớp (mọi năm), phòng, trẻ (đăng ký / từng học / xếp năm sau), nhân sự (cả lịch sử); null = trống
    private String dataBlockers(Long campusId) {
        List<String> parts = new ArrayList<>();
        long classes = classroomRepository.countByCampusId(campusId);
        if (classes > 0) {
            parts.add(classes + " lớp");
        }
        long rooms = roomRepository.countByCampusIdAndStatusNot(campusId, RoomStatus.DELETED);
        if (rooms > 0) {
            parts.add(rooms + " phòng");
        }
        long children = campusRepository.countChildrenEver(campusId);
        if (children > 0) {
            parts.add(children + " trẻ");
        }
        addStaff(parts, campusRepository.findStaffNamesEver(campusId));
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private CampusResponse toResponseWithStats(Campus campus) {
        LocalDate today = LocalDate.now(clock);
        Long yearId = resolveSchoolYearId(null);
        long classCount = yearId == null
                ? 0
                : toMap(campusRepository.countClassesByCampus(yearId)).getOrDefault(campus.getId(), 0L);
        long childCount = yearId == null
                ? 0
                : toMap(campusRepository.countChildrenByCampus(yearId, today)).getOrDefault(campus.getId(), 0L);
        return campusMapper.toResponse(
                campus,
                classCount,
                childCount,
                campusStaff(today, StaffRole.VICE_PRINCIPAL).getOrDefault(campus.getId(), List.of()));
    }

    // Điểm trường chính của năm (V180); năm chưa đặt ⇒ theo bảng chung
    private Long yearMainId(Long yearId) {
        return campusRepository
                .findYearMainCampusId(yearId)
                .or(() -> campusRepository.findFirstByMainTrue().map(Campus::getId))
                .orElse(null);
    }

    // Đặt điểm trường chính của năm; năm đang hoạt động (hoặc không theo năm, hoặc trường chưa có điểm chính) thì cả
    // bảng chung, module khác đọc campuses.is_main
    private void markMain(Campus campus, Long yearId) {
        if (yearId != null) {
            campusRepository.clearYearMain(yearId, campus.getId());
            campusRepository.markYearMain(yearId, campus.getId());
        }
        boolean activeYear = yearId == null
                || schoolYearRepository
                        .findById(yearId)
                        .map(y -> y.getStatus() == SchoolYearStatus.ACTIVE)
                        .orElse(false);
        if (activeYear || campusRepository.findFirstByMainTrue().isEmpty()) {
            unsetCurrentMain();
            campus.markMain(true);
        }
    }

    private void unsetCurrentMain() {
        campusRepository.findFirstByMainTrue().ifPresent(main -> {
            main.markMain(false);
            campusRepository.flush();
        });
    }

    private Campus findCampus(Long campusId) {
        return campusRepository.findById(campusId).orElseThrow(() -> GlobalException.of(ApiCode.CAMPUS_NOT_FOUND));
    }

    private static Map<Long, Long> toMap(List<CampusRepository.CampusCount> counts) {
        return counts.stream()
                .collect(Collectors.toMap(
                        CampusRepository.CampusCount::getCampusId, CampusRepository.CampusCount::getTotal));
    }

    private static CampusRequest normalize(CampusRequest request) {
        String phone = request.phone() == null || request.phone().isBlank()
                ? null
                : request.phone().replaceAll("[\\s.\\-]", "").replaceFirst("^\\+?84", "0");
        return new CampusRequest(
                normalizeCode(request.code()),
                DisplayNames.normalize(request.name()),
                DisplayNames.normalize(request.shortName()),
                request.address().trim(),
                phone,
                request.isMain());
    }

    private static String normalizeCode(String code) {
        return DisplayNames.normalize(code).replace(" ", "");
    }
}
