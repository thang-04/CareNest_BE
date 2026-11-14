# Tasks — 014
- [x] T001 Domain spec, ADR-0026, kiểm constitution (`.specify/memory/constitution.md` dùng lại, không đổi).
- [x] T002 Spec/plan/data model/contract/tasks và analyze trước code.
- [x] T003 Migration V123, entity, repository khóa dòng; `ParentActivationCodes` sinh/hash/lưu mã (AC-6, AC-8, AC-9) — `ParentActivationCodesTest`.
- [x] T004 `IdentityProviderClient.setPassword`; tạo tài khoản không mật khẩu + SMS mã ở form tiếp nhận và kích hoạt sau khi bật SMS thật; cổng giả giữ mật khẩu tạm (AC-6, AC-14) — `ParentSmsSenderTest`, `ChildEnrollmentIntegrationTest` (cổng giả, không đổi).
- [x] T005 Endpoint công khai kích hoạt + `SecurityConfig` (AC-7, AC-8, AC-9) — `ParentActivationServiceImplTest`, `ParentActivationControllerTest`.
- [x] T006 Gửi lại mã trong phạm vi, cooldown, trả mã khi không gửi được SMS thật (AC-10..AC-13) — `GuardianAccountServiceImplTest#resendActivationCode*`, `ParentActivationCodesTest`.
- [x] T007 Test, snapshot OpenAPI có chủ đích (chỉ thêm), docs AUTH_CONTRACT/DATA_DICTIONARY/ERD/CROSS_REPO_MAP/module card.

## Analyze (trước code, 2026-11-11)
Khớp domain spec ↔ contract ↔ data model ↔ tasks; sửa một lệch: contract ghi sai đường kích hoạt sau (`/account-recipient/activate`) ⇒ `/children/{childId}/guardians/activate`. Không còn lỗi chặn.

## Converge
Vòng 1 (2026-11-11): AC-6 chưa có test tích hợp chế độ SMS thật (context Spring cần cấu hình Twilio) — giữ bằng unit (`ParentSmsSender`, code path giống hệt cổng giả trừ nhánh `codeMode`), ghi giới hạn ở Progress log; không thêm task. Không còn gap khác.
