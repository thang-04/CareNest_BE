package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiagnosticTermsTest {

    @Test
    @DisplayName("OBS-17: bắt thuật ngữ chẩn đoán dù viết hoa, đổi dấu, bỏ dấu hoặc gõ rời dấu")
    void catchesVariants() {
        assertThat(DiagnosticTerms.containsAny("Bé nghi TỰ KỶ")).isTrue();
        assertThat(DiagnosticTerms.containsAny("bé tự kĩ")).isTrue();
        assertThat(DiagnosticTerms.containsAny("be tu ky")).isTrue();
        assertThat(DiagnosticTerms.containsAny("tự ky\u0309")).isTrue();
        assertThat(DiagnosticTerms.containsAny("có dấu hiệu ADHD")).isTrue();
        assertThat(DiagnosticTerms.containsAny("autism")).isTrue();
        assertThat(DiagnosticTerms.containsAny("bé hay tăng động")).isTrue();
    }

    @Test
    @DisplayName("OBS-17: không chặn nhầm từ thường dùng chỉ khác dấu với thuật ngữ chẩn đoán")
    void doesNotBlockLookAlikeWords() {
        assertThat(DiagnosticTerms.containsAny("Bé thiếu năng lượng buổi sáng")).isFalse();
        assertThat(DiagnosticTerms.containsAny("Bé hỏi bài nào cô cũng trả lời"))
                .isFalse();
        assertThat(DiagnosticTerms.containsAny("Bé có dấu hiệu thiểu năng")).isTrue();
        assertThat(DiagnosticTerms.containsAny("Bé bị bại não")).isTrue();
        assertThat(DiagnosticTerms.containsAny("be bi bai nao")).isTrue();
        assertThat(DiagnosticTerms.containsAny("Bé có dấu hiệu tâm thần")).isTrue();
        assertThat(DiagnosticTerms.containsAny("hay tang dong")).isTrue();
    }

    @Test
    @DisplayName("OBS-17: câu mô tả hành vi quan sát được không bị chặn")
    void allowsObservedBehaviour() {
        assertThat(DiagnosticTerms.containsAny("Bé ăn hết suất, ngủ ngon, chơi vui cùng bạn"))
                .isFalse();
        assertThat(DiagnosticTerms.containsAny("Hôm nay bé khóc khi mẹ đi, dỗ một lúc thì nín"))
                .isFalse();
        assertThat(DiagnosticTerms.containsAny("")).isFalse();
        assertThat(DiagnosticTerms.containsAny(null)).isFalse();
    }
}
