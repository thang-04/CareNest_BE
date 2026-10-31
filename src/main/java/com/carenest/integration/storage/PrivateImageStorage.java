package com.carenest.integration.storage;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.util.UUID;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Ảnh nhỏ (≤ 2 MB) private trên S3, cùng bucket với tài liệu giáo dục, mỗi loại một prefix; ghi/đọc cả mảng byte. Chỉ đọc
 * qua API có kiểm phạm vi, không trả key hay URL bucket.
 */
public abstract class PrivateImageStorage {
    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    protected PrivateImageStorage(S3Client s3, String bucket, String prefix) {
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

    /** Dọn tệp không còn dùng; lỗi S3 ở đây bỏ qua (object mồ côi không đọc được vì không còn bản ghi trỏ tới). */
    public void removeQuietly(String key) {
        try {
            s3.deleteObject(r -> r.bucket(bucket).key(objectKey(key)));
        } catch (SdkException ignored) {
            /* Cần lifecycle dọn object mồ côi nếu S3 vẫn lỗi. */
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
