package com.carenest.exception;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.utils.ApiCode;
import java.util.List;
import lombok.Getter;

/**
 * Lỗi nghiệp vụ từ service, tạo bằng {@link #of} theo mã tập trung trong {@link ApiCode}; message (câu tiếng Việt của mã)
 * được trả cho client làm {@code desc}, nên không chứa thông tin nội bộ.
 */
@Getter
public class GlobalException extends RuntimeException {

    private final ApiCode apiCode;

    /** Lỗi theo ô (C-5): trả trong {@code data} dạng {@code [{ field, message }]}; rỗng = lỗi không gắn ô. */
    private final List<FieldErrorResponse> fieldErrors;

    private GlobalException(ApiCode apiCode, String desc, Throwable cause, List<FieldErrorResponse> fieldErrors) {
        super(desc, cause);
        this.apiCode = apiCode;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** Lỗi theo mã tập trung trong {@link ApiCode}: câu lỗi lấy từ mã, {@code args} thay vào {0}, {1}... */
    public static GlobalException of(ApiCode apiCode, Object... args) {
        return new GlobalException(apiCode, apiCode.format(args), null, List.of());
    }

    /** Như {@link #of(ApiCode, Object...)} nhưng giữ nguyên nhân gốc. */
    public static GlobalException withCause(ApiCode apiCode, Throwable cause, Object... args) {
        return new GlobalException(apiCode, apiCode.format(args), cause, List.of());
    }

    /**
     * Lỗi kèm danh sách ô sai do service kiểm (400 {@code INVALID_REQUEST_DATA}, 409 trùng...): {@code field} là đường
     * dẫn ô FE dùng (vd. {@code guardians.0.phone}), {@code message} là câu tiếng Việt hiển thị dưới ô.
     */
    public static GlobalException withFieldErrors(ApiCode apiCode, List<FieldErrorResponse> fieldErrors) {
        return new GlobalException(apiCode, apiCode.getDefaultDesc(), null, fieldErrors);
    }
}
