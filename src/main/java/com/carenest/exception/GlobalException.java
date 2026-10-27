package com.carenest.exception;

import com.carenest.utils.ApiCode;
import lombok.Getter;

/**
 * Lỗi nghiệp vụ từ service, tạo bằng {@link #of} theo mã tập trung trong {@link ApiCode}; message (câu tiếng Việt của mã)
 * được trả cho client làm {@code desc}, nên không chứa thông tin nội bộ.
 */
@Getter
public class GlobalException extends RuntimeException {

    private final ApiCode apiCode;

    private GlobalException(ApiCode apiCode, String desc) {
        super(desc);
        this.apiCode = apiCode;
    }

    private GlobalException(ApiCode apiCode, String desc, Throwable cause) {
        super(desc, cause);
        this.apiCode = apiCode;
    }

    /** Lỗi theo mã tập trung trong {@link ApiCode}: câu lỗi lấy từ mã, {@code args} thay vào {0}, {1}... */
    public static GlobalException of(ApiCode apiCode, Object... args) {
        return new GlobalException(apiCode, apiCode.format(args));
    }

    /** Như {@link #of(ApiCode, Object...)} nhưng giữ nguyên nhân gốc. */
    public static GlobalException withCause(ApiCode apiCode, Throwable cause, Object... args) {
        return new GlobalException(apiCode, apiCode.format(args), cause);
    }
}
