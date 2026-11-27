package com.carenest.service.eduplan.impl.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChildNameMaskTest {

    private final List<String> names = List.of("Nguyễn Gia An", "Trần Thị Bảo Ngọc");

    @Test
    void masksFullNameShortNameAndCapitalisedGivenName() {
        assertThat(ChildNameMask.mask("nguyễn gia an còn nhút nhát, Bảo Ngọc ăn chậm, Ngọc và An thích vẽ", names))
                .isEqualTo("trẻ còn nhút nhát, trẻ ăn chậm, trẻ và trẻ thích vẽ");
    }

    @Test
    void keepsOrdinaryLowercaseWordsAndPartialWords() {
        assertThat(ChildNameMask.mask("Cả lớp cần an toàn, nhiều trẻ thích Anh ngữ", names))
                .isEqualTo("Cả lớp cần an toàn, nhiều trẻ thích Anh ngữ");
        assertThat(ChildNameMask.mask("  ", names)).isEmpty();
    }
}
