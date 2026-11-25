package com.carenest.service.eduplan.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.EduAiResponse;
import com.carenest.entity.eduplan.EduRagChunk;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.EmbeddingClient;
import com.carenest.repository.eduplan.EduRagChunkRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EduRagRetrieverTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final EduRagChunkRepository repository = mock(EduRagChunkRepository.class);
    private final EmbeddingClient client = mock(EmbeddingClient.class);
    private final EduRagRetriever retriever =
            new EduRagRetriever(repository, client, mock(EduRagIndexer.class), MAPPER);

    @Test
    void webSourceIsDiscountedAndAgeOrCodeMatchGetsBonus() {
        var seed = hit("S", "SEED", 0.8, "{\"age\":\"4-5 tuổi\",\"code\":\"MT12\"}");
        var web = hit("W", "WEB", 0.8, "{}");
        assertThat(EduRagRetriever.score(web, null, Set.of())).isCloseTo(0.64, within());
        assertThat(EduRagRetriever.score(seed, null, Set.of())).isCloseTo(0.8, within());
        assertThat(EduRagRetriever.score(seed, "45", Set.of("mt12"))).isCloseTo(0.9, within());
    }

    @Test
    void rankDropsBelowMinSimAndKeepsTopK() {
        List<EduRagRetriever.Hit> ranked = EduRagRetriever.rank(
                "bật xa",
                List.of(hit("A", "SEED", 0.9, "{}"), hit("B", "WEB", 0.7, "{}"), hit("C", "SEED", 0.6, "{}")),
                2,
                0.5);
        assertThat(ranked).extracting(EduRagRetriever.Hit::id).containsExactly("A", "C");
    }

    @Test
    void ageHintAndCodesFromQuestion() {
        assertThat(EduRagRetriever.ageHint("bài cho trẻ 4-5 tuổi")).isEqualTo("45");
        assertThat(EduRagRetriever.ageHint("lớp lá")).isEqualTo("56");
        assertThat(EduRagRetriever.ageHint("ném xa")).isNull();
        assertThat(EduRagRetriever.codes("MT12 và 3.2")).containsExactly("mt12", "3.2");
        // Số thường trong câu không bị coi là mã
        assertThat(EduRagRetriever.codes("ném bằng 1 tay")).isEmpty();
    }

    @Test
    void fallsBackToKeywordSearchWhenAiIsOff() {
        when(client.isAvailable()).thenReturn(false);
        when(repository.findByStatusAndChunkTypeIn("ACTIVE", Set.of("lesson")))
                .thenReturn(List.of(
                        EduRagChunk.seed("L1", "lesson", "SEED", "Thơ Giúp mẹ cho trẻ", "{}", "h"),
                        EduRagChunk.seed("L2", "lesson", "SEED", "Ném xa bằng một tay", "{}", "h")));

        List<EduAiResponse.RagHit> hits = retriever.search("Thơ Giúp mẹ", "lesson", 5);

        assertThat(hits).extracting(EduAiResponse.RagHit::id).containsExactly("L1");
        verify(repository, never()).similarTo(any(), any(), anyCollection(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void unknownTypeIsBadRequestAndBlankQueryReturnsNothing() {
        assertThatThrownBy(() -> retriever.search("x", "video", 5)).isInstanceOf(GlobalException.class);
        assertThat(retriever.search("   ", null, 5)).isEmpty();
    }

    private static EduRagRetriever.Hit hit(String id, String source, double sim, String meta) {
        JsonNode node = MAPPER.readTree(meta);
        return new EduRagRetriever.Hit(id, "lesson", source, "nội dung", sim, node);
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(1e-9);
    }
}
