package com.carenest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 client for image storage. With app.storage.endpoint set (local SeaweedFS, MinIO...) it uses path-style URLs;
 * without it, the client talks to AWS S3 directly.
 */
@Configuration
public class StorageConfiguration {

    @Value("${app.storage.endpoint:}")
    private String endpoint;

    @Value("${app.storage.region}")
    private String region;

    @Value("${app.storage.access-key}")
    private String accessKey;

    @Value("${app.storage.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials())
                .serviceConfiguration(s3Configuration());
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials())
                .serviceConfiguration(s3Configuration());
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private S3Configuration s3Configuration() {
        // Self-hosted S3 servers need path-style URLs (http://host:9000/bucket/key).
        return S3Configuration.builder().pathStyleAccessEnabled(StringUtils.hasText(endpoint)).build();
    }
}
