package com.carenest.integration.ai;

import com.carenest.utils.enums.AiDataClass;
import com.carenest.utils.enums.AiTask;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Cấu hình AI (ADR-0007): danh sách provider tương thích OpenAI và chuỗi provider theo task. Đổi provider/model chỉ
 * đổi env. Cấu hình sai (provider lạ, task dữ liệu trẻ đi qua provider có thể train) ⇒ ứng dụng không khởi động.
 */
@ConfigurationProperties("carenest.ai")
public record AiProperties(boolean enabled, List<Provider> providers, Map<AiTask, TaskConfig> tasks) {

    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(30);

    public AiProperties {
        providers = providers == null ? List.of() : List.copyOf(providers);
        tasks = tasks == null ? Map.of() : Map.copyOf(tasks);
        validate(providers, tasks);
    }

    public record Provider(
            String id,
            String baseUrl,
            String model,
            String apiKey,
            boolean mayTrainOnData,
            Duration connectTimeout,
            Duration readTimeout,
            String reasoningEffort) {

        public Provider {
            connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
            readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
        }

        /** Thiếu base URL, model hoặc key ⇒ bỏ khỏi chuỗi, không lỗi khởi động (máy dev không có key). */
        public boolean configured() {
            return StringUtils.hasText(baseUrl) && StringUtils.hasText(model) && StringUtils.hasText(apiKey);
        }

        // Không để API key lọt vào log khi record bị in ra
        @Override
        public String toString() {
            return "Provider[id=" + id + ", baseUrl=" + baseUrl + ", model=" + model + ", mayTrainOnData="
                    + mayTrainOnData + "]";
        }
    }

    public record TaskConfig(List<String> chain, AiDataClass dataClass, Duration deadline, int maxRepairRounds) {

        public TaskConfig {
            chain = chain == null ? List.of() : List.copyOf(chain);
            dataClass = dataClass == null ? AiDataClass.CHILD_DERIVED : dataClass;
            deadline = deadline == null ? DEFAULT_DEADLINE : deadline;
            maxRepairRounds = Math.max(0, maxRepairRounds);
        }
    }

    // AI-04: thiếu khai báo loại dữ liệu thì coi là dữ liệu trẻ (an toàn hơn), nên cũng bị chặn provider có thể train
    private static void validate(List<Provider> providers, Map<AiTask, TaskConfig> tasks) {
        Set<String> ids = new HashSet<>();
        for (Provider provider : providers) {
            if (!StringUtils.hasText(provider.id()) || !ids.add(provider.id())) {
                throw new IllegalStateException("carenest.ai.providers: id trống hoặc trùng: " + provider.id());
            }
        }
        tasks.forEach((task, config) -> {
            for (String id : config.chain()) {
                Provider provider = providers.stream()
                        .filter(p -> p.id().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "carenest.ai.tasks." + task + ": provider không tồn tại: " + id));
                if (config.dataClass() == AiDataClass.CHILD_DERIVED && provider.mayTrainOnData()) {
                    throw new IllegalStateException("carenest.ai.tasks." + task + ": task dữ liệu trẻ không được dùng"
                            + " provider có thể dùng dữ liệu để train: " + id);
                }
            }
        });
    }
}
