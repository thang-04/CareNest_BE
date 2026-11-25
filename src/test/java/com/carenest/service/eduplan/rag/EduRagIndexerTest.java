package com.carenest.service.eduplan.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.entity.eduplan.EduRagChunk;
import com.carenest.integration.ai.EmbeddingClient;
import com.carenest.repository.eduplan.EduRagChunkRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EduRagIndexerTest {

    private final EmbeddingClient client = mock(EmbeddingClient.class);
    private final EduRagChunkRepository repository = mock(EduRagChunkRepository.class);
    private final EduRagIndexer indexer =
            new EduRagIndexer(client, repository, new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @Test
    void staleWhenNoModelOtherModelOrChangedText() {
        EduRagChunk fresh = chunk("A", "nội dung");
        ReflectionTestUtils.setField(fresh, "model", "m1");
        assertThat(EduRagIndexer.isStale(fresh, "m1")).isFalse();
        assertThat(EduRagIndexer.isStale(fresh, "m2")).isTrue();
        assertThat(EduRagIndexer.isStale(chunk("B", "x"), "m1")).isTrue();
        ReflectionTestUtils.setField(fresh, "content", "nội dung mới");
        assertThat(EduRagIndexer.isStale(fresh, "m1")).isTrue();
    }

    @Test
    void literalIsPgvectorText() {
        assertThat(EduRagIndexer.literal(new float[] {0.5f, -1f})).isEqualTo("[0.5,-1.0]");
    }

    @Test
    void doesNothingWhenAiIsOff() {
        when(client.isAvailable()).thenReturn(false);
        assertThat(indexer.indexStale(Duration.ofSeconds(1))).isZero();
        verify(client, never()).embed(anyList(), any());
    }

    @Test
    void embedsStaleChunksAndStoresVectors() {
        when(client.isAvailable()).thenReturn(true);
        when(client.modelName()).thenReturn("m1");
        when(repository.findByStatus("ACTIVE")).thenReturn(List.of(chunk("A", "a"), chunk("B", "b")));
        when(client.embed(anyList(), any())).thenReturn(List.of(new float[] {1f}, new float[] {2f}));

        assertThat(indexer.indexStale(Duration.ofSeconds(5))).isEqualTo(2);
        verify(repository).updateEmbedding(eq("A"), eq("m1"), eq(EduRagIndexer.sha256("a")), eq("[1.0]"));
        verify(repository).updateEmbedding(eq("B"), eq("m1"), anyString(), eq("[2.0]"));
    }

    @Test
    void embeddingErrorIsSwallowedAndNextRunCanStart() {
        when(client.isAvailable()).thenReturn(true);
        when(client.modelName()).thenReturn("m1");
        when(repository.findByStatus("ACTIVE")).thenReturn(List.of(chunk("A", "a")));
        when(client.embed(anyList(), any())).thenThrow(new IllegalStateException("down"));

        assertThat(indexer.indexStale(Duration.ofSeconds(1))).isZero();
        assertThat(indexer.indexStale(Duration.ofSeconds(1))).isZero();
        verify(client, times(2)).embed(anyList(), any());
    }

    @Test
    void startupRunWaitsAndRetriesBatchAfterRateLimit() {
        when(client.isAvailable()).thenReturn(true);
        when(client.modelName()).thenReturn("m1");
        when(repository.findByStatus("ACTIVE")).thenReturn(List.of(chunk("A", "a")));
        when(client.embed(anyList(), any()))
                .thenThrow(new IllegalStateException("429"))
                .thenReturn(List.of(new float[] {1f}));

        assertThat(indexer.indexStale(Duration.ofSeconds(5), Duration.ofMillis(10)))
                .isEqualTo(1);
        verify(client, times(2)).embed(anyList(), any());
    }

    private static EduRagChunk chunk(String id, String text) {
        return EduRagChunk.seed(id, "mt", "SEED", text, "{}", EduRagIndexer.sha256(text));
    }
}
