package com.carenest.controller.eduplan;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.GoalFilePreviewResponse;
import com.carenest.dto.eduplan.PlanActionRequest;
import com.carenest.dto.eduplan.YearPlanChangesResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduGoalFileService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Mục tiêu năm học (kế hoạch giáo dục năm theo nhóm tuổi) — sequence SD-01..SD-08. */
@Validated
@RestController
@RequestMapping("/education-plans/goals")
@RequiredArgsConstructor
@Slf4j
public class EduYearPlanController {
    private static final String LOG_PREFIX = "[EduYearPlanController]";

    private final EduYearPlanService eduYearPlanService;
    private final EduGoalFileService eduGoalFileService;

    /** EDU-46: đọc file bảng mục tiêu (Word/Excel/CSV) thành bản xem trước; không lưu. */
    @PostMapping(value = "/file-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<GoalFilePreviewResponse>> previewGoalFile(
            @RequestPart MultipartFile file, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|previewGoalFile|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        GoalFilePreviewResponse preview = eduGoalFileService.preview(currentUser, file);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Preview goal file success", preview);
    }

    // Role chỉ là lớp 1; EduYearPlanService kiểm quyền goals.* và phạm vi nhóm tuổi (lớp 2)
    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<PageResponse<YearPlanSummaryResponse>>> getYearPlans(
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) Long ageGroupId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) com.carenest.utils.enums.EduPlanLevel planLevel,
            @RequestParam(required = false) com.carenest.utils.enums.EduPlanLevel planType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getYearPlans|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        if (planType != null && planLevel != null && planType != planLevel) {
            throw com.carenest.exception.GlobalException.of(ApiCode.INVALID_VALUE, "loại kế hoạch");
        }
        var level = planType != null
                ? planType
                : planLevel == null ? com.carenest.utils.enums.EduPlanLevel.TEAM : planLevel;
        PageResponse<YearPlanSummaryResponse> plans = eduYearPlanService.getYearPlans(
                currentUser,
                schoolYearId,
                ageGroupId,
                q,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")),
                level);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get year plans success", plans);
    }

    /** Kế hoạch năm/tổ đang chờ đúng người gọi duyệt (spec 002 FR-001, FR-007). */
    @GetMapping("/review-queue")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<java.util.List<YearPlanSummaryResponse>>> getReviewQueue(
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getReviewQueue|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        var plans = eduYearPlanService.getReviewQueue(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get review queue success", plans);
    }

    @GetMapping("/{planId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> getYearPlan(
            @PathVariable Long planId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.getYearPlan(currentUser, planId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get year plan success", plan);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    // Để OpenAPI ghi 201 khớp ApiCode.CREATED
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> createYearPlan(
            @Valid @RequestBody YearPlanRequest request,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix =
                LOG_PREFIX + "|createYearPlan|userId=" + currentUser.userId() + "|ageGroupId=" + request.ageGroupId();
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.createYearPlan(
                currentUser, request, new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|planId={}|END", logPrefix, plan.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create year plan success", plan);
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> updateYearPlan(
            @PathVariable Long planId,
            @Valid @RequestBody YearPlanRequest request,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|updateYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.updateYearPlan(
                currentUser, planId, request, new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update year plan success", plan);
    }

    @PostMapping("/{planId}/review/{reviewItemId}/resolve")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> resolveReview(
            @PathVariable Long planId,
            @PathVariable Long reviewItemId,
            @Valid @RequestBody ReviewResolutionRequest request,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|resolveReview|planId=" + planId;
        log.info("{}|START", logPrefix);
        var result = eduYearPlanService.resolveReview(
                currentUser,
                planId,
                reviewItemId,
                request.expectedSourcePlanId(),
                new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Review resolved", result);
    }

    public record ReviewResolutionRequest(
            @jakarta.validation.constraints.NotNull Long expectedSourcePlanId) {}

    @PostMapping("/{planId}/submit")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> submitYearPlan(
            @PathVariable Long planId,
            @Valid @RequestBody(required = false) PlanActionRequest.Note request,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|submitYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.submitYearPlan(
                currentUser,
                planId,
                request == null ? null : request.note(),
                new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Submit year plan success", plan);
    }

    @PostMapping("/{planId}/issue")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> issueYearPlan(
            @PathVariable Long planId,
            @Valid @RequestBody(required = false) PlanActionRequest.Note request,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|issueYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.issueYearPlan(
                currentUser,
                planId,
                request == null ? null : request.note(),
                new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Issue year plan success", plan);
    }

    @PostMapping("/{planId}/return")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> returnYearPlan(
            @PathVariable Long planId,
            @Valid @RequestBody PlanActionRequest.RequiredNote request,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|returnYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.returnYearPlan(
                currentUser, planId, request.note(), new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Return year plan success", plan);
    }

    @PostMapping("/{planId}/start-editing")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> startEditing(
            @PathVariable Long planId,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|startEditing|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.startEditing(
                currentUser, planId, new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Start editing year plan success", plan);
    }

    @PostMapping("/{planId}/finish-editing")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> finishEditing(
            @PathVariable Long planId,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|finishEditing|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.finishEditing(
                currentUser, planId, new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Finish editing year plan success", plan);
    }

    @PostMapping("/{planId}/editing-heartbeat")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> heartbeat(
            @PathVariable Long planId,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|heartbeat|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.heartbeat(
                currentUser, planId, new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Editing session renewed", plan);
    }

    /** Spec 004 (Q5): Hiệu trưởng mở khóa kế hoạch năm học / tổ (lý do + hạn mới). */
    @PostMapping("/{planId}/unlock")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> unlockYearPlan(
            @PathVariable Long planId,
            @RequestBody com.carenest.dto.eduplan.YearPlanUnlockRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|unlockYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan =
                eduYearPlanService.unlockYearPlan(currentUser, planId, request.note(), request.deadline());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Unlock year plan success", plan);
    }

    @PostMapping("/{planId}/versions")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<YearPlanDetailResponse>> createVersion(
            @PathVariable Long planId,
            @RequestBody(required = false) com.carenest.dto.eduplan.YearPlanVersionRequest request,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|createVersion|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanDetailResponse plan = eduYearPlanService.createVersion(
                currentUser,
                planId,
                new YearPlanEditRequest(sessionId, revision, requestId),
                request == null ? null : request.curriculumSourcePlanId());
        log.info("{}|newPlanId={}|END", logPrefix, plan.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create year plan version success", plan);
    }

    @GetMapping("/{planId}/changes")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<YearPlanChangesResponse>> getChanges(
            @PathVariable Long planId, @RequestParam(required = false) Long fromPlanId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getChanges|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        YearPlanChangesResponse changes = eduYearPlanService.getChanges(currentUser, planId, fromPlanId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get year plan changes success", changes);
    }

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<Void>> deleteYearPlan(
            @PathVariable Long planId,
            CurrentUser currentUser,
            @RequestHeader(value = "X-Edit-Session", required = false) UUID sessionId,
            @RequestHeader("X-Plan-Revision") Long revision,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        String logPrefix = LOG_PREFIX + "|deleteYearPlan|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        eduYearPlanService.deleteYearPlan(currentUser, planId, new YearPlanEditRequest(sessionId, revision, requestId));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete year plan success");
    }
}
