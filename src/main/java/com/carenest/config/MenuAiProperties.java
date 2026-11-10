package com.carenest.config;

import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tham số gợi ý thực đơn (AI-07): số món ứng viên tối đa mỗi ô, trọng số điểm phương án, khung ô món mặc định của bữa
 * khi khối chưa có thực đơn mẫu đang dùng. Trọng số chỉ chỉnh khi có kết quả eval.
 */
@ConfigurationProperties("carenest.menu-ai")
public record MenuAiProperties(
        Integer candidateLimit, Weights weights, Map<MealSession, List<List<DishType>>> defaultSlots) {

    private static final int DEFAULT_CANDIDATE_LIMIT = 15;

    public MenuAiProperties {
        candidateLimit = candidateLimit == null || candidateLimit < 1 ? DEFAULT_CANDIDATE_LIMIT : candidateLimit;
        weights = weights == null ? Weights.DEFAULT : weights;
        defaultSlots = defaultSlots == null || defaultSlots.isEmpty() ? standardSlots() : Map.copyOf(defaultSlots);
    }

    public static MenuAiProperties defaults() {
        return new MenuAiProperties(null, null, null);
    }

    /** Trọng số (tổng 100): sát định mức, giá, đa dạng, cảnh báo. */
    public record Weights(int norm, int cost, int variety, int warnings) {
        static final Weights DEFAULT = new Weights(45, 20, 25, 10);
    }

    // Bữa trưa cơm + mặn + canh + rau; bữa phụ một món (khung thực đơn mẫu đang dùng của khối được ưu tiên hơn)
    private static Map<MealSession, List<List<DishType>>> standardSlots() {
        Map<MealSession, List<List<DishType>>> slots = new EnumMap<>(MealSession.class);
        slots.put(MealSession.MORNING, List.of(List.of(DishType.SNACK, DishType.DESSERT)));
        slots.put(
                MealSession.LUNCH,
                List.of(
                        List.of(DishType.CARB),
                        List.of(DishType.MAIN),
                        List.of(DishType.SOUP),
                        List.of(DishType.VEGETABLE)));
        slots.put(MealSession.AFTERNOON, List.of(List.of(DishType.CARB, DishType.SNACK, DishType.DESSERT)));
        return Map.copyOf(slots);
    }
}
