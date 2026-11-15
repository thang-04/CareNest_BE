package com.carenest.integration.ai;

import lombok.Getter;

/** Lỗi gọi AI; service đổi sang AI_SERVICE_UNAVAILABLE (8001) hoặc AI_TIMEOUT (8002), giữ dữ liệu người dùng. */
@Getter
public class AiException extends RuntimeException {

    public enum Reason {
        UNAVAILABLE,
        TIMEOUT
    }

    private final Reason reason;

    public AiException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
