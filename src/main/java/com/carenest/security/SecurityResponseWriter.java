package com.carenest.security;

import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/** Lỗi 401/403 ở filter xảy ra trước controller nên phải tự ghi ResponseJson tại đây. */
final class SecurityResponseWriter {

    private SecurityResponseWriter() {}

    static void write(HttpServletResponse response, ObjectMapper objectMapper, ApiCode apiCode, String desc)
            throws IOException {
        response.setStatus(apiCode.getCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ResponseJson.of(apiCode, desc, null));
    }
}
