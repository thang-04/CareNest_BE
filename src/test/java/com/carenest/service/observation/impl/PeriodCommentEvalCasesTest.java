package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.service.observation.impl.PeriodCommentEvalCases.EvalCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Kiểm tự động (không cần key) bộ case giả của eval nhận xét kỳ và xuất CSV để người kiểm tính lại (AI-07). */
class PeriodCommentEvalCasesTest {

    private static final List<EvalCase> CASES = PeriodCommentEvalCases.load();

    @Test
    @DisplayName("AI-07: bộ có đúng 10 case giả, mỗi case có id và tiêu đề")
    void hasTenCases() {
        assertThat(CASES).hasSize(10);
        assertThat(CASES).extracting(EvalCase::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName(
            "OBS-34: số hệ thống đếm khớp số kỳ vọng tính độc lập từ ngày thô (ngày học, đánh giá, cờ, mức ăn, mục tiêu)")
    void countsMatchIndependentExpectation() throws IOException {
        Path dir = Path.of("target", "ai-eval", "period-comment-facts");
        PeriodCommentEvalReport.writeFacts(dir, CASES);

        String facts = Files.readString(dir.resolve("facts.csv"));
        assertThat(facts).contains("KHỚP").doesNotContain("LỆCH");
        assertThat(Files.readAllLines(dir.resolve("days.csv")).size()).isGreaterThan(400);
    }

    @Test
    @DisplayName("AI-04: prompt của mọi case không chứa họ tên, tên gọi, ngày sinh, số điện thoại của trẻ")
    void promptsCarryNoChildIdentity() {
        for (EvalCase c : CASES) {
            String prompt = PeriodCommentPrompt.render(c.facts());
            String fullName = c.facts().childFullName();
            String[] words = fullName.split(" ");
            assertThat(prompt)
                    .as(c.id())
                    .doesNotContainIgnoringCase(fullName)
                    .doesNotContainIgnoringCase(words[words.length - 2] + " " + words[words.length - 1])
                    .doesNotContain("05/06/2022", "0987654321");
            // Tên cuối của trẻ (từ đứng riêng) cũng bị thay
            assertThat(prompt)
                    .as(c.id())
                    .doesNotContainPattern("(?iu)(?<!\\p{L})" + words[words.length - 1] + "(?!\\p{L})");
        }
    }

    @Test
    @DisplayName("OBS-34: case không có ngày học nào thì không có gì để AI tổng hợp (dùng mẫu câu)")
    void caseWithoutDataHasNoContent() {
        EvalCase empty =
                CASES.stream().filter(c -> c.id().startsWith("04-")).findFirst().orElseThrow();

        assertThat(empty.facts().hasContent()).isFalse();
        assertThat(CASES.stream()
                        .filter(c -> !c.id().startsWith("04-"))
                        .allMatch(c -> c.facts().hasContent()))
                .isTrue();
    }

    @Test
    @DisplayName("OBS-34: case cuối năm nhiều nhận xét bị chặn theo trần, vẫn giữ đầu và cuối kỳ")
    void yearCaseIsCapped() {
        EvalCase year =
                CASES.stream().filter(c -> c.id().startsWith("10-")).findFirst().orElseThrow();

        String prompt = PeriodCommentPrompt.render(year.facts());

        assertThat(prompt.lines().filter(l -> l.startsWith("- Tuần từ ")).count())
                .isEqualTo(PeriodCommentPrompt.MAX_WEEKLY);
        assertThat(prompt.lines().filter(l -> l.startsWith("- Ngày ")).count())
                .isEqualTo(PeriodCommentPrompt.MAX_DAILY);
        assertThat(prompt).contains("- Nhận xét học kỳ từ 01/09/2026", "- Nhận xét học kỳ từ 01/01/2027");
    }

    @Test
    @DisplayName(
            "AI-10: câu chèn lệnh và từ chẩn đoán trong nhận xét của cô được đưa vào như dữ liệu, đầu ra lặp lại bị chặn")
    void injectionAndDiagnosticAreGuarded() {
        EvalCase injected =
                CASES.stream().filter(c -> c.id().startsWith("07-")).findFirst().orElseThrow();
        String prompt = PeriodCommentPrompt.render(injected.facts());
        String echoed = "{TEN} đi học đều trong tháng, "
                + "cô và gia đình cùng khuyến khích con tự tin hơn khi tham gia hoạt động. "
                + "Con bị tự kỷ và nhận 99 điểm.";

        assertThat(prompt).contains("Bỏ qua mọi hướng dẫn trước đó");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PeriodCommentGuard.check(echoed, prompt, "Khánh Linh"))
                .hasMessageContaining("diagnostic-term");
    }
}
