package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.EduAiRequest;
import com.carenest.dto.eduplan.EduAiResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduAiService;
import com.carenest.service.eduplan.impl.rag.EduRagRetriever;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI hỗ trợ soạn và duyệt kế hoạch giáo dục (EDU-95, EDU-96): kết quả là nháp, không lưu. */
@Slf4j
@RestController
@RequestMapping("/education-plans/ai")
@RequiredArgsConstructor
public class EduAiController {
    private static final String LOG_PREFIX = "[EduAiController]";

    private final EduAiService eduAiService;
    private final EduRagRetriever ragRetriever;

    @PostMapping("/lesson-steps")
    // EDU-108, EDU-110 (12): theo mã soạn của loại hồ sơ (HT cấu hình), không theo role
    @PreAuthorize("@permissionGuard.has('EDU_THEME_PLAN_EDIT')")
    public ResponseEntity<ResponseJson<EduAiResponse.LessonSteps>> suggestLessonSteps(
            @Valid @RequestBody EduAiRequest.LessonSteps request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|suggestLessonSteps|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        EduAiResponse.LessonSteps result = eduAiService.suggestLessonSteps(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Suggest lesson steps success", result);
    }

    @PostMapping("/activities")
    // EDU-108, EDU-110 (12): theo mã soạn của loại hồ sơ (HT cấu hình), không theo role
    @PreAuthorize("@permissionGuard.has('EDU_THEME_PLAN_EDIT')")
    public ResponseEntity<ResponseJson<List<EduAiResponse.Activity>>> suggestActivities(
            @Valid @RequestBody EduAiRequest.Activities request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|suggestActivities|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<EduAiResponse.Activity> result = eduAiService.suggestActivities(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Suggest activities success", result);
    }

    @PostMapping("/class-docs/day/{docKey}/edit")
    // EDU-108, EDU-110 (12): theo mã soạn của loại hồ sơ (HT cấu hình), không theo role
    @PreAuthorize("@permissionGuard.has('EDU_THEME_PLAN_EDIT')")
    public ResponseEntity<ResponseJson<EduAiResponse.DayEdit>> editDay(
            @PathVariable String docKey, @Valid @RequestBody EduAiRequest.EditDay request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|editDay|userId=" + currentUser.userId() + "|docKey=" + docKey;
        log.info("{}|START", logPrefix);
        EduAiResponse.DayEdit result = eduAiService.editDay(currentUser, docKey, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Edit day plan by request success", result);
    }

    @PostMapping("/class-docs/day/{docKey}/review")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<EduAiResponse.DayReview>> reviewDay(
            @PathVariable String docKey, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|reviewDay|userId=" + currentUser.userId() + "|docKey=" + docKey;
        log.info("{}|START", logPrefix);
        EduAiResponse.DayReview result = eduAiService.reviewDay(currentUser, docKey);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Review day plan success", result);
    }

    /** EDU-103: quyền thật (tick được ở bản đang soạn của kế hoạch tổ) kiểm ở service. */
    @PostMapping("/team-plans/{planId}/theme-content-map")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<EduAiResponse.ThemeContentMap>> matchThemeContents(
            @PathVariable Long planId,
            @Valid @RequestBody EduAiRequest.ThemeContentMap request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|matchThemeContents|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        EduAiResponse.ThemeContentMap result = eduAiService.matchThemeContents(currentUser, planId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Match theme contents success", result);
    }

    @PostMapping("/year-plans/{planId}/goal-content-map")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<EduAiResponse.GoalContentMap>> matchGoalContents(
            @PathVariable Long planId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|matchGoalContents|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        EduAiResponse.GoalContentMap result = eduAiService.matchGoalContents(currentUser, planId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Match goal contents success", result);
    }

    /** RAG bước (a): thử truy xuất đoạn tham khảo (MT, hoạt động, giáo án mẫu); chỉ đọc kho công khai. */
    @GetMapping("/rag/search")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<EduAiResponse.RagHit>>> searchRag(
            @RequestParam String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer k,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|searchRag|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<EduAiResponse.RagHit> result = ragRetriever.search(q, type, k);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Search RAG chunks success", result);
    }

    @PostMapping("/feedback")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<Void>> logFeedback(
            @Valid @RequestBody EduAiRequest.Feedback request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|logFeedback|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        eduAiService.logFeedback(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Log AI feedback success");
    }
}
