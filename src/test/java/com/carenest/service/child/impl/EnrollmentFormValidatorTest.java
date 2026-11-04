package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.child.AllergiesInput;
import com.carenest.dto.child.ChildAddressInput;
import com.carenest.dto.child.ChildProfileInput;
import com.carenest.dto.child.DeclarationAnswerInput;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentChildInput;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.HealthDeclarationInput;
import com.carenest.dto.child.MedicalHistoryInput;
import com.carenest.dto.child.TbContactInput;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.utils.enums.DeclarationState;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.YesNo;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnrollmentFormValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    @Test
    @DisplayName("CHD-08: form đủ thông tin ⇒ không lỗi, chuẩn hóa SĐT, CCCD, email, bỏ nội dung của mục không phải Có")
    void completeFormIsNormalized() {
        EnrollmentFormValidator.Result result = EnrollmentFormValidator.validate(
                request(
                        child(List.of(new GuardianInput(
                                "  Trần   Thị Mẹ ", "MOTHER", "+84 912.345-678", "0012 3456 7890", " Me@Example.VN "))),
                        declaration("NONE_REPORTED", List.of("tôm"), "NONE_REPORTED", "sẽ bỏ", "NONE_REPORTED", null)),
                TODAY);

        assertThat(result.errors()).isEmpty();
        EnrollmentDraft draft = result.draft();
        assertThat(draft.fullName()).isEqualTo("Nguyễn Minh An");
        assertThat(draft.gender()).isEqualTo(Gender.MALE);
        assertThat(draft.guardians()).singleElement().satisfies(g -> {
            assertThat(g.fullName()).isEqualTo("Trần Thị Mẹ");
            assertThat(g.relationship()).isEqualTo(GuardianRelationship.MOTHER);
            assertThat(g.phone()).isEqualTo("0912345678");
            assertThat(g.citizenId()).isEqualTo("001234567890");
            assertThat(g.email()).isEqualTo("me@example.vn");
        });
        assertThat(draft.profile().premature()).isEqualTo(YesNo.NO);
        assertThat(draft.profile().gestationWeeks()).isNull();
        assertThat(draft.declaration().allergies()).isEmpty();
        assertThat(draft.declaration().diet().text()).isNull();
        assertThat(draft.declaration().otherNotes().state()).isEqualTo(DeclarationState.NOT_PROVIDED);
    }

    @Test
    @DisplayName("C-5: thiếu thông tin ⇒ lỗi theo đường dẫn ô của FE (không tiền tố child.)")
    void missingFieldsUseFrontendPaths() {
        EnrollChildRequest empty = new EnrollChildRequest(
                new EnrollmentChildInput(
                        null, null, null, List.of(new GuardianInput(null, null, null, null, null)), null),
                new HealthDeclarationInput(null, null, null, null),
                null,
                null);

        Map<String, String> errors = errors(EnrollmentFormValidator.validate(empty, TODAY));

        assertThat(errors)
                .containsEntry("fullName", "Nhập họ tên trẻ")
                .containsEntry("dateOfBirth", "Chọn ngày sinh của trẻ")
                .containsEntry("gender", "Chọn giới tính")
                .containsEntry("guardians.0.fullName", "Nhập họ tên phụ huynh")
                .containsEntry("guardians.0.relation", "Chọn quan hệ với trẻ")
                .containsEntry("guardians.0.phone", "Nhập số điện thoại để gửi tài khoản")
                .containsEntry("guardians.0.idNumber", "Nhập số CCCD của phụ huynh")
                .containsEntry("profile.premature", "Chọn trẻ có sinh non hay không")
                .containsEntry("profile.ethnicity", "Nhập dân tộc")
                .containsEntry("profile.beneficiary", "Nhập đối tượng")
                .containsEntry("profile.address.province", "Nhập tỉnh/thành nơi ở")
                .containsEntry("profile.address.ward", "Nhập phường/xã nơi ở")
                .containsEntry("profile.address.street", "Nhập số nhà/thôn/xóm nơi ở")
                .containsEntry("declaration.allergies", "Chọn tình trạng dị ứng")
                .containsEntry("declaration.diet", "Chọn chế độ ăn")
                .containsEntry("declaration.medicalHistory.self", "Chọn Có hoặc Không cho tiền sử bản thân")
                .containsEntry("declaration.medicalHistory.family", "Chọn Có hoặc Không cho tiền sử gia đình")
                .containsEntry("declaration.medicalHistory.tbContact", "Tiếp xúc người bệnh lao chọn Có hoặc Không")
                .doesNotContainKey("declaration.otherNotes");
    }

    @Test
    @DisplayName("AUTH-18: tối đa 3 phụ huynh, không chung CCCD / SĐT, quan hệ chỉ 6 loại (không nhận OTHER)")
    void guardianListRules() {
        GuardianInput mother = new GuardianInput("Mẹ", "MOTHER", "0912345678", "001234567890", null);
        GuardianInput other = new GuardianInput("Cô", "OTHER", "0912345679", "001234567891", "không-phải-email");

        assertThat(errors(EnrollmentFormValidator.validate(
                        request(child(List.of(mother, mother, mother, mother)), okDeclaration()), TODAY)))
                .containsEntry("guardians", "Mỗi trẻ tối đa 3 phụ huynh");
        assertThat(errors(EnrollmentFormValidator.validate(
                        request(
                                child(List.of(
                                        mother, new GuardianInput("Bố", "FATHER", "0987654321", "001234567890", null))),
                                okDeclaration()),
                        TODAY)))
                .containsEntry("guardians", "Hai phụ huynh không được dùng chung số CCCD");
        assertThat(errors(EnrollmentFormValidator.validate(
                        request(
                                child(List.of(
                                        mother,
                                        new GuardianInput("Bố", "FATHER", "+84912345678", "009876543210", null))),
                                okDeclaration()),
                        TODAY)))
                .containsEntry("guardians", "Hai phụ huynh không được dùng chung số điện thoại");
        assertThat(errors(EnrollmentFormValidator.validate(request(child(List.of(other)), okDeclaration()), TODAY)))
                .containsEntry("guardians.0.relation", "Quan hệ chỉ nhận: Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ")
                .containsEntry("guardians.0.email", "Nhập email hợp lệ, ví dụ ten@example.com");
        assertThat(errors(EnrollmentFormValidator.validate(
                        request(
                                child(List.of(new GuardianInput("Mẹ", "MOTHER", "123", "12345", null))),
                                okDeclaration()),
                        TODAY)))
                .containsEntry("guardians.0.phone", "Nhập số điện thoại hợp lệ, ví dụ 0912 345 678")
                .containsEntry("guardians.0.idNumber", "Số CCCD gồm đúng 12 chữ số");
    }

    @Test
    @DisplayName("CHD-06: mục Có phải có nội dung; ngày tiếp xúc lao không ở tương lai, không trước ngày sinh")
    void declarationRules() {
        Map<String, String> reportedWithoutContent = errors(EnrollmentFormValidator.validate(
                request(
                        child(List.of(guardian())),
                        declaration("REPORTED", List.of(" "), "REPORTED", " ", "REPORTED", "2027-01-01")),
                TODAY));
        assertThat(reportedWithoutContent)
                .containsEntry("declaration.allergies", "Nhập ít nhất một thực phẩm/chất gây dị ứng")
                .containsEntry("declaration.diet", "Mô tả chế độ ăn riêng của trẻ")
                .containsEntry("declaration.medicalHistory.tbContactDate", "Ngày tiếp xúc không được ở tương lai");

        Map<String, String> beforeBirth = errors(EnrollmentFormValidator.validate(
                request(
                        child(List.of(guardian())),
                        declaration("NONE_REPORTED", List.of(), "NONE_REPORTED", null, "REPORTED", "2022-12-31")),
                TODAY));
        assertThat(beforeBirth)
                .containsEntry(
                        "declaration.medicalHistory.tbContactDate", "Ngày tiếp xúc không được trước ngày sinh của trẻ");

        EnrollmentFormValidator.Result reported = EnrollmentFormValidator.validate(
                request(
                        child(List.of(guardian())),
                        declaration(
                                "REPORTED",
                                List.of("tôm", " tôm ", "sữa"),
                                "REPORTED",
                                "Ăn chay",
                                "REPORTED",
                                "2026-10-01")),
                TODAY);
        assertThat(reported.errors()).isEmpty();
        assertThat(reported.draft().declaration().allergies()).containsExactly("tôm", "sữa");
        assertThat(reported.draft().declaration().diet().text()).isEqualTo("Ăn chay");
        assertThat(reported.draft().declaration().tbContactDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("CHD-05: trẻ sinh non phải có tuần thai 20–42; số định danh 12 số hoặc hộ chiếu 6–12 ký tự")
    void profileRules() {
        assertThat(errors(EnrollmentFormValidator.validate(
                        request(child(List.of(guardian()), profile("YES", null, "")), okDeclaration()), TODAY)))
                .containsEntry("profile.gestationWeeks", "Nhập tuần thai khi sinh của trẻ sinh non");
        assertThat(errors(EnrollmentFormValidator.validate(
                        request(child(List.of(guardian()), profile("YES", 45, "12345")), okDeclaration()), TODAY)))
                .containsEntry("profile.gestationWeeks", "Nhập tuần thai từ 20 đến 42")
                .containsEntry("profile.idNumber", "Nhập 12 chữ số (CCCD/mã định danh) hoặc số hộ chiếu 6–12 ký tự");

        EnrollmentFormValidator.Result passport = EnrollmentFormValidator.validate(
                request(child(List.of(guardian()), profile("YES", 34, "B1234567")), okDeclaration()), TODAY);
        assertThat(passport.errors()).isEmpty();
        assertThat(passport.draft().profile().gestationWeeks()).isEqualTo((short) 34);
        assertThat(passport.draft().profile().idNumber()).isEqualTo("B1234567");
    }

    @Test
    @DisplayName("CHD-08: ngày sinh không ở tương lai, từ 2018-01-01")
    void birthDateRules() {
        assertThat(errors(EnrollmentFormValidator.validate(request(childBorn("2026-10-30"), okDeclaration()), TODAY)))
                .containsEntry("dateOfBirth", "Ngày sinh không được ở tương lai");
        assertThat(errors(EnrollmentFormValidator.validate(request(childBorn("2017-12-31"), okDeclaration()), TODAY)))
                .containsEntry("dateOfBirth", "Nhập ngày sinh hợp lệ cho trẻ mầm non");
        assertThat(errors(EnrollmentFormValidator.validate(request(childBorn("01/03/2023"), okDeclaration()), TODAY)))
                .containsEntry("dateOfBirth", "Chọn ngày sinh của trẻ");
    }

    @Test
    @DisplayName("CHD-04: so họ tên giữ dấu, không phân biệt hoa thường và khoảng trắng thừa")
    void nameKeyKeepsDiacritics() {
        assertThat(EnrollmentFormValidator.nameKey("  NGUYỄN   Minh  Anh ")).isEqualTo("nguyễn minh anh");
        assertThat(EnrollmentFormValidator.nameKey("Minh Anh"))
                .isNotEqualTo(EnrollmentFormValidator.nameKey("Minh Ánh"));
    }

    private static Map<String, String> errors(EnrollmentFormValidator.Result result) {
        return result.errors().stream()
                .collect(Collectors.toMap(FieldErrorResponse::field, FieldErrorResponse::message, (a, b) -> a));
    }

    private static EnrollChildRequest request(EnrollmentChildInput child, HealthDeclarationInput declaration) {
        return new EnrollChildRequest(child, declaration, null, 1L);
    }

    private static GuardianInput guardian() {
        return new GuardianInput("Trần Thị Mẹ", "MOTHER", "0912345678", "001234567890", null);
    }

    private static EnrollmentChildInput child(List<GuardianInput> guardians) {
        return child(guardians, profile("NO", null, null));
    }

    private static EnrollmentChildInput child(List<GuardianInput> guardians, ChildProfileInput profile) {
        return new EnrollmentChildInput(" Nguyễn  Minh An ", "2023-01-01", "MALE", guardians, profile);
    }

    private static EnrollmentChildInput childBorn(String dateOfBirth) {
        return new EnrollmentChildInput(
                "Nguyễn Minh An", dateOfBirth, "MALE", List.of(guardian()), profile("NO", null, null));
    }

    private static ChildProfileInput profile(String premature, Integer weeks, String idNumber) {
        return new ChildProfileInput(
                idNumber,
                premature,
                weeks,
                "Kinh",
                "Không",
                null,
                new ChildAddressInput("Hà Nội", "Cầu Giấy", "1 Láng"));
    }

    private static HealthDeclarationInput okDeclaration() {
        return declaration("NONE_REPORTED", List.of(), "NONE_REPORTED", null, "NONE_REPORTED", null);
    }

    private static HealthDeclarationInput declaration(
            String allergyState,
            List<String> allergies,
            String dietState,
            String dietText,
            String tbState,
            String tbDate) {
        return new HealthDeclarationInput(
                new AllergiesInput(allergyState, allergies),
                new DeclarationAnswerInput(dietState, dietText),
                null,
                new MedicalHistoryInput(
                        new DeclarationAnswerInput("NONE_REPORTED", null),
                        new DeclarationAnswerInput("NONE_REPORTED", null),
                        new TbContactInput(tbState, tbDate)));
    }
}
