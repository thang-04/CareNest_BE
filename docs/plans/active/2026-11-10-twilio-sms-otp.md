---
title: Nối nhà cung cấp SMS Twilio (bản dùng thử) và mã kích hoạt tài khoản phụ huynh qua SMS
status: in-progress   # user duyệt 2026-11-11 ("làm phase 1 đi")
owner: thangnd
jira: G94-60 / BE-FEAT-01   # theo nhánh hiện tại; xác nhận task Jira khi commit
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, child]
rules: [AUTH-18, AUTH-21]
risk: high
created: 2026-11-10
updated: 2026-11-10
---
# Nối nhà cung cấp SMS Twilio (bản dùng thử) và mã kích hoạt tài khoản phụ huynh qua SMS

## Context
User muốn cấu hình OTP qua SMS bằng Twilio bản free (trial); luồng đầu tiên: mã kích hoạt tài khoản phụ huynh. Hiện trạng:
- Cổng SMS: interface `integration/sms/SmsGateway.java:7`, bean duy nhất `FakeSmsGateway.java:12` (không gửi, chỉ log 2 số cuối). Chưa có cấu hình Twilio nào trong code, docs, git log.
- Kích hoạt hiện tại: tạo user Keycloak với mật khẩu tạm (`SelectedParentAccountServiceImpl.java:157-160`, `IdentityProviderClient.java:30`), gửi **mật khẩu tạm** qua SMS giả (`ParentSmsSender.java:29-31`; gọi từ `ChildEnrollmentServiceImpl.java:247`, `SelectedParentAccountServiceImpl.java:168`).
- OTP hiện chỉ qua email cho tài khoản Web: `AuthServiceImpl.java:486` → `KeycloakAuthGatewayClient.java:99` → extension `AuthOperations.java:70`. Challenge/lần sai/quota: `AuthStateStore.java:44`; mục đích token `PasswordTokenPurpose.java` (`OTP`, `FIRST_LOGIN`, `RESET`).
- AUTH-18 (`docs/business/BUSINESS_RULES.md:32`, CONFIRMED, 30/10): không gửi mật khẩu qua SMS; có nhà cung cấp SMS thì chỉ gửi mã/đường dẫn kích hoạt có thời hạn.

## Quyết định đã chốt
1. Không gửi mật khẩu plaintext qua SMS (AUTH-18) — user, 30/10/2026.
2. Dùng cổng SMS giả trước, chọn nhà cung cấp thật trước khi dùng (D-2 A) — user, 28/10/2026.
3. Phụ huynh, bếp không dùng gateway Web; HT/PHT cấp lại cho phụ huynh (AUTH-21) — user.
4. Luồng SMS đầu tiên: **mã kích hoạt tài khoản phụ huynh** (thay mật khẩu tạm) — user, 10/11/2026.
5. Mã 6 số, hạn 72 giờ, sai 5 lần hủy; phụ huynh tự đặt mật khẩu trên APP; chỉ HT/PHT gửi lại mã (60 giây); SMS lỗi ⇒ phiếu in mã; tài khoản đã có không gửi mã; Twilio trial chỉ dev/demo — user, 10/11/2026 (AUTH-18).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** Actor: HT/PHT (kích hoạt, gửi lại mã), phụ huynh (nhập mã trên APP) · Outcome: phụ huynh nhận mã qua SMS, tự đặt mật khẩu, không ai thấy mật khẩu · Rule: AUTH-18, AUTH-21 (CONFIRMED) · Flow: `[B]` lưu form tiếp nhận / kích hoạt ở màn Tài khoản phụ huynh → `[S]` tạo tài khoản không mật khẩu + mã có hạn → `[S]` SMS → `[APP]` nhập SĐT + mã → đặt mật khẩu → đăng nhập.
- **Đối chiếu:** AUTH-18 cho phép "mã/đường dẫn kích hoạt có thời hạn" — khớp. Code hiện gửi mật khẩu tạm qua SMS (lệch, `BUSINESS_RULES.md:323`) ⇒ Phase 2 sửa.
- **Ảnh hưởng:** identity-access, child (tiếp nhận) · endpoint công khai mới cho APP (chưa đăng nhập) · FE: `/children/activation` (gửi lại mã, phiếu in), form tiếp nhận (kết quả kích hoạt) · APP: màn "Kích hoạt tài khoản" mới · dữ liệu cũ: tài khoản đã kích hoạt bằng mật khẩu tạm giữ nguyên.

### Câu hỏi mở
- [x] 1. OTP SMS dùng cho luồng nào trước? ⇒ mã kích hoạt.
- [x] 2. Tin kích hoạt có mật khẩu khi bật Twilio? ⇒ suy từ câu 1 + AUTH-18: tin đổi sang mã ở Phase 2; trước đó đường kích hoạt vẫn đi cổng giả.
- [x] 3. Twilio trial chỉ dùng dev/demo? ⇒ đúng; production chọn nhà cung cấp có brandname VN sau (D-2).
- [x] 4. Mã Jira? ⇒ tạm theo nhánh (G94-60); xác nhận task Jira khi commit.
- [x] 5. Mã dạng gì, hạn, lần sai? ⇒ 6 số, hạn 72 giờ, sai 5 lần thì mã hủy.
- [x] 6. Kích hoạt trên APP? ⇒ nhập SĐT + mã → đặt mật khẩu (policy Keycloak) → đăng nhập; bỏ mật khẩu tạm.
- [x] 7. Ai gửi lại mã? ⇒ HT/PHT có `PARENT_ACCOUNT_MANAGE` ở màn Tài khoản phụ huynh, cách nhau 60 giây; phụ huynh không tự xin.
- [x] 8. Dự phòng khi SMS lỗi? ⇒ phiếu in mã kích hoạt (không in mật khẩu), cùng hạn.
- [x] 9. Tài khoản đã có? ⇒ không gửi mã, giữ mật khẩu đang dùng.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| 1. Luồng OTP SMS đầu tiên | Mã kích hoạt tài khoản phụ huynh | user | 2026-11-10 | plan này (AUTH-18 đã có sẵn) |
| 2. Tin kích hoạt có mật khẩu | Đổi sang mã ở Phase 2; trước đó cổng giả | agent suy từ câu 1 + AUTH-18 | 2026-11-10 | plan này |
| 3, 5–9 | Nhận hết đề xuất agent (giá trị ở danh sách trên) | user | 2026-11-10 | AUTH-18 (`BUSINESS_RULES.md:32`) |
| 4. Jira | Tạm G94-60 theo nhánh, xác nhận khi commit | agent, theo tiền lệ plan `2026-10-28-child-enrollment-guardian-accounts.md` | 2026-11-10 | frontmatter |

## Acceptance criteria
- **AC-1** (D-2): Given không đặt `carenest.sms.provider`, When gửi SMS, Then dùng `FakeSmsGateway`, không gọi mạng.
- **AC-2:** Given `provider=twilio` đủ cấu hình, When `send("0912345678", text)`, Then POST `/2010-04-01/Accounts/{sid}/Messages.json` Basic auth, form `To=+84912345678`, `From`/`MessagingServiceSid`, `Body`; 2xx ⇒ không lỗi.
- **AC-3:** Given Twilio trả 4xx/5xx hoặc timeout, When gửi, Then ném `SmsDeliveryException`; log chỉ có mã lỗi Twilio, không SĐT/nội dung/token.
- **AC-4:** Given `provider=twilio` thiếu SID/token/sender, When khởi động, Then app dừng, báo rõ thiếu biến nào.
- **AC-5** (AUTH-18): Given `provider=twilio`, When kích hoạt phụ huynh trước khi Phase 2 xong, Then tin có mật khẩu tạm không đi qua Twilio.
- **AC-6..n** (AUTH-18, Phase 2): chi tiết trong Domain spec 014 — gửi mã khi tạo tài khoản mới, mã hết hạn sau 72 giờ, sai 5 lần hủy mã, HT/PHT gửi lại (60 giây), tài khoản đã có không gửi mã, SMS lỗi ⇒ phiếu in mã.

## Key decisions
| Quyết định | Chọn (đề xuất) | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Cách gọi Twilio | REST Messages API qua `RestClient` có sẵn | Twilio Java SDK; Twilio Verify | Không thêm dependency; đổi nhà cung cấp VN chỉ thay adapter |
| Sinh/kiểm mã | BE tự sinh, lưu hash + lần sai (mở rộng `password_tokens`, mục đích mới `ACTIVATION`) | Twilio Verify; bảng riêng | Cùng cách lưu OTP email; Verify khóa vào Twilio, hạn tối đa ngắn hơn 72 giờ |
| Tài khoản Keycloak lúc tạo | Tạo không mật khẩu; kích hoạt thành công ⇒ BE đặt mật khẩu qua Admin API | Giữ mật khẩu tạm ẩn | Không tồn tại mật khẩu ai biết ngoài phụ huynh |
| Endpoint kích hoạt | Endpoint công khai riêng cho APP, giới hạn theo SĐT hash | Qua gateway Web `/auth/**` | Gateway Web cấm phụ huynh (AUTH-21); cần ADR vì endpoint chưa đăng nhập đặt mật khẩu |
| Chọn bean SMS | `@ConditionalOnProperty("carenest.sms.provider")`, mặc định `fake` | Profile Spring | Bật/tắt bằng env, rollback = đổi biến |

## Phases
### Phase 1 — Adapter Twilio, chọn bằng cấu hình (không đổi nghiệp vụ)
- Files: C `integration/sms/SmsProperties.java` · C `integration/sms/TwilioSmsGateway.java` · M `integration/sms/FakeSmsGateway.java` · M `service/account/impl/ParentSmsSender.java` · M `src/main/resources/application.yml` · M `.env.example` · M `compose.yaml` · M `docs/system/DEPLOYMENT.md` · C `TwilioSmsGatewayTest`, `SmsGatewaySelectionTest`
- Steps:
  1. `SmsProperties` record `carenest.sms`: `provider`, `twilio.accountSid`, `twilio.authToken`, `twilio.from`, `twilio.messagingServiceSid`, timeout kết nối 5s/đọc 10s; `toString` che token.
  2. `TwilioSmsGateway`: `0XXXXXXXXX` ⇒ `+84XXXXXXXXX`, sai định dạng ⇒ `SmsDeliveryException`; POST form; lỗi như AC-3; fail-fast như AC-4.
  3. `FakeSmsGateway` chỉ khi `provider=fake` hoặc không đặt; `ParentSmsSender` tin kích hoạt luôn dùng cổng giả (AC-5) tới Phase 2.
  4. Env rỗng trong `.env.example` (`CARENEST_SMS_PROVIDER=fake`, `TWILIO_ACCOUNT_SID=`, `TWILIO_AUTH_TOKEN=`, `TWILIO_FROM=`), truyền qua `compose.yaml`; `DEPLOYMENT.md` hướng dẫn trial: đăng ký → SID/token ở Console → số trial → Verified Caller IDs → bật Geo Permissions Việt Nam → đặt env.
- Tests: AC-1, AC-4 → `SmsGatewaySelectionTest` (`ApplicationContextRunner`); AC-2, AC-3 → `TwilioSmsGatewayTest` (`MockRestServiceServer`); AC-5 → `ChildEnrollmentIntegrationTest` case `@DisplayName("AUTH-18: …")`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0. Smoke tay (user, cần secret): gửi 1 tin tới số đã xác minh.
- **Dừng chờ user review.**

### Phase 2 — Mã kích hoạt qua SMS (BE)
- Feature mới ⇒ `.ai/workflows/feature-spec-loop.md`: Domain spec + `specs/014-parent-activation-code/` + ADR endpoint kích hoạt công khai. Phạm vi dự kiến: migration (mục đích `ACTIVATION`), tạo tài khoản không mật khẩu, tin SMS chứa mã, `POST` gửi lại mã (HT/PHT), endpoint công khai kiểm mã + đặt mật khẩu, bỏ SMS mật khẩu tạm, cập nhật `AUTH_CONTRACT.md`, `openapi.yaml`, `CROSS_REPO_MAP.md`. >8 file ⇒ spec riêng chia phase chi tiết.
- **Dừng chờ user review.**

### Phase 3 — Client (repo khác, plan riêng)
- APP: màn "Kích hoạt tài khoản". FE: màn Tài khoản phụ huynh "Gửi lại mã", phiếu in mã; form tiếp nhận hiện kết quả gửi mã. Chỉ làm khi user cho phép sửa repo FE/APP.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | D-2 | `SmsGatewaySelectionTest#defaultsToFakeGateway` (context) | 1 | pass |
| AC-2 | — | `TwilioSmsGatewayTest#postsFormWithBasicAuthAndE164Phone`, `#usesMessagingServiceWhenConfigured` | 1 | pass |
| AC-3 | — | `TwilioSmsGatewayTest#rejectionCarriesOnlyStatusAndTwilioCode`, `#serverErrorThrowsDeliveryException`, `#timeoutThrowsDeliveryException` | 1 | pass |
| AC-4 | — | `SmsGatewaySelectionTest#twilioWithoutSecretsFailsStartup`, `TwilioSmsGatewayTest#missingConfigurationFailsFast` | 1 | pass |
| AC-5 | AUTH-18 | `ParentSmsSenderTest#realProviderNeverReceivesTemporaryPassword` (unit) | 1 | pass |
| AC-6..14 | AUTH-18 | `ParentActivationCodesTest`, `ParentActivationServiceImplTest`, `ParentActivationControllerTest`, `GuardianAccountServiceImplTest#resendActivationCode*` (unit/WebMvc) | 2 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Mật khẩu tạm đi qua SMS thật | Vi phạm AUTH-18 | AC-5 | `CARENEST_SMS_PROVIDER=fake` |
| Lộ Auth Token | Người khác gửi SMS bằng tài khoản team | Chỉ env, `toString` che, không log | Rotate token trên Console |
| Endpoint kích hoạt công khai bị dò mã | Chiếm tài khoản chưa kích hoạt | Hash mã, 5 lần sai hủy mã, quota theo SĐT hash, ADR | Tắt endpoint, quay về phiếu in |
| Nhà mạng VN lọc tin không brandname | Tin không tới | Trial chỉ demo; phiếu in mã (câu 8) | provider `fake` |
| Hết tín dụng trial | Gửi lỗi | `SmsDeliveryException` ⇒ `FAILED`, nghiệp vụ vẫn lưu | provider `fake` |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: Phase 1 không đổi API; Phase 2 đổi APP (màn kích hoạt), FE `/children/activation` và form tiếp nhận — chi tiết ở spec 014
- [x] Mỗi AC Phase 1 có test; phase có Exit
- [x] >8 file / 3 phase: Phase 1 chạm 10 file (2 test, 3 cấu hình/docs) giữ 1 phase vì cùng một adapter; Phase 2 tách spec riêng, Phase 3 plan client riêng
- [x] Không dùng rule PENDING/OPEN như đã chốt (câu 5–9 ghi là đề xuất)
- [x] Tự review: không placeholder · không mâu thuẫn · scope 1 plan

## Progress log
### 2026-11-10 — Tạo plan (draft)
- Commit: chưa commit
- Verify: chưa chạy (chưa sửa code)
- Giả định (user soát): "twinblo" = Twilio; dùng REST không thêm SDK; trial chỉ dev/demo.

### 2026-11-10 — User chọn mã kích hoạt (draft)
- Trả lời câu 1: mã kích hoạt tài khoản phụ huynh. Thêm câu 5–9, Phase 2 (BE, spec 014), Phase 3 (client).

### 2026-11-10 — User nhận hết đề xuất câu 3, 5–9 (draft)
- Đóng mọi câu hỏi mở; câu 4 Jira tạm G94-60 theo nhánh. Ghi AUTH-18 (`BUSINESS_RULES.md:32`).
- Tiếp: user duyệt plan ⇒ `approved` ⇒ Phase 1 (adapter Twilio). Phase 2 bắt đầu bằng Domain spec 014.

### 2026-11-11 — Phase 1 (chờ review)
- Duyệt: user 2026-11-11 ("làm phase 1 đi") ⇒ `in-progress`.
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 1200 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 568s | log target/verify.log`
- Khác plan: AC-5 kiểm bằng unit `ParentSmsSenderTest` (không cần DB) thay `ChildEnrollmentIntegrationTest`; `ParentSmsSender` dùng một `FakeSmsGateway` nội bộ cho tin kích hoạt khi provider thật (giữ `@MockitoBean SmsGateway` của test cũ hoạt động); thêm biến `TWILIO_MESSAGING_SERVICE_SID`. `compose.vps.yaml`/`compose.dev.yaml` không truyền `TWILIO_*` ⇒ VPS luôn cổng giả (ghi ở `DEPLOYMENT.md`).
- Chưa kiểm: gửi thật qua Twilio trial (cần secret của user).
- Tiếp: user review Phase 1; smoke tay theo `docs/system/DEPLOYMENT.md` mục SMS; Phase 2 bắt đầu bằng Domain spec 014.

### 2026-11-11 — Phase 2 (đang làm dở, dừng do hết hạn mức)
- User: "làm phase 2 đi". Xong: ADR-0024, `specs/014-parent-activation-code/` (domain-spec, spec, plan, data-model, contract, tasks T001–T002); V123 `parent_activation_codes` (V83 trước khi gộp dev 13/11/2026), entity, repository; `IdentityProviderClient.setPassword`; `ParentActivationCodes`; `ApiCode.ACTIVATION_NOT_PENDING` (1136); `ParentSmsSender.sendActivationCode`; form tiếp nhận và kích hoạt sau tạo tài khoản không mật khẩu + mã khi bật SMS thật.
- Khác plan: bảng riêng `parent_activation_codes` thay `password_tokens` (ràng buộc hạn ngắn + job dọn gateway) — ADR-0024.
- Chưa làm: endpoint công khai `POST /parent-activation` + `SecurityConfig`; gửi lại mã `POST /parent-accounts/{userId}/activation-code`; test; snapshot OpenAPI; docs (AUTH_CONTRACT, DATA_DICTIONARY, CROSS_REPO_MAP, module card). Chưa compile/verify sau các sửa này.

### 2026-11-11 — Phase 2 xong (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 1221 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 835s | log target/verify.log`
- Làm thêm: `ParentActivationService(Impl)` + `ParentActivationController` `POST /parent-activation` (`permitAll` ở `SecurityConfig`); `GuardianAccountService.resendActivationCode` + `POST /parent-accounts/{userId}/activation-code`; DTO `ParentActivationRequest/Response`, `ActivationCodeResponse`; snapshot OpenAPI cập nhật có chủ đích (chỉ thêm 2 endpoint, 3 schema); docs AUTH_CONTRACT, DATA_DICTIONARY, ERD, CROSS_REPO_MAP, module card identity-access; spec 014 tasks T001–T007 + analyze/converge.
- Test mới: `ParentActivationCodesTest` (9), `ParentActivationServiceImplTest` (6), `ParentActivationControllerTest` (3), `GuardianAccountServiceImplTest#resendActivationCode*` (3).
- Khác plan/giả định (user soát): bật mã kích hoạt theo `CARENEST_SMS_PROVIDER` (cổng giả = mật khẩu tạm như cũ); `POST /children/{childId}/guardians` kiểu cũ vẫn trả mật khẩu tạm; method controller đặt `activateParentAccount` để không đổi `operationId` `activate` của API cũ; đặt mật khẩu Keycloak và đánh dấu mã đã dùng là hai bước (ADR-0024).
- Chưa kiểm: test tích hợp có DB cho chế độ SMS thật (cần context cấu hình Twilio) — phủ bằng unit; gửi SMS thật; màn APP/FE (Phase 3, repo khác); giới hạn theo IP cho endpoint công khai (ADR-0024 Còn chờ).
- Tiếp: user review Phase 2; Phase 3 là plan client ở FE/APP, chỉ làm khi user cho phép sửa repo khác.
