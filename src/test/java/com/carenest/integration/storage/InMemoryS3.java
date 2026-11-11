package com.carenest.integration.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Bucket giả trong bộ nhớ cho test: gắn vào một mock {@link S3Client} tạo với {@code Answers.CALLS_REAL_METHODS} (bản
 * nhận Consumer của SDK gọi xuống bản nhận Request), để {@link FileStorage} thật chạy đủ kiểm tên/giới hạn/nội dung.
 */
public final class InMemoryS3 {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    private InMemoryS3() {}

    public static InMemoryS3 install(S3Client s3) {
        InMemoryS3 store = new InMemoryS3();
        doAnswer(call -> store.put(call.getArgument(0), call.getArgument(1)))
                .when(s3)
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));
        doAnswer(call -> {
                    byte[] content = store.require(((HeadObjectRequest) call.getArgument(0)).key());
                    return HeadObjectResponse.builder()
                            .contentLength((long) content.length)
                            .build();
                })
                .when(s3)
                .headObject(any(HeadObjectRequest.class));
        doAnswer(call -> ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(), store.read(call.getArgument(0))))
                .when(s3)
                .getObjectAsBytes(any(GetObjectRequest.class));
        doAnswer(call -> new ResponseInputStream<>(
                        GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(store.read(call.getArgument(0))))))
                .when(s3)
                .getObject(any(GetObjectRequest.class));
        doAnswer(call -> {
                    store.objects.remove(((DeleteObjectRequest) call.getArgument(0)).key());
                    return DeleteObjectResponse.builder().build();
                })
                .when(s3)
                .deleteObject(any(DeleteObjectRequest.class));
        doAnswer(call -> {
                    CopyObjectRequest r = call.getArgument(0);
                    store.objects.put(r.destinationKey(), store.require(r.sourceKey()));
                    return CopyObjectResponse.builder().build();
                })
                .when(s3)
                .copyObject(any(CopyObjectRequest.class));
        return store;
    }

    public Map<String, byte[]> objects() {
        return objects;
    }

    PutObjectResponse put(PutObjectRequest request, RequestBody body) {
        if ("*".equals(request.ifNoneMatch()) && objects.containsKey(request.key()))
            throw (S3Exception) S3Exception.builder()
                    .statusCode(412)
                    .awsErrorDetails(
                            AwsErrorDetails.builder().errorCode("PreconditionFailed").build())
                    .build();
        try (InputStream input = body.contentStreamProvider().newStream()) {
            objects.put(request.key(), input.readAllBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return PutObjectResponse.builder().build();
    }

    private byte[] read(GetObjectRequest request) {
        byte[] content = require(request.key());
        if (request.range() == null) return content;
        // Chỉ dạng "bytes=a-b" mà FileStorage dùng
        String[] range = request.range().substring("bytes=".length()).split("-");
        int from = Integer.parseInt(range[0]);
        int to = Math.min(Integer.parseInt(range[1]) + 1, content.length);
        return Arrays.copyOfRange(content, Math.min(from, content.length), to);
    }

    private byte[] require(String key) {
        byte[] content = objects.get(key);
        if (content == null) throw NoSuchKeyException.builder().statusCode(404).build();
        return content;
    }
}
