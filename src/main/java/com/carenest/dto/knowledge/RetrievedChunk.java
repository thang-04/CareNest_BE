package com.carenest.dto.knowledge;

public record RetrievedChunk(Long chunkId, Long documentId, String title, String content, double score) {}
