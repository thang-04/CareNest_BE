package com.carenest.integration.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class EducationStorageConfig {
    @Bean(destroyMethod = "close")
    S3Client educationS3Client(
            @Value("${carenest.education-media.region:ap-southeast-1}") String region,
            @Value("${carenest.education-media.endpoint:}") String endpoint,
            @Value("${carenest.education-media.path-style:false}") boolean pathStyle) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .forcePathStyle(pathStyle)
                .overrideConfiguration(
                        c -> c.apiCallTimeout(Duration.ofMinutes(2)).apiCallAttemptTimeout(Duration.ofSeconds(45)));
        // Credential dùng default provider chain, không chứa khóa trong source/config mẫu.
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }
}
