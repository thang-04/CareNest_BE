package com.carenest.integration.storage;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FileCategory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Kho tệp dùng chung (S3 private) cho mọi module. Module tự kiểm quyền trên đối tượng chứa tệp rồi mới gọi; DB chỉ lưu key
 * trả về. Cách dùng: docs/system/EDUCATION_MEDIA_S3.md.
 */
@Component
public class FileStorage {
    private static final Pattern UUID_KEY = Pattern.compile("[a-f0-9-]{36}");
    private static final Pattern OWNER = Pattern.compile("[0-9A-Za-z-]{1,40}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String CACHE_CONTROL = "private, no-store";
    private static final long MB = 1024L * 1024;

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;
    private final Map<FileCategory, StorageProperties.Category> categories;
    private final Clock clock;
    private final ZoneId zone;
    private final SecureRandom random = new SecureRandom();

    public FileStorage(
            S3Client s3,
            S3Presigner presigner,
            StorageProperties props,
            Clock clock,
            @Value("${carenest.time-zone:Asia/Ho_Chi_Minh}") ZoneId zone) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
        this.categories = props.resolved();
        this.clock = clock;
        this.zone = zone;
    }

    public record StoredFile(String key, String contentType, long size) {}

    public record FileUrl(String url, Instant expiresAt) {}

    public record PresignedUpload(String uploadId, String url, Map<String, String> headers, Instant expiresAt) {}

    // ---------- giới hạn ----------

    /** Giới hạn dung lượng của loại, theo nhóm của đuôi nếu loại có cấu hình theo nhóm (ảnh/video/khác). */
    public long maxBytes(FileCategory category, String ext) {
        var c = categories.get(category);
        var byKind = c.maxSizeByKind();
        var size = byKind == null ? null : byKind.get(FileTypes.kind(ext));
        return (size == null ? c.maxSize() : size).toBytes();
    }

    /** Giới hạn dạng chữ cho thông báo lỗi, vd. "2 MB". */
    public String maxSizeText(FileCategory category, String ext) {
        long bytes = maxBytes(category, ext);
        return bytes % MB == 0 ? bytes / MB + " MB" : (bytes + 1023) / 1024 + " KB";
    }

    // ---------- ghi qua backend ----------

    /** Lưu ảnh/tệp nhỏ đã có trong bộ nhớ; đuôi suy từ nội dung thật trong các định dạng cho phép của loại. */
    public StoredFile store(FileCategory category, Object ownerId, byte[] content) {
        requireConfigured();
        if (content == null || content.length == 0) throw GlobalException.of(ApiCode.FILE_EMPTY);
        String ext = categories.get(category).types().stream()
                .filter(t -> FileTypes.matches(t, content))
                .findFirst()
                .orElseThrow(() -> unsupported(category));
        checkSize(category, ext, content.length);
        // Trùng key (cùng giây, cùng 4 ký tự) ⇒ S3 từ chối nhờ If-None-Match, sinh tên khác; không bao giờ ghi đè
        for (int attempt = 0; attempt < 3; attempt++) {
            String key = newKey(category, ownerId, ext);
            if (put(key, FileTypes.mime(ext), RequestBody.fromBytes(content))) {
                return new StoredFile(key, FileTypes.mime(ext), content.length);
            }
        }
        throw unavailable();
    }

    /** Lưu tệp dạng luồng (tệp lớn), kiểm đuôi theo tên gốc, dung lượng và chữ ký đầu tệp trước khi ghi. */
    public StoredFile store(FileCategory category, Object ownerId, String fileName, InputStream input, long size) {
        requireConfigured();
        String ext = FileTypes.extension(fileName);
        validate(category, ext, size);
        InputStream buffered = new BufferedInputStream(input, FileTypes.HEAD_BYTES);
        try {
            buffered.mark(FileTypes.HEAD_BYTES);
            byte[] head = buffered.readNBytes(FileTypes.HEAD_BYTES);
            buffered.reset();
            if (!FileTypes.matches(ext, head)) throw GlobalException.of(ApiCode.FILE_CONTENT_MISMATCH);
        } catch (IOException ex) {
            throw GlobalException.withCause(ApiCode.FILE_UNREADABLE, ex);
        }
        String key = newKey(category, ownerId, ext);
        // ponytail: luồng không đọc lại được nên trùng key (xác suất ~1/1,7 triệu trong cùng giây) báo thử lại thay vì
        // tự sinh lại
        if (!put(key, FileTypes.mime(ext), RequestBody.fromInputStream(buffered, size))) throw unavailable();
        return new StoredFile(key, FileTypes.mime(ext), size);
    }

    // ---------- đọc / xóa ----------

    public Resource load(FileCategory category, String key) {
        requireConfigured();
        String objectKey = resolve(category, key);
        final long size;
        try {
            size = s3.headObject(r -> r.bucket(props.bucket()).key(objectKey)).contentLength();
        } catch (SdkException ex) {
            throw notFoundOrUnavailable(ex);
        }
        return new AbstractResource() {
            @Override
            public String getDescription() {
                return "Private stored file";
            }

            @Override
            public String getFilename() {
                return fileName(objectKey);
            }

            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                try {
                    return s3.getObject(r -> r.bucket(props.bucket()).key(objectKey));
                } catch (SdkException ex) {
                    throw new IOException("Cannot read stored file", ex);
                }
            }
        };
    }

    public byte[] loadBytes(FileCategory category, String key) {
        requireConfigured();
        String objectKey = resolve(category, key);
        try {
            return s3.getObjectAsBytes(r -> r.bucket(props.bucket()).key(objectKey))
                    .asByteArray();
        } catch (SdkException ex) {
            throw notFoundOrUnavailable(ex);
        }
    }

    public void remove(FileCategory category, String key) {
        requireConfigured();
        String objectKey = resolve(category, key);
        try {
            s3.deleteObject(r -> r.bucket(props.bucket()).key(objectKey));
        } catch (SdkException ex) {
            throw unavailable();
        }
    }

    /** Dọn tệp không còn dùng; lỗi S3 bỏ qua (tệp mồ côi không đọc được vì không còn bản ghi trỏ tới). */
    public void removeQuietly(FileCategory category, String key) {
        try {
            remove(category, key);
        } catch (RuntimeException ignored) {
            /* Chấp nhận tệp mồ côi khi S3 lỗi lúc dọn. */
        }
    }

    // ---------- liên kết có hạn ----------

    /** Liên kết xem/tải có hạn (FR-009); gọi sau khi module đã kiểm quyền. downloadName null ⇒ không đặt tên tải. */
    public FileUrl presignGet(FileCategory category, String key, String contentType, String downloadName) {
        requireConfigured();
        String objectKey = resolve(category, key);
        var presigned = presigner.presignGetObject(p -> p.signatureDuration(props.presignTtl())
                .getObjectRequest(r -> {
                    r.bucket(props.bucket()).key(objectKey);
                    if (contentType != null) r.responseContentType(contentType);
                    if (downloadName != null)
                        r.responseContentDisposition("inline; filename*=UTF-8''"
                                + URLEncoder.encode(downloadName, StandardCharsets.UTF_8)
                                        .replace("+", "%20"));
                }));
        return new FileUrl(presigned.url().toString(), presigned.expiration());
    }

    /**
     * Liên kết tải lên trực tiếp vào vùng tạm (FR-010): chữ ký gồm đúng loại và dung lượng khai báo. Tệp chỉ dùng được sau
     * {@link #confirm}; không xác nhận thì lifecycle của vùng tạm xóa.
     */
    public PresignedUpload presignPut(
            FileCategory category, Object ownerId, String fileName, String contentType, long size) {
        requireConfigured();
        String ext = FileTypes.extension(fileName);
        validate(category, ext, size);
        if (!FileTypes.mime(ext).equals(contentType)) throw unsupported(category);
        String key = newKey(category, ownerId, ext);
        var presigned = presigner.presignPutObject(p -> p.signatureDuration(props.presignTtl())
                .putObjectRequest(r -> r.bucket(props.bucket())
                        .key(props.tmpPrefix() + key)
                        .contentType(contentType)
                        .contentLength(size)));
        return new PresignedUpload(
                fileName(key), presigned.url().toString(), Map.of("Content-Type", contentType), presigned.expiration());
    }

    /** Xác nhận tệp đã tải trực tiếp: kiểm lại dung lượng và nội dung thật, đạt thì chuyển khỏi vùng tạm. */
    public StoredFile confirm(FileCategory category, Object ownerId, String uploadId) {
        requireConfigured();
        String owner = owner(ownerId);
        String key = resolve(category, uploadId);
        if (UUID_KEY.matcher(uploadId).matches()
                || !fileName(key).startsWith(categories.get(category).code() + "_" + owner + "_"))
            throw GlobalException.of(ApiCode.FILE_KEY_INVALID);
        String tmpKey = props.tmpPrefix() + key;
        String ext = FileTypes.extension(key);
        try {
            long size = s3.headObject(r -> r.bucket(props.bucket()).key(tmpKey)).contentLength();
            try {
                validate(category, ext, size);
                byte[] head = s3.getObjectAsBytes(r ->
                                r.bucket(props.bucket()).key(tmpKey).range("bytes=0-" + (FileTypes.HEAD_BYTES - 1)))
                        .asByteArray();
                if (!FileTypes.matches(ext, head)) throw GlobalException.of(ApiCode.FILE_CONTENT_MISMATCH);
            } catch (GlobalException ex) {
                s3.deleteObject(r -> r.bucket(props.bucket()).key(tmpKey));
                throw ex;
            }
            s3.copyObject(r -> r.sourceBucket(props.bucket())
                    .sourceKey(tmpKey)
                    .destinationBucket(props.bucket())
                    .destinationKey(key)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .contentType(FileTypes.mime(ext))
                    .cacheControl(CACHE_CONTROL));
            s3.deleteObject(r -> r.bucket(props.bucket()).key(tmpKey));
            return new StoredFile(key, FileTypes.mime(ext), size);
        } catch (SdkException ex) {
            throw notFoundOrUnavailable(ex);
        }
    }

    // ---------- key ----------

    /** Phần tên tệp của key (sau dấu / cuối). */
    public static String fileName(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    /**
     * Key đầy đủ trong bucket: UUID cũ ⇒ thư mục cũ của loại (FR-018); tên tệp trơn ⇒ ghép thư mục; key mới phải đúng
     * mẫu và đúng thư mục của loại, không cho chạy sang thư mục khác.
     */
    String resolve(FileCategory category, String key) {
        var c = categories.get(category);
        if (key == null || key.contains("..")) throw GlobalException.of(ApiCode.FILE_KEY_INVALID);
        if (UUID_KEY.matcher(key).matches()) {
            if (c.legacyPrefix() == null || c.legacyPrefix().isBlank())
                throw GlobalException.of(ApiCode.FILE_KEY_INVALID);
            return c.legacyPrefix() + key;
        }
        String full = key.indexOf('/') < 0 ? c.folder() + key : key;
        if (!keyPattern(c).matcher(full).matches()) throw GlobalException.of(ApiCode.FILE_KEY_INVALID);
        return full;
    }

    private static Pattern keyPattern(StorageProperties.Category c) {
        return Pattern.compile(Pattern.quote(c.folder() + c.code())
                + "_[0-9A-Za-z-]{1,40}_\\d{8}-\\d{6}_[a-z0-9]{4}\\.(" + String.join("|", c.types()) + ")");
    }

    private String newKey(FileCategory category, Object ownerId, String ext) {
        var c = categories.get(category);
        StringBuilder suffix = new StringBuilder(4);
        for (int i = 0; i < 4; i++) suffix.append(RANDOM_CHARS.charAt(random.nextInt(RANDOM_CHARS.length())));
        return c.folder() + c.code() + "_" + owner(ownerId) + "_"
                + STAMP.format(clock.instant().atZone(zone)) + "_" + suffix + "." + ext;
    }

    private static String owner(Object ownerId) {
        String owner = String.valueOf(ownerId);
        if (ownerId == null || !OWNER.matcher(owner).matches()) throw GlobalException.of(ApiCode.FILE_KEY_INVALID);
        return owner;
    }

    // ---------- kiểm ----------

    private void validate(FileCategory category, String ext, long size) {
        if (ext == null || !categories.get(category).types().contains(ext)) throw unsupported(category);
        if (size <= 0) throw GlobalException.of(ApiCode.FILE_EMPTY);
        checkSize(category, ext, size);
    }

    /** Chặn sớm tệp quá lớn trước khi đọc vào bộ nhớ; ext null ⇒ giới hạn chung của loại. */
    public void checkSize(FileCategory category, String ext, long size) {
        if (size > maxBytes(category, ext))
            throw GlobalException.of(ApiCode.FILE_TOO_LARGE, " (tối đa " + maxSizeText(category, ext) + ")");
    }

    private GlobalException unsupported(FileCategory category) {
        return GlobalException.of(
                ApiCode.UNSUPPORTED_MEDIA_TYPE,
                ", chỉ nhận: "
                        + String.join(
                                ", ", new TreeSet<>(categories.get(category).types())));
    }

    /** true khi ghi được; false khi key đã tồn tại (412). */
    private boolean put(String key, String contentType, RequestBody body) {
        try {
            s3.putObject(
                    r -> r.bucket(props.bucket())
                            .key(key)
                            .contentType(contentType)
                            .cacheControl(CACHE_CONTROL)
                            .ifNoneMatch("*"),
                    body);
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 412) return false;
            cleanup(key);
            throw unavailable();
        } catch (SdkException ex) {
            // Request có thể đã tới S3 dù phản hồi bị mất; dọn đúng key mới, không chạm tệp khác
            cleanup(key);
            throw unavailable();
        }
    }

    private void cleanup(String objectKey) {
        try {
            s3.deleteObject(r -> r.bucket(props.bucket()).key(objectKey));
        } catch (SdkException ignored) {
            /* Tệp mồ côi không có bản ghi trỏ tới nên không đọc được. */
        }
    }

    private void requireConfigured() {
        if (props.bucket().isBlank()) throw GlobalException.of(ApiCode.STORAGE_NOT_CONFIGURED);
    }

    private static GlobalException notFoundOrUnavailable(SdkException ex) {
        if (ex instanceof NoSuchKeyException || (ex instanceof S3Exception s && s.statusCode() == 404))
            return GlobalException.of(ApiCode.FILE_NOT_FOUND);
        return unavailable();
    }

    private static GlobalException unavailable() {
        return GlobalException.of(ApiCode.STORAGE_UNAVAILABLE);
    }
}
