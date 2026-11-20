package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeriodCommentGuardTest {

    private static final String PROMPT = "Đi học 18 ngày. Cờ bé ngoan: 4. Ngủ tốt 14; ngủ ít 4.";
    private static final String GOOD = "{TEN} đi học đều 18 ngày trong tháng và nhận 4 cờ bé ngoan. "
            + "Con vui vẻ, hay giúp bạn xếp đồ chơi; cô và gia đình cùng khuyến khích con nói to hơn khi kể chuyện.";

    @Test
    @DisplayName("OBS-34: đầu ra hợp lệ được ghép tên gọi thay cho ký hiệu")
    void acceptsValidOutputAndFillsName() {
        String text = PeriodCommentGuard.check(GOOD, PROMPT, "Minh Khôi");

        assertThat(text)
                .startsWith("Minh Khôi đi học đều 18 ngày")
                .doesNotContain("{")
                .doesNotContain("}");
    }

    @Test
    @DisplayName("OBS-34: số không có trong đầu vào bị chặn (AI không tự tính)")
    void rejectsNumberNotInInput() {
        String text = GOOD.replace("18 ngày", "19 ngày");

        assertThatThrownBy(() -> PeriodCommentGuard.check(text, PROMPT, "Khôi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("number-not-in-input");
    }

    @Test
    @DisplayName("OBS-17: từ chẩn đoán bị chặn")
    void rejectsDiagnosticTerm() {
        String text = GOOD + " Con có dấu hiệu tăng động cần theo dõi thêm ở nhà.";

        assertThatThrownBy(() -> PeriodCommentGuard.check(text, PROMPT, "Khôi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diagnostic-term");
    }

    @Test
    @DisplayName("OBS-34: quá ngắn, quá dài, rỗng bị chặn")
    void rejectsLengthViolations() {
        assertThatThrownBy(() -> PeriodCommentGuard.check("  ", PROMPT, "Khôi")).hasMessageContaining("too-short");
        assertThatThrownBy(() -> PeriodCommentGuard.check("Con ngoan.", PROMPT, "Khôi"))
                .hasMessageContaining("too-short");
        assertThatThrownBy(() -> PeriodCommentGuard.check("Con ngoan lắm. ".repeat(150), PROMPT, "Khôi"))
                .hasMessageContaining("too-long");
        assertThatThrownBy(() -> PeriodCommentGuard.check(null, PROMPT, "Khôi")).hasMessageContaining("too-short");
    }

    @Test
    @DisplayName("OBS-34: gạch đầu dòng, tiêu đề, in đậm bị chặn; ký hiệu còn sót bị chặn")
    void rejectsMarkupAndLeftoverPlaceholder() {
        assertThatThrownBy(() -> PeriodCommentGuard.check("- " + GOOD, PROMPT, "Khôi"))
                .hasMessageContaining("markup");
        assertThatThrownBy(() -> PeriodCommentGuard.check("# Nhận xét\n" + GOOD, PROMPT, "Khôi"))
                .hasMessageContaining("markup");
        assertThatThrownBy(() -> PeriodCommentGuard.check(GOOD.replace("đi học", "**đi học**"), PROMPT, "Khôi"))
                .hasMessageContaining("markup");
        assertThatThrownBy(() -> PeriodCommentGuard.check(GOOD + " {TÊN BÉ}", PROMPT, "Khôi"))
                .hasMessageContaining("placeholder");
    }

    @Test
    @DisplayName("OBS-34: tên gọi rỗng thì bỏ ký hiệu, không lỗi")
    void emptyGivenNameDropsPlaceholder() {
        assertThat(PeriodCommentGuard.check(GOOD, PROMPT, null))
                .startsWith("đi học đều 18 ngày")
                .doesNotContain("{");
    }
}
