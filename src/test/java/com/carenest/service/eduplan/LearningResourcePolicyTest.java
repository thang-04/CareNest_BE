package com.carenest.service.eduplan;

import static org.assertj.core.api.Assertions.*;

import com.carenest.exception.GlobalException;
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

    @Test
    void fileMustMatchTypeAndLimits() {
        assertThat(LearningResourcePolicy.inspect("anh.png", new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, 8))
                .isEqualTo("image/png");
        assertThatThrownBy(() -> LearningResourcePolicy.inspect("anh.png", "%PDF-1.7".getBytes(), 8))
                .isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> LearningResourcePolicy.inspect("x.pdf", "%PDF-".getBytes(), 21L * 1024 * 1024))
                .isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> LearningResourcePolicy.inspect("x.html", "<html>".getBytes(), 6))
                .isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> LearningResourcePolicy.inspect("x.txt", new byte[0], 0))
                .isInstanceOf(GlobalException.class);
    }
}
