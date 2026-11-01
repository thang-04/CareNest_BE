# Keycloak extension và backend gateway cho auth React

Trạng thái: source extension Phase 1 và backend gateway Phase 2 đã có; FE chưa ghép (Phase 3). Bằng chứng test và giới hạn ở `docs/quality/reports/2026-10-12-react-auth-phase2.md`. Gateway mặc định tắt; triển khai thật cần hoàn tất Phase 3 và review bảo mật.

Nguồn quyết định: `docs/decisions/ADR-0013-react-auth-gateway.md`; kế hoạch `docs/plans/active/2026-10-11-react-auth-keycloak.md`.

## Thành phần

- Module Maven độc lập `docker/keycloak/extensions/carenest-auth/`, Java 21, Keycloak 26.8.0. Provider JAR không đóng gói thêm dependency runtime. RESTEasy chỉ thuộc test scope.
- Private client `carenest-auth-gateway`: confidential, không browser redirect, không service account, mặc định DISABLED.
- Bootstrap **copy native direct-grant flow** sau khi Keycloak tạo built-in flows, giữ username/password/conditional OTP rồi thêm checkpoint REQUIRED cuối cùng. Không thay flow của Web/Mobile hiện có.
- Mật khẩu tạm chỉ phát `setup_token` opaque 256 bit, hạn 10 phút. Token gắn realm/client/user/credential fingerprint, chỉ bản mới nhất còn hiệu lực; lưu hash token trong one-use cache. Không phát access/refresh token ở bước này.
- Thao tác provider phải dùng bearer của service account `carenest-backend` đang hoạt động và có `realm-management:manage-users`. Không chấp nhận JWT của người dùng thông thường.
- Cấp/redeem capability được khóa theo realm/user tới khi transaction Keycloak commit/rollback. Giữ transaction timeout dưới 600 giây (lease của khóa); sau lỗi node, khóa có thể giữ tới khi lease hết hạn.

## Giao diện provider (nội bộ, không phải API public của FE)

Prefix `/realms/carenest/carenest-auth`:

| Method/path | Body | Điều kiện |
| --- | --- | --- |
| POST `/password/setup` | `{token,newPassword,confirmPassword}` | Service account + capability đã được kiểm mật khẩu; không có reset theo userId |
| POST `/password/capability` | `{assertion}` | Service account + HMAC assertion60s một lần, đúng realm/subject/issuer/audience/purpose; trả capability10m |
| POST `/recovery/email` | `{userId,code}` | Service account; chỉ gửi OTP 6 số tới email đang lưu ở Keycloak |
| POST `/setup/email` | `{userId}` | Service account; email dẫn về React `/forgot-password` |

Native token endpoint trả 400 `error=carenest_password_change_required`, `user_id`, `setup_token`, `expires_in=600` cho mật khẩu tạm đã xác thực. Phase 2 sẽ chuyển kết quả nội bộ này thành 428 theo contract Web và kiểm tài khoản CareNest ACTIVE/provisioned trước khi cấp challenge cho FE.

Policy được kiểm trước khi consume capability. Thành công chỉ xóa UPDATE_PASSWORD, hủy capability khác và session online/offline. Các required action khác không bị bỏ qua. Recovery OTP/reset hoàn chỉnh và đổi mật khẩu đang đăng nhập thuộc Phase 2; không dùng đường setup để thay thế bằng userId.

## Build và kiểm thử cục bộ

PowerShell, cwd `D:\CAPSTONE_FALL26\CareNest_CODE\CareNest_BE`:

```powershell
.\mvnw.cmd -f docker/keycloak/extensions/carenest-auth/pom.xml verify
node --test docker/keycloak/auth/*.test.mjs
docker build --tag carenest-keycloak-auth:26.8.0 --file docker/keycloak/auth/Dockerfile docker/keycloak
node docker/keycloak/auth/e2e.mjs
node scripts/check-ai-layer.mjs
node scripts/verify.mjs
```

Docker Desktop phải chạy Linux engine. E2E dùng project `carenest-auth-e2e-<random>`, PostgreSQL tmpfs, Keycloak/Mailpit riêng, cổng ngẫu nhiên chỉ bind loopback; credentials giả sinh ngẫu nhiên, không in ra output. Cuối run chỉ dọn project đó. Diagnostics nằm trong `target/`; không chạm `.env`, stack hoặc volume có sẵn. Log test vẫn phải giữ riêng, không commit.

## Opt-in sau khi review

Không chạy các bước dưới để thử trên DB hiện có khi chưa xem preview. Import không cập nhật realm đã tồn tại.

1. Build provider/image như trên; `node docker/keycloak/auth/prepare.mjs` tạo realm dẫn xuất trong thư mục generated bị ignore, không sửa bản import chuẩn.
2. Cấp `KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET` ngẫu nhiên ít nhất 32 ký tự; origin link email React dùng biến riêng `CARENEST_AUTH_WEB_ORIGIN` (mặc định localhost:5173), không đổi origin client public. Không in/commit secret và không ghi đè `.env` hiện có.
3. Cấp thêm `KEYCLOAK_AUTH_RECOVERY_ASSERTION_SECRET` và `CARENEST_AUTH_ENCRYPTION_KEY`: mỗi khóa32 byte ngẫu nhiên mã hóa base64, độc lập nhau. Assertion secret được chia sẻ BE/extension; encryption key chỉ BE. Điền qua environment, không ghi đè `.env` đang có. `CARENEST_AUTH_ENABLED=false` mặc định; origin credentialed CORS dùng `CARENEST_AUTH_WEB_ORIGIN`. Cookie HTTPS giữ Secure=true; thử HTTP loopback mới đặt `CARENEST_AUTH_SECURE_COOKIES=false`.
4. Kiểm tra `docker compose -f compose.yaml -f compose.auth.yaml config --quiet`. Overlay thay image/import Keycloak và truyền biến gateway cho API; không cập nhật image API đã build hoặc dữ liệu. API image cần build lại khi lấy source mới. Fresh import vẫn tạo gateway DISABLED; không chạy overlay trên DB hiện có khi chưa xem preview.
5. Cấp `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` qua environment; chạy `node docker/keycloak/auth/bootstrap.mjs` để xem preview read-only.
6. Chỉ sau khi duyệt thay đổi cụ thể mới chạy cùng lệnh với `--apply`; không `--enable` thì client vẫn tắt. `--apply --enable` chỉ cho môi trường test đã được duyệt. CLI không log token/secret.

Bootstrap chỉ quản lý client/flow mang marker CareNest và emailTheme; từ chối định nghĩa trùng không có ownership marker. Thay flow/secret sẽ tạm disable client cho tới khi kiểm xong native validators. Không tạo lại realm hoặc reset DB.

Rollback: tắt gateway client, bỏ overlay và quay về ảnh stock; giữ nguyên accounts, public clients và volumes. Không fallback mock auth ở môi trường thật.

## Backend gateway Phase 2

Contract đầy đủ: `docs/contracts/AUTH_CONTRACT.md`. Sau khi bootstrap đúng flow và bật client, bật feature BE với đủ secret/key/origin. FE cần GET `/api/auth/csrf` để lấy token từ response body, gửi cookie + `X-XSRF-TOKEN` cho auth POST; API nghiệp vụ dùng access token Bearer. Cấp tài khoản ở chế độ gateway gửi setup link React; mode tắt giữ legacy action email. Tác vụ cleanup chạy60s khi gateway bật, loại phiên/quota hết hạn và challenge quá cutoff1h sau expiry.

Kiểm chứng local: `node scripts/verify.mjs`; extension `mvnw.cmd -f docker/keycloak/extensions/carenest-auth/pom.xml verify`; build image như trên rồi `node docker/keycloak/auth/e2e.mjs`. E2E chỉ dùng project Docker riêng, dữ liệu giả, loopback và dọn tài nguyên của chính nó. Không dùng lệnh này để reset/cập nhật realm thật.
