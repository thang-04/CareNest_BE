package com.carenest.service.knowledge;

import com.carenest.dto.knowledge.AiDocumentResponse;
import com.carenest.dto.knowledge.CreateAiDocumentRequest;
import com.carenest.dto.knowledge.RetrievalQuery;
import com.carenest.dto.knowledge.RetrievedChunk;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.AiDocumentStatus;
import java.util.List;

/** Kho tài liệu tham khảo dùng chung cho mọi module AI (RAG). */
public interface KnowledgeService {

    AiDocumentResponse ingest(CurrentUser currentUser, CreateAiDocumentRequest request);

    List<AiDocumentResponse> listDocuments(CurrentUser currentUser);

    /** Embed lại toàn bộ chunk bằng model hiện tại (dùng khi đổi provider/model embedding). */
    AiDocumentResponse reindex(CurrentUser currentUser, Long documentId);

    AiDocumentResponse changeStatus(CurrentUser currentUser, Long documentId, AiDocumentStatus status);

    void delete(CurrentUser currentUser, Long documentId);

    /**
     * Không ném lỗi khi embedding không dùng được: trả danh sách rỗng để module gọi tiếp tục không có tài liệu
     * (luồng AI vẫn chạy, chỉ thiếu ngữ cảnh tham khảo).
     */
    List<RetrievedChunk> retrieve(RetrievalQuery query);
}
