package com.carenest.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Mã kết quả dùng trong {@link ResponseJson}. {@code code} luôn trùng HTTP status của response.
 * Chỉ thêm giá trị mới khi cần một HTTP status chưa có; lỗi cùng status phân biệt bằng {@code desc}.
 */
@Getter
@RequiredArgsConstructor
public enum ApiCode {
    SUCCESSFUL(HttpStatus.OK, "Success"),
    CREATED(HttpStatus.CREATED, "Created"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    /** Alias của BAD_REQUEST, giữ tương thích tài liệu kiến trúc. */
    UNSUCCESSFUL(HttpStatus.BAD_REQUEST, "Unsuccessful"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    /** Trùng dữ liệu hoặc sai trạng thái nghiệp vụ (vd. số suất ăn đã được xác nhận). */
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File too large"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
    /** Dịch vụ ngoài (AI, storage) không khả dụng. */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"),
    /** Dịch vụ ngoài (AI) quá thời gian chờ. */
    GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Upstream timeout");

    private final HttpStatus httpStatus;
    private final String defaultDesc;

    public int getCode() {
        return httpStatus.value();
    }
}
