# ADR-0020 — Module education-plan: CareNest soạn và duyệt kế hoạch giáo dục

- Status: ACCEPTED
- Date: 2026-10-22, cập nhật 2026-10-24
- Liên quan: EDU-01..22, OBS-07, ADR-0006, ADR-0003, module education-plan, school-structure, learning-observation
- Số cũ: ADR-0017 trên nhánh G94-226; đổi thành ADR-0020 khi merge `dev` (ADR-0017 của `dev` là phân quyền động). Phân quyền động đã có trên `dev` (ADR-0017, AUTH-10, AUTH-23); code eduplan chưa chuyển sang, vẫn gán role tạm theo EDU-08 (việc còn lại: EDU-38).

## Bối cảnh
Trường MN Thượng Hồng lập kế hoạch giáo dục theo mẫu MN Thái Dương: kế hoạch năm theo nhóm tuổi, rồi mỗi lớp lập chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày; đánh giá trẻ dựa trên mục tiêu của giáo án. Team đã làm xong UI toàn bộ luồng này ở CareNest_FE (nhánh G94-178). Ngày 22/10/2026 Tiền chốt đưa phần này vào V1, lấy UI làm chuẩn.

## Quyết định
- Module **education-plan** (feature package `eduplan`) sở hữu mọi dữ liệu kế hoạch giáo dục: kế hoạch năm, cấu hình, chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày (bảng `edu_*`).
- Không gộp vào learning-observation: hai vòng đời dữ liệu khác nhau; learning-observation chỉ đọc giáo án đã duyệt để chấm theo MT (ADR-0006).
- Phụ thuộc: education-plan → school-structure (năm học, nhóm tuổi, lớp, phân công), identity-access (tên người dùng). Người dùng tham chiếu bằng `user_accounts.id`.
- Trạng thái do BE quyết định qua endpoint hành động (`/submit`, `/issue`, `/return`, `/start-editing`, `/finish-editing`; tài liệu lớp: hành động gửi, duyệt, trả lại). Client không tự đặt status.
- Quyền dùng mã permission `goals.*` (EDU-08), tạm gán theo role tại một chỗ (`EduPlanAccessPolicy`) cho tới khi có phân quyền động. Scope Tổ trưởng lấy từ phân công `AGE_GROUP_TEAM_LEADER` theo nhóm tuổi.
- Không ký ảnh trong hệ thống; xuất Word / in PDF ở FE, ký tay trên giấy (EDU-21).

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Module mới education-plan | Ranh giới rõ, không làm phình learning-observation | Thêm 1 module |
| Gộp vào learning-observation | Ít module | Trộn hai vòng đời dữ liệu khác nhau |
| Để GoKids giữ soạn, duyệt giáo án | Không phải làm | Trái quyết định 22/10; mất ngữ cảnh MT cho đánh giá |

## Hệ quả
- learning-observation đọc MT, hoạt động của giáo án đã duyệt từ education-plan, không chiều ngược lại.
- Kế hoạch năm đã ban hành bị khóa vì lớp dùng chủ đề, đợt nghỉ và số MT của nó.

## Còn chờ
- BE cho cấu hình kế hoạch, chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày, AI gợi ý (UI đã có, đang chạy mock).
- Phân quyền động ở BE (thay phần gán role tạm của EDU-08).
