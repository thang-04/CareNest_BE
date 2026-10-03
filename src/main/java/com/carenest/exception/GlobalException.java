package com.carenest.exception;

import com.carenest.utils.ApiCode;
import lombok.Getter;

/** Lỗi nghiệp vụ từ service; message được trả cho client làm {@code desc}, nên không chứa thông tin nội bộ. */
@Getter
public class GlobalException extends RuntimeException {

    private final ApiCode apiCode;

    public GlobalException(ApiCode apiCode, String desc) {
        super(desc);
        this.apiCode = apiCode;
    }

    public GlobalException(ApiCode apiCode, String desc, Throwable cause) {
        super(desc, cause);
        this.apiCode = apiCode;
    }
}
