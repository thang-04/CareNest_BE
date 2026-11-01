# React auth gateway — Phase 2 verification

Ngày2026-10-12, branch hiện có `feature/authentication`. User duyệt Phase2 bằng “ok tiếp”; các lần “tiếp đi” tiếp tục cùng phạm vi. Không commit/push/tạo branch; FE/APP không sửa. `.claude/settings.json` và `plans/` là dirty work có trước, giữ nguyên.

## Kết quả

Backend có login, csrf, refresh, logout, first-login setup, forgot/OTP/resend/verify/reset và protected change-password theo `docs/contracts/AUTH_CONTRACT.md`. Keycloak giữ quyền xác minh credentials, policy, native brute-force và ký JWT. Feature mặc định tắt; không deploy hoặc cập nhật realm thật.

- Opaque HttpOnly session cookie; DB lưu hash handle và AES-GCM access/refresh token, AAD gắn session/purpose. CORS auth chỉ exact origins, CSRF bắt buộc auth mutations kể cả Bearer header.
- ACTIVE/provisioned/profile check trước cấp session. Temporary login chỉ428 + proof, không cookie phiên/access/refresh token.
- Refresh có lease owner/expiry fencing, transaction kết thúc trước HTTP, callers BUSY chờ cùng kết quả. Provider invalid_grant refresh trả401; outage503. Uncertain rotation fail closed.
- OTP6 số/5m, resend60s, 5 sai mỗi request kể cả resend; 5 send mỗi email-hash/fixed30m bucket chung forgot/resend. Unknown/inactive/throttled email có generic response shape. Proof một lần, không reset theo userId.
- Current-password/recovery dùng HMAC assertion60s ràng buộc subject/realm/issuer/audience/purpose/nonce, exchange service-account-authenticated thành capability10m. Mutations thành công revoke session; lỗi uncertain consume proof, policy rejection cho sửa lại.
- Provisioning giữ contract; gateway bật gửi link React, tắt gửi legacy action email. Disable account revoke local auth trước Keycloak sync. Cleanup60s khi bật, session/quota expired và challenge cutoff1h sau expiry.
- OpenAPI auth routes `security: []`, internal CsrfToken ẩn; API business vẫn Bearer. API_PREFIX hiện có tiếp tục áp dụng.

## Bằng chứng chạy local

| Kiểm tra | Kết quả |
| --- | --- |
| `node scripts/verify.mjs` sau source edit cuối | `VERIFY PASS full` —196 tests, fail0, err0, skip0; ArchUnit/snapshot/Spotless ok,48s |
| Extension Maven verify (provider module riêng) |38 tests, fail0, err0, skip0; Spotless ok; đối chiếu surefire XML |
| Keycloak26.8.0/PostgreSQL17/Mailpit E2E |26/26 pass; project riêng `carenest-auth-e2e-caa94f1e`, đã dọn |
| OpenAPI semantic regression + regenerate có chủ đích |3 tests, fail0, err0, skip0; auth không Bearer, không query CsrfToken;428 khớp code |
| Regression session/OTP/client/HTTP/provisioning |62 tests pass trước thêm review regressions; các tests mới đã có trong full196 |

E2E gồm temp password, replay/policy/disabled subject, privileged service boundary, invalid signed assertion matrix, concurrent one-use assertion, current/recovery password thay thế, email React/OTP, native brute-force/rotation, Mobile/Web public PKCE giữ nguyên, SMTP outage và feature disable. Không in token/password/OTP body; chỉ dùng dữ liệu giả.

## RED → GREEN và review

- Context lỗi thiếu adapter bean: full25 context errors → context/snapshot3 tests pass.
- OAuth error nullable và phân loại400:2 failures → adapter suite pass; không dựa error_description.
- Regression boundary:60 tests/8 failures →62 tests pass; expired lease/idle, resend attempt budget, token cleanup và uncertain proof đã sửa. Fixture jwt() tự skip CSRF được thay bằng header thật.
- Review độc lập tĩnh:2 Medium (refresh invalid_grant thành503, cleanup không caller), không Critical/High. Đã sửa + regression; recheck không còn blocker.
- Snapshot review phát hiện auth kế thừa Bearer global và internal CsrfToken thành query; annotations/test/snapshot đã sửa đúng semantics.

Engineering memory: `BUG-261012-web-auth-client-bean`, `BUG-261012-web-auth-oauth-error-mapping`, `BUG-261012-web-auth-session-otp-boundaries`, cập nhật `BUG-261012-keycloak-mock-expectation-order`. Module card/contract/CROSS_REPO_MAP/DATA_DICTIONARY/CURRENT_STATE cập nhật.

## Giới hạn và handoff

- HTTP/controller, service và PostgreSQL gateway kiểm chứng trong Spring tests; provider/SMTP kiểm chứng bằng real-container E2E riêng. Chưa có browser React→BE→Keycloak full end-to-end; đó là Phase3.
- Native MFA không bị bỏ qua; gateway chưa có form TOTP. User phải có account CareNest ACTIVE; role/scope cũ không được mở rộng.
- Access JWT đã phát còn hiệu lực tối đa5m sau logout/password mutation; ACTIVE check vẫn chặn account bị khóa. Không cam kết tức thời revocation mọi access JWT.
- Cleanup chỉ chạy khi feature bật; nếu ngừng gateway lâu dài cần retention vận hành riêng. Đây là source local được kiểm chứng, chưa chứng minh deployment production.
- Consumer FE Phase3: login, forgot-password, verify-otp, reset-password, change-password, AuthContext, HTTP client; access token memory-only, CSRF từ response body, credentialsinclude auth routes,403/503 xử lý rõ. Mobile giữ PKCE, không chuyển Direct Grant.
- Theo `.ai/workflows/plan-change.md` bước6, Phase2 dừng chờ review; chưa sửa repo FE.
