package com.carenest.entity.child;

import com.carenest.utils.enums.DeclarationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Khai báo sức khỏe, 1–1 với trẻ (CHD-06, HLT-06). Mục "Có" phải có nội dung, mục khác thì không (CHECK V10); danh
 * sách dị ứng ở {@link ChildAllergy}.
 */
@Getter
@Entity
@Table(name = "child_health_declarations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildHealthDeclaration {

    @Id
    @Column(name = "child_id")
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allergies_state", nullable = false, length = 20)
    private DeclarationState allergiesState;

    @Enumerated(EnumType.STRING)
    @Column(name = "diet_state", nullable = false, length = 20)
    private DeclarationState dietState;

    @Column(name = "diet_text", length = 500)
    private String dietText;

    @Enumerated(EnumType.STRING)
    @Column(name = "other_notes_state", nullable = false, length = 20)
    private DeclarationState otherNotesState;

    @Column(name = "other_notes_text", length = 500)
    private String otherNotesText;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_history_state", nullable = false, length = 20)
    private DeclarationState selfHistoryState;

    @Column(name = "self_history_text", length = 300)
    private String selfHistoryText;

    @Enumerated(EnumType.STRING)
    @Column(name = "family_history_state", nullable = false, length = 20)
    private DeclarationState familyHistoryState;

    @Column(name = "family_history_text", length = 300)
    private String familyHistoryText;

    @Enumerated(EnumType.STRING)
    @Column(name = "tb_contact_state", nullable = false, length = 20)
    private DeclarationState tbContactState;

    @Column(name = "tb_contact_date")
    private LocalDate tbContactDate;

    @Column(name = "declared_by", nullable = false)
    private Long declaredBy;

    @Column(name = "declared_at", nullable = false)
    private Instant declaredAt;

    /** Nội dung từng mục; text = null khi không phải "Có" (service đã chuẩn hóa). */
    public record Answer(DeclarationState state, String text) {}

    public static ChildHealthDeclaration declare(
            Long childId,
            DeclarationState allergiesState,
            Answer diet,
            Answer otherNotes,
            Answer selfHistory,
            Answer familyHistory,
            DeclarationState tbContactState,
            LocalDate tbContactDate,
            Long declaredBy,
            Instant declaredAt) {
        ChildHealthDeclaration declaration = new ChildHealthDeclaration();
        declaration.childId = childId;
        declaration.allergiesState = allergiesState;
        declaration.dietState = diet.state();
        declaration.dietText = diet.text();
        declaration.otherNotesState = otherNotes.state();
        declaration.otherNotesText = otherNotes.text();
        declaration.selfHistoryState = selfHistory.state();
        declaration.selfHistoryText = selfHistory.text();
        declaration.familyHistoryState = familyHistory.state();
        declaration.familyHistoryText = familyHistory.text();
        declaration.tbContactState = tbContactState;
        declaration.tbContactDate = tbContactDate;
        declaration.declaredBy = declaredBy;
        declaration.declaredAt = declaredAt;
        return declaration;
    }

    /** Cập nhật tiền sử (từ phiếu khám, phiếu theo dõi; HLT-06); các mục khác, người khai, lúc khai giữ nguyên. */
    public void updateMedicalHistory(
            Answer selfHistory, Answer familyHistory, DeclarationState tbContactState, LocalDate tbContactDate) {
        this.selfHistoryState = selfHistory.state();
        this.selfHistoryText = selfHistory.text();
        this.familyHistoryState = familyHistory.state();
        this.familyHistoryText = familyHistory.text();
        this.tbContactState = tbContactState;
        this.tbContactDate = tbContactDate;
    }
}
