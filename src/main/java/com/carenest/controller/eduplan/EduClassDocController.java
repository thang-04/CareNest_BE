package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ObjectNode;

/** Tài liệu kế hoạch của lớp (EDU-12..18): chủ đề nhánh, kế hoạch chủ đề, giáo án tuần + 5 kế hoạch ngày. */
@RestController
@RequestMapping("/education-plans/class-docs")
@RequiredArgsConstructor
@Slf4j
public class EduClassDocController {
    private static final String LOG_PREFIX = "[EduClassDocController]";

    private final EduClassDocService classDocService;

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
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ResponseJson<ObjectNode>> saveClassDoc(
            @PathVariable String kind,
            @PathVariable String docKey,
            @RequestBody ObjectNode doc,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|saveClassDoc|userId=" + currentUser.userId() + "|kind=" + kind + "|docKey=" + docKey;
        log.info("{}|START", logPrefix);
        ObjectNode saved = classDocService.saveClassDoc(currentUser, kind, docKey, doc);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save class document success", saved);
    }

    @PostMapping("/{kind}/{docKey}/actions")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ObjectNode>> actOnClassDoc(
            @PathVariable String kind,
            @PathVariable String docKey,
            @Valid @RequestBody ClassDocActionRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|actOnClassDoc|userId=" + currentUser.userId() + "|kind=" + kind + "|docKey="
                + docKey + "|action=" + request.action();
        log.info("{}|START", logPrefix);
        ObjectNode doc = classDocService.actOnClassDoc(currentUser, kind, docKey, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update class document status success", doc);
    }
}
