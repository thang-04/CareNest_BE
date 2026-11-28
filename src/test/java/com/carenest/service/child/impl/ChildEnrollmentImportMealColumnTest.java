package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.HealthDeclarationInput;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildEnrollmentImportService.ImportRequest;
import com.carenest.service.child.ChildEnrollmentImportService.Preview;
import com.carenest.service.child.ChildEnrollmentImportService.PreviewRow;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/** Unit test service bằng Mockito (không DB): cột "Báo ăn" khi xem trước và nhập trẻ từ file CSV. Dữ liệu giả. */
@ExtendWith(MockitoExtension.class)
class ChildEnrollmentImportMealColumnTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-13T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String MOTHER_ID = "001200000001";
    private static final CurrentUser VICE = new CurrentUser(
            3L, "kc-3", "vp", "PHT giả", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of(Permission.CHILD_ENROLL));

    private static final List<String> HEADER = List.of(
            "Họ tên trẻ",
            "Ngày sinh (dd/mm/yyyy)",
            "Giới tính (Nam/Nữ)",
            "Họ tên phụ huynh",
            "Quan hệ (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ)",
            "Số điện thoại",
            "Số CCCD phụ huynh",
            "Báo ăn",
            "Dị ứng",
            "Chế độ ăn",
            "Sinh non (Có/Không)",
            "Tuần thai khi sinh (20–42)",
            "Dân tộc",
            "Đối tượng",
            "Nơi ở - Tỉnh/thành",
            "Nơi ở - Phường/xã",
            "Nơi ở - Số nhà/thôn/xóm",
            "Tiền sử bản thân",
            "Tiền sử gia đình",
            "Tiếp xúc người bệnh lao");

    @Mock
    private ChildIntakeService childIntakeService;

    private ChildEnrollmentImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChildEnrollmentImportServiceImpl(childIntakeService, CLOCK);
    }

    @Nested
    @DisplayName("preview")
    class PreviewFile {

        @Test
        @DisplayName("CHD-13: Báo ăn = Không ⇒ cờ false, bỏ qua dị ứng và chế độ ăn, có ghi chú")
        void mealColumnNoSkipsAllergyAndDiet() {
            Map<String, String> row = validRow("Không");
            row.put("Dị ứng", "Trứng");

            PreviewRow result = service.preview(VICE, csv(row)).rows().getFirst();

            HealthDeclarationInput declaration = result.payload().declaration();
            assertThat(declaration.mealRegistered()).isFalse();
            assertThat(declaration.allergies().state()).isEqualTo("NOT_PROVIDED");
            assertThat(declaration.diet().state()).isEqualTo("NOT_PROVIDED");
            assertThat(result.notes()).contains("Không báo ăn: bỏ qua cột Dị ứng, Chế độ ăn");
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("CHD-13: ô Báo ăn trống ⇒ có báo ăn (true)")
        void blankMealColumnMeansRegistered() {
            PreviewRow result = service.preview(VICE, csv(validRow(""))).rows().getFirst();

            assertThat(result.payload().declaration().mealRegistered()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("Báo ăn không phân biệt hoa thường, dấu cách thừa")
        void mealColumnIgnoresCaseAndSpaces() {
            List<PreviewRow> rows = service.preview(VICE, csv(validRow(" KHÔNG "), validRow("có")))
                    .rows();

            assertThat(rows.get(0).payload().declaration().mealRegistered()).isFalse();
            assertThat(rows.get(1).payload().declaration().mealRegistered()).isTrue();
        }

        @Test
        @DisplayName("Giá trị lạ ⇒ lỗi dòng 'Báo ăn ghi Có hoặc Không'")
        void invalidMealValueIsRowError() {
            PreviewRow result =
                    service.preview(VICE, csv(validRow("Ăn trưa"))).rows().getFirst();

            assertThat(result.errors()).contains("Báo ăn ghi Có hoặc Không");
        }
    }

    @Nested
    @DisplayName("importRows")
    class ImportRows {

        @Test
        @DisplayName("Nhập giữ nguyên cờ báo ăn của dòng đã xem trước")
        void importKeepsMealFlag() {
            Preview preview = service.preview(VICE, csv(validRow("Không")));
            EnrollChildRequest row = preview.rows().getFirst().payload();
            EnrollmentDraft draft = new EnrollmentDraft("Bé An giả", null, null, List.of(), null, null, null, 1L);
            given(childIntakeService.prepareEnrollment(eq(VICE), any())).willReturn(draft);
            given(childIntakeService.createEnrolledChild(eq(VICE), eq(draft), any()))
                    .willReturn(201L);

            service.importRows(VICE, new ImportRequest(List.of(row), 1L));

            ArgumentCaptor<EnrollChildRequest> scoped = ArgumentCaptor.forClass(EnrollChildRequest.class);
            verify(childIntakeService).prepareEnrollment(eq(VICE), scoped.capture());
            assertThat(scoped.getValue().declaration().mealRegistered()).isFalse();
        }
    }

    private static Map<String, String> validRow(String meal) {
        Map<String, String> row = new LinkedHashMap<>();
        HEADER.forEach(h -> row.put(h, ""));
        row.put("Họ tên trẻ", "Bé An giả");
        row.put("Ngày sinh (dd/mm/yyyy)", "15/03/2022");
        row.put("Giới tính (Nam/Nữ)", "Nam");
        row.put("Họ tên phụ huynh", "Mẹ giả");
        row.put("Quan hệ (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ)", "Mẹ");
        row.put("Số điện thoại", "0900000001");
        row.put("Số CCCD phụ huynh", MOTHER_ID);
        row.put("Báo ăn", meal);
        row.put("Sinh non (Có/Không)", "Không");
        row.put("Tuần thai khi sinh (20–42)", "39");
        row.put("Dân tộc", "Kinh");
        row.put("Đối tượng", "Thường");
        row.put("Nơi ở - Tỉnh/thành", "Tỉnh giả");
        row.put("Nơi ở - Phường/xã", "Phường giả");
        row.put("Nơi ở - Số nhà/thôn/xóm", "Số 1");
        row.put("Tiền sử bản thân", "Không");
        row.put("Tiền sử gia đình", "Không");
        row.put("Tiếp xúc người bệnh lao", "Không");
        return row;
    }

    @SafeVarargs
    private static MockMultipartFile csv(Map<String, String>... rows) {
        List<List<String>> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Map<String, String> row : rows)
            lines.add(HEADER.stream().map(row::get).toList());
        String text = lines.stream()
                .map(line -> line.stream()
                        .map(v -> "\"" + v.replace("\"", "\"\"") + "\"")
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
        return new MockMultipartFile("file", "tre.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }
}
