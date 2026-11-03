package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class GoalFileGridHardeningTest {
    @Test
    void malformedCellsAndTablesFailPromptly() {
        for (String html : new String[] {
            "<table><tr>" + "<td>missing".repeat(2048) + "</tr></table>",
            "<table>".repeat(4096) + "text",
            "<table><tr><td>" + "<".repeat(100000) + "</tr></table>"
        }) {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(2),
                    () -> assertThatThrownBy(() -> GoalFileGrid.read("bad.doc", html.getBytes(StandardCharsets.UTF_8)))
                            .isInstanceOf(GlobalException.class));
        }
    }

    @Test
    void validHtmlPreservesMergedCellsAndTextLines() {
        var tables = GoalFileGrid.read(
                "valid.doc",
                ("<table><tr><th rowspan='2'>Mục tiêu</th><td>A<br>B</td></tr>" + "<tr><td>C</td></tr></table>")
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(tables.getFirst().at(0, 1).text()).isEqualTo("A\nB");
        assertThat(tables.getFirst().at(1, 0).continued()).isTrue();
    }

    @Test
    void oversizedUnusedEntryIsRejectedEvenAfterRequestedEntry() throws Exception {
        assertTooLarge(archive(1, 9 * 1024 * 1024));
    }

    @Test
    void aggregateExpansionAndEntryCountAreBounded() throws Exception {
        assertTooLarge(archive(5, 7 * 1024 * 1024));
        assertTooLarge(archive(257, 1));
    }

    @Test
    void ordinaryArchiveWithUnusedEntryStillWorks() throws Exception {
        assertThat(GoalFileGrid.read("valid.docx", archive(1, 1024))).isEmpty();
    }

    private void assertTooLarge(byte[] bytes) {
        assertThatThrownBy(() -> GoalFileGrid.read("test.docx", bytes))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.GOAL_FILE_TOO_LARGE));
    }

    private byte[] archive(int entries, int size) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document xmlns:w='urn:word'><w:body/></w:document>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            byte[] padding = new byte[size];
            for (int i = 0; i < entries; i++) {
                zip.putNextEntry(new ZipEntry("unused/entry" + i));
                zip.write(padding);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
