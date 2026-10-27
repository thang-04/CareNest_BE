# Issue Index

Search đầu tiên khi gặp bug, lỗi môi trường, case lạ. Grep chuỗi lỗi nguyên văn, module, từ khóa VN/EN, CamelCase class. 1 issue = 1 dòng, **kể cả bug đang mở** (`open`). Chi tiết và các cách đã thử nằm trong `incidents/<ID>.md`. Cách ghi: `.ai/workflows/update-knowledge.md` T2.

ID: `BUG-` (code) · `CASE-` (edge case nghiệp vụ) · `ENV-` (môi trường/build/deploy), dạng `<loại>-YYMMDD-<slug>` (vd. `BUG-261004-meal-count-stale`). Status: `open` | `workaround` | `fixed`.

| ID | Module | Triệu chứng (từ khóa + chuỗi lỗi) | Root cause (1 câu, `?` nếu chưa rõ) | Status |
| --- | --- | --- | --- | --- |
| CASE-261027-activity-media-positional-identity | education-plan | Reorder hoạt động làm day pick cùng ID đổi nội dung | goalId#index theo vị trí; phải kiểm saved text và fingerprint khi ghép media | fixed |
| BUG-261023-edu-theme-tick-lost-on-resave | education-plan | Lưu lại kế hoạch năm mất dấu tích chủ đề; test `themeIds` rỗng `Expecting actual: []` | Xóa tick bằng JPQL rồi `saveAll` → `merge` bỏ qua tick cùng khóa còn managed trong persistence context | fixed |
| ENV-261013-docker-desktop-init-timeout | deployment/identity-access | FE localhost:5173 lên, API:8080/Keycloak:8180/PG:5432 không nghe; `docker start`/`docker pull` treo | ? Docker Desktop `starting`, init control API `/ping` timeout; nguyên nhân sâu chưa rõ | open |
| BUG-261013-web-auth-session-absolute-expiry | identity-access | Phiên web gateway hết hạn sau ~30 phút dù remember; refresh 503 rồi retry 401 | `expiresAt` lấy từ `refresh_expires_in` (hạn idle) không gia hạn; `ProviderUnavailable` xóa phiên | fixed |
| BUG-261012-web-auth-client-bean | identity-access | 25 context errors: `No qualifying bean of type WebAuthKeycloakClient` | Client draft thiếu component và constructor injection annotations | fixed |
| BUG-261012-web-auth-oauth-error-mapping | identity-access | OAuth error `unknown`; HTTP400 malformed/invalid_client bị coi là sai mật khẩu | Error DTO dùng primitive expires_in dù OAuth error không có trường này; fallback400 quá rộng | fixed |
| BUG-261012-web-auth-session-otp-boundaries | identity-access | Refresh lease/idle expiry vẫn complete; OTP resend reset attempts; token lỗi chưa logout | Thiếu fencing expiry, giữ attempt budget và cleanup toàn luồng; mock jwt bỏ qua CSRF | fixed |
| BUG-261012-keycloak-mock-expectation-order | identity-access | `Cannot add more expectations after actual requests are made` trong `MockRestServiceServer` test | Test khai báo expectation thứ hai sau khi request đầu tiên đã chạy | fixed |
| BUG-261012-auth-flow-topology-validation | identity-access | REQUIRED password/OTP under DISABLED parent passed bootstrap checks | Initial validator checked presence/order but not complete native flow ancestry | fixed |
| BUG-261012-auth-capability-concurrent-mutation | identity-access | New capability can issue while previous credential mutation is in progress | Separate token consumption did not serialize generations until transaction completion | fixed |
| BUG-261012-keycloak-email-immutable-attributes | identity-access | Extension OTP email returns 503, EmailException when using Map.of | Keycloak FreeMarker email provider enriches its input map, which must be mutable | fixed |
| ENV-261011-keycloak-extension-runtime-delegate | identity-access | Standalone extension test: Provider for jakarta.ws.rs.ext.RuntimeDelegate cannot be found | Keycloak supplies JAX-RS runtime in the server, not the standalone test JVM | fixed |
| BUG-261007-openapi-create-account-status | identity-access | OpenAPI POST /api/accounts ghi 200, controller trả 201; FE handoff | Springdoc mặc định 200 cho method trả `ResponseEntity` khi thiếu `@ResponseStatus` | fixed |
| BUG-261006-jwks-unavailable-returns-500 | identity-access | Keycloak tắt, API chưa cache JWKS ⇒ 500 body Boot `AuthenticationServiceException` `Couldn't retrieve remote JWK set` | Failure handler mặc định của `BearerTokenAuthenticationFilter` ném lại lỗi thay vì gọi entry point | fixed |
| ENV-261013-keycloak-dev-users-missing | identity-access | Login dev user (`teacher.a`…) báo "Email hoặc mật khẩu không đúng"; Keycloak event `LOGIN_ERROR` `error="user_not_found"` | Realm `carenest` tạo trước khi mount `carenest-users-0.json`; import `IGNORE_EXISTING` bỏ qua file user, DB app vẫn có seed | workaround |
| ENV-261004-keycloak-user-profile-account-not-fully-set-up | identity-access | Keycloak `invalid_grant` `Account is not fully set up`, user không có email/lastName | User profile mặc định Keycloak 26 bắt buộc email + lastName | fixed |
| [CASE-261027-year-plan-autosave-fencing](incidents/CASE-261027-year-plan-autosave-fencing.md) | education-plan | Tự lưu: hai tab, bản phục hồi offline, stale revision và late acquire | Cần fencing theo phiên/revision và bản phục hồi riêng mỗi slot; đã có gate BE/FE | fixed |
