package com.carenest.integration.storage;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Component
public class StorageService {
    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public StorageService(
            S3Client s3,
            @Value("${carenest.education-media.bucket:}") String bucket,
            @Value("${carenest.education-media.prefix:education/learning-resources/}") String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        if (!prefix.matches("[a-zA-Z0-9/_-]*")) throw new IllegalArgumentException("Invalid S3 media prefix");
        this.prefix = prefix;
    }

    public String store(InputStream input, long expectedSize) {
        requireConfigured();
        if (expectedSize <= 0 || expectedSize > 100L * 1024 * 1024) throw GlobalException.of(ApiCode.FILE_TOO_LARGE);
        String key = UUID.randomUUID().toString();
        try {
            s3.putObject(
                    r -> r.bucket(bucket)
                            .key(objectKey(key))
                            .contentType("application/octet-stream")
                            .cacheControl("private, no-store"),
                    RequestBody.fromInputStream(input, expectedSize));
            return key;
        } catch (SdkException ex) {
            // Request có thể đã tới S3 dù phản hồi bị mất; dọn đúng key mới, không chạm tệp cũ.
            try {
                s3.deleteObject(r -> r.bucket(bucket).key(objectKey(key)));
            } catch (SdkException ignored) {
                /* Cần lifecycle dọn object chưa có metadata nếu S3 vẫn lỗi. */
            }
            throw unavailable();
        }
    }

    public Resource load(String key) {
        requireConfigured();
        String objectKey = objectKey(key);
        final long size;
        try {
            size = s3.headObject(r -> r.bucket(bucket).key(objectKey)).contentLength();
        } catch (NoSuchKeyException ex) {
            throw GlobalException.of(ApiCode.FILE_NOT_FOUND);
        } catch (software.amazon.awssdk.services.s3.model.S3Exception ex) {
            if (ex.statusCode() == 404) throw GlobalException.of(ApiCode.FILE_NOT_FOUND);
            throw unavailable();
        } catch (SdkException ex) {
            throw unavailable();
        }
        return new AbstractResource() {
            @Override
            public String getDescription() {
                return "Private learning resource";
            }

            @Override
            public String getFilename() {
                return key;
            }

            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                try {
                    return s3.getObject(r -> r.bucket(bucket).key(objectKey));
                } catch (SdkException ex) {
                    throw new IOException("Cannot read learning resource", ex);
                }
            }
        };
    }

    public void remove(String key) {
        requireConfigured();
        String objectKey = objectKey(key);
        try {
            s3.deleteObject(r -> r.bucket(bucket).key(objectKey));
        } catch (SdkException ex) {
            throw unavailable();
        }
    }

    private String objectKey(String key) {
        if (key == null || !key.matches("[a-f0-9-]{36}")) throw GlobalException.of(ApiCode.FILE_KEY_INVALID);
        return prefix + key;
    }

    private void requireConfigured() {
        if (bucket.isBlank()) throw GlobalException.of(ApiCode.STORAGE_NOT_CONFIGURED);
    }

    private GlobalException unavailable() {
        return GlobalException.of(ApiCode.STORAGE_UNAVAILABLE);
    }
}
