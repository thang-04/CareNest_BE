# ADR-0017 — Module education-plan: CareNest soạn và duyệt kế hoạch giáo dục

- Status: ACCEPTED
- Date: 2026-10-22
- Liên quan: EDU-01..09, OBS-08 (SUPERSEDED), ADR-0006, ADR-0003, module education-plan, school-structure

## Bối cảnh
OBS-08 (team chốt 02/10) để GoKids giữ phần soạn và duyệt giáo án. Sau đó team đã làm xong UI kế hoạch giáo dục theo mẫu MN Thái Dương ở CareNest_FE (nhánh G94-178). UI gồm mục tiêu năm học, chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày, và nối với đánh giá trẻ theo MT. Ngày 22/10/2026, Tiền chốt đưa phần này vào V1, lấy UI làm chuẩn.

## Quyết định
- Tạo module mới **education-plan** (feature package `eduplan`), sở hữu các bảng `edu_*` của kế hoạch giáo dục.
- Không gộp vào learning-observation, vì card của module đó cấm mở rộng thành quản lý giáo án.
- Phụ thuộc: education-plan → school-structure (năm học, nhóm tuổi, lớp, phân công).
- Người dùng được tham chiếu bằng `user_accounts.id`.
- Luồng duyệt do BE quyết định qua endpoint hành động (`/submit`, `/issue`, `/return`). Client không tự đặt status.
- Quyền dùng mã permission `goals.*` (EDU-08), tạm gán theo role tại một chỗ cho tới khi có phân quyền động. Scope Tổ trưởng lấy từ phân công `AGE_GROUP_TEAM_LEADER` theo nhóm tuổi.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Module mới education-plan | Ranh giới rõ, không làm phình learning-observation | Thêm 1 module |
| Gộp vào learning-observation | Ít module | Trái pitfall của card; trộn hai vòng đời dữ liệu khác nhau |
| Giữ GoKids (OBS-08) | Không phải làm | Trái quyết định 22/10; UI đã có sẵn |

## Hệ quả
- Phải bỏ "soạn/duyệt giáo án" khỏi Exclusions, ROUTER, README và guide.
- learning-observation đọc MT, hoạt động từ education-plan, không chiều ngược lại.
- Chưa ký ảnh trong hệ thống: xuất và in ở FE, ký tay trên giấy.

## Còn chờ
- Chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày (các task sau).
- Phân quyền động ở BE (thay phần gán role tạm của EDU-08).
