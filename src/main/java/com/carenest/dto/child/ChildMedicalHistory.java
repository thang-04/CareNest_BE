package com.carenest.dto.child;

import com.carenest.utils.enums.DeclarationState;
import java.time.LocalDate;

/**
 * Tiền sử trong khai báo sức khỏe (HLT-06): bản thân, gia đình, tiếp xúc người bệnh lao, chỉ Có ({@code REPORTED}) hoặc
 * Không ({@code NONE_REPORTED}); nội dung, ngày tiếp xúc chỉ khi Có.
 */
public record ChildMedicalHistory(
        DeclarationState self,
        String selfText,
        DeclarationState family,
        String familyText,
        DeclarationState tbContact,
        LocalDate tbContactDate) {}
