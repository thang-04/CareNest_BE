# Security Design

## Threat model ngắn

Dữ liệu cần bảo vệ: thông tin trẻ em, sức khỏe, quan hệ phụ huynh, tài khoản. Rủi ro chính: lộ dữ liệu ngoài scope (campus/lớp/trẻ khác), lộ dữ liệu qua AI provider/log, chiếm tài khoản.

## Authentication

**Keycloak + Spring Security OAuth2 Resource Server** (ADR-0011). Contract: `docs/contracts/AUTH_CONTRACT.md`; vận hành: `docs/system/KEYCLOAK.md`.

- Keycloak: đăng nhập (Authorization Code + PKCE), băm mật khẩu, password policy, reset mật khẩu, khóa tạm khi sai 5 lần (PROPOSED), session, access token 5 phút + refresh token xoay vòng, realm role toàn cục.
- BE: kiểm JWT tại chỗ bằng JWKS có cache (chữ ký, `iss`, `aud=carenest-api`, `exp`/`nbf`), map `realm_access.roles` → `ROLE_*` chỉ giữ role của nền tảng theo claim `azp` (ADR-0022: Web = ADMIN/PRINCIPAL/VICE_PRINCIPAL/TEACHER, App = TEACHER/KITCHEN_STAFF/PARENT; hết role, client lạ hoặc thiếu `azp` ⇒ 403 mọi API), `@PreAuthorize`; 401/403 dạng `ResponseJson`. Stateless, tắt CSRF (chỉ xác thực bằng header Bearer, không cookie), CORS theo danh sách origin.
- BE **không** có login/refresh, không lưu mật khẩu hay refresh token, không tự ký JWT, không log token/header `Authorization`.
- Ngoại lệ Web (ADR-0013, ADR-0015): gateway opt-in `/auth/**` nhận mật khẩu qua confidential client Direct Grant, refresh token mã hóa trong cookie HttpOnly + CSRF cookie/header cho POST `/auth/**`; dòng PKCE/CSRF ở trên áp cho API Bearer, Mobile và Web legacy. Gateway chỉ cấp phiên cho role ADMIN/PRINCIPAL/VICE_PRINCIPAL/TEACHER (AUTH-21).
- `ActiveAccountInterceptor`: **mọi** request API có token phải thuộc tài khoản `user_accounts` ACTIVE (403 `Account is not provisioned` / `Account is inactive`), không phụ thuộc controller có khai báo `CurrentUser` hay không.
- Keycloak tắt khi API chưa cache JWKS ⇒ 503 `Authentication service unavailable` (BUG-261006-jwks-unavailable-returns-500); đã cache thì token còn hạn vẫn kiểm được.
- Client `carenest-dev` (password grant) chỉ bật khi `CARENEST_DEV_CLIENT_ENABLED=true` (compose dev); realm import mặc định tắt.
- `logging.level.org.springframework.web.client: INFO` cố định để bật DEBUG không in body RestClient (client_secret, mật khẩu tạm).
- Giới hạn còn mở: `docs/knowledge/KNOWN_ISSUES.md` KI-003..KI-009.
- Yêu cầu tối thiểu (ACCEPTED) vẫn giữ: HTTPS khi lên server, không log token/mật khẩu.

## Luồng xác thực và xử lý lỗi (đã implement)

```text
Client (Web/Mobile)          Keycloak (realm carenest)              CareNest API
  | Authorization Code+PKCE ->|                                          |
  |<- access token (5') + refresh token (xoay vòng, dùng 1 lần)          |
  |-- Authorization: Bearer <access token> ----------------------------->|
  |                           |<-- JWKS (chỉ khi chưa cache / đổi key) --| BearerTokenAuthenticationFilter
  |                                     kiểm chữ ký, iss, aud=carenest-api, exp/nbf
  |                                     realm_access.roles -> ROLE_* (whitelist CareNestRole)
  |                                     ActiveAccountInterceptor: sub -> user_accounts ACTIVE
  |                                     @PreAuthorize (@permissionGuard) -> service: *AccessPolicy (scope)
  |<----------------------------------------------- {code, desc, data} --|
```

| Tình huống | HTTP | Nơi xử lý |
| --- | --- | --- |
| Thiếu token, sai chữ ký, hết hạn, sai `iss`/`aud` | 401 `Unauthorized` + `WWW-Authenticate: Bearer` | `RestAuthenticationEntryPoint` |
| Chưa lấy được JWKS (Keycloak tắt, API chưa cache key) | 503 `Authentication service unavailable` | `RestAuthenticationEntryPoint` (failure handler của `BearerTokenAuthenticationFilter`, BUG-261006-jwks-unavailable-returns-500) |
| Token hợp lệ nhưng chưa cấp tài khoản / `INACTIVE` | 403 `Account is not provisioned` / `Account is inactive` | `ActiveAccountInterceptor` |
| Token không còn role nào dùng được trên nền tảng của client (`azp`) | 403 `FORBIDDEN` 1101 | `SecurityConfig` (authority `PLATFORM_DENIED`) ⇒ `RestAccessDeniedHandler` |
| Không có quyền (permission) của endpoint | 403 `Forbidden` | `@PreAuthorize` ⇒ `GlobalExceptionHandler`; trong filter chain ⇒ `RestAccessDeniedHandler` |
| Ngoài phạm vi campus/lớp/trẻ (kể cả id không tồn tại) | 403 | `*AccessPolicy.checkCanXxx` |

Mọi lỗi đi qua `SecurityResponseWriter` / `GlobalExceptionHandler` ⇒ cùng format `ResponseJson`. Không thêm filter JWT tự viết.

### Vòng đời token và phiên

- Access token 5 phút; SSO session idle 30 phút, tối đa 10 giờ; refresh token xoay vòng (`revokeRefreshToken`, max reuse 0); khóa tạm sau 5 lần sai mật khẩu (giá trị realm import dev; production chờ chốt).
- Refresh/logout là việc của client với Keycloak (`docs/contracts/AUTH_CONTRACT.md`). Logout không thu hồi access token đã cấp — còn dùng tối đa 5 phút.
- Khóa tài khoản (`PATCH /accounts/{userId}/status` `INACTIVE`): CareNest chặn ngay ở request kế tiếp (kể cả token còn hạn), Keycloak disable user + hủy session. Admin không tự khóa mình (AUTH-09).

### Trade-off đã chấp nhận

- Kiểm token tại chỗ (không introspection): nhanh, chịu được Keycloak tắt ngắn; đổi lại logout/đổi role chỉ có hiệu lực ở token kế tiếp (≤ 5 phút). Khóa tài khoản không chịu độ trễ này nhờ kiểm `user_accounts` mỗi request.
- Role toàn cục ở Keycloak, phạm vi ở DB CareNest: không cần đồng bộ phân công sang Keycloak; mỗi request API tốn thêm 1 query tài khoản.
- Cấp tài khoản ghi 2 hệ thống không có transaction chung ⇒ bù trừ (xóa user Keycloak khi bước sau lỗi); trường hợp còn sót: KI-004.
- Service account `carenest-backend` có `manage-users` (KI-008, chấp nhận V1).

## Authorization (ADR-0003)

- Permission cấu hình được (ADR-0017): ma trận theo role + ngoại lệ từng người do Hiệu trưởng quản lý (`docs/business/USER_ROLES.md`) + access scope theo assignment/guardian link.
- Kiểm tra tại service; `@PreAuthorize` ở controller chỉ là lớp đầu, **không** thay lọc scope trong query.
- Truy cập theo ID (vd. `/children/{id}`) phải kiểm tra ID thuộc scope — chống IDOR.
- Parent: default-deny với dữ liệu chưa được công bố (ADR-0010).
- System Admin không mặc định có quyền đọc dữ liệu trẻ (AUTH-06, PENDING P-16).

### Hai lớp phân quyền (đã implement)

1. **Permission** — `@PreAuthorize("@permissionGuard.has('CODE')")` ở controller. Quyền hiệu lực = phần cố định của role trong token (ADMIN/PRINCIPAL/PARENT) ∪ (ma trận `role_permissions` ∪ GRANT − REVOKE), tính 1 lần/request trong `CurrentUserService` (`EffectivePermissionResolver`), đổi là có hiệu lực ngay; không nằm trong JWT. Mã lạ ⇒ từ chối. Có quyền KHÔNG có nghĩa được xem mọi dữ liệu (AUTH-13).
2. **Scope** — policy trong `security/authorization/`, gọi ở service với `CurrentUser`. Quyền chỉ có khi **role trong token VÀ phân công/liên kết còn hiệu lực hôm nay** (theo `carenest.time-zone`) cùng khớp.

| Policy | Cho phép khi | Không bao giờ |
| --- | --- | --- |
| `CampusAccessPolicy.canViewCampus` | PRINCIPAL + phân công PRINCIPAL còn hiệu lực (mọi campus); VICE_PRINCIPAL + phân công VP tại campus đó | Bếp (phân công campus của bếp chỉ cho dữ liệu bếp), GV, PH, Admin; quyền đọc mọi cơ sở không mở rộng hàm này |
| `CampusAccessPolicy.canReadCampus` / `canReadAllCampuses` | PRINCIPAL có phân công còn hiệu lực; VICE_PRINCIPAL có phân công VP còn hiệu lực ở campus bất kỳ ⇒ đọc mọi campus (AUTH-20, chốt 31/10; `ALL_CAMPUS_VIEW` đã bỏ — V39) | Như `canViewCampus`; không dùng cho đường ghi |
| `ClassroomAccessPolicy.canViewClassroom` / `canReadClassroom` | TEACHER + phân công lớp đó còn hiệu lực; hoặc `canViewCampus` / `canReadCampus` (campus của lớp) | Phân công lớp đã kết thúc/năm trước (AUTH-03 mở rộng chưa làm) |
| `ChildAccessPolicy.canViewChild` / `canReadChild` | PARENT + liên kết với trẻ; hoặc TEACHER/VP/PRINCIPAL có `canViewClassroom` / `canReadClassroom` (lớp trẻ đang học hôm nay) | Bếp, Admin (không bypass), trẻ không có enrollment hiện tại |

`canView*` = phạm vi được thao tác: mọi đường ghi dùng nhóm này. `canRead*` rộng hơn (PHT xem mọi cơ sở), chỉ cho endpoint đọc; danh sách đọc của BGH lọc bằng `canReadAllCampuses` (campus, phòng, lớp, phân công GV, xếp lớp, điểm danh, sức khỏe, số suất), không tự lọc theo phân công PHT; module quên chuyển sang `canRead*` thì chỉ thiếu xem, không mở quyền ghi.
| `StaffAccessPolicy` (AUTH-15..17) | Tạo: Admin → PRINCIPAL; HT có phân công PRINCIPAL → PHT/GV/bếp; PHT → GV/bếp kèm phân công campus mình. Quản lý (khóa/mở, mật khẩu tạm, chi tiết): Admin mọi tài khoản; HT → PHT/GV/bếp; PHT → GV/bếp đang phân công trong campus mình. Phân công: HT → PHT/GV/bếp; PHT → GV/bếp campus mình | Thao tác HT/Admin (trừ Admin), chính mình, tài khoản `primary_role` null (trừ Admin) |

- Mỗi policy có `canXxx` (boolean) và `checkCanXxx` (ném `GlobalException(FORBIDDEN, ...)`, log warn kèm id — không log dữ liệu cá nhân).
- **403 thay vì 404:** service gọi `checkCanXxx` **trước** khi load resource ⇒ id không tồn tại và id ngoài scope cùng trả 403 cùng `desc` (không lộ id để dò). Chỉ người có scope mới có thể nhận 404.
- Quyền xem lịch sử (lớp năm trước, trẻ đã chuyển lớp) chưa có rule ⇒ từ chối; logic gói trong policy để đổi sau.
- Policy đọc dữ liệu qua service của `organization`/`child`; các service đó không gọi ngược policy (MODULE_MAP). Test: `security/authorization/*PolicyTest` (case GV/PH/VP/bếp/Admin/hết hạn/đa role).
- Chống leo quyền (AUTH-11, AUTH-12): `PERMISSION_MANAGE` chỉ PRINCIPAL, không giao được; ma trận chỉ nhận VICE_PRINCIPAL/TEACHER/KITCHEN_STAFF (CHECK DB); không tự tạo ngoại lệ cho mình (API + CHECK `created_by <> user_id`); đích có PRINCIPAL/ADMIN không có ngoại lệ. Quyền ghi chỉ cấu hình cho TEACHER khi policy ghi còn dùng chung policy xem.

## Dữ liệu trẻ & AI (ADR-0007)

- Data minimization: AI chỉ nhận dữ liệu cần cho task, bí danh hóa, không tên/ngày sinh/ảnh.
- Không log prompt/response chứa dữ liệu trẻ.
- Provider external ⇒ cần xem điều khoản lưu dữ liệu của provider trước khi bật (ghi vào ADR-0007 khi chốt).

## Secret & cấu hình

- Secret qua biến môi trường/secret store; không commit `.env`, key, credential.
- `KEYCLOAK_ADMIN_CLIENT_SECRET` (service account `carenest-backend`, quyền `manage-users`) tương đương toàn quyền tài khoản realm: chỉ để trong secret store, xoay ngay khi nghi lộ (KI-008, chấp nhận cho V1).
- Test/fixture dùng dữ liệu giả.

## Audit

Hành động nhạy cảm ghi AuditEvent (`docs/modules/audit.md`). Thay đổi phân quyền ghi `permission_change_log` (người đổi, trước/sau, lý do — AUTH-14).
