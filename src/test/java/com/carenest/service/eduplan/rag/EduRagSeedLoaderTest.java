package com.carenest.service.eduplan.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.entity.eduplan.EduRagChunk;
import com.carenest.repository.eduplan.EduRagChunkRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

class EduRagSeedLoaderTest {

    private final EduRagChunkRepository repository = mock(EduRagChunkRepository.class);
    private final EduRagSeedLoader loader = new EduRagSeedLoader(
            repository, mock(EduRagIndexer.class), JsonMapper.builder().build());

    @Test
    void parsesValidLinesWithHashAndSkipsBlankLines() {
        List<EduRagChunk> chunks = loader.parse(
                "SEED",
                List.of(
                        "{\"id\":\"MT-1\",\"type\":\"mt\",\"text\":\"Trẻ bật xa\",\"meta\":{\"age\":\"4-5 tuổi\"}}",
                        "  ",
                        "{\"id\":\"A-1\",\"type\":\"activity\",\"text\":\"Ném bóng\"}"));

        assertThat(chunks).hasSize(2);
        EduRagChunk first = chunks.getFirst();
        assertThat(first.getId()).isEqualTo("MT-1");
        assertThat(first.getSource()).isEqualTo("SEED");
        assertThat(first.getStatus()).isEqualTo("ACTIVE");
        assertThat(first.getModel()).isNull();
        assertThat(first.getContentHash()).isEqualTo(EduRagIndexer.sha256("Trẻ bật xa"));
        assertThat(first.getMeta()).contains("4-5 tuổi");
        assertThat(chunks.get(1).getMeta()).isEqualTo("{}");
    }

    @Test
    void rejectsUnknownTypeOrMissingIdSoBrokenFilesFailEarly() {
        assertThatThrownBy(() -> loader.parse("SEED", List.of("{\"id\":\"X\",\"type\":\"video\",\"text\":\"a\"}")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> loader.parse("SEED", List.of("{\"type\":\"mt\",\"text\":\"a\"}")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void skipsSourceThatIsAlreadyLoaded() {
        when(repository.existsBySource("SEED")).thenReturn(true);
        loader.loadIfMissing("SEED", "edu/rag/chunks.jsonl");
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void loadsSourceWhenTableHasNone() {
        when(repository.existsBySource("WEB")).thenReturn(false);
        when(repository.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        loader.loadIfMissing("WEB", "edu/rag/web_chunks.jsonl");
        verify(repository).saveAll(anyList());
    }

    @Test
    void bundledSeedFilesAreValidUniqueAndFreeOfSchoolOrPlaceNames() throws Exception {
        List<EduRagChunk> seed = loader.parse("SEED", lines("edu/rag/chunks.jsonl"));
        List<EduRagChunk> web = loader.parse("WEB", lines("edu/rag/web_chunks.jsonl"));
        assertThat(seed).hasSize(862);
        assertThat(web).hasSize(150);
        Set<String> ids = new HashSet<>();
        seed.forEach(c -> ids.add(c.getId()));
        web.forEach(c -> ids.add(c.getId()));
        assertThat(ids).hasSize(862 + 150);
        // AI-04: giáo án web đã bỏ tên người soạn, tên trường, địa danh
        Pattern leak = Pattern.compile(
                "Người soạn|Bố Hạ|Lạng Giang|Đại Lâm|Nghĩa Ho|Tân Sỏi|Hương Sơn", Pattern.CASE_INSENSITIVE);
        assertThat(web)
                .noneMatch(c -> leak.matcher(c.getContent() + c.getMeta()).find());
    }

    private static List<String> lines(String path) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }
}
