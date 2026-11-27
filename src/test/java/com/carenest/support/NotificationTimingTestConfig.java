package com.carenest.support;

import com.carenest.config.NotificationTimingProperties;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

/** Test cũng đọc YAML thật để không tạo một bộ mặc định thời gian thứ hai trong fixture. */
public final class NotificationTimingTestConfig {
    private NotificationTimingTestConfig() {}

    public static NotificationTimingProperties load() {
        return load(Map.of());
    }

    public static NotificationTimingProperties load(Map<String, Object> overrides) {
        try {
            MutablePropertySources sources = new MutablePropertySources();
            sources.addFirst(new MapPropertySource("test-overrides", overrides));
            new YamlPropertySourceLoader()
                    .load("timing-yaml", new ClassPathResource("application.yml"))
                    .forEach(sources::addLast);
            return new Binder(
                            ConfigurationPropertySources.from(sources),
                            new PropertySourcesPlaceholdersResolver(sources))
                    .bind("carenest", Bindable.of(NotificationTimingProperties.class))
                    .orElseThrow(() -> new IllegalStateException("Thiếu cấu hình thời gian"));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
