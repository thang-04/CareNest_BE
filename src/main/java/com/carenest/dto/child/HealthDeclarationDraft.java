package com.carenest.dto.child;

import com.carenest.utils.enums.DeclarationState;
import java.time.LocalDate;
import java.util.List;

/** Khai báo đã chuẩn hóa: nội dung chỉ giữ khi REPORTED, dị ứng đã bỏ ô trống và trùng. Dữ liệu sức khỏe: không log. */
public record HealthDeclarationDraft(
        DeclarationState allergiesState,
        List<String> allergies,
        Answer diet,
        Answer otherNotes,
        Answer selfHistory,
        Answer familyHistory,
        DeclarationState tbContactState,
        LocalDate tbContactDate) {

    public HealthDeclarationDraft {
        allergies = List.copyOf(allergies);
    }

    public record Answer(DeclarationState state, String text) {}

    @Override
    public String toString() {
        return "HealthDeclarationDraft[allergiesState=" + allergiesState + "]";
    }
}
