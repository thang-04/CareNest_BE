---
title: Ảnh đại diện nhân sự (lưu S3, quyền xem theo điểm trường)
status: in-progress
owner: thangnd
jira: G94-355 / BE-FEAT-57 (FE: G94-356 / FE-FEAT-57)
branch: feature/G94-355-BE-FEAT-57-staff-avatar
modules: [identity-access]
rules: [AUTH-27, AUTH-21, AUTH-01, AUTH-02, AUTH-16]
risk: medium
created: 2026-11-14
updated: 2026-11-14
---
# Ảnh đại diện nhân sự

Spec Kit (làm tay, chưa cài skill): `specs/019-staff-avatar/` (domain-spec, spec, data-model, contracts, tasks).

## Context
Chỉ phụ huynh có ảnh đại diện: `AccountPhotoController.java:36,47,69` `hasRole('PARENT')`. Ảnh nằm trên `user_accounts.photo_key` (`UserAccount.java:61`), lưu S3 `USER_AVATAR` qua `ParentPhotoServiceImpl.java:60`. Web hiện avatar bằng chữ cái đầu (`FE: src/components/ui/Avatar.jsx`, ~13 chỗ dùng).

## Quyết định đã chốt
1. Chỉ làm Web đợt này; App (ảnh phụ huynh, ảnh bữa NUT-16) phase sau — user, 14/11/2026.
2. Nhân sự chỉ tự đổi ảnh, không đổi hộ — user, 14/11/2026.
3. Phụ huynh được xem ảnh GV lớp con — user, 14/11/2026.
4. Lấy link hàng loạt `POST /accounts/photos/urls`; nhân sự xem theo điểm trường — user, 14/11/2026.
5. Ảnh nhân sự dùng chung thư mục `avatars/users/` với phụ huynh (ảnh theo tài khoản) — user duyệt thiết kế, 14/11/2026.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** actor nhân sự (ADMIN/PRINCIPAL/VICE_PRINCIPAL/TEACHER/KITCHEN_STAFF), phụ huynh · outcome ảnh đại diện hiện đúng người được xem · AUTH-27 (mới, CONFIRMED 14/11) · flow `[B]` chọn ảnh → `[S]` lưu S3; `[B]` mở danh sách → `[S]` lọc quyền, ký link.
- **Đối chiếu:** `docs/modules/identity-access.md:27` ghi ảnh chỉ PARENT ⇒ cập nhật ở Phase 3.
- **Ảnh hưởng:** module identity-access; quyền xem dựa phân công (school-structure) và liên kết/enrollment (child) qua service hiện có; FE `AccountSideCard`, `Avatar`; APP chưa đổi.
- Ràng buộc phát sinh: ảnh phụ huynh bắt buộc (`AccountPhotoResponse.profileUpdateRequired`) ⇒ tài khoản có role PARENT không xóa được ảnh (AC-10).

### Câu hỏi mở
Không còn.

## Acceptance criteria
AC-1…AC-10 ở `specs/019-staff-avatar/spec.md` (rule AUTH-27).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Hiển thị ảnh nhiều người | API link hàng loạt | `photoUrl` trong từng DTO; mỗi avatar 1 request | Không đổi DTO danh sách, 1 request/màn, luật xem 1 chỗ |
| Campus của GV | Lớp đang phân công → campus | `findActiveCampusIds(TEACHER)` | Query lọc `campusId is not null`, phân công GV gắn lớp |
| Tên service | Đổi `ParentPhotoService` → `AccountPhotoService` | Giữ tên | Service phục vụ mọi tài khoản sau thay đổi |

## Phases
### Phase 1 — Tự đổi/xóa ảnh
- Files: M `controller/account/AccountPhotoController.java` · M/R `service/account/ParentPhotoService*.java` → `AccountPhotoService*` · M caller của service · C/M test.
- Tests: AC-1, AC-2, AC-3, AC-10.
- Exit: `mvnw.cmd -q -Dtest='AccountPhoto*Test' test` xanh. **Dừng chờ user review.**

### Phase 2 — Quyền xem + link hàng loạt
- Files: C `security/authorization/AccountPhotoAccessPolicy.java` · C 2 DTO · M controller/service · C test.
- Tests: AC-4…AC-9.
- Exit: `mvnw.cmd -q test` toàn bộ xanh. **Dừng chờ user review.**

### Phase 3 — Tài liệu
- Files: M `docs/api/openapi.yaml`, `docs/api/API_INDEX.md`, `docs/modules/identity-access.md`.
- Exit: diff tài liệu khớp code.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1..3, AC-10 | AUTH-27 | `AccountPhotoServiceImplTest`, `AccountPhotoControllerTest` | 1 | pass |
| AC-4..8 | AUTH-27 | `AccountPhotoAccessPolicyTest` (unit) | 2 | pass |
| AC-9 | AUTH-27 | `AccountPhotoControllerTest` (WebMvc) | 2 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Lộ ảnh ngoài phạm vi | Quyền riêng tư | Ma trận AC-4..8, người không được xem bị bỏ qua im lặng | Revert PR, endpoint cũ trở lại PARENT |
| Đổi tên service vỡ caller | Build lỗi | Đổi tên bằng IDE, build + test toàn bộ | Revert commit đổi tên |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể (FE `AccountSideCard`, `Avatar.jsx`; APP chưa dùng)
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] ≤8 file mỗi phase, 3 phase
- [x] Không dùng rule PENDING/OPEN
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan

## Progress log
### 2026-11-14 — Plan draft
- Commit: chưa commit
- Tiếp: chờ user duyệt plan.

### 2026-11-14 — Duyệt + Phase 1–3 (done, chờ commit)
- User duyệt plan ("tiếp đi") và yêu cầu làm liền một mạch tới khi xong (không dừng review từng phase).
- Commit: chưa commit (chờ user cho phép).
- Verify: `mvnw -o test` → `Tests run: 1786, Failures: 0, Errors: 0, Skipped: 0` · `BUILD SUCCESS`; chạy lại `ApiCodeTest`, `EduYearPlanServiceIntegrationTest` sau khi sửa tài liệu → pass. OpenAPI snapshot cập nhật có chủ đích (`-Dopenapi.snapshot.update=true`, chỉ thêm 2 endpoint + 3 schema).
- Khác plan: thêm `ApiCode.ACCOUNT_PHOTO_REQUIRED` (1145, 403) cho AC-10; PreAuthorize các endpoint của tôi dùng `isAuthenticated()` như `/accounts/me` (`AccountController.java:56`).
- FE: `CareNest_FE` nhánh `feature/G94-356-FE-FEAT-57-staff-avatar` (plan client `docs/plans/active/2026-11-14-staff-avatar.md`).
- Tiếp: commit/PR BE trước, FE sau; thử tay trên dev.
