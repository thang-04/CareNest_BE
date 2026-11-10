package com.carenest.repository.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.carenest.dto.knowledge.CreateAiDocumentRequest;
import com.carenest.dto.knowledge.RetrievalQuery;
import com.carenest.dto.knowledge.RetrievedChunk;
import com.carenest.integration.ai.EmbeddingClient;
import com.carenest.security.CurrentUser;
import com.carenest.service.knowledge.KnowledgeService;
import com.carenest.support.TestDatabaseImage;
import com.carenest.utils.enums.AiDocumentStatus;
import com.carenest.utils.enums.AiDocumentType;
import com.carenest.utils.enums.CareNestRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Migration V7 + nạp tài liệu + tìm vector trên PostgreSQL/pgvector thật. Embedding giả lập bằng vector cố định
 * (không gọi API). Kiểm metadata filter chạy trước similarity. Mỗi test rollback. Cần Docker.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class KnowledgeRetrievalIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final float[] NEAR = {1f, 0f, 0f};
    private static final float[] FAR = {0f, 1f, 0f};

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(TestDatabaseImage.POSTGRES);

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;

    @BeforeEach
    void setUp() {
        Long userId = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "kc-principal-rag",
                "principal.rag",
                "Demo principal");
        principal = new CurrentUser(
                userId, "kc-principal-rag", "principal.rag", "Demo principal", null, Set.of(CareNestRole.PRINCIPAL));
        given(embeddingClient.modelName()).willReturn("test-embedding");
        // Nội dung có chữ "phù hợp" ⇒ vector gần query; còn lại ⇒ vector xa
        given(embeddingClient.embedBatch(anyList())).willAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream()
                    .map(text -> text.contains("phù hợp") ? NEAR : FAR)
                    .toList();
        });
        given(embeddingClient.embed(anyString())).willReturn(NEAR);
    }

    @Test
    void returnsOnlyActiveEffectiveDocumentsOfRequestedTypeAndRegion() {
        Long valid = ingest(
                "Chuẩn hiện hành",
                AiDocumentType.CHILD_DEVELOPMENT_STANDARD,
                "HAI_PHONG",
                TODAY.minusYears(1),
                null,
                "Nội dung phù hợp độ tuổi");
        ingest(
                "Đã hết hạn",
                AiDocumentType.CHILD_DEVELOPMENT_STANDARD,
                "ALL",
                TODAY.minusYears(2),
                TODAY.minusDays(1),
                "Nội dung phù hợp cũ");
        ingest(
                "Chưa hiệu lực",
                AiDocumentType.CHILD_DEVELOPMENT_STANDARD,
                "ALL",
                TODAY.plusDays(1),
                null,
                "Nội dung phù hợp tương lai");
        ingest(
                "Vùng khác",
                AiDocumentType.CHILD_DEVELOPMENT_STANDARD,
                "DA_NANG",
                TODAY.minusYears(1),
                null,
                "Nội dung phù hợp vùng khác");
        ingest(
                "Sai loại",
                AiDocumentType.NUTRITION_STANDARD,
                "ALL",
                TODAY.minusYears(1),
                null,
                "Nội dung phù hợp dinh dưỡng");
        Long inactive = ingest(
                "Ngừng dùng",
                AiDocumentType.CHILD_DEVELOPMENT_STANDARD,
                "ALL",
                TODAY.minusYears(1),
                null,
                "Nội dung phù hợp đã tắt");
        knowledgeService.changeStatus(principal, inactive, AiDocumentStatus.INACTIVE);

        List<RetrievedChunk> chunks = knowledgeService.retrieve(new RetrievalQuery(
                "trẻ 48-60 tháng", Set.of(AiDocumentType.CHILD_DEVELOPMENT_STANDARD), "HAI_PHONG", TODAY));

        assertThat(chunks).extracting(RetrievedChunk::documentId).containsExactly(valid);
        assertThat(chunks.getFirst().score()).isGreaterThan(0.99);
    }

    @Test
    void dropsChunksBelowMinScore() {
        ingest("Không liên quan", AiDocumentType.SCHOOL_SOP, "ALL", TODAY.minusYears(1), null, "Quy trình đón trả");

        List<RetrievedChunk> chunks = knowledgeService.retrieve(
                new RetrievalQuery("trẻ 48-60 tháng", Set.of(AiDocumentType.SCHOOL_SOP), "HAI_PHONG", TODAY));

        assertThat(chunks).isEmpty();
    }

    @Test
    void ignoresChunksEmbeddedWithAnotherModel() {
        ingest("Model cũ", AiDocumentType.SCHOOL_SOP, "ALL", TODAY.minusYears(1), null, "Nội dung phù hợp");
        given(embeddingClient.modelName()).willReturn("new-embedding");

        List<RetrievedChunk> chunks = knowledgeService.retrieve(
                new RetrievalQuery("trẻ 48-60 tháng", Set.of(AiDocumentType.SCHOOL_SOP), "HAI_PHONG", TODAY));

        assertThat(chunks).isEmpty();
    }

    private Long ingest(
            String title, AiDocumentType type, String region, LocalDate effective, LocalDate expiry, String content) {
        return knowledgeService
                .ingest(
                        principal,
                        new CreateAiDocumentRequest(
                                title, type, "Nguồn thử nghiệm", null, region, effective, expiry, "v1", content))
                .id();
    }
}
