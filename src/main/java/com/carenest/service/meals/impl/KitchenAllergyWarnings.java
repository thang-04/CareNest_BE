package com.carenest.service.meals.impl;

import com.carenest.dto.meals.KitchenDayResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.Allergen;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Số trẻ dị ứng theo lớp cho bếp (NUT-23, AUTH-05): đối chiếu chữ đã khai với danh mục lúc đọc, chỉ trả số đếm, không
 * trả trẻ. Đếm trẻ báo ăn (CHD-13) đang học trong lớp vào ngày xem, không theo điểm danh.
 */
@Component
@RequiredArgsConstructor
class KitchenAllergyWarnings {

    private static final Map<String, Allergen> BY_NAME = namesOfCatalog();

    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final ChildService childService;
    private final MealDemandCalculator demandCalculator;
    private final NutritionCatalogMapper catalogMapper;

    private record ClassTally(
            ClassroomInfoResponse classroom,
            MenuBlock block,
            Map<Allergen, Integer> known,
            Map<String, Integer> unknown) {}

    /** Kết quả của một điểm trường–ngày; {@code unknownLabels}: khóa chuẩn hóa → chữ đã khai gặp đầu tiên. */
    final class Report {
        private final List<ClassTally> classes;
        private final Map<String, String> unknownLabels;

        private Report(List<ClassTally> classes, Map<String, String> unknownLabels) {
            this.classes = classes;
            this.unknownLabels = unknownLabels;
        }

        /** Chỉ chất có trẻ dị ứng ở lớp đúng khối và ăn bữa đó (NUT-13, NUT-17). */
        List<KitchenDayResponse.DishAllergyWarning> warnings(
                MealSession session, MenuBlock block, Collection<Allergen> dishAllergens) {
            return dishAllergens.stream()
                    .distinct()
                    .sorted()
                    .map(allergen -> new KitchenDayResponse.DishAllergyWarning(
                            catalogMapper.toResponse(allergen),
                            byClass(
                                    t -> t.block() == block
                                            && t.classroom().mealSessions().contains(session),
                                    t -> t.known().getOrDefault(allergen, 0))))
                    .filter(w -> !w.byClass().isEmpty())
                    .toList();
        }

        List<KitchenDayResponse.UnmatchedAllergy> unmatched() {
            return unknownLabels.entrySet().stream()
                    .map(e -> new KitchenDayResponse.UnmatchedAllergy(
                            e.getValue(), byClass(t -> true, t -> t.unknown().getOrDefault(e.getKey(), 0))))
                    .toList();
        }

        private List<KitchenDayResponse.AllergyClassCount> byClass(
                Predicate<ClassTally> included, ToIntFunction<ClassTally> count) {
            return classes.stream()
                    .filter(included)
                    .filter(t -> count.applyAsInt(t) > 0)
                    .map(t -> new KitchenDayResponse.AllergyClassCount(
                            t.classroom().id(), t.classroom().name(), count.applyAsInt(t)))
                    .toList();
        }
    }

    /** Tên khai trùng tên chất trong danh mục hoặc bí danh đã chốt; giữ dấu tiếng Việt để không lẫn chất. */
    static Optional<Allergen> match(String declared) {
        return Optional.ofNullable(BY_NAME.get(key(declared)));
    }

    Report empty() {
        return new Report(List.of(), Map.of());
    }

    Report load(Long campusId, LocalDate date) {
        List<ClassroomInfoResponse> classes = classroomService.findActiveYearClassrooms().stream()
                .filter(c -> campusId.equals(c.campusId()) && !c.mealSessions().isEmpty())
                .filter(c -> !date.isBefore(c.yearStart()) && !date.isAfter(c.yearEnd()))
                .sorted(Comparator.comparing(ClassroomInfoResponse::name).thenComparing(ClassroomInfoResponse::id))
                .toList();
        if (classes.isEmpty()) {
            return empty();
        }
        Map<Long, Set<Long>> enrolled = enrollmentService.findChildIdsInClassrooms(
                classes.stream().map(ClassroomInfoResponse::id).toList(), date);
        Set<Long> children = enrolled.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Set<Long> withoutMeals = childService.findChildIdsWithoutMeals(children);
        Map<Long, List<String>> declared = childService.findAllergyNames(
                children.stream().filter(id -> !withoutMeals.contains(id)).toList());

        Map<Long, MenuBlock> blocks = demandCalculator.blockOfAgeGroup();
        Map<String, String> unknownLabels = new TreeMap<>();
        List<ClassTally> tallies = new ArrayList<>();
        for (ClassroomInfoResponse c : classes) {
            Map<Allergen, Integer> known = new EnumMap<>(Allergen.class);
            Map<String, Integer> unknown = new HashMap<>();
            for (Long childId :
                    enrolled.getOrDefault(c.id(), Set.of()).stream().sorted().toList()) {
                // Mỗi trẻ chỉ tính một lần cho một chất dù khai nhiều dòng
                Set<Allergen> matched = EnumSet.noneOf(Allergen.class);
                Set<String> others = new LinkedHashSet<>();
                for (String name : declared.getOrDefault(childId, List.of())) {
                    match(name).ifPresentOrElse(matched::add, () -> {
                        String key = key(name);
                        if (!key.isEmpty()) {
                            others.add(key);
                            unknownLabels.putIfAbsent(key, clean(name));
                        }
                    });
                }
                matched.forEach(a -> known.merge(a, 1, Integer::sum));
                others.forEach(o -> unknown.merge(o, 1, Integer::sum));
            }
            tallies.add(new ClassTally(c, blocks.get(c.ageGroupId()), known, unknown));
        }
        return new Report(tallies, unknownLabels);
    }

    private static String clean(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC)
                .replaceAll("(?U)\\s+", " ")
                .trim();
    }

    private static String key(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    private static Map<String, Allergen> namesOfCatalog() {
        Map<String, Allergen> names =
                Arrays.stream(Allergen.values()).collect(Collectors.toMap(a -> key(a.getName()), a -> a));
        names.put(key("Cá biển"), Allergen.FISH);
        names.put(key("Mè"), Allergen.SESAME);
        names.put(key("Vừng"), Allergen.SESAME);
        return Map.copyOf(names);
    }
}
