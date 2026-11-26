# Tasks — thực đơn phụ huynh (G94-497)

Test trước, chạy cho fail đúng lý do rồi mới viết code.

## Phase 1 — Thực đơn tuần (US1, US3)
- [x] T001 Test `ParentMenuIntegrationTest`: không liên kết, con nghỉ học, nhân sự không phải phụ huynh của con ⇒ 4108 (AC-1).
- [x] T002 Test: `weekStart` giữa tuần và bỏ trống; tuần nháp có ảnh ⇒ trống; khối và bữa theo lớp; chuyển lớp giữa tuần (AC-2, AC-3, AC-4).
- [x] T003 Test: ngày nghỉ, trước nhập học, ngoài năm học; không báo ăn tuần này và tuần đã qua (AC-5, AC-6).
- [x] T004 Test: dị ứng khớp bí danh, chữ không khớp, không lấy dữ liệu trẻ khác (AC-8).
- [x] T005 `ParentMenuResponse`, `ParentMenuService`, `ParentMenuServiceImpl#getWeek` cho T001–T004 pass.
- [x] T006 `ParentMenuController` `GET /children/{childId}/menu` + `ParentMenuControllerTest`: role, envelope, JSON không có trường nhân sự (AC-1, AC-9).

## Phase 2 — Ảnh bữa (US2)
- [x] T007 Test integration: ảnh hợp lệ; khác điểm trường, chưa học, ngày nghỉ, bữa ngoài lớp, tuần nháp, bị ẩn do không báo ăn ⇒ 6068; không liên kết ⇒ 4108 (AC-1, AC-7).
- [x] T008 `getPhotoUrl`, `getPhotoContent` dùng chung luật hiển thị của `getWeek`.
- [x] T009 Hai endpoint ảnh + test controller (header no-store, nosniff).
- [x] T010 Snapshot `openapi.yaml`, `API_INDEX.md`, `docs/modules/nutrition.md`, ghi việc App cần đổi; verify.
