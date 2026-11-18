package com.carenest.controller.announcement;

import com.carenest.dto.announcement.AnnouncementScheduleRequest;
import com.carenest.dto.announcement.AnnouncementScheduleResponse;
import com.carenest.dto.announcement.ScheduleActivationRequest;
import com.carenest.dto.announcement.ScheduleSelectionRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.announcement.AnnouncementScheduleService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<AnnouncementScheduleResponse>> createSchedule(
            @RequestBody AnnouncementScheduleRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createSchedule|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AnnouncementScheduleResponse created = scheduleService.createSchedule(currentUser, request);
        log.info("{}|scheduleId={}|END", logPrefix, created.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create announcement schedule success", created);
    }

    @PutMapping(value = "/{scheduleId}", consumes = MediaType.APPLICATION_JSON_VALUE)
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

    @GetMapping("/{scheduleId}")
    public ResponseEntity<ResponseJson<AnnouncementScheduleResponse>> getSchedule(
            @PathVariable Long scheduleId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSchedule|userId=" + currentUser.userId() + "|scheduleId=" + scheduleId;
        log.info("{}|START", logPrefix);
        AnnouncementScheduleResponse result = scheduleService.getSchedule(currentUser, scheduleId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get announcement schedule success", result);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseJson<AnnouncementScheduleResponse>> createScheduleWithFiles(
            @RequestPart("data") AnnouncementScheduleRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createScheduleWithFiles|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AnnouncementScheduleResponse result = scheduleService.createSchedule(currentUser, request, files);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create announcement schedule success", result);
    }

    @PutMapping(value = "/{scheduleId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseJson<AnnouncementScheduleResponse>> updateScheduleWithFiles(
            @PathVariable Long scheduleId,
            @RequestPart("data") AnnouncementScheduleRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|updateScheduleWithFiles|userId=" + currentUser.userId() + "|scheduleId=" + scheduleId;
        log.info("{}|START", logPrefix);
        AnnouncementScheduleResponse result = scheduleService.updateSchedule(currentUser, scheduleId, request, files);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update announcement schedule success", result);
    }

    @PostMapping("/activation")
    public ResponseEntity<ResponseJson<Integer>> activateSchedules(
            @RequestBody ScheduleActivationRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|activateSchedules|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        int count = scheduleService.activateSchedules(currentUser, request);
        log.info("{}|count={}|END", logPrefix, count);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update schedule activation success", count);
    }

    @PostMapping("/delete-selection")
    public ResponseEntity<ResponseJson<Integer>> deleteSchedules(
            @RequestBody ScheduleSelectionRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteSchedules|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        int count = scheduleService.deleteSchedules(currentUser, request == null ? null : request.ids());
        log.info("{}|count={}|END", logPrefix, count);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Delete announcement schedules success", count);
    }
}
