---
title: Sửa năm học và lịch sử chuyển lớp
status: done
owner: G94 team
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [attendance, nutrition, learning-observation]
rules: [ATT-06, OBS-29, NUT-03]
risk: high
created: 2026-11-22
updated: 2026-11-22
---
# Sửa năm học và lịch sử chuyển lớp (2026-11-22)

User đã yêu cầu triển khai các lỗi review, giữ lớp ghép ngoài phạm vi.

- Sổ suất ăn: kiểm cả hai biên năm học; đọc giờ chốt và lớp đúng năm đang chọn.
- Nhận xét tuần: giữ bản của từng lớp bằng khóa trẻ/lớp/tuần; lớp cũ chỉ xem sau chuyển/nghỉ,
  lớp hiện tại tạo bản riêng, không sửa hoặc đọc nháp lớp cũ.
- Cuối chủ đề: thêm nhãn lịch sử cho trẻ đã rời lớp, giữ nguyên kết quả đã chốt.
- Migration mới, giữ nguyên dữ liệu và migration cũ; kiểm bằng integration PostgreSQL và test FE liên quan.

## Hoàn tất
- PostgreSQL integration: sổ ăn 21/21, nhận xét tuần 22/22, cuối chủ đề 18/18, dữ liệu chuyển lớp 4/4.
- FE lint, 88 test dashboard và build đạt. Không chạy UI production hoặc migrate DB local của người dùng.
- Migration V168 đã kiểm trên DB container mới; cần khởi động lại BE để áp dụng vào DB local.
- Chưa commit/push.
