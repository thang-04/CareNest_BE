package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.child.DuplicateCheckResponse;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentChildInput;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.HealthDeclarationInput;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildEnrollmentImportService.ImportRequest;
import com.carenest.service.child.ChildEnrollmentImportService.ImportResult;
import com.carenest.service.child.ChildEnrollmentImportService.Preview;
import com.carenest.service.child.ChildEnrollmentImportService.PreviewRow;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DuplicateChildKind;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.Permission;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/** Unit test service bằng Mockito (không DB): xem trước và nhập trẻ từ file CSV. Dữ liệu giả. */
@ExtendWith(MockitoExtension.class)
class ChildEnrollmentImportServiceImplTest {

    // Hôm nay 2026-11-13 giờ trường
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-13T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String MOTHER_ID = "001200000001";
    private static final String FATHER_ID = "001200000002";
    private static final String RECIPIENT_ERROR = "Chọn đúng một phụ huynh nhận tài khoản";
    private static final String IN_FILE_DUPLICATE = "Trùng trẻ trong file";
    private static final String INCONSISTENT = "CCCD và số điện thoại của phụ huynh không nhất quán giữa các dòng";
    private static final CurrentUser VICE = new CurrentUser(
            3L, "kc-3", "vp", "PHT giả", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of(Permission.CHILD_ENROLL));

    // Cột theo mẫu nhập (nhãn đầy đủ hoặc phần trước ngoặc đều nhận)
    private static final List<String> HEADER = List.of(
            "Họ tên trẻ",
            "Ngày sinh (dd/mm/yyyy)",
            "Giới tính (Nam/Nữ)",
            "Họ tên phụ huynh",
            "Quan hệ (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ)",
            "Số điện thoại",
            "Số CCCD phụ huynh",
            "Email",
            "Dị ứng",
            "Chế độ ăn",
            "Sinh non (Có/Không)",
            "Tuần thai khi sinh (20–42)",
            "Dân tộc",
            "Đối tượng",
            "Nhóm máu",
            "Nơi ở - Tỉnh/thành",
            "Nơi ở - Phường/xã",
            "Nơi ở - Số nhà/thôn/xóm",
            "Tiền sử bản thân",
            "Tiền sử gia đình",
            "Tiếp xúc người bệnh lao",
            "Ngày tiếp xúc người bệnh lao gần nhất",
            "CCCD phụ huynh nhận tài khoản",
            "Họ tên phụ huynh 2",
            "Quan hệ phụ huynh 2",
            "Số điện thoại phụ huynh 2",
            "Số CCCD phụ huynh 2");

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
        @DisplayName("Dòng hợp lệ ⇒ không lỗi; dữ liệu chuyển sang form tiếp nhận (ngày ISO, mã giới tính, quan hệ)")
        void validRowIsMappedToEnrollmentForm() {
            Preview preview = service.preview(VICE, csv(validRow()));

            assertThat(preview.supported()).isTrue();
            assertThat(preview.fileName()).isEqualTo("tre.csv");
            PreviewRow row = preview.rows().getFirst();
            assertThat(row.rowNo()).isEqualTo(2);
            assertThat(row.errors()).isEmpty();
            assertThat(row.selectionError()).isNull();
            assertThat(row.notes()).isEmpty();
            assertThat(row.raw()).containsEntry("fullName", "Bé An giả").containsEntry("phone", "0900000001");
            EnrollmentChildInput child = row.payload().child();
            assertThat(child.fullName()).isEqualTo("Bé An giả");
            assertThat(child.dateOfBirth()).isEqualTo("2022-03-15");
            assertThat(child.gender()).isEqualTo("MALE");
            assertThat(child.guardians())
                    .containsExactly(new GuardianInput("Mẹ giả", "MOTHER", "0900000001", MOTHER_ID, "", false));
            assertThat(child.profile().premature()).isEqualTo("NO");
            assertThat(child.profile().gestationWeeks()).isEqualTo(39);
            assertThat(child.profile().address().province()).isEqualTo("Tỉnh giả");
            assertThat(row.payload().classId()).isNull();
            assertThat(row.payload().campusId()).isNull();
            HealthDeclarationInput declaration = row.payload().declaration();
            assertThat(declaration.allergies().state()).isEqualTo("NOT_PROVIDED");
            assertThat(declaration.diet().state()).isEqualTo("NOT_PROVIDED");
            assertThat(declaration.otherNotes().state()).isEqualTo("NOT_PROVIDED");
            assertThat(declaration.medicalHistory().self().state()).isEqualTo("NONE_REPORTED");
            assertThat(declaration.medicalHistory().tbContact().state()).isEqualTo("NONE_REPORTED");
            verify(childIntakeService).checkDuplicate(VICE, "Bé An giả", "2022-03-15", List.of(MOTHER_ID), null);
        }

        @Test
        @DisplayName("G94-180/CHD-05, CHD-06: ngày kiểu số Excel, Nữ, Anh/chị, dị ứng nhiều chất, tiếp xúc lao có ngày")
        void mapsExcelValuesAndDeclaration() {
            Map<String, String> row = validRow();
            row.put("Ngày sinh (dd/mm/yyyy)", "44635");
            row.put("Giới tính (Nam/Nữ)", "Nữ");
            row.put("Quan hệ (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ)", "Anh/chị");
            row.put("Dị ứng", "Sữa bò; Trứng / Tôm");
            row.put("Chế độ ăn", "Không");
            row.put("Sinh non (Có/Không)", "Có");
            row.put("Tuần thai khi sinh (20–42)", "35");
            row.put("Tiền sử bản thân", "Hen phế quản giả");
            row.put("Tiếp xúc người bệnh lao", "Có");
            row.put("Ngày tiếp xúc người bệnh lao gần nhất", "1/10/2026");

            PreviewRow result = service.preview(VICE, csv(row)).rows().getFirst();

            assertThat(result.errors()).isEmpty();
            EnrollmentChildInput child = result.payload().child();
            assertThat(child.dateOfBirth()).isEqualTo("2022-03-15");
            assertThat(child.gender()).isEqualTo("FEMALE");
            assertThat(child.guardians().getFirst().relation()).isEqualTo("SIBLING");
            assertThat(child.profile().premature()).isEqualTo("YES");
            HealthDeclarationInput declaration = result.payload().declaration();
            assertThat(declaration.allergies().state()).isEqualTo("REPORTED");
            assertThat(declaration.allergies().items()).containsExactly("Sữa bò", "Trứng", "Tôm");
            assertThat(declaration.diet().state()).isEqualTo("NONE_REPORTED");
            assertThat(declaration.medicalHistory().self().state()).isEqualTo("REPORTED");
            assertThat(declaration.medicalHistory().self().text()).isEqualTo("Hen phế quản giả");
            assertThat(declaration.medicalHistory().tbContact().state()).isEqualTo("REPORTED");
            assertThat(declaration.medicalHistory().tbContact().contactDate()).isEqualTo("2026-10-01");
        }

        @Test
        @DisplayName("AUTH-18: hai phụ huynh, cột CCCD người nhận khớp phụ huynh 2 (bỏ khoảng trắng) ⇒ chọn đúng người")
        void recipientColumnSelectsGuardian() {
            Map<String, String> row = withSecondGuardian(validRow());
            row.put("CCCD phụ huynh nhận tài khoản", "0012 0000 0002");

            PreviewRow result = service.preview(VICE, csv(row)).rows().getFirst();

            assertThat(result.selectionError()).isNull();
            assertThat(result.errors()).isEmpty();
            assertThat(result.payload().child().guardians())
                    .extracting(GuardianInput::accountRecipient)
                    .containsExactly(false, true);
            verify(childIntakeService)
                    .checkDuplicate(VICE, "Bé An giả", "2022-03-15", List.of(MOTHER_ID, FATHER_ID), null);
        }

        @Test
        @DisplayName("AUTH-18: hai phụ huynh chưa chọn người nhận ⇒ selectionError riêng, không tính vào lỗi dòng")
        void missingRecipientIsSelectionError() {
            PreviewRow result = service.preview(VICE, csv(withSecondGuardian(validRow())))
                    .rows()
                    .getFirst();

            assertThat(result.selectionError()).isEqualTo(RECIPIENT_ERROR);
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("Ô sai định dạng ⇒ câu lỗi của validator trong lỗi dòng")
        void fieldErrorsBecomeRowErrors() {
            Map<String, String> row = validRow();
            row.put("Số điện thoại", "12345");
            row.put("Tuần thai khi sinh (20–42)", "ba chín");

            PreviewRow result = service.preview(VICE, csv(row)).rows().getFirst();

            assertThat(result.errors())
                    .contains("Nhập số điện thoại hợp lệ, ví dụ 0912 345 678", "Nhập tuần thai khi sinh");
        }

        @Test
        @DisplayName("G94-180/CHD-04: trùng trẻ đang học/chờ xếp lớp ⇒ lỗi dòng")
        void activeDuplicateIsError() {
            given(childIntakeService.checkDuplicate(VICE, "Bé An giả", "2022-03-15", List.of(MOTHER_ID), null))
                    .willReturn(new DuplicateCheckResponse(
                            DuplicateChildKind.ACTIVE, true, 7L, "TR007", "Bé An giả", null, null));

            PreviewRow result = service.preview(VICE, csv(validRow())).rows().getFirst();

            assertThat(result.errors())
                    .containsExactly("Trẻ đã có hồ sơ trong hệ thống (trùng họ tên, ngày sinh và phụ huynh)");
            assertThat(result.notes()).isEmpty();
        }

        @Test
        @DisplayName("G94-180/CHD-04: trùng trẻ đã nghỉ ⇒ chỉ ghi chú gợi ý mở lại hồ sơ cũ (có mã hồ sơ)")
        void leftDuplicateIsNoteWithCode() {
            given(childIntakeService.checkDuplicate(VICE, "Bé An giả", "2022-03-15", List.of(MOTHER_ID), null))
                    .willReturn(new DuplicateCheckResponse(
                            DuplicateChildKind.LEFT, true, 7L, "TR007", "Bé An giả", LocalDate.of(2026, 6, 1), true));

            PreviewRow result = service.preview(VICE, csv(validRow())).rows().getFirst();

            assertThat(result.errors()).isEmpty();
            assertThat(result.notes())
                    .containsExactly("Trẻ từng học ở trường (hồ sơ cũ TR007, đã nghỉ học): nên mở lại hồ sơ cũ ở"
                            + " Tiếp nhận trẻ thay vì nhập mới.");
        }

        @Test
        @DisplayName("G94-180/CHD-04: trùng trẻ đã nghỉ ở điểm trường khác ⇒ ghi chú không lộ mã hồ sơ")
        void leftDuplicateOtherCampusHidesCode() {
            given(childIntakeService.checkDuplicate(VICE, "Bé An giả", "2022-03-15", List.of(MOTHER_ID), null))
                    .willReturn(DuplicateCheckResponse.otherCampus(DuplicateChildKind.LEFT));

            PreviewRow result = service.preview(VICE, csv(validRow())).rows().getFirst();

            assertThat(result.notes())
                    .containsExactly(
                            "Trẻ từng học ở trường (đã nghỉ học): nên mở lại hồ sơ cũ ở Tiếp nhận trẻ thay vì nhập mới.");
        }

        @Test
        @DisplayName(
                "G94-180/CHD-04: hai dòng cùng trẻ (họ tên khác hoa thường, khoảng trắng) ⇒ dòng sau lỗi trùng trong file")
        void duplicateInFileIsFlaggedOnLaterRow() {
            Map<String, String> again = validRow();
            again.put("Họ tên trẻ", "  BÉ AN   GIẢ ");

            Preview preview = service.preview(VICE, csv(validRow(), again));

            assertThat(preview.rows()).extracting(PreviewRow::rowNo).containsExactly(2, 3);
            assertThat(preview.rows().getFirst().errors()).isEmpty();
            assertThat(preview.rows().get(1).errors()).containsExactly(IN_FILE_DUPLICATE);
        }

        @Test
        @DisplayName("AUTH-18: cùng CCCD khác SĐT giữa các dòng ⇒ lỗi không nhất quán")
        void sameCitizenIdDifferentPhoneAcrossRows() {
            Map<String, String> sibling = validRow();
            sibling.put("Họ tên trẻ", "Bé Bình giả");
            sibling.put("Số điện thoại", "0900000003");

            Preview preview = service.preview(VICE, csv(validRow(), sibling));

            assertThat(preview.rows().getFirst().errors()).isEmpty();
            assertThat(preview.rows().get(1).errors()).containsExactly(INCONSISTENT);
        }

        @Test
        @DisplayName("AUTH-18: cùng SĐT khác CCCD giữa các dòng ⇒ lỗi không nhất quán")
        void samePhoneDifferentCitizenIdAcrossRows() {
            Map<String, String> sibling = validRow();
            sibling.put("Họ tên trẻ", "Bé Bình giả");
            sibling.put("Số CCCD phụ huynh", "001200000009");

            Preview preview = service.preview(VICE, csv(validRow(), sibling));

            assertThat(preview.rows().get(1).errors()).containsExactly(INCONSISTENT);
        }

        @Test
        @DisplayName("Anh chị em cùng phụ huynh (CCCD, SĐT khớp) ⇒ không lỗi")
        void siblingsWithSameGuardianAreFine() {
            Map<String, String> sibling = validRow();
            sibling.put("Họ tên trẻ", "Bé Bình giả");

            Preview preview = service.preview(VICE, csv(validRow(), sibling));

            assertThat(preview.rows()).allSatisfy(r -> assertThat(r.errors()).isEmpty());
        }

        @Test
        @DisplayName("File không đúng loại ⇒ lỗi ô file, không kiểm trùng")
        void unreadableFileIsRejected() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "tre.txt", "text/plain", "Họ tên trẻ".getBytes(StandardCharsets.UTF_8));

            assertFileError(
                    () -> service.preview(VICE, file),
                    "Không đọc được file. Dùng CSV UTF-8, XLS hoặc XLSX theo mẫu, tối đa 10 MB và 500 trẻ.");
            verifyNoInteractions(childIntakeService);
        }

        @Test
        @DisplayName("Chỉ có dòng tiêu đề ⇒ File không có dữ liệu")
        void headerOnlyIsRejected() {
            assertFileError(() -> service.preview(VICE, csv()), "File không có dữ liệu");
        }

        @Test
        @DisplayName("Thiếu cột bắt buộc ⇒ liệt kê nhãn cột thiếu theo thứ tự mẫu")
        void missingRequiredColumnsAreListed() {
            List<String> header = new ArrayList<>(HEADER);
            header.remove("Dân tộc");
            header.remove("Số CCCD phụ huynh");
            MockMultipartFile file =
                    file(List.of(header, header.stream().map(h -> "x").toList()));

            assertFileError(() -> service.preview(VICE, file), "Thiếu cột: Số CCCD phụ huynh, Dân tộc");
            verifyNoInteractions(childIntakeService);
        }
    }

    @Nested
    @DisplayName("importRows")
    class ImportRows {

        @Test
        @DisplayName(
                "G94-180/AUTH-20: kiểm mọi dòng trước, rồi ghi hồ sơ Chờ xếp lớp ở điểm trường chọn, chưa liên kết tài khoản")
        void importsAllRowsAfterValidation() {
            EnrollChildRequest first = request("Bé An giả", MOTHER_ID, "0900000001", 99L);
            EnrollChildRequest second = request("Bé Bình giả", FATHER_ID, "0900000002", null);
            EnrollmentDraft draftA = draft("Bé An giả");
            EnrollmentDraft draftB = draft("Bé Bình giả");
            given(childIntakeService.prepareEnrollment(eq(VICE), any())).willReturn(draftA, draftB);
            given(childIntakeService.createEnrolledChild(VICE, draftA, Map.of()))
                    .willReturn(201L);
            given(childIntakeService.createEnrolledChild(VICE, draftB, Map.of()))
                    .willReturn(202L);

            ImportResult result = service.importRows(VICE, new ImportRequest(List.of(first, second), 1L));

            assertThat(result).isEqualTo(new ImportResult(2, List.of(201L, 202L)));
            ArgumentCaptor<EnrollChildRequest> scoped = ArgumentCaptor.forClass(EnrollChildRequest.class);
            InOrder order = inOrder(childIntakeService);
            order.verify(childIntakeService, times(2)).prepareEnrollment(eq(VICE), scoped.capture());
            order.verify(childIntakeService).createEnrolledChild(VICE, draftA, Map.of());
            order.verify(childIntakeService).createEnrolledChild(VICE, draftB, Map.of());
            assertThat(scoped.getAllValues())
                    .containsExactly(
                            new EnrollChildRequest(first.child(), first.declaration(), null, 1L),
                            new EnrollChildRequest(second.child(), second.declaration(), null, 1L));
        }

        @Test
        @DisplayName("Không có dòng ⇒ Mỗi lần nhập từ 1 đến 500 trẻ")
        void emptyRequestIsRejected() {
            assertFileError(() -> service.importRows(VICE, null), "Mỗi lần nhập từ 1 đến 500 trẻ");
            assertFileError(
                    () -> service.importRows(VICE, new ImportRequest(null, 1L)), "Mỗi lần nhập từ 1 đến 500 trẻ");
            assertFileError(
                    () -> service.importRows(VICE, new ImportRequest(List.of(), 1L)), "Mỗi lần nhập từ 1 đến 500 trẻ");
            verifyNoInteractions(childIntakeService);
        }

        @Test
        @DisplayName("Quá 500 dòng ⇒ từ chối, không kiểm dòng nào")
        void moreThan500RowsIsRejected() {
            EnrollChildRequest row = request("Bé An giả", MOTHER_ID, "0900000001", null);

            assertFileError(
                    () -> service.importRows(VICE, new ImportRequest(Collections.nCopies(501, row), 1L)),
                    "Mỗi lần nhập từ 1 đến 500 trẻ");
            verifyNoInteractions(childIntakeService);
        }

        @Test
        @DisplayName("Dòng null ⇒ Dòng dữ liệu không hợp lệ, không ghi gì")
        void nullRowIsRejected() {
            List<EnrollChildRequest> rows = new ArrayList<>();
            rows.add(null);

            assertFileError(() -> service.importRows(VICE, new ImportRequest(rows, 1L)), "Dòng dữ liệu không hợp lệ");
            verifyNoInteractions(childIntakeService);
        }

        @Test
        @DisplayName("G94-180/CHD-04: hai dòng cùng trẻ ⇒ lỗi trùng trong file, không ghi hồ sơ nào")
        void duplicateInBatchStopsImport() {
            EnrollChildRequest row = request("Bé An giả", MOTHER_ID, "0900000001", null);
            given(childIntakeService.prepareEnrollment(eq(VICE), any())).willReturn(draft("Bé An giả"));

            assertFileError(
                    () -> service.importRows(VICE, new ImportRequest(List.of(row, row), 1L)), IN_FILE_DUPLICATE);
            verify(childIntakeService, never()).createEnrolledChild(any(), any(), anyMap());
        }

        @Test
        @DisplayName("AUTH-18: CCCD phụ huynh gắn hai SĐT giữa các dòng ⇒ lỗi không nhất quán, không ghi hồ sơ nào")
        void inconsistentGuardianStopsImport() {
            EnrollChildRequest first = request("Bé An giả", MOTHER_ID, "0900000001", null);
            EnrollChildRequest second = request("Bé Bình giả", MOTHER_ID, "0900000003", null);
            given(childIntakeService.prepareEnrollment(eq(VICE), any())).willReturn(draft("Bé An giả"));

            assertFileError(
                    () -> service.importRows(VICE, new ImportRequest(List.of(first, second), 1L)), INCONSISTENT);
            verify(childIntakeService, never()).createEnrolledChild(any(), any(), anyMap());
        }

        @Test
        @DisplayName("Lỗi kiểm form của một dòng (vd. trùng hồ sơ CHD-04) ⇒ ném ra, không ghi hồ sơ nào")
        void prepareErrorStopsImport() {
            EnrollChildRequest first = request("Bé An giả", MOTHER_ID, "0900000001", null);
            EnrollChildRequest second = request("Bé Bình giả", FATHER_ID, "0900000002", null);
            given(childIntakeService.prepareEnrollment(eq(VICE), any()))
                    .willReturn(draft("Bé An giả"))
                    .willThrow(GlobalException.of(ApiCode.CHILD_DUPLICATE));

            assertThatThrownBy(() -> service.importRows(VICE, new ImportRequest(List.of(first, second), 1L)))
                    .isInstanceOf(GlobalException.class)
                    .extracting(ex -> ((GlobalException) ex).getApiCode())
                    .isEqualTo(ApiCode.CHILD_DUPLICATE);
            verify(childIntakeService, never()).createEnrolledChild(any(), any(), anyMap());
        }
    }

    private static Map<String, String> validRow() {
        Map<String, String> row = new LinkedHashMap<>();
        HEADER.forEach(h -> row.put(h, ""));
        row.put("Họ tên trẻ", "Bé An giả");
        row.put("Ngày sinh (dd/mm/yyyy)", "15/03/2022");
        row.put("Giới tính (Nam/Nữ)", "Nam");
        row.put("Họ tên phụ huynh", "Mẹ giả");
        row.put("Quan hệ (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ)", "Mẹ");
        row.put("Số điện thoại", "0900000001");
        row.put("Số CCCD phụ huynh", MOTHER_ID);
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

    private static Map<String, String> withSecondGuardian(Map<String, String> row) {
        row.put("Họ tên phụ huynh 2", "Bố giả");
        row.put("Quan hệ phụ huynh 2", "Bố");
        row.put("Số điện thoại phụ huynh 2", "0900000002");
        row.put("Số CCCD phụ huynh 2", FATHER_ID);
        return row;
    }

    @SafeVarargs
    private static MockMultipartFile csv(Map<String, String>... rows) {
        List<List<String>> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Map<String, String> row : rows) {
            lines.add(HEADER.stream().map(row::get).toList());
        }
        return file(lines);
    }

    private static MockMultipartFile file(List<List<String>> lines) {
        String text = lines.stream()
                .map(line -> line.stream()
                        .map(v -> "\"" + v.replace("\"", "\"\"") + "\"")
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
        return new MockMultipartFile("file", "tre.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }

    private static EnrollChildRequest request(String childName, String citizenId, String phone, Long classId) {
        return new EnrollChildRequest(
                new EnrollmentChildInput(
                        childName,
                        "2022-03-15",
                        "MALE",
                        List.of(new GuardianInput("Phụ huynh giả", "MOTHER", phone, citizenId, null)),
                        null),
                null,
                classId,
                null);
    }

    private static EnrollmentDraft draft(String childName) {
        return new EnrollmentDraft(childName, LocalDate.of(2022, 3, 15), Gender.MALE, List.of(), null, null, null, 1L);
    }

    private static void assertFileError(ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOf(GlobalException.class).satisfies(ex -> {
            GlobalException error = (GlobalException) ex;
            assertThat(error.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
            assertThat(error.getFieldErrors()).containsExactly(new FieldErrorResponse("file", message));
        });
    }
}
