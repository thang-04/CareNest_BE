# Security Design

## Threat model ngắn

Dữ liệu cần bảo vệ: thông tin trẻ em, sức khỏe, quan hệ phụ huynh, tài khoản. Rủi ro chính: lộ dữ liệu ngoài scope (campus/lớp/trẻ khác), lộ dữ liệu qua AI provider/log, chiếm tài khoản.

## Authentication

Chưa chốt cơ chế (session vs JWT, refresh token, đăng nhập phụ huynh). Contract: `docs/contracts/AUTH_CONTRACT.md` (SKELETON). Khi chốt ⇒ ADR + cập nhật contract + FE/APP AUTH_FLOW.

Yêu cầu tối thiểu (ACCEPTED): mật khẩu băm bằng thuật toán chuẩn (BCrypt/Argon2 qua Spring Security), HTTPS, không log token/mật khẩu, khóa tạm khi đăng nhập sai lặp (ngưỡng PROPOSED).

## Response 401/403 (thiết kế tham khảo, chuyển từ guide 8.5)

> **Chưa triển khai** — team đang chốt phương án auth. Phần dưới là thiết kế tham khảo, áp dụng khi thêm Spring Security. `ObjectMapper` lấy từ `tools.jackson.databind` (Jackson 3).

Lỗi xác thực/phân quyền ở filter xảy ra **trước** controller nên `@RestControllerAdvice` không bắt được. PHẢI có handler riêng ghi `ResponseJson`:

```java
// security/RestAuthenticationEntryPoint.java
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        SecurityResponseWriter.write(response, objectMapper, ApiCode.UNAUTHORIZED, "Unauthorized");
    }
}

// security/RestAccessDeniedHandler.java
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        SecurityResponseWriter.write(response, objectMapper, ApiCode.FORBIDDEN, "Forbidden");
    }
}

// security/SecurityResponseWriter.java
final class SecurityResponseWriter {
    private SecurityResponseWriter() {}

    static void write(HttpServletResponse response, ObjectMapper objectMapper,
                      ApiCode apiCode, String desc) throws IOException {
        response.setStatus(apiCode.getCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ResponseJson.of(apiCode, desc, null));
    }
}
```

Đăng ký trong `SecurityConfig`:

```java
http.exceptionHandling(ex -> ex
        .authenticationEntryPoint(restAuthenticationEntryPoint)
        .accessDeniedHandler(restAccessDeniedHandler));
```

Filter JWT tự viết KHÔNG ĐƯỢC tự ghi response lỗi theo dạng khác; để lỗi đi qua entry point ở trên.

## Authorization (ADR-0003)

- Permission theo role (`docs/business/USER_ROLES.md`) + access scope theo assignment/guardian link.
- Kiểm tra tại service; `@PreAuthorize` ở controller chỉ là lớp đầu, **không** thay lọc scope trong query.
- Truy cập theo ID (vd. `/children/{id}`) phải kiểm tra ID thuộc scope — chống IDOR.
- Parent: default-deny với dữ liệu chưa được công bố (ADR-0010).
- System Admin không mặc định có quyền đọc dữ liệu trẻ (AUTH-06, PENDING P-16).

## Dữ liệu trẻ & AI (ADR-0007)

- Data minimization: AI chỉ nhận dữ liệu cần cho task, bí danh hóa, không tên/ngày sinh/ảnh.
- Không log prompt/response chứa dữ liệu trẻ.
- Provider external ⇒ cần xem điều khoản lưu dữ liệu của provider trước khi bật (ghi vào ADR-0007 khi chốt).

## Secret & cấu hình

- Secret qua biến môi trường/secret store; không commit `.env`, key, credential.
- Test/fixture dùng dữ liệu giả.

## Audit

Hành động nhạy cảm ghi AuditEvent (`docs/modules/audit.md`).
