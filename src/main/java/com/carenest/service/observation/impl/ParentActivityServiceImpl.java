package com.carenest.service.observation.impl;

import com.carenest.dto.eduplan.DayActivity;
import com.carenest.dto.observation.ParentActivityDtos.ParentDayActivitiesResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.observation.ParentActivityService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentActivityServiceImpl implements ParentActivityService {
    private static final String LOG_PREFIX = "[ParentActivityServiceImpl]";

    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentService enrollmentService;
    private final EduLessonQueryService eduLessonQueryService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ParentDayActivitiesResponse getDayActivities(CurrentUser currentUser, Long childId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getDayActivities|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-04: chỉ phụ huynh đã liên kết với trẻ
            if (!currentUser.hasRole(CareNestRole.PARENT)
                    || !guardianChildLinkService.isGuardianOf(currentUser.userId(), childId)) {
                throw GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN);
            }
            LocalDate day = date == null ? LocalDate.now(clock) : date;
            // Lớp trẻ học ngày đó; chưa có lớp ⇒ không có hoạt động
            List<DayActivity> activities = enrollmentService.findActiveClassroomIds(childId, day).stream()
                    .findFirst()
                    .map(classroomId -> eduLessonQueryService.findDayActivities(classroomId, day))
                    .orElse(List.of());
            ParentDayActivitiesResponse response = new ParentDayActivitiesResponse(day, activities);
            log.info("{}|rows={}|END", logPrefix, activities.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
