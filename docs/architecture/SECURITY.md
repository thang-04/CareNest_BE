# Security Design

## Threat model ngắn

Dữ liệu cần bảo vệ: thông tin trẻ em, sức khỏe, quan hệ phụ huynh, tài khoản. Rủi ro chính: lộ dữ liệu ngoài scope (campus/lớp/trẻ khác), lộ dữ liệu qua AI provider/log, chiếm tài khoản.

## Authentication

**Keycloak + Spring Security OAuth2 Resource Server** (ADR-0011). Contract: `docs/contracts/AUTH_CONTRACT.md`; vận hành: `docs/system/KEYCLOAK.md`.

- Keycloak: đăng nhập (Authorization Code + PKCE), băm mật khẩu, password policy, reset mật khẩu, khóa tạm khi sai 5 lần (PROPOSED), session, access token 5 phút + refresh token xoay vòng, realm role toàn cục.
- BE: kiểm JWT tại chỗ bằng JWKS có cache (chữ ký, `iss`, `aud=carenest-api`, `exp`/`nbf`), map `realm_access.roles` → `ROLE_*`, `@PreAuthorize`; 401/403 dạng `ResponseJson`. Stateless, tắt CSRF (chỉ xác thực bằng header Bearer, không cookie), CORS theo danh sách origin.
- BE **không** có login/refresh, không lưu mật khẩu hay refresh token, không tự ký JWT, không log token/header `Authorization`.
- `ActiveAccountInterceptor`: **mọi** request API có token phải thuộc tài khoản `user_accounts` ACTIVE (403 `Account is not provisioned` / `Account is inactive`), không phụ thuộc controller có khai báo `CurrentUser` hay không.
- Keycloak tắt khi API chưa cache JWKS ⇒ 503 `Authentication service unavailable` (BUG-001); đã cache thì token còn hạn vẫn kiểm được.
- Client `carenest-dev` (password grant) chỉ bật khi `CARENEST_DEV_CLIENT_ENABLED=true` (compose dev); realm import mặc định tắt.
- `logging.level.org.springframework.web.client: INFO` cố định để bật DEBUG không in body RestClient (client_secret, mật khẩu tạm).
- Giới hạn còn mở: `docs/knowledge/KNOWN_ISSUES.md` KI-003..KI-009.
- Yêu cầu tối thiểu (ACCEPTED) vẫn giữ: HTTPS khi lên server, không log token/mật khẩu.

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

### Hai lớp phân quyền (đã implement)

1. **Role** — `@PreAuthorize` ở controller (role toàn cục từ token Keycloak). Đúng role KHÔNG có nghĩa được xem mọi dữ liệu.
2. **Scope** — policy trong `security/authorization/`, gọi ở service với `CurrentUser`. Quyền chỉ có khi **role trong token VÀ phân công/liên kết còn hiệu lực hôm nay** (theo `carenest.time-zone`) cùng khớp.

| Policy | Cho phép khi | Không bao giờ |
| --- | --- | --- |
| `CampusAccessPolicy.canViewCampus` | PRINCIPAL + phân công PRINCIPAL còn hiệu lực (mọi campus); VICE_PRINCIPAL + phân công VP tại campus đó | Bếp (phân công campus của bếp chỉ cho dữ liệu bếp), GV, PH, Admin |
| `ClassroomAccessPolicy.canViewClassroom` | TEACHER + phân công lớp đó còn hiệu lực; hoặc `canViewCampus(campus của lớp)` | Phân công lớp đã kết thúc/năm trước |
| `ChildAccessPolicy.canViewChild` | PARENT + liên kết với trẻ; hoặc TEACHER/VP/PRINCIPAL có `canViewClassroom(lớp trẻ đang học hôm nay)` | Bếp, Admin (không bypass), trẻ không có enrollment hiện tại |

- Mỗi policy có `canXxx` (boolean) và `checkCanXxx` (ném `GlobalException(FORBIDDEN, ...)`, log warn kèm id — không log dữ liệu cá nhân).
- **403 thay vì 404:** service gọi `checkCanXxx` **trước** khi load resource ⇒ id không tồn tại và id ngoài scope cùng trả 403 cùng `desc` (không lộ id để dò). Chỉ người có scope mới có thể nhận 404.
- Quyền xem lịch sử (lớp năm trước, trẻ đã chuyển lớp) chưa có rule ⇒ từ chối; logic gói trong policy để đổi sau.
- Policy đọc dữ liệu qua service của `organization`/`child`; các service đó không gọi ngược policy (MODULE_MAP). Test: `security/authorization/*PolicyTest` (case GV/PH/VP/bếp/Admin/hết hạn/đa role).

## Dữ liệu trẻ & AI (ADR-0007)

- Data minimization: AI chỉ nhận dữ liệu cần cho task, bí danh hóa, không tên/ngày sinh/ảnh.
- Không log prompt/response chứa dữ liệu trẻ.
- Provider external ⇒ cần xem điều khoản lưu dữ liệu của provider trước khi bật (ghi vào ADR-0007 khi chốt).

## Secret & cấu hình

- Secret qua biến môi trường/secret store; không commit `.env`, key, credential.
- `KEYCLOAK_ADMIN_CLIENT_SECRET` (service account `carenest-backend`, quyền `manage-users`) tương đương toàn quyền tài khoản realm: chỉ để trong secret store, xoay ngay khi nghi lộ (KI-008, chấp nhận cho V1).
- Test/fixture dùng dữ liệu giả.

## Audit

Hành động nhạy cảm ghi AuditEvent (`docs/modules/audit.md`).
