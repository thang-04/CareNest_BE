package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.Participation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateWeeklyCommentDraftGeneratorTest {

    private final TemplateWeeklyCommentDraftGenerator generator = new TemplateWeeklyCommentDraftGenerator();

    @Test
    @DisplayName("OBS-29: nháp 2 đoạn văn xuôi từ số liệu tuần, gọi trẻ là con, không mã MT")
    void writesTwoProseParagraphs() {
        String text = generator.write(new WeeklyCommentDraftGenerator.WeekFacts(
                5,
                5,
                Map.of(Emotion.HAPPY, 4),
                Map.of(Participation.TICH_CUC, 3),
                Map.of(NapLevel.NGU_TOT, 5),
                4,
                5,
                List.of("MT3 – Trẻ nhảy bật xa", "MT1 – Trẻ nhận biết màu", "MT2 – Trẻ đếm đến 5"),
                List.of("MT7 – Trẻ kể chuyện theo tranh"),
                4,
                true));

        String[] paragraphs = text.split("\n\n");
        assertThat(paragraphs).hasSize(2);
        assertThat(paragraphs[0])
                .startsWith("Tuần này con đi học đều cả 5 ngày, vào lớp vui vẻ.")
                .contains("tham gia hoạt động tích cực", "ăn hết suất trưa 4/5 ngày", "ngủ trưa ngon")
                .contains("\"trẻ nhảy bật xa\", \"trẻ nhận biết màu\"")
                .doesNotContain("đếm đến 5")
                .contains("4 cờ bé ngoan và nhận phiếu Bé ngoan của tuần");
        assertThat(paragraphs[1]).startsWith("Con còn lúng túng ở \"trẻ kể chuyện theo tranh\"");
        assertThat(text).doesNotContain("MT");
    }

    @Test
    @DisplayName("OBS-29: tuần ít dữ liệu vẫn có câu đi học và gợi ý gia đình, không bịa thêm")
    void sparseWeek() {
        String text = generator.write(new WeeklyCommentDraftGenerator.WeekFacts(
                2, 5, Map.of(), Map.of(), Map.of(), 0, 0, List.of("MT (mã 9)"), List.of(), 0, false));

        assertThat(text)
                .startsWith("Tuần này con đi học 2/5 ngày.")
                .doesNotContain("ăn", "cờ", "làm tốt", "lúng túng")
                .endsWith("để con thêm tự tin nhé.");
    }

    @Test
    @DisplayName("OBS-29: nghỉ cả tuần thì nháp hỏi thăm và gợi ý gia đình, không nhận xét hoạt động")
    void absentAllWeek() {
        String text = generator.write(new WeeklyCommentDraftGenerator.WeekFacts(
                0, 5, Map.of(), Map.of(), Map.of(), 0, 0, List.of(), List.of("MT1 – Kể chuyện"), 0, false));

        assertThat(text.split("\n\n")).hasSize(2);
        assertThat(text)
                .startsWith("Tuần này con nghỉ học cả tuần")
                .contains("mong con sớm trở lại lớp", "Bố mẹ có thể")
                .doesNotContain("lúng túng", "kể chuyện", "cờ");
    }
}
