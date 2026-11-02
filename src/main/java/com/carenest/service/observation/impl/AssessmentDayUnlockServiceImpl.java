package com.carenest.service.observation.impl;

import com.carenest.dto.observation.AssessmentDayUnlockRequest;
import com.carenest.dto.observation.AssessmentDayUnlockResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.AssessmentDayUnlock;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.AssessmentDayUnlockRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.observation.AssessmentDayUnlockService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AssessmentDayUnlockServiceImpl implements AssessmentDayUnlockService {

    private static final String LOG_PREFIX = "[AssessmentDayUnlockServiceImpl]";

    private final AssessmentDayUnlockRepository unlockRepository;
    private final ObservationDayRules rules;
    private final ClassroomService classroomService;
    private final AccountService accountService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentDayUnlockResponse> getUnlocks(
            CurrentUser currentUser, Long campusId, Long classId, Boolean active, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getUnlocks|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            if (!rules.isLeader(currentUser)) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
            }
            if (from != null && to != null && to.isBefore(from)) {
                throw GlobalException.of(ApiCode.INVALID_DATE_RANGE);
            }
            Instant now = rules.now();
            Map<Long, Boolean> campusAllowed = new HashMap<>();
            // Mới nhất trước; PHT chỉ thấy lớp điểm trường mình
            List<AssessmentDayUnlock> unlocks = unlockRepository.findAllByOrderByUnlockedAtDescIdDesc().stream()
                    .filter(u ->
                            campusAllowed.computeIfAbsent(u.getCampusId(), id -> rules.leadsCampus(currentUser, id)))
                    .filter(u -> campusId == null || campusId.equals(u.getCampusId()))
                    .filter(u -> classId == null || classId.equals(u.getClassroomId()))
                    .filter(u -> active == null || active == u.isActiveAt(now))
                    .filter(u -> from == null || !u.getDate().isBefore(from))
                    .filter(u -> to == null || !u.getDate().isAfter(to))
                    .toList();
            Map<Long, String> names = accountService.findFullNames(unlocks.stream()
                    .flatMap(u -> Stream.of(u.getUnlockedBy(), u.getRevokedBy()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
            Map<Long, String> classNames = new HashMap<>();
            List<AssessmentDayUnlockResponse> result = unlocks.stream()
                    .map(u -> ObservationDayRules.toView(
                            u,
                            classNames.computeIfAbsent(
                                    u.getClassroomId(),
                                    id -> classroomService
                                            .findClassroomInfo(id)
                                            .map(ClassroomInfoResponse::name)
                                            .orElse(null)),
                            names,
                            now))
                    .toList();
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AssessmentDayUnlockResponse unlockDay(CurrentUser currentUser, AssessmentDayUnlockRequest request) {
        String logPrefix = LOG_PREFIX + "|unlockDay|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(request.classId());
            if (!rules.leadsCampus(currentUser, cls.campusId())) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
            }
            if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_YEAR_NOT_ACTIVE);
            }
            LocalDate date = request.date();
            LocalDate today = rules.today();
            if (!ObservationDayRules.isSchoolDay(date)
                    || date.isAfter(today)
                    || (cls.yearStart() != null && date.isBefore(cls.yearStart()))) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_DATE_OUT_OF_RANGE);
            }
            if (!rules.isBaseLocked(date)) {
                throw GlobalException.of(ApiCode.ASSESSMENT_DAY_NOT_LOCKED_YET);
            }
            // Hạn do người mở chọn: sau hiện tại, không quá hết ngày kết thúc năm học (không giới hạn số ngày)
            Instant now = rules.now();
            Instant yearEndAt = cls.yearEnd() == null
                    ? null
                    : cls.yearEnd()
                            .atTime(LocalTime.MAX)
                            .atZone(clock.getZone())
                            .toInstant();
            if (!request.validUntil().isAfter(now)
                    || (yearEndAt != null && request.validUntil().isAfter(yearEndAt))) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_DEADLINE_INVALID);
            }
            if (rules.activeUnlock(cls.id(), date).isPresent()) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_ALREADY_ACTIVE);
            }
            AssessmentDayUnlock unlock = unlockRepository.save(AssessmentDayUnlock.create(
                    cls.id(),
                    cls.campusId(),
                    date,
                    request.validUntil(),
                    request.reason().trim(),
                    currentUser.userId(),
                    now));
            AssessmentDayUnlockResponse response = rules.toView(unlock, cls.name());
            log.info("{}|unlockId={}|END", logPrefix, unlock.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AssessmentDayUnlockResponse revokeUnlock(CurrentUser currentUser, Long unlockId) {
        String logPrefix = LOG_PREFIX + "|revokeUnlock|userId=" + currentUser.userId() + "|unlockId=" + unlockId;
        log.info("{}|START", logPrefix);
        try {
            AssessmentDayUnlock unlock = unlockRepository
                    .findById(unlockId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_NOT_FOUND));
            if (!rules.leadsCampus(currentUser, unlock.getCampusId())) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_FORBIDDEN);
            }
            Instant now = rules.now();
            if (!unlock.isActiveAt(now)) {
                throw GlobalException.of(ApiCode.ASSESSMENT_UNLOCK_ALREADY_ENDED);
            }
            // Kết thúc sớm: hạn = lúc thu hồi, giữ bản ghi làm lịch sử
            unlock.revoke(currentUser.userId(), now);
            unlockRepository.flush();
            String className = classroomService
                    .findClassroomInfo(unlock.getClassroomId())
                    .map(ClassroomInfoResponse::name)
                    .orElse(null);
            AssessmentDayUnlockResponse response = rules.toView(unlock, className);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
