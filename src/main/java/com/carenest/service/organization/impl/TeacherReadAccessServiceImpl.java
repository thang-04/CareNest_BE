package com.carenest.service.organization.impl;

import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.service.organization.TeacherReadAccessService;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeacherReadAccessServiceImpl implements TeacherReadAccessService {
    private static final String LOG_PREFIX = "[TeacherReadAccessServiceImpl]";
    private final StaffAssignmentService staffAssignmentService;

    @Override
    public boolean covers(Long userId, Long classId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|covers|userId=" + userId + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            var result = coversRange(userId, classId, from, to);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public LocalDate latestDay(Long userId, Long classId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|latestDay|userId=" + userId + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            var result = latest(userId, classId, from, to);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<StaffAssignmentResponse> assignments(Long userId, Long classId) {
        return staffAssignmentService.findAssignments(userId).stream()
                .filter(a -> a.staffRole() == StaffRole.TEACHER && Objects.equals(a.classroomId(), classId))
                .filter(a -> a.validFrom() != null
                        && (a.validTo() == null || !a.validTo().isBefore(a.validFrom())))
                .sorted(Comparator.comparing(StaffAssignmentResponse::validFrom))
                .toList();
    }

    private boolean coversRange(Long userId, Long classId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) return false;
        LocalDate cursor = from;
        for (var assignment : assignments(userId, classId)) {
            LocalDate end = assignment.validTo() == null ? LocalDate.MAX : assignment.validTo();
            if (end.isBefore(cursor)) continue;
            if (assignment.validFrom().isAfter(cursor)) return false;
            if (!end.isBefore(to)) return true;
            cursor = end.plusDays(1);
        }
        return false;
    }

    private LocalDate latest(Long userId, Long classId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) return null;
        return assignments(userId, classId).stream()
                .filter(a -> !a.validFrom().isAfter(to)
                        && (a.validTo() == null || !a.validTo().isBefore(from)))
                .map(a -> a.validTo() == null || a.validTo().isAfter(to) ? to : a.validTo())
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
