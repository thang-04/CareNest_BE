package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.service.observation.impl.PeriodCommentFacts.Note;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeriodCommentPromptTest {

    @Test
    @DisplayName("AI-04: prompt không chứa họ tên, tên gọi, ngày sinh, số điện thoại; nhận xét của cô giữ nội dung")
    void promptHasNoChildIdentity() {
        PeriodCommentFacts facts = PeriodCommentTestFacts.month(List.of(
                new Note("Ngày 15/09", "Khôi giúp bạn xếp đồ chơi. Sinh nhật 14/03/2022, gọi 0912 345 678."),
                new Note("Ngày 16/09", "Nguyễn Minh Khôi ngại phát biểu.")));

        String prompt = PeriodCommentPrompt.render(facts);

        assertThat(prompt)
                .doesNotContainIgnoringCase("khôi")
                .doesNotContainIgnoringCase("nguyễn")
                .doesNotContain("2022", "0912");
        assertThat(prompt).contains("- Ngày 15/09: {TEN} giúp bạn xếp đồ chơi.", "- Ngày 16/09: {TEN} ngại phát biểu.");
    }

    @Test
    @DisplayName("OBS-34: số liệu kỳ do code đếm, có số ngày đi học, cờ, mức ăn ngủ và mục tiêu")
    void promptCarriesCountedFacts() {
        String prompt = PeriodCommentPrompt.render(PeriodCommentTestFacts.month(List.of()));

        assertThat(prompt)
                .contains("Kỳ nhận xét: tháng 09/2026.")
                .contains("- Đi học 18 ngày.", "- Cô đánh giá hằng ngày 18 ngày.", "- Cờ bé ngoan: 4.")
                .contains("bữa trưa (ăn hết 15, ăn ít 3, không ăn 0)")
                .contains("ngủ tốt 14; ngủ ít 4")
                .contains("Mục tiêu bé đạt: trẻ nhảy bật xa.")
                .contains("Mục tiêu cần hỗ trợ thêm: trẻ kể chuyện theo tranh.");
    }

    @Test
    @DisplayName("OBS-34: kỳ chưa có đánh giá nào chỉ ghi một dòng, không có số liệu ăn ngủ")
    void emptyPeriodHasSingleLine() {
        String prompt = PeriodCommentPrompt.render(PeriodCommentTestFacts.empty());

        assertThat(prompt).contains("chưa có đánh giá hằng ngày nào").doesNotContain("Ăn uống", "Cảm xúc");
    }

    @Test
    @DisplayName("OBS-34: nhiều nhận xét thì chọn đều theo thời gian, có trần và cắt độ dài")
    void capsAndSamplesNotes() {
        List<Note> daily = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            daily.add(new Note(String.format("Ngày %02d/09", i), "Con " + "rất chăm ".repeat(60) + i));
        }

        String prompt = PeriodCommentPrompt.render(PeriodCommentTestFacts.month(daily));

        List<String> lines = prompt.lines().filter(l -> l.startsWith("- Ngày ")).toList();
        assertThat(lines).hasSize(PeriodCommentPrompt.MAX_DAILY);
        assertThat(prompt).contains("- Ngày 01/09:", "- Ngày 30/09:");
        assertThat(lines).allMatch(l -> l.length() < 280);
    }

    @Test
    @DisplayName("OBS-34: chọn đều giữ đầu và cuối, không đổi khi chưa vượt trần")
    void sampleKeepsEnds() {
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertThat(PeriodCommentPrompt.sample(items, 20)).isSameAs(items);
        assertThat(PeriodCommentPrompt.sample(items, 4)).containsExactly(1, 4, 7, 10);
    }
}
