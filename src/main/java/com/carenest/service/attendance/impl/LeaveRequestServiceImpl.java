package com.carenest.service.attendance.impl;

import com.carenest.dto.attendance.LeaveRequestDtos.CreateLeaveRequest;
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveRequestResponse;
import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.entity.attendance.AttendanceRecordHistory;
import com.carenest.entity.attendance.LeaveRequest;
import com.carenest.exception.GlobalException;
import com.carenest.repository.attendance.AttendanceRecordHistoryRepository;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.LeaveRequestRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.LeaveRequestService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ATT-04, ATT-05, ATT-05b: đơn nghỉ là thông báo, không duyệt. Ngày học chưa qua giờ chốt được ghi Vắng có phép, không
 * suất (sổ báo ăn tính theo điểm danh); ngày đã qua giờ chốt giữ nguyên. Trẻ vẫn đến lớp thì GV sửa trước giờ chốt (P-20).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LeaveRequestServiceImpl implements LeaveRequestService {
    private static final String LOG_PREFIX = "[LeaveRequestServiceImpl]";
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceRecordHistoryRepository historyRepository;
    private final AttendanceDayRules rules;
    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final AccountService accountService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public LeaveRequestResponse createLeaveRequest(CurrentUser currentUser, CreateLeaveRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createLeaveRequest|userId=" + currentUser.userId() + "|childId=" + request.childId();
        log.info("{}|START", logPrefix);
        try {
            checkGuardian(currentUser, request.childId());
            LocalDate from = request.fromDate();
            LocalDate to = request.toDate();
            if (to.isBefore(from)) {
                throw GlobalException.of(ApiCode.LEAVE_DATE_RANGE_INVALID);
            }
            if (from.isBefore(rules.today())) {
                throw GlobalException.of(ApiCode.LEAVE_DATE_IN_PAST);
            }
            for (LeaveRequest other : leaveRequestRepository.findOverlapping(List.of(request.childId()), from, to)) {
                if (!other.effectiveTo().isBefore(from) && !other.effectiveTo().isBefore(other.getFromDate())) {
                    LocalDate clash = other.getFromDate().isAfter(from) ? other.getFromDate() : from;
                    throw GlobalException.of(ApiCode.LEAVE_OVERLAP, clash.format(DD_MM_YYYY));
                }
            }
            List<LocalDate> applied = new ArrayList<>();
            List<LocalDate> skipped = new ArrayList<>();
            Map<LocalDate, ClassroomInfoResponse> classOf = new HashMap<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (!AttendanceDayRules.isSchoolDay(d)) {
                    continue;
                }
                ClassroomInfoResponse cls = activeClassOf(request.childId(), d);
                // ATT-05b: ngày đã qua giờ chốt giữ nguyên điểm danh và số suất
                if (rules.isBaseLocked(d, rules.cutoff(cls.schoolYearId(), d))) {
                    skipped.add(d);
                } else {
                    applied.add(d);
                    classOf.put(d, cls);
                }
            }
            if (applied.isEmpty()) {
                throw GlobalException.of(ApiCode.LEAVE_NO_SCHOOL_DAY);
            }
            Instant at = rules.now();
            LeaveRequest saved = leaveRequestRepository.save(LeaveRequest.create(
                    request.childId(), from, to, request.reason().trim(), currentUser.userId(), at));
            for (LocalDate d : applied) {
                markExcused(request.childId(), classOf.get(d), d, currentUser.userId(), at);
            }
            LeaveRequestResponse response = toResponse(saved, classOf.get(applied.getFirst()), applied, skipped);
            log.info("{}|leaveRequestId={}|days={}|END", logPrefix, saved.getId(), applied.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> findMyLeaveRequests(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|findMyLeaveRequests|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            List<Long> childIds = guardianChildLinkService.findChildIdsOfGuardian(currentUser.userId());
            List<LeaveRequestResponse> result = childIds.isEmpty()
                    ? List.of()
                    : leaveRequestRepository.findByChildIdInOrderByFromDateDescIdDesc(childIds).stream()
                            .map(r -> toResponse(r, classOnOrNull(r.getChildId(), r.getFromDate()), null, null))
                            .toList();
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public LeaveRequestResponse cancelLeaveRequest(CurrentUser currentUser, Long leaveRequestId) {
        String logPrefix =
                LOG_PREFIX + "|cancelLeaveRequest|userId=" + currentUser.userId() + "|leaveRequestId=" + leaveRequestId;
        log.info("{}|START", logPrefix);
        try {
            LeaveRequest leave = leaveRequestRepository
                    .findById(leaveRequestId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.LEAVE_NOT_FOUND));
            checkGuardian(currentUser, leave.getChildId());
            LocalDate today = rules.today();
            LocalDate end = leave.effectiveTo();
            LocalDate first = null;
            for (LocalDate d = leave.getFromDate().isBefore(today) ? today : leave.getFromDate();
                    !d.isAfter(end);
                    d = d.plusDays(1)) {
                if (!isLocked(leave.getChildId(), d)) {
                    first = d;
                    break;
                }
            }
            if (first == null) {
                throw GlobalException.of(ApiCode.LEAVE_CANNOT_CANCEL);
            }
            Instant at = rules.now();
            leave.cancelFrom(first, currentUser.userId(), at);
            for (LocalDate d = first; !d.isAfter(end); d = d.plusDays(1)) {
                LocalDate day = d;
                // Chỉ bỏ ghi do đơn nghỉ; GV đã sửa (trẻ vẫn đến lớp) thì giữ
                recordRepository.findByChildIdInAndDate(List.of(leave.getChildId()), day).stream()
                        .filter(r -> r.getStatus() == AttendanceStatus.EXCUSED
                                && r.getRecordedBy().equals(leave.getCreatedBy()))
                        .findFirst()
                        .ifPresent(r -> {
                            recordRepository.delete(r);
                            eventPublisher.publishEvent(new AttendanceService.AttendanceChangedEvent(
                                    day, r.getCampusId(), r.getClassroomId()));
                        });
            }
            leaveRequestRepository.flush();
            LeaveRequestResponse response =
                    toResponse(leave, classOnOrNull(leave.getChildId(), leave.getFromDate()), null, null);
            log.info("{}|cancelledFrom={}|END", logPrefix, first);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> searchLeaveRequests(
            CurrentUser currentUser, Long classId, Long campusId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|searchLeaveRequests|userId=" + currentUser.userId() + "|classId=" + classId
                + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate start = from != null ? from : rules.today().withDayOfMonth(1);
            LocalDate end = to != null ? to : start.withDayOfMonth(start.lengthOfMonth());
            if (end.isBefore(start)) {
                throw GlobalException.of(ApiCode.LEAVE_DATE_RANGE_INVALID);
            }
            if (campusId != null && !campusAccessPolicy.canViewCampus(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            List<ClassroomInfoResponse> classes = classroomService.findActiveYearClassrooms().stream()
                    .filter(c -> classId == null || c.id().equals(classId))
                    .filter(c -> campusId == null || c.campusId().equals(campusId))
                    .toList();
            if (classId != null && (classes.isEmpty() || !rules.canView(currentUser, classes.getFirst()))) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            // GV: lớp mình; PHT: lớp điểm trường mình; HT: mọi lớp (AUTH-01..03)
            Map<Long, ClassroomInfoResponse> classOfChild = new HashMap<>();
            Map<Long, String> names = new HashMap<>();
            LocalDate rosterDay = start.isBefore(rules.today()) ? rules.today() : start;
            for (ClassroomInfoResponse c : classes) {
                if (!rules.canView(currentUser, c)) {
                    continue;
                }
                for (ClassChildResponse ch : enrollmentService.findChildrenInClassroom(c.id(), rosterDay)) {
                    classOfChild.putIfAbsent(ch.id(), c);
                    names.putIfAbsent(ch.id(), ch.fullName());
                }
            }
            List<LeaveRequestResponse> result = classOfChild.isEmpty()
                    ? List.of()
                    : leaveRequestRepository.findOverlapping(classOfChild.keySet(), start, end).stream()
                            .filter(r -> !r.effectiveTo().isBefore(r.getFromDate()))
                            .map(r -> toResponse(r, classOfChild.get(r.getChildId()), null, null, names))
                            .toList();
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void checkGuardian(CurrentUser user, Long childId) {
        if (!guardianChildLinkService.isGuardianOf(user.userId(), childId)) {
            throw GlobalException.of(ApiCode.LEAVE_NOT_GUARDIAN);
        }
    }

    private boolean isLocked(Long childId, LocalDate date) {
        return classOn(childId, date)
                .map(c -> rules.isBaseLocked(date, rules.cutoff(c.schoolYearId(), date)))
                .orElse(date.isBefore(rules.today()));
    }

    private ClassroomInfoResponse activeClassOf(Long childId, LocalDate date) {
        return classOn(childId, date)
                .filter(c -> c.yearStatus() == SchoolYearStatus.ACTIVE)
                .orElseThrow(() -> GlobalException.of(ApiCode.LEAVE_CHILD_NO_CLASS, date.format(DD_MM_YYYY)));
    }

    private Optional<ClassroomInfoResponse> classOn(Long childId, LocalDate date) {
        return enrollmentService.findActiveClassroomIds(childId, date).stream()
                .findFirst()
                .flatMap(classroomService::findClassroomInfo);
    }

    private ClassroomInfoResponse classOnOrNull(Long childId, LocalDate date) {
        return classOn(childId, date).or(() -> classOn(childId, rules.today())).orElse(null);
    }

    private void markExcused(Long childId, ClassroomInfoResponse cls, LocalDate date, Long userId, Instant at) {
        AttendanceRecord record = recordRepository.findByChildIdInAndDate(List.of(childId), date).stream()
                .findFirst()
                .orElse(null);
        String action;
        if (record == null) {
            record = recordRepository.save(AttendanceRecord.create(
                    childId, cls.id(), cls.campusId(), date, AttendanceStatus.EXCUSED, List.of(), userId, at));
            action = AttendanceRecordHistory.RECORDED;
        } else if (record.differsFrom(AttendanceStatus.EXCUSED, List.of())) {
            record.update(AttendanceStatus.EXCUSED, List.of(), userId, at);
            action = AttendanceRecordHistory.UPDATED;
        } else {
            return;
        }
        historyRepository.save(AttendanceRecordHistory.of(record, action, null));
        eventPublisher.publishEvent(new AttendanceService.AttendanceChangedEvent(date, cls.campusId(), cls.id()));
    }

    private LeaveRequestResponse toResponse(
            LeaveRequest r, ClassroomInfoResponse cls, List<LocalDate> applied, List<LocalDate> skipped) {
        Map<Long, String> names = new HashMap<>();
        if (cls != null) {
            LocalDate day = r.getFromDate().isBefore(rules.today()) ? rules.today() : r.getFromDate();
            enrollmentService.findChildrenInClassroom(cls.id(), day).forEach(ch -> names.put(ch.id(), ch.fullName()));
        }
        return toResponse(r, cls, applied, skipped, names);
    }

    private LeaveRequestResponse toResponse(
            LeaveRequest r,
            ClassroomInfoResponse cls,
            List<LocalDate> applied,
            List<LocalDate> skipped,
            Map<Long, String> childNames) {
        String creator = accountService.findFullNames(Set.of(r.getCreatedBy())).get(r.getCreatedBy());
        boolean cancelledAll =
                r.getCancelledFrom() != null && !r.getCancelledFrom().isAfter(r.getFromDate());
        return new LeaveRequestResponse(
                r.getId(),
                r.getChildId(),
                childNames.get(r.getChildId()),
                cls == null ? null : cls.id(),
                cls == null ? null : cls.name(),
                cls == null ? null : cls.campusId(),
                r.getFromDate(),
                r.getToDate(),
                cancelledAll ? null : r.effectiveTo(),
                r.getReason(),
                cancelledAll ? "CANCELLED" : "ACTIVE",
                r.getCreatedBy(),
                creator,
                r.getCreatedAt(),
                r.getCancelledAt(),
                applied == null ? List.of() : applied,
                skipped == null ? List.of() : skipped);
    }
}
