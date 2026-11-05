package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BranchFileImportTest {
    @Test
    @DisplayName("EDU-47: 35 tên nhánh, ô chủ đề gộp tương đương giữa Word/Excel/HTML/CSV")
    void thirtyFiveBranchesAcrossFormats() throws Exception {
        StringBuilder csv = new StringBuilder("Chủ đề,Chủ đề nhánh\n");
        StringBuilder word = new StringBuilder(
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:tbl><w:tr><w:tc><w:p><w:r><w:t>Chủ đề</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Chủ đề nhánh</w:t></w:r></w:p></w:tc></w:tr>");
        StringBuilder html = new StringBuilder("<table><tr><th>Chủ đề</th><th>Chủ đề nhánh</th></tr>");
        StringBuilder sheet = new StringBuilder(
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>Chủ đề</t></is></c><c r=\"B1\" t=\"inlineStr\"><is><t>Chủ đề nhánh</t></is></c></row>");
        for (int i = 1; i <= 35; i++) {
            String name = "Nhánh " + i + ": Tên mẫu " + i;
            csv.append(i == 1 ? "Chủ đề mẫu" : "").append(',').append(name).append('\n');
            word.append("<w:tr><w:tc><w:tcPr><w:vMerge")
                    .append(i == 1 ? " w:val=\"restart\"" : "")
                    .append("/></w:tcPr><w:p><w:r><w:t>")
                    .append(i == 1 ? "Chủ đề mẫu" : "")
                    .append("</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>")
                    .append(name)
                    .append("</w:t></w:r></w:p></w:tc></w:tr>");
            html.append("<tr>")
                    .append(i == 1 ? "<td rowspan=\"35\">Chủ đề mẫu</td>" : "")
                    .append("<td>")
                    .append(name)
                    .append("</td></tr>");
            sheet.append("<row r=\"").append(i + 1).append("\">");
            if (i == 1) sheet.append("<c r=\"A2\" t=\"inlineStr\"><is><t>Chủ đề mẫu</t></is></c>");
            sheet.append("<c r=\"B")
                    .append(i + 1)
                    .append("\" t=\"inlineStr\"><is><t>")
                    .append(name)
                    .append("</t></is></c></row>");
        }
        word.append("</w:tbl></w:body></w:document>");
        sheet.append("</sheetData><mergeCells><mergeCell ref=\"A2:A36\"/></mergeCells></worksheet>");
        html.append("</table>");
        var expected = csv(csv.toString());
        assertThat(expected.entries()).hasSize(35);
        assertThat(BranchFileTable.interpret(GoalFileGrid.read("b.docx", zip("word/document.xml", word.toString()))))
                .isEqualTo(expected);
        assertThat(BranchFileTable.interpret(
                        GoalFileGrid.read("b.xlsx", zip("xl/worksheets/sheet1.xml", sheet.toString()))))
                .isEqualTo(expected);
        assertThat(BranchFileTable.interpret(
                        GoalFileGrid.read("b.doc", html.toString().getBytes(StandardCharsets.UTF_8))))
                .isEqualTo(expected);
    }

    private static byte[] zip(String path, String xml) throws java.io.IOException {
        var out = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(path));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("EDU-47: đọc tên theo chủ đề, bỏ nhãn nhánh và dòng nghỉ, không dùng ngày/số tuần")
    void readsBranchNamesWithoutFileSchedule() {
        var parsed = csv("Tháng,Chủ đề,Chủ đề nhánh,Số tuần,Ghi chú\n"
                + "9,TRƯỜNG MẦM NON,- Nhánh 1: Bé đến trường,Tuần 99: 01/01/2001,note\n"
                + ",,Nhánh2: Lớp của bé,Tuần 1: 01/01/2020,\n"
                + ",,Nghỉ Tết từ ngày 01/01/2020 đến 05/01/2020,,\n"
                + ",,Từ 27/04/2020 đến 01/05/2020 Ôn tập nghỉ lễ,,\n"
                + "10,BẢN THÂN,Bé là ai?,,\n");
        assertThat(parsed.entries())
                .containsExactly(
                        new BranchFileTable.Entry(2, "TRƯỜNG MẦM NON", "Bé đến trường"),
                        new BranchFileTable.Entry(3, "TRƯỜNG MẦM NON", "Lớp của bé"),
                        new BranchFileTable.Entry(6, "BẢN THÂN", "Bé là ai?"));
        assertThat(parsed.warnings()).isEmpty();
    }

    @Test
    @DisplayName("EDU-47: đọc đúng file xuất / file mẫu của hệ thống (Tháng, Chủ đề gộp dòng, dòng nghỉ, nhánh trống)")
    void readsExportedBranchFile() {
        String html = "<html><body><h1>DỰ KIẾN CÁC CHỦ ĐỀ NHÁNH</h1><table class=\"grid\">"
                + "<thead><tr><th>Tháng</th><th>Chủ đề</th><th>Chủ đề nhánh</th><th>Số tuần</th><th>Ghi chú</th></tr></thead><tbody>"
                + "<tr><td class=\"c\" rowspan=\"3\">12 - 1</td><td class=\"c\" rowspan=\"3\"><b>THẾ GIỚI THỰC VẬT</b></td>"
                + "<td>- Nhánh 1: Cây xanh quanh bé</td><td>Tuần 15: 14/12/2026<br/>18/12/2026</td><td></td></tr>"
                + "<tr><td colspan=\"3\"><i>Nghỉ Ôn tập, đánh giá cuối học kỳ I từ ngày 04/01/2027 đến hết ngày 08/01/2027</i></td></tr>"
                + "<tr><td>- Nhánh 2: </td><td>Tuần 16: 11/01/2027<br/>15/01/2027</td><td></td></tr>"
                + "<tr><td class=\"c\">1 - 2</td><td class=\"c\"><b>TẾT VÀ MÙA XUÂN</b></td>"
                + "<td>- Nhánh 1: Mùa xuân của bé</td><td>Tuần 17: 18/01/2027<br/>22/01/2027</td><td></td></tr>"
                + "</tbody></table><table class=\"plain\"><tr><td>Người duyệt kế hoạch</td></tr></table></body></html>";
        var parsed = BranchFileTable.interpret(GoalFileGrid.read("Chồi 2.doc", html.getBytes(StandardCharsets.UTF_8)));
        assertThat(parsed.entries())
                .extracting(BranchFileTable.Entry::themeName, BranchFileTable.Entry::name)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("THẾ GIỚI THỰC VẬT", "Cây xanh quanh bé"),
                        org.assertj.core.groups.Tuple.tuple("TẾT VÀ MÙA XUÂN", "Mùa xuân của bé"));
        assertThat(parsed.warnings()).isEmpty();
    }

    @Test
    @DisplayName("EDU-47: thiếu chủ đề báo dòng nguồn, bảng khác bị từ chối")
    void missingThemeAndWrongTable() {
        var parsed = csv("Chủ đề,Chủ đề nhánh\n,Nhánh 1: Tên lẻ\nBản thân,Nhánh 1: Bé là ai?\n");
        assertThat(parsed.entries()).hasSize(1);
        assertThat(parsed.warnings())
                .singleElement()
                .satisfies(w -> assertThat(w.row()).isEqualTo(2));
        assertThatThrownBy(() -> csv("Tên,Ngày\nA,01/01/2020"))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.BRANCH_FILE_NO_TABLE));
    }

    private static BranchFileTable.Parsed csv(String text) {
        return BranchFileTable.interpret(GoalFileGrid.read("branches.csv", text.getBytes(StandardCharsets.UTF_8)));
    }
}
