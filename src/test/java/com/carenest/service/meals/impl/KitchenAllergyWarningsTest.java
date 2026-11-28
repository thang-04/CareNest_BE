package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.meals.KitchenDayResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.Allergen;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import com.carenest.utils.enums.SchoolYearStatus;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Cảnh báo dị ứng cho bếp: đối chiếu tên đã khai và đếm trẻ theo lớp (NUT-23, AUTH-05). */
class KitchenAllergyWarningsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);
    private static final long CAMPUS = 1L;
    private static final long KINDER_GROUP = 3L;
    private static final long NURSERY_GROUP = 2L;

    private final ClassroomService classroomService = mock(ClassroomService.class);
    private final EnrollmentService enrollmentService = mock(EnrollmentService.class);
    private final ChildService childService = mock(ChildService.class);
    private final MealDemandCalculator demandCalculator = mock(MealDemandCalculator.class);

    private KitchenAllergyWarnings warnings;

    @BeforeEach
    void setUp() {
        warnings = new KitchenAllergyWarnings(
                classroomService, enrollmentService, childService, demandCalculator, new NutritionCatalogMapper());
        lenient()
                .when(demandCalculator.blockOfAgeGroup())
                .thenReturn(Map.of(KINDER_GROUP, MenuBlock.KINDERGARTEN, NURSERY_GROUP, MenuBlock.NURSERY));
    }

    @ParameterizedTest
    @CsvSource({
        "Tôm, SHRIMP",
        "'  tôm ', SHRIMP",
        "SỮA   BÒ, COW_MILK",
        "Cá biển, FISH",
        "Mè, SESAME",
        "Vừng, SESAME",
        "Mè (vừng), SESAME",
        "Đậu phộng, PEANUT"
    })
    @DisplayName("NUT-23: tên khai khác hoa thường, khoảng trắng và bí danh đã chốt ra đúng chất")
    void matchesDeclaredName(String declared, Allergen expected) {
        assertThat(KitchenAllergyWarnings.match(declared)).contains(expected);
    }

    @Test
    @DisplayName("NUT-23: tên gõ dạng dấu tách rời vẫn khớp")
    void matchesDecomposedUnicode() {
        String decomposed = Normalizer.normalize("Tôm", Normalizer.Form.NFD);

        assertThat(KitchenAllergyWarnings.match(decomposed)).contains(Allergen.SHRIMP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mực", "Hải sản", "Sữa", "Tom", "Dị ứng tôm"})
    @DisplayName("NUT-23: tên ngoài danh mục hoặc mất dấu không tự khớp")
    void leavesUnknownNameUnmatched(String declared) {
        assertThat(KitchenAllergyWarnings.match(declared)).isEmpty();
    }

    @Test
    @DisplayName("NUT-23, NUT-13, NUT-17: đếm mỗi trẻ một lần cho một chất; chỉ lớp đúng khối và ăn bữa đó")
    void countsChildrenPerClassOfBlockAndSession() {
        classes(
                classroom(10L, "Mầm 1", CAMPUS, KINDER_GROUP, MealSession.LUNCH),
                classroom(11L, "Mầm chiều", CAMPUS, KINDER_GROUP, MealSession.AFTERNOON),
                classroom(12L, "Nhà trẻ", CAMPUS, NURSERY_GROUP, MealSession.LUNCH));
        children(
                Map.of(10L, Set.of(1L, 2L), 11L, Set.of(3L), 12L, Set.of(4L)),
                Map.of(
                        1L, List.of("Mè", "Vừng", "Tôm"),
                        2L, List.of("Mè"),
                        3L, List.of("Mè"),
                        4L, List.of("Mè")));

        List<KitchenDayResponse.DishAllergyWarning> result = warnings.load(CAMPUS, DATE)
                .warnings(MealSession.LUNCH, MenuBlock.KINDERGARTEN, List.of(Allergen.SESAME, Allergen.EGG));

        assertThat(result).singleElement().satisfies(w -> {
            assertThat(w.allergen().code()).isEqualTo("SESAME");
            assertThat(w.byClass()).containsExactly(new KitchenDayResponse.AllergyClassCount(10L, "Mầm 1", 2));
        });
    }

    @Test
    @DisplayName("NUT-23: tên ngoài danh mục gộp theo chữ không phân biệt hoa thường, đếm theo lớp")
    void groupsUnmatchedNames() {
        classes(classroom(10L, "Mầm 1", CAMPUS, KINDER_GROUP, MealSession.LUNCH));
        children(Map.of(10L, Set.of(1L, 2L)), Map.of(1L, List.of("Mực", "mực ", "Tôm"), 2L, List.of("MỰC", "Hải sản")));

        List<KitchenDayResponse.UnmatchedAllergy> result =
                warnings.load(CAMPUS, DATE).unmatched();

        assertThat(result)
                .containsExactly(
                        new KitchenDayResponse.UnmatchedAllergy(
                                "Hải sản", List.of(new KitchenDayResponse.AllergyClassCount(10L, "Mầm 1", 1))),
                        new KitchenDayResponse.UnmatchedAllergy(
                                "Mực", List.of(new KitchenDayResponse.AllergyClassCount(10L, "Mầm 1", 2))));
    }

    @Test
    @DisplayName("AUTH-05: bỏ lớp của điểm trường khác và lớp của năm không chứa ngày xem")
    void ignoresOtherCampusAndOtherDates() {
        classes(
                classroom(10L, "Mầm 1", 99L, KINDER_GROUP, MealSession.LUNCH),
                classroom(11L, "Mầm 2", CAMPUS, KINDER_GROUP, MealSession.LUNCH));
        children(Map.of(11L, Set.of(1L)), Map.of(1L, List.of("Mực")));

        assertThat(warnings.load(CAMPUS, LocalDate.of(2027, 8, 1)).unmatched()).isEmpty();
        assertThat(warnings.load(CAMPUS, DATE).unmatched())
                .singleElement()
                .satisfies(u -> assertThat(u.byClass())
                        .extracting(KitchenDayResponse.AllergyClassCount::classroomId)
                        .containsExactly(11L));
    }

    private void classes(ClassroomInfoResponse... classes) {
        when(classroomService.findActiveYearClassrooms()).thenReturn(List.of(classes));
    }

    private void children(Map<Long, Set<Long>> byClass, Map<Long, List<String>> allergies) {
        lenient()
                .when(enrollmentService.findChildIdsInClassrooms(any(), eq(DATE)))
                .thenReturn(byClass);
        lenient().when(childService.findChildIdsWithoutMeals(any())).thenReturn(Set.of());
        lenient().when(childService.findAllergyNames(any())).thenReturn(allergies);
    }

    private static ClassroomInfoResponse classroom(
            long id, String name, long campusId, long ageGroupId, MealSession session) {
        return new ClassroomInfoResponse(
                id,
                name,
                campusId,
                1L,
                SchoolYearStatus.ACTIVE,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 5, 31),
                ageGroupId,
                List.of(session),
                null,
                ClassroomStatus.ACTIVE);
    }
}
