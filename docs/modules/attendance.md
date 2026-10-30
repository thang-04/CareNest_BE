# Module — attendance

Feature: `attendance` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Nguồn dữ liệu có mặt của trẻ theo ngày (số suất suy từ đây — ATT-02); mở lại điểm danh sau giờ chốt; đơn xin nghỉ; đón trẻ = xác nhận trả trẻ (người đón, giờ đón — PCK-01..04, ATT-10).

## Owns
AttendanceRecord (Có mặt/Vắng, vắng có phép theo đơn — ATT-09), ghi nhận đón trẻ (người đón, giờ đón, về sớm, lý do khi người lạ đón — PCK-02, PCK-04), lần mở lại điểm danh (lý do, nhật ký — ATT-08), LeaveRequest. Không còn MealParticipation nhập riêng (ATT-02). Nhiệt độ/biểu hiện đầu ngày nhập cùng màn điểm danh (HLT-07); lưu ở attendance hay health: quyết định khi thiết kế bảng.

## KHÔNG owns
MealCount, MealPlan, định lượng (nutrition). Attendance **không biết** nutrition tồn tại.

## Rules
ATT-01..11, ATT-05b, PCK-01..04, HLT-07. Flows: `docs/business/flows/attendance.md`, `docs/business/flows/leave-request.md`, `docs/business/flows/pickup.md`.

## Phụ thuộc
- Dùng: service `child` (trẻ trong lớp, danh sách phụ huynh/người giám hộ cho màn đón — PCK-02), service `organization` (giờ chốt do HT cấu hình — ATT-06), identity (scope).
- Được dùng bởi: nutrition (đếm trẻ có mặt), learning-observation (lịch sử), reporting (ATT-11, NUT-19).
- Event phát: `AttendanceChanged(date, campusId, childId)`, `LeaveRequestSubmitted`, `LeaveRequestCancelled`, đã đón trẻ (thông báo phụ huynh — PCK-04). Attendance không biết MealCount đã chốt hay chưa — nutrition nghe event và tự quyết có tạo adjustment.

## API & bảng
Chưa có. API nhập theo batch (cả lớp/ngày). Thiết kế bảng (PROPOSED, chưa migration): `docs/database/MEAL_NUTRITION_AI_SCHEMA.md` §3.

## PENDING
P-19 (ngày nghỉ tự khóa điểm danh), P-20 (trẻ có đơn nghỉ vẫn đến lớp), P-21, P-22 (sức khỏe đầu ngày), P-33 (ảnh đối chiếu người đón). Đã đóng 28/10: P-03 (giờ chốt — ATT-06), P-04 (đến muộn — ATT-03). Đơn nghỉ: thông báo, không duyệt, GV không tạo thay (ATT-05).

## Known pitfalls
- Số suất = trẻ có mặt × các bữa cấu hình cho nhóm tuổi (ATT-02, NUT-01, NUT-17); không còn MealParticipation riêng (đổi 28/10).
- Sau giờ chốt trường không nhận trẻ đến muộn: không có trạng thái "đến muộn" (ATT-03, ATT-09). Sửa sau giờ chốt chỉ qua mở lại có lý do (ATT-08).
- Sửa điểm danh sau khi số suất đã xác nhận ⇒ số suất phải được đánh dấu thay đổi + xác nhận lại, không sửa ngầm (ADR-0005).
- Batch save: idempotent theo (childId, date) để tránh bản ghi trùng khi GV bấm lưu nhiều lần.
- Không có đăng ký trước người đón hộ trên App (PCK-03); người lạ đón ⇒ GV gọi phụ huynh, ghi lý do (PCK-02).

## Related issues
Chưa có. Rủi ro dự kiến: `docs/knowledge/CROSS_MODULE_ISSUES.md` CMR-01.

## Đọc thêm khi
Thay đổi ảnh hưởng số suất ⇒ card nutrition + ADR-0005 (L3).
