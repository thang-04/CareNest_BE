package com.carenest.controller.announcement;

import com.carenest.dto.announcement.AnnouncementScheduleRequest;
import com.carenest.dto.announcement.AnnouncementScheduleResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.announcement.AnnouncementScheduleService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lịch gửi lặp thông báo của trường (PAR-10): chỉ HT. */
@RestController
@RequestMapping("/announcement-schedules")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('PRINCIPAL')")
public class AnnouncementScheduleController {
    private static final String LOG_PREFIX = "[AnnouncementScheduleController]";

    private final AnnouncementScheduleService scheduleService;

    @GetMapping
    public ResponseEntity<ResponseJson<List<AnnouncementScheduleResponse>>> listSchedules(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listSchedules|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<AnnouncementScheduleResponse> list = scheduleService.listSchedules(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get announcement schedules success", list);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<AnnouncementScheduleResponse>> createSchedule(
            @RequestBody AnnouncementScheduleRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createSchedule|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AnnouncementScheduleResponse created = scheduleService.createSchedule(currentUser, request);
        log.info("{}|scheduleId={}|END", logPrefix, created.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create announcement schedule success", created);
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<ResponseJson<AnnouncementScheduleResponse>> updateSchedule(
            @PathVariable Long scheduleId, @RequestBody AnnouncementScheduleRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateSchedule|userId=" + currentUser.userId() + "|scheduleId=" + scheduleId;
        log.info("{}|START", logPrefix);
        AnnouncementScheduleResponse updated = scheduleService.updateSchedule(currentUser, scheduleId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update announcement schedule success", updated);
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<ResponseJson<Void>> deleteSchedule(@PathVariable Long scheduleId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteSchedule|userId=" + currentUser.userId() + "|scheduleId=" + scheduleId;
        log.info("{}|START", logPrefix);
        scheduleService.deleteSchedule(currentUser, scheduleId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete announcement schedule success");
    }
}
