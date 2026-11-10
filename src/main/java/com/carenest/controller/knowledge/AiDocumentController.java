package com.carenest.controller.knowledge;

import com.carenest.dto.knowledge.AiDocumentResponse;
import com.carenest.dto.knowledge.CreateAiDocumentRequest;
import com.carenest.dto.knowledge.UpdateAiDocumentStatusRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.knowledge.KnowledgeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quản trị tài liệu tham khảo cho RAG. Frontend không bao giờ gọi trực tiếp LLM/embedding. */
@RestController
@RequestMapping("/ai-documents")
@RequiredArgsConstructor
@Slf4j
public class AiDocumentController {
    private static final String LOG_PREFIX = "[AiDocumentController]";

    private final KnowledgeService knowledgeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
    public ResponseEntity<ResponseJson<AiDocumentResponse>> ingest(
            @Valid @RequestBody CreateAiDocumentRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|ingest|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        AiDocumentResponse document = knowledgeService.ingest(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Ingest document success", document);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<AiDocumentResponse>>> listDocuments(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listDocuments|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<AiDocumentResponse> documents = knowledgeService.listDocuments(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List documents success", documents);
    }

    @PostMapping("/{documentId}/reindex")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
    public ResponseEntity<ResponseJson<AiDocumentResponse>> reindex(
            @PathVariable Long documentId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|reindex|userId=" + currentUser.userId() + "|documentId=" + documentId;
        log.info("{}|START", logPrefix);
        AiDocumentResponse document = knowledgeService.reindex(currentUser, documentId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Reindex document success", document);
    }

    @PatchMapping("/{documentId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
    public ResponseEntity<ResponseJson<AiDocumentResponse>> changeStatus(
            @PathVariable Long documentId,
            @Valid @RequestBody UpdateAiDocumentStatusRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|changeStatus|userId=" + currentUser.userId() + "|documentId=" + documentId;
        log.info("{}|START", logPrefix);
        AiDocumentResponse document = knowledgeService.changeStatus(currentUser, documentId, request.status());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Change document status success", document);
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
    public ResponseEntity<ResponseJson<Void>> delete(@PathVariable Long documentId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|delete|userId=" + currentUser.userId() + "|documentId=" + documentId;
        log.info("{}|START", logPrefix);
        knowledgeService.delete(currentUser, documentId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete document success");
    }
}
