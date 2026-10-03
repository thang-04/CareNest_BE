package com.carenest.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

/**
 * Response chung {@code {code, desc, data}}: HTTP status lấy từ {@link ApiCode}, {@code data} luôn có mặt kể cả null.
 * Service không dùng class này; chỉ controller và GlobalExceptionHandler.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ResponseJson<T> {

    private final int code;
    private final String desc;
    private final T data;

    private ResponseJson(int code, String desc, T data) {
        this.code = code;
        this.desc = desc;
        this.data = data;
    }

    /** Chỉ tạo body, dùng khi phải tự ghi response ngoài controller (vd. filter). */
    public static <T> ResponseJson<T> of(ApiCode apiCode, String desc, T data) {
        return new ResponseJson<>(apiCode.getCode(), desc, data);
    }

    public static <T> ResponseEntity<ResponseJson<T>> toJsonWithData(ApiCode apiCode, String desc, T data) {
        return ResponseEntity.status(apiCode.getHttpStatus()).body(of(apiCode, desc, data));
    }

    public static ResponseEntity<ResponseJson<Void>> toJson(ApiCode apiCode, String desc) {
        return toJsonWithData(apiCode, desc, null);
    }
}
