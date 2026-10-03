# ADR-0006 — Nguồn ngữ cảnh hoạt động/học tập

- Status: **OPEN** — chờ khảo sát P-01
- Date: 2026-10-03
- Liên quan: OBS-07, OBS-08, module learning-observation

## Bối cảnh
GoKids giữ soạn/nộp/duyệt giáo án (CONFIRMED). Feedback review 24/09: nếu đánh giá/AI không biết hoạt động dự kiến, nội dung đã dạy, mục tiêu ⇒ phân tích thiếu ngữ cảnh. CareNest **không** clone GoKids.

## Phương án
| Phương án | Mô tả | Ưu | Nhược |
| --- | --- | --- | --- |
| A | CareNest quản lý tối thiểu lịch hoạt động tuần, mô tả, nhóm tuổi, mục tiêu | Dữ liệu cấu trúc tốt | Gần với chức năng GoKids; nhập trùng |
| B | Import/sync từ GoKids | Không nhập trùng | Phụ thuộc GoKids có API/export (chưa biết) |
| C | GV/admin nhập metadata cần thiết khi cần | Đơn giản, độc lập | Phụ thuộc kỷ luật nhập; dữ liệu thưa |

## Hướng thiết kế an toàn trong khi OPEN
- Entity `ActivityContext` không phụ thuộc nguồn: `source = MANUAL | IMPORT | GOKIDS`, trường tối thiểu (lớp/nhóm tuổi, ngày/tuần, mô tả, mục tiêu).
- Không xây chức năng soạn/duyệt giáo án dưới bất kỳ phương án nào.
- Đề xuất (chưa quyết): bắt đầu C, chuyển B nếu GoKids có export. Chỉ chốt sau P-01.

## Còn chờ
P-01: GoKids có API/export không; trường ghi mục tiêu theo độ tuổi ở đâu.
