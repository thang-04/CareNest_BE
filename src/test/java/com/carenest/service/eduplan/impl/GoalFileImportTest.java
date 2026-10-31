package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.GoalFilePreviewResponse;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Content;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Row;
import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** EDU-46: đọc file bảng mục tiêu (Word/Excel/HTML/CSV) thành bản xem trước. */
class GoalFileImportTest {

    private static final List<String> DOMAINS = List.of(
            "Phát triển thể chất",
            "Phát triển nhận thức",
            "Phát triển ngôn ngữ",
            "Phát triển tình cảm và kỹ năng xã hội",
            "Phát triển thẩm mỹ");

    private static GoalFilePreviewResponse preview(String name, byte[] bytes) {
        return GoalFileTable.interpret(GoalFileGrid.read(name, bytes), DOMAINS);
    }

    @Test
    @DisplayName(
            "Word .docx theo mẫu Thái Dương: lĩnh vực, lĩnh vực con, nhóm, mục tiêu gộp ô, dấu tích, cảnh báo theo dòng")
    void docxSchoolTable() throws IOException {
        String rows = tr(
                        tc("STT", 1, "restart"),
                        tc("Mục tiêu giáo dục năm", 1, "restart"),
                        tc("Nội dung giáo dục năm", 1, "restart"),
                        tc("Chủ đề thực hiện", 2, null))
                + tr(
                        tc("", 1, "continue"),
                        tc("", 1, "continue"),
                        tc("", 1, "continue"),
                        tc("TMN", 1, null),
                        tc("BT", 1, null))
                + tr(tc("", 1, null), tc("I. Giáo dục phát triển thể chất", 2, null), tc("", 1, null), tc("", 1, null))
                + tr(tc("", 1, null), tc("*Phát triển vận động", 2, null), tc("", 1, null), tc("", 1, null))
                + tr(tc("", 1, null), tc("- Thực hiện được các vận động", 2, null), tc("", 1, null), tc("", 1, null))
                + tr(
                        tc("1", 1, "restart"),
                        tc("1. Trẻ thực hiện được động tác", 1, "restart"),
                        tc("- Tập các động tác", 1, null),
                        tc("TMN", 1, null),
                        tc("", 1, null))
                + tr(
                        tc("", 1, "continue"),
                        tc("", 1, "continue"),
                        tc("- Đi bước dồn", 1, null),
                        tc("", 1, null),
                        tc("BT", 1, null))
                + tr(
                        tc("", 1, null),
                        tc("II. Giáo dục phát triển nhận thức", 2, null),
                        tc("", 1, null),
                        tc("", 1, null))
                + tr(
                        tc("2", 1, null),
                        tc("2. Trẻ biết quan sát", 1, null),
                        tc("- Quan sát đồ vật", 1, null),
                        tc("x", 1, null),
                        tc("x", 1, null))
                + tr(tc("", 1, null), tc("IX. Lĩnh vực lạ", 2, null), tc("", 1, null), tc("", 1, null))
                + tr(
                        tc("3", 1, null),
                        tc("3. Mục tiêu bị bỏ", 1, null),
                        tc("- nd", 1, null),
                        tc("", 1, null),
                        tc("", 1, null));
        String xml = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + "<w:p><w:r><w:t>KẾ HOẠCH GIÁO DỤC NĂM HỌC</w:t></w:r></w:p><w:tbl>" + rows
                + "</w:tbl></w:body></w:document>";

        GoalFilePreviewResponse p = preview("ke-hoach.docx", zip(Map.of("word/document.xml", xml)));

        assertThat(p.themeAbbrs()).containsExactly("TMN", "BT");
        assertThat(p.domains())
                .extracting(GoalFilePreviewResponse.Domain::name)
                .containsExactly("Phát triển thể chất", "Phát triển nhận thức");
        List<Row> tc = p.domains().get(0).rows();
        assertThat(tc).extracting(Row::kind).containsExactly("sub", "group", "goal");
        assertThat(tc.get(0).text()).isEqualTo("Phát triển vận động");
        assertThat(tc.get(1).text()).isEqualTo("Thực hiện được các vận động");
        assertThat(tc.get(2).text()).isEqualTo("Trẻ thực hiện được động tác");
        assertThat(tc.get(2).contents())
                .containsExactly(
                        new Content("Tập các động tác", List.of("TMN")), new Content("Đi bước dồn", List.of("BT")));
        assertThat(p.domains().get(1).rows().get(0).contents())
                .containsExactly(new Content("Quan sát đồ vật", List.of("TMN", "BT")));
        assertThat(p.warnings())
                .extracting(GoalFilePreviewResponse.Warning::row)
                .containsExactly(10, 11);
    }

    @Test
    @DisplayName("Excel .xlsx: ô gộp dọc là cùng mục tiêu, tiêu đề chủ đề một dòng")
    void xlsxMergedGoal() throws IOException {
        String sheet = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + row(1, "A", "STT", "B", "Mục tiêu giáo dục", "C", "Nội dung giáo dục", "D", "TMN", "E", "BT")
                + row(2, "B", "I. Phát triển thể chất")
                + "<row r=\"3\"><c r=\"A3\"><v>1</v></c>" + is("B3", "Trẻ khỏe mạnh") + is("C3", "Ăn uống đủ chất")
                + is("D3", "x") + "</row>"
                + row(4, "C", "Vệ sinh cá nhân", "E", "x")
                + "</sheetData><mergeCells count=\"2\"><mergeCell ref=\"A3:A4\"/><mergeCell ref=\"B3:B4\"/></mergeCells>"
                + "</worksheet>";

        GoalFilePreviewResponse p = preview("muc-tieu.xlsx", zip(Map.of("xl/worksheets/sheet1.xml", sheet)));

        Row goal = p.domains().get(0).rows().get(0);
        assertThat(goal.text()).isEqualTo("Trẻ khỏe mạnh");
        assertThat(goal.contents())
                .containsExactly(
                        new Content("Ăn uống đủ chất", List.of("TMN")), new Content("Vệ sinh cá nhân", List.of("BT")));
        assertThat(p.warnings()).isEmpty();
    }

    @Test
    @DisplayName("Word .doc CareNest xuất (HTML): rowspan/colspan như bảng A")
    void careNestDocExport() {
        String html = "<html><body><table class=\"grid\"><thead><tr><th rowspan=\"2\">STT</th>"
                + "<th rowspan=\"2\">Mục tiêu giáo dục năm</th><th rowspan=\"2\">Nội dung giáo dục năm</th>"
                + "<th colspan=\"2\">Chủ đề thực hiện</th></tr><tr><th class=\"abbr\">TMN</th><th>BT</th></tr></thead><tbody>"
                + "<tr><td></td><td colspan=\"2\"><b>I. Giáo dục phát triển thể chất</b></td><td></td><td></td></tr>"
                + "<tr><td rowspan=\"2\" class=\"c\">1</td><td rowspan=\"2\">1. Trẻ khỏe &amp; vui</td><td>- Ăn uống</td>"
                + "<td>TMN</td><td></td></tr>"
                + "<tr><td>- Vệ sinh</td><td></td><td>BT</td></tr></tbody></table></body></html>";

        GoalFilePreviewResponse p = preview("1. KẾ HOẠCH.doc", html.getBytes(StandardCharsets.UTF_8));

        Row goal = p.domains().get(0).rows().get(0);
        assertThat(goal.text()).isEqualTo("Trẻ khỏe & vui");
        assertThat(goal.contents())
                .containsExactly(new Content("Ăn uống", List.of("TMN")), new Content("Vệ sinh", List.of("BT")));
    }

    @Test
    @DisplayName("CSV mẫu cũ 6 cột vẫn nhập được")
    void legacyCsv() {
        String csv = "﻿Lĩnh vực,Loại dòng,STT,Mục tiêu,Nội dung giáo dục,\"Chủ đề (viết tắt, cách nhau bởi ;)\"\n"
                + "Phát triển thể chất,Lĩnh vực con,,Vận động,,\n"
                + "Phát triển thể chất,Mục tiêu,MT1,Trẻ khỏe,Ăn uống,TMN; BT\n"
                + "Phát triển thể chất,Mục tiêu,MT1,Trẻ khỏe,Vệ sinh,\n"
                + "Lĩnh vực lạ,Mục tiêu,MT2,Bỏ,Bỏ,\n";

        GoalFilePreviewResponse p = preview("muc-tieu.csv", csv.getBytes(StandardCharsets.UTF_8));

        List<Row> rows = p.domains().get(0).rows();
        assertThat(rows).extracting(Row::kind).containsExactly("sub", "goal");
        assertThat(rows.get(1).contents())
                .containsExactly(new Content("Ăn uống", List.of("TMN", "BT")), new Content("Vệ sinh", List.of()));
        assertThat(p.themeAbbrs()).containsExactly("TMN", "BT");
        assertThat(p.warnings())
                .extracting(GoalFilePreviewResponse.Warning::row)
                .containsExactly(5);
    }

    @Test
    @DisplayName("Word đời cũ (.doc nhị phân), file không có bảng mục tiêu, định dạng lạ: báo lỗi rõ")
    void unsupportedFiles() throws IOException {
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0};
        assertThatThrownBy(() -> preview("cu.doc", ole))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.GOAL_FILE_UNSUPPORTED));
        assertThatThrownBy(() -> preview("ghi-chu.txt", "abc".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.GOAL_FILE_UNSUPPORTED));
        String noTable = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + "<w:tbl>" + tr(tc("Tên", 1, null), tc("Ngày", 1, null)) + "</w:tbl></w:body></w:document>";
        assertThatThrownBy(() -> preview("khac.docx", zip(Map.of("word/document.xml", noTable))))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.GOAL_FILE_NO_TABLE));
    }

    @Test
    @DisplayName("XML có DOCTYPE (XXE) bị từ chối")
    void rejectsDoctype() throws IOException {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">&x;</w:document>";
        assertThatThrownBy(() -> preview("x.docx", zip(Map.of("word/document.xml", xxe))))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.GOAL_FILE_UNSUPPORTED));
    }

    /* ---------- dựng file trong test ---------- */

    private static String tr(String... cells) {
        return "<w:tr>" + String.join("", cells) + "</w:tr>";
    }

    private static String tc(String text, int span, String vMerge) {
        String pr = (span > 1 ? "<w:gridSpan w:val=\"" + span + "\"/>" : "")
                + (vMerge == null ? "" : "continue".equals(vMerge) ? "<w:vMerge/>" : "<w:vMerge w:val=\"restart\"/>");
        String paragraphs = String.join(
                "",
                text.lines()
                        .map(l -> "<w:p><w:r><w:t>" + l + "</w:t></w:r></w:p>")
                        .toList());
        return "<w:tc>" + (pr.isEmpty() ? "" : "<w:tcPr>" + pr + "</w:tcPr>")
                + (paragraphs.isEmpty() ? "<w:p/>" : paragraphs) + "</w:tc>";
    }

    private static String row(int r, String... colValues) {
        StringBuilder sb = new StringBuilder("<row r=\"" + r + "\">");
        for (int i = 0; i < colValues.length; i += 2) {
            sb.append(is(colValues[i] + r, colValues[i + 1]));
        }
        return sb.append("</row>").toString();
    }

    private static String is(String ref, String text) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t>" + text + "</t></is></c>";
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
