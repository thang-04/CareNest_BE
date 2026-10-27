package com.carenest.service.organization.impl;

import com.carenest.config.DevActivationProperties;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.SchoolYearRequest;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AttendanceCutoffRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
public class SchoolYearServiceImpl implements SchoolYearService {
    private static final String LOG_PREFIX = "[SchoolYearServiceImpl]";
    private static final Pattern YEAR_NAME = Pattern.compile("(\\d{4})-(\\d{4})");
    // Đề xuất FE 13/10/2026 (ACTIVATION_LEAD_DAYS): được kích hoạt từ 7 ngày trước ngày bắt đầu
    private static final int ACTIVATION_LEAD_DAYS = 7;

    private final SchoolYearRepository schoolYearRepository;
    private final ClassroomRepository classroomRepository;
    private final AttendanceCutoffRepository attendanceCutoffRepository;
    private final StaffAssignmentService staffAssignmentService;
    private final SchoolStructureMapper schoolStructureMapper;
    private final Clock clock;
    private final DevActivationProperties devActivation;

    @Override
    @Transactional(readOnly = true)
    public List<SchoolYearDetailResponse> getSchoolYears(CurrentUser currentUser, SchoolYearStatus status) {
        String logPrefix = LOG_PREFIX + "|getSchoolYears|userId=" + currentUser.userId() + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            List<SchoolYear> schoolYears = schoolYearRepository.findAllByOrderByStartDateDesc().stream()
                    .filter(y -> status == null || y.getStatus() == status)
                    .toList();
            Map<Long, Long> classCounts = schoolYears.isEmpty()
                    ? Map.of()
                    : classroomRepository
                            .countBySchoolYearIds(
                                    schoolYears.stream().map(SchoolYear::getId).toList())
                            .stream()
                            .collect(Collectors.toMap(
                                    ClassroomRepository.SchoolYearClassCount::getSchoolYearId,
                                    ClassroomRepository.SchoolYearClassCount::getClassCount));
            List<SchoolYearDetailResponse> response = schoolYears.stream()
                    .map(y -> schoolStructureMapper.toDetailResponse(y, classCounts.getOrDefault(y.getId(), 0L)))
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
    public SchoolYearDetailResponse getSchoolYear(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            SchoolYearDetailResponse response = toDetail(findSchoolYear(schoolYearId));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SchoolYearDetailResponse createSchoolYear(CurrentUser currentUser, SchoolYearRequest request) {
        String logPrefix = LOG_PREFIX + "|createSchoolYear|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            String name = validateYearInfo(request, null);
            checkFollowsLatest(name, request);
            SchoolYear schoolYear =
                    schoolYearRepository.save(SchoolYear.plan(name, request.startDate(), request.endDate()));
            log.info("{}|schoolYearId={}|END", logPrefix, schoolYear.getId());
            return schoolStructureMapper.toDetailResponse(schoolYear, 0);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SchoolYearDetailResponse updateSchoolYear(
            CurrentUser currentUser, Long schoolYearId, SchoolYearRequest request) {
        String logPrefix =
                LOG_PREFIX + "|updateSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            SchoolYear schoolYear = findSchoolYear(schoolYearId);
            SchoolYearRules.checkOpen(schoolYear);
            String name = validateYearInfo(request, schoolYearId);
            long classCount = classroomRepository.countBySchoolYearId(schoolYearId);
            // Tên năm học dùng ở lớp, kế hoạch, bộ chọn năm: chỉ đổi khi năm chưa bắt đầu và chưa có lớp
            if (!name.equals(schoolYear.getName())) {
                if (schoolYear.getStatus() != SchoolYearStatus.PLANNED) {
                    throw GlobalException.of(ApiCode.SCHOOL_YEAR_NAME_LOCKED);
                }
                if (classCount > 0) {
                    throw GlobalException.of(ApiCode.SCHOOL_YEAR_HAS_CLASSES);
                }
            }
            schoolYear.changeInfo(name, request.startDate(), request.endDate());
            schoolYearRepository.flush();
            log.info("{}|END", logPrefix);
            return schoolStructureMapper.toDetailResponse(schoolYear, classCount);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteSchoolYear(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|deleteSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            SchoolYear schoolYear = findSchoolYear(schoolYearId);
            if (schoolYear.getStatus() != SchoolYearStatus.PLANNED) {
                throw GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_DELETABLE);
            }
            try {
                // Năm chưa bắt đầu chỉ có dữ liệu chuẩn bị: xóa cùng năm học (lớp, phân công, giờ chốt; xếp lớp
                // xóa theo FK cascade). Trẻ chỉ vào lớp khi kích hoạt nên không có enrollment (Cần chốt 22/10/2026)
                schoolYearRepository.deletePlannedYearAssignments(schoolYearId);
                schoolYearRepository.deletePlannedYearPlacements(schoolYearId);
                schoolYearRepository.deletePlannedYearClasses(schoolYearId);
                attendanceCutoffRepository.deleteAllBySchoolYearId(schoolYearId);
                schoolYearRepository.delete(schoolYear);
                schoolYearRepository.flush();
            } catch (DataIntegrityViolationException integrityEx) {
                // Bảng của module khác (vd. kế hoạch năm học) còn tham chiếu năm này
                throw GlobalException.withCause(ApiCode.SCHOOL_YEAR_IN_USE, integrityEx);
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SchoolYearDetailResponse activateSchoolYear(CurrentUser currentUser, Long schoolYearId, String confirmName) {
        String logPrefix =
                LOG_PREFIX + "|activateSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            SchoolYear schoolYear = findSchoolYear(schoolYearId);
            if (schoolYear.getStatus() != SchoolYearStatus.PLANNED) {
                throw GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_ACTIVATABLE);
            }
            // Kích hoạt theo thứ tự: chỉ năm PLANNED sớm nhất (năm liền sau năm đang chạy)
            SchoolYear next = schoolYearRepository
                    .findFirstByStatusOrderByStartDateAsc(SchoolYearStatus.PLANNED)
                    .orElse(schoolYear);
            if (!next.getId().equals(schoolYear.getId())) {
                throw GlobalException.of(ApiCode.ACTIVATE_EARLIER_YEAR_FIRST, next.getName());
            }
            if (confirmName == null || !confirmName.trim().equals(schoolYear.getName())) {
                throw GlobalException.of(ApiCode.ACTIVATION_CONFIRM_MISMATCH);
            }
            LocalDate today = LocalDate.now(clock);
            if (!devActivation.skipDateWindow()
                    && today.isBefore(schoolYear.getStartDate().minusDays(ACTIVATION_LEAD_DAYS))) {
                throw GlobalException.of(
                        ApiCode.ACTIVATION_TOO_EARLY, schoolYear.getStartDate().minusDays(ACTIVATION_LEAD_DAYS));
            }
            Instant now = clock.instant();
            // Chỉ một năm ACTIVE: đóng năm đang chạy trước (flush) rồi mới mở năm mới, cùng transaction
            schoolYearRepository.findFirstByStatus(SchoolYearStatus.ACTIVE).ifPresent(current -> {
                current.close(currentUser.userId(), now);
                // Lớp đóng cùng năm học
                classroomRepository.closeAllBySchoolYearId(current.getId());
                schoolYearRepository.flush();
                takeSnapshot(current.getId());
            });
            schoolYear.activate(currentUser.userId(), now);
            schoolYearRepository.flush();
            SchoolYearDetailResponse response = toDetail(schoolYear);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SchoolYearDetailResponse closeSchoolYear(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|closeSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            SchoolYear schoolYear = findSchoolYear(schoolYearId);
            if (schoolYear.getStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_CLOSABLE);
            }
            // Kết thúc tay chỉ sau ngày kết thúc năm học; trước đó năm cũ tự đóng khi kích hoạt năm mới
            if (!devActivation.skipCloseEndDate() && !LocalDate.now(clock).isAfter(schoolYear.getEndDate())) {
                throw GlobalException.of(ApiCode.SCHOOL_YEAR_CLOSE_TOO_EARLY, schoolYear.getEndDate());
            }
            schoolYear.close(currentUser.userId(), clock.instant());
            classroomRepository.closeAllBySchoolYearId(schoolYearId);
            schoolYearRepository.flush();
            takeSnapshot(schoolYearId);
            SchoolYearDetailResponse response = toDetail(schoolYear);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkYearOpen(Long schoolYearId) {
        SchoolYearRules.checkOpen(findSchoolYear(schoolYearId));
    }

    @Override
    @Transactional(readOnly = true)
    public void checkClassYearOpen(Long classroomId) {
        Classroom classroom = classroomRepository
                .findById(classroomId)
                .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
        SchoolYearRules.checkOpen(findSchoolYear(classroom.getSchoolYearId()));
    }

    @Override
    @Transactional(readOnly = true)
    public void checkDateYearOpen(LocalDate date) {
        schoolYearRepository.findContaining(date).ifPresent(SchoolYearRules::checkOpen);
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolYearDetailResponse findNextPlannedYear() {
        return schoolYearRepository
                .findFirstByStatusOrderByStartDateAsc(SchoolYearStatus.PLANNED)
                .map(this::toDetail)
                .orElse(null);
    }

    // Năm mới nối tiếp năm mới nhất: tên = năm mới nhất + 1, bắt đầu sau ngày kết thúc năm đó
    private void checkFollowsLatest(String name, SchoolYearRequest request) {
        SchoolYear latest = schoolYearRepository.findAllByOrderByStartDateDesc().stream()
                .findFirst()
                .orElse(null);
        if (latest == null) {
            return;
        }
        Matcher latestName = YEAR_NAME.matcher(latest.getName());
        Matcher newName = YEAR_NAME.matcher(name);
        if (latestName.matches() && newName.matches()) {
            int expected = Integer.parseInt(latestName.group(1)) + 1;
            if (Integer.parseInt(newName.group(1)) != expected) {
                throw GlobalException.of(
                        ApiCode.SCHOOL_YEAR_OUT_OF_SEQUENCE, expected + "-" + (expected + 1), latest.getName());
            }
        }
        if (!request.startDate().isAfter(latest.getEndDate())) {
            throw GlobalException.of(
                    ApiCode.SCHOOL_YEAR_START_BEFORE_LATEST_END, latest.getName(), latest.getEndDate());
        }
    }

    // CFG-YR-02: chụp phòng / điểm trường / nhóm tuổi lúc năm kết thúc; xem năm cũ đọc bản chụp này
    private void takeSnapshot(Long schoolYearId) {
        schoolYearRepository.deleteRoomSnapshot(schoolYearId);
        schoolYearRepository.deleteCampusSnapshot(schoolYearId);
        schoolYearRepository.deleteAgeGroupSnapshot(schoolYearId);
        schoolYearRepository.snapshotRooms(schoolYearId);
        schoolYearRepository.snapshotCampuses(schoolYearId);
        schoolYearRepository.snapshotAgeGroups(schoolYearId);
    }

    // AUTH-01: cấu hình toàn trường cần role Hiệu trưởng VÀ phân công Hiệu trưởng còn hiệu lực
    private void checkCanManage(CurrentUser currentUser) {
        if (!currentUser.hasRole(CareNestRole.PRINCIPAL)
                || !staffAssignmentService.hasActivePrincipalAssignment(currentUser.userId(), LocalDate.now(clock))) {
            throw GlobalException.of(ApiCode.PRINCIPAL_ONLY, "quản lý năm học");
        }
    }

    // Tên dạng NNNN-NNNN với năm sau = năm trước + 1, không trùng tên, không chồng thời gian năm khác
    private String validateYearInfo(SchoolYearRequest request, Long excludeId) {
        String name = request.name().trim();
        Matcher matcher = YEAR_NAME.matcher(name);
        if (!matcher.matches() || Integer.parseInt(matcher.group(2)) != Integer.parseInt(matcher.group(1)) + 1) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_NAME_INVALID);
        }
        if (!request.endDate().isAfter(request.startDate())) {
            throw GlobalException.of(ApiCode.END_DATE_BEFORE_START);
        }
        boolean duplicated = excludeId == null
                ? schoolYearRepository.existsByName(name)
                : schoolYearRepository.existsByNameAndIdNot(name, excludeId);
        if (duplicated) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_EXISTS, name);
        }
        List<SchoolYear> overlapping =
                schoolYearRepository.findOverlapping(request.startDate(), request.endDate(), excludeId);
        if (!overlapping.isEmpty()) {
            throw GlobalException.of(
                    ApiCode.SCHOOL_YEAR_DATES_OVERLAP, overlapping.getFirst().getName());
        }
        return name;
    }

    private SchoolYear findSchoolYear(Long schoolYearId) {
        return schoolYearRepository
                .findById(schoolYearId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
    }

    private SchoolYearDetailResponse toDetail(SchoolYear schoolYear) {
        return schoolStructureMapper.toDetailResponse(
                schoolYear, classroomRepository.countBySchoolYearId(schoolYear.getId()));
    }
}
