package com.carenest.service.knowledge.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void mergesShortParagraphsIntoOneChunk() {
        List<String> chunks = TextChunker.chunk("Đoạn một.\n\nĐoạn   hai.\r\n\r\nĐoạn ba.");

        assertThat(chunks).containsExactly("Đoạn một.\n\nĐoạn hai.\n\nĐoạn ba.");
    }

    @Test
    void startsNewChunkWhenLimitExceeded() {
        String paragraph = "a".repeat(500);

        List<String> chunks = TextChunker.chunk(paragraph + "\n\n" + paragraph);

        assertThat(chunks).hasSize(2).allSatisfy(chunk -> assertThat(chunk).hasSize(500));
    }

    @Test
    void splitsLongParagraphWithOverlapAndLimit() {
        String paragraph = ("từ ".repeat(700)).strip();

        List<String> chunks = TextChunker.chunk(paragraph);

        assertThat(chunks)
                .hasSizeGreaterThan(2)
                .allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(TextChunker.MAX_CHARS));
        String tailOfFirst = chunks.get(0).substring(chunks.get(0).length() - 20);
        assertThat(chunks.get(1)).contains(tailOfFirst.strip());
    }

    @Test
    void returnsNoChunkForBlankText() {
        assertThat(TextChunker.chunk(" \n\n \n")).isEmpty();
    }
}
