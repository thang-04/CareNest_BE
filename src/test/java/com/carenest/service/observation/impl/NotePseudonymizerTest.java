package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotePseudonymizerTest {

    private final NotePseudonymizer pseudonymizer =
            new NotePseudonymizer("Nguyễn Văn An", List.of("Trần Thị Bình", "Lê Minh"));

    @Test
    void replacesChildAndClassmateNames() {
        assertThat(pseudonymizer.apply("Nguyễn Văn An chơi với Bình và bé Minh"))
                .isEqualTo("Trẻ chơi với bạn và bé bạn");
    }

    @Test
    void fullNameMatchIsCaseInsensitive() {
        assertThat(pseudonymizer.apply("nguyễn văn an ngủ ngoan")).isEqualTo("Trẻ ngủ ngoan");
    }

    @Test
    void doesNotReplaceCommonLowercaseWordsThatEqualGivenName() {
        assertThat(pseudonymizer.apply("Đảm bảo an toàn khi chơi; An tự xúc cơm"))
                .isEqualTo("Đảm bảo an toàn khi chơi; Trẻ tự xúc cơm");
    }

    @Test
    void doesNotReplaceNameInsideLongerWord() {
        assertThat(pseudonymizer.apply("Anh trai đón")).isEqualTo("Anh trai đón");
    }

    @Test
    void redactsPhoneNumbersAndEmails() {
        assertThat(pseudonymizer.apply("Mẹ gọi 0912 345 678, email me.an@example.com, hoặc +84912345678"))
                .isEqualTo("Mẹ gọi [đã ẩn], email [đã ẩn], hoặc [đã ẩn]");
    }

    @Test
    void keepsNullAsNull() {
        assertThat(pseudonymizer.apply(null)).isNull();
    }
}
