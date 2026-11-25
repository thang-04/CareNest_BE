---
id: BUG-261123-former-teacher-read-interval
type: bug
modules: [attendance, observation]
rules: [AUTH-03]
status: fixed
date: 2026-11-23
keywords: [BUG-006, hết phân công, năm đang học, lịch sử, khoảng phân công]
---

# Quyền đọc lớp cũ chưa theo đúng ngày phân công

## Triệu chứng và mong đợi

AUTH-03 (chốt15/11) cho GV từng dạy đọc dữ liệu đúng khoảng phân công, không ghi khi đã hết phân công. Hiện năm ACTIVE chặn GV dạy chính cũ dù đang xem đúng ngày từng dạy; năm CLOSED lại cho đọc mọi ngày nếu từng có phân công.

## Tái hiện kiểm tra rule

Audit chạy bytecode hiện tại, Clock23/11/2026, lớp/GV giả và mock dependencies; không gọi API/không sửa DB. AttendanceDayRules và ObservationDayRules đều có hai kết quả:

- Lớp ACTIVE, phân công ngày15/10 hợp lệ nhưng hôm nay hết hiệu lực: expected canViewDay=true, actual=false.
- Lớp CLOSED, người dùng từng dạy nhưng ngày02/09/2025 ngoài phân công: expected=false, actual=true.

Log ngoài Git: logs/former-teacher-read-audit.log, source kiểm tra logs/FormerTeacherReadAudit.java. Đây là kiểm chứng guard cô lập, không phải full UI/HTTP integration pass.

## Root cause và phạm vi

AttendanceDayRules.canView/ObservationDayRules.canView dựa vào ClassroomAccessPolicy đang kiểm phân công hôm nay; ngoại lệ lịch sử chỉ năm kết thúc. canViewDay chỉ thêm dạy thay; không thêm GV dạy chính cũ đúng ngày yêu cầu. getClasses cũng chỉ thêm lịch sử dạy thay nên GV thường không chọn được lớp cũ trong năm ACTIVE.

Ngược lại taughtClosedYear dùng hasTeacherAssignmentHistory(...today), chỉ chứng minh từng dạy; canViewDay trảtrue từ canView trước khi kiểm ngày xem. Tổng hợp điểm danh, ngày sức khỏe và đánh giá ngày dùng các guard này. Nhận xét tuần/phiếu có chỗ kiểm cấp lớp nên cần rà cả khoảng kỳ khi sửa.

Lỗi lộ khi phân công hết hiệu lực hoặc xem lại năm đã đóng, không lộ khi chỉ thử GV đang dạy lớp hiện tại. Chưa có chứng cứ dữ liệu DB bị ghi sai trong audit này; đây là thiếu/chênh phạm vi quyền đọc.

## Hướng sửa đề xuất

Tách quyền hiện lớp có lịch sử khỏi quyền đọc ngày/khoảng; kiểm ngày yêu cầu bằng lịch phân công. Giữ quyền ghi và giới hạn Tổng quan lớp theo AUTH-23 (GV thường không được vào Tổng quan là chủ đích). Tổng hợp cần cắt về khoảng có quyền hoặc từ chối theo quy tắc chốt, không cấp cả năm chỉ vì từng dạy.

## Attempts

1. Review độc lập đọc source chỉ ra hai đường ngược nhau.
2. Audit Mockito ngoài source/test của repo tái hiện cả hai guard với bốn kết quả sai. Chưa sửa source nghiệp vụ trong lượt rà này.

## Sửa và kiểm chứng

Guard theo ngày/khoảng dùng TeacherReadAccessService; class discovery có metadata lịch sử. Reader theo kỳ kiểm range trước snapshot. Hồ sơ phát triển có nhánh giao phân công; UI giữ bộ lọc khi kỳ ngoài quyền. Các ca ACTIVE/CLOSED, phạm vi campus, ASSESSMENT độc lập ATTENDANCE đã có regression. Chi tiết docs/quality/2026-11-23-history-continuity-verification.md. Còn kiểm UI thêm và tích hợp dev sau checkpoint.
