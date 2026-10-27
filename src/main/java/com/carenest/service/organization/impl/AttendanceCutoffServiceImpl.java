package com.carenest.service.organization.impl;

import com.carenest.dto.organization.CutoffRequest;
import com.carenest.dto.organization.CutoffResponse;
import com.carenest.entity.organization.AttendanceCutoff;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.repository.organization.AttendanceCutoffRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AttendanceCutoffService;
import com.carenest.utils.ApiCode;
import java.time.LocalDate;
import java.time.LocalTime;
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
            if (!history.isEmpty()
                    && history.getFirst().getCutoffTime().equals(time)
                    && history.getFirst().getEffectiveFrom().equals(request.effectiveFrom())) {
                throw GlobalException.of(ApiCode.CUTOFF_UNCHANGED);
            }
            attendanceCutoffRepository.saveAndFlush(AttendanceCutoff.create(
                    schoolYearId,
                    time,
                    request.effectiveFrom(),
                    request.note() == null ? "" : request.note().trim(),
                    currentUser.userId()));
            // TODO(Cần): báo các PHT khi đổi giờ chốt — chờ module notification
            CutoffResponse response = toResponse(year);
            log.info("{}|END", logPrefix);
            return response;
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
                    row.getCreatedAt()));
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
                    history);
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
}
