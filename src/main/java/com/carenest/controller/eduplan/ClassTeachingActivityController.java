package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.ClassTeachingActivitiesResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.ClassTeachingActivityService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ClassTeachingActivityController {
    private static final String LOG_PREFIX = "[ClassTeachingActivityController]";

    private final ClassTeachingActivityService activityService;

    @GetMapping("/classes/{classId}/teaching-activities")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<ClassTeachingActivitiesResponse>> getWeek(
            @PathVariable Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getWeek|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        ClassTeachingActivitiesResponse week = activityService.getWeek(currentUser, classId, weekStart);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class teaching activities success", week);
    }
}
