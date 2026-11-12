package com.carenest.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FileCategory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Kiểm bucket thật (chạy riêng: {@code -Dtest=StorageS3Smoke}, credential qua biến môi trường, không tự chạy trên CI):
 * lưu qua backend, liên kết xem có hạn, tải trực tiếp + xác nhận, chặn đọc ẩn danh, dọn sạch. Mọi tệp nằm dưới
 * {@code carenest-smoke/<uuid>/}.
 */
class StorageS3Smoke {
    private static final byte[] PNG = java.util.Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aH1cAAAAASUVORK5CYII=");

    @Test
    void privateObjectsRoundTripAndCleanup() throws Exception {
        String bucket = System.getenv("CARENEST_EDUCATION_MEDIA_BUCKET");
        assertThat(bucket).as("Configure the smoke bucket through environment").isNotBlank();
        Region region = Region.of(System.getenv().getOrDefault("AWS_REGION", "ap-southeast-1"));
        String root = "carenest-smoke/" + UUID.randomUUID() + "/";
        List<String> keys = new ArrayList<>();
        HttpClient http = HttpClient.newHttpClient();
        try (S3Client s3 = S3Client.builder()
                        .region(region)
                        .overrideConfiguration(c ->
                                c.apiCallTimeout(Duration.ofSeconds(45)).apiCallAttemptTimeout(Duration.ofSeconds(20)))
                        .build();
                S3Presigner presigner = S3Presigner.builder().region(region).build()) {
            FileStorage storage = new FileStorage(
                    s3,
                    presigner,
                    TestStorageProperties.of(bucket, root),
                    Clock.systemUTC(),
                    ZoneId.of("Asia/Ho_Chi_Minh"));
            try {
                // 1. Lưu qua backend + đọc lại
                FileStorage.StoredFile photo = storage.store(FileCategory.MEAL, 1L, PNG);
                keys.add(photo.key());
                assertThat(storage.loadBytes(FileCategory.MEAL, photo.key())).isEqualTo(PNG);

                // 2. Liên kết xem có hạn tải được không cần header; URL không ký bị chặn
                var url = storage.presignGet(FileCategory.MEAL, photo.key(), photo.contentType(), "anh.png");
                var get = http.send(HttpRequest.newBuilder(URI.create(url.url())).build(), HttpResponse.BodyHandlers.ofByteArray());
                assertThat(get.statusCode()).isEqualTo(200);
                assertThat(get.body()).isEqualTo(PNG);
                String plain = "https://" + bucket + ".s3." + region.id() + ".amazonaws.com/" + photo.key();
                var anonymous = http.send(HttpRequest.newBuilder(URI.create(plain)).build(), HttpResponse.BodyHandlers.discarding());
                assertThat(anonymous.statusCode()).as("Bucket must stay private").isIn(403, 404);

                // 3. Tải trực tiếp qua liên kết PUT rồi xác nhận
                byte[] pdf = "%PDF-1.4\nCareNest smoke\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                var upload =
                        storage.presignPut(FileCategory.LEARNING_RESOURCE, 1L, "smoke.pdf", "application/pdf", pdf.length);
                keys.add("tmp/" + root + "hoclieu/" + upload.uploadId());
                var put = http.send(
                        HttpRequest.newBuilder(URI.create(upload.url()))
                                .header("Content-Type", "application/pdf")
                                .PUT(HttpRequest.BodyPublishers.ofByteArray(pdf))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
                assertThat(put.statusCode()).isEqualTo(200);
                FileStorage.StoredFile confirmed = storage.confirm(FileCategory.LEARNING_RESOURCE, 1L, upload.uploadId());
                keys.add(confirmed.key());
                assertThat(storage.loadBytes(FileCategory.LEARNING_RESOURCE, confirmed.key())).isEqualTo(pdf);

                // 4. Xóa xong không đọc được nữa
                for (String key : List.of(photo.key(), confirmed.key())) {
                    FileCategory category = key.equals(photo.key()) ? FileCategory.MEAL : FileCategory.LEARNING_RESOURCE;
                    storage.remove(category, key);
                    assertThatThrownBy(() -> storage.loadBytes(category, key))
                            .isInstanceOf(GlobalException.class)
                            .satisfies(ex -> assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.FILE_NOT_FOUND));
                }
            } finally {
                for (String key : keys) s3.deleteObject(r -> r.bucket(bucket).key(key));
            }
            System.out.println("S3_SMOKE PASS | store, presigned GET, presigned PUT + confirm, private, cleanup");
        } catch (Exception | AssertionError ex) {
            throw new AssertionError("S3 smoke failed (sanitized): " + ex.getClass().getSimpleName() + " " + safe(ex));
        }
    }

    /** Thông báo lỗi không chứa URL ký (có chữ ký/khóa truy cập). */
    private static String safe(Throwable ex) {
        String message = String.valueOf(ex.getMessage());
        return message.replaceAll("https://\\S+", "<url>");
    }
}
