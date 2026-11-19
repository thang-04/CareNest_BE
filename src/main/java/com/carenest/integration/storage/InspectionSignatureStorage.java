package com.carenest.integration.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Bản sao ảnh chữ ký nằm trên biên bản kiểm thực đã ký (INS-03). Tách prefix khỏi ảnh chữ ký tài khoản để chủ tài khoản
 * đổi hoặc xóa ảnh không làm mất ảnh trên chứng từ.
 */
@Component
public class InspectionSignatureStorage extends PrivateImageStorage {

    public InspectionSignatureStorage(
            S3Client s3,
            @Value("${carenest.education-media.bucket:}") String bucket,
            @Value("${carenest.inspection-signature-media.prefix:meals/inspection-signatures/}") String prefix) {
        super(s3, bucket, prefix);
    }
}
