package com.carenest.integration.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.carenest.exception.GlobalException;
import java.io.ByteArrayInputStream;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class StorageServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void uploadUsesOpaqueKeyPrivateMetadataAndExactStream() throws Exception {
        S3Client s3 = mock(S3Client.class);
        StorageService storage = new StorageService(s3, "private-test", "education/");
        doAnswer(call -> {
                    PutObjectRequest.Builder b = PutObjectRequest.builder();
                    ((Consumer<PutObjectRequest.Builder>) call.getArgument(0)).accept(b);
                    var request = b.build();
                    assertThat(request.bucket()).isEqualTo("private-test");
                    assertThat(request.key()).matches("education/[a-f0-9-]{36}");
                    assertThat(request.acl()).isNull();
                    assertThat(request.cacheControl()).isEqualTo("private, no-store");
                    RequestBody body = call.getArgument(1);
                    assertThat(body.optionalContentLength()).contains(4L);
                    assertThat(body.contentStreamProvider().newStream().readAllBytes())
                            .isEqualTo("demo".getBytes());
                    return PutObjectResponse.builder().build();
                })
                .when(s3)
                .putObject(any(Consumer.class), any(RequestBody.class));
        String key = storage.store(new ByteArrayInputStream("demo".getBytes()), 4);
        assertThat(key).matches("[a-f0-9-]{36}");
        assertThatThrownBy(() -> storage.load("../outside")).isInstanceOf(GlobalException.class);
        storage.remove(key);
        verify(s3).deleteObject(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedUploadCleansOnlyNewKeyAndReportsFailure() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("test unavailable"));
        StorageService storage = new StorageService(s3, "private-test", "education/");
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream("demo".getBytes()), 4))
                .isInstanceOf(GlobalException.class);
        verify(s3).deleteObject(any(Consumer.class));
    }

    @Test
    void missingBucketNeverFallsBackToLocalSuccess() {
        S3Client s3 = mock(S3Client.class);
        StorageService storage = new StorageService(s3, "", "education/");
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream("demo".getBytes()), 4))
                .isInstanceOf(GlobalException.class);
        verifyNoInteractions(s3);
    }
}
