---
title: AUTH-18 — nhận và lưu email liên hệ của phụ huynh
status: done
owner: carenestg94@gmail.com
jira: G94-180 / BE-FEAT-43   # tạm theo nhánh; xác nhận task Jira trước khi commit
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [identity-access]
rules: [AUTH-18]
risk: medium
created: 2026-10-28
updated: 2026-10-28
---
# AUTH-18 — nhận và lưu email liên hệ của phụ huynh

## Context
- User chốt 2026-10-28 (Q2-A, plan FE `CareNest_FE/docs/plans/completed/2026-10-27-guardian-activation-ready-at-enrollment.md`): email phụ huynh chỉ để liên hệ, không dùng nhận diện. AUTH-18 đã ghi "Email phụ huynh chỉ để liên hệ, không dùng nhận diện" (`docs/business/BUSINESS_RULES.md:30`). Cùng ngày user yêu cầu "làm tiếp phần còn lại" sau báo cáo AUTH-18 nêu L-17 (BE nhận, lưu email phụ huynh) là việc còn lại; đề xuất: `CareNest_FE/docs/integration/CHILD_ENROLLMENT_BE_GAPS.md` L-17, module card `docs/modules/identity-access.md:31`.
- `AddGuardianRequest` chỉ có SĐT, họ tên, quan hệ (`src/main/java/com/carenest/dto/child/AddGuardianRequest.java:9-12`); tài khoản phụ huynh mới lưu email null (`GuardianAccountServiceImpl.java:112`, `:116`). `GuardianResponse`, `AddGuardianResponse` không có email.
- `user_accounts.email` VARCHAR(255), cho phép null, không UNIQUE (`src/main/resources/db/migration/V1__create_user_accounts.sql:7`).
- Cột này đang dùng cho đăng nhập/khôi phục của nhân sự: `POST /auth/password/forgot` tìm `findByEmailIgnoreCase`, chỉ gửi OTP khi đúng 1 tài khoản đang hoạt động khớp (`AuthServiceImpl.java:269-272`). Đăng nhập gửi `identifier` cho Keycloak (`AuthServiceImpl.java:103`; `docs/contracts/AUTH_CONTRACT.md:33` "username hoặc email") ⇒ email nào có trên user Keycloak thì đăng nhập được bằng email đó.

## Quyết định đã chốt
1. Email phụ huynh chỉ để liên hệ, không dùng nhận diện — user, 2026-10-28 (Q2-A).
2. Tài khoản nhân sự tách riêng, không chặn SĐT/email phụ huynh — user, 2026-10-28 (Q1).
3. Làm tiếp phần còn lại (L-17) ở repo BE — user, 2026-10-28.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** actor HT / PHT có `PARENT_ACCOUNT_MANAGE` thêm phụ huynh cho trẻ (AUTH-18) · outcome: email phụ huynh nhập ở hồ sơ được BE lưu và trả lại để liên hệ · rule AUTH-18 (CONFIRMED) · flow: [S] thêm phụ huynh, SĐT mới ⇒ tạo tài khoản kèm email liên hệ · [S] SĐT đã có tài khoản ⇒ chỉ thêm liên kết, giữ email của tài khoản · [S] quên mật khẩu không dùng email phụ huynh.
- **Đối chiếu thiết kế ban đầu:** AUTH-18 bản 2026-10-17 không nói tới email phụ huynh; code tạo tài khoản phụ huynh không email.
- **Ảnh hưởng:** module identity-access · quyền/scope: không đổi · FE: chưa gọi API thật (`CareNest_FE/src/services/account/guardianService.js` trả 501 khi không chạy mock) · APP: không gọi các API này · dữ liệu cũ: phụ huynh hiện có email null, không đổi; tài khoản dev `parent.a` có email (`src/main/resources/db/dev/R__dev_seed.sql:11`) ⇒ không còn dùng được ở quên mật khẩu web (phụ huynh dùng APP).

### Câu hỏi mở
Chế độ tự chạy (CLAUDE.md FE, user chốt 2026-10-23): chi tiết kỹ thuật tự chọn phương án khuyến nghị, ghi ở Key decisions để user soát.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Email phụ huynh dùng làm gì? | Chỉ để liên hệ | user | 2026-10-28 | AUTH-18 |

## Acceptance criteria
- **AC-1** (`AUTH-18`): Given SĐT chưa có tài khoản, When `POST /children/{childId}/guardians` có `email` `Me@Example.TEST`, Then `user_accounts.email` = `me@example.test`, user Keycloak không có email, response có `email` = `me@example.test`.
- **AC-2** (`AUTH-18`): Given không gửi `email` hoặc gửi chuỗi rỗng, When thêm phụ huynh, Then lưu null như cũ.
- **AC-3** (`AUTH-18`): Given SĐT đã là phụ huynh, When thêm phụ huynh kèm email khác, Then chỉ thêm liên kết, email của tài khoản giữ nguyên, response trả email của tài khoản.
- **AC-4**: Given `email` sai định dạng hoặc dài hơn 255 ký tự, When thêm phụ huynh, Then 400, không gọi service.
- **AC-5** (`AUTH-18`): `GET /children/{childId}/guardians` trả `email` của từng phụ huynh.
- **AC-6** (`AUTH-18` "không dùng nhận diện"): Given email chỉ khớp tài khoản phụ huynh, When `POST /auth/password/forgot`, Then không gửi OTP (response cùng shape). Given email khớp một nhân sự và một phụ huynh, Then nhân sự vẫn nhận OTP.
- **AC-7**: `docs/api/openapi.yaml`, `AUTH_CONTRACT.md`, `DATA_DICTIONARY.md`, module card ghi field và quy tắc mới.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lưu ở đâu | `user_accounts.email` (có sẵn, nullable, không UNIQUE) | Cột riêng `contact_email`; bảng phụ huynh trên hồ sơ trẻ (AUTH-20, chưa có) | Đúng đề xuất L-17 và module card; không cần migration; khi làm AUTH-20 sẽ xét chuyển |
| Keycloak | Không gửi email phụ huynh lên Keycloak (`NewIdentityUser.email` = null như cũ) | Gửi | Email trên Keycloak thành tên đăng nhập thứ hai; Keycloak chặn email trùng với nhân sự, trái Q1 |
| Quên mật khẩu | Bỏ tài khoản `PARENT` khi tìm theo email | Giữ nguyên | Phụ huynh trùng email nhân sự (vd. giáo viên có con học ở trường) làm có 2 kết quả ⇒ nhân sự không nhận OTP; phụ huynh dùng APP, không dùng gateway web |
| SĐT đã có tài khoản | Không ghi đè email tài khoản | Ghi đè; điền khi đang trống | AUTH-18 "chỉ thêm liên kết"; giống họ tên hiện nay và BE giả FE (`CareNest_FE/src/mocks/parentAccountMock.js:72-120`); sửa thông tin phụ huynh thuộc AUTH-21 (chưa có API) |
| Chuẩn hóa, kiểm | `@Email @Size(max = 255)`; trim + chữ thường; rỗng ⇒ null | — | Giống email nhân sự (`CreateAccountRequest.java:20`, `AccountProvisioningServiceImpl.java:83-85`) |

## Phases
### Phase 1 — Code và test
- Files: M `src/main/java/com/carenest/dto/child/AddGuardianRequest.java` · M `src/main/java/com/carenest/dto/child/AddGuardianResponse.java` · M `src/main/java/com/carenest/dto/child/GuardianResponse.java` · M `src/main/java/com/carenest/service/account/impl/GuardianAccountServiceImpl.java` · M `src/main/java/com/carenest/service/account/impl/AuthServiceImpl.java` · M `src/test/java/com/carenest/service/account/impl/GuardianAccountServiceImplTest.java` · M `src/test/java/com/carenest/controller/child/GuardianControllerTest.java` · M `src/test/java/com/carenest/service/account/impl/AuthServiceImplTest.java`
- Steps: 1. Test đỏ AC-1, AC-3, AC-5, AC-6. 2. Thêm field DTO, lưu email, trả email. 3. Lọc `PARENT` ở quên mật khẩu.
- Tests: AC-1, AC-2 → `GuardianAccountServiceImplTest#storesContactEmailOnlyInCareNest`, `blankEmailIsStoredAsNull` · AC-3 → `existingParentKeepsAccountEmail` · AC-5 → `listGuardiansReturnsEmail` · AC-4 → `GuardianControllerTest#invalidOrTooLongEmailIs400` · AC-6 → `AuthServiceImplTest#parentContactEmailIsNotUsedForRecovery`
- Exit: `mvnw test -Dtest=GuardianAccountServiceImplTest,GuardianControllerTest,AuthServiceImplTest` pass.

### Phase 2 — Hợp đồng, tài liệu, đồng bộ FE
- Files: M `docs/api/openapi.yaml` (`-Dopenapi.snapshot.update=true`) · M `docs/contracts/AUTH_CONTRACT.md` · M `docs/database/DATA_DICTIONARY.md` · M `docs/modules/identity-access.md` · M `docs/context/CURRENT_STATE.md` · FE: `CareNest_FE/docs/integration/CHILD_ENROLLMENT_BE_GAPS.md` (L-17), `CareNest_FE/docs/features/README.md`
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full` có Docker (snapshot chạy, không skip); `node scripts/check-ai-layer.mjs` (BE, FE) không thêm lỗi.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | AUTH-18 | `GuardianAccountServiceImplTest#storesContactEmailOnlyInCareNest` (service) | 1 | pass (đỏ trước khi sửa) |
| AC-2 | AUTH-18 | `GuardianAccountServiceImplTest#blankEmailIsStoredAsNull` (service) | 1 | pass (chặn hồi quy, xanh từ đầu) |
| AC-3 | AUTH-18 | `GuardianAccountServiceImplTest#existingParentKeepsAccountEmail` (service) | 1 | pass (đỏ trước khi sửa) |
| AC-4 | — | `GuardianControllerTest#invalidOrTooLongEmailIs400` (web) | 1 | pass |
| AC-5 | AUTH-18 | `GuardianAccountServiceImplTest#listGuardiansReturnsEmail` (service) | 1 | pass (đỏ trước khi sửa) |
| AC-6 | AUTH-18 | `AuthServiceImplTest#parentContactEmailIsNotUsedForRecovery` (service) | 1 | pass (đỏ trước khi sửa) |
| AC-7 | — | `OpenApiSnapshotTest` (Docker) + `node scripts/check-ai-layer.mjs` | 2 | snapshot cập nhật: chỉ thêm `email` ở 3 schema; xem Progress log |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Client gửi email sai định dạng | 400 mới cho `POST /children/{childId}/guardians` | Field tùy chọn; FE kiểm định dạng trước khi gửi | Bỏ `@Email` |
| Phụ huynh từng khôi phục mật khẩu web bằng email | Không nhận OTP nữa | Phụ huynh dùng APP; mật khẩu phụ huynh do HT/PHT cấp lại (AUTH-19) | Bỏ bộ lọc `PARENT` |
| Bảng phụ huynh trên hồ sơ trẻ (AUTH-20) làm sau | Email có thể nằm 2 nơi | Ghi vào module card để xét khi thiết kế AUTH-20 | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: thêm `email` (tùy chọn) vào request, thêm `email` vào 2 response — FE chưa gọi API thật, APP không gọi; quên mật khẩu giữ shape — FE gateway web (chỉ nhân sự)
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] 8 file code/test + 5 tài liệu BE + 2 tài liệu FE, 2 phase
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan · không câu mơ hồ

## Progress log
### 2026-10-28 — Plan
- Duyệt: chế độ tự chạy (CLAUDE.md FE, user chốt 2026-10-23); user "làm tiếp phần còn lại" sau báo cáo nêu L-17. Quyết định kỹ thuật ở Key decisions chờ user soát.
- Commit: chưa commit.

### 2026-10-28 — Phase 1 (done)
- DTO thêm `email`; `addGuardian` lưu email chuẩn hóa vào tài khoản mới, Keycloak vẫn không có email; 2 response trả `account.getEmail()`; `forgot` lọc `PARENT`. `mvnw spotless:apply`.
- Trước khi sửa logic: `mvnw test -Dtest=GuardianAccountServiceImplTest,GuardianControllerTest,AuthServiceImplTest` → 37 test, 4 đỏ (AC-1, AC-3, AC-5, AC-6). Sau khi sửa (thêm `AccountProvisioningServiceImplTest`): 69/69 pass.
- Commit: chưa commit.

### 2026-10-28 — Phase 2 (done)
- `mvnw test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true` (Docker) → `docs/api/openapi.yaml` +9 dòng: `email` (`format: email`, `maxLength: 255`) ở `AddGuardianRequest`; `email` ở `AddGuardianResponse`, `GuardianResponse`.
- `AUTH_CONTRACT.md` (bảng phụ huynh, ghi chú `email`, bảng lỗi, quên mật khẩu), `DATA_DICTIONARY.md` (`user_accounts.email`), module card, `CURRENT_STATE.md`. FE: `CHILD_ENROLLMENT_BE_GAPS.md` L-17, `docs/features/README.md`.
- Verify: xem báo cáo cuối (chạy sau lần sửa cuối).
- Commit: chưa commit.
