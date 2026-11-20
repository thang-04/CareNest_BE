package com.carenest.service.meals.impl;

import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishEmbedding;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.MenuTemplateItem;
import com.carenest.entity.meals.MenuTemplateSet;
import com.carenest.integration.ai.EmbeddingClient;
import com.carenest.repository.meals.DishEmbeddingRepository;
import com.carenest.repository.meals.DishEmbeddingRepository.ScoreRow;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.MenuTemplateSetRepository;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phần "mềm" của truy xuất lai (AI-06): điểm giống ngữ nghĩa của món với mong muốn của PHT (hoặc với món cần thay),
 * chỉ để xếp lại thứ tự món ứng viên SAU lọc cứng bằng SQL ({@link MenuCandidateBuilder}). Embedding tắt, lỗi hoặc hết
 * ngân sách thời gian ⇒ trả rỗng, thứ tự ứng viên như trước (AI-03). Vector món được embed lười theo lô và lưu ở
 * {@code dish_embeddings}; chỉ văn bản danh mục công khai và câu mong muốn rời khỏi hệ thống (AI-04). Không log nội
 * dung (AI-05).
 *
 * <p>Gọi NGOÀI transaction: có gọi mạng. Ngoại lệ: các phương thức {@code cached} chỉ đọc cache và DB, dùng được
 * trong transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DishSimilarity {

    private static final String LOG_PREFIX = "[DishSimilarity]";
    private static final int QUERY_CACHE_SIZE = 64;
    private static final int FOODS_PER_DISH = 5;

    private final EmbeddingClient embeddingClient;
    private final DishRepository dishRepository;
    private final DishEmbeddingRepository embeddingRepository;
    private final MenuTemplateSetRepository templateRepository;
    private final TransactionTemplate transactionTemplate;

    // Vector câu mong muốn đã embed (giữ trong bộ nhớ): đọc lại ngữ cảnh của nháp khi sửa/dùng không phải gọi mạng lại
    private final Map<String, float[]> queryCache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return size() > QUERY_CACHE_SIZE;
        }
    });

    /** Món gần với mong muốn {@code wishes}: điểm cosine theo id món; rỗng khi không có mong muốn hoặc không có vector. */
    Map<Long, Double> byWishes(String wishes) {
        return scoreByWishes(wishes, true);
    }

    /** Như {@link #byWishes} nhưng không gọi mạng: chỉ dùng vector đã lưu và câu mong muốn đã có trong cache. */
    Map<Long, Double> byWishesCached(String wishes) {
        return scoreByWishes(wishes, false);
    }

    /** Món gần món {@code fromDishId} (thay một món ⇒ món cùng ý, khác món cũ); rỗng khi thiếu vector. */
    Map<Long, Double> likeDish(Long fromDishId) {
        if (fromDishId == null || !embeddingClient.isAvailable()) {
            return Map.of();
        }
        try {
            long deadline = System.nanoTime() + embeddingClient.budget().toNanos();
            ensureDishVectors(deadline, fromDishId);
            return toMap(embeddingRepository.similarToDish(fromDishId, embeddingClient.modelName()));
        } catch (RuntimeException ex) {
            log.warn("{}|likeDish|ERROR|type={}", LOG_PREFIX, ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    private Map<Long, Double> scoreByWishes(String wishes, boolean allowNetwork) {
        String text = normalize(wishes);
        if (text.isEmpty() || !embeddingClient.isAvailable()) {
            return Map.of();
        }
        try {
            String model = embeddingClient.modelName();
            long deadline = System.nanoTime() + embeddingClient.budget().toNanos();
            // Embed câu mong muốn trước: lỗi ở đây thì khỏi tốn ngân sách cho vector món
            float[] query = queryCache.get(model + '\u0000' + text);
            if (query == null) {
                if (!allowNetwork) {
                    return Map.of();
                }
                query = embeddingClient
                        .embed(List.of(text), remaining(deadline))
                        .getFirst();
                queryCache.put(model + '\u0000' + text, query);
            }
            if (allowNetwork) {
                ensureDishVectors(deadline, null);
            }
            Map<Long, Double> scores = toMap(embeddingRepository.similarTo(literal(query), model));
            log.info("{}|byWishes|network={}|scored={}", LOG_PREFIX, allowNetwork, scores.size());
            return scores;
        } catch (RuntimeException ex) {
            log.warn("{}|byWishes|ERROR|type={}", LOG_PREFIX, ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    /**
     * Embed lười theo lô cho món đang dùng thiếu vector hoặc lệch model/hash nội dung, trong ngân sách còn lại; lô nào
     * xong thì lưu ngay nên lần gợi ý sau dùng tiếp. {@code priorityDishId} được embed trước.
     */
    private void ensureDishVectors(long deadlineNanos, Long priorityDishId) {
        String model = embeddingClient.modelName();
        List<Doc> stale =
                Objects.requireNonNull(transactionTemplate.execute(status -> staleDocs(model, priorityDishId)));
        int batch = 50;
        for (int from = 0; from < stale.size(); from += batch) {
            List<Doc> chunk = stale.subList(from, Math.min(stale.size(), from + batch));
            List<float[]> vectors =
                    embeddingClient.embed(chunk.stream().map(Doc::text).toList(), remaining(deadlineNanos));
            transactionTemplate.executeWithoutResult(status -> {
                for (int i = 0; i < chunk.size(); i++) {
                    embeddingRepository.upsert(
                            chunk.get(i).id(), model, chunk.get(i).hash(), literal(vectors.get(i)));
                }
            });
        }
    }

    private List<Doc> staleDocs(String model, Long priorityDishId) {
        Map<Long, String> stored = embeddingRepository.findByModel(model).stream()
                .collect(Collectors.toMap(DishEmbedding::getDishId, DishEmbedding::getContentHash));
        Map<Long, Set<String>> seasons = seasonLabels();
        List<Doc> stale = new ArrayList<>();
        for (Dish dish : dishRepository.findAllByOrderByNameAsc()) {
            if (dish.getStatus() != CatalogStatus.ACTIVE) {
                continue;
            }
            String text = describe(dish, seasons.getOrDefault(dish.getId(), Set.of()));
            String hash = sha256(text);
            if (!hash.equals(stored.get(dish.getId()))) {
                stale.add(new Doc(dish.getId(), text, hash));
            }
        }
        if (priorityDishId != null) {
            stale.sort(Comparator.comparing((Doc d) -> !d.id().equals(priorityDishId)));
        }
        return stale;
    }

    // Nhãn mùa của các bộ mẫu chứa món: món "hợp mùa" gần nghĩa với mong muốn như "món mát ngày hè"
    private Map<Long, Set<String>> seasonLabels() {
        Map<Long, Set<String>> byDish = new HashMap<>();
        for (MenuTemplateSet set : templateRepository.findAllByOrderByMenuBlockAscNameAsc()) {
            if (set.getStatus() != CatalogStatus.ACTIVE
                    || set.getSeasonLabel() == null
                    || set.getSeasonLabel().isBlank()) {
                continue;
            }
            for (MenuTemplateItem item : set.getItems()) {
                byDish.computeIfAbsent(item.getDishId(), k -> new TreeSet<>())
                        .add(set.getSeasonLabel().trim());
            }
        }
        return byDish;
    }

    /** Văn bản embed của món: tên, loại, thực phẩm chính (nhiều gam nhất), nhãn mùa. Không có dữ liệu trẻ. */
    static String describe(Dish dish, Set<String> seasons) {
        List<String> foods = dish.getIngredients().stream()
                .sorted(Comparator.comparing(DishSimilarity::grams).reversed())
                .map(i -> i.getFood().getName())
                .distinct()
                .limit(FOODS_PER_DISH)
                .toList();
        return describe(dish.getName(), dish.getDishType(), foods, seasons);
    }

    /** Dựng văn bản từ giá trị thô; eval truy xuất (`DishSimilarityLiveEvalTest`) dùng đúng hàm này. */
    static String describe(String name, DishType type, List<String> foods, Set<String> seasons) {
        StringBuilder text = new StringBuilder("Món ")
                .append(name)
                .append(". Loại: ")
                .append(typeLabel(type))
                .append('.');
        if (!foods.isEmpty()) {
            text.append(" Thực phẩm chính: ").append(String.join(", ", foods)).append('.');
        }
        if (!seasons.isEmpty()) {
            text.append(" Mùa: ").append(String.join(", ", seasons)).append('.');
        }
        return text.toString();
    }

    private static BigDecimal grams(DishIngredient ingredient) {
        return ingredient.getGramsPerChild() == null ? BigDecimal.ZERO : ingredient.getGramsPerChild();
    }

    private static String typeLabel(DishType type) {
        return switch (type) {
            case CARB -> "tinh bột (cơm, bún, xôi)";
            case MAIN -> "món mặn";
            case SOUP -> "canh, súp";
            case VEGETABLE -> "rau";
            case SNACK -> "bữa phụ";
            case DESSERT -> "tráng miệng";
        };
    }

    private static Duration remaining(long deadlineNanos) {
        return Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime()));
    }

    private static Map<Long, Double> toMap(List<ScoreRow> rows) {
        Map<Long, Double> scores = new HashMap<>();
        for (ScoreRow row : rows) {
            if (row.getScore() != null) {
                scores.put(row.getDishId(), row.getScore());
            }
        }
        return scores;
    }

    // Cùng cách làm sạch với câu mong muốn lưu trong nháp (clean là idempotent) ⇒ đọc lại nháp trúng cache
    private static String normalize(String wishes) {
        return MenuAiPromptBuilder.clean(wishes, MenuAiPromptBuilder.WISHES_MAX);
    }

    // pgvector nhận chuỗi "[v1,v2,...]"
    static String literal(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
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

    private record Doc(Long id, String text, String hash) {}
}
