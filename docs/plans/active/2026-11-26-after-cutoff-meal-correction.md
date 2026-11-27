---
title: Sửa nhầm tham gia ăn sau giờ chốt khi sổ chưa xác nhận
status: approved
owner: Cần
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [attendance, meals]
rules: [ATT-02a, ATT-06, ATT-07, ATT-08]
risk: high
created: 2026-11-26
updated: 2026-11-26
---

# Sửa nhầm tham gia ăn sau giờ chốt khi sổ chưa xác nhận

## Context

`AttendanceServiceImpl#saveClassAttendance` cho ngày mở lại đang tính lại suất từ lựa chọn buổi có mặt và phát `AttendanceChangedEvent`. Điều này làm thao tác sửa chuyên cần vô tình đổi suất sau giờ chốt. Ngược lại, giáo viên cần sửa được lỗi đã gạt nhầm “Tham gia ăn” trong chính ngày đó trước khi sổ suất của các bữa liên quan được xác nhận.

## Quyết định đã chốt

1. Sau giờ chốt, giáo viên chỉ được sửa nhầm “Tham gia ăn” của ngày hôm nay khi sổ suất của tất cả bữa bị ảnh hưởng chưa xác nhận — user, 26/11/2026.
2. Sổ suất đã xác nhận hoặc đã bị khóa bởi nghiệp vụ bếp không sửa được; không mở lại, không xác nhận lại — user, 26/11/2026.
3. Sửa buổi có mặt trong ngày đã mở lại không tự đổi suất; chỉ thao tác sửa suất riêng mới đổi sổ — user, 26/11/2026.

## Làm rõ nghiệp vụ

- **Hiểu nghiệp vụ:** giáo viên lớp sửa nhầm công tắc ăn cho trẻ trong ngày hiện tại; server kiểm lớp, ngày, giờ chốt và trạng thái sổ từng bữa. Thay đổi ghi lịch sử điểm danh với người và thời điểm thực hiện.
- **Đối chiếu:** ATT-02a hiện cho sửa sau giờ chốt khi mở lại; ATT-07 chỉ yêu cầu xác nhận lại khi số đã xác nhận bị đổi. Quyết định mới tạo ngoại lệ hẹp: chỉ thay đổi khi chưa xác nhận, nên không có số đã xác nhận cần duyệt lại.
- **Ảnh hưởng:** attendance xác định trẻ và các bữa phục vụ; meals cập nhật sổ chưa xác nhận qua event; Web thêm thao tác sửa suất riêng. Không ảnh hưởng App.

### Câu hỏi mở

Không có.

## Acceptance criteria

- **AC-1** (`ATT-02a`, `ATT-06`): Given hôm nay đã qua giờ chốt và giáo viên của lớp, When sửa công tắc ăn của trẻ có mặt, Then chỉ các bữa trẻ thực tế có mặt được đổi và lịch sử ghi người sửa.
- **AC-2** (`ATT-07`): Given một bữa liên quan đã xác nhận hoặc bị khóa bếp, When giáo viên sửa suất, Then API từ chối và không đổi điểm danh hay sổ suất.
- **AC-3** (`ATT-08`): Given ngày đã mở lại, When giáo viên đổi Cả ngày/Chỉ sáng/Chỉ chiều, Then suất đang có giữ nguyên; chỉ sửa suất riêng mới phát thay đổi cho sổ chưa xác nhận.

## Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Cách sửa | Endpoint riêng nhận một công tắc ăn | Dùng lại lưu điểm danh | Tách rõ sửa chuyên cần và sửa suất, không tự đổi suất khi chọn buổi. |
| Cửa sổ sửa | Hôm nay, sau giờ chốt, các bữa liên quan chưa xác nhận | Mở lại mọi ngày hoặc cho sửa sổ đã xác nhận | Giữ số bếp đã chốt bất biến. |

## Phases

### Phase 1 — Backend sửa suất có điều kiện
- Files: M `AttendanceController`, `AttendanceService`, `AttendanceServiceImpl`, DTO/API snapshot, `MealCountService` hoặc lookup sổ, integration tests.
- Steps: thêm endpoint sửa công tắc ăn; kiểm quyền lớp, hôm nay, sau giờ chốt, bữa thuộc thời gian có mặt, sổ chưa xác nhận/chưa khóa; cập nhật lịch sử và chỉ phát event khi suất thực sự đổi.
- Tests: AC-1, AC-2 → attendance + meal-count integration tests.
- Exit: targeted Maven tests, OpenAPI snapshot, Spotless pass.
- **Dừng chờ user review.**

### Phase 2 — Web và mock
- Files: M attendance API/service/mock, `AttendancePage`, attendance test.
- Steps: chọn buổi chỉ giữ suất hiện có; hiển thị công tắc sửa suất riêng khi backend cho phép; hiển thị lý do khóa khi sổ đã xác nhận.
- Tests: AC-3 → frontend attendance test, lint, design check, build.
- Exit: targeted frontend tests, lint, design check, build pass.
- **Dừng chờ user review.**

## Test matrix

| AC | Test | Phase |
| --- | --- | --- |
| AC-1 | teacher corrects an unconfirmed serving after cutoff | 1 |
| AC-2 | confirmed or kitchen-locked serving rejects correction | 1 |
| AC-3 | reopened attendance-part selection preserves meals | 2 |

## Rủi ro & rollback

| Rủi ro | Giảm thiểu | Rollback |
| --- | --- | --- |
| Sửa số bếp đã xác nhận | Server kiểm từng bữa trước ghi | Tắt endpoint; dữ liệu đã sửa có lịch sử để đối soát. |
| Chọn buổi tự đổi suất | Tách endpoint và test AC-3 | Khôi phục logic hiển thị cũ, không động dữ liệu đã chốt. |

## Validation log

- Code hiện tại: `AttendanceServiceImpl#saveClassAttendance` tính `mealsOf` cho bản ghi mở lại và phát `AttendanceChangedEvent` khi có khác biệt.
- Consumer: `AttendancePage` gọi `saveClassAttendance`; `MealCountAttendanceListener` nhận event để cập nhật sổ suất.
- Mỗi AC có test theo phase; không dùng rule PENDING/OPEN.

## Progress log

### 2026-11-26 — Approved
- User chốt: sổ đã xác nhận hoặc đã bị bếp khóa không được sửa.
- Tiếp: thực hiện Phase 1.
