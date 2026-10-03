package com.carenest;

import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import com.carenest.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StorageServiceTests {

    private final S3Client s3Client = mock(S3Client.class);
    private final StorageService storageService = new StorageService(s3Client, mock(S3Presigner.class));

    StorageServiceTests() {
        ReflectionTestUtils.setField(storageService, "bucket", "test");
    }

    @Test
    void acceptsRealImagesByContentNotByHeader() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 1, 2};
        // Content-Type lies, magic bytes decide.
        String key = storageService.uploadImage(new MockMultipartFile("file", "x.txt", "text/plain", jpeg), "children/1");

        assertThat(key).startsWith("children/1/").endsWith(".jpg");
        verify(s3Client).putObject(anyPut(), any(RequestBody.class));
    }

    @Test
    void rejectsNonImagesAndEmptyFiles() {
        MockMultipartFile fake = new MockMultipartFile("file", "x.png", "image/png", "<script>".getBytes());
        MockMultipartFile empty = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storageService.uploadImage(fake, "users/1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE);
        assertThatThrownBy(() -> storageService.uploadImage(empty, "users/1"))
                .isInstanceOf(AppException.class);
        verify(s3Client, never()).putObject(anyPut(), any(RequestBody.class));
    }

    private static Consumer<PutObjectRequest.Builder> anyPut() {
        return any();
    }
}
