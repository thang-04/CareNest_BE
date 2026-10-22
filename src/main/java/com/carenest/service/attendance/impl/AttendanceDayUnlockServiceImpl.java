package com.carenest.service.attendance.impl;

import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.DayUnlockResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceDayUnlock;
import com.carenest.exception.GlobalException;
import com.carenest.repository.attendance.AttendanceDayUnlockRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceDayUnlockServiceImpl implements AttendanceDayUnlockService {
    private static final String LOG_PREFIX = "[AttendanceDayUnlockServiceImpl]";

    private final AttendanceDayUnlockRepository unlockRepository;
    private final AttendanceDayRules rules;
    private final ClassroomService classroomService;
    private final AccountService accountService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<DayUnlockResponse> getUnlocks(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getUnlocks|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            if (!currentUser.hasRole(CareNestRole.PRINCIPAL) && !currentUser.hasRole(CareNestRole.VICE_PRINCIPAL)) {
                throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to attendance unlocks");
            }
            Map<Long, Boolean> campusAllowed = new HashMap<>();
            List<AttendanceDayUnlock> unlocks =
                    unlockRepository.findAll(Sort.by(Sort.Direction.DESC, "unlockedAt")).stream()
                            .filter(u -> campusAllowed.computeIfAbsent(
                                    u.getCampusId(), id -> rules.canUnlock(currentUser, id)))
                            .toList();
            Map<Long, String> names = accountService.findFullNames(unlocks.stream()
                    .flatMap(u -> Stream.of(u.getUnlockedBy(), u.getRevokedBy()))
                    .filter(Objects::nonNull)
                    .toList());
            Map<Long, String> classNames = new HashMap<>();
            List<DayUnlockResponse> result = unlocks.stream()
                    .map(u -> AttendanceDayRules.toUnlockView(
                            u,
                            classNames.computeIfAbsent(
                                    u.getClassroomId(),
                                    id -> classroomService
                                            .findClassroomInfo(id)
                                            .map(ClassroomInfoResponse::name)
                                            .orElse(null)),
                            names))
                    .toList();
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public DayUnlockResponse unlockDay(CurrentUser currentUser, DayUnlockRequest request) {
        String logPrefix = LOG_PREFIX + "|unlockDay|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = classroomService
                    .findClassroomInfo(request.classId())
                    .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Class not found"));
            if (!rules.canUnlock(currentUser, cls.campusId())) {
                throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this class");
            }
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw new GlobalException(ApiCode.CONFLICT, "Only classes of the active school year can be unlocked");
            }
            LocalDate date = request.date();
            LocalDate today = rules.today();
            if (!AttendanceDayRules.isSchoolDay(date)
                    || date.isAfter(today)
                    || (cls.yearStart() != null && date.isBefore(cls.yearStart()))) {
                throw new GlobalException(
                        ApiCode.BAD_REQUEST, "Choose a school day from the start of the school year to today");
            }
            if (!rules.isBaseLocked(date, rules.cutoff(cls.schoolYearId(), date))) {
                throw new GlobalException(ApiCode.CONFLICT, "Attendance of this day is not locked yet");
            }
            // Cần chốt 21/10/2026: hạn do người mở chọn, sau hiện tại và không quá hết ngày kết thúc năm học
            Instant now = rules.now();
            Instant yearEndAt = cls.yearEnd() == null
                    ? null
                    : cls.yearEnd()
                            .atTime(LocalTime.MAX)
                            .atZone(clock.getZone())
                            .toInstant();
            if (!request.validUntil().isAfter(now)
                    || (yearEndAt != null && request.validUntil().isAfter(yearEndAt))) {
                throw new GlobalException(
                        ApiCode.BAD_REQUEST,
                        "Unlock deadline must be after now and not after the end of the school year");
            }
            String reason = request.reason().trim();
            if (rules.activeUnlock(cls.id(), date).isPresent()) {
                throw new GlobalException(ApiCode.CONFLICT, "This class already has an active unlock for this day");
            }
            AttendanceDayUnlock unlock = unlockRepository.save(AttendanceDayUnlock.create(
                    cls.id(), cls.campusId(), date, request.validUntil(), reason, currentUser.userId(), now));
            DayUnlockResponse response = rules.toUnlockView(unlock, cls.name());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public DayUnlockResponse revokeUnlock(CurrentUser currentUser, Long unlockId) {
        String logPrefix = LOG_PREFIX + "|revokeUnlock|userId=" + currentUser.userId() + "|unlockId=" + unlockId;
        log.info("{}|START", logPrefix);
        try {
            AttendanceDayUnlock unlock = unlockRepository
                    .findById(unlockId)
                    .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Attendance unlock not found"));
            if (!rules.canUnlock(currentUser, unlock.getCampusId())) {
                throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this class");
            }
            Instant now = rules.now();
            if (!unlock.isActiveAt(now)) {
                throw new GlobalException(ApiCode.CONFLICT, "This unlock has already ended");
            }
            unlock.revoke(currentUser.userId(), now);
            String className = classroomService
                    .findClassroomInfo(unlock.getClassroomId())
                    .map(ClassroomInfoResponse::name)
                    .orElse(null);
            DayUnlockResponse response = rules.toUnlockView(unlock, className);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
