package com.carenest.service.meals.impl;

import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.MealCount;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.meals.MealBookReminderService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MealBookReminderServiceImpl implements MealBookReminderService {
    private static final String LOG_PREFIX = "[MealBookReminderServiceImpl]";
    private static final Duration AFTER_CUTOFF = Duration.ofMinutes(30);

    private final AttendanceService attendanceService;
    private final ClassroomService classroomService;
    private final MealCountRepository mealCountRepository;
    private final StaffAssignmentService staffAssignmentService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Override
    @Transactional
    @Scheduled(cron = "0 */10 7-14 * * MON-FRI", zone = "${carenest.time-zone}")
    public int remindUnconfirmedMealBook() {
        String logPrefix = LOG_PREFIX + "|remindUnconfirmedMealBook";
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            DayInfoResponse info = attendanceService.getActiveYearDayInfo(today);
            int sent = 0;
            if (info != null && info.schoolDay() && info.cutoff() != null) {
                LocalDateTime remindAt =
                        today.atTime(LocalTime.parse(info.cutoff())).plus(AFTER_CUTOFF);
                if (!LocalDateTime.now(clock).isBefore(remindAt)) {
                    List<Long> campusIds = classroomService.findActiveYearClassrooms().stream()
                            .filter(c -> c.yearStatus() == SchoolYearStatus.ACTIVE)
                            .map(ClassroomInfoResponse::campusId)
                            .distinct()
                            .toList();
                    Set<Long> principals = staffAssignmentService.findPrincipalIds(today);
                    for (Long campusId : campusIds) {
                        List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(campusId, today);
                        if (!counts.isEmpty() && counts.stream().allMatch(MealCount::isConfirmed)) {
                            continue;
                        }
                        Set<Long> to = new HashSet<>(principals);
                        to.addAll(staffAssignmentService.findCampusVicePrincipalIds(campusId, today));
                        if (to.isEmpty()) {
                            continue;
                        }
                        // Mỗi điểm trường – ngày một lần (khóa nằm trong đường dẫn)
                        notificationService.notifyUsers(
                                to,
                                NotificationType.LEADER_REMINDER,
                                "Sổ báo ăn chưa xác nhận",
                                "Đã quá giờ chốt " + info.cutoff()
                                        + ", sổ báo ăn hôm nay của điểm trường chưa xác nhận.",
                                "/attendance/meal-count?campusId=" + campusId + "&date=" + today + "&r=meal-" + today,
                                true);
                        sent += to.size();
                    }
                }
            }
            log.info("{}|sent={}|END", logPrefix, sent);
            return sent;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
