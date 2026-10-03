# Module — attendance

Feature: `attendance` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Nguồn dữ liệu có mặt và tham gia ăn của trẻ theo ngày; đơn xin nghỉ.

## Owns
AttendanceRecord, MealParticipation, LeaveRequest.

## KHÔNG owns
MealCount, MealPlan, định lượng (nutrition). Attendance **không biết** nutrition tồn tại.

## Rules
ATT-01..07. Flows: `docs/business/flows/attendance.md`, `docs/business/flows/leave-request.md`.

## Phụ thuộc
- Dùng: service `child` (trẻ trong lớp), service `organization`, identity (scope).
- Được dùng bởi: nutrition (đếm MealParticipation), learning-observation (lịch sử), reporting.
- Event phát: `AttendanceChanged(date, campusId, childId)`, `LeaveRequestApproved`. Attendance không biết MealCount đã chốt hay chưa — nutrition nghe event và tự quyết có tạo adjustment.

## API & bảng
Chưa có. API nhập theo batch (cả lớp/ngày).

## PENDING
P-03 (cut-off), P-04 (late sau chốt), P-15 (đơn nghỉ: ai tạo/duyệt).

## Known pitfalls
- Đếm suất bằng "số trẻ có mặt" là sai — phải dùng MealParticipation (NUT-01).
- Sửa điểm danh sau khi số suất đã xác nhận ⇒ số suất phải được đánh dấu thay đổi + xác nhận lại, không sửa ngầm (ADR-0005).
- Batch save: idempotent theo (childId, date) để tránh bản ghi trùng khi GV bấm lưu nhiều lần.

## Related issues
Chưa có. Rủi ro dự kiến: `docs/knowledge/CROSS_MODULE_ISSUES.md` CMR-01.

## Đọc thêm khi
Thay đổi ảnh hưởng số suất ⇒ card nutrition + ADR-0005 (L3).
