package com.carenest.integration.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/** Ảnh hồ sơ trẻ và phụ huynh tại thời điểm tiếp nhận, tách ảnh tài khoản. */
@Component
public class ChildPhotoStorage extends PrivateImageStorage {
    public ChildPhotoStorage(
            S3Client s3,
            @Value("${carenest.education-media.bucket:}") String bucket,
            @Value("${carenest.child-media.prefix:children/photos/}") String prefix) {
        super(s3, bucket, prefix);
    }
}
