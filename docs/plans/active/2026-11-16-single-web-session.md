---
title: Một phiên Web mỗi tài khoản (AUTH-28)
status: in-progress   # chế độ tự chạy (CLAUDE.md 2026-10-23); user soát quyết định D1–D5
owner: thangnd
jira: G94-60 / BE-FEAT-01   # theo nhánh hiện tại; xác nhận task Jira khi commit
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access]
rules: [AUTH-28]
risk: high
created: 2026-11-16
updated: 2026-11-16
---
# Một phiên Web mỗi tài khoản (AUTH-28)

- Làn L (auth)
- Nguồn: user 2026-11-16 "Muốn chỉ 1 phiên" (sau khi xác nhận hiện tại 2 trình duyệt cùng đăng nhập được).

## Mục tiêu / AC

- AC1: Đăng nhập Web ở trình duyệt B khi A đang đăng nhập ⇒ phiên Keycloak của A bị hủy; A nhận 401 ở `/auth/refresh` kế tiếp và về màn đăng nhập.
- AC2: Phiên vừa cấp cho B không bị hủy (giữ theo claim `sid`).
- AC3: Phiên Mobile cùng tài khoản không bị hủy.
- AC4: Keycloak admin lỗi ⇒ B vẫn đăng nhập được, chỉ log cảnh báo.

## Quyết định (agent tự chọn theo chế độ tự chạy — user soát)

| # | Quyết định | Lý do | Phương án bỏ |
| --- | --- | --- | --- |
| D1 | Đăng nhập mới đá phiên cũ | Chặn đăng nhập mới làm khóa người dùng khi đóng trình duyệt không đăng xuất | Chặn login mới |
| D2 | Chỉ phiên Web gateway | GV dùng cả Web và Mobile (`ClientPlatform`); user hỏi về 2 trình duyệt | Mọi phiên Keycloak |
| D3 | Làm ở BE sau login (Keycloak admin API), không đổi realm/flow | Không cần re-import realm đang chạy; test unit được; không đụng bootstrap flow | Authenticator `user-session-limits` của Keycloak |
| D4 | Best-effort | Lỗi admin không nên làm hỏng đăng nhập; phiên cũ tự hết idle 30 phút | Fail-closed 503 |
| D5 | ~~Không thêm mã lỗi~~ → thêm 1016 `WEB_SESSION_REPLACED` (16/11, sau brainstorm case form đang sửa) | Người ở trình duyệt cũ cần biết vì sao bị out; mã cũ 1003 vẫn dùng cho hết hạn thường nên FE cũ không vỡ (vẫn là 401) | Giữ 1003 cho mọi trường hợp |
| D6 | Chặn ngay (user yêu cầu 16/11 "chặn ngay, A hiện cảnh báo ngay"): lưu `sid` phiên bị hủy vào `revoked_web_sessions` (V143) + bộ nhớ, validator JWT từ chối ⇒ 401 1016 mọi request; giữ 10 phút rồi dọn | Không gọi Keycloak mỗi request (đúng `security.md`); chịu được restart (nạp lại từ DB) | Gọi Keycloak introspect mỗi request; rút access token xuống 1 phút |
| D7 | Web hỏi `GET /me/session` mỗi 15 giây khi tab hiển thị + khi focus/quay lại tab | Repo chưa có SSE/WebSocket, không thêm hạ tầng; 15 giây đủ "ngay" với người dùng, tải nhẹ | SSE/WebSocket đẩy sự kiện; poll 5 giây |
| D8 | (user yêu cầu 16/11: "modal hỏi ở cả 2, confirm ở phiên nào rồi mới xóa ở session cũ, nhiều case mở nhầm") Login khi còn phiên Web khác ⇒ 409 1017, chưa cấp phiên; mới "Dùng ở đây"/"Hủy", cũ "Tiếp tục dùng ở đây"/"Đăng xuất ở đây"; **ai chọn trước thắng**; chờ 2 phút | Mở nhầm chỉ cần "Hủy"; người đang dùng thật được giữ phiên; người ở máy cũ đã tắt vẫn đăng nhập được (cũ không ai trả lời, mới tự "Dùng ở đây") | Mới luôn phải chờ cũ đồng ý (khóa người dùng khi quên tắt máy cũ); chỉ hỏi ở trình duyệt mới |
| D9 | Refresh token lượt chờ nằm trong cookie HttpOnly niêm phong `CARENEST_TAKEOVER`; server chỉ giữ id/user/`sid`/trạng thái trong bộ nhớ; từ chối/hết hạn ⇒ admin `DELETE /sessions/{sid}` | `security.md` cấm lưu refresh token phía server; restart API chỉ làm mất lượt chờ (người dùng đăng nhập lại) | Bảng DB lượt chờ chứa token |

## Case trình duyệt cũ đang thao tác (brainstorm 16/11)

| Case ở trình duyệt A khi B đăng nhập | Hành vi |
| --- | --- |
| Đang gõ form thường, chưa lưu | Sau D6: request kế tiếp (hoặc lần hỏi phiên ≤ 15s) 401 1016 ⇒ màn có `useLeaveGuard` giữ nguyên + `ReauthModal`; màn không guard về đăng nhập |
| Đang sửa kế hoạch năm (khóa sửa + tự lưu) | B mở cùng kế hoạch ⇒ B lấy quyền ngay (cùng tài khoản), A nhận 409 `EDIT_SESSION_LOST`; tự lưu ≤ 15s trước và bản phục hồi cục bộ còn |
| Bấm Lưu đúng lúc token hết | 401 → refresh 401 ⇒ không ghi gì, không ghi trùng |
| Nhiều tab cùng trình duyệt A | Chung cookie ⇒ cùng bị out; đăng nhập lại ở một tab của A không đá tab khác của A |
| A đăng nhập lại | Đá B (ping-pong) — đúng rule |
| Upload/request đang chạy | Token kiểm lúc nhận request ⇒ chạy xong |

Phương án cho trải nghiệm ở A: (1) chỉ đổi câu FE chung chung — không phân biệt lý do; (2) **chọn**: BE trả 401 mã 1016 `WEB_SESSION_REPLACED` khi refresh bị từ chối và Keycloak có phiên Web mới hơn `loginAt`; FE báo đúng lý do + giữ nháp; (3) hỏi trước ở B "đang mở nơi khác, vẫn đăng nhập?" — thêm bước login, không cứu dữ liệu A, để sau nếu cần. Chặn A ngay bằng kiểm Keycloak mỗi request bị loại (trái `security.md`); thay bằng danh sách thu hồi cục bộ (D6).

## Thay đổi

- `AuthGatewayClient.revokeOtherWebSessions` + impl Keycloak (`GET /admin/realms/{realm}/users/{id}/sessions`, `DELETE /admin/realms/{realm}/sessions/{id}`), quyền service account `carenest-backend` sẵn có (`manage-users`).
- `AuthServiceImpl.login` gọi sau khi kiểm role/profile, trước khi phát cookie.
- `ApiCode.WEB_SESSION_REPLACED` (1016, 401); `AuthGatewayClient.replacedByNewerWebSession` đọc `sub`/`sid` từ refresh token trong cookie (không xác thực lại chữ ký — cookie đã niêm phong), tra phiên Web có `start ≥ loginAt`; lỗi ⇒ false (1003).
- FE (user cho phép 16/11): `authTransport.lastRefreshErrorCode()`; `AuthContext` báo đúng lý do 1016 và trạng thái `reauth` khi `useLeaveGuard` báo có thay đổi chưa lưu; `ProtectedRoute` giữ màn + `src/components/auth/ReauthModal.jsx` đăng nhập lại tại chỗ (khác tài khoản ⇒ tải lại `/`). Giữ nháp dựa vào `useLeaveGuard` (~30 màn) nên màn chưa dùng guard vẫn mất dữ liệu chưa lưu.
- Chặn ngay: `V143__create_revoked_web_sessions.sql`, entity `RevokedWebSession`, `RevokedWebSessionRepository`, `security/WebSessionRevocations` (validator JWT, nạp lại khi khởi động, dọn trong `AuthStateStoreImpl.cleanup`), `SecurityConfig` ghép validator, `RestAuthenticationEntryPoint` trả 1016, `AuthStateStore.revokeWebSessions`, `controller/account/SessionController` (`GET /me/session`); OpenAPI snapshot cập nhật.
- FE: `authService.checkWebSession()` + `AuthContext` hỏi mỗi 15s/focus (chỉ gateway mode); `docs/integration/be-api-routes.json` thêm đúng `GET /me/session` (không `--sync` vì BE nhánh này lệch route nhánh khác).
- Docs: `BUSINESS_RULES.md` AUTH-28, `DATA_DICTIONARY.md`, `AUTH_CONTRACT.md`, `docs/modules/identity-access.md`.

## Giới hạn đã biết

- Cảnh báo ở tab cũ trễ ≤ ~15 giây (chu kỳ hỏi); tab ẩn không hỏi, hỏi ngay khi quay lại.
- Nhiều instance API: mỗi instance chỉ nạp DB lúc khởi động ⇒ instance khác chưa thấy thu hồi mới cho tới khi refresh (≤ 5 phút). Hiện deploy 1 instance.
- Phiên cũ chỉ thấy hỏi ở lần hỏi phiên kế tiếp (≤ 15 giây); trình duyệt mới chọn nhanh hơn thì phiên cũ không kịp thấy hỏi.
- Kẻ có mật khẩu ở trình duyệt mới bị phiên thật từ chối thì vẫn thử lại được nhiều lần; khuyến cáo trong modal: đổi mật khẩu.
- Phiên Web cũ đã đóng trình duyệt (chưa hết idle 30 phút) vẫn tính là "đang dùng" ⇒ đăng nhập mới vẫn bị hỏi, chọn "Dùng ở đây".
- Lượt chờ trong bộ nhớ: nhiều instance API thì lượt chờ không thấy chéo instance.
- Token thiếu `sid` ⇒ bỏ qua giới hạn, log `sessionLimitSkipped`.
- Chưa kiểm với Keycloak thật (chỉ unit test với MockRestServiceServer).

## Progress log

- 2026-11-16 — Code + unit test + docs. `mvnw test -Dtest=AuthServiceImplTest,KeycloakAuthGatewayClientTest` 41/41 pass; `ArchitectureRulesTest` 14/14 pass; `check-ai-layer` 0 lỗi. Chưa chạy full suite, chưa kiểm tay với Keycloak local (2 trình duyệt).
- 2026-11-16 — Brainstorm case form đang sửa ⇒ thêm 1016 `WEB_SESSION_REPLACED`. Lỗi gặp: record `UserSession` field `long start` làm Jackson 3 lỗi khi JSON thiếu `start` ⇒ đổi `Long`. Test: AuthServiceImplTest 30, KeycloakAuthGatewayClientTest 16, AuthControllerTest 15, ArchitectureRulesTest 14, OpenApiSnapshotTest 4 — 0 fail. FE chưa đọc 1016 (CROSS_REPO_MAP). Chưa xác minh refresh token Keycloak 26 thật có `sub`/`sid` (thiếu ⇒ trả 1003).
- 2026-11-16 — FE: `npm run test:auth` 100/100 (2 test mới), `npm run lint` pass, `npm run build` pass. Lint chặn `components/account` import `utils/auth` ⇒ chuyển modal sang `components/auth`. Chưa thử trên trình duyệt với BE + Keycloak thật.
- 2026-11-16 — Chặn ngay + cảnh báo ngay (D6, D7). BE: `mvnw test -Dtest=AuthServiceImplTest,KeycloakAuthGatewayClientTest,WebSessionRevocationsTest,RestAuthenticationEntryPointTest,AuthControllerTest,ArchitectureRulesTest,ResponseContractTest` exit 0; OpenAPI snapshot regenerate có chủ đích (`-Dopenapi.snapshot.update=true`) thêm `/api/me/session`. FE: `npm run test:auth` 100/100, eslint + prettier file sửa pass, `npm run build` pass, `check:api` 0 lỗi. `AuthSchemaIntegrationTest` (Postgres Testcontainers) exit 0, Flyway lên migration bảng phiên bị hủy. Chưa thử 2 trình duyệt với Keycloak thật.
- 2026-11-16 — Dựng stack test riêng `-p carenest-auth27` (API 8090, Keycloak 8280, PG 5462, FE 5183) vì port mặc định do stack `fix-g94-305` dùng. Lỗi gặp: login 503 1013, Keycloak `client_disabled` — realm import luôn tắt gateway ⇒ chạy `bootstrap.mjs --apply --enable` (ghi `TROUBLESHOOTING.md`). User thử: phiên cũ bị đá ngay nhưng yêu cầu đổi sang xác nhận ở cả 2 (D8, D9).
- 2026-11-16 — Xác nhận dùng ở đâu (D8, D9). BE: `WebSessionTakeoverStore`, `AuthGatewayClient.hasOtherWebSessions/revokeSession`, `AuthService.confirmTakeover/cancelTakeover/sessionStatus/rejectTakeover/expireTakeovers`, endpoint `POST /auth/login/takeover/{confirm,cancel}`, `POST /me/session/takeovers/{id}/reject`, `GET /me/session` trả `pendingTakeover`, ApiCode 1017–1019, cleanup 60s. `mvnw test -Dtest=AuthServiceImplTest,WebSessionTakeoverStoreTest,AuthCleanupConfigTest,AuthControllerTest,KeycloakAuthGatewayClientTest,ArchitectureRulesTest,ResponseContractTest,RestAuthenticationEntryPointTest,WebSessionRevocationsTest` exit 0; OpenAPI snapshot regenerate có chủ đích. FE: `TakeoverConfirmModal`, `SessionTakeoverModal`, `authService.confirmTakeover/cancelTakeover/keepSessionHere`, `AuthContext.pendingTakeover`; `npm run lint` (eslint src) pass, prettier pass, `test:auth` 100/100, build pass, `check:api` 0 lỗi. Chưa có test FE riêng cho modal; chưa thử tay 2 trình duyệt.
- 2026-11-16 — User yêu cầu modal dễ hiểu hơn: 2 modal theo mẫu hộp xác nhận chuẩn (`modal--confirm`: icon, tiêu đề, 1 câu) + ô "chọn nút nào thì điều gì xảy ra" (`SessionChoices`, CSS chung `.confirm__choices`/`.confirm__hint` trong `components.css`); trạng thái bị từ chối/hết hạn là hộp riêng một nút "Đăng nhập lại". `ReauthModal` đổi `<input>` sang `TextField`. FE: eslint src pass, `check-design` ok, build pass, `test:auth` 100/100. Chưa chụp màn/so trên trình duyệt.
- 2026-11-16 — Giữ dữ liệu đang sửa (user đồng ý fix 1+2). `SessionTakeoverModal`: 'Đăng xuất ở đây' khi `hasUnsavedChanges()` ⇒ hộp hỏi 'Ở lại để lưu' (= giữ phiên ở đây) / 'Vẫn đăng xuất'. `useLeaveGuard.js` thêm `useUnsavedMark` (modal chỉ đánh dấu chưa lưu, không đặt mục lịch sử phụ — tránh 2 guard lệch sentinel). Gắn `useLeaveGuard` cho MenuForm, WeeklyMenuForm, DishForm, FoodForm, AllergyMenuForm, AiMenuSuggestion, Profile, ChangePassword (canSave=false), AssessmentDayUnlock, AttendanceDayUnlock (canSave=false); `useUnsavedMark` cho SupplierEditModal, FoodEditModal, ResourcePickerModal (tệp đang chờ đặt tên), ResourceLinkForm, ResourceTagsModal. `useFoodForm` trả `dirty`, `submit` trả boolean. FE: eslint src pass, prettier pass, check:design ok, test:auth 100/100, build pass. Giới hạn: trang guard `when: dirty && !busy` ⇒ phiên bị đá đúng lúc đang lưu thì vẫn về trang đăng nhập (giống các trang có sẵn). Chưa thử tay trên trình duyệt.
- 2026-11-16 — Đưa nhánh lên ngang `dev` trước PR: `dev` đã dùng AUTH-27 cho ảnh đại diện ⇒ quy tắc một phiên Web đổi thành **AUTH-28** (BE + FE); migration `V124` (sau đó `V139`) trùng số trên `dev` ⇒ đổi thành `V143__create_revoked_web_sessions.sql`. Phần SMS/mã kích hoạt cũ trong cây làm việc (đã có trên `dev` qua G94-298) bỏ, giữ bản của `dev`.
