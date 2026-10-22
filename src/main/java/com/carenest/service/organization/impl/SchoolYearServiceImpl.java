package com.carenest.service.organization.impl;

import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.SchoolYearRequest;
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
            checkNotClosed(schoolYear);
            String name = validateYearInfo(request, schoolYearId);
            long classCount = classroomRepository.countBySchoolYearId(schoolYearId);
            // Tên năm học dùng ở lớp, kế hoạch, bộ chọn năm: chỉ đổi khi năm chưa bắt đầu và chưa có lớp
            if (!name.equals(schoolYear.getName())) {
                if (schoolYear.getStatus() != SchoolYearStatus.PLANNED) {
                    throw new GlobalException(ApiCode.CONFLICT, "School year name can only change before it starts");
                }
                if (classCount > 0) {
                    throw new GlobalException(ApiCode.CONFLICT, "School year already has classes, name cannot change");
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
                throw new GlobalException(ApiCode.CONFLICT, "Only a planned school year can be deleted");
            }
            if (classroomRepository.countBySchoolYearId(schoolYearId) > 0) {
                throw new GlobalException(ApiCode.CONFLICT, "School year already has classes and cannot be deleted");
            }
            try {
                // Giờ chốt chỉ là cấu hình của năm chưa bắt đầu: xóa cùng năm học
                attendanceCutoffRepository.deleteAllBySchoolYearId(schoolYearId);
                schoolYearRepository.delete(schoolYear);
                schoolYearRepository.flush();
            } catch (DataIntegrityViolationException integrityEx) {
                // Bảng của module khác (vd. kế hoạch năm học) còn tham chiếu năm này
                throw new GlobalException(
                        ApiCode.CONFLICT, "School year is used by other records and cannot be deleted", integrityEx);
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
                throw new GlobalException(ApiCode.CONFLICT, "Only a planned school year can be activated");
            }
            if (confirmName == null || !confirmName.trim().equals(schoolYear.getName())) {
                throw new GlobalException(
                        ApiCode.BAD_REQUEST, "Type the school year name exactly to confirm activation");
            }
            LocalDate today = LocalDate.now(clock);
            if (today.isBefore(schoolYear.getStartDate().minusDays(ACTIVATION_LEAD_DAYS))) {
                throw new GlobalException(
                        ApiCode.CONFLICT,
                        "School year can be activated from "
                                + schoolYear.getStartDate().minusDays(ACTIVATION_LEAD_DAYS));
            }
            // TODO(Cần): kiểm tra readiness (lớp, phòng, phân công, xếp lớp) khi có các API đó
            Instant now = clock.instant();
            // Chỉ một năm ACTIVE: đóng năm đang chạy trước (flush) rồi mới mở năm mới, cùng transaction
            schoolYearRepository.findFirstByStatus(SchoolYearStatus.ACTIVE).ifPresent(current -> {
                current.close(currentUser.userId(), now);
                // Lớp đóng cùng năm học
                classroomRepository.closeAllBySchoolYearId(current.getId());
                schoolYearRepository.flush();
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
                throw new GlobalException(ApiCode.CONFLICT, "Only the active school year can be closed");
            }
            schoolYear.close(currentUser.userId(), clock.instant());
            classroomRepository.closeAllBySchoolYearId(schoolYearId);
            schoolYearRepository.flush();
            SchoolYearDetailResponse response = toDetail(schoolYear);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // AUTH-01: cấu hình toàn trường cần role Hiệu trưởng VÀ phân công Hiệu trưởng còn hiệu lực
    private void checkCanManage(CurrentUser currentUser) {
        if (!currentUser.hasRole(CareNestRole.PRINCIPAL)
                || !staffAssignmentService.hasActivePrincipalAssignment(currentUser.userId(), LocalDate.now(clock))) {
            throw new GlobalException(ApiCode.FORBIDDEN, "Only the principal can manage school years");
        }
    }

    private static void checkNotClosed(SchoolYear schoolYear) {
        if (schoolYear.getStatus() == SchoolYearStatus.CLOSED) {
            throw new GlobalException(ApiCode.CONFLICT, "School year " + schoolYear.getName() + " is closed");
        }
    }

    // Tên dạng NNNN-NNNN với năm sau = năm trước + 1, không trùng tên, không chồng thời gian năm khác
    private String validateYearInfo(SchoolYearRequest request, Long excludeId) {
        String name = request.name().trim();
        Matcher matcher = YEAR_NAME.matcher(name);
        if (!matcher.matches() || Integer.parseInt(matcher.group(2)) != Integer.parseInt(matcher.group(1)) + 1) {
            throw new GlobalException(
                    ApiCode.BAD_REQUEST, "School year name must be YYYY-YYYY with consecutive years, e.g. 2027-2028");
        }
        if (!request.endDate().isAfter(request.startDate())) {
            throw new GlobalException(ApiCode.BAD_REQUEST, "End date must be after start date");
        }
        boolean duplicated = excludeId == null
                ? schoolYearRepository.existsByName(name)
                : schoolYearRepository.existsByNameAndIdNot(name, excludeId);
        if (duplicated) {
            throw new GlobalException(ApiCode.CONFLICT, "School year " + name + " already exists");
        }
        List<SchoolYear> overlapping =
                schoolYearRepository.findOverlapping(request.startDate(), request.endDate(), excludeId);
        if (!overlapping.isEmpty()) {
            throw new GlobalException(
                    ApiCode.CONFLICT,
                    "Dates overlap school year " + overlapping.getFirst().getName());
        }
        return name;
    }

    private SchoolYear findSchoolYear(Long schoolYearId) {
        return schoolYearRepository
                .findById(schoolYearId)
                .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "School year not found"));
    }

    private SchoolYearDetailResponse toDetail(SchoolYear schoolYear) {
        return schoolStructureMapper.toDetailResponse(
                schoolYear, classroomRepository.countBySchoolYearId(schoolYear.getId()));
    }
}
