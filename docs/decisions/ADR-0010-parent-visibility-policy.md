# ADR-0010 — Chính sách hiển thị cho phụ huynh

- Status: ACCEPTED (nguyên tắc) · field cụ thể PENDING P-13b
- Date: 2026-10-03
- Liên quan: PAR-01..04, AUTH-04

## Bối cảnh
Phụ huynh xem thông tin con: điểm danh, bữa ăn, sức khỏe, hoạt động, cập nhật phát triển — nhưng chỉ phần nhà trường cho phép. Chưa biết field cụ thể.

## Quyết định
- Default-deny: dữ liệu chỉ hiện cho phụ huynh khi thuộc loại được policy cho phép **và** (với dữ liệu có duyệt) đã APPROVED/được công bố.
- Policy cấu hình theo loại dữ liệu (không hard-code field trong client).
- API phụ huynh là endpoint/DTO riêng, chỉ chứa field được phép.
- Không có chat (Zalo vẫn dùng cho trao đổi tức thời).

## Hệ quả
Thêm dữ liệu mới hiển thị cho phụ huynh ⇒ cập nhật policy + DTO phụ huynh + `CareNest_APP/docs/features/parent/`.
