# ADR-0003 — Mô hình quyền: Role × Permission ∩ Access scope

- Status: ACCEPTED (chi tiết phạm vi HP: PENDING P-14)
- Date: 2026-10-03

## Bối cảnh
Cần phân biệt "được làm gì" (permission) và "trên dữ liệu nào" (campus/lớp/trẻ). HP có thể đổi phân công; GV đổi lớp theo năm học.

## Quyết định
- Permission gán theo role (cấu hình được).
- Scope từ StaffAssignment (SCHOOL/CAMPUS/CLASS, có hiệu lực từ–đến) và GuardianChildLink (CHILD).
- Kiểm tra scope tập trung (tên minh họa guide: `AccessScopeService`, ở `security/`/service), đọc assignment + liên kết phụ huynh; không gọi vòng.
- Kiểm tra ở service/security, đối chiếu assignment, không tin id client gửi (guide mục 9); UI chỉ phản ánh.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Role cứng gắn campus (VP_CAMPUS_A) | Đơn giản | Đổi phân công phải đổi role; không có lịch sử |
| Assignment có hiệu lực | Linh hoạt, có lịch sử | Phải lọc theo ngày |

## Hệ quả
Mọi API đọc dữ liệu trẻ/lớp cần test scope. Chống IDOR khi truy cập theo ID.
