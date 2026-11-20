package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChildTextScrubberTest {

    private static final LocalDate DOB = PeriodCommentTestFacts.DOB;
    private static final String NAME = PeriodCommentTestFacts.FULL_NAME;

    @Test
    @DisplayName("AI-04: thay họ tên, tên gọi và tên cuối của chính trẻ, không phân biệt hoa thường và dấu")
    void replacesChildNameVariants() {
        String text = ChildTextScrubber.scrub(
                "Nguyễn Minh Khôi hôm nay giúp bạn. Bé minh khôi cười nhiều, khoi rất ngoan.", NAME, DOB);

        assertThat(text).doesNotContainIgnoringCase("khôi").doesNotContainIgnoringCase("khoi");
        assertThat(text).doesNotContain("Nguyễn");
        assertThat(text).contains("{TEN} hôm nay giúp bạn", "Bé {TEN} cười nhiều", "{TEN} rất ngoan");
    }

    @Test
    @DisplayName("AI-04: chỉ thay từ đứng riêng, không cắt từ chứa tên; họ và tên đệm đứng riêng giữ nguyên")
    void replacesWholeWordsOnly() {
        String text = ChildTextScrubber.scrub(
                "Bạn An ngồi cạnh, các bạn khác chăm chú; cô Lan đọc truyện.", "Trần Thị An", DOB);

        assertThat(text).isEqualTo("Bạn {TEN} ngồi cạnh, các bạn khác chăm chú; cô Lan đọc truyện.");
        // Tên gọi trùng từ thường thì bị thay luôn: ưu tiên không lộ tên hơn giữ nguyên câu chữ
        assertThat(ChildTextScrubber.scrub("Cô đọc truyện, Minh chăm chú; khôi phục lại đồ chơi.", NAME, DOB))
                .contains("Minh chăm chú")
                .doesNotContain("khôi");
    }

    @Test
    @DisplayName("AI-04: bỏ ngày sinh (nhiều dạng) và số điện thoại")
    void removesBirthDateAndPhone() {
        String text = ChildTextScrubber.scrub(
                "Sinh nhật 14/03/2022, sinh ngày 14-3-2022, 2022-03-14, gọi 0912 345 678 hoặc +84912345678 nhé.",
                NAME,
                DOB);

        assertThat(text).doesNotContain("2022", "14/03", "0912", "84912345678");
        assertThat(text).contains("Sinh nhật", "gọi", "nhé.");
    }

    @Test
    @DisplayName("AI-04: ngày khác ngày sinh giữ nguyên")
    void keepsOtherDates() {
        String text = ChildTextScrubber.scrub("Ngày 15/09 con tham gia múa hát.", NAME, DOB);

        assertThat(text).isEqualTo("Ngày 15/09 con tham gia múa hát.");
    }

    @Test
    @DisplayName("AI-04: văn bản rỗng hoặc không có tên trả về chuỗi rỗng / giữ nguyên")
    void handlesEmptyInput() {
        assertThat(ChildTextScrubber.scrub(null, NAME, DOB)).isEmpty();
        assertThat(ChildTextScrubber.scrub("  ", NAME, DOB)).isEmpty();
        assertThat(ChildTextScrubber.scrub("Con ăn hết suất.", null, null)).isEqualTo("Con ăn hết suất.");
    }

    @Test
    @DisplayName("OBS-34: tên gọi là hai chữ cuối của họ tên")
    void givenNameIsLastTwoWords() {
        assertThat(ChildTextScrubber.givenName(NAME)).isEqualTo("Minh Khôi");
        assertThat(ChildTextScrubber.givenName("An")).isEqualTo("An");
        assertThat(ChildTextScrubber.givenName(null)).isEmpty();
    }
}
