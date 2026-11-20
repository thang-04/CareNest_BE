package com.carenest.integration.ai;

import com.carenest.utils.enums.AiDataClass;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.AiTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Gọi lần lượt các provider trong chuỗi của task cho tới khi có nội dung hoặc hết deadline (ADR-0007). Provider
 * thiếu cấu hình bị bỏ qua; task dữ liệu trẻ không bao giờ đi qua provider có thể train (AI-04, đã chặn lúc khởi
 * động, kiểm lại ở đây). Chỉ log metadata (AI-05).
 */
@Slf4j
@Component
public class ProviderChainAiGateway implements AiGateway {

    private static final String LOG_PREFIX = "[ProviderChainAiGateway]";

    private final AiProperties properties;
    private final Map<String, OpenAiCompatibleProvider> providers;
    private final Clock clock;

    @Autowired
    public ProviderChainAiGateway(AiProperties properties) {
        this(properties, OpenAiCompatibleProvider::new, Clock.systemUTC());
    }

    ProviderChainAiGateway(
            AiProperties properties,
            Function<AiProperties.Provider, OpenAiCompatibleProvider> providerFactory,
            Clock clock) {
        this.properties = properties;
        this.providers = properties.providers().stream()
                .collect(Collectors.toUnmodifiableMap(AiProperties.Provider::id, providerFactory));
        this.clock = clock;
    }

    @Override
    public boolean isAvailable(AiTask task) {
        return !usableChain(task).isEmpty();
    }

    @Override
    public Optional<AiProperties.TaskConfig> taskConfig(AiTask task) {
        return Optional.ofNullable(properties.tasks().get(task));
    }

    @Override
    public AiRawResult complete(AiRequest request) {
        List<OpenAiCompatibleProvider> chain = usableChain(request.task());
        if (chain.isEmpty()) {
            throw new AiClientException(AiErrorReason.DISABLED, "No usable AI provider for " + request.task(), null);
        }
        AiClientException lastError = null;
        for (OpenAiCompatibleProvider provider : chain) {
            Duration remaining = Duration.between(Instant.now(clock), request.deadline());
            if (remaining.isNegative() || remaining.isZero()) {
                lastError = new AiClientException(AiErrorReason.TIMEOUT, "AI deadline reached", null);
                break;
            }
            long startedAt = System.nanoTime();
            try {
                OpenAiCompatibleProvider.Completion completion =
                        provider.complete(request.systemPrompt(), request.userPrompt(), remaining);
                long latencyMs = elapsedMillis(startedAt);
                log.info(
                        "{}|complete|task={}|provider={}|model={}|prompt={}|status=OK|latencyMs={}|inputTokens={}|outputTokens={}",
                        LOG_PREFIX,
                        request.task(),
                        provider.id(),
                        provider.model(),
                        request.promptVersion(),
                        latencyMs,
                        completion.inputTokens(),
                        completion.outputTokens());
                return new AiRawResult(
                        completion.content(),
                        provider.id(),
                        provider.model(),
                        request.promptVersion(),
                        completion.inputTokens(),
                        completion.outputTokens(),
                        latencyMs);
            } catch (AiClientException ex) {
                lastError = ex;
                log.warn(
                        "{}|complete|task={}|provider={}|model={}|status=FAILED|reason={}|latencyMs={}",
                        LOG_PREFIX,
                        request.task(),
                        provider.id(),
                        provider.model(),
                        ex.getReason(),
                        elapsedMillis(startedAt));
            }
        }
        throw lastError;
    }

    private List<OpenAiCompatibleProvider> usableChain(AiTask task) {
        AiProperties.TaskConfig config = properties.tasks().get(task);
        if (!properties.enabled() || config == null) {
            return List.of();
        }
        return config.chain().stream()
                .map(providers::get)
                .filter(p -> p != null && p.configured())
                .filter(p -> config.dataClass() == AiDataClass.PUBLIC_CATALOG || !p.mayTrainOnData())
                .toList();
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
