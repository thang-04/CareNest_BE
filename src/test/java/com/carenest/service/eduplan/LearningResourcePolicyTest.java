package com.carenest.service.eduplan;

import static org.assertj.core.api.Assertions.*;

import com.carenest.exception.GlobalException;
import com.carenest.utils.LearningResourcePolicy;
import org.junit.jupiter.api.Test;

class LearningResourcePolicyTest {
    @Test
    void safeNameDropsPathAndControlCharacters() {
        assertThat(LearningResourcePolicy.safeName("C:\\tmp\\bai\u0007 1.pdf")).isEqualTo("bai 1.pdf");
        assertThat(LearningResourcePolicy.safeName("../../x/slide.pptx")).isEqualTo("slide.pptx");
        assertThatThrownBy(() -> LearningResourcePolicy.safeName("  ")).isInstanceOf(GlobalException.class);
    }
}
