package com.carenest.service.eduplan.impl.rag;

import com.carenest.entity.eduplan.EduRagChunk;
import com.carenest.integration.ai.EmbeddingClient;
import com.carenest.repository.eduplan.EduRagChunkRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Embed lười kho đoạn RAG theo lô 50: đoạn chưa có vector, khác model đang dùng hoặc nội dung đổi (hash lệch). AI tắt,
 * lỗi hay hết ngân sách ⇒ dừng, lần sau làm tiếp (AI-03). Chỉ gửi văn bản chương trình/giáo án mẫu, không dữ liệu trẻ
 * (AI-04); không log nội dung (AI-05). Gọi NGOÀI transaction (có gọi mạng).
 *
 * <p>ponytail: EmbeddingClient chưa hỗ trợ taskType RETRIEVAL_DOCUMENT/QUERY của Gemini; thêm khi đo thấy cần.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EduRagIndexer {

    private static final String LOG_PREFIX = "[EduRagIndexer]";
    private static final int BATCH = 50;

    private final EmbeddingClient embeddingClient;
    private final EduRagChunkRepository repository;
    private final TransactionTemplate transactionTemplate;

    // Một lượt index tại một thời điểm: luồng khởi động và lượt tìm kiếm không embed trùng
    private final AtomicBoolean running = new AtomicBoolean();

    /** Số đoạn đã embed trong lượt này; 0 khi AI tắt, đang có lượt khác chạy hoặc không còn đoạn cũ. */
    public int indexStale(Duration budget) {
        return indexStale(budget, Duration.ZERO);
    }

    /**
     * {@code retryWait} > 0 (luồng nền lúc khởi động): lô lỗi (vd. Gemini gói miễn phí giới hạn ~100 lượt/phút) thì chờ
     * rồi thử lại tới khi hết ngân sách; 0 (lượt tìm kiếm) thì dừng ngay để giáo viên không phải chờ.
     */
    public int indexStale(Duration budget, Duration retryWait) {
        if (!embeddingClient.isAvailable() || !running.compareAndSet(false, true)) {
            return 0;
        }
        int done = 0;
        try {
            String model = embeddingClient.modelName();
            long deadline = System.nanoTime() + budget.toNanos();
            List<EduRagChunk> stale = Objects.requireNonNull(transactionTemplate.execute(status -> stale(model)));
            for (int from = 0; from < stale.size(); from += BATCH) {
                List<EduRagChunk> batch = stale.subList(from, Math.min(stale.size(), from + BATCH));
                List<float[]> vectors;
                try {
                    vectors = embeddingClient.embed(
                            batch.stream().map(EduRagChunk::getContent).toList(), remaining(deadline));
                } catch (RuntimeException ex) {
                    if (retryWait.isZero() || remaining(deadline).compareTo(retryWait) <= 0) throw ex;
                    log.info(
                            "{}|indexStale|retry|type={}|embedded={}",
                            LOG_PREFIX,
                            ex.getClass().getSimpleName(),
                            done);
                    sleep(retryWait);
                    from -= BATCH;
                    continue;
                }
                transactionTemplate.executeWithoutResult(status -> {
                    for (int i = 0; i < batch.size(); i++) {
                        EduRagChunk chunk = batch.get(i);
                        repository.updateEmbedding(
                                chunk.getId(), model, sha256(chunk.getContent()), literal(vectors.get(i)));
                    }
                });
                done += batch.size();
            }
            log.info("{}|indexStale|stale={}|embedded={}", LOG_PREFIX, stale.size(), done);
        } catch (RuntimeException ex) {
            log.warn(
                    "{}|indexStale|ERROR|type={}|embedded={}",
                    LOG_PREFIX,
                    ex.getClass().getSimpleName(),
                    done);
        } finally {
            running.set(false);
        }
        return done;
    }

    private List<EduRagChunk> stale(String model) {
        return repository.findByStatus("ACTIVE").stream()
                .filter(c -> isStale(c, model))
                .toList();
    }

    static boolean isStale(EduRagChunk chunk, String model) {
        return chunk.getModel() == null
                || !chunk.getModel().equals(model)
                || !sha256(chunk.getContent()).equals(chunk.getContentHash());
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static Duration remaining(long deadlineNanos) {
        return Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime()));
    }

    // pgvector nhận chuỗi "[v1,v2,...]"
    static String literal(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(i > 0 ? "," : "").append(vector[i]);
        }
        return sb.append(']').toString();
    }

    static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
