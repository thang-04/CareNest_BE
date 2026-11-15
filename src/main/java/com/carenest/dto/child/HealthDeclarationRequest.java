package com.carenest.dto.child;

/**
 * Sửa khai báo sức khỏe ({@code PUT /children/{id}/health-declaration}, mục 4.5 plan tiếp nhận): khai báo như form tiếp
 * nhận; lý do bắt buộc khi trẻ đã có khai báo, tối đa 300 ký tự. Dữ liệu sức khỏe: không log.
 */
public record HealthDeclarationRequest(HealthDeclarationInput declaration, String reason) {
    @Override
    public String toString() {
        return "HealthDeclarationRequest[]";
    }
}
