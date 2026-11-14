# Tasks — Ảnh đại diện nhân sự (019)

Thứ tự phụ thuộc. Test đặt `@DisplayName("AUTH-27: …")`.

## Phase 1 — Tự đổi/xóa ảnh (FR-1..3)
- [x] T1 `AccountPhotoController`: bỏ `hasRole('PARENT')` ở PUT/GET `/accounts/me/photo` và `/url` (giữ `isAuthenticated()`); thêm `DELETE /accounts/me/photo`. (FR-1, FR-2, FR-3)
- [x] T2 `ParentPhotoService` → đổi tên `AccountPhotoService` (impl tương ứng), thêm `deleteMyPhoto(CurrentUser)`: tài khoản có role PARENT ⇒ 403; bỏ 3 cột, `removeQuietly` sau commit. (FR-3, AC-2, AC-10)
- [x] T3 Test: GV PUT ảnh → key `avatars/users/` (AC-1); xóa idempotent (AC-2); 3 MB / PDF (AC-3); phụ huynh và GV kiêm phụ huynh DELETE ⇒ 403 (AC-10); test phụ huynh cũ vẫn xanh.

## Phase 2 — Quyền xem + lấy link hàng loạt (FR-4, FR-5)
- [x] T4 `AccountPhotoAccessPolicy` (`security/authorization`): `Set<Long> visibleUserIds(CurrentUser, Set<Long>)` theo AUTH-27; campus GV qua `findCurrentTeacherClassroomIds` → `classroomService.findCampusIds`; PHT/bếp qua `findActiveCampusIds`; nhân sự cùng campus qua `findActiveStaffUserIds`; HT qua `findPrincipalIds`; phụ huynh qua `findChildIdsOfGuardian` → `findActiveClassroomIdByChildIds` → `findCurrentTeacherIds`. (FR-5)
- [x] T5 DTO `AccountPhotoUrlsRequest(@NotEmpty @Size(max=100) List<@Positive Long> userIds)`, `AccountPhotoUrlResponse(userId, url, expiresAt)`; service `findPhotoUrls`: gộp trùng → policy → `findAllById` có ảnh → `presignGet`. (FR-4)
- [x] T6 `POST /accounts/photos/urls` ở `AccountPhotoController`. (FR-4)
- [x] T7 Test: `AccountPhotoAccessPolicyTest` (unit, AC-4…AC-8 + GV kiêm phụ huynh), `AccountPhotoServiceImplTest` (lọc có ảnh/nhân sự), `AccountPhotoControllerTest` (WebMvc, AC-9, 401). Chưa có test tích hợp DB riêng cho quyền xem — policy chỉ ghép service đã có test tích hợp.

## Phase 3 — Tài liệu
- [x] T8 `docs/api/openapi.yaml`, `docs/api/API_INDEX.md`, `docs/modules/identity-access.md` (đoạn ảnh), `docs/system/CROSS_REPO_MAP.md` nếu cần; `docs/system/FE_API_CALLS.md` cập nhật khi FE nối.
