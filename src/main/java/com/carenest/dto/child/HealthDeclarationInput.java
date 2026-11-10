package com.carenest.dto.child;

/** Khai báo sức khỏe khi tiếp nhận (CHD-06, HLT-06); trạng thái REPORTED / NONE_REPORTED / NOT_PROVIDED. */
public record HealthDeclarationInput(
        AllergiesInput allergies,
        DeclarationAnswerInput diet,
        DeclarationAnswerInput otherNotes,
        MedicalHistoryInput medicalHistory) {}
