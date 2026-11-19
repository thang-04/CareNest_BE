package com.carenest.service.child.impl;

import com.carenest.dto.child.*;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildEnrollmentImportService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.DuplicateChildKind;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChildEnrollmentImportServiceImpl implements ChildEnrollmentImportService {
    private static final String LOG_PREFIX = "[ChildEnrollmentImportServiceImpl]";
    private final ChildIntakeService childIntakeService;
    private final Clock clock;

    private record Column(String key, String label, boolean required) {}

    private static final List<Column> COLUMNS = List.of(
            new Column("fullName", "Họ tên trẻ", true),
            new Column("dateOfBirth", "Ngày sinh (dd/mm/yyyy)", true),
            new Column("gender", "Giới tính (Nam/Nữ)", true),
            new Column("guardianName", "Họ tên phụ huynh", true),
            new Column("relation", "Quan hệ (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ)", true),
            new Column("phone", "Số điện thoại", true),
            new Column("guardianIdNumber", "Số CCCD phụ huynh", true),
            new Column("email", "Email", false),
            new Column("allergies", "Dị ứng (để trống = chưa cung cấp, \"Không\" = không có)", false),
            new Column("diet", "Chế độ ăn (để trống = chưa cung cấp, \"Không\" = không có)", false),
            new Column("idNumber", "Số CCCD/mã định danh của trẻ", false),
            new Column("premature", "Sinh non (Có/Không)", true),
            new Column("gestationWeeks", "Tuần thai khi sinh (20–42)", true),
            new Column("ethnicity", "Dân tộc", true),
            new Column("beneficiary", "Đối tượng", true),
            new Column("bloodType", "Nhóm máu", false),
            new Column("province", "Nơi ở - Tỉnh/thành", true),
            new Column("ward", "Nơi ở - Phường/xã", true),
            new Column("street", "Nơi ở - Số nhà/thôn/xóm", true),
            new Column("selfHistory", "Tiền sử bản thân (\"Không\" hoặc tên bệnh)", true),
            new Column("familyHistory", "Tiền sử gia đình (\"Không\" hoặc tên bệnh)", true),
            new Column("tbContact", "Tiếp xúc người bệnh lao (Có/Không)", true),
            new Column("tbContactDate", "Ngày tiếp xúc người bệnh lao gần nhất (dd/mm/yyyy)", false),
            new Column("accountRecipientIdNumber", "CCCD phụ huynh nhận tài khoản", false),
            new Column("guardianName2", "Họ tên phụ huynh 2", false),
            new Column("relation2", "Quan hệ phụ huynh 2", false),
            new Column("phone2", "Số điện thoại phụ huynh 2", false),
            new Column("guardianIdNumber2", "Số CCCD phụ huynh 2", false),
            new Column("email2", "Email phụ huynh 2", false),
            new Column("guardianName3", "Họ tên phụ huynh 3", false),
            new Column("relation3", "Quan hệ phụ huynh 3", false),
            new Column("phone3", "Số điện thoại phụ huynh 3", false),
            new Column("guardianIdNumber3", "Số CCCD phụ huynh 3", false),
            new Column("email3", "Email phụ huynh 3", false));

    @Override
    public Preview preview(CurrentUser actor, MultipartFile file) {
        String logPrefix = LOG_PREFIX + "|preview|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            List<List<String>> table;
            try {
                table = EnrollmentTableReader.read(file);
            } catch (Exception ex) {
                throw invalid(
                        "file",
                        "Không đọc được file. Dùng CSV UTF-8, XLS hoặc XLSX theo mẫu, tối đa 10 MB và 500 trẻ.");
            }
            if (table.size() < 2) throw invalid("file", "File không có dữ liệu");
            List<String> keys = table.getFirst().stream()
                    .map(ChildEnrollmentImportServiceImpl::headerKey)
                    .toList();
            List<String> missing = COLUMNS.stream()
                    .filter(c -> c.required() && !keys.contains(c.key()))
                    .map(Column::label)
                    .toList();
            if (!missing.isEmpty()) throw invalid("file", "Thiếu cột: " + String.join(", ", missing));
            List<PreviewRow> result = new ArrayList<>();
            List<EnrollmentChildInput> children = new ArrayList<>();
            Map<String, String> ids = new HashMap<>();
            Map<String, String> phones = new HashMap<>();
            for (int i = 1; i < table.size(); i++) {
                Map<String, String> raw = new HashMap<>();
                for (int c = 0; c < keys.size(); c++)
                    if (keys.get(c) != null)
                        raw.put(
                                keys.get(c),
                                c < table.get(i).size() ? table.get(i).get(c).trim() : "");
                EnrollChildRequest payload = payload(raw);
                var validation = EnrollmentFormValidator.validate(payload, LocalDate.now(clock));
                List<String> errors = new ArrayList<>();
                String selectionError = null;
                for (var error : validation.errors()) {
                    if ("accountRecipient".equals(error.field())) selectionError = error.message();
                    else errors.add(error.message());
                }
                batchCheck(payload, children, ids, phones, errors);
                var duplicate = childIntakeService.checkDuplicate(
                        actor,
                        payload.child().fullName(),
                        payload.child().dateOfBirth(),
                        payload.child().guardians().stream()
                                .map(GuardianInput::idNumber)
                                .toList(),
                        null);
                // G94-180/CHD-04, như BE giả FE: trùng trẻ đang học/chờ xếp lớp chặn; trùng trẻ đã nghỉ chỉ gợi ý mở
                // lại
                // hồ sơ cũ (lúc lưu cũng chỉ chặn trẻ chưa nghỉ)
                List<String> notes = new ArrayList<>();
                if (duplicate != null && duplicate.kind() == DuplicateChildKind.LEFT) {
                    notes.add("Trẻ từng học ở trường ("
                            + (duplicate.code() == null ? "" : "hồ sơ cũ " + duplicate.code() + ", ")
                            + "đã nghỉ học): nên mở lại hồ sơ cũ ở Tiếp nhận trẻ thay vì nhập mới.");
                } else if (duplicate != null) {
                    errors.add("Trẻ đã có hồ sơ trong hệ thống (trùng họ tên, ngày sinh và phụ huynh)");
                }
                result.add(new PreviewRow(
                        i + 1, raw, payload, errors.stream().distinct().toList(), selectionError, List.copyOf(notes)));
            }
            log.info("{}|rows={}|END", logPrefix, result.size());
            return new Preview(true, file.getOriginalFilename(), result);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ImportResult importRows(CurrentUser actor, ImportRequest request) {
        String logPrefix = LOG_PREFIX + "|importRows|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            if (request == null
                    || request.rows() == null
                    || request.rows().isEmpty()
                    || request.rows().size() > 500) throw invalid("file", "Mỗi lần nhập từ 1 đến 500 trẻ");
            List<EnrollmentDraft> drafts = new ArrayList<>();
            List<EnrollmentChildInput> children = new ArrayList<>();
            Map<String, String> ids = new HashMap<>();
            Map<String, String> phones = new HashMap<>();
            for (EnrollChildRequest row : request.rows()) {
                if (row == null) throw invalid("file", "Dòng dữ liệu không hợp lệ");
                EnrollChildRequest scoped =
                        new EnrollChildRequest(row.child(), row.declaration(), null, request.campusId());
                EnrollmentDraft draft = childIntakeService.prepareEnrollment(actor, scoped);
                List<String> errors = new ArrayList<>();
                batchCheck(scoped, children, ids, phones, errors);
                if (!errors.isEmpty()) throw invalid("file", String.join("; ", errors));
                drafts.add(draft);
            }
            List<Long> idsCreated = drafts.stream()
                    .map(draft -> childIntakeService.createEnrolledChild(actor, draft, Map.of()))
                    .toList();
            log.info("{}|created={}|END", logPrefix, idsCreated.size());
            return new ImportResult(idsCreated.size(), idsCreated);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static void batchCheck(
            EnrollChildRequest row,
            List<EnrollmentChildInput> children,
            Map<String, String> ids,
            Map<String, String> phones,
            List<String> errors) {
        Set<String> guardianIds = row.child().guardians().stream()
                .map(g -> com.carenest.utils.CitizenIds.normalize(g.idNumber()).orElse(""))
                .filter(id -> !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        boolean duplicate = children.stream()
                .anyMatch(previous -> java.util.Objects.equals(
                                previous.dateOfBirth(), row.child().dateOfBirth())
                        && java.util.Objects.equals(
                                EnrollmentFormValidator.nameKey(previous.fullName()),
                                EnrollmentFormValidator.nameKey(row.child().fullName()))
                        && previous.guardians().stream()
                                .anyMatch(
                                        g -> guardianIds.contains(com.carenest.utils.CitizenIds.normalize(g.idNumber())
                                                .orElse(""))));
        if (duplicate) errors.add("Trùng trẻ trong file");
        children.add(row.child());
        for (GuardianInput g : row.child().guardians()) {
            String id = g.idNumber() == null ? "" : g.idNumber().replaceAll("\\s", "");
            String phone = com.carenest.utils.PhoneNumbers.normalize(g.phone()).orElse("");
            if (id.isBlank() || phone.isBlank()) continue;
            String oldPhone = ids.putIfAbsent(id, phone);
            String oldId = phones.putIfAbsent(phone, id);
            if ((oldPhone != null && !oldPhone.equals(phone)) || (oldId != null && !oldId.equals(id)))
                errors.add("CCCD và số điện thoại của phụ huynh không nhất quán giữa các dòng");
        }
    }

    private static GlobalException invalid(String field, String message) {
        return GlobalException.withFieldErrors(
                ApiCode.INVALID_REQUEST_DATA, List.of(new FieldErrorResponse(field, message)));
    }

    private static String normalized(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String headerKey(String value) {
        String header = normalized(value);
        return COLUMNS.stream()
                .filter(c -> header.equals(normalized(c.label()))
                        || header.equals(normalized(c.label().split(" \\(")[0]))
                        || header.startsWith(normalized(c.label().split(" \\(")[0]) + " ("))
                .map(Column::key)
                .findFirst()
                .orElse(null);
    }

    private static String date(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.matches("[0-9]{5}(\\.[0-9]+)?"))
            return LocalDate.of(1899, 12, 30)
                    .plusDays((long) Double.parseDouble(value))
                    .toString();
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("d/M/uuuu"))
                    .toString();
        } catch (RuntimeException ex) {
            return value;
        }
    }

    private static String yesNo(String value) {
        return switch (normalized(value)) {
            case "co", "yes", "x" -> "YES";
            case "khong", "khong co", "no", "none" -> "NO";
            default -> Objects.toString(value, "");
        };
    }

    private static DeclarationAnswerInput answer(String value) {
        String state = value == null || value.isBlank()
                ? "NOT_PROVIDED"
                : "NO".equals(yesNo(value)) ? "NONE_REPORTED" : "REPORTED";
        return new DeclarationAnswerInput(state, "REPORTED".equals(state) && !"YES".equals(yesNo(value)) ? value : "");
    }

    private static String relation(String value) {
        return switch (normalized(value).replaceAll("[^a-z]", "")) {
            case "bo" -> "FATHER";
            case "me" -> "MOTHER";
            case "ong" -> "GRANDFATHER";
            case "ba" -> "GRANDMOTHER";
            case "anhchi" -> "SIBLING";
            case "nguoigiamho" -> "GUARDIAN";
            default -> value;
        };
    }

    private static EnrollChildRequest payload(Map<String, String> raw) {
        List<GuardianInput> guardians = new ArrayList<>();
        String selected = raw.getOrDefault("accountRecipientIdNumber", "").replaceAll("\\s", "");
        for (String suffix : List.of("", "2", "3")) {
            if (!suffix.isEmpty()
                    && List.of("guardianName", "relation", "phone", "guardianIdNumber", "email").stream()
                            .allMatch(k -> raw.getOrDefault(k + suffix, "").isBlank())) continue;
            String id = raw.getOrDefault("guardianIdNumber" + suffix, "");
            guardians.add(new GuardianInput(
                    raw.getOrDefault("guardianName" + suffix, ""),
                    relation(raw.getOrDefault("relation" + suffix, "")),
                    raw.getOrDefault("phone" + suffix, ""),
                    id,
                    raw.getOrDefault("email" + suffix, ""),
                    !selected.isBlank() && selected.equals(id.replaceAll("\\s", ""))));
        }
        Integer weeks = null;
        try {
            weeks = Integer.valueOf(raw.getOrDefault("gestationWeeks", ""));
        } catch (NumberFormatException ignored) {
        }
        ChildProfileInput profile = new ChildProfileInput(
                raw.get("idNumber"),
                yesNo(raw.get("premature")),
                weeks,
                raw.get("ethnicity"),
                raw.get("beneficiary"),
                raw.get("bloodType"),
                new ChildAddressInput(raw.get("province"), raw.get("ward"), raw.get("street")));
        DeclarationAnswerInput allergy = answer(raw.get("allergies"));
        DeclarationAnswerInput tb = answer(raw.get("tbContact"));
        HealthDeclarationInput declaration = new HealthDeclarationInput(
                new AllergiesInput(
                        allergy.state(),
                        "REPORTED".equals(allergy.state())
                                ? Arrays.stream(allergy.text().split("[;,/]"))
                                        .map(String::trim)
                                        .filter(s -> !s.isBlank())
                                        .toList()
                                : List.of()),
                answer(raw.get("diet")),
                answer(null),
                new MedicalHistoryInput(
                        answer(raw.get("selfHistory")),
                        answer(raw.get("familyHistory")),
                        new TbContactInput(tb.state(), date(raw.get("tbContactDate")))));
        String gender =
                switch (normalized(raw.get("gender"))) {
                    case "nam", "m", "male" -> "MALE";
                    case "nu", "f", "female" -> "FEMALE";
                    default -> raw.get("gender");
                };
        return new EnrollChildRequest(
                new EnrollmentChildInput(raw.get("fullName"), date(raw.get("dateOfBirth")), gender, guardians, profile),
                declaration,
                null,
                null);
    }
}
