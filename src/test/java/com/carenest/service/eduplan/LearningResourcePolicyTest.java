package com.carenest.service.eduplan;

import static org.assertj.core.api.Assertions.*;

import com.carenest.utils.LearningResourcePolicy;
import org.junit.jupiter.api.Test;

class LearningResourcePolicyTest {
    @Test
    void pastedWordWhitespaceMatchesJsActivityParser() {
        assertThat(LearningResourcePolicy.hasActivity("- HĐ học:\u00a0", 0)).isFalse();
        assertThat(LearningResourcePolicy.hasActivity("\u00a0+ A:", 0)).isTrue();
        assertThat(LearningResourcePolicy.hasActivity("\ufeff+ A:", 0)).isTrue();
    }

    @Test
    void signatureNormalizesLineEndingsButDetectsReordering() {
        assertThat(LearningResourcePolicy.signature("- A\r\n+ B"))
                .isEqualTo(LearningResourcePolicy.signature("- A\n+ B"));
        assertThat(LearningResourcePolicy.signature("A\nB")).isNotEqualTo(LearningResourcePolicy.signature("B\nA"));
        assertThat(LearningResourcePolicy.hasActivity("- HĐ học:\n+ A\n+ B\n", 1))
                .isTrue();
        assertThat(LearningResourcePolicy.hasActivity("- HĐ học:\n+ A\n+ B\n", 2))
                .isFalse();
    }
}
