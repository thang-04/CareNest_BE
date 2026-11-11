package com.carenest.integration.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/** Ảnh chữ ký của tài khoản nhân sự (SIG-01), prefix riêng cùng bucket với ảnh tài khoản. */
@Component
public class SignatureStorage extends PrivateImageStorage {

    public SignatureStorage(
            S3Client s3,
            @Value("${carenest.education-media.bucket:}") String bucket,
            @Value("${carenest.signature-media.prefix:accounts/signatures/}") String prefix) {
        super(s3, bucket, prefix);
    }
}
