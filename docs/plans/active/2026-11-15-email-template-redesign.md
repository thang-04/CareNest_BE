---
title: Thiết kế lại mail Keycloak (thiết lập tài khoản, OTP)
status: in-progress
owner: Thắng
jira: chưa gán (hỏi user trước khi commit)
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [account]
rules: []
risk: medium
created: 2026-11-15
updated: 2026-11-15
---
# Thiết kế lại mail Keycloak (thiết lập tài khoản, OTP)

Liên repo: phần FE chỉ thêm 1 ảnh tĩnh (Phase 1).

## Context
- Hệ thống chỉ có 2 mail, đều do Keycloak gửi qua extension `carenest-auth`:
  - `sendSetupEmail` ở `docker/keycloak/extensions/carenest-auth/src/main/java/com/carenest/keycloak/AuthOperations.java:79`, template `carenest-setup.ftl`.
  - `sendOtp` ở `AuthOperations.java:70`, template `carenest-recovery-code.ftl`.
  - Gửi qua `sendEmail` (`AuthOperations.java:109`), dùng `EmailTemplateProvider`.
- Template hiện là 3 thẻ `<p>`, không logo, không bố cục.
- Tiêu đề mất dấu ("CareNest - Thiet lap tai khoan"): realm chưa bật đa ngôn ngữ nên Keycloak dùng `email/messages/messages_en.properties`, file này viết không dấu.
- Mail thiết lập tài khoản không ghi tên đăng nhập (SĐT), nên nhân sự không biết đăng nhập bằng gì.
- `EmailGateway` của BE chưa có nơi gọi; ngoài phạm vi plan.

## Quyết định đã chốt
1. Mail thiết lập tài khoản ghi họ tên, tên đăng nhập (SĐT), tên trường, vai trò — Thắng, 2026-11-15.
2. Footer tối giản: "© CareNest · Email tự động, vui lòng không trả lời" — Thắng, 2026-11-15.
3. Bố cục B cho cả 2 mail: header trắng, logo nhỏ + chữ "CareNest" bên trái, viền xanh brand `#4fb0f5` phía trên, thẻ nội dung trắng trên nền `#eef4f9`, chữ đậm `#0e3f63` — Thắng, 2026-11-15 (mockup duyệt qua visual companion).
4. Mail OTP: lời chào theo họ tên, mã to dạng đều nét, hiệu lực 5 phút, cảnh báo không chia sẻ mã kể cả với người tự xưng nhân viên nhà trường, câu trấn an khi không yêu cầu — Thắng, 2026-11-15.
5. Logo lấy từ web FE: `<CARENEST_AUTH_WEB_ORIGIN>/email/logo-mark.png` — Thắng, 2026-11-15.
6. Tên trường lấy từ env `CARENEST_SCHOOL_NAME` của container Keycloak, mặc định "Trường Mầm Non Thượng Hồng"; vai trò suy từ realm role trong extension. Không đổi API BE ↔ Keycloak, không sửa code BE — Thắng, 2026-11-15.
7. SMS ngoài phạm vi.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** actor = nhân sự được nhà trường tạo tài khoản; người dùng (nhân sự, phụ huynh) quên mật khẩu. Outcome: mail rõ ràng, có nhận diện thương hiệu, có đủ thông tin để đăng nhập lần đầu. Không đổi luồng (vẫn: mở link → `/forgot-password` → OTP → đặt mật khẩu).
- **Đối chiếu thiết kế ban đầu:** chỉ đổi nội dung/giao diện; luồng AUTH giữ nguyên.
- **Ảnh hưởng:** chỉ extension Keycloak + compose; FE thêm 1 ảnh tĩnh `public/email/logo-mark.png`; APP không ảnh hưởng. Dữ liệu cũ không đổi.

### Câu hỏi mở
Không còn.

## Acceptance criteria
- **AC-1:** Given nhân sự có `firstName`="Nguyễn Thị Lan", username "0369666456", role `TEACHER`; When BE gọi `setup/email`; Then mail HTML có tiêu đề "CareNest – Thiết lập tài khoản", chứa "Nguyễn Thị Lan", "0369666456", "Giáo viên", tên trường, nút "Thiết lập mật khẩu" trỏ `<origin>/forgot-password`, logo `<origin>/email/logo-mark.png`, footer tối giản.
- **AC-2:** Given user có role `TEACHER` và `PARENT`; Then `roleLabel` = "Giáo viên". Given user không có role nhân sự; Then câu giới thiệu không ghi vai trò.
- **AC-3:** Given `CARENEST_SCHOOL_NAME` không đặt; Then tên trường = "Trường Mầm Non Thượng Hồng".
- **AC-4:** Given `firstName` rỗng; Then lời chào là "Xin chào,". Given `firstName` chứa `<script>`; Then HTML ra đã escape.
- **AC-5:** Mail OTP có tiêu đề "CareNest – Mã xác thực đặt lại mật khẩu", lời chào theo họ tên, mã 6 số trong ô nổi bật, "5 phút", cảnh báo không chia sẻ mã, câu trấn an.
- **AC-6:** Cả 2 mail có bản text thuần tiếng Việt có dấu, cùng nội dung; tiêu đề có dấu dù user không có locale `vi`.
- **AC-7:** HTML dùng `<table>` + CSS inline, không flex/grid/webfont/ảnh base64/SVG.
- **AC-8:** Hành vi cũ giữ nguyên: origin không hợp lệ vẫn `ServiceUnavailableException`; mã OTP không đúng 6 số vẫn `BadRequestException`; user không có email vẫn lỗi.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nguồn logo | Ảnh tĩnh trên web FE | Keycloak `/resources` (login theme); đính kèm CID | Gmail chặn base64/SVG; `/resources` có mã phiên bản trong path, Caddy chặn một phần; Keycloak không hỗ trợ đính kèm ảnh |
| Tiêu đề có dấu | Viết tiếng Việt có dấu vào cả `messages_en` và `messages_vi` | Bật internationalization realm, `defaultLocale=vi` | Sản phẩm chỉ tiếng Việt; không phải đổi cấu hình realm trên VPS |
| Tên trường | Env `CARENEST_SCHOOL_NAME` | BE truyền qua API `setup/email` | Không đổi contract BE ↔ Keycloak; 1 trường (nguyên tắc 3) |
| Vai trò | Extension suy từ realm role | BE truyền | Như trên |
| Khung dùng chung | Macro `carenest-layout.ftl` | Lặp HTML trong 2 template | Tránh lặp, đổi khung 1 chỗ |

## Phases
### Phase 1 — Logo trên web FE (repo `CareNest_FE`)
- Files: C `CareNest_FE/public/email/logo-mark.png` (chép từ `src/assets/brand/logo-mark.png`)
- Exit: `npm run build` → `dist/email/logo-mark.png` tồn tại.

### Phase 2 — Extension Keycloak
- Files:
  - C `docker/keycloak/extensions/carenest-auth/src/main/resources/theme/carenest-auth/email/html/carenest-layout.ftl`
  - M `.../email/html/carenest-setup.ftl`, `.../email/html/carenest-recovery-code.ftl`
  - M `.../email/text/carenest-setup.ftl`, `.../email/text/carenest-recovery-code.ftl`
  - M `.../email/messages/messages_en.properties`, `messages_vi.properties`
  - M `docker/keycloak/extensions/carenest-auth/src/main/java/com/carenest/keycloak/AuthOperations.java`
  - M `docker/keycloak/extensions/carenest-auth/src/test/java/com/carenest/keycloak/AuthOperationsTest.java`
  - M `compose.yaml`, `compose.dev.yaml`, `compose.vps.yaml` (thêm `CARENEST_SCHOOL_NAME` cho keycloak)
- Steps:
  1. `AuthOperations`: hàm dựng attributes chung (`fullName`, `username`, `schoolName`, `logoUrl`); `sendSetupEmail` thêm `roleLabel`; `sendOtp` nhận thêm origin để dựng `logoUrl` (`CareNestAuthResource` truyền `CARENEST_AUTH_WEB_ORIGIN` như `setup/email`).
  2. Map role → nhãn theo thứ tự ưu tiên: `PRINCIPAL` Hiệu trưởng, `VICE_PRINCIPAL` Phó hiệu trưởng, `TEACHER` Giáo viên, `KITCHEN_STAFF` Nhân viên bếp, `ADMIN` Quản trị hệ thống; bỏ `PARENT`.
  3. Viết macro layout (table, CSS inline) + 2 template HTML theo mockup B; bản text có dấu.
  4. Sửa 2 file messages: `carenestSetupSubject=CareNest – Thiết lập tài khoản`, `carenestRecoverySubject=CareNest – Mã xác thực đặt lại mật khẩu`.
  5. Thêm env vào 3 compose.
- Tests: AC-1..4, AC-8 → `AuthOperationsTest`; AC-4 (escape), AC-5..7 → test render FreeMarker template.
- Exit: `mvn -q test` trong `docker/keycloak/extensions/carenest-auth` → pass, 0 skip.

### Phase 3 — Kiểm tra thủ công local
- `docker compose up` (build lại Keycloak), tạo tài khoản nhân sự có email, xem 2 mail trong Mailpit (tab HTML + Text); gửi thử 1 mail ra Gmail thật.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | — | `AuthOperationsTest#setupEmailCarriesAccountDetailsForTheTemplate` | 2 | pass |
| AC-2 | — | `AuthOperationsTest#setupEmailPrefersStaffRole…`, `#setupEmailOmitsRole…` | 2 | pass |
| AC-3 | — | `AuthOperationsTest#setupEmailPrefersStaffRoleOverParentAndFallsBackToDefaultSchool` | 2 | pass |
| AC-4 | — | `EmailTemplateRenderTest#setupHtmlEscapesNameAndHandlesMissingOptionalFields` | 2 | pass |
| AC-5 | — | `EmailTemplateRenderTest#recoveryHtmlShowsCodeAndValidity` | 2 | pass |
| AC-6 | — | `EmailTemplateRenderTest#textVersions…`, `#subjectsHaveDiacritics…` | 2 | pass |
| AC-7 | — | `EmailTemplateRenderTest#assertEmailSafeHtml` (2 test HTML) | 2 | pass |
| AC-8 | — | `AuthOperationsTest` (cũ) + `#setupEmailRejectsUnsafeWebOrigin` | 2 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| FE chưa deploy ảnh | Mail thiếu logo | Deploy FE trước; alt text + chữ "CareNest" vẫn hiện | Không cần |
| Template FreeMarker lỗi | Gửi mail thất bại (BE vẫn tạo tài khoản, `setupEmailSent=false`) | Test render trong Phase 2 | Deploy lại image Keycloak cũ |
| Hiển thị khác nhau giữa trình đọc mail | Giao diện lệch | Table + CSS inline; thử Gmail web/điện thoại | Như trên |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ không đổi contract BE ↔ Keycloak (`CareNestAuthResource` chỉ đổi nội bộ)
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] 11 file trong 2 repo, 3 phase ngắn; không tách thêm (cùng 1 thay đổi giao diện)
- [x] Không dùng rule PENDING/OPEN
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan

## Progress log
### 2026-11-15 — Thiết kế (done)
- Mockup duyệt: bố cục B (setup + OTP). User duyệt plan.

### 2026-11-15 — Phase 1–2 (chờ review)
- Commit: chưa commit (2 repo).
- Verify:
  - `mvn test` (extension) → `Tests run: 48, Failures: 0, Errors: 0, Skipped: 0` · `BUILD SUCCESS`.
  - FE `npm run build` → `✓ built`, `dist/email/logo-mark.png` có.
  - Render thật 2 template HTML qua FreeMarker, xem trên trình duyệt: đúng bố cục B, có dấu.
- Khác plan:
  - Thêm `org.freemarker:freemarker:2.3.34` scope `test` vào `pom.xml` extension (render template trong test; runtime Keycloak đã có FreeMarker).
  - Test render tách file riêng `EmailTemplateRenderTest.java`.
  - `sendOtp` nhận thêm `webOrigin`; origin lỗi ⇒ vẫn gửi OTP, chỉ bỏ logo (không chặn đường khôi phục).
  - Logo `alt=""` vì chữ "CareNest" đứng cạnh (tránh đọc trùng).
  - Bỏ câu cũ "Nếu không có email khôi phục, dùng mật khẩu tạm…" (người nhận đã có email); thêm link dự phòng khi nút không bấm được.
- Tiếp: Phase 3.

### 2026-11-15 — Phase 3 (done, trừ Gmail thật)
- Môi trường tạm: image build từ `docker/keycloak/auth/Dockerfile` (jar mới) + Mailpit trên network riêng; realm `carenest` tạo bằng kcadm (`emailTheme=carenest-auth`, client `carenest-backend` service account `manage-users`, client `carenest-auth-gateway`, user role `TEACHER`+`PARENT`). Đã xóa container/network/image sau khi thử.
- Gọi thật `POST /realms/carenest/carenest-auth/setup/email` và `/recovery/email` bằng token service account → `200 {"success":true}` cả 2.
- Mailpit nhận 2 mail, tiêu đề có dấu: `CareNest – Thiết lập tài khoản`, `CareNest – Mã xác thực đặt lại mật khẩu`.
- Bản text: đủ họ tên, tên trường mặc định, "Giáo viên" (TEACHER thắng PARENT), tên đăng nhập, link `/forgot-password`, mã + 5 phút, footer.
- Bản HTML xem trong Mailpit: đúng bố cục B; logo tải được từ `<origin>/email/logo-mark.png` (256×256).
- Chưa làm: gửi 1 mail ra Gmail thật — để thử trên dev sau khi deploy (dev đã dùng SMTP Gmail).
- Tiếp: commit 2 repo (cần mã Jira); deploy FE trước BE.
