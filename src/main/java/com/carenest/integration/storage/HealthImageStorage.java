package com.carenest.integration.storage;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Ảnh kết quả khám chuyên khoa của phiếu theo dõi sức khỏe trên S3: cùng bucket với tài liệu giáo dục, prefix riêng; tệp
 * riêng tư, chỉ đọc qua API có kiểm phạm vi (user chốt 2026-10-31). Ảnh nhỏ (≤ 1 MB) nên đọc/ghi cả mảng byte.
 */
@Component
public class HealthImageStorage {
    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public HealthImageStorage(
            S3Client s3,
            @Value("${carenest.education-media.bucket:}") String bucket,
            @Value("${carenest.health-media.prefix:health/follow-up-results/}") String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        if (!prefix.matches("[a-zA-Z0-9/_-]*")) throw new IllegalArgumentException("Invalid S3 media prefix");
        this.prefix = prefix;
    }

    /** Lưu ảnh, trả mã tệp (UUID). */
    public String store(byte[] content, String contentType) {
        requireConfigured();
        String key = UUID.randomUUID().toString();
        try {
            s3.putObject(
                    r -> r.bucket(bucket)
                            .key(objectKey(key))
                            .contentType(contentType)
                            .cacheControl("private, no-store"),
                    RequestBody.fromBytes(content));
            return key;
        } catch (SdkException ex) {
            // Request có thể đã tới S3 dù phản hồi bị mất; dọn đúng key mới
            removeQuietly(key);
            throw GlobalException.of(ApiCode.STORAGE_UNAVAILABLE);
        }
    }

    public byte[] load(String key) {
        requireConfigured();
        try {
            return s3.getObjectAsBytes(r -> r.bucket(bucket).key(objectKey(key)))
                    .asByteArray();
        } catch (NoSuchKeyException ex) {
            throw GlobalException.of(ApiCode.FILE_NOT_FOUND);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) throw GlobalException.of(ApiCode.FILE_NOT_FOUND);
            throw GlobalException.of(ApiCode.STORAGE_UNAVAILABLE);
        } catch (SdkException ex) {
            throw GlobalException.of(ApiCode.STORAGE_UNAVAILABLE);
        }
    }

    /** Dọn tệp đã lưu khi ghi dữ liệu lỗi; lỗi S3 ở đây bỏ qua (cần lifecycle dọn object mồ côi). */
    public void removeQuietly(String key) {
        try {
            s3.deleteObject(r -> r.bucket(bucket).key(objectKey(key)));
        } catch (SdkException ignored) {
            /* Object mồ côi không lộ dữ liệu: chỉ đọc được qua bản ghi ảnh. */
        }
    }

    private String objectKey(String key) {
        if (key == null || !key.matches("[a-f0-9-]{36}")) throw GlobalException.of(ApiCode.FILE_KEY_INVALID);
        return prefix + key;
    }

    private void requireConfigured() {
        if (bucket.isBlank()) throw GlobalException.of(ApiCode.STORAGE_NOT_CONFIGURED);
    }
}
