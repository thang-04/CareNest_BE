package com.carenest.service.eduplan.impl.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.EmbeddingClient;
import com.carenest.utils.enums.AiErrorReason;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class EduCandidateRankerTest {

    private final EmbeddingClient client = mock(EmbeddingClient.class);
    private final EduCandidateRanker ranker = new EduCandidateRanker(client);
    private final List<String> docs = List.of("Tung bóng lên cao và bắt.", "Nhún chân.", "Tập đánh răng, lau mặt.");

    @Test
    void containsPhraseIgnoresDashSpacingAndCase() {
        assertThat(EduTextMatch.containsPhrase("Phối hợp tay- mắt trong vận động", "phối hợp tay - mắt"))
                .isTrue();
        assertThat(EduTextMatch.containsPhrase("Phối hợp tay- mắt", "tay–mắt")).isTrue();
        assertThat(EduTextMatch.containsPhrase("Phối hợp tay- mắt", "phối hợp chân"))
                .isFalse();
        assertThat(EduTextMatch.containsPhrase("Phối hợp", "  ")).isFalse();
        // Không khớp nửa từ: "tay" không nằm trong "tayy"
        assertThat(EduTextMatch.containsPhrase("vẫy tayy", "tay")).isFalse();
    }

    @Test
    void aiOffRanksByKeywordOnly() {
        when(client.isAvailable()).thenReturn(false);
        List<List<Integer>> top = ranker.topEach(List.of("bắt bóng"), docs, 5);
        // Khớp chữ lên đầu, phần còn lại điền theo thứ tự gốc
        assertThat(top.getFirst()).containsExactly(0, 1, 2);
        verify(client, never()).embed(anyList(), any());
    }

    @Test
    void vectorFindsMeaningWithoutSharedWordsAndCachesDocs() {
        when(client.isAvailable()).thenReturn(true);
        when(client.modelName()).thenReturn("m");
        when(client.budget()).thenReturn(Duration.ofSeconds(5));
        // Câu hỏi "vệ sinh cá nhân" không chung chữ với "đánh răng" nhưng gần về nghĩa
        when(client.embed(anyList(), any())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream()
                    .map(t -> t.contains("răng") || t.contains("vệ sinh") ? new float[] {1, 0} : new float[] {0, 1})
                    .toList();
        });
        assertThat(ranker.topEach(List.of("vệ sinh cá nhân"), docs, 1).getFirst())
                .containsExactly(2);
        ranker.topEach(List.of("vệ sinh cá nhân"), docs, 1);
        // Lần hai chỉ embed câu hỏi, tài liệu lấy từ cache
        verify(client, times(3)).embed(anyList(), any());
    }

    @Test
    void embeddingErrorFallsBackToKeyword() {
        when(client.isAvailable()).thenReturn(true);
        when(client.modelName()).thenReturn("m");
        when(client.budget()).thenReturn(Duration.ofSeconds(5));
        when(client.embed(anyList(), any())).thenThrow(new AiClientException(AiErrorReason.TIMEOUT, "slow", null));
        assertThat(ranker.topEach(List.of("nhún chân"), docs, 5).getFirst()).containsExactly(1, 0, 2);
    }
}
