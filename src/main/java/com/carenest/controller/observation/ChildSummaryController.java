package com.carenest.controller.observation;

import com.carenest.dto.observation.ChildSummaryResponse;
import com.carenest.dto.observation.CreateSummaryDraftRequest;
import com.carenest.dto.observation.UpdateSummaryContentRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ChildSummaryService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Nhận xét tổng hợp: GV tạo nháp (AI/template), sửa, duyệt; BGH chỉ xem (OBS-05). Phụ huynh chờ P-12. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ChildSummaryController {
    private static final String LOG_PREFIX = "[ChildSummaryController]";

    private final ChildSummaryService childSummaryService;

    @PostMapping("/children/{childId}/summaries/draft")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<ChildSummaryResponse>> createDraft(
            @PathVariable Long childId,
            @Valid @RequestBody CreateSummaryDraftRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createDraft|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ChildSummaryResponse summary = childSummaryService.createDraft(currentUser, childId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Create summary draft success", summary);
    }

    @GetMapping("/children/{childId}/summaries")
    @PreAuthorize("hasAnyRole('TEACHER', 'VICE_PRINCIPAL', 'PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<ChildSummaryResponse>>> listSummaries(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listSummaries|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        List<ChildSummaryResponse> summaries = childSummaryService.listSummaries(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List summaries success", summaries);
    }

    @PutMapping("/summaries/{summaryId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<ChildSummaryResponse>> updateContent(
            @PathVariable Long summaryId,
            @Valid @RequestBody UpdateSummaryContentRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateContent|userId=" + currentUser.userId() + "|summaryId=" + summaryId;
        log.info("{}|START", logPrefix);
        ChildSummaryResponse summary = childSummaryService.updateContent(currentUser, summaryId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update summary success", summary);
    }

    @PostMapping("/summaries/{summaryId}/approve")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<ChildSummaryResponse>> approve(
            @PathVariable Long summaryId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|approve|userId=" + currentUser.userId() + "|summaryId=" + summaryId;
        log.info("{}|START", logPrefix);
        ChildSummaryResponse summary = childSummaryService.approve(currentUser, summaryId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Approve summary success", summary);
    }
}
