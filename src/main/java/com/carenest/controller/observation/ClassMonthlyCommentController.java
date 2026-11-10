package com.carenest.controller.observation;

import com.carenest.dto.observation.ClassMonthlyCommentDtos.ClassMonthlyCommentRequest;
import com.carenest.dto.observation.ClassMonthlyCommentDtos.ClassMonthlyCommentResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ClassMonthlyCommentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Nhận xét chung cả lớp theo tháng (OBS-26): GV lớp soạn, xác nhận; HT, PHT xem bản đã xác nhận. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClassMonthlyCommentController {
    private static final String LOG_PREFIX = "[ClassMonthlyCommentController]";

    private final ClassMonthlyCommentService classMonthlyCommentService;

    @GetMapping("/assessment/class-monthly-comments")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ClassMonthlyCommentResponse>> getComment(
            @RequestParam Long classId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getComment|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        ClassMonthlyCommentResponse response = classMonthlyCommentService.get(currentUser, classId, month);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class monthly comment success", response);
    }

    @PutMapping("/assessment/class-monthly-comments")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<ClassMonthlyCommentResponse>> saveComment(
            @Valid @RequestBody ClassMonthlyCommentRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveComment|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        ClassMonthlyCommentResponse response = classMonthlyCommentService.save(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save class monthly comment success", response);
    }

    @PostMapping("/assessment/class-monthly-comments/confirm")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<ClassMonthlyCommentResponse>> confirmComment(
            @Valid @RequestBody ClassMonthlyCommentRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|confirmComment|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        ClassMonthlyCommentResponse response = classMonthlyCommentService.confirm(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Confirm class monthly comment success", response);
    }
}
