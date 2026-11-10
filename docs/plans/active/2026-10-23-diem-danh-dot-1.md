---
title: Điểm danh & báo ăn — Đợt 1 (điểm danh lớp/ngày, khóa theo giờ chốt, mở lại ngày, tổng hợp)
status: in-progress
owner: Nhữ Đình Cần
jira: G94-236 (BE-FEAT-29)
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [attendance, school-structure, child]
rules: [ATT-01, ATT-01b, ATT-02, ATT-03, ATT-06, ATT-07, CFG-YR-01, AUTH-01, AUTH-02, AUTH-03]
risk: high
created: 2026-10-23
updated: 2026-10-23
---
# Điểm danh & báo ăn — Đợt 1

## Context
- **UI là chuẩn:** CareNest_FE `pages/attendance/AttendancePage.jsx`, `AttendanceDayUnlockPage.jsx`, `AttendanceSummaryPage.jsx`; hành vi lấy từ `services/attendance/mock/attendanceMockRepository.js` (đặc tả rút gọn ở mục "Hành vi theo mock" dưới).
- **BE hiện có:** giờ chốt theo năm học (`attendance_cutoffs`, V17), lớp/năm, phân công GV, enrollment theo ngày, quy tắc năm đã kết thúc chỉ đọc (CFG-YR-01). Chưa có bảng điểm danh.
- **Module card:** `docs/modules/attendance.md` — attendance không biết nutrition; phát event cho nutrition.

## Phạm vi đợt 1
1. `GET /attendance/day-info?date=&classId=` — ngày học, giờ chốt, khóa/mở lại.
2. `GET /classes/{id}/attendance?date=` — danh sách trẻ + bản ghi + bữa của nhóm tuổi.
3. `PUT /classes/{id}/attendance` — lưu theo lớp/ngày, idempotent theo (trẻ, ngày).
4. `GET/POST /attendance/day-unlocks`, `POST /attendance/day-unlocks/{id}/revoke` — mở lại ngày đã khóa.
5. `GET /classes/{id}/attendance/summary`, `GET /attendance/summary` — tổng hợp theo khoảng ngày.

Ngoài đợt 1 (đợt 2–3): số suất + xác nhận, hủy suất sau giờ khóa (`correctMealAfterLock`), bàn giao bếp, tự ghi vắng có phép từ đơn nghỉ.

## Hành vi theo mock (đã đối chiếu)
- Trạng thái: `PRESENT | EXCUSED | UNEXCUSED` (không có "đến muộn"). Vắng thì mọi bữa = false.
- Bữa của lớp = `meal_sessions` của nhóm tuổi; mặc định `[LUNCH, AFTERNOON]` nếu chưa cấu hình.
- Khóa: `date < hôm nay` hoặc (`date = hôm nay` và giờ hiện tại ≥ giờ chốt của năm học). Giờ Asia/Ho_Chi_Minh.
- Ngày học: thứ 2–6 (chưa có lịch nghỉ lễ). Chỉ lớp năm đang hoạt động được ghi; năm đã kết thúc chỉ xem (CFG-YR-01).
- Quyền: ghi = GV đang phân công lớp; xem = HT toàn trường, PHT điểm trường mình, GV lớp mình. Mở lại ngày = HT, PHT điểm trường mình.
- Lỗi theo mock: 403 không quyền; 422 "Ngày đã chọn không phải ngày học." / "Chỉ điểm danh được cho ngày hôm nay." / dữ liệu dòng (details theo childId); 423 "Điểm danh và báo ăn đã khóa sau {giờ}."; 409 mở lại trùng / chưa tới giờ khóa.
- Mở lại: hạn mở sau hiện tại, ≤ ngày kết thúc năm, không giới hạn số phút (Cần 21/10/2026); lý do ≤ 300 ký tự; mỗi lớp/ngày 1 lần mở còn hiệu lực; thu hồi = đặt hạn = bây giờ, giữ bản ghi.

## Thiết kế
- Bảng (V21): `attendance_records(id, child_id, classroom_id, campus_id, date, status, meals VARCHAR(40), recorded_by, recorded_at, …, UNIQUE(child_id, date))` (ATT-01b); `attendance_record_history(record_id, at, user_id, action, note, unlock_id)`; `attendance_day_unlocks(id, classroom_id, campus_id, date, valid_until, reason, unlocked_by, unlocked_at, revoked_at, revoked_by)`.
- Package `attendance` (controller/service/repository/dto) theo guide; gọi `child` (trẻ đang học lớp ngày đó), `organization` (lớp, nhóm tuổi, giờ chốt, phân công, `SchoolYearService#checkClassYearOpen`).
- Sau khi lưu có thay đổi: phát `AttendanceChanged(date, campusId, classId)` (ADR-0004) — đợt 2 nutrition dùng để đánh dấu số suất cần xác nhận lại (ATT-07).
- FE: `attendanceApi` theo path trên; cầu nối dev như Cấu hình trường (đăng nhập demo).

## Câu hỏi mở
- [x] (Tạm theo mặc định, Cần "làm tiếp" 23/10) Lịch nghỉ lễ/ngày nghỉ của trường: tạm chỉ thứ 2–6 như mock, có cần màn cấu hình ngày nghỉ không?
- [x] (Tạm theo mặc định, Cần "làm tiếp" 23/10) Đơn nghỉ của phụ huynh (tự ghi "vắng có phép"): làm ở đợt sau cùng module đơn nghỉ — đồng ý?
- [x] (Tạm theo mặc định, Cần "làm tiếp" 23/10) Thông báo (GV khi được mở lại ngày, PHT khi đổi số liệu): BE chưa có module thông báo ⇒ đợt 1 chưa gửi thông báo — đồng ý?

## Acceptance criteria
- **AC-1** (ATT-01, AUTH-03): GV lớp lưu điểm danh hôm nay trước giờ chốt ⇒ 200; GV lớp khác ⇒ 403.
- **AC-2** (ATT-01b): lưu 2 lần cùng trẻ/ngày ⇒ 1 bản ghi, lịch sử UPDATED.
- **AC-3** (ATT-06): sau giờ chốt, không có mở lại ⇒ 423; có mở lại còn hạn ⇒ lưu được, lịch sử ghi unlockId.
- **AC-4**: trẻ vắng có đăng ký ăn ⇒ 422 với details theo childId.
- **AC-5** (CFG-YR-01): lớp năm đã kết thúc ⇒ không ghi được.
- **AC-6** (AUTH-02): PHT mở lại ngày cho lớp điểm trường khác ⇒ 403; mở trùng ⇒ 409.
- **AC-7**: tổng hợp lớp/khoảng ngày đếm đúng có mặt/vắng/chưa điểm danh và số trẻ ăn.

## Phases
1. Migration V21 + entity/repository + day-info + xem/lưu điểm danh (AC-1..5).
2. Mở lại ngày (AC-6) + tổng hợp (AC-7).
3. Nối FE (`attendanceApi`, cầu nối dev), thử trên trình duyệt, Postman folder "Điểm danh".

## Test matrix
| AC | Test |
| --- | --- |
| AC-1..5 | `AttendanceServiceIntegrationTest` (Testcontainers), `AttendanceControllerTest` |
| AC-6 | `AttendanceUnlockIntegrationTest` |
| AC-7 | `AttendanceSummaryIntegrationTest` |

## Rủi ro & rollback
| Rủi ro | Giảm thiểu | Rollback |
| --- | --- | --- |
| Lệch shape với FE | Response bám mock, Postman example | Sửa DTO trước khi FE nối |
| Giờ máy chủ khác giờ trường | Dùng `Clock` múi Asia/Ho_Chi_Minh | — |

## Validation log
- 2026-10-23: đặc tả hành vi rút từ mock FE (attendanceMockRepository.js: day-info 427-445, lưu 652-737, mở lại 539-612, tổng hợp 804-849).

## Progress log
- 2026-10-23: draft, chờ Cần duyệt + trả lời câu hỏi mở.
- 2026-10-23: Cần bảo "làm tiếp" ⇒ làm với mặc định của 3 câu hỏi mở (thứ 2–6; đơn nghỉ đợt sau; chưa thông báo). Lỗi dùng mã chung 400/403/404/409 (không thêm 422/423 vào ApiCode). Phase 1–2 BE xong: V21, `AttendanceService`, `AttendanceDayUnlockService`, `AttendanceController`; `AttendanceIntegrationTest` (6), `AttendanceControllerTest` (4). Còn phase 3 nối FE.
- 2026-10-23: phase 3 nối FE xong (cầu nối dev); thử trình duyệt: GV demo lưu 2 có mặt + 1 vắng vào DB thật. Cần hỏi sức khỏe trong ngày ⇒ thêm V22 `daily_health_entries`, `DailyHealthService` (ghi nhận, xem theo lớp/trẻ, tổng hợp BGH); chưa gửi thông báo phụ huynh.
