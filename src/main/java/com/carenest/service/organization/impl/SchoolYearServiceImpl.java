package com.carenest.service.organization.impl;

import com.carenest.config.DevActivationProperties;
import com.carenest.dto.organization.SchoolHolidayDtos.SchoolHolidayItem;
import com.carenest.dto.organization.SchoolHolidayDtos.SchoolHolidaysRequest;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.SchoolYearRequest;
import com.carenest.dto.organization.SchoolYearSemesterRequest;
import com.carenest.dto.organization.SemesterLockResponse;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolHoliday;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AttendanceCutoffRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.SchoolHolidayRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.SchoolDayUsageCheck;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.SemesterUsageCheck;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final List<SemesterUsageCheck> semesterUsageChecks;
    private final SchoolHolidayRepository schoolHolidayRepository;
    private final List<SchoolDayUsageCheck> schoolDayUsageChecks;

    @Override
    @Transactional(readOnly = true)
    public List<SchoolYearDetailResponse> getSchoolYears(CurrentUser currentUser, SchoolYearStatus status) {
        String logPrefix = LOG_PREFIX + "|getSchoolYears|userId=" + currentUser.userId() + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            boolean leader = isSchoolLeader(currentUser);
            List<SchoolYear> schoolYears = schoolYearRepository.findAllByOrderByStartDateDesc().stream()
                    .filter(y -> status == null || y.getStatus() == status)
                    .filter(y -> leader || isVisibleToStaff(currentUser, y, today))
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
                    .map(y -> schoolStructureMapper.toDetailResponse(y, classCounts.getOrDefault(y.getId(), 0L), today))
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
            SchoolYear schoolYear = findSchoolYear(schoolYearId);
            // CFG-YR-05: GV chỉ thấy năm sắp diễn ra từ ngày mở chuẩn bị, khi có phân công năm đó
            if (!isSchoolLeader(currentUser) && !isVisibleToStaff(currentUser, schoolYear, LocalDate.now(clock))) {
                throw GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND);
            }
            SchoolYearDetailResponse response = toDetail(schoolYear);
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
            LocalDate prepDate = resolvePrepDate(request, null);
            SchoolYear schoolYear =
                    schoolYearRepository.save(SchoolYear.plan(name, request.startDate(), request.endDate(), prepDate));
            log.info("{}|schoolYearId={}|END", logPrefix, schoolYear.getId());
            return schoolStructureMapper.toDetailResponse(schoolYear, 0, LocalDate.now(clock));
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
            // Năm đang hoạt động đã có dữ liệu theo ngày: khóa ngày bắt đầu, không kết thúc lùi về quá khứ
            if (schoolYear.getStatus() == SchoolYearStatus.ACTIVE) {
                if (!request.startDate().equals(schoolYear.getStartDate())) {
                    throw GlobalException.of(ApiCode.SCHOOL_YEAR_START_LOCKED);
                }
                if (request.endDate().isBefore(LocalDate.now(clock))) {
                    throw GlobalException.of(ApiCode.SCHOOL_YEAR_END_BEFORE_TODAY);
                }
            }
            // Đổi ngày năm học: học kỳ đã đặt phải còn nằm trong năm
            if (schoolYear.getSemester1Start() != null) {
                checkSemesters(
                        new SchoolYearSemesterRequest(
                                schoolYear.getSemester1Start(),
                                schoolYear.getSemester1End(),
                                schoolYear.getSemester2Start(),
                                schoolYear.getSemester2End()),
                        request.startDate(),
                        request.endDate());
            }
            for (SchoolHolidayItem holiday : holidaysOf(schoolYearId)) {
                if (holiday.startDate().isBefore(request.startDate())
                        || holiday.endDate().isAfter(request.endDate())) {
                    throw GlobalException.of(
                            ApiCode.HOLIDAY_OUT_OF_YEAR,
                            holiday.name(),
                            DMY.format(request.startDate()),
                            DMY.format(request.endDate()));
                }
            }
            LocalDate oldStart = schoolYear.getStartDate();
            LocalDate oldEnd = schoolYear.getEndDate();
            LocalDate oldAssignmentStart = assignmentStart(schoolYear);
            LocalDate prepDate = resolvePrepDate(request, schoolYear);
            schoolYear.changeInfo(name, request.startDate(), request.endDate(), prepDate);
            schoolYearRepository.flush();
            // Phân công GV / tổ trưởng / PHT lưu theo ngày năm học: đổi ngày năm thì đổi theo, không để GV mất quyền
            // lớp. CFG-YR-05: năm sắp diễn ra tính từ ngày mở chuẩn bị, đổi ngày bắt đầu không đẩy phân công đã có
            LocalDate newAssignmentStart = assignmentStart(schoolYear);
            staffAssignmentService.realignYearAssignments(
                    schoolYearId, oldAssignmentStart, oldEnd, newAssignmentStart, request.endDate());
            if (!oldStart.equals(oldAssignmentStart) && !oldStart.equals(newAssignmentStart)) {
                // Phân công tạo trước khi tính theo ngày chuẩn bị (bắt đầu ở ngày bắt đầu cũ) cũng theo ngày mới
                staffAssignmentService.realignYearAssignments(
                        schoolYearId, oldStart, request.endDate(), newAssignmentStart, request.endDate());
            }
            log.info("{}|END", logPrefix);
            return schoolStructureMapper.toDetailResponse(schoolYear, classCount, LocalDate.now(clock));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SchoolYearDetailResponse updateSemester(
            CurrentUser currentUser, Long schoolYearId, SchoolYearSemesterRequest request) {
        String logPrefix =
                LOG_PREFIX + "|updateSemester|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            SchoolYear schoolYear = findSchoolYear(schoolYearId);
            SchoolYearRules.checkOpen(schoolYear);
            checkSemesters(request, schoolYear.getStartDate(), schoolYear.getEndDate());
            checkSemesterLocked(
                    schoolYear,
                    "Học kỳ I",
                    schoolYear.getSemester1Start(),
                    schoolYear.getSemester1End(),
                    request.semester1Start(),
                    request.semester1End(),
                    false);
            checkSemesterLocked(
                    schoolYear,
                    "Học kỳ II",
                    schoolYear.getSemester2Start(),
                    schoolYear.getSemester2End(),
                    request.semester2Start(),
                    request.semester2End(),
                    yearEndUsed(schoolYear));
            schoolYear.changeSemesters(
                    request.semester1Start(), request.semester1End(), request.semester2Start(), request.semester2End());
            schoolYearRepository.flush();
            log.info("{}|END", logPrefix);
            return toDetail(schoolYear);
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
                forgetTransactionHolidays();
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
            // CFG-YR-05: phải qua giai đoạn chuẩn bị (GV, tổ trưởng năm mới soạn giáo án) rồi mới kích hoạt
            if (!devActivation.skipDateWindow() && today.isBefore(schoolYear.getPrepDate())) {
                throw GlobalException.of(ApiCode.ACTIVATION_BEFORE_PREP, DMY.format(schoolYear.getPrepDate()));
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
    public void checkPlanYearWritable(Long schoolYearId) {
        SchoolYear year = findSchoolYear(schoolYearId);
        SchoolYearRules.checkOpen(year);
        if (!SchoolYearRules.planWritable(year, LocalDate.now(clock))) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_PREPARING, year.getName(), DMY.format(year.getPrepDate()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPlanYearWritable(Long schoolYearId) {
        return schoolYearRepository
                .findById(schoolYearId)
                .map(y -> SchoolYearRules.planWritable(y, LocalDate.now(clock)))
                .orElse(false);
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
    public java.util.Optional<SchoolYearDetailResponse> findSchoolYearDetail(Long schoolYearId) {
        return schoolYearRepository.findById(schoolYearId).map(this::toDetail);
    }

    @Override
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

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Object HOLIDAYS_TX_KEY = SchoolYearServiceImpl.class.getName() + ".holidays";
    // Đợt nghỉ dài nhất (Tết, nghỉ giữa kỳ); nghỉ hè nằm ngoài năm học
    private static final int HOLIDAY_MAX_DAYS = 31;

    // Cả 4 ngày bắt buộc, nằm trong năm học; mỗi học kỳ bắt đầu ≤ kết thúc; HK II bắt đầu sau khi HK I kết thúc
    @Override
    @Transactional(readOnly = true)
    public List<SchoolHolidayItem> getHolidays(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getHolidays|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            findSchoolYear(schoolYearId);
            List<SchoolHolidayItem> holidays = holidaysOf(schoolYearId);
            log.info("{}|END", logPrefix);
            return holidays;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public List<SchoolHolidayItem> replaceHolidays(
            CurrentUser currentUser, Long schoolYearId, SchoolHolidaysRequest request) {
        String logPrefix = LOG_PREFIX + "|replaceHolidays|userId=" + currentUser.userId() + "|schoolYearId="
                + schoolYearId + "|count=" + request.holidays().size();
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            SchoolYear year = findSchoolYear(schoolYearId);
            SchoolYearRules.checkOpen(year);
            checkHolidays(year, request.holidays());
            forgetTransactionHolidays();
            schoolHolidayRepository.deleteAllBySchoolYearId(schoolYearId);
            schoolHolidayRepository.flush();
            schoolHolidayRepository.saveAllAndFlush(request.holidays().stream()
                    .map(h -> SchoolHoliday.of(
                            schoolYearId, h.startDate(), h.endDate(), h.name().trim()))
                    .toList());
            List<SchoolHolidayItem> holidays = holidaysOf(schoolYearId);
            log.info("{}|END", logPrefix);
            return holidays;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSchoolDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return !schoolHolidayRepository.existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
        }
        return holidaysOfTransaction().stream().noneMatch(h -> h.covers(date));
    }

    // Báo cáo gọi isSchoolDay hàng nghìn lần: đọc ngày nghỉ một lần cho mỗi transaction (luôn là dữ liệu đã commit lúc
    // đọc, không dùng chung giữa các request); lưu / xóa ngày nghỉ trong cùng transaction thì bỏ bản đã đọc
    @SuppressWarnings("unchecked")
    private List<SchoolHoliday> holidaysOfTransaction() {
        List<SchoolHoliday> holidays =
                (List<SchoolHoliday>) TransactionSynchronizationManager.getResource(HOLIDAYS_TX_KEY);
        if (holidays == null) {
            holidays = List.copyOf(schoolHolidayRepository.findAll());
            TransactionSynchronizationManager.bindResource(HOLIDAYS_TX_KEY, holidays);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(HOLIDAYS_TX_KEY);
                }
            });
        }
        return holidays;
    }

    private static void forgetTransactionHolidays() {
        TransactionSynchronizationManager.unbindResourceIfPossible(HOLIDAYS_TX_KEY);
    }

    private List<SchoolHolidayItem> holidaysOf(Long schoolYearId) {
        return schoolHolidayRepository.findAllBySchoolYearIdOrderByStartDateAsc(schoolYearId).stream()
                .map(h -> new SchoolHolidayItem(h.getStartDate(), h.getEndDate(), h.getName()))
                .toList();
    }

    // SCH-05: mỗi đợt từ ≤ đến, nằm trong năm học, dài tối đa HOLIDAY_MAX_DAYS, không chồng đợt khác, không trùm ngày
    // đã có điểm danh (đặt nghỉ sau khi đã điểm danh làm lệch số liệu)
    private void checkHolidays(SchoolYear year, List<SchoolHolidayItem> items) {
        List<SchoolHolidayItem> sorted = items.stream()
                .sorted(java.util.Comparator.comparing(SchoolHolidayItem::startDate))
                .toList();
        SchoolHolidayItem previous = null;
        for (SchoolHolidayItem item : sorted) {
            String name = item.name().trim();
            if (item.endDate().isBefore(item.startDate())) {
                throw GlobalException.of(ApiCode.HOLIDAY_RANGE_INVALID, name);
            }
            if (item.startDate().isBefore(year.getStartDate()) || item.endDate().isAfter(year.getEndDate())) {
                throw GlobalException.of(
                        ApiCode.HOLIDAY_OUT_OF_YEAR,
                        name,
                        DMY.format(year.getStartDate()),
                        DMY.format(year.getEndDate()));
            }
            if (java.time.temporal.ChronoUnit.DAYS.between(item.startDate(), item.endDate()) + 1 > HOLIDAY_MAX_DAYS) {
                throw GlobalException.of(ApiCode.HOLIDAY_TOO_LONG, name, HOLIDAY_MAX_DAYS);
            }
            if (previous != null && !item.startDate().isAfter(previous.endDate())) {
                throw GlobalException.of(
                        ApiCode.HOLIDAY_OVERLAP, name, previous.name().trim());
            }
            previous = item;
            for (SchoolDayUsageCheck check : schoolDayUsageChecks) {
                check.firstUsedDay(item.startDate(), item.endDate()).ifPresent(day -> {
                    throw GlobalException.of(ApiCode.HOLIDAY_HAS_ATTENDANCE, DMY.format(day));
                });
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SemesterLockResponse getSemesterLocks(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getSemesterLocks|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            SchoolYear year = findSchoolYear(schoolYearId);
            SemesterLockResponse response = new SemesterLockResponse(
                    semesterUsed(year, year.getSemester1Start(), year.getSemester1End()),
                    semesterUsed(year, year.getSemester2Start(), year.getSemester2End()) || yearEndUsed(year));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private boolean semesterUsed(SchoolYear year, LocalDate start, LocalDate end) {
        return start != null
                && end != null
                && semesterUsageChecks.stream().anyMatch(c -> c.usedBySemester(year.getId(), start, end));
    }

    // Đánh giá cuối năm, khen thưởng mở từ ngày kết thúc HK II: đã có thì dời ngày HK II làm chúng thành "chưa mở"
    private boolean yearEndUsed(SchoolYear year) {
        return semesterUsageChecks.stream().anyMatch(c -> c.usedByYearEnd(year.getId()));
    }

    // Học kỳ đã có dữ liệu theo khoảng ngày cũ (đánh giá học kỳ) thì không đổi ngày học kỳ đó (OBS-06)
    private void checkSemesterLocked(
            SchoolYear year,
            String label,
            LocalDate oldStart,
            LocalDate oldEnd,
            LocalDate newStart,
            LocalDate newEnd,
            boolean lockedByYearEnd) {
        if (oldStart == null || oldEnd == null || (oldStart.equals(newStart) && oldEnd.equals(newEnd))) {
            return;
        }
        if (lockedByYearEnd || semesterUsed(year, oldStart, oldEnd)) {
            throw GlobalException.of(ApiCode.SEMESTER_DATES_LOCKED, label);
        }
    }

    private static void checkSemesters(SchoolYearSemesterRequest r, LocalDate startDate, LocalDate endDate) {
        Map<String, LocalDate> dates = new LinkedHashMap<>();
        dates.put("ngày bắt đầu học kỳ I", r.semester1Start());
        dates.put("ngày kết thúc học kỳ I", r.semester1End());
        dates.put("ngày bắt đầu học kỳ II", r.semester2Start());
        dates.put("ngày kết thúc học kỳ II", r.semester2End());
        dates.forEach((label, date) -> {
            if (date == null) {
                throw GlobalException.of(ApiCode.SEMESTER_DATE_REQUIRED, label);
            }
        });
        dates.forEach((label, date) -> {
            if (date.isBefore(startDate) || date.isAfter(endDate)) {
                throw GlobalException.of(
                        ApiCode.SEMESTER_OUT_OF_YEAR,
                        Character.toUpperCase(label.charAt(0)) + label.substring(1),
                        startDate.format(DMY),
                        endDate.format(DMY));
            }
        });
        if (r.semester1Start().isAfter(r.semester1End())) {
            throw GlobalException.of(ApiCode.SEMESTER_START_AFTER_END, "Học kỳ I");
        }
        if (r.semester2Start().isAfter(r.semester2End())) {
            throw GlobalException.of(ApiCode.SEMESTER_START_AFTER_END, "Học kỳ II");
        }
        if (!r.semester2Start().isAfter(r.semester1End())) {
            throw GlobalException.of(ApiCode.SEMESTER_OVERLAP, r.semester1End().format(DMY));
        }
    }

    private SchoolYear findSchoolYear(Long schoolYearId) {
        return schoolYearRepository
                .findById(schoolYearId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
    }

    private SchoolYearDetailResponse toDetail(SchoolYear schoolYear) {
        return schoolStructureMapper.toDetailResponse(
                schoolYear, classroomRepository.countBySchoolYearId(schoolYear.getId()), LocalDate.now(clock));
    }

    // Ngày phân công năm học bắt đầu có hiệu lực (như TeacherAssignmentServiceImpl#effectiveStart): năm sắp diễn ra
    // từ ngày mở chuẩn bị (CFG-YR-05, không chồng năm trước), năm khác từ ngày bắt đầu
    private LocalDate assignmentStart(SchoolYear year) {
        LocalDate today = LocalDate.now(clock);
        if (year.getStatus() != SchoolYearStatus.PLANNED || today.isAfter(year.getStartDate())) {
            return year.getStartDate();
        }
        LocalDate prep = year.getPrepDate().isAfter(year.getStartDate()) ? year.getStartDate() : year.getPrepDate();
        for (SchoolYear other : schoolYearRepository.findAllByOrderByStartDateDesc()) {
            if (other.getEndDate().isBefore(year.getStartDate()) && !prep.isAfter(other.getEndDate())) {
                prep = other.getEndDate().plusDays(1);
            }
        }
        return today.isAfter(prep) ? today : prep;
    }

    // HT, PHT thấy mọi năm (cấu hình, xếp lớp); người khác không thấy năm sắp diễn ra chưa tới ngày mở chuẩn bị
    private static boolean isSchoolLeader(CurrentUser currentUser) {
        return currentUser.hasRole(CareNestRole.PRINCIPAL) || currentUser.hasRole(CareNestRole.VICE_PRINCIPAL);
    }

    // CFG-YR-05: năm sắp diễn ra chỉ hiện cho GV / Tổ trưởng được phân công năm đó, từ ngày mở chuẩn bị
    private boolean isVisibleToStaff(CurrentUser currentUser, SchoolYear schoolYear, LocalDate today) {
        return schoolYear.getStatus() != SchoolYearStatus.PLANNED
                || (schoolYear.isPreparing(today)
                        && staffAssignmentService.hasStaffAssignmentInYear(
                                currentUser.userId(), schoolYear.getId(), today));
    }

    // CFG-YR-05: ngày mở chuẩn bị trước ngày bắt đầu, sau ngày kết thúc năm liền trước, không trước hôm nay; trống khi
    // tạo ⇒ 1 tháng trước ngày bắt đầu, kẹp vào khoảng hợp lệ. Sửa năm: trống hoặc giữ nguyên ngày cũ (kể cả đã qua) ⇒
    // không kiểm lại ngày quá khứ; năm đã bắt đầu / kết thúc không đổi được
    private LocalDate resolvePrepDate(SchoolYearRequest request, SchoolYear existing) {
        LocalDate today = LocalDate.now(clock);
        if (existing != null && existing.getStatus() != SchoolYearStatus.PLANNED) {
            if (request.prepDate() != null && !request.prepDate().equals(existing.getPrepDate())) {
                throw GlobalException.of(ApiCode.SCHOOL_YEAR_PREP_LOCKED);
            }
            return existing.getPrepDate();
        }
        Long excludeId = existing == null ? null : existing.getId();
        SchoolYear previous = schoolYearRepository.findAllByOrderByStartDateDesc().stream()
                .filter(y -> !y.getId().equals(excludeId) && y.getEndDate().isBefore(request.startDate()))
                .findFirst()
                .orElse(null);
        LocalDate prepDate = request.prepDate();
        if (prepDate == null && existing == null) {
            prepDate = request.startDate().minusMonths(1);
            if (previous != null && !prepDate.isAfter(previous.getEndDate())) {
                prepDate = previous.getEndDate().plusDays(1);
            }
            if (prepDate.isBefore(today)) {
                prepDate = today;
            }
            // Năm bắt đầu quá gần: không còn ngày hợp lệ trước ngày bắt đầu ⇒ báo lỗi như HT tự chọn
        } else if (prepDate == null) {
            prepDate = existing.getPrepDate();
        }
        boolean unchanged = existing != null && prepDate.equals(existing.getPrepDate());
        if (!prepDate.isBefore(request.startDate())) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_PREP_AFTER_START);
        }
        if (previous != null && !prepDate.isAfter(previous.getEndDate())) {
            throw GlobalException.of(
                    ApiCode.SCHOOL_YEAR_PREP_BEFORE_PREVIOUS_END,
                    previous.getName(),
                    DMY.format(previous.getEndDate()));
        }
        if (!unchanged && prepDate.isBefore(today)) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_PREP_IN_PAST);
        }
        return prepDate;
    }
}
