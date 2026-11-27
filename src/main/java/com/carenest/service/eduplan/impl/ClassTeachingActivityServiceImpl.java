package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.eduplan.ClassTeachingActivitiesResponse;
import com.carenest.dto.eduplan.ClassTeachingActivitiesResponse.TeachingDay;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.eduplan.ClassTeachingActivityService;
import com.carenest.service.eduplan.EduLessonQueryService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassTeachingActivityServiceImpl implements ClassTeachingActivityService {
    private static final String LOG_PREFIX = "[ClassTeachingActivityServiceImpl]";
    private static final int SCHOOL_DAYS = 5;

    private final ClassroomAccessPolicy classroomAccessPolicy;
    private final EduLessonQueryService lessonQueryService;
    private final Clock clock;

    @Override
    public ClassTeachingActivitiesResponse getWeek(CurrentUser currentUser, Long classId, LocalDate weekStart) {
        String logPrefix = LOG_PREFIX + "|getWeek|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-03: giáo viên chỉ lớp đang được phân công
            classroomAccessPolicy.checkCanViewClassroom(currentUser, classId);
            LocalDate monday = (weekStart == null ? LocalDate.now(clock) : weekStart).with(DayOfWeek.MONDAY);
            Map<LocalDate, ApprovedDayLessonResponse> lessons =
                    lessonQueryService.findReleasedDayActivities(classId, monday, monday.plusDays(SCHOOL_DAYS - 1L));
            List<TeachingDay> days = IntStream.range(0, SCHOOL_DAYS)
                    .mapToObj(monday::plusDays)
                    .map(date -> {
                        ApprovedDayLessonResponse lesson = lessons.get(date);
                        return new TeachingDay(date, lesson != null, lesson == null ? List.of() : lesson.activities());
                    })
                    .toList();
            log.info("{}|publishedDays={}|END", logPrefix, lessons.size());
            return new ClassTeachingActivitiesResponse(classId, monday, !lessons.isEmpty(), days);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
