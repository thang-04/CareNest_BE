package com.carenest.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FileCategory;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class FileStorageTest {
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0};
    private static final byte[] PDF = "%PDF-1.7 test".getBytes();
    // 2026-11-05 14:30:12 giờ Việt Nam
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T07:30:12Z"), ZoneOffset.UTC);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private S3Client s3;
    private InMemoryS3 bucket;
    private S3Presigner presigner;
    private FileStorage storage;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class, Answers.CALLS_REAL_METHODS);
        bucket = InMemoryS3.install(s3);
        // Ký liên kết chạy cục bộ, không gọi mạng; khóa giả chỉ để ký
        presigner = S3Presigner.builder()
                .region(Region.AP_SOUTHEAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
        storage = new FileStorage(s3, presigner, TestStorageProperties.of("test-bucket", ""), CLOCK, VN);
    }

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    private static void assertCode(Throwable ex, ApiCode code) {
        assertThat(ex).isInstanceOf(GlobalException.class);
        assertThat(((GlobalException) ex).getApiCode()).isEqualTo(code);
    }

    @Test
    void storesUnderCategoryFolderWithNamingRuleAndRealContentType() {
        FileStorage.StoredFile stored = storage.store(FileCategory.MEAL, 123L, JPG);

        assertThat(stored.key()).matches("meals/meal_123_20261105-143012_[a-z0-9]{4}\\.jpg");
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(bucket.objects()).containsKey(stored.key());
        assertThat(storage.loadBytes(FileCategory.MEAL, stored.key())).isEqualTo(JPG);
    }

    @Test
    void sameSecondCollisionGetsNewNameInsteadOfOverwriting() {
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
                    if (first.getAndSet(false))
                        throw S3Exception.builder().statusCode(412).build();
                    return bucket.put(call.getArgument(0), call.getArgument(1));
                })
                .when(s3)
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));

        FileStorage.StoredFile stored = storage.store(FileCategory.USER_AVATAR, 7L, PNG);

        assertThat(bucket.objects()).containsOnlyKeys(stored.key());
    }

    @Test
    void rejectsOversizeWrongTypeAndDisguisedContentWithoutStoring() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(JPG, 0, big, 0, JPG.length);
        Throwable tooLarge = catchThrowable(() -> storage.store(FileCategory.MEAL, 1L, big));
        assertCode(tooLarge, ApiCode.FILE_TOO_LARGE);
        assertThat(tooLarge.getMessage()).contains("tối đa 5 MB");

        Throwable wrongType = catchThrowable(() -> storage.store(FileCategory.MEAL, 1L, EXE));
        assertCode(wrongType, ApiCode.UNSUPPORTED_MEDIA_TYPE);
        assertThat(wrongType.getMessage()).contains("jpg, png, webp");

        // Đuôi .jpg nhưng nội dung là tệp chạy được
        assertCode(
                catchThrowable(() -> storage.store(
                        FileCategory.LEARNING_RESOURCE, 1L, "anh.jpg", new ByteArrayInputStream(EXE), EXE.length)),
                ApiCode.FILE_CONTENT_MISMATCH);
        assertCode(catchThrowable(() -> storage.store(FileCategory.MEAL, 1L, new byte[0])), ApiCode.FILE_EMPTY);
        assertThat(bucket.objects()).isEmpty();
    }

    @Test
    void learningResourceLimitsDependOnKind() {
        int sixMb = 6 * 1024 * 1024;
        assertCode(
                catchThrowable(
                        () -> storage.presignPut(FileCategory.LEARNING_RESOURCE, 1L, "a.png", "image/png", sixMb)),
                ApiCode.FILE_TOO_LARGE);
        assertThat(storage.presignPut(FileCategory.LEARNING_RESOURCE, 1L, "a.pdf", "application/pdf", sixMb))
                .isNotNull();
        assertCode(
                catchThrowable(() -> storage.presignPut(
                        FileCategory.LEARNING_RESOURCE, 1L, "v.mp4", "video/mp4", 101L * 1024 * 1024)),
                ApiCode.FILE_TOO_LARGE);
    }

    @Test
    void resolvesLegacyUuidAndBlocksOtherFolders() {
        String uuid = "0f8fad5b-d9cb-469f-a165-70867728950e";
        assertThat(storage.resolve(FileCategory.USER_AVATAR, uuid)).isEqualTo("accounts/photos/" + uuid);
        assertThat(storage.resolve(FileCategory.INSPECTION_SIGNATURE, "inspsig_1_20261105-143012_ab12.png"))
                .isEqualTo("signatures/inspections/inspsig_1_20261105-143012_ab12.png");
        assertCode(
                catchThrowable(() ->
                        storage.resolve(FileCategory.USER_AVATAR, "avatars/children/child_1_20261105-143012_ab12.png")),
                ApiCode.FILE_KEY_INVALID);
        assertCode(
                catchThrowable(() -> storage.resolve(FileCategory.USER_AVATAR, "avatars/users/../x")),
                ApiCode.FILE_KEY_INVALID);
        assertCode(catchThrowable(() -> storage.resolve(FileCategory.PICKUP, uuid)), ApiCode.FILE_KEY_INVALID);
    }

    @Test
    void presignedGetExpiresInFifteenMinutes() {
        FileStorage.StoredFile stored = storage.store(FileCategory.MEAL, 3L, JPG);

        FileStorage.FileUrl url = storage.presignGet(FileCategory.MEAL, stored.key(), "image/jpeg", "bữa trưa.jpg");

        assertThat(url.url()).contains(stored.key()).contains("X-Amz-Expires=900");
        assertThat(url.expiresAt()).isAfter(Instant.now().plusSeconds(14 * 60));
    }

    @Test
    void directUploadIsConfirmedOnlyWhenContentMatches() {
        var upload = storage.presignPut(FileCategory.LEARNING_RESOURCE, 12L, "bai.pdf", "application/pdf", PDF.length);
        assertThat(upload.uploadId()).matches("hoclieu_12_20261105-143012_[a-z0-9]{4}\\.pdf");
        assertThat(upload.url()).contains("tmp/hoclieu/" + upload.uploadId());

        bucket.objects().put("tmp/hoclieu/" + upload.uploadId(), PDF);
        assertCode(
                catchThrowable(() -> storage.confirm(FileCategory.LEARNING_RESOURCE, 99L, upload.uploadId())),
                ApiCode.FILE_KEY_INVALID);
        FileStorage.StoredFile stored = storage.confirm(FileCategory.LEARNING_RESOURCE, 12L, upload.uploadId());
        assertThat(stored.key()).isEqualTo("hoclieu/" + upload.uploadId());
        assertThat(bucket.objects()).containsOnlyKeys(stored.key());

        var fake = storage.presignPut(FileCategory.LEARNING_RESOURCE, 12L, "v.mp4", "video/mp4", PDF.length);
        bucket.objects().put("tmp/hoclieu/" + fake.uploadId(), PDF);
        assertCode(
                catchThrowable(() -> storage.confirm(FileCategory.LEARNING_RESOURCE, 12L, fake.uploadId())),
                ApiCode.FILE_CONTENT_MISMATCH);
        assertThat(bucket.objects()).doesNotContainKey("tmp/hoclieu/" + fake.uploadId());
        assertCode(
                catchThrowable(() -> storage.confirm(FileCategory.LEARNING_RESOURCE, 12L, fake.uploadId())),
                ApiCode.FILE_NOT_FOUND);
    }

    @Test
    void unconfiguredBucketFailsClearly() {
        var empty = new FileStorage(s3, presigner, TestStorageProperties.of("", ""), CLOCK, VN);
        assertCode(catchThrowable(() -> empty.store(FileCategory.MEAL, 1L, JPG)), ApiCode.STORAGE_NOT_CONFIGURED);
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException ex) {
            return ex;
        }
    }
}
