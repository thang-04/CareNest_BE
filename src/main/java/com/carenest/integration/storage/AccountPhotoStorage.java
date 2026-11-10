package com.carenest.integration.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/** Ảnh chân dung của tài khoản (phụ huynh tải khi đăng nhập App lần đầu, GV đối chiếu khi trả trẻ), prefix riêng. */
@Component
public class AccountPhotoStorage extends PrivateImageStorage {

    public AccountPhotoStorage(
            S3Client s3,
            @Value("${carenest.education-media.bucket:}") String bucket,
            @Value("${carenest.account-media.prefix:accounts/photos/}") String prefix) {
        super(s3, bucket, prefix);
    }
}
