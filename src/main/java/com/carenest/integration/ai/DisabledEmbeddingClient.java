package com.carenest.integration.ai;

import com.carenest.utils.enums.AiErrorReason;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Adapter khi AI tắt: không ingest được tài liệu, truy xuất RAG trả rỗng ở phía caller. */
@Component
@ConditionalOnProperty(prefix = "carenest.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledEmbeddingClient implements EmbeddingClient {

    @Override
    public float[] embed(String text) {
        throw disabled();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        throw disabled();
    }

    @Override
    public String modelName() {
        return "disabled";
    }

    private static AiClientException disabled() {
        return new AiClientException(AiErrorReason.DISABLED, "Embedding is disabled", null);
    }
}
