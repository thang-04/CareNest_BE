package com.carenest.controller.observation;

import com.carenest.dto.observation.ParentActivityDtos.ParentDayActivitiesResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ParentActivityService;
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

/** App phụ huynh: hoạt động trong ngày của lớp con (EDU-37, AUTH-04). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ParentActivityController {
    private static final String LOG_PREFIX = "[ParentActivityController]";

    private final ParentActivityService parentActivityService;

    @GetMapping("/children/{childId}/activities")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<ParentDayActivitiesResponse>> getDayActivities(
            @PathVariable Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDayActivities|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ParentDayActivitiesResponse response = parentActivityService.getDayActivities(currentUser, childId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child day activities success", response);
    }
}
