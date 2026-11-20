package com.carenest.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.utils.enums.AiDataClass;
import com.carenest.utils.enums.AiTask;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AiPropertiesTest {

    @Test
    @DisplayName("AI-09: cấu hình provider và task bind từ khóa kebab-case, task theo tên enum")
    void bindsProvidersAndTasksFromConfiguration() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("carenest.ai.enabled", "true");
        source.put("carenest.ai.providers[0].id", "gemini");
        source.put("carenest.ai.providers[0].base-url", "http://gemini.test");
        source.put("carenest.ai.providers[0].model", "flash");
        source.put("carenest.ai.providers[0].api-key", "k1");
        source.put("carenest.ai.providers[0].may-train-on-data", "true");
        source.put("carenest.ai.providers[1].id", "groq");
        source.put("carenest.ai.providers[1].base-url", "http://groq.test");
        source.put("carenest.ai.providers[1].model", "oss");
        source.put("carenest.ai.providers[1].read-timeout", "12s");
        source.put("carenest.ai.tasks.MENU_WEEKLY.chain[0]", "gemini");
        source.put("carenest.ai.tasks.MENU_WEEKLY.chain[1]", "groq");
        source.put("carenest.ai.tasks.MENU_WEEKLY.data-class", "PUBLIC_CATALOG");
        source.put("carenest.ai.tasks.MENU_WEEKLY.deadline", "25s");
        source.put("carenest.ai.tasks.MENU_WEEKLY.max-repair-rounds", "2");

        AiProperties properties = new Binder(new MapConfigurationPropertySource(source))
                .bind("carenest.ai", AiProperties.class)
                .get();

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.providers()).extracting(AiProperties.Provider::id).containsExactly("gemini", "groq");
        assertThat(properties.providers().get(0).configured()).isTrue();
        // Thiếu key ⇒ provider vẫn hợp lệ nhưng bị bỏ khỏi chuỗi lúc gọi
        assertThat(properties.providers().get(1).configured()).isFalse();
        assertThat(properties.providers().get(1).readTimeout()).isEqualTo(Duration.ofSeconds(12));
        assertThat(properties.providers().get(1).connectTimeout()).isEqualTo(AiProperties.DEFAULT_CONNECT_TIMEOUT);
        AiProperties.TaskConfig weekly = properties.tasks().get(AiTask.MENU_WEEKLY);
        assertThat(weekly.chain()).containsExactly("gemini", "groq");
        assertThat(weekly.dataClass()).isEqualTo(AiDataClass.PUBLIC_CATALOG);
        assertThat(weekly.deadline()).isEqualTo(Duration.ofSeconds(25));
        assertThat(weekly.maxRepairRounds()).isEqualTo(2);
    }

    @Test
    @DisplayName("AI-04: task dữ liệu trẻ cấu hình provider có thể train ⇒ không khởi động")
    void rejectsChildDataTaskOnTrainingProvider() {
        assertThatThrownBy(() -> new AiProperties(
                        true,
                        List.of(provider("gemini", true)),
                        Map.of(AiTask.MENU_WEEKLY, task(AiDataClass.CHILD_DERIVED, "gemini"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini");
    }

    @Test
    @DisplayName("AI-04: task không khai loại dữ liệu bị coi là dữ liệu trẻ")
    void treatsMissingDataClassAsChildData() {
        assertThat(task(null, "groq").dataClass()).isEqualTo(AiDataClass.CHILD_DERIVED);
        assertThatThrownBy(() -> new AiProperties(
                        true, List.of(provider("gemini", true)), Map.of(AiTask.MENU_DAILY, task(null, "gemini"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AI-09: chuỗi trỏ provider không tồn tại hoặc id trùng ⇒ không khởi động")
    void rejectsUnknownOrDuplicateProvider() {
        assertThatThrownBy(() -> new AiProperties(
                        true,
                        List.of(provider("groq", false)),
                        Map.of(AiTask.MENU_WEEKLY, task(AiDataClass.PUBLIC_CATALOG, "gemini"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini");
        assertThatThrownBy(() ->
                        new AiProperties(true, List.of(provider("groq", false), provider("groq", false)), Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AI-04: lỗi cấu hình làm hỏng bind (ứng dụng dừng khi khởi động)")
    void bindingFailsOnUnsafeConfiguration() {
        Map<String, String> source = Map.of(
                "carenest.ai.providers[0].id", "gemini",
                "carenest.ai.providers[0].may-train-on-data", "true",
                "carenest.ai.tasks.MENU_WEEKLY.chain[0]", "gemini",
                "carenest.ai.tasks.MENU_WEEKLY.data-class", "CHILD_DERIVED");

        assertThatThrownBy(() ->
                        new Binder(new MapConfigurationPropertySource(source)).bind("carenest.ai", AiProperties.class))
                .isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("AI-05: API key không xuất hiện khi in cấu hình")
    void toStringHidesApiKey() {
        assertThat(provider("groq", false).toString())
                .doesNotContain("secret-key")
                .contains("groq");
    }

    @Test
    @DisplayName("AI-03: không cấu hình gì ⇒ danh sách rỗng, không lỗi")
    void emptyConfigurationIsValid() {
        AiProperties properties = new AiProperties(false, null, null);

        assertThat(properties.providers()).isEmpty();
        assertThat(properties.tasks()).isEmpty();
    }

    private static AiProperties.Provider provider(String id, boolean mayTrain) {
        return new AiProperties.Provider(id, "http://" + id + ".test", "m", "secret-key", mayTrain, null, null, null);
    }

    private static AiProperties.TaskConfig task(AiDataClass dataClass, String... chain) {
        return new AiProperties.TaskConfig(List.of(chain), dataClass, null, 0);
    }
}
