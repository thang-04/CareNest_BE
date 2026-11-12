---
title: Rút gọn bảng DB auth (gộp quyền, bỏ guardian_child_links) — migration V82
status: in-progress
owner: thangnd
jira: G94-60 / BE-FEAT-01
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, child]
rules: [AUTH-04, AUTH-10, AUTH-11, AUTH-12, AUTH-14, AUTH-18, AUTH-19, AUTH-20]
risk: high
created: 2026-11-09
updated: 2026-11-10
---
# Rút gọn bảng DB auth

## Context
User muốn ERD gọn cho báo cáo đồ án (cùng đợt với bán trú, nhánh day-boarding,
`docs/plans/active/2026-11-09-boarding-table-consolidation.md` trên nhánh đó). Phần auth bỏ 2 bảng:
- `role_permissions` + `user_permission_overrides` → một bảng `permission_grants`.
- `guardian_child_links` trùng với `child_guardians.user_id` → bỏ.

## Quyết định đã chốt (user, 09/11/2026)
1. Mục tiêu: ERD gọn. 2. ~~Sửa baseline tại chỗ~~ → đổi 10/11/2026: hook `.githooks/pre-commit` chặn sửa migration đã
commit, user chọn **migration mới `V82__consolidate_auth_tables.sql`** (chuyển dữ liệu rồi xóa bảng cũ, không reset
DB). Nhánh bán trú dùng V83 ⇒ nhánh này merge vào `dev` trước. 3. Làm trên nhánh auth. 4. API contract giữ nguyên.

## Thay đổi
- `permission_grants` (V82 tạo, chép dữ liệu từ 2 bảng cũ rồi xóa): dòng role (`role`,
  `user_id` NULL) hoặc dòng người (`user_id`, `effect`, `reason`, `granted_by`); CHECK đúng một chủ thể, unique một
  phần theo từng loại. Code giữ 2 entity `RolePermission`, `UserPermissionOverride` map cùng bảng với
  `@SQLRestriction` ⇒ resolver, service, DTO không đổi.
- Bỏ `guardian_child_links` (V82: lấy `created_at` của liên kết làm `linked_at`; dòng hồ sơ còn `user_id` mà không còn
  liên kết bị gỡ `user_id`; liên kết không có dòng hồ sơ — chỉ có ở dữ liệu rất cũ — bị bỏ).
  `child_guardians.linked_at` mới (CHECK có `user_id` ⇔ có `linked_at`); `GuardianLinkResponse.linkedAt` lấy từ đây.
  `GuardianChildLinkService` giữ interface (bỏ `createLink`), impl đọc `ChildGuardianRepository`. Liên kết tạo qua
  `ChildGuardian.linkAccount(user, at)` ở tiếp nhận / kích hoạt; gỡ = `unlinkAccount()`.
- Hành vi đổi: gỡ phụ huynh (`DELETE /children/{id}/guardians/{userId}`) nay xóa luôn `user_id` trên hồ sơ (trước đây
  còn sót). Đổi người nhận tài khoản: người cũ vẫn giữ liên kết như trước.

## Verify
- `node scripts/verify.mjs` full (snapshot OpenAPI không đổi).

## Rủi ro
- Thứ tự merge: nhánh này (V82) trước bán trú (V83).
- Hai entity trên một bảng: insert dòng role để `user_id` NULL, dòng người để `role` NULL — CHECK DB bắt lỗi.
- `ChildEnrollmentMigrationIntegrationTest` kiểm thêm phần chuyển dữ liệu của V82.

## Progress log
- 2026-11-09: Sửa migration, entity, service, repository, seed `db/dev`, 11 file test, DATA_DICTIONARY, ERD, module
  card child + identity-access.
- 2026-11-09 23:00: `node scripts/verify.mjs` PASS full — 1187 test, 0 fail, ArchUnit ok, snapshot OpenAPI không đổi,
  Spotless ok. Chưa commit, chưa reset DB VPS.
- 2026-11-10: Commit bị hook chặn (sửa migration đã commit). Trả V3–V81 về nguyên trạng, thêm V82 chuyển dữ liệu.
  V82 đụng ràng buộc CCCD NOT VALID khi cập nhật hồ sơ cũ thiếu CCCD ⇒ bỏ tạm rồi gắn lại NOT VALID (như V81).
- 2026-11-12: Gộp `origin/dev` (đã tới V121, V114/V116 còn ghi `role_permissions`) ⇒ đổi số `V82__consolidate_auth_tables`
  → `V122` (nội dung giữ nguyên, chạy sau cùng nên chép cả quyền V114). Chuyển phần `dev` mới thêm còn dùng
  `guardian_child_links` sang `child_guardians`: `findLinksOfChildren`, `findAccountRows`, 3 seed `db/dev`, 8 file test.
