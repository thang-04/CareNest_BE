package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.BranchFilePreviewResponse;
import com.carenest.dto.eduplan.ClassCalendarPreviewRequest;
import com.carenest.dto.eduplan.ClassCalendarPreviewResponse;
import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocAdjustmentsRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
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
            @RequestParam(required = false) Long schoolYearId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getClassDocs|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        ClassDocsResponse docs = classDocService.getClassDocs(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class documents success", docs);
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

    // EDU-27: ô "Kế hoạch điều chỉnh và bổ sung" của kế hoạch ngày, ghi riêng để sửa được cả sau khi duyệt
    @PutMapping("/week/{docKey}/adjustments")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ObjectNode>> saveAdjustments(
            @PathVariable String docKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Class-Doc-Version") Long expectedVersion,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Dossier-Version", required = false)
                    Long expectedRootVersion,
            @Valid @RequestBody ClassDocAdjustmentsRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveAdjustments|userId=" + currentUser.userId() + "|docKey=" + docKey;
        log.info("{}|START", logPrefix);
        ObjectNode saved = classDocService.saveAdjustments(
                currentUser, docKey, request.adjustments(), expectedVersion, expectedRootVersion);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save lesson adjustments success", saved);
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
