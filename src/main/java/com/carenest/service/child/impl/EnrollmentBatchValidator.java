package com.carenest.service.child.impl;

import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentChildInput;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EnrollmentBatchValidator {
    private final List<EnrollmentChildInput> children = new ArrayList<>();
    private final Map<String, String> ids = new HashMap<>();
    private final Map<String, String> phones = new HashMap<>();

    void check(EnrollChildRequest row) {
        List<String> errors = new ArrayList<>();
        collectErrors(row, errors);
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(
                    ApiCode.INVALID_REQUEST_DATA, List.of(new FieldErrorResponse("file", String.join("; ", errors))));
        }
    }

    void collectErrors(EnrollChildRequest row, List<String> errors) {
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
}
