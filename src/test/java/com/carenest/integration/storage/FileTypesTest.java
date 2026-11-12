package com.carenest.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileTypesTest {
    @Test
    void recognisesByContentNotByExtension() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(FileTypes.matches("png", png)).isTrue();
        assertThat(FileTypes.matches("jpg", png)).isFalse();
        assertThat(FileTypes.matches("jpg", new byte[] {'M', 'Z', 0, 0})).isFalse();
        assertThat(FileTypes.matches("pdf", "%PDF-1.7".getBytes())).isTrue();
        assertThat(FileTypes.matches("mp4", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'}))
                .isTrue();
        assertThat(FileTypes.matches("docx", new byte[] {'P', 'K', 3, 4})).isTrue();
        assertThat(FileTypes.matches("txt", "<html>".getBytes())).isFalse();
        assertThat(FileTypes.matches("exe", new byte[] {'M', 'Z'})).isFalse();
    }

    @Test
    void kindAndExtension() {
        assertThat(FileTypes.kind("webm")).isEqualTo("video");
        assertThat(FileTypes.kind("jpeg")).isEqualTo("image");
        assertThat(FileTypes.kind("pptx")).isEqualTo("other");
        assertThat(FileTypes.extension("Bài 1.PDF")).isEqualTo("pdf");
        assertThat(FileTypes.extension("no-ext")).isNull();
    }
}
