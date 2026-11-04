package com.carenest.dto.child;

/** Một mục khai báo: trạng thái + nội dung (bắt buộc khi REPORTED). */
public record DeclarationAnswerInput(String state, String text) {

    @Override
    public String toString() {
        return "DeclarationAnswerInput[state=" + state + "]";
    }
}
