package com.carenest.integration.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Đọc prompt có version từ {@code classpath:prompts/<name>.txt}. Tên prompt (vd. "summary/draft_v1") chính là
 * prompt version lưu kèm nháp; sửa prompt ⇒ tạo file version mới, không sửa file cũ.
 */
@Component
public class PromptTemplateLoader {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String name) {
        return cache.computeIfAbsent(name, PromptTemplateLoader::read);
    }

    private static String read(String name) {
        ClassPathResource resource = new ClassPathResource("prompts/" + name + ".txt");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Prompt template not found: " + name, ex);
        }
    }
}
