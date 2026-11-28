# Module — attendance

Feature: `attendance` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đợt 1 (điểm danh lớp/ngày, mở lại ngày, tổng hợp); đón trẻ (2026-10-31, V66)

## Mục đích
Nguồn dữ liệu có mặt của trẻ theo ngày (số suất suy từ đây — ATT-02); mở lại điểm danh sau giờ chốt; đơn xin nghỉ; đón trẻ = xác nhận trả trẻ (người đón, giờ đón — PCK-01..04, ATT-10).

## Owns
AttendanceRecord (Có mặt/Vắng, vắng có phép theo đơn — ATT-09), ghi nhận đón trẻ (`pickup_records`: cách xác minh, người đón, giờ trả, nội dung cuộc gọi khi gọi phụ huynh — PCK-02, PCK-04), lần mở lại điểm danh (lý do, nhật ký — ATT-08), LeaveRequest. `attendance_cutoffs` lưu lịch sinh hoạt có hiệu lực theo năm học: mở đón trẻ; bắt đầu/kết thúc sáng; bắt đầu/kết thúc chiều; bắt đầu/kết thúc khung trả trẻ; mốc trả trẻ tính đủ ngày. Giờ chốt và giờ đến muộn nhất buổi chiều là cấu hình riêng trên cùng lịch sử; trả trẻ không tự sửa số suất đã ghi. Không còn MealParticipation nhập riêng (ATT-02). Nhiệt độ/biểu hiện đầu ngày nhập cùng màn điểm danh (HLT-07); lưu ở attendance hay health: quyết định khi thiết kế bảng.

## KHÔNG owns
MealCount, MealPlan, định lượng (nutrition). Attendance **không biết** nutrition tồn tại.

## Rules
ATT-01..11, ATT-05b, PCK-01..04, HLT-07, HLT-15 (sức khỏe trong ngày ghi sau giờ chốt — code ở module này). Flows: `docs/business/flows/attendance.md`, `docs/business/flows/leave-request.md`, `docs/business/flows/pickup.md`. G94-180/ATT-09 (đón trẻ theo lớp năm học đang hoạt động, chốt 2026-10-28 — `BUSINESS_RULES` mục G94-180): plan `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md` mục 9.

## Phụ thuộc
- Dùng: service `child` (trẻ trong lớp, danh sách phụ huynh/người giám hộ cho màn đón — PCK-02, liên kết tài khoản phụ huynh để báo), service `organization` (giờ chốt do HT cấu hình — ATT-06, lớp GV phụ trách), service `account` (`ParentPhotoService`: phụ huynh nào có ảnh), service `notification` (báo phụ huynh trẻ đã được đón), identity (scope).
- Được dùng bởi: nutrition (đếm trẻ có mặt), learning-observation (lịch sử), reporting (ATT-11, NUT-19).
- Event phát: `AttendanceChanged(date, campusId, childId)`, `LeaveRequestSubmitted`, `LeaveRequestCancelled`, đã đón trẻ (thông báo phụ huynh — PCK-04). Attendance không biết MealCount đã chốt hay chưa — nutrition nghe event và tự quyết có tạo adjustment.

## API & bảng
Bảng `attendance_records`, `attendance_record_history`, `attendance_day_unlocks` (V21). API: `docs/api/FE_MAPPING.md` mục 3. Nhập theo batch (cả lớp/ngày). Giờ chốt mặc định khi năm chưa cấu hình: `carenest.attendance.default-cutoff` (ATT-06). Ngày học tạm thứ 2–6. Sức khỏe trong ngày (`daily_health_entries`, V22) cũng ở module này: GV lớp ghi khi tiếp nhận trẻ đã lưu Có mặt hôm nay (HLT-15). Chưa có: đơn nghỉ, đón trẻ, thông báo phụ huynh. Code viết theo rule trước khảo sát 28/10 (ATT-02 tham gia ăn, ATT-03 đến muộn, ATT-08 HT/PHT mở lại ngày) — đối chiếu lại ATT-01..11. Thiết kế bảng phần chưa có (PROPOSED): `MEAL_NUTRITION_AI_SCHEMA.md` (file thiết kế của dev, chưa có trong repo) §3.

## PENDING
P-19 (ngày nghỉ tự khóa điểm danh), P-20 (trẻ có đơn nghỉ vẫn đến lớp), P-21, P-22 (sức khỏe đầu ngày). Đã đóng 31/10: P-33 (ảnh phụ huynh ở hồ sơ, phụ huynh tải khi đăng nhập App lần đầu). Đã đóng 28/10: P-03 (giờ chốt — ATT-06), P-04 (đến muộn — ATT-03). Đơn nghỉ: thông báo, không duyệt, GV không tạo thay (ATT-05).

## Known pitfalls

- Concurrent health retries serialize before UUID lookup; unlock creation locks the class before checking active grants. Open FE sheets reconcile parent leave without replacing unrelated drafts. See [concurrency repair](../knowledge/incidents/BUG-261104-reception-concurrency.md).

- Số suất = trẻ có mặt × các bữa cấu hình cho nhóm tuổi (ATT-02, NUT-01, NUT-17); không còn MealParticipation riêng (đổi 28/10).
- Sau giờ chốt trường không nhận trẻ đến muộn: không có trạng thái "đến muộn" (ATT-03, ATT-09). Ngoại lệ duy nhất là trẻ đã bị ghi vắng buổi sáng; GV chỉ có thể ghi "Đã đến" từ giờ bắt đầu nhận trẻ chiều đến trước giờ đến muộn nhất buổi chiều. Thao tác này chỉ đổi chuyên cần nửa ngày, giữ nguyên bữa/sổ suất đã chốt và không phát sự kiện làm sổ suất cần xác nhận lại. Ngày mở lại cũng chỉ sửa chuyên cần. Riêng hôm nay, sửa nhầm công tắc ăn chỉ được khi bữa liên quan chưa xác nhận và phiếu bếp chưa ký; service khóa lại cả hai điều kiện trong giao dịch. Sửa khác sau giờ chốt chỉ qua mở lại có lý do (ATT-08).
- Sửa điểm danh sau khi số suất đã xác nhận ⇒ số suất phải được đánh dấu thay đổi + xác nhận lại, không sửa ngầm (ADR-0005).
- Batch save: idempotent theo (childId, date) để tránh bản ghi trùng khi GV bấm lưu nhiều lần.
- Không có đăng ký trước người đón hộ trên App (PCK-03); người lạ đón ⇒ GV gọi phụ huynh, ghi nội dung cuộc gọi (PCK-02).
- Đón trẻ (plan `docs/plans/completed/2026-10-31-child-pickup.md`): `GET /pickups/classes` (lớp cho ô chọn của Web/App: quyền `PICKUP_RECORD` + GV đang phân công, năm đang hoạt động; không có ⇒ rỗng; không dựa vào `/attendance/classes` vì cờ ở đó theo `ATTENDANCE_RECORD`), `GET /classes/{classId}/pickups?date`, `GET /pickups/children/{childId}`, `POST /pickups`, `GET /pickups?date` — chỉ GV đang phụ trách lớp, năm học đang hoạt động; chỉ trẻ điểm danh Có mặt hôm nay trả được; khớp ảnh chọn phụ huynh theo vị trí trên hồ sơ (BE chép họ tên, quan hệ, SĐT); trả thành công một lần/trẻ/ngày (index UNIQUE có điều kiện); không phân biệt về sớm/đón muộn. Mã lỗi 4101–4107.
- Trẻ đã trả ⇒ `DailyHealthServiceImpl` chặn ghi sức khỏe trong ngày (`HEALTH_CHILD_PICKED_UP`, HLT-15).
- ATT-02 (01/11): bữa ăn suy từ `status` (`AttendanceServiceImpl.mealsFor`: có mặt ⇒ mọi bữa nhóm tuổi của lớp) cả khi lưu, tổng hợp suất, tổng hợp điểm danh và trả `record.meals`; trẻ không báo ăn (CHD-13) không bao giờ có bữa, không thay thế (BE bỏ qua `meals` gửi lên, cờ lấy từ `ChildService.findChildIdsWithoutMeals`); field `meals` trong `SaveAttendanceRequest` deprecated, giá trị bị bỏ qua (giữ để client cũ không vỡ). Bản ghi cũ có `meals` lệch vẫn được đọc theo `status`.
- ATT-09 (01/11): GV gửi `EXCUSED` cho trẻ chưa có bản ghi `EXCUSED` ⇒ 400 `4018` `ATTENDANCE_EXCUSED_NOT_ALLOWED`; gửi lại `EXCUSED` đang có (sau này do đơn nghỉ đặt) được chấp nhận để lưu cả sheet không vỡ.

- Tổng hợp điểm danh (Cần chốt 24/11/2026): khoảng luôn cắt trong năm học của lớp; `semester=1|2` thay cho from/to (ngày học kỳ của năm, chưa hết học kỳ thì tới hôm nay; năm chưa cấu hình học kỳ ⇒ `2026`). Bản ghi chỉ tính trong thời gian trẻ ở lớp (theo enrollment, không theo `classroom_id` lưu trên bản ghi). `ChildTotal.transferredInOn` + `previousClasses[{classId,className,from,to,present,excused,absent}]`: số liệu chỉ xem ở lớp cũ cùng năm.
- Đơn nghỉ gửi trước khi chuyển lớp: sổ báo ăn, "Đã đến", lưu điểm danh xét lớp theo enrollment ngày đó; bản ghi được chuyển sang lớp mới khi GV lớp mới ghi. `LeaveRequestResponse.days[{date,classId,className}]`.
- Người dạy thay đang trong đợt chỉ xem đúng ngày dạy thay (`canViewDay`/`canViewRange`), không xem cả lớp.
- API phụ huynh (ATT-15, AUTH-04, chỉ role PARENT, `ParentAttendanceController`):
  - `POST /leave-requests/preview` body `{childId, fromDate, toDate, reason?, part?, eats?}` ⇒ `{part, offMeals[], keptMeals[], eatsApplies, appliedDates[], skippedDates[]}`; cùng kiểm tra và mã lỗi như `POST /leave-requests` (4019, 4020, 4021, 4022, 4025, 4026, 4028, 4032, 4033, 4035); không lưu. Từ ngày trẻ đã có lịch nghỉ học, gửi/xem trước đơn bị chặn (ATT-05); các đơn cũ không tự hủy.
  - `LeaveRequestResponse` thêm `offMeals[]` (bữa của buổi nghỉ; cả ngày = mọi bữa), `teacherSeenAt`, `teacherSeenByName` (V178: GV của lớp mở `GET /leave-requests` hoặc `GET /classes/{id}/attendance` ngày của đơn; ghi một lần).
  - `GET /children/{childId}/attendance/today` ⇒ `{date, status, absentPart, arrivedAt, classId, className}`.
  - `GET /children/{childId}/attendance?from&to` ⇒ `{childId, from, to, days[{date, status, absentPart, arrivedAt, mealsEaten[], cancelledMeals[], pickedUpByName, classId, className}], totals{schoolDays, present, excused, absent}}`. `status`: PRESENT / ABSENT_EXCUSED / ABSENT / NOT_RECORDED / NO_SCHOOL / NOT_ENROLLED. `pickedUpByName` luôn null (chưa có hàm đọc đón trẻ cho phụ huynh ở `PickupService`).
  - `GET /children/{childId}/daily-health/parent?from&to` ⇒ `[{date, temperature, symptoms[], note, recordedByName, classId, className}]`; API sức khỏe của nhân viên không mở cho phụ huynh.
  - Lỗi: `4108` PARENT_CHILD_FORBIDDEN (403), `4109` PARENT_RANGE_INVALID (400, tối đa 62 ngày, một năm học).

## Related issues
Chưa có. Rủi ro dự kiến: `docs/knowledge/CROSS_MODULE_ISSUES.md` CMR-01.

## Đọc thêm khi
Thay đổi ảnh hưởng số suất ⇒ card nutrition + ADR-0005 (L3).

- ChildDay sức khỏe trả `canRecord=false` và `handedOverAt` sau lần trả thành công; liên hệ lấy từ ChildIntakeService sau kiểm scope, không lấy người đón đã chụp snapshot thay cho hồ sơ hiện tại. Xem BUG-261105-health-pickup-context.
