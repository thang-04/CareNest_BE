package com.carenest.entity.meals;

import java.time.Instant;

/**
 * Chữ ký trên biên bản (INS-03): bản sao ảnh chữ ký lúc ký ({@code imageKey} là tệp riêng của biên bản, không phải tệp
 * của chữ ký tài khoản) cùng người ký và phiên bản biên bản được ký.
 */
public record InspectionSignature(
        Long signedBy,
        String signedByName,
        Instant signedAt,
        int documentVersion,
        String imageKey,
        String contentType,
        int sizeBytes) {}
