package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.eduplan.GoalFilePreviewResponse;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Content;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class YearPlanDomainVariantTest {
    private static final List<String> DOMAINS = List.of("Phát triển tình cảm và kỹ năng xã hội", "Phát triển thẩm mỹ");

    @Test
    @DisplayName("EDU-46: lĩnh vực kĩ/mĩ giữ đủ mục tiêu và dấu tích, trả tên cấu hình kỹ/mỹ")
    void romanBandsAcceptOrthographicVariantsWithoutChangingUserText() {
        var preview = schoolPreview(
                row("", "IV. Giáo dục phát triển tình cảm và kĩ năng xã hội", "", ""),
                row("1", "1. Tập kĩ năng giao tiếp", "Nội dung kĩ năng", "x"),
                row("", "V. Giáo dục phát triển thẩm mĩ", "", ""),
                row("2", "2. Cảm nhận thẩm mĩ", "Nội dung thẩm mĩ", "x"));

        assertThat(preview.domains())
                .extracting(GoalFilePreviewResponse.Domain::name)
                .containsExactlyElementsOf(DOMAINS);
        assertThat(preview.warnings()).isEmpty();
        assertThat(preview.domains().getFirst().rows().getFirst().text()).isEqualTo("Tập kĩ năng giao tiếp");
        assertThat(preview.domains().getFirst().rows().getFirst().contents())
                .containsExactly(new Content("Nội dung kĩ năng", List.of("TMN")));
        assertThat(preview.domains().getLast().rows().getFirst().text()).isEqualTo("Cảm nhận thẩm mĩ");
        assertThat(preview.domains().getLast().rows().getFirst().contents())
                .containsExactly(new Content("Nội dung thẩm mĩ", List.of("TMN")));
    }

    @Test
    @DisplayName("EDU-46: tên lĩnh vực không số La Mã dùng chung cách so kĩ/mĩ")
    void unnumberedBandsAcceptOrthographicVariants() {
        var preview = schoolPreview(
                row("", "Giáo dục phát triển tình cảm và kĩ năng xã hội", "", ""),
                row("1", "Câu mục tiêu thứ nhất", "Nội dung thứ nhất", ""),
                row("", "Phát triển thẩm mĩ", "", ""),
                row("2", "Câu mục tiêu thứ hai", "Nội dung thứ hai", ""));

        assertThat(preview.domains())
                .extracting(GoalFilePreviewResponse.Domain::name)
                .containsExactlyElementsOf(DOMAINS);
        assertThat(preview.warnings()).isEmpty();
        assertThat(preview.domains())
                .allSatisfy(domain -> assertThat(domain.rows()).hasSize(1));
    }

    @Test
    @DisplayName("EDU-46: CSV giữ so lĩnh vực rút gọn và cảnh báo tên lạ")
    void csvPartialNamesRemainSupportedAndUnknownNamesStillWarn() {
        var table = new GoalFileGrid.Table(List.of(
                row("Lĩnh vực", "Loại dòng", "Mục tiêu", "Nội dung", "Chủ đề"),
                row("Thẩm mĩ", "Mục tiêu", "1. Câu mục tiêu", "Nội dung thẩm mĩ", "TMN"),
                row("Tên lĩnh vực không có", "Mục tiêu", "Không được thêm", "Không được thêm", "TMN")));
        var preview = GoalFileTable.interpret(List.of(table), DOMAINS);

        assertThat(preview.domains())
                .extracting(GoalFilePreviewResponse.Domain::name)
                .containsExactly(DOMAINS.getLast());
        assertThat(preview.domains().getFirst().rows()).hasSize(1);
        assertThat(preview.domains().getFirst().rows().getFirst().contents())
                .containsExactly(new Content("Nội dung thẩm mĩ", List.of("TMN")));
        assertThat(preview.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.row()).isEqualTo(3);
            assertThat(warning.message()).contains("Không nhận ra lĩnh vực");
        });
    }

    @Test
    @DisplayName("EDU-46: tên cấu hình dùng kĩ/mĩ vẫn được trả nguyên dạng")
    void canonicalOutputFollowsConfigurationInEitherSpelling() {
        var names = List.of("Phát triển tình cảm và kĩ năng xã hội", "Phát triển thẩm mĩ");
        var table = new GoalFileGrid.Table(List.of(
                row("STT", "Mục tiêu", "Nội dung", "TMN"),
                row("", "V. Phát triển thẩm mỹ", "", ""),
                row("1", "Câu mục tiêu", "Nội dung", "")));
        var preview = GoalFileTable.interpret(List.of(table), names);

        assertThat(preview.domains())
                .extracting(GoalFilePreviewResponse.Domain::name)
                .containsExactly(names.getLast());
        assertThat(preview.warnings()).isEmpty();
    }

    @SafeVarargs
    private static GoalFilePreviewResponse schoolPreview(List<GoalFileGrid.Cell>... rows) {
        var source = new java.util.ArrayList<List<GoalFileGrid.Cell>>();
        source.add(row("STT", "Mục tiêu", "Nội dung", "TMN"));
        source.addAll(List.of(rows));
        return GoalFileTable.interpret(List.of(new GoalFileGrid.Table(source)), DOMAINS);
    }

    private static List<GoalFileGrid.Cell> row(String... values) {
        return java.util.Arrays.stream(values)
                .map(value -> new GoalFileGrid.Cell(value, false))
                .toList();
    }
}
