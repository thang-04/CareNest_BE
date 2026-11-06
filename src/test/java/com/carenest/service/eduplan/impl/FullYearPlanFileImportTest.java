package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.carenest.entity.eduplan.EduDomain;
import com.carenest.repository.eduplan.EduDomainRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.utils.enums.CareNestRole;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class FullYearPlanFileImportTest {
    @Mock
    private EduDomainRepository domains;

    @Mock
    private EduPlanAccessPolicy access;

    @Mock
    private com.carenest.service.eduplan.EduWorkflowService workflow;

    @Mock
    private com.carenest.service.organization.SchoolStructureService schoolStructure;

    @InjectMocks
    private EduGoalFileServiceImpl service;

    @BeforeEach
    void allowed() {
        given(schoolStructure.findAgeGroups())
                .willReturn(List.of(
                        new com.carenest.dto.organization.AgeGroupResponse(1L, "ag-3", "Mẫu giáo", "MG", (short) 1)));
        given(workflow.isAuthorEligible(any(), eq("year"), eq(1L), org.mockito.ArgumentMatchers.isNull()))
                .willReturn(true);
        var domain = Mockito.mock(EduDomain.class);
        given(domain.isActive()).willReturn(true);
        given(domain.getName()).willReturn("Phát triển thể chất");
        given(domains.findAllByOrderBySortOrderAsc()).willReturn(List.of(domain));
    }

    @Test
    @DisplayName("EDU-46: preview nhận thông tin chung, lịch, nghỉ và viết tắt cột; không thay ngày nguồn")
    void completePlanPreview() {
        var p = read("<h1>KẾ HOẠCH GIÁO DỤC NĂM HỌC 2025 - 2026</h1><p>KHỐI MẪU GIÁO 3 - 4 TUỔI</p>"
                + goals()
                + schedule(row("1", "Trường mầm non", "Ngày hội", "3 tuần 08/09/2025 – 26/09/2025", "")
                        + row(
                                "2",
                                "Bản thân",
                                "",
                                "4 tuần 29/09/2025 – 24/10/2025",
                                "Nghỉ ôn tập 13/10/2025 → 17/10/2025")));
        assertThat(p.path("metadata").path("schoolYear").asText()).isEqualTo("2025-2026");
        assertThat(p.path("metadata").path("title").asText()).contains("KẾ HOẠCH GIÁO DỤC");
        assertThat(p.path("metadata").path("ageFromMonths").asInt()).isEqualTo(36);
        assertThat(p.path("metadata").path("ageToMonths").asInt()).isEqualTo(48);
        assertThat(p.path("themes").size()).isEqualTo(2);
        assertThat(p.path("themes").get(0).path("abbr").asText()).isEqualTo("MN");
        assertThat(p.path("themes").get(0).path("start").asText()).isEqualTo("2025-09-08");
        assertThat(p.path("themes").get(1).path("abbr").asText()).isEqualTo("BT");
        assertThat(p.path("themes").get(1).path("festivals").asText()).isEmpty();
        assertThat(p.path("breaks").size()).isEqualTo(1);
        assertThat(p.path("breaks").get(0).path("start").asText()).isEqualTo("2025-10-13");
        assertThat(p.path("breaks").get(0).path("end").asText()).isEqualTo("2025-10-17");
        assertThat(p.path("domains")
                        .get(0)
                        .path("rows")
                        .get(0)
                        .path("contents")
                        .get(0)
                        .path("themes")
                        .size())
                .isEqualTo(1);
        assertThat(p.path("warnings").size()).isZero();
    }

    @Test
    @DisplayName("EDU-46: không suy mã chủ đề nếu số thứ tự/số cột không đủ; báo cảnh báo")
    void ambiguousOrdinalDoesNotInventLinks() {
        var p = read(goals() + schedule(row("2", "Chủ đề không rõ", "", "3 tuần", "")));
        assertThat(p.path("themes").size()).isZero();
        assertThat(p.path("warnings").size()).isPositive();
    }

    @Test
    @DisplayName("EDU-46: ngày sai không bị tự sửa, trả cảnh báo; mã rõ không phụ thuộc thứ tự")
    void explicitAbbreviationAndInvalidDate() {
        var p = read(goals() + "<table><tr><th>Tên chủ đề</th><th>Viết tắt</th><th>Từ ngày</th><th>Đến ngày</th></tr>"
                + "<tr><td>Bản thân</td><td>BT</td><td>31/02/2025</td><td>07/03/2025</td></tr></table>");
        assertThat(p.path("themes").size()).isEqualTo(1);
        assertThat(p.path("themes").get(0).path("abbr").asText()).isEqualTo("BT");
        assertThat(p.path("themes").get(0).path("start").isNull()).isTrue();
        assertThat(p.path("warnings").size()).isPositive();
    }

    @Test
    @DisplayName("EDU-46: file chỉ bảng mục tiêu cũ không tạo metadata/chủ đề giả")
    void goalsOnlyRemainsCompatible() {
        var p = read(goals());
        assertThat(p.path("metadata").isNull()).isTrue();
        assertThat(p.path("themes").size()).isZero();
        assertThat(p.path("breaks").size()).isZero();
        assertThat(p.path("domains").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("EDU-46: nhiều bảng lịch không đủ căn cứ ghép ordinal ⇒ cảnh báo, không đoán")
    void competingSchedulesAreAmbiguous() {
        String table = schedule(row("1", "Chủ đề A", "", "08/09/2025 - 12/09/2025", "")
                + row("2", "Chủ đề B", "", "15/09/2025 - 19/09/2025", ""));
        var p = read(goals() + table + table);
        assertThat(p.path("themes").size()).isZero();
        assertThat(p.path("warnings").size()).isPositive();
    }

    @Test
    @DisplayName("EDU-46: không lấy khoảng tuổi trong câu mục tiêu làm thông tin chung")
    void ageInsideGoalIsNotMetadata() {
        var p = read(goals().replace("Trẻ vận động", "Trẻ 3 - 4 tuổi vận động"));
        assertThat(p.path("metadata").isNull()).isTrue();
    }

    @Test
    @DisplayName("EDU-46: cảnh báo năm học/khoảng tuổi sai trong metadata phải được trả về")
    void metadataWarningsAreIncluded() {
        var p = read("<p>KẾ HOẠCH GIÁO DỤC NĂM HỌC 2025 - 2028</p><p>KHỐI 9 - 12 TUỔI</p>" + goals());
        assertThat(p.path("warnings").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("EDU-46: nhiều khoảng ngày trong ghi chú không bị cắt âm thầm thành một đợt nghỉ")
    void multipleBreakRangesNeedReview() {
        String note = "Nghỉ đợt 1 13/10/2025 - 17/10/2025; nghỉ đợt 2 20/10/2025 - 24/10/2025";
        var p = read(goals()
                + schedule(row("1", "Chủ đề A", "", "08/09/2025 - 12/09/2025", note)
                        + row("2", "Chủ đề B", "", "15/09/2025 - 19/09/2025", "")));
        assertThat(p.path("warnings").size()).isPositive();
        assertThat(p.path("breaks").size()).isZero();
    }

    @Test
    @DisplayName("EDU-46: nhiều khoảng thời gian một chủ đề cần xem lại, không tự chọn hai ngày đầu")
    void multipleThemeRangesNeedReview() {
        var p = read(goals()
                + schedule(row("1", "Chủ đề A", "", "08/09/2025 - 12/09/2025; 22/09/2025 - 26/09/2025", "")
                        + row("2", "Chủ đề B", "", "15/09/2025 - 19/09/2025", "")));
        assertThat(p.path("warnings").size()).isPositive();
        assertThat(p.path("themes").get(0).path("start").isNull()).isTrue();
    }

    @Test
    @DisplayName("EDU-46: Word docx đọc metadata ngoài bảng, không dùng khoảng tuổi trong nội dung")
    void docxMetadataOutsideTables() throws IOException {
        String xml = "<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body>"
                + "<w:p><w:r><w:t>KẾ HOẠCH GIÁO DỤC NĂM HỌC 2025 - 2026</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>NHÀ TRẺ 24 – 36 THÁNG</w:t></w:r></w:p>"
                + "<w:tbl><w:tr>" + xmlCell("STT") + xmlCell("Mục tiêu") + xmlCell("Nội dung") + "</w:tr>"
                + "<w:tr>" + xmlCell("I. Phát triển thể chất") + "</w:tr>"
                + "<w:tr>" + xmlCell("1") + xmlCell("Trẻ 3 - 4 tuổi vận động") + xmlCell("Đi thăng bằng") + "</w:tr>"
                + "</w:tbl></w:body></w:document>";
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var p = read("synthetic.docx", out.toByteArray());
        assertThat(p.path("metadata").path("ageFromMonths").asInt()).isEqualTo(24);
        assertThat(p.path("metadata").path("ageToMonths").asInt()).isEqualTo(36);
        assertThat(p.path("metadata").path("schoolYear").asText()).isEqualTo("2025-2026");
        assertThat(p.path("domains").get(0).path("rows").get(0).path("text").asText())
                .contains("3 - 4 tuổi");
    }

    private static String xmlCell(String text) {
        return "<w:tc><w:p><w:r><w:t>" + text + "</w:t></w:r></w:p></w:tc>";
    }

    @Test
    @DisplayName("EDU-46: khoảng tuổi giải thích sau bảng không trở thành nhóm tuổi nguồn")
    void ageAfterGoalTableIsNotMetadata() {
        var p = read(goals() + "<p>Giải thích: Trẻ 3 - 4 tuổi cần được hỗ trợ vận động.</p>");
        assertThat(p.path("metadata").isNull()).isTrue();
    }

    private JsonNode read(String html) {
        return read("synthetic.doc", html.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode read(String name, byte[] bytes) {
        var user = new CurrentUser(8L, "test-8", "synthetic", "Kiểm thử", null, Set.of(CareNestRole.VICE_PRINCIPAL));
        var file = new MockMultipartFile("file", name, "application/msword", bytes);
        return new ObjectMapper().valueToTree(service.preview(user, file));
    }

    private static String goals() {
        return "<table><tr><th>STT</th><th>Mục tiêu</th><th>Nội dung</th><th>MN</th><th>BT</th></tr>"
                + "<tr><td colspan='5'>I. Phát triển thể chất</td></tr>"
                + "<tr><td>1</td><td>Trẻ vận động</td><td>Đi thăng bằng</td><td>MN</td><td></td></tr></table>";
    }

    private static String schedule(String rows) {
        return "<table><tr><th>STT</th><th>Tên chủ đề</th><th>Ngày lễ hội</th><th>Thời gian</th><th>Ghi chú</th></tr>"
                + rows + "</table>";
    }

    private static String row(String no, String name, String festivals, String dates, String note) {
        return "<tr><td>" + no + "</td><td>" + name + "</td><td>" + festivals + "</td><td>" + dates + "</td><td>" + note
                + "</td></tr>";
    }
}
