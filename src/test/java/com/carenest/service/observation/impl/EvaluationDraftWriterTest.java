package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.EvaluationPeriodType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EvaluationDraftWriterTest {

    @Test
    void parentDraftIsShortNaturalAndContainsNoInternalGoalCodes() {
        String draft = EvaluationDraftWriter.write(
                EvaluationPeriodType.MONTH,
                PeriodCommentTestFacts.FULL_NAME,
                LocalDate.of(2026, 9, 1),
                18,
                4,
                PeriodCommentTestFacts.structured(18),
                0);

        assertThat(draft)
                .hasSizeLessThan(600)
                .contains("Minh Khôi", "giúp đỡ bạn", "gia đình")
                .doesNotContain(
                        "MT3",
                        "MT7",
                        "nhảy bật xa",
                        "kể chuyện theo tranh",
                        "mục tiêu",
                        "chủ đề",
                        "cờ bé ngoan",
                        "Kiến thức, kỹ năng:",
                        "Sức khỏe:");
        assertThat(draft.split("\\n\\n")).hasSize(2);
    }

    @Test
    void noObservationsDoesNotInventAchievements() {
        String draft = EvaluationDraftWriter.write(
                EvaluationPeriodType.MONTH,
                PeriodCommentTestFacts.FULL_NAME,
                LocalDate.of(2026, 9, 1),
                0,
                0,
                PeriodCommentTestFacts.structured(0),
                0);

        assertThat(draft).contains("chưa có đủ ghi nhận", "gia đình").doesNotContain("đã làm tốt", "đạt", "MT");
    }
}
