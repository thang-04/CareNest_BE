# ADR-0002 — Phân vùng dữ liệu theo campus (không multi-tenant)

- Status: ACCEPTED
- Date: 2026-10-03
- Liên quan: AUTH-01..08, P-06, P-14

## Bối cảnh
1 trường, 2 campus sau sáp nhập. HT xem toàn trường, HP chủ yếu 1 campus, báo cáo theo lớp/campus/trường. Không phải nhiều trường.

## Quyết định
Shared database, shared schema. Dữ liệu campus-scoped có cột `campus_id`; dữ liệu theo trẻ suy campus qua Enrollment tại ngày. Lọc theo access scope ở application layer. Không `school_id`/tenant trên mọi bảng.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Shared schema + campus_id | Đơn giản, báo cáo toàn trường dễ | Phải kỷ luật lọc scope |
| Schema/DB per campus | Cô lập mạnh | Báo cáo toàn trường khó, thừa cho 2 campus |
| Multi-tenant SaaS | Mở rộng nhiều trường | Ngoài scope, phức tạp |

## Hệ quả
Test bắt buộc chống truy cập chéo campus. Báo cáo dùng campus tại ngày dữ liệu.
