package com.carenest.integration.ai;

import com.carenest.utils.enums.AiErrorReason;
import lombok.Getter;

@Getter
public class AiClientException extends RuntimeException {

    private final AiErrorReason reason;

    public AiClientException(AiErrorReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
