package com.carenest.dto.child;

import com.carenest.utils.enums.DeclarationState;
import com.carenest.utils.enums.HealthDeclarationAction;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Khai báo sức khỏe của trẻ (CHD-06, mục 4.5–4.6 plan tiếp nhận) như BE giả của FE: các mục theo trạng thái Có / Không
 * có / Chưa cung cấp, người khai gần nhất, lịch sử (mới nhất cuối). Tên record lồng duy nhất (springdoc đặt tên schema
 * theo tên đơn giản). Dữ liệu sức khỏe: không log.
 */
public record HealthDeclarationResponse(
        Long childId,
        DeclarationAllergies allergies,
        DeclarationAnswer diet,
        DeclarationAnswer otherNotes,
        DeclarationMedicalHistory medicalHistory,
        Long declaredBy,
        String declaredByName,
        Instant declaredAt,
        List<DeclarationHistoryEntry> history) {

    public record DeclarationAllergies(DeclarationState state, List<String> items) {}

    public record DeclarationAnswer(DeclarationState state, String text) {}

    public record DeclarationTbContact(DeclarationState state, LocalDate contactDate) {}

    public record DeclarationMedicalHistory(
            DeclarationAnswer self, DeclarationAnswer family, DeclarationTbContact tbContact) {}

    /** Dị ứng trước/sau khi đổi; {@code from} null khi lần khai đầu. */
    public record DeclarationAllergyChange(DeclarationAllergies from, DeclarationAllergies to) {}

    /** Tiền sử trước/sau khi đổi (sửa khai báo hoặc từ phiếu khám). */
    public record DeclarationHistoryChange(DeclarationMedicalHistory from, DeclarationMedicalHistory to) {}

    public record DeclarationHistoryEntry(
            HealthDeclarationAction action,
            Long userId,
            String userName,
            Instant at,
            String note,
            DeclarationAllergyChange allergyChange,
            DeclarationHistoryChange historyChange) {}

    @Override
    public String toString() {
        return "HealthDeclarationResponse[childId=" + childId + "]";
    }
}
