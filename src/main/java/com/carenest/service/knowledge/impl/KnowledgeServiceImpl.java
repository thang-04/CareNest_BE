package com.carenest.service.knowledge.impl;

import com.carenest.dto.knowledge.AiDocumentResponse;
import com.carenest.dto.knowledge.CreateAiDocumentRequest;
import com.carenest.dto.knowledge.RetrievalQuery;
import com.carenest.dto.knowledge.RetrievedChunk;
import com.carenest.entity.knowledge.AiDocument;
import com.carenest.entity.knowledge.AiDocumentChunk;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.EmbeddingClient;
import com.carenest.mapper.knowledge.AiDocumentMapper;
import com.carenest.repository.knowledge.AiDocumentChunkRepository;
import com.carenest.repository.knowledge.AiDocumentChunkRepository.ChunkSearchRow;
import com.carenest.repository.knowledge.AiDocumentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.knowledge.KnowledgeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AiDocumentStatus;
import com.carenest.utils.enums.AiDocumentType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {
    private static final String LOG_PREFIX = "[KnowledgeServiceImpl]";

    // Câu mang tính ra lệnh cho model: tài liệu chứa các mẫu này bị từ chối khi nạp (chống prompt injection qua RAG)
    private static final List<String> INSTRUCTION_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore all previous",
            "ignore the above",
            "disregard the above",
            "disregard previous",
            "system prompt",
            "you are now",
            "bỏ qua hướng dẫn",
            "bỏ qua các hướng dẫn",
            "bỏ qua mọi hướng dẫn",
            "bỏ qua mọi luật",
            "bỏ qua các quy tắc",
            "</reference",
            "<|im_start|>");

    private final AiDocumentRepository documentRepository;
    private final AiDocumentChunkRepository chunkRepository;
    private final EmbeddingClient embeddingClient;
    private final AiDocumentMapper documentMapper;
    private final AiProperties aiProperties;

    @Override
    @Transactional
    public AiDocumentResponse ingest(CurrentUser currentUser, CreateAiDocumentRequest request) {
        String logPrefix = LOG_PREFIX + "|ingest|userId=" + currentUser.userId() + "|type=" + request.documentType();
        log.info("{}|START", logPrefix);
        try {
            validateDates(request);
            String content = TextChunker.clean(request.content());
            rejectInstructionLikeContent(content);
            List<String> parts = TextChunker.chunk(content);
            if (parts.isEmpty()) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Document content is empty");
            }

            AiDocument document = documentRepository.save(AiDocument.create(
                    request.title().strip(),
                    request.documentType(),
                    request.sourceName().strip(),
                    request.sourceUrl(),
                    normalizeRegion(request.region()),
                    request.effectiveDate(),
                    request.expiryDate(),
                    request.version(),
                    currentUser.userId()));
            List<AiDocumentChunk> chunks = new ArrayList<>(parts.size());
            for (int index = 0; index < parts.size(); index++) {
                String part = parts.get(index);
                chunks.add(AiDocumentChunk.create(document.getId(), index, part, sha256(part)));
            }
            chunks = chunkRepository.saveAll(chunks);
            embedChunks(chunks);

            AiDocumentResponse response = documentMapper.toResponse(document, chunks.size());
            log.info("{}|END|documentId={}|chunks={}", logPrefix, document.getId(), chunks.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiDocumentResponse> listDocuments(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listDocuments|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<AiDocumentResponse> documents = documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(document ->
                        documentMapper.toResponse(document, chunkRepository.countByDocumentId(document.getId())))
                .toList();
        log.info("{}|END|count={}", logPrefix, documents.size());
        return documents;
    }

    @Override
    @Transactional
    public AiDocumentResponse reindex(CurrentUser currentUser, Long documentId) {
        String logPrefix = LOG_PREFIX + "|reindex|userId=" + currentUser.userId() + "|documentId=" + documentId;
        log.info("{}|START", logPrefix);
        try {
            AiDocument document = findDocument(documentId);
            chunkRepository.clearEmbeddings(documentId);
            List<AiDocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
            embedChunks(chunks);
            log.info("{}|END|chunks={}", logPrefix, chunks.size());
            return documentMapper.toResponse(document, chunks.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public AiDocumentResponse changeStatus(CurrentUser currentUser, Long documentId, AiDocumentStatus status) {
        String logPrefix = LOG_PREFIX + "|changeStatus|userId=" + currentUser.userId() + "|documentId=" + documentId
                + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            AiDocument document = findDocument(documentId);
            document.changeStatus(status);
            log.info("{}|END", logPrefix);
            return documentMapper.toResponse(document, chunkRepository.countByDocumentId(documentId));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void delete(CurrentUser currentUser, Long documentId) {
        String logPrefix = LOG_PREFIX + "|delete|userId=" + currentUser.userId() + "|documentId=" + documentId;
        log.info("{}|START", logPrefix);
        try {
            AiDocument document = findDocument(documentId);
            chunkRepository.deleteByDocumentId(documentId);
            documentRepository.delete(document);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieve(RetrievalQuery query) {
        String logPrefix = LOG_PREFIX + "|retrieve|types=" + query.types();
        if (!StringUtils.hasText(query.text())
                || query.types() == null
                || query.types().isEmpty()) {
            return List.of();
        }
        float[] vector;
        try {
            vector = embeddingClient.embed(query.text());
        } catch (AiClientException ex) {
            // RAG là phần bổ trợ: thiếu embedding thì module gọi tiếp tục không có tài liệu tham khảo
            log.warn("{}|SKIPPED|reason={}", logPrefix, ex.getReason());
            return List.of();
        }
        AiProperties.Rag rag = aiProperties.rag();
        List<ChunkSearchRow> rows = chunkRepository.searchSimilar(
                toVectorLiteral(vector),
                query.types().stream().map(AiDocumentType::name).toList(),
                normalizeRegion(query.region()),
                query.asOf(),
                embeddingClient.modelName(),
                Math.max(1, rag.topK()));
        List<RetrievedChunk> chunks = rows.stream()
                .filter(row -> row.getScore() != null && row.getScore() >= rag.minScore())
                .map(row -> new RetrievedChunk(
                        row.getChunkId(), row.getDocumentId(), row.getTitle(), row.getContent(), row.getScore()))
                .toList();
        // RAG_NO_RELEVANT_CONTEXT: không tài liệu nào đủ liên quan, module gọi vẫn chạy với references rỗng
        log.info(
                "{}|candidates={}|kept={}{}",
                logPrefix,
                rows.size(),
                chunks.size(),
                chunks.isEmpty() ? "|RAG_NO_RELEVANT_CONTEXT" : "");
        return chunks;
    }

    // Chunk trùng nội dung dùng lại embedding cũ; phần còn lại gọi API theo lô. Lỗi API ⇒ rollback cả lần nạp
    private void embedChunks(List<AiDocumentChunk> chunks) {
        String model = embeddingClient.modelName();
        List<AiDocumentChunk> pending = new ArrayList<>();
        for (AiDocumentChunk chunk : chunks) {
            if (chunkRepository.copyEmbeddingFromSameContent(chunk.getId(), chunk.getContentHash(), model) == 0) {
                pending.add(chunk);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        List<float[]> vectors;
        try {
            vectors = embeddingClient.embedBatch(
                    pending.stream().map(AiDocumentChunk::getContent).toList());
        } catch (AiClientException ex) {
            throw new GlobalException(
                    ApiCode.SERVICE_UNAVAILABLE, "Embedding service unavailable: " + ex.getReason(), ex);
        }
        for (int index = 0; index < pending.size(); index++) {
            chunkRepository.updateEmbedding(pending.get(index).getId(), toVectorLiteral(vectors.get(index)), model);
        }
        log.info("{}|embedChunks|embedded={}|reused={}", LOG_PREFIX, pending.size(), chunks.size() - pending.size());
    }

    private AiDocument findDocument(Long documentId) {
        return documentRepository
                .findById(documentId)
                .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Document not found"));
    }

    private static void validateDates(CreateAiDocumentRequest request) {
        if (request.expiryDate() != null && request.expiryDate().isBefore(request.effectiveDate())) {
            throw new GlobalException(ApiCode.BAD_REQUEST, "expiryDate must not be before effectiveDate");
        }
    }

    static void rejectInstructionLikeContent(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        for (String pattern : INSTRUCTION_PATTERNS) {
            if (lower.contains(pattern)) {
                throw new GlobalException(
                        ApiCode.BAD_REQUEST, "Document contains instruction-like text and was rejected: " + pattern);
            }
        }
    }

    private static String normalizeRegion(String region) {
        return StringUtils.hasText(region) ? region.strip().toUpperCase(Locale.ROOT) : AiDocument.REGION_ALL;
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 10).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(vector[index]);
        }
        return literal.append(']').toString();
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
