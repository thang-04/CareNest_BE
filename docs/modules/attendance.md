# Module — attendance

Feature: `attendance` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đợt 1 (điểm danh lớp/ngày, mở lại ngày, tổng hợp)

## Mục đích
Nguồn dữ liệu có mặt và tham gia ăn của trẻ theo ngày; đơn xin nghỉ.

## Owns
AttendanceRecord, MealParticipation, LeaveRequest.

## KHÔNG owns
MealCount, MealPlan, định lượng (nutrition). Attendance **không biết** nutrition tồn tại.

## Rules
ATT-01..09, ATT-05b. Flows: `docs/business/flows/attendance.md`, `docs/business/flows/leave-request.md`. ATT-09 (đón trẻ theo lớp năm học đang hoạt động, chốt 2026-10-28; BE chưa có module đón trẻ): plan `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md` mục 9.

## Phụ thuộc
- Dùng: service `child` (trẻ trong lớp), service `organization`, identity (scope).
- Được dùng bởi: nutrition (đếm MealParticipation), learning-observation (lịch sử), reporting.
- Event phát: `AttendanceChanged(date, campusId, childId)`, `LeaveRequestSubmitted`, `LeaveRequestCancelled`. Attendance không biết MealCount đã chốt hay chưa — nutrition nghe event và tự quyết có tạo adjustment.

## API & bảng
Bảng `attendance_records`, `attendance_record_history`, `attendance_day_unlocks` (V18). API: `docs/api/FE_MAPPING.md` mục 3. Nhập theo batch (cả lớp/ngày). Giờ chốt mặc định khi năm chưa cấu hình: `carenest.attendance.default-cutoff` (P-03). Ngày học tạm thứ 2–6. Sức khỏe trong ngày (`daily_health_entries`, V19) cũng ở module này: GV lớp ghi khi tiếp nhận trẻ đã lưu Có mặt hôm nay. Chưa có: đơn nghỉ, số suất, bàn giao, thông báo phụ huynh.

## PENDING
P-03 (cut-off), P-04 (late sau chốt). Đơn nghỉ đã chốt: thông báo, không duyệt (ATT-05).

## Known pitfalls
- Đếm suất bằng "số trẻ có mặt" là sai — phải dùng MealParticipation (NUT-01).
- Sửa điểm danh sau khi số suất đã xác nhận ⇒ số suất phải được đánh dấu thay đổi + xác nhận lại, không sửa ngầm (ADR-0005).
- Batch save: idempotent theo (childId, date) để tránh bản ghi trùng khi GV bấm lưu nhiều lần.

## Related issues
Chưa có. Rủi ro dự kiến: `docs/knowledge/CROSS_MODULE_ISSUES.md` CMR-01.

## Đọc thêm khi
Thay đổi ảnh hưởng số suất ⇒ card nutrition + ADR-0005 (L3).
