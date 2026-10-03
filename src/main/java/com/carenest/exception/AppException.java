package com.carenest.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * @param args values for the placeholders (%d, %s) in the error message, if any
     */
    public AppException(ErrorCode errorCode, Object... args) {
        super(args.length == 0 ? errorCode.getMessage() : errorCode.getMessage().formatted(args));
        this.errorCode = errorCode;
    }
}
