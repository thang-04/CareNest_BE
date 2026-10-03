package com.carenest.service;

import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * Stores images in a private S3-compatible bucket. The database keeps only the object key;
 * clients read images through short-lived presigned URLs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Value("${app.storage.presigned-url-expiration}")
    private Duration presignedUrlExpiration;

    @EventListener(ApplicationReadyEvent.class)
    public void createBucketIfMissing() {
        try {
            s3Client.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(b -> b.bucket(bucket));
            log.info("Created storage bucket {}", bucket);
        } catch (SdkException e) {
            // Do not block startup; uploads fail with STORAGE_FAILED until storage is reachable.
            log.warn("Storage is not reachable: {}", e.getMessage());
        }
    }

    /**
     * Uploads a JPEG, PNG or WebP image under the folder and returns its object key.
     */
    public String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_IMAGE);
        }
        ImageType type = detectType(file);
        String key = folder + "/" + UUID.randomUUID() + type.extension;
        try (InputStream in = file.getInputStream()) {
            s3Client.putObject(b -> b.bucket(bucket).key(key).contentType(type.contentType),
                    RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException | SdkException e) {
            log.error("Cannot upload image {}", key, e);
            throw new AppException(ErrorCode.STORAGE_FAILED);
        }
        return key;
    }

    /**
     * Presigned GET URL for the key, or null when there is no image.
     */
    public String url(String key) {
        if (key == null) {
            return null;
        }
        return s3Presigner.presignGetObject(b -> b
                        .signatureDuration(presignedUrlExpiration)
                        .getObjectRequest(o -> o.bucket(bucket).key(key)))
                .url()
                .toString();
    }

    /**
     * Best-effort delete: a leftover object is not worth failing the request for.
     * Inside a transaction the object is deleted only after commit, so a rollback keeps the image.
     */
    public void delete(String key) {
        if (key == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteNow(key);
                }
            });
        } else {
            deleteNow(key);
        }
    }

    private void deleteNow(String key) {
        try {
            s3Client.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            log.warn("Cannot delete image {}: {}", key, e.getMessage());
        }
    }

    // Checks the file's magic bytes; the client's Content-Type header cannot be trusted.
    private static ImageType detectType(MultipartFile file) {
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(head, 0, head.length);
            head = Arrays.copyOf(head, read);
        } catch (IOException e) {
            throw new AppException(ErrorCode.INVALID_IMAGE);
        }
        for (ImageType type : ImageType.values()) {
            if (type.matches(head)) {
                return type;
            }
        }
        throw new AppException(ErrorCode.INVALID_IMAGE);
    }

    private enum ImageType {
        JPEG("image/jpeg", ".jpg"),
        PNG("image/png", ".png"),
        WEBP("image/webp", ".webp");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        boolean matches(byte[] h) {
            return switch (this) {
                case JPEG -> h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
                case PNG -> h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
                case WEBP -> h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                        && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            };
        }
    }
}
