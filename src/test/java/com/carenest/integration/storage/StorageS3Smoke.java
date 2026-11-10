package com.carenest.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

// Chạy riêng với -Dtest=StorageS3Smoke; không đọc credential từ file hoặc tự chạy trên CI.
class StorageS3Smoke {
    @Test
    void privateObjectsRoundTripAndCleanup() throws Exception {
        String bucket = System.getenv("CARENEST_EDUCATION_MEDIA_BUCKET");
        assertThat(bucket).as("Configure the smoke bucket through environment").isNotBlank();
        String region = System.getenv().getOrDefault("AWS_REGION", "ap-southeast-1");
        List<String> keys = new ArrayList<>();
        try (S3Client s3 = S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(
                        c -> c.apiCallTimeout(Duration.ofSeconds(45)).apiCallAttemptTimeout(Duration.ofSeconds(20)))
                .build()) {
            String prefix = "carenest-smoke/" + UUID.randomUUID() + "/";
            StorageService storage = new StorageService(s3, bucket, prefix);
            byte[] binary = new byte[1024 * 1024 + 19];
            for (int i = 0; i < binary.length; i++) binary[i] = (byte) (i % 251);
            List<byte[]> samples = List.of(
                    java.util.Base64.getDecoder()
                            .decode(
                                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aH1cAAAAASUVORK5CYII="),
                    pdfFixture(),
                    "CareNest teaching resource smoke".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    binary);
            try {
                for (byte[] expected : samples) {
                    String key = storage.store(new ByteArrayInputStream(expected), expected.length);
                    keys.add(key);
                    var resource = storage.load(key);
                    assertThat(resource.contentLength()).isEqualTo(expected.length);
                    try (var input = resource.getInputStream()) {
                        assertThat(input.readAllBytes()).isEqualTo(expected);
                    }
                    try (S3Client anonymous = S3Client.builder()
                            .region(Region.of(region))
                            .credentialsProvider(
                                    software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider.create())
                            .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(20)))
                            .build()) {
                        assertThatThrownBy(() -> anonymous.headObject(
                                        r -> r.bucket(bucket).key(prefix + key)))
                                .as("Test object must not allow anonymous read")
                                .isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception.class)
                                .satisfies(ex -> assertThat(((software.amazon.awssdk.services.s3.model.S3Exception) ex)
                                                .statusCode())
                                        .isIn(403, 404));
                    }
                    storage.remove(key);
                    keys.remove(key);
                    assertThatThrownBy(() -> storage.load(key))
                            .isInstanceOf(GlobalException.class)
                            .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode())
                                    .isEqualTo(ApiCode.NOT_FOUND));
                }
            } finally {
                for (String key : keys) storage.remove(key);
            }
            System.out.println("S3_SMOKE PASS | image, PDF, text and 1MiB binary roundtrip | all test objects cleaned");
        } catch (Exception ex) {
            throw new AssertionError(
                    "S3 smoke failed (sanitized): " + ex.getClass().getSimpleName());
        }
    }

    private byte[] pdfFixture() {
        String stream = "BT /F1 18 Tf 70 720 Td (CareNest S3 smoke fixture) Tj ET";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream");
        StringBuilder body = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(body.length());
            body.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = body.length();
        body.append("xref\n0 6\n0000000000 65535 f \n");
        offsets.forEach(offset -> body.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset)));
        body.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n")
                .append(xref)
                .append("\n%%EOF");
        return body.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
