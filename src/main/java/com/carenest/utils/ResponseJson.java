package com.carenest.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ResponseJson<T> {

    private final int code;
    /** Mã lỗi nghiệp vụ 4 số ({@link ApiCode#getErrorCode()}); null khi thành công hoặc lỗi chưa gắn mã. */
    private final Integer errorCode;

    private final String desc;
    private final T data;

    private ResponseJson(int code, Integer errorCode, String desc, T data) {
        this.code = code;
        this.errorCode = errorCode;
        this.desc = desc;
        this.data = data;
    }

    /** Chỉ tạo body, dùng khi phải tự ghi response ngoài controller (vd. filter). */
    public static <T> ResponseJson<T> of(ApiCode apiCode, String desc, T data) {
        return new ResponseJson<>(apiCode.getCode(), apiCode.getErrorCode(), desc, data);
    }

    public static <T> ResponseEntity<ResponseJson<T>> toJsonWithData(ApiCode apiCode, String desc, T data) {
        return ResponseEntity.status(apiCode.getHttpStatus()).body(of(apiCode, desc, data));
    }

    public static ResponseEntity<ResponseJson<Void>> toJson(ApiCode apiCode, String desc) {
        return toJsonWithData(apiCode, desc, null);
    }
}
