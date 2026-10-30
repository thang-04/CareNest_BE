package com.carenest.controller.observation;

import com.carenest.dto.observation.RewardDtos.RewardClassDetail;
import com.carenest.dto.observation.RewardDtos.RewardClassRow;
import com.carenest.dto.observation.RewardDtos.RewardDecisionRequest;
import com.carenest.dto.observation.RewardDtos.RewardListRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.RewardListService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đề xuất khen thưởng cuối năm theo danh sách lớp: GV lập, PHT xem xét, HT phê duyệt. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class RewardListController {
    private static final String LOG_PREFIX = "[RewardListController]";

    private final RewardListService rewardListService;

    @GetMapping("/assessment/reward-lists")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<RewardClassRow>>> classes(
            @RequestParam(required = false) Long schoolYearId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|classes|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<RewardClassRow> rows = rewardListService.classes(currentUser, schoolYearId);
        log.info("{}|rows={}|END", logPrefix, rows.size());
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get reward lists success", rows);
    }

    @GetMapping("/assessment/reward-lists/classes/{classId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<RewardClassDetail>> detail(@PathVariable Long classId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|detail|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        RewardClassDetail detail = rewardListService.detail(currentUser, classId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get reward list success", detail);
    }

    @PutMapping("/assessment/reward-lists/classes/{classId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<RewardClassDetail>> save(
            @PathVariable Long classId,
            @RequestBody RewardListRequest request,
            @RequestParam(defaultValue = "false") boolean submit,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|save|userId=" + currentUser.userId() + "|classId=" + classId + "|submit=" + submit;
        log.info("{}|START", logPrefix);
        RewardClassDetail detail = rewardListService.save(currentUser, classId, request, submit);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save reward list success", detail);
    }

    @PostMapping("/assessment/reward-lists/{id}/review")
    @PreAuthorize("hasRole('VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<RewardClassDetail>> review(
            @PathVariable Long id, @RequestBody RewardDecisionRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|review|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        RewardClassDetail detail = rewardListService.review(currentUser, id, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Review reward list success", detail);
    }

    @PostMapping("/assessment/reward-lists/{id}/decision")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<RewardClassDetail>> decide(
            @PathVariable Long id, @RequestBody RewardDecisionRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|decide|userId=" + currentUser.userId() + "|id=" + id;
        log.info("{}|START", logPrefix);
        RewardClassDetail detail = rewardListService.decide(currentUser, id, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Decide reward list success", detail);
    }
}
