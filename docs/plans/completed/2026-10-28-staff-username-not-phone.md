---
title: AUTH-18 — tài khoản nhân sự tách riêng SĐT phụ huynh
status: done
owner: carenestg94@gmail.com
jira: G94-180 / BE-FEAT-43   # tạm theo nhánh; xác nhận task Jira trước khi commit
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [identity-access]
rules: [AUTH-18, AUTH-15]
risk: medium
created: 2026-10-28
updated: 2026-10-28
---
# AUTH-18 — tài khoản nhân sự tách riêng SĐT phụ huynh

## Context
- User chốt 2026-10-28 (plan FE `CareNest_FE/docs/plans/completed/2026-10-27-guardian-activation-ready-at-enrollment.md`, Q1): bỏ kiểm "SĐT phụ huynh trùng nhân sự" — nhân sự có tài khoản riêng, không đụng SĐT phụ huynh. Cùng ngày user yêu cầu: "Sửa AUTH-18 theo đề xuất sửa cả repo BE" (đề xuất: `CareNest_FE/docs/integration/CHILD_ENROLLMENT_BE_GAPS.md` L-18).
- AUTH-18 (`docs/business/BUSINESS_RULES.md:30`) còn ghi "SĐT của nhân sự ⇒ từ chối". `GuardianAccountServiceImpl.java:95-101` tìm tài khoản theo tên đăng nhập = SĐT; tài khoản không phải PARENT ⇒ 409 `This phone number belongs to a staff account` (`docs/contracts/AUTH_CONTRACT.md:225`).
- Nhân sự không có cột SĐT (`src/main/java/com/carenest/entity/account/UserAccount.java:28-57`). Tên đăng nhập nhân sự tự đặt 3–100 ký tự `[A-Za-z0-9._-]` (`src/main/java/com/carenest/dto/account/CreateAccountRequest.java:15-17`, `docs/contracts/AUTH_CONTRACT.md:121`), UNIQUE (`docs/database/DATA_DICTIONARY.md:22`).
- `AccountProvisioningServiceImpl.createAccount` (`:63-122`) không chặn tên đăng nhập dạng SĐT ⇒ nhân sự đặt tên đăng nhập `0912345678` thì phụ huynh có SĐT đó không tạo được tài khoản (tên đăng nhập trùng).
- ⇒ "SĐT của nhân sự" ở BE thực chất là tên đăng nhập nhân sự có dạng SĐT. Bỏ câu trong rule chưa đủ vì tên đăng nhập UNIQUE; cần quy ước tên đăng nhập nhân sự không phải SĐT.

## Quyết định đã chốt
1. Tài khoản nhân sự tách riêng, không liên quan SĐT phụ huynh; bỏ "SĐT của nhân sự ⇒ từ chối" khỏi AUTH-18 — user, 2026-10-28.
2. Sửa repo BE theo đề xuất L-18: sửa rule và chặn tên đăng nhập nhân sự dạng SĐT — user, 2026-10-28.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** actor Admin / Hiệu trưởng / PHT tạo tài khoản nhân sự (AUTH-15); Hiệu trưởng / PHT thêm phụ huynh (AUTH-18) · outcome: SĐT phụ huynh không bao giờ đụng tài khoản nhân sự · rule AUTH-18 (CONFIRMED, sửa 2026-10-28), AUTH-15 (CONFIRMED) · flow: [S] tạo nhân sự, tên đăng nhập dạng SĐT ⇒ 400 · [S] thêm phụ huynh, tên đăng nhập = SĐT chuẩn hóa.
- **Đối chiếu thiết kế ban đầu:** AUTH-18 ghi từ chối SĐT của nhân sự (`BUSINESS_RULES.md:30`) ⇒ user đổi 2026-10-28.
- **Ảnh hưởng:** module identity-access · quyền/scope: không đổi · FE: chưa có màn gọi `POST /accounts` (form nhân sự của FE dùng "Email đăng nhập", `CareNest_FE/src/components/school-config/StaffAccountModals.jsx:179`); màn Tài khoản phụ huynh `/children/activation` chỉ còn gặp 409 với tài khoản nhân sự cũ · APP: không · dữ liệu cũ: migration không seed `user_accounts`; DB đang chạy có thể có tài khoản nhân sự tên đăng nhập dạng SĐT ⇒ giữ 409 bảo vệ.

### Câu hỏi mở
Không còn câu hỏi nghiệp vụ; chi tiết kỹ thuật ở Key decisions.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Bỏ kiểm SĐT phụ huynh trùng nhân sự? | Bỏ; nhân sự có tài khoản riêng | user | 2026-10-28 | AUTH-18 |

## Acceptance criteria
- **AC-1** (`AUTH-18`): Given người tạo có quyền, When `POST /accounts` với tên đăng nhập `0912345678`, `84912345678` hoặc `0912.345.678`, Then 400 `Staff username must not be a phone number`, không gọi Keycloak, không kiểm/ghi DB.
- **AC-2** (`AUTH-15`): tên đăng nhập thường (`teacher.a`, `0912345678a`) ⇒ tạo như cũ.
- **AC-3** (`AUTH-18`): Given tài khoản nhân sự cũ có tên đăng nhập = SĐT, When thêm phụ huynh có SĐT đó, Then 409 `This phone number belongs to a staff account`, không gắn trẻ vào tài khoản nhân sự.
- **AC-4**: AUTH-18, `AUTH_CONTRACT.md`, `DATA_DICTIONARY.md`, module card ghi quy tắc mới.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| "Dạng SĐT" | `PhoneNumbers.normalize(username)` có giá trị — cùng định nghĩa SĐT khi thêm phụ huynh | Chỉ `^0\d{9}$`; cấm mọi tên toàn chữ số | Một định nghĩa SĐT cho cả hai luồng; chặn cả `84…`, `0912.345.678` dễ nhầm với SĐT |
| Chỗ kiểm | `createAccount` sau kiểm quyền, trước Keycloak; 400 `GlobalException` | Bean Validation trên DTO | Không đổi schema OpenAPI; giống các lỗi 400 khác của service; người không có quyền vẫn nhận 403 trước |
| 409 khi thêm phụ huynh | Giữ status, `desc` làm bảo vệ cho tài khoản cũ | Bỏ | Tên đăng nhập UNIQUE; bỏ kiểm thì code gắn trẻ vào tài khoản nhân sự |
| Tài khoản nhân sự cũ dạng SĐT | Không tự đổi | Migration đổi tên đăng nhập | Tên đăng nhập Keycloak không đổi được (`DATA_DICTIONARY.md:22`); chưa có dữ liệu thật |

## Phases
### Phase 1 — Code và test
- Files: M `src/main/java/com/carenest/service/account/impl/AccountProvisioningServiceImpl.java` · M `src/main/java/com/carenest/service/account/impl/GuardianAccountServiceImpl.java` (comment) · M `src/test/java/com/carenest/service/account/impl/AccountProvisioningServiceImplTest.java` · M `src/test/java/com/carenest/service/account/impl/GuardianAccountServiceImplTest.java`
- Steps: 1. Test đỏ AC-1. 2. Kiểm trong `createAccount`. 3. Comment và `@DisplayName` cho 409 cũ.
- Tests: AC-1 → `AccountProvisioningServiceImplTest#rejectsPhoneShapedStaffUsername` · AC-2 → test có sẵn + `allowsUsernameThatOnlyLooksNumeric` · AC-3 → `GuardianAccountServiceImplTest#staffPhoneNumberIsConflict`
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full` (máy không có Docker ⇒ test integration bị skip, ghi rõ).

### Phase 2 — Tài liệu và đồng bộ FE
- Files: M `docs/business/BUSINESS_RULES.md` · M `docs/contracts/AUTH_CONTRACT.md` · M `docs/database/DATA_DICTIONARY.md` · M `docs/modules/identity-access.md` · FE (user cho phép đồng bộ): `CareNest_FE/docs/integration/CHILD_ENROLLMENT_BE_GAPS.md` (L-18), `CareNest_FE/docs/features/README.md`
- Exit: `node scripts/check-ai-layer.mjs` (BE, FE) 0 lỗi; `node scripts/verify.mjs` → `VERIFY PASS full`.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | AUTH-18 | `AccountProvisioningServiceImplTest#rejectsPhoneShapedStaffUsername` (service) | 1 | pass (đỏ trước khi sửa: 3/3 ca không ném lỗi) |
| AC-2 | AUTH-15 | `AccountProvisioningServiceImplTest#allowsUsernameThatOnlyLooksNumeric` + test tạo có sẵn (service) | 1 | pass |
| AC-3 | AUTH-18 | `GuardianAccountServiceImplTest#staffPhoneNumberIsConflict` (service) | 1 | pass |
| AC-4 | AUTH-18 | `node scripts/check-ai-layer.mjs` | 2 | không thêm lỗi (7 lỗi có sẵn ở dòng không sửa) |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Môi trường thật đã có tài khoản nhân sự tên đăng nhập dạng SĐT | Phụ huynh có SĐT đó vẫn nhận 409 | Giữ 409; trước khi phát hành kiểm `user_accounts` (nhân sự có tên đăng nhập là SĐT) và xử lý tay | — |
| Tạo nhân sự thẳng trên Keycloak console (bỏ qua BE) | Có thể đặt tên đăng nhập dạng SĐT | Ghi vào module card | — |
| Client đang tạo nhân sự với tên đăng nhập dạng SĐT | Nhận 400 mới | FE hiện không có màn gọi `POST /accounts`; APP không tạo nhân sự | Bỏ kiểm trong `createAccount` |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: `POST /accounts` thêm 400 — FE chưa có consumer (`StaffAccountModals.jsx` chạy BE giả), APP không gọi; 409 thêm phụ huynh giữ nguyên — FE `/children/activation`
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] ≤8 file BE, 2 phase
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan · không câu mơ hồ

## Progress log
### 2026-10-28 — Plan
- Duyệt: user 2026-10-28 ("Sửa AUTH-18 theo đề xuất sửa cả repo BE"; nội dung đề xuất đã trình trong phiên FE). User chạy liền các phase (ưu tiên đã ghi của user), không dừng giữa phase.
- Commit: chưa commit.

### 2026-10-28 — Phase 1 (done)
- `createAccount` từ chối tên đăng nhập dạng SĐT sau kiểm quyền, trước Keycloak; comment + `@DisplayName` cho 409 cũ. `mvnw spotless:apply` định dạng lại test.
- `mvnw test -Dtest=AccountProvisioningServiceImplTest,GuardianAccountServiceImplTest` → 32/32, 9/9 (trước khi sửa: 3 ca AC-1 đỏ).
- Commit: chưa commit.

### 2026-10-28 — Phase 2 (done)
- AUTH-18 (`BUSINESS_RULES.md:30`), `AUTH_CONTRACT.md` (username + bảng lỗi `POST /accounts`, 409 phụ huynh), `DATA_DICTIONARY.md:22`, module card, `CURRENT_STATE.md`; ghi chú AC-2 plan nháp `2026-10-18-parent-account-auto-provisioning.md`. FE (user cho phép): `CareNest_FE/docs/integration/CHILD_ENROLLMENT_BE_GAPS.md` L-18, `CareNest_FE/docs/features/README.md`.
- Khác plan: không sửa `docs/system/CROSS_REPO_MAP.md` — bảng điểm chạm đã trỏ `docs/contracts/`, không có consumer FE/APP mới.
- Verify: xem báo cáo cuối (chạy sau lần sửa cuối).
- Tiếp: chạy `node scripts/verify.mjs` trên máy có Docker (37 test integration và snapshot OpenAPI bị skip trên máy này) rồi chuyển `done`.

### 2026-10-28 — Verify có Docker (done)
- Bật Docker Desktop trên máy này; `node scripts/verify.mjs` → `VERIFY PASS full | tests 310 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok` (trước khi làm plan `2026-10-28-guardian-contact-email.md`; lần chạy sau cùng ghi ở plan đó).
- Trước khi phát hành, chạy trên DB thật để tìm tài khoản nhân sự có tên đăng nhập dạng SĐT (cùng quy tắc `PhoneNumbers.normalize`; đã thử trên PostgreSQL 17 với 10 mẫu: khớp `0912345678`, `84912345678`, `+84912345678`, `0912.345.678`, `0912-345-678`, `(091) 234 5678`; không khớp `0912345678a`, `teacher.a`, `840912345678`, tài khoản `PARENT`). Có kết quả ⇒ đổi tên đăng nhập trên Keycloak và `user_accounts` bằng tay trước khi thêm phụ huynh có SĐT đó.
  ```sql
  SELECT u.id, u.username, u.primary_role
  FROM user_accounts u,
       LATERAL (SELECT regexp_replace(btrim(u.username), '[[:space:].()-]', '', 'g') AS d) s
  WHERE u.primary_role IS DISTINCT FROM 'PARENT'
    AND CASE
          WHEN s.d LIKE '+84%' THEN '0' || substr(s.d, 4)
          WHEN s.d LIKE '84%' AND length(s.d) = 11 THEN '0' || substr(s.d, 3)
          ELSE s.d
        END ~ '^0[0-9]{9}$';
  ```
- Commit: chưa commit.
