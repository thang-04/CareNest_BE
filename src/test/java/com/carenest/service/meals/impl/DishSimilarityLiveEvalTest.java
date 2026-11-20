package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.EmbeddingProperties;
import com.carenest.integration.ai.OpenAiCompatibleEmbeddingClient;
import com.carenest.utils.enums.DishType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Eval truy xuất của vector xếp lại món (AC-17, AC-18): 10 câu "mong muốn" so với danh mục món giả, in top-5 để chấm
 * tay. Gọi embedding API thật nên mặc định tắt; bật bằng {@code AI_LIVE_EVAL_EMBEDDING=true} cùng {@code
 * AI_EMBEDDING_MODEL}, {@code AI_EMBEDDING_API_KEY} (tuỳ chọn {@code AI_EMBEDDING_BASE_URL}, {@code
 * AI_EMBEDDING_DIMENSIONS}, {@code AI_LIVE_EVAL_PAUSE_MS}). Không cần Docker, không ghi DB. Văn bản embed dựng bằng đúng
 * {@link DishSimilarity#describe}. Kết quả ghi ở {@code target/ai-eval/embedding-<thời điểm>/}: chép vào {@code
 * docs/quality/ai-eval/<ngày>/}. Cột "Khớp từ khóa" chỉ là ước lượng tự động, không thay chấm tay.
 */
@EnabledIfEnvironmentVariable(named = "AI_LIVE_EVAL_EMBEDDING", matches = "true")
class DishSimilarityLiveEvalTest {

    private static final int TOP = 5;
    private static final Duration DISH_BUDGET = Duration.ofSeconds(90);
    private static final Duration QUERY_BUDGET = Duration.ofSeconds(30);

    private record EvalDish(String name, DishType type, List<String> foods, Set<String> seasons) {
        String text() {
            return DishSimilarity.describe(name, type, foods, seasons);
        }

        String haystack() {
            return (name + " " + String.join(" ", foods)).toLowerCase(Locale.ROOT);
        }
    }

    // keywords: từ khóa mà món liên quan thường chứa trong tên hoặc thực phẩm chính (chỉ để ước lượng tự động)
    private record Wish(String text, List<String> keywords) {}

    private static final List<EvalDish> CATALOG = List.of(
            dish("Cơm trắng", DishType.CARB, List.of("Gạo tẻ"), Set.of()),
            dish("Xôi đậu xanh", DishType.CARB, List.of("Gạo nếp", "Đậu xanh"), Set.of("Mùa đông")),
            dish("Bún chả", DishType.CARB, List.of("Bún", "Thịt lợn"), Set.of()),
            dish("Cháo thịt bằm", DishType.CARB, List.of("Gạo tẻ", "Thịt lợn nạc"), Set.of("Mùa đông")),
            dish("Cháo cá lóc", DishType.CARB, List.of("Gạo tẻ", "Cá lóc"), Set.of("Mùa đông")),
            dish("Bánh mì trứng", DishType.CARB, List.of("Bánh mì", "Trứng gà"), Set.of()),
            dish("Miến gà", DishType.CARB, List.of("Miến dong", "Thịt gà"), Set.of("Mùa đông")),
            dish("Phở bò", DishType.CARB, List.of("Bánh phở", "Thịt bò"), Set.of("Mùa đông")),
            dish("Thịt gà rim", DishType.MAIN, List.of("Thịt gà", "Hành"), Set.of()),
            dish("Gà hấp hành", DishType.MAIN, List.of("Thịt gà", "Hành lá"), Set.of()),
            dish("Thịt lợn rim", DishType.MAIN, List.of("Thịt lợn", "Nước mắm"), Set.of("Mùa đông")),
            dish("Cá hấp xì dầu", DishType.MAIN, List.of("Cá trắm", "Gừng"), Set.of()),
            dish("Cá kho", DishType.MAIN, List.of("Cá chép", "Nước mắm"), Set.of("Mùa đông")),
            dish("Đậu phụ sốt cà chua", DishType.MAIN, List.of("Đậu phụ", "Cà chua"), Set.of()),
            dish("Đậu phụ nhồi thịt", DishType.MAIN, List.of("Đậu phụ", "Thịt lợn"), Set.of()),
            dish("Trứng đúc thịt", DishType.MAIN, List.of("Trứng gà", "Thịt lợn"), Set.of()),
            dish("Trứng hấp", DishType.MAIN, List.of("Trứng gà"), Set.of()),
            dish("Tôm rim", DishType.MAIN, List.of("Tôm đồng", "Nước mắm"), Set.of()),
            dish("Thịt bò xào hành tây", DishType.MAIN, List.of("Thịt bò", "Hành tây"), Set.of()),
            dish("Chả cá hấp", DishType.MAIN, List.of("Cá", "Thì là"), Set.of("Mùa hè")),
            dish("Canh bí đỏ nấu thịt", DishType.SOUP, List.of("Bí đỏ", "Thịt lợn"), Set.of("Mùa đông")),
            dish("Canh rau ngót", DishType.SOUP, List.of("Rau ngót", "Thịt lợn"), Set.of("Mùa hè")),
            dish("Canh bầu tôm", DishType.SOUP, List.of("Bầu", "Tôm"), Set.of("Mùa hè")),
            dish("Canh cua rau đay", DishType.SOUP, List.of("Cua đồng", "Rau đay"), Set.of("Mùa hè")),
            dish("Canh bí xanh", DishType.SOUP, List.of("Bí xanh", "Thịt lợn"), Set.of("Mùa hè")),
            dish("Canh mướp nấu tôm", DishType.SOUP, List.of("Mướp", "Tôm"), Set.of("Mùa hè")),
            dish("Súp gà ngô", DishType.SOUP, List.of("Thịt gà", "Ngô ngọt"), Set.of("Mùa đông")),
            dish("Canh cải xanh", DishType.SOUP, List.of("Cải xanh", "Thịt lợn"), Set.of()),
            dish("Rau muống luộc", DishType.VEGETABLE, List.of("Rau muống"), Set.of("Mùa hè")),
            dish("Su su xào", DishType.VEGETABLE, List.of("Su su", "Tỏi"), Set.of()),
            dish("Cải thìa xào tỏi", DishType.VEGETABLE, List.of("Cải thìa", "Tỏi"), Set.of()),
            dish("Bắp cải luộc", DishType.VEGETABLE, List.of("Bắp cải"), Set.of("Mùa đông")),
            dish("Đậu cô ve xào", DishType.VEGETABLE, List.of("Đậu cô ve", "Tỏi"), Set.of()),
            dish("Cà rốt su hào luộc", DishType.VEGETABLE, List.of("Cà rốt", "Su hào"), Set.of("Mùa đông")),
            dish("Chuối chín", DishType.DESSERT, List.of("Chuối tiêu"), Set.of()),
            dish("Dưa hấu", DishType.DESSERT, List.of("Dưa hấu"), Set.of("Mùa hè")),
            dish("Sữa chua", DishType.SNACK, List.of("Sữa chua"), Set.of()),
            dish("Chè đậu xanh", DishType.DESSERT, List.of("Đậu xanh", "Đường"), Set.of("Mùa hè")),
            dish("Bánh flan", DishType.DESSERT, List.of("Trứng gà", "Sữa tươi"), Set.of()),
            dish("Sữa tươi và bánh quy", DishType.SNACK, List.of("Sữa tươi", "Bánh quy"), Set.of()));

    private static final List<Wish> WISHES = List.of(
            new Wish("món nhiều rau xanh", List.of("rau", "cải", "su su", "bắp cải", "đậu cô ve", "bí xanh")),
            new Wish("món có cá", List.of("cá")),
            new Wish("món canh thanh mát cho ngày hè", List.of("canh", "bầu", "mướp", "bí xanh", "rau")),
            new Wish("món ấm nóng cho mùa đông", List.of("cháo", "phở", "súp", "miến", "kho", "bí đỏ", "xôi")),
            new Wish("trái cây hoặc đồ tráng miệng", List.of("chuối", "dưa", "chè", "flan", "sữa chua")),
            new Wish("món hấp hoặc luộc, ít dầu mỡ", List.of("hấp", "luộc")),
            new Wish("món làm từ đậu phụ hoặc trứng", List.of("đậu phụ", "trứng")),
            new Wish("món giàu đạm từ thịt gà", List.of("gà")),
            new Wish("món bột hoặc cháo mềm cho trẻ nhỏ", List.of("cháo", "bột", "súp")),
            new Wish("món có tôm hoặc cua", List.of("tôm", "cua")));

    private static EvalDish dish(String name, DishType type, List<String> foods, Set<String> seasons) {
        return new EvalDish(name, type, foods, seasons);
    }

    @Test
    @DisplayName("AC-17: top-5 món truy xuất cho 10 câu mong muốn bằng embedding thật (chấm tay)")
    void retrievalTopFiveForTenWishes() throws IOException, InterruptedException {
        EmbeddingProperties properties = new EmbeddingProperties(
                env("AI_EMBEDDING_BASE_URL", "https://generativelanguage.googleapis.com/v1beta/openai"),
                env("AI_EMBEDDING_MODEL", ""),
                env("AI_EMBEDDING_API_KEY", ""),
                Integer.parseInt(env("AI_EMBEDDING_DIMENSIONS", "0")),
                null,
                null,
                null,
                DISH_BUDGET);
        OpenAiCompatibleEmbeddingClient client =
                new OpenAiCompatibleEmbeddingClient(new AiProperties(true, List.of(), Map.of()), properties);
        assertThat(client.isAvailable())
                .as("cần AI_EMBEDDING_MODEL và AI_EMBEDDING_API_KEY")
                .isTrue();
        long pauseMs = Long.parseLong(env("AI_LIVE_EVAL_PAUSE_MS", "700"));

        long dishStart = System.nanoTime();
        List<float[]> dishVectors =
                client.embed(CATALOG.stream().map(EvalDish::text).toList(), DISH_BUDGET);
        long dishMs = (System.nanoTime() - dishStart) / 1_000_000;

        StringBuilder md = new StringBuilder();
        StringBuilder csv = new StringBuilder("﻿wish,rank,dish,type,score,keyword_hit,manual_grade\n");
        double hitSum = 0;
        long queryMsSum = 0;
        for (Wish wish : WISHES) {
            String text = MenuAiPromptBuilder.clean(wish.text(), MenuAiPromptBuilder.WISHES_MAX);
            long start = System.nanoTime();
            float[] query = client.embed(List.of(text), QUERY_BUDGET).getFirst();
            queryMsSum += (System.nanoTime() - start) / 1_000_000;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < CATALOG.size(); i++) {
                order.add(i);
            }
            order.sort(Comparator.comparingDouble((Integer i) -> -cosine(query, dishVectors.get(i))));
            md.append("\n### ").append(wish.text()).append("\n\n");
            md.append("| # | Món | Loại | Điểm | Khớp từ khóa | Chấm tay (0/1) |\n|---|---|---|---|---|---|\n");
            int hits = 0;
            for (int rank = 0; rank < TOP; rank++) {
                EvalDish d = CATALOG.get(order.get(rank));
                double score = cosine(query, dishVectors.get(order.get(rank)));
                boolean hit = wish.keywords().stream().anyMatch(d.haystack()::contains);
                hits += hit ? 1 : 0;
                md.append(String.format(
                        Locale.ROOT,
                        "| %d | %s | %s | %.3f | %s |  |%n",
                        rank + 1,
                        d.name(),
                        d.type(),
                        score,
                        hit ? "có" : "không"));
                csv.append(String.format(
                        Locale.ROOT,
                        "\"%s\",%d,\"%s\",%s,%.4f,%s,%n",
                        wish.text(),
                        rank + 1,
                        d.name(),
                        d.type(),
                        score,
                        hit ? 1 : 0));
            }
            hitSum += hits / (double) TOP;
            Thread.sleep(pauseMs);
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = Path.of("target", "ai-eval", "embedding-" + stamp);
        Files.createDirectories(dir);
        String summary = String.format(
                Locale.ROOT,
                "# Eval truy xuất embedding%n%n- Model: %s%n- Danh mục giả: %d món; %d câu mong muốn; top-%d%n"
                        + "- Embed danh mục: %d ms (một lần)%n- Embed câu mong muốn: trung bình %d ms%n"
                        + "- Khớp từ khóa trung bình (ước lượng tự động, không thay chấm tay): %.0f%%%n"
                        + "- Điền cột \"Chấm tay\" (1 = món hợp ý câu mong muốn) rồi tính precision@%d%n%s",
                properties.model(),
                CATALOG.size(),
                WISHES.size(),
                TOP,
                dishMs,
                queryMsSum / WISHES.size(),
                100 * hitSum / WISHES.size(),
                TOP,
                md);
        Files.writeString(dir.resolve("retrieval.md"), summary, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("retrieval.csv"), csv.toString(), StandardCharsets.UTF_8);
        System.out.println("[DishSimilarityLiveEvalTest] kết quả: " + dir.toAbsolutePath());
        assertThat(dishVectors).hasSameSizeAs(CATALOG);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
