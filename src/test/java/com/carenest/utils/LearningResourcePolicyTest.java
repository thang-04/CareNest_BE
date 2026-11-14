package com.carenest.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.exception.GlobalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LearningResourcePolicyTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtube.com/watch?list=PL1&v=dQw4w9WgXcQ&t=10",
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtu.be/dQw4w9WgXcQ?si=abc",
                "https://www.youtube.com/shorts/dQw4w9WgXcQ",
                "https://www.youtube.com/embed/dQw4w9WgXcQ",
                "https://www.youtube.com/live/dQw4w9WgXcQ?feature=share"
            })
    void youtubeFormsNormalizeToWatchUrl(String raw) {
        // EDU-83: mọi dạng link YouTube về một url chuẩn để dán lại thì nhận ra trùng
        assertThat(LearningResourcePolicy.normalizeLink(raw)).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    void googleDriveAndDocsKeptAsIs() {
        String drive = "https://drive.google.com/file/d/abc/view";
        assertThat(LearningResourcePolicy.normalizeLink(" " + drive + " ")).isEqualTo(drive);
        assertThat(LearningResourcePolicy.normalizeLink("https://docs.google.com/presentation/d/x/edit"))
                .startsWith("https://docs.google.com/");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://example.com/watch?v=dQw4w9WgXcQ",
                "https://www.youtube.com/playlist?list=PL1",
                "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
                "https://user@drive.google.com/file",
                "javascript:alert(1)",
                ""
            })
    void otherLinksRejected(String raw) {
        assertThatThrownBy(() -> LearningResourcePolicy.normalizeLink(raw))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_LINK_INVALID);
    }
}
