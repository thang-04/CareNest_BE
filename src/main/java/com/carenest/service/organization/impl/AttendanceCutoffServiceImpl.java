package com.carenest.service.organization.impl;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.organization.CutoffRequest;
import com.carenest.dto.organization.CutoffResponse;
import com.carenest.entity.organization.AttendanceCutoff;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.repository.organization.AttendanceCutoffRepository;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.AttendanceCutoffService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
@Transactional
public class AttendanceCutoffServiceImpl implements AttendanceCutoffService {
    private static final String LOG_PREFIX = "[AttendanceCutoffServiceImpl]";

    private final AttendanceCutoffRepository attendanceCutoffRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final SchoolConfigAccess schoolConfigAccess;
    private final Clock clock;
    private final CampusRepository campusRepository;
    private final StaffAssignmentService staffAssignmentService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public CutoffResponse getCutoff(Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getCutoff|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            CutoffResponse response = toResponse(findYear(schoolYearId));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CutoffResponse getCutoff(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getCutoff|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            SchoolYear year = findYear(schoolYearId);
            // CFG-YR-05: năm chưa tới ngày mở chuẩn bị chỉ HT thấy
            schoolConfigAccess.checkYearVisible(currentUser, year);
            CutoffResponse response = toResponse(year);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public CutoffResponse saveCutoff(CurrentUser currentUser, Long schoolYearId, CutoffRequest request) {
        String logPrefix = LOG_PREFIX + "|saveCutoff|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            SchoolYear year = findYear(schoolYearId);
            SchoolYearRules.checkOpen(year);
            if (request.effectiveFrom().isBefore(year.getStartDate())
                    || request.effectiveFrom().isAfter(year.getEndDate())) {
                throw GlobalException.of(ApiCode.CUTOFF_DATE_OUT_OF_YEAR, year.getName());
            }
            LocalTime time = request.time().withSecond(0).withNano(0);
            List<AttendanceCutoff> history =
                    attendanceCutoffRepository.findAllBySchoolYearIdOrderByIdDesc(schoolYearId);
            // Không để khoảng đầu năm học không có giờ chốt (sẽ rơi về giá trị mặc định mà màn hình báo đã cài)
            if (history.isEmpty() && request.effectiveFrom().isAfter(year.getStartDate())) {
                throw GlobalException.of(
                        ApiCode.CUTOFF_FIRST_FROM_YEAR_START,
                        DateTimeFormatter.ofPattern("dd/MM/yyyy").format(year.getStartDate()));
            }
            LocalTime latestArrival = request.latestAfternoonArrival() != null
                    ? request.latestAfternoonArrival().withSecond(0).withNano(0)
                    : history.isEmpty()
                            ? AttendanceCutoff.DEFAULT_LATEST_ARRIVAL
                            : history.getFirst().getLatestAfternoonArrival();
            AttendanceCutoff latest = history.isEmpty() ? null : history.getFirst();
            LocalTime dropoffOpenFrom = scheduleValue(
                    request.dropoffOpenFrom(),
                    latest == null ? null : latest.getDropoffOpenFrom(),
                    AttendanceCutoff.DEFAULT_DROPOFF_OPEN_FROM);
            LocalTime morningStart = scheduleValue(
                    request.morningStart(),
                    latest == null ? null : latest.getMorningStart(),
                    AttendanceCutoff.DEFAULT_MORNING_START);
            LocalTime morningEnd = scheduleValue(
                    request.morningEnd(),
                    latest == null ? null : latest.getMorningEnd(),
                    AttendanceCutoff.DEFAULT_MORNING_END);
            LocalTime afternoonStart = scheduleValue(
                    request.afternoonStart(),
                    latest == null ? null : latest.getAfternoonStart(),
                    AttendanceCutoff.DEFAULT_AFTERNOON_START);
            LocalTime pickupStart = scheduleValue(
                    request.pickupStart(),
                    latest == null ? null : latest.getPickupStart(),
                    AttendanceCutoff.DEFAULT_PICKUP_START);
            LocalTime fullDayPickupFrom = scheduleValue(
                    request.fullDayPickupFrom(),
                    latest == null ? null : latest.getFullDayPickupFrom(),
                    AttendanceCutoff.DEFAULT_FULL_DAY_PICKUP_FROM);
            LocalTime pickupEnd = scheduleValue(
                    request.pickupEnd(),
                    latest == null ? null : latest.getPickupEnd(),
                    AttendanceCutoff.DEFAULT_PICKUP_END);
            LocalTime afternoonEnd = scheduleValue(
                    request.afternoonEnd(),
                    latest == null ? null : latest.getAfternoonEnd(),
                    AttendanceCutoff.DEFAULT_AFTERNOON_END);
            // PCK-04, ATT-06, ATT-13 (phương án A): mỗi giờ sai thứ tự báo lỗi ở đúng ô
            List<FieldErrorResponse> violations = DailyScheduleRules.violations(new DailyScheduleRules.Schedule(
                    time,
                    latestArrival,
                    dropoffOpenFrom,
                    morningStart,
                    morningEnd,
                    afternoonStart,
                    fullDayPickupFrom,
                    pickupStart,
                    pickupEnd,
                    afternoonEnd));
            if (!violations.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, violations);
            }
            if (!history.isEmpty()
                    && history.getFirst().getCutoffTime().equals(time)
                    && history.getFirst().getEffectiveFrom().equals(request.effectiveFrom())
                    && history.getFirst().getLatestAfternoonArrival().equals(latestArrival)
                    && history.getFirst().getDropoffOpenFrom().equals(dropoffOpenFrom)
                    && history.getFirst().getMorningStart().equals(morningStart)
                    && history.getFirst().getMorningEnd().equals(morningEnd)
                    && history.getFirst().getAfternoonStart().equals(afternoonStart)
                    && history.getFirst().getPickupStart().equals(pickupStart)
                    && history.getFirst().getFullDayPickupFrom().equals(fullDayPickupFrom)
                    && history.getFirst().getPickupEnd().equals(pickupEnd)
                    && history.getFirst().getAfternoonEnd().equals(afternoonEnd)) {
                throw GlobalException.of(ApiCode.CUTOFF_UNCHANGED);
            }
            // Đổi giờ chốt lùi ngày làm đổi lại đơn nghỉ đã tính theo giờ chốt cũ (ATT-04): lần đặt sau chỉ áp dụng từ
            // hôm nay; lần đặt đầu tiên của năm học được tính từ đầu năm
            if (!history.isEmpty() && request.effectiveFrom().isBefore(LocalDate.now(clock))) {
                throw GlobalException.of(ApiCode.CUTOFF_DATE_PAST);
            }
            // Không đổi giờ chốt giữa ngày: hôm nay đã qua giờ chốt hiện hành hoặc giờ chốt mới thì ngày đã/đang khóa,
            // đổi lúc này làm đơn nghỉ và điểm danh đã xử lý bị tính lại sai
            LocalDate today = LocalDate.now(clock);
            if (!history.isEmpty() && request.effectiveFrom().equals(today)) {
                LocalTime nowTime = LocalTime.now(clock);
                LocalTime currentCutoff = history.stream()
                        .filter(c -> !c.getEffectiveFrom().isAfter(today))
                        .max(java.util.Comparator.comparing(AttendanceCutoff::getEffectiveFrom)
                                .thenComparing(AttendanceCutoff::getId))
                        .map(AttendanceCutoff::getCutoffTime)
                        .orElse(time);
                LocalTime earliest = currentCutoff.isBefore(time) ? currentCutoff : time;
                if (!nowTime.isBefore(earliest)) {
                    throw GlobalException.of(ApiCode.CUTOFF_CHANGE_TOO_LATE);
                }
            }
            attendanceCutoffRepository.saveAndFlush(AttendanceCutoff.create(
                            schoolYearId,
                            time,
                            request.effectiveFrom(),
                            request.note() == null ? "" : request.note().trim(),
                            currentUser.userId())
                    .withLatestArrival(latestArrival)
                    .withDailySchedule(
                            dropoffOpenFrom,
                            morningStart,
                            morningEnd,
                            afternoonStart,
                            pickupStart,
                            fullDayPickupFrom,
                            pickupEnd,
                            afternoonEnd));
            // OBS-31: báo PHT, GV, bếp đang phân công khi đổi giờ chốt (lần đặt đầu của năm không báo)
            if (!history.isEmpty()) {
                notifyCutoffChanged(year, time, request.effectiveFrom());
            }
            CutoffResponse response = toResponse(year);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void notifyCutoffChanged(SchoolYear year, LocalTime time, LocalDate from) {
        LocalDate today = LocalDate.now(clock);
        List<Long> campusIds = campusRepository.findYearCampusIds(year.getId());
        Set<Long> staff = staffAssignmentService.findActiveStaffUserIds(campusIds, today);
        if (staff.isEmpty()) {
            return;
        }
        notificationService.notifyUsers(
                staff,
                NotificationType.CUTOFF_CHANGED,
                "Cập nhật giờ điểm danh và báo ăn",
                "Cấu hình giờ chốt điểm danh, báo ăn và lịch sinh hoạt năm học " + year.getName()
                        + " được cập nhật từ ngày "
                        + DateTimeFormatter.ofPattern("dd/MM/yyyy").format(from)
                        + ". Giờ chốt là " + time + ".",
                "/attendance",
                false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DailySchedule> findDailySchedule(Long schoolYearId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findDailySchedule|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Optional<DailySchedule> schedule = attendanceCutoffRepository
                    .findFirstBySchoolYearIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                            schoolYearId, date)
                    .map(c -> new DailySchedule(
                            c.getDropoffOpenFrom(),
                            c.getMorningStart(),
                            c.getMorningEnd(),
                            c.getAfternoonStart(),
                            c.getLatestAfternoonArrival(),
                            c.getPickupStart(),
                            c.getFullDayPickupFrom(),
                            c.getPickupEnd(),
                            c.getAfternoonEnd(),
                            c.getCutoffTime()));
            log.info("{}|END", logPrefix);
            return schedule;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalTime> findCutoffTime(Long schoolYearId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findCutoffTime|schoolYearId=" + schoolYearId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            Optional<LocalTime> time = attendanceCutoffRepository
                    .findFirstBySchoolYearIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                            schoolYearId, date)
                    .map(AttendanceCutoff::getCutoffTime);
            log.info("{}|END", logPrefix);
            return time;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalTime> findAfternoonStart(Long schoolYearId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findAfternoonStart|schoolYearId=" + schoolYearId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            Optional<LocalTime> time = attendanceCutoffRepository
                    .findFirstBySchoolYearIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                            schoolYearId, date)
                    .map(AttendanceCutoff::getAfternoonStart);
            log.info("{}|END", logPrefix);
            return time;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalTime> findLatestAfternoonArrival(Long schoolYearId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findLatestAfternoonArrival|schoolYearId=" + schoolYearId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            Optional<LocalTime> time = attendanceCutoffRepository
                    .findFirstBySchoolYearIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                            schoolYearId, date)
                    .map(AttendanceCutoff::getLatestAfternoonArrival);
            log.info("{}|END", logPrefix);
            return time;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalTime> findFullDayPickupFrom(Long schoolYearId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findFullDayPickupFrom|schoolYearId=" + schoolYearId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            Optional<LocalTime> time = attendanceCutoffRepository
                    .findFirstBySchoolYearIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                            schoolYearId, date)
                    .map(AttendanceCutoff::getFullDayPickupFrom);
            log.info("{}|END", logPrefix);
            return time;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private CutoffResponse toResponse(SchoolYear year) {
        List<AttendanceCutoff> rows = attendanceCutoffRepository.findAllBySchoolYearIdOrderByIdDesc(year.getId());
        Set<Long> userIds = rows.stream()
                .map(AttendanceCutoff::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = userIds.isEmpty()
                ? Map.of()
                : attendanceCutoffRepository.findUserNames(userIds).stream()
                        .collect(Collectors.toMap(
                                RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
        List<CutoffResponse.Entry> history = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            AttendanceCutoff row = rows.get(i);
            LocalTime previous = i + 1 < rows.size() ? rows.get(i + 1).getCutoffTime() : null;
            history.add(new CutoffResponse.Entry(
                    row.getId(),
                    row.getCutoffTime(),
                    row.getEffectiveFrom(),
                    previous,
                    row.getNote(),
                    row.getCreatedBy(),
                    row.getCreatedBy() == null ? null : names.get(row.getCreatedBy()),
                    row.getCreatedAt(),
                    row.getLatestAfternoonArrival(),
                    row.getDropoffOpenFrom(),
                    row.getMorningStart(),
                    row.getMorningEnd(),
                    row.getAfternoonStart(),
                    row.getPickupStart(),
                    row.getFullDayPickupFrom(),
                    row.getPickupEnd(),
                    row.getAfternoonEnd()));
        }
        LocalTime todayTime = attendanceCutoffRepository
                .findFirstBySchoolYearIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                        year.getId(), schoolConfigAccess.today())
                .map(AttendanceCutoff::getCutoffTime)
                .orElse(null);
        CutoffResponse.Setting setting = null;
        if (!history.isEmpty()) {
            CutoffResponse.Entry latest = history.getFirst();
            setting = new CutoffResponse.Setting(
                    year.getId(),
                    latest.time(),
                    latest.effectiveFrom(),
                    latest.note(),
                    latest.userId(),
                    latest.userName(),
                    latest.at(),
                    history,
                    latest.latestAfternoonArrival(),
                    latest.dropoffOpenFrom(),
                    latest.morningStart(),
                    latest.morningEnd(),
                    latest.afternoonStart(),
                    latest.pickupStart(),
                    latest.fullDayPickupFrom(),
                    latest.pickupEnd(),
                    latest.afternoonEnd());
        }
        return new CutoffResponse(
                new CutoffResponse.Year(
                        year.getId(), year.getName(), year.getStartDate(), year.getEndDate(), year.getStatus()),
                setting,
                todayTime);
    }

    private SchoolYear findYear(Long schoolYearId) {
        return schoolYearRepository
                .findById(schoolYearId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
    }

    private static LocalTime scheduleValue(LocalTime requested, LocalTime previous, LocalTime fallback) {
        LocalTime value = requested != null ? requested : previous == null ? fallback : previous;
        return value.withSecond(0).withNano(0);
    }
}
