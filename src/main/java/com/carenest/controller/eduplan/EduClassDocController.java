package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.BranchFilePreviewResponse;
import com.carenest.dto.eduplan.ClassCalendarPreviewRequest;
import com.carenest.dto.eduplan.ClassCalendarPreviewResponse;
import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.node.ObjectNode;

/** Tài liệu kế hoạch của lớp (EDU-12..18): chủ đề nhánh, kế hoạch chủ đề, giáo án tuần + 5 kế hoạch ngày. */
@RestController
@RequestMapping("/education-plans/class-docs")
@RequiredArgsConstructor
@Slf4j
public class EduClassDocController {
    private static final String LOG_PREFIX = "[EduClassDocController]";

    private final EduClassDocService classDocService;
    private final com.carenest.service.eduplan.EduOverdueBoardService overdueBoardService;

    @PostMapping(value = "/branch/file-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<BranchFilePreviewResponse>> previewBranchFile(
            @RequestParam Long classId,
            @RequestParam Long yearPlanId,
            @RequestPart MultipartFile file,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|previewBranchFile|userId=" + currentUser.userId() + "|classId=" + classId
                + "|yearPlanId=" + yearPlanId;
        log.info("{}|START", logPrefix);
        BranchFilePreviewResponse preview = classDocService.previewBranchFile(currentUser, classId, yearPlanId, file);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Preview branch file success", preview);
    }

    @PostMapping("/branch/calendar-preview")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ClassCalendarPreviewResponse>> previewClassCalendar(
            @RequestBody ClassCalendarPreviewRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|previewClassCalendar|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        ClassCalendarPreviewResponse response = classDocService.previewClassCalendar(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Preview class calendar success", response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ClassDocsResponse>> getClassDocs(
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(defaultValue = "MAIN") ClassDocView view,
            // Trang của một lớp: chỉ tài liệu lớp đó (Tiền 25/11/2026); bỏ trống = cả phạm vi như cũ
            @RequestParam(required = false) Long classId,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getClassDocs|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        if (view == ClassDocView.OVERDUE && classId == null)
            throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "classId");
        ClassDocsResponse docs = view == ClassDocView.OVERDUE
                ? classDocService.getClassDocsOverdueView(currentUser, schoolYearId, classId)
                : classId != null
                        ? classDocService.getClassDocsOfClass(
                                currentUser, schoolYearId, view == ClassDocView.REVIEW, classId)
                        : view == ClassDocView.REVIEW
                                ? classDocService.getClassDocsForReview(currentUser, schoolYearId)
                                : classDocService.getClassDocs(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class documents success", docs);
    }

    public enum ClassDocView {
        MAIN,
        REVIEW,
        OVERDUE
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<ClassDocsResponse>> getOverdueClassDocs(
            @RequestParam Long schoolYearId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getOverdueClassDocs|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        var docs = classDocService.getOverdueClassDocs(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get overdue class documents success", docs);
    }

    /** Danh sách gọn có phân trang và lọc (edu-list-paging, Tiền 25/11/2026); không có nội dung soạn. */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<com.carenest.dto.common.PageResponse<ObjectNode>>> getClassDocSummaries(
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(defaultValue = "MAIN") ClassDocView view,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) java.util.Set<String> classIds,
            @RequestParam(required = false) java.util.Set<String> kind,
            @RequestParam(required = false) Integer semesterNo,
            @RequestParam(required = false) String themeId,
            @RequestParam(required = false) java.util.Set<String> status,
            @RequestParam(defaultValue = "false") boolean myTurn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "false") boolean currentOnly,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getClassDocSummaries|userId=" + currentUser.userId() + "|schoolYearId="
                + schoolYearId + "|page=" + page;
        log.info("{}|START", logPrefix);
        var filter = new com.carenest.dto.eduplan.ClassDocSummaryFilter(
                classId, classIds, kind, semesterNo, themeId, status, myTurn, page, size, currentOnly);
        var result =
                classDocService.getClassDocSummaries(currentUser, schoolYearId, view == ClassDocView.REVIEW, filter);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class document summaries success", result);
    }

    /** Kế hoạch quá hạn (HT): kế hoạch năm/tổ + hồ sơ lớp ghép, lọc, phân trang ở BE (edu-list-paging, 25/11/2026). */
    @GetMapping("/overdue-board")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<com.carenest.service.eduplan.EduOverdueBoardService.Board>> getOverdueBoard(
            @RequestParam Long schoolYearId,
            @RequestParam(defaultValue = "overdue") String tab,
            @RequestParam(required = false) String deadlineKind,
            @RequestParam(required = false) String ageGroupId,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer semesterNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getOverdueBoard|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        var board = overdueBoardService.getOverdueBoard(
                currentUser,
                schoolYearId,
                new com.carenest.service.eduplan.EduOverdueBoardService.Filter(
                        tab, deadlineKind, ageGroupId, classId, kind, semesterNo, page, size));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get overdue board success", board);
    }

    @GetMapping("/context")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<com.carenest.dto.eduplan.ClassDocContextResponse>> getClassDocContext(
            @RequestParam(required = false) Long schoolYearId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getClassDocContext|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        var result = classDocService.getClassDocContext(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class document context success", result);
    }

    @GetMapping("/{kind}/{docKey}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ObjectNode>> getClassDoc(
            @PathVariable String kind, @PathVariable String docKey, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getClassDoc|userId=" + currentUser.userId() + "|kind=" + kind;
        log.info("{}|START", logPrefix);
        var doc = classDocService.getClassDoc(currentUser, kind, docKey);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class document success", doc);
    }

    @GetMapping("/overdue/{kind}/{docKey}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<ObjectNode>> getOverdueClassDoc(
            @PathVariable String kind,
            @PathVariable String docKey,
            @RequestParam Long schoolYearId,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getOverdueClassDoc|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        var doc = classDocService.getOverdueClassDoc(currentUser, schoolYearId, kind, docKey);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get overdue class document success", doc);
    }

    @PutMapping("/{kind}/{docKey}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ObjectNode>> saveClassDoc(
            @PathVariable String kind,
            @PathVariable String docKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Class-Doc-Version") Long expectedVersion,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Dossier-Version", required = false)
                    Long expectedRootVersion,
            @RequestBody ObjectNode doc,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|saveClassDoc|userId=" + currentUser.userId() + "|kind=" + kind + "|docKey=" + docKey;
        log.info("{}|START", logPrefix);
        ObjectNode saved =
                classDocService.saveClassDoc(currentUser, kind, docKey, doc, expectedVersion, expectedRootVersion);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save class document success", saved);
    }

    /** Spec 002 FR-018: xem lại một bản kế hoạch giáo dục ngày đã ban hành trước đây. */
    @GetMapping("/releases/{releaseId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ObjectNode>> getClassDocRelease(
            @PathVariable Long releaseId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getClassDocRelease|userId=" + currentUser.userId() + "|releaseId=" + releaseId;
        log.info("{}|START", logPrefix);
        ObjectNode release = classDocService.getClassDocRelease(currentUser, releaseId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class document release success", release);
    }

    /** EDU-96: tiêu chí gửi duyệt đạt / chưa đạt của kế hoạch giáo dục chủ đề / kế hoạch giáo dục ngày (khung Kiểm tra). */
    @GetMapping("/{kind}/{docKey}/checks")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ObjectNode>> getClassDocChecks(
            @PathVariable String kind, @PathVariable String docKey, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getClassDocChecks|userId=" + currentUser.userId() + "|kind=" + kind
                + "|docKey=" + docKey;
        log.info("{}|START", logPrefix);
        ObjectNode checks = classDocService.getClassDocChecks(currentUser, kind, docKey);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class document checks success", checks);
    }

    @PostMapping("/{kind}/{docKey}/actions")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ObjectNode>> actOnClassDoc(
            @PathVariable String kind,
            @PathVariable String docKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Class-Doc-Version") Long expectedVersion,
            @Valid @RequestBody ClassDocActionRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|actOnClassDoc|userId=" + currentUser.userId() + "|kind=" + kind + "|docKey="
                + docKey + "|action=" + request.action();
        log.info("{}|START", logPrefix);
        ObjectNode doc = classDocService.actOnClassDoc(currentUser, kind, docKey, request, expectedVersion);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update class document status success", doc);
    }
}
