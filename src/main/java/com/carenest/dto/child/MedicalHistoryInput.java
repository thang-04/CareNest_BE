package com.carenest.dto.child;

/** Tiền sử bản thân, gia đình, tiếp xúc người bệnh lao: chỉ REPORTED hoặc NONE_REPORTED. */
public record MedicalHistoryInput(
        DeclarationAnswerInput self, DeclarationAnswerInput family, TbContactInput tbContact) {}
