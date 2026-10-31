package com.carenest.integration.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/** Ảnh kết quả khám chuyên khoa của phiếu theo dõi sức khỏe (user chốt 2026-10-31), prefix riêng. */
@Component
public class HealthImageStorage extends PrivateImageStorage {

    public HealthImageStorage(
            S3Client s3,
            @Value("${carenest.education-media.bucket:}") String bucket,
            @Value("${carenest.health-media.prefix:health/follow-up-results/}") String prefix) {
        super(s3, bucket, prefix);
    }
}
