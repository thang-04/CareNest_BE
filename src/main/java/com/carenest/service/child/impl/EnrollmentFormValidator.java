package com.carenest.service.child.impl;

import com.carenest.dto.child.AllergiesInput;
import com.carenest.dto.child.ChildAddressInput;
import com.carenest.dto.child.ChildProfileDraft;
import com.carenest.dto.child.ChildProfileInput;
import com.carenest.dto.child.DeclarationAnswerInput;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentChildInput;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.GuardianDraft;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.HealthDeclarationDraft;
import com.carenest.dto.child.HealthDeclarationInput;
import com.carenest.dto.child.MedicalHistoryInput;
import com.carenest.dto.child.TbContactInput;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.utils.CitizenIds;
import com.carenest.utils.PhoneNumbers;
import com.carenest.utils.enums.DeclarationState;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.YesNo;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Kiểm form tiếp nhận theo từng ô (C-5: 400 + {@code [{ field, message }]}), câu lỗi và đường dẫn ô như BE giả của FE
 * ({@code FE:src/utils/children/childrenValidation.js}). Không đọc DB: lớp, điểm trường, trùng hồ sơ, trùng phụ huynh do
 * service kiểm sau. Câu lỗi không chứa dữ liệu người dùng nhập.
 */
final class EnrollmentFormValidator {

    static final int MAX_GUARDIANS = 3;
    static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(2018, 1, 1);

    // Tài khoản phụ huynh dùng 6 loại quan hệ (D-5a B); OTHER chỉ còn ở dữ liệu cũ
    static final Set<GuardianRelationship> RELATIONSHIPS = EnumSet.of(
            GuardianRelationship.FATHER,
            GuardianRelationship.MOTHER,
            GuardianRelationship.GRANDFATHER,
            GuardianRelationship.GRANDMOTHER,
            GuardianRelationship.SIBLING,
            GuardianRelationship.GUARDIAN);
    private static final Set<DeclarationState> YES_NO_STATES =
            EnumSet.of(DeclarationState.REPORTED, DeclarationState.NONE_REPORTED);

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");
    private static final Pattern PASSPORT = Pattern.compile("^[A-Za-z0-9]{6,12}$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

    private EnrollmentFormValidator() {}

    /** errors rỗng ⇒ draft có giá trị (chưa có lớp, điểm trường). */
    record Result(List<FieldErrorResponse> errors, EnrollmentDraft draft) {}

    static Result validate(EnrollChildRequest request, LocalDate today) {
        Map<String, String> errors = new LinkedHashMap<>();
        EnrollmentChildInput child = request == null ? null : request.child();
        if (child == null) {
            child = new EnrollmentChildInput(null, null, null, List.of(), null);
        }

        String fullName = clean(child.fullName());
        if (fullName == null) {
            errors.put("fullName", "Nhập họ tên trẻ");
        } else if (fullName.length() > 100) {
            errors.put("fullName", "Họ tên tối đa 100 ký tự");
        }
        LocalDate dateOfBirth = parseDate(child.dateOfBirth());
        if (dateOfBirth == null) {
            errors.put("dateOfBirth", "Chọn ngày sinh của trẻ");
        } else if (dateOfBirth.isAfter(today)) {
            errors.put("dateOfBirth", "Ngày sinh không được ở tương lai");
        } else if (dateOfBirth.isBefore(EARLIEST_BIRTH_DATE)) {
            errors.put("dateOfBirth", "Nhập ngày sinh hợp lệ cho trẻ mầm non");
        }
        Gender gender = parseEnum(Gender.class, child.gender());
        if (gender == null) {
            errors.put("gender", "Chọn giới tính");
        }

        List<GuardianDraft> guardians = validateGuardians(child.guardians(), errors);
        ChildProfileDraft profile = validateProfile(child.profile(), errors);
        HealthDeclarationDraft declaration =
                validateDeclaration(request == null ? null : request.declaration(), dateOfBirth, today, errors);

        List<FieldErrorResponse> list = errors.entrySet().stream()
                .map(e -> new FieldErrorResponse(e.getKey(), e.getValue()))
                .toList();
        if (!list.isEmpty()) {
            return new Result(list, null);
        }
        return new Result(
                List.of(),
                new EnrollmentDraft(fullName, dateOfBirth, gender, guardians, profile, declaration, null, null));
    }

    /**
     * Khóa so họ tên khi kiểm trùng (CHD-04): giữ dấu, không phân biệt hoa thường và khoảng trắng thừa ("Minh Anh"
     * khác "Minh Ánh").
     */
    static String nameKey(String name) {
        String cleaned = clean(name);
        return cleaned == null ? "" : cleaned.toLowerCase(VIETNAMESE);
    }

    private static List<GuardianDraft> validateGuardians(List<GuardianInput> inputs, Map<String, String> errors) {
        List<GuardianInput> guardians = inputs == null ? List.of() : inputs;
        List<GuardianDraft> drafts = new ArrayList<>();
        for (int i = 0; i < guardians.size(); i++) {
            GuardianInput g =
                    guardians.get(i) == null ? new GuardianInput(null, null, null, null, null) : guardians.get(i);
            String prefix = "guardians." + i + ".";
            String name = clean(g.fullName());
            if (name == null) {
                errors.put(prefix + "fullName", "Nhập họ tên phụ huynh");
            } else if (name.length() > 150) {
                errors.put(prefix + "fullName", "Họ tên tối đa 150 ký tự");
            }
            GuardianRelationship relationship = parseEnum(GuardianRelationship.class, g.relation());
            if (isBlank(g.relation())) {
                errors.put(prefix + "relation", "Chọn quan hệ với trẻ");
            } else if (relationship == null || !RELATIONSHIPS.contains(relationship)) {
                errors.put(prefix + "relation", "Quan hệ chỉ nhận: Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ");
            }
            Optional<String> phone = PhoneNumbers.normalize(g.phone());
            if (isBlank(g.phone())) {
                errors.put(prefix + "phone", "Nhập số điện thoại để gửi tài khoản");
            } else if (phone.isEmpty()) {
                errors.put(prefix + "phone", "Nhập số điện thoại hợp lệ, ví dụ 0912 345 678");
            }
            String email = clean(g.email());
            if (email != null && !EMAIL.matcher(email).matches()) {
                errors.put(prefix + "email", "Nhập email hợp lệ, ví dụ ten@example.com");
            } else if (email != null && email.length() > 255) {
                errors.put(prefix + "email", "Email tối đa 255 ký tự");
            }
            Optional<String> citizenId = CitizenIds.normalize(g.idNumber());
            if (isBlank(g.idNumber())) {
                errors.put(prefix + "idNumber", "Nhập số CCCD của phụ huynh");
            } else if (citizenId.isEmpty()) {
                errors.put(prefix + "idNumber", "Số CCCD gồm đúng 12 chữ số");
            }
            drafts.add(new GuardianDraft(
                    i,
                    name,
                    relationship,
                    phone.orElse(null),
                    citizenId.orElse(null),
                    email == null ? null : email.toLowerCase(Locale.ROOT)));
        }
        // Lỗi chung của danh sách: như BE giả, quá 3 người được báo trước, rồi trùng CCCD, rồi trùng SĐT
        if (guardians.isEmpty()) {
            errors.put("guardians", "Thêm ít nhất một phụ huynh");
        } else if (guardians.size() > MAX_GUARDIANS) {
            errors.put("guardians", "Mỗi trẻ tối đa " + MAX_GUARDIANS + " phụ huynh");
        } else if (hasDuplicate(drafts.stream().map(GuardianDraft::citizenId).toList())) {
            errors.put("guardians", "Hai phụ huynh không được dùng chung số CCCD");
        } else if (hasDuplicate(drafts.stream().map(GuardianDraft::phone).toList())) {
            errors.put("guardians", "Hai phụ huynh không được dùng chung số điện thoại");
        }
        return drafts;
    }

    private static ChildProfileDraft validateProfile(ChildProfileInput input, Map<String, String> errors) {
        ChildProfileInput p = input == null ? new ChildProfileInput(null, null, null, null, null, null, null) : input;
        ChildAddressInput address = p.address() == null ? new ChildAddressInput(null, null, null) : p.address();

        String idNumber =
                p.idNumber() == null ? null : WHITESPACE.matcher(p.idNumber()).replaceAll("");
        if (idNumber != null && idNumber.isEmpty()) {
            idNumber = null;
        }
        if (idNumber != null && !isValidChildIdNumber(idNumber)) {
            errors.put("profile.idNumber", "Nhập 12 chữ số (CCCD/mã định danh) hoặc số hộ chiếu 6–12 ký tự");
        }
        YesNo premature = parseEnum(YesNo.class, p.premature());
        if (premature == null) {
            errors.put("profile.premature", "Chọn trẻ có sinh non hay không");
        }
        Integer weeks = p.gestationWeeks();
        if (weeks == null) {
            if (premature == YesNo.YES) {
                errors.put("profile.gestationWeeks", "Nhập tuần thai khi sinh của trẻ sinh non");
            }
        } else if (weeks < 20 || weeks > 42) {
            errors.put("profile.gestationWeeks", "Nhập tuần thai từ 20 đến 42");
        }
        String ethnicity = required(p.ethnicity(), 100, "profile.ethnicity", "Nhập dân tộc", errors);
        String beneficiary = required(p.beneficiary(), 100, "profile.beneficiary", "Nhập đối tượng", errors);
        String bloodType = clean(p.bloodType());
        if (bloodType != null && bloodType.length() > 100) {
            errors.put("profile.bloodType", "Tối đa 100 ký tự");
        }
        String province =
                required(address.province(), 100, "profile.address.province", "Nhập tỉnh/thành nơi ở", errors);
        String ward = required(address.ward(), 100, "profile.address.ward", "Nhập phường/xã nơi ở", errors);
        String street = required(address.street(), 300, "profile.address.street", "Nhập số nhà/thôn/xóm nơi ở", errors);
        // Tuần thai chỉ ghi cho trẻ sinh non (ô chỉ hiện khi chọn Có)
        Short gestationWeeks = premature == YesNo.YES && weeks != null ? weeks.shortValue() : null;
        return new ChildProfileDraft(
                idNumber, premature, gestationWeeks, ethnicity, beneficiary, bloodType, province, ward, street);
    }

    private static HealthDeclarationDraft validateDeclaration(
            HealthDeclarationInput input, LocalDate dateOfBirth, LocalDate today, Map<String, String> errors) {
        if (input == null) {
            errors.put("declaration.allergies", "Khai báo sức khỏe còn thiếu");
            return null;
        }
        AllergiesInput allergies = input.allergies() == null ? new AllergiesInput(null, null) : input.allergies();
        DeclarationState allergiesState = parseEnum(DeclarationState.class, allergies.state());
        List<String> items =
                new ArrayList<>(new LinkedHashSet<>((allergies.items() == null ? List.<String>of() : allergies.items())
                        .stream()
                                .map(EnrollmentFormValidator::clean)
                                .filter(s -> s != null)
                                .toList()));
        if (allergiesState == null) {
            errors.put("declaration.allergies", "Chọn tình trạng dị ứng");
        } else if (allergiesState == DeclarationState.REPORTED && items.isEmpty()) {
            errors.put("declaration.allergies", "Nhập ít nhất một thực phẩm/chất gây dị ứng");
        } else if (allergiesState == DeclarationState.REPORTED && items.stream().anyMatch(s -> s.length() > 100)) {
            errors.put("declaration.allergies", "Tên chất gây dị ứng tối đa 100 ký tự");
        }

        HealthDeclarationDraft.Answer diet = answer(
                input.diet(),
                false,
                500,
                "declaration.diet",
                "Chọn chế độ ăn",
                "Mô tả chế độ ăn riêng của trẻ",
                "Nội dung tối đa 500 ký tự",
                errors);
        // Thông tin khác: BE giả không bắt chọn ⇒ để trống = Chưa cung cấp
        DeclarationAnswerInput otherNotesInput =
                input.otherNotes() == null || isBlank(input.otherNotes().state())
                        ? new DeclarationAnswerInput(DeclarationState.NOT_PROVIDED.name(), null)
                        : input.otherNotes();
        HealthDeclarationDraft.Answer otherNotes = answer(
                otherNotesInput,
                false,
                500,
                "declaration.otherNotes",
                "Chọn tình trạng thông tin sức khỏe khác",
                "Nhập thông tin sức khỏe khác",
                "Nội dung tối đa 500 ký tự",
                errors);

        MedicalHistoryInput history =
                input.medicalHistory() == null ? new MedicalHistoryInput(null, null, null) : input.medicalHistory();
        HealthDeclarationDraft.Answer self = answer(
                history.self(),
                true,
                300,
                "declaration.medicalHistory.self",
                "Chọn Có hoặc Không cho tiền sử bản thân",
                "Ghi rõ tên bệnh",
                "Tối đa 300 ký tự",
                errors);
        HealthDeclarationDraft.Answer family = answer(
                history.family(),
                true,
                300,
                "declaration.medicalHistory.family",
                "Chọn Có hoặc Không cho tiền sử gia đình",
                "Ghi rõ tên bệnh",
                "Tối đa 300 ký tự",
                errors);

        TbContactInput tb = history.tbContact() == null ? new TbContactInput(null, null) : history.tbContact();
        DeclarationState tbState = parseEnum(DeclarationState.class, tb.state());
        LocalDate tbDate = null;
        if (tbState == null || !YES_NO_STATES.contains(tbState)) {
            errors.put("declaration.medicalHistory.tbContact", "Tiếp xúc người bệnh lao chọn Có hoặc Không");
        } else if (tbState == DeclarationState.REPORTED) {
            tbDate = parseDate(tb.contactDate());
            if (tbDate == null) {
                errors.put("declaration.medicalHistory.tbContactDate", "Chọn ngày tiếp xúc gần nhất");
            } else if (tbDate.isAfter(today)) {
                errors.put("declaration.medicalHistory.tbContactDate", "Ngày tiếp xúc không được ở tương lai");
            } else if (dateOfBirth != null && tbDate.isBefore(dateOfBirth)) {
                errors.put(
                        "declaration.medicalHistory.tbContactDate", "Ngày tiếp xúc không được trước ngày sinh của trẻ");
            }
        }
        if (allergiesState != DeclarationState.REPORTED) {
            items = List.of();
        }
        return new HealthDeclarationDraft(allergiesState, items, diet, otherNotes, self, family, tbState, tbDate);
    }

    private static HealthDeclarationDraft.Answer answer(
            DeclarationAnswerInput input,
            boolean yesNoOnly,
            int maxLength,
            String field,
            String chooseMessage,
            String textMessage,
            String lengthMessage,
            Map<String, String> errors) {
        DeclarationAnswerInput a = input == null ? new DeclarationAnswerInput(null, null) : input;
        DeclarationState state = parseEnum(DeclarationState.class, a.state());
        String text = cleanText(a.text());
        if (state == null || (yesNoOnly && !YES_NO_STATES.contains(state))) {
            errors.put(field, chooseMessage);
        } else if (state == DeclarationState.REPORTED && text == null) {
            errors.put(field, textMessage);
        } else if (state == DeclarationState.REPORTED && text.length() > maxLength) {
            errors.put(field, lengthMessage);
        }
        return new HealthDeclarationDraft.Answer(state, state == DeclarationState.REPORTED ? text : null);
    }

    private static String required(
            String value, int maxLength, String field, String message, Map<String, String> errors) {
        String cleaned = clean(value);
        if (cleaned == null) {
            errors.put(field, message);
        } else if (cleaned.length() > maxLength) {
            errors.put(field, "Tối đa " + maxLength + " ký tự");
        }
        return cleaned;
    }

    /** CCCD/mã định danh 12 chữ số, hoặc số hộ chiếu 6–12 chữ và số (CHD-05). */
    private static boolean isValidChildIdNumber(String value) {
        return DIGITS.matcher(value).matches()
                ? value.length() == 12
                : PASSPORT.matcher(value).matches();
    }

    private static boolean hasDuplicate(List<String> values) {
        Set<String> seen = new HashSet<>();
        return values.stream().filter(v -> v != null).anyMatch(v -> !seen.add(v));
    }

    /** NFC, bỏ khoảng trắng đầu cuối, gộp khoảng trắng giữa; rỗng ⇒ null. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = WHITESPACE
                .matcher(Normalizer.normalize(value, Normalizer.Form.NFC).trim())
                .replaceAll(" ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Nội dung tự do (chế độ ăn, tiền sử...): NFC, bỏ khoảng trắng đầu cuối, giữ xuống dòng; rỗng ⇒ null. */
    private static String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = Normalizer.normalize(value, Normalizer.Form.NFC).strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
