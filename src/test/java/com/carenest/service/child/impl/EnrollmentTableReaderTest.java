package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class EnrollmentTableReaderTest {
    @Test
    void csvReadsQuotedCommasNewlinesAndUtf8Bom() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "children.csv",
                "text/csv",
                "\uFEFFHọ tên,Phụ huynh\r\n\"Trẻ, A\",\"Mẹ\nBố\"".getBytes(StandardCharsets.UTF_8));
        var rows = EnrollmentTableReader.read(file);
        assertThat(rows.get(0)).containsExactly("Họ tên", "Phụ huynh");
        assertThat(rows.get(1)).containsExactly("Trẻ, A", "Mẹ\nBố");
    }

    @Test
    void xlsxReadsSharedAndInlineStringsAndSparseCells() throws Exception {
        var file = xlsx(
                "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"C1\" t=\"inlineStr\"><is><t>Bố</t></is></c></row></sheetData></worksheet>");
        assertThat(EnrollmentTableReader.read(file).getFirst()).containsExactly("Mẹ", "", "Bố");
    }

    @Test
    void xlsxRejectsExternalEntitiesAndOversizedColumns() throws Exception {
        assertThatThrownBy(
                        () -> EnrollmentTableReader.read(
                                xlsx(
                                        "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///never-read'>]><worksheet><row><c r=\"A1\">&e;</c></row></worksheet>")))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> EnrollmentTableReader.read(
                        xlsx("<worksheet><row><c r=\"ZZZ1\"><v>1</v></c></row></worksheet>")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockMultipartFile xlsx(String sheet) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(
                    zip,
                    "xl/workbook.xml",
                    "<workbook xmlns:r=\"urn:relationship\"><sheets><sheet r:id=\"rId1\"/></sheets></workbook>");
            entry(
                    zip,
                    "xl/_rels/workbook.xml.rels",
                    "<Relationships><Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            entry(zip, "xl/sharedStrings.xml", "<sst><si><t>Mẹ</t></si></sst>");
            entry(zip, "xl/worksheets/sheet1.xml", sheet);
        }
        return new MockMultipartFile(
                "file",
                "children.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes.toByteArray());
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
