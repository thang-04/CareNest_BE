---
title: Quên mật khẩu trên App bằng OTP SMS (phụ huynh, GV, bếp)
status: approved
owner: Giáp
jira: G94-87 / BE-FEAT-0   # mã công việc tạm, đổi khi có
branch: feature/G94-87-BE-FEAT-0-app-rules-sms-reset
modules: [identity-access]
rules: [AUTH-15, AUTH-17, AUTH-19, AUTH-21, AUTH-22, AUTH-27]
risk: high           # endpoint công khai, security config dùng chung, gửi SMS tốn tiền
created: 2026-11-14
updated: 2026-11-14
---
# Quên mật khẩu trên App bằng OTP SMS (phụ huynh, GV, bếp)

## Context
User chốt 2026-11-14 trong phiên APP: trên App, phụ huynh, GV và bếp tự đặt lại mật khẩu bằng OTP gửi SMS (AUTH-27). Quyết định này thay phần AUTH-21 chốt 2026-10-30 ("phụ huynh, bếp không tự khôi phục, HT/PHT cấp lại"), chỉ cho App. Web giữ nguyên: chỉ nhân sự Web, OTP email.

Hiện trạng `dev` (dfef4d08):
- Quên mật khẩu Web theo email: `AuthController.java:114` `POST /auth/password/forgot` → `AuthServiceImpl.java:267` (lọc `mayUseWeb` dòng 516, gửi OTP qua Keycloak dòng 486). Resend, verify, reset ở `AuthController.java:133,142,152`; trạng thái OTP và giới hạn lần trong `AuthStateStoreImpl`; hằng `Constants.java:13-15` (OTP 300 s, resend 60 s, 5 lần/30 phút).
- Gửi SMS: `integration/sms/SmsGateway.java` chỉ có `FakeSmsGateway`. Twilio thật nằm trên nhánh G94-298 (chưa merge).
- Mọi tài khoản đăng nhập bằng SĐT: `CreateAccountRequest.java:17` `phone` bắt buộc; phụ huynh theo AUTH-18. Vì vậy không cần thêm field SĐT tổ trưởng bếp: SĐT đăng nhập của tài khoản bếp chính là SĐT đó (AUTH-22, AUTH-27).
- Đường công khai khai báo ở `SecurityConfig.java:215-218` (vùng dùng chung `config/**`).

## Quyết định đã chốt
- Kênh: SMS tới SĐT đăng nhập; người dùng: tài khoản có role PARENT, TEACHER hoặc KITCHEN_STAFF (user 2026-11-14).
- Tài khoản bếp: SĐT đăng nhập = SĐT tổ trưởng bếp, HT/PHT nhập khi tạo (user 2026-11-14).
- Phản hồi không lộ tài khoản có tồn tại (giữ nguyên nguyên tắc của luồng email).

## Làm rõ nghiệp vụ
```text
Actor: phụ huynh, GV, bếp trên App · Outcome: tự đặt mật khẩu mới, không cần HT/PHT
Rule: AUTH-27 (CONFIRMED; P-41 đóng 14/11) · AUTH-21 (CONFIRMED, phần App đổi 14/11)
Flow: [App] nhập SĐT → [S] gửi OTP SMS → [App] nhập OTP → [S] trả reset token → [App] mật khẩu mới → [S] đặt qua Keycloak, hủy phiên
Ảnh hưởng: identity-access · endpoint công khai mới · APP màn Quên mật khẩu · Web không đổi
Tài liệu vs yêu cầu: lệch AUTH-21 (2026-10-30) — user chọn yêu cầu mới; rule đã cập nhật 14/11
```

### Câu hỏi mở
- [x] P-41: màn nằm trong App, gọi API BE (user 2026-11-14, theo đề xuất)
- [x] Mã Jira: G94-87 (user 2026-11-14); mã công việc tạm BE-FEAT-0

## Acceptance criteria
- **AC-1** (AUTH-27): Given SĐT của tài khoản PARENT/TEACHER/KITCHEN_STAFF đang hoạt động, When gọi gửi OTP SMS, Then SMS chứa mã gửi tới SĐT đó; response chỉ có `requestId`, thời hạn.
- **AC-2** (AUTH-27): Given SĐT không có tài khoản, tài khoản khóa hoặc chỉ có role BGH/ADMIN, When gọi gửi OTP, Then response giống AC-1, không gửi SMS.
- **AC-3** (AUTH-27): Given `requestId` từ SMS, When resend, Then gửi lại qua SMS (không qua email); verify và reset dùng chung luồng hiện có.
- **AC-4** (AUTH-27): Given vượt 5 lần/30 phút theo SĐT, When gọi gửi, Then không gửi SMS, response vẫn như AC-1.
- **AC-5** (AUTH-21): Luồng email Web không đổi: bếp/phụ huynh vẫn được xử lý như email không tồn tại.
- **AC-6**: Log, test, response không chứa mã OTP, nội dung SMS hay SĐT đầy đủ.

## Key decisions
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| **A (đề xuất).** Endpoint mới `POST /auth/password/forgot/sms` `{phone}`; resend/verify/reset dùng lại, phân biệt kênh qua `lookup` của token | Ít code, tái dùng giới hạn lần và token; App có UI sẵn | Thêm đường công khai (sửa `SecurityConfig`) |
| B. Mở rộng `POST /auth/password/forgot` nhận `email` hoặc `phone` | Không thêm path | Đổi DTO Web đang dùng (FE `src/services/account/api/accountApi.js`), lẫn hai kênh trong một request |
| C. Trang Keycloak + SPI gửi SMS | App chỉ mở trình duyệt | Phải viết/deploy SPI Keycloak, khó test; chỉ hợp lý nếu P-41 chọn Keycloak |

## Phases
### Phase 1 — API gửi OTP SMS (sau khi duyệt plan, có mã Jira)
- Files (dự kiến, ~9): M `controller/account/AuthController.java` · M `dto/account/AuthRequests.java` · M `service/account/AuthService.java` · M `service/account/impl/AuthServiceImpl.java` · M `config/SecurityConfig.java` (vùng dùng chung — in bản tác động trước khi sửa) · M `docs/api/openapi.yaml` · M `docs/contracts/AUTH_CONTRACT.md` · M `docs/api/API_INDEX.md` · M `src/test/.../AuthServiceImplTest.java`
- Steps: thêm `ForgotSms(phone)` chuẩn hóa bằng `utils/PhoneNumbers` → tìm tài khoản theo SĐT, lọc role App (AUTH-27) → token `OTP` với `lookup = keyedHash("phone:"+phone)` → `SmsGateway.send` (bắt `SmsDeliveryException`, không lộ ra response) → resend chọn kênh theo `lookup`.
- Exit: `./mvnw test -Dtest=AuthServiceImplTest` pass; test có `@DisplayName("AUTH-27: …")` cho AC-1..AC-6.
- **Dừng chờ user review.**

### Phase 2 — Tích hợp SMS thật
- Khi G94-298 vào `dev`: chạy lại test với `TwilioSmsGateway`; ghi giới hạn bản trial (chỉ gửi tới số đã xác minh) vào `docs/system/DEPLOYMENT.md`.

## Test matrix
| AC | Test |
| --- | --- |
| AC-1..AC-4, AC-6 | `AuthServiceImplTest` (unit, `FakeSmsGateway` hoặc mock) |
| AC-5 | test luồng email hiện có không đổi |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Lạm dụng gửi SMS (tốn phí, spam) | Chi phí, phiền người dùng | Giới hạn theo SĐT như luồng email; xem xét thêm giới hạn theo IP | Gỡ path khỏi `SecurityConfig` |
| Conflict với G94-298, TBD-1 (cùng sửa `AUTH_CONTRACT.md`, `identity-access.md`, dòng AUTH-21) | Merge lâu | Làm sau khi hai nhánh vào `dev`, hoặc rebase trước PR | Revert commit |
| Dò SĐT có tài khoản qua thời gian phản hồi | Lộ thông tin | Gửi SMS bất đồng bộ hoặc giữ thời gian như nhau | — |

## Validation log
- [x] Khẳng định về code có `file:line` (mục Context)
- [x] Không dùng rule PENDING như đã chốt: P-41 đóng 14/11 trước khi approve
- [x] Đổi contract ⇒ consumer: APP màn Quên mật khẩu (chưa gọi API thật); Web không gọi path mới

## Progress log
### 2026-11-14 — draft
- Rule đã ghi: AUTH-27 mới, AUTH-21 thêm dẫn chiếu, P-41 mở (`docs/business/BUSINESS_RULES.md`).
- Tiếp: user trả lời P-41, tạo mã Jira, duyệt plan ⇒ Phase 1.

### 2026-11-14 — approved
- User duyệt plan, chọn phương án A, P-41 = màn trong App, Jira G94-87. Bắt đầu Phase 1.

### 2026-11-14 — Phase 1 (chờ review)
- Làm: `POST /auth/password/forgot/sms` (`AuthController#forgotBySms`), `AuthRequests.ForgotSms`, `AuthServiceImpl.forgotBySms` + `resendCode` (kênh suy từ `lookup_hash` = HMAC "phone:"+SĐT, không migration), tách helper `issueOtp`/`takeRecoveryRate` dùng chung với luồng email. Snapshot `openapi.yaml`, `API_INDEX.md`, `AUTH_CONTRACT.md`.
- Khác plan: không sửa `utils/` (vùng dùng chung) — dùng hằng `Auth.*` có sẵn; tập role khôi phục SMS khai báo riêng trong `AuthServiceImpl` (không dùng `ClientPlatform.MOBILE` vì nhánh TBD-1 thêm BGH vào App).
- `SecurityConfig.authPostMatcher` thêm `/auth/password/forgot/sms` (user duyệt bản tác động 14/11, phương án A): công khai khi gateway bật, kiểm CSRF. Test đặt ở `AuthControllerTest` (nơi đã test các path khôi phục với CSRF) thay `SecurityConfigTest`: thêm `smsRecoveryIsPublicWithCsrf`, thêm path vào `everyPublicMutationRequiresCsrfEvenWithBearer`; hai test fail (401) trước khi sửa config, pass sau.
- Verify: `./mvnw -o test -Dtest='*Auth*Test,*Security*Test,ResponseContractTest,OpenApiSnapshotTest,ArchitectureRulesTest'` pass, 0 fail, 0 skip (AuthServiceImplTest 32/32 có 6 test AUTH-27, AuthControllerTest 16/16, SecurityConfigTest 15/15, OpenApiSnapshotTest 4/4 sau `-Dopenapi.snapshot.update=true`, ArchitectureRulesTest 14/14). Chưa chạy toàn bộ suite.
