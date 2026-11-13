package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentChildInput;
import com.carenest.dto.child.GuardianCheckRequest;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.ChildEnrollmentService;
import com.carenest.service.child.ChildEnrollmentImportService;
import com.carenest.service.child.ChildEnrollmentImportService.ImportRequest;
import com.carenest.service.child.ChildEnrollmentImportService.ImportResult;
import com.carenest.service.child.ChildEnrollmentImportService.Preview;
import com.carenest.service.child.ChildEnrollmentImportService.PreviewRow;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit test service bằng Mockito (không DB): nhập trẻ từ Excel kèm kiểm tài khoản phụ huynh. Dữ liệu giả. */
@ExtendWith(MockitoExtension.class)
class EnrollmentImportAccountServiceImplTest {

    private static final String MOTHER_ID = "001200000001";
    private static final String FATHER_ID = "001200000002";
    private static final CurrentUser VICE = new CurrentUser(
            3L,
            "kc-3",
            "vp",
            "PHT giả",
            null,
            Set.of(CareNestRole.VICE_PRINCIPAL),
            Set.of(Permission.CHILD_ENROLL, Permission.PARENT_ACCOUNT_MANAGE));
    private static final MockMultipartFile FILE = new MockMultipartFile("file", "tre.csv", "text/csv", new byte[] {1});

    @Mock
    private ChildEnrollmentImportService profiles;

    @Mock
    private ChildEnrollmentService accounts;

    @Mock
    private UserAccountRepository userAccountRepository;

    private EnrollmentImportAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentImportAccountServiceImpl(profiles, accounts, userAccountRepository);
    }

    @Nested
    @DisplayName("preview")
    class PreviewFile {

        @Test
        @DisplayName("G94-180/AUTH-18: phụ huynh trùng CCCD tài khoản ⇒ ghi chú tài khoản đứng trước ghi chú hồ sơ")
        void notesExistingAccountBeforeProfileNotes() {
            PreviewRow row = row(2, List.of(), List.of("Ghi chú hồ sơ"), guardian("Mẹ giả", "0900000001", MOTHER_ID));
            givenPreview(row);
            given(userAccountRepository.findAllByCitizenIdIn(Set.of(MOTHER_ID)))
                    .willReturn(List.of(parent(60L, "Mẹ giả", "0900000001", MOTHER_ID)));

            Preview preview = service.preview(VICE, FILE);

            assertThat(preview.supported()).isTrue();
            assertThat(preview.fileName()).isEqualTo("tre.csv");
            PreviewRow result = preview.rows().getFirst();
            assertThat(result.notes())
                    .containsExactly(
                            "Phụ huynh đã có tài khoản (trùng CCCD): Mẹ giả · 0900000001. Dùng thông tin tài khoản, chỉ"
                                    + " liên kết người được chọn khi kích hoạt sau khi nhập.",
                            "Ghi chú hồ sơ");
            assertThat(result.errors()).isEmpty();
            assertThat(result.rowNo()).isEqualTo(2);
            assertThat(result.raw()).isSameAs(row.raw());
            assertThat(result.payload()).isSameAs(row.payload());
            verify(accounts)
                    .checkGuardians(
                            VICE, new GuardianCheckRequest(row.payload().child().guardians()));
        }

        @Test
        @DisplayName("CCCD có khoảng trắng vẫn khớp tài khoản; hai dòng cùng phụ huynh chỉ tra DB một lần")
        void normalizesCitizenIdAndQueriesOnce() {
            PreviewRow first = row(2, List.of(), List.of(), guardian("Mẹ giả", "0900000001", "0012 0000 0001"));
            PreviewRow second = row(
                    3,
                    List.of(),
                    List.of(),
                    guardian("Mẹ giả", "0900000001", MOTHER_ID),
                    guardian("Bố giả", "0900000002", FATHER_ID));
            givenPreview(first, second);
            given(userAccountRepository.findAllByCitizenIdIn(Set.of(MOTHER_ID, FATHER_ID)))
                    .willReturn(List.of(parent(60L, "Mẹ giả", "0900000001", MOTHER_ID)));

            Preview preview = service.preview(VICE, FILE);

            assertThat(preview.rows()).allSatisfy(r -> assertThat(r.notes()).hasSize(1));
            verify(userAccountRepository).findAllByCitizenIdIn(Set.of(MOTHER_ID, FATHER_ID));
        }

        @Test
        @DisplayName("Không có CCCD hợp lệ nào trong file ⇒ không tra tài khoản, không ghi chú")
        void noValidCitizenIdSkipsAccountLookup() {
            givenPreview(
                    row(2, List.of("Số CCCD gồm đúng 12 chữ số"), List.of(), guardian("Mẹ giả", "0900000001", "12")));

            Preview preview = service.preview(VICE, FILE);

            assertThat(preview.rows().getFirst().notes()).isEmpty();
            assertThat(preview.rows().getFirst().errors()).containsExactly("Số CCCD gồm đúng 12 chữ số");
            verifyNoInteractions(userAccountRepository);
        }

        @Test
        @DisplayName(
                "AUTH-18: trùng danh tính phụ huynh ⇒ lỗi theo ô đưa vào lỗi của dòng (bỏ trùng), dòng khác vẫn xem trước")
        void identityConflictBecomesRowError() {
            PreviewRow conflicted = row(
                    2,
                    List.of(ChildEnrollmentServiceImpl.CITIZEN_ID_TAKEN),
                    List.of(),
                    guardian("Mẹ giả", "0900000001", MOTHER_ID));
            PreviewRow clean = row(3, List.of(), List.of(), guardian("Bố giả", "0900000002", FATHER_ID));
            givenPreview(conflicted, clean);
            willThrow(GlobalException.withFieldErrors(
                            ApiCode.GUARDIAN_IDENTITY_CONFLICT,
                            List.of(
                                    new FieldErrorResponse(
                                            "guardians.0.idNumber", ChildEnrollmentServiceImpl.CITIZEN_ID_TAKEN),
                                    new FieldErrorResponse(
                                            "guardians.0.phone", ChildEnrollmentServiceImpl.PHONE_HAS_OTHER_ID))))
                    .given(accounts)
                    .checkGuardians(
                            VICE,
                            new GuardianCheckRequest(
                                    conflicted.payload().child().guardians()));
            willDoNothing()
                    .given(accounts)
                    .checkGuardians(
                            VICE,
                            new GuardianCheckRequest(clean.payload().child().guardians()));

            Preview preview = service.preview(VICE, FILE);

            assertThat(preview.rows().getFirst().errors())
                    .containsExactly(
                            ChildEnrollmentServiceImpl.CITIZEN_ID_TAKEN, ChildEnrollmentServiceImpl.PHONE_HAS_OTHER_ID);
            assertThat(preview.rows().get(1).errors()).isEmpty();
        }

        @Test
        @DisplayName("Quá 3 phụ huynh (INVALID_REQUEST_DATA) ⇒ lỗi của dòng; giữ selectionError của dòng")
        void tooManyGuardiansBecomesRowError() {
            PreviewRow row = new PreviewRow(
                    2,
                    Map.of(),
                    request(guardian("Mẹ giả", "0900000001", MOTHER_ID)),
                    List.of(),
                    "Chọn đúng một phụ huynh nhận tài khoản",
                    List.of());
            givenPreview(row);
            willThrow(GlobalException.withFieldErrors(
                            ApiCode.INVALID_REQUEST_DATA,
                            List.of(new FieldErrorResponse("guardians", "Mỗi trẻ tối đa 3 phụ huynh"))))
                    .given(accounts)
                    .checkGuardians(any(), any());

            PreviewRow result = service.preview(VICE, FILE).rows().getFirst();

            assertThat(result.errors()).containsExactly("Mỗi trẻ tối đa 3 phụ huynh");
            assertThat(result.selectionError()).isEqualTo("Chọn đúng một phụ huynh nhận tài khoản");
        }

        @Test
        @DisplayName("Lỗi khác (vd. không có quyền) ⇒ ném ra, không nuốt vào dòng")
        void otherErrorsPropagate() {
            givenPreview(row(2, List.of(), List.of(), guardian("Mẹ giả", "0900000001", MOTHER_ID)));
            willThrow(GlobalException.of(ApiCode.ACCOUNT_MANAGE_DENIED))
                    .given(accounts)
                    .checkGuardians(any(), any());

            assertThatThrownBy(() -> service.preview(VICE, FILE))
                    .isInstanceOf(GlobalException.class)
                    .extracting(ex -> ((GlobalException) ex).getApiCode())
                    .isEqualTo(ApiCode.ACCOUNT_MANAGE_DENIED);
        }

        @Test
        @DisplayName("File không đọc được (lỗi của bước đọc hồ sơ) ⇒ ném ra, không kiểm tài khoản")
        void unreadableFilePropagates() {
            given(profiles.preview(VICE, FILE))
                    .willThrow(GlobalException.withFieldErrors(
                            ApiCode.INVALID_REQUEST_DATA,
                            List.of(new FieldErrorResponse("file", "File không có dữ liệu"))));

            assertThatThrownBy(() -> service.preview(VICE, FILE)).isInstanceOf(GlobalException.class);
            verifyNoInteractions(accounts, userAccountRepository);
        }
    }

    @Nested
    @DisplayName("importRows")
    class ImportRows {

        @Test
        @DisplayName(
                "G94-180/AUTH-20: kiểm danh tính phụ huynh mọi dòng trước, rồi mới ghi hồ sơ (kích hoạt tài khoản sau)")
        void checksEveryRowBeforeImport() {
            EnrollChildRequest first = request(guardian("Mẹ giả", "0900000001", MOTHER_ID));
            EnrollChildRequest second = request(guardian("Bố giả", "0900000002", FATHER_ID));
            ImportRequest request = new ImportRequest(List.of(first, second), 1L);
            ImportResult created = new ImportResult(2, List.of(101L, 102L));
            given(profiles.importRows(VICE, request)).willReturn(created);

            ImportResult result = service.importRows(VICE, request);

            assertThat(result).isSameAs(created);
            InOrder order = inOrder(accounts, profiles);
            order.verify(accounts)
                    .checkGuardians(VICE, new GuardianCheckRequest(first.child().guardians()));
            order.verify(accounts)
                    .checkGuardians(
                            VICE, new GuardianCheckRequest(second.child().guardians()));
            order.verify(profiles).importRows(VICE, request);
        }

        @Test
        @DisplayName("AUTH-18: một dòng trùng danh tính ⇒ dừng cả lô, không ghi hồ sơ nào")
        void conflictStopsWholeBatch() {
            EnrollChildRequest first = request(guardian("Mẹ giả", "0900000001", MOTHER_ID));
            ImportRequest request = new ImportRequest(List.of(first), 1L);
            willThrow(GlobalException.withFieldErrors(
                            ApiCode.GUARDIAN_IDENTITY_CONFLICT,
                            List.of(new FieldErrorResponse(
                                    "guardians.0.idNumber", ChildEnrollmentServiceImpl.CITIZEN_ID_TAKEN))))
                    .given(accounts)
                    .checkGuardians(VICE, new GuardianCheckRequest(first.child().guardians()));

            assertThatThrownBy(() -> service.importRows(VICE, request))
                    .isInstanceOf(GlobalException.class)
                    .extracting(ex -> ((GlobalException) ex).getApiCode())
                    .isEqualTo(ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            verify(profiles, never()).importRows(any(), any());
        }

        @Test
        @DisplayName("Dòng null hoặc thiếu thông tin trẻ ⇒ bỏ qua bước kiểm tài khoản, để bước ghi hồ sơ báo lỗi")
        void nullRowsAreLeftToProfileImport() {
            List<EnrollChildRequest> rows = new ArrayList<>();
            rows.add(null);
            rows.add(new EnrollChildRequest(null, null, null, null));
            ImportRequest request = new ImportRequest(rows, 1L);
            willThrow(GlobalException.withFieldErrors(
                            ApiCode.INVALID_REQUEST_DATA,
                            List.of(new FieldErrorResponse("file", "Dòng dữ liệu không hợp lệ"))))
                    .given(profiles)
                    .importRows(VICE, request);

            assertThatThrownBy(() -> service.importRows(VICE, request)).isInstanceOf(GlobalException.class);
            verifyNoInteractions(accounts);
        }

        @Test
        @DisplayName("Quá 500 dòng hoặc request rỗng ⇒ không kiểm tài khoản, để bước ghi hồ sơ từ chối")
        void oversizedOrMissingRequestSkipsAccountCheck() {
            EnrollChildRequest row = request(guardian("Mẹ giả", "0900000001", MOTHER_ID));
            ImportRequest oversized = new ImportRequest(Collections.nCopies(501, row), 1L);
            GlobalException tooMany = GlobalException.withFieldErrors(
                    ApiCode.INVALID_REQUEST_DATA,
                    List.of(new FieldErrorResponse("file", "Mỗi lần nhập từ 1 đến 500 trẻ")));
            given(profiles.importRows(any(), any())).willThrow(tooMany);

            assertThatThrownBy(() -> service.importRows(VICE, oversized)).isSameAs(tooMany);
            assertThatThrownBy(() -> service.importRows(VICE, null)).isSameAs(tooMany);
            verifyNoInteractions(accounts);
        }
    }

    private void givenPreview(PreviewRow... rows) {
        given(profiles.preview(VICE, FILE)).willReturn(new Preview(true, "tre.csv", List.of(rows)));
    }

    private static PreviewRow row(int rowNo, List<String> errors, List<String> notes, GuardianInput... guardians) {
        return new PreviewRow(rowNo, Map.of("fullName", "Bé giả"), request(guardians), errors, null, notes);
    }

    private static EnrollChildRequest request(GuardianInput... guardians) {
        return new EnrollChildRequest(
                new EnrollmentChildInput("Bé giả", "2022-03-15", "MALE", List.of(guardians), null), null, null, null);
    }

    private static GuardianInput guardian(String name, String phone, String idNumber) {
        return new GuardianInput(name, "MOTHER", phone, idNumber, null);
    }

    private static UserAccount parent(long id, String fullName, String phone, String citizenId) {
        UserAccount account = UserAccount.createParent("kc-" + id, phone, null, fullName, citizenId);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
