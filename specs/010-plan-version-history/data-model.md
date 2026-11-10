# Data model: Lịch sử phiên bản

**Không đổi DB** (Tiền chọn 1b, 03/11/2026). Dùng lại:
- Bản đã lưu hiện tại: dữ liệu tài liệu BE trả về sẵn (kế hoạch năm, `edu_class_docs`).
- Mốc chưa lưu: localStorage của trình duyệt, riêng theo tài khoản.

## Mốc chưa lưu (trình duyệt)
| Trường | Ý nghĩa |
|---|---|
| session | id lần soạn (tài liệu lớp: mỗi lần nạp/lưu; kế hoạch năm: slot của tab) |
| doc | toàn bộ nội dung đang soạn |
| base / baseRevision | mốc phiên bản bản trên hệ thống lúc giữ |
| startedAt, savedAt | lúc bắt đầu lần soạn, lần ghi gần nhất (ms) |

- Tài liệu lớp: khóa `carenest:plan-backup:<userId>:<docKey>` ⇒ `{ entries: [mốc, ≤2] }`; bản cũ dạng `{ doc, base, savedAt }` được đọc như 1 mốc.
- Kế hoạch năm: khóa `carenest:year-plan-recovery:<userId>:<planKey>:<slot>` ⇒ 1 mốc/khóa; giữ ≤2 khóa mỗi kế hoạch (xóa khóa cũ nhất).
- Lifecycle: xem domain-spec mục 9. Dữ liệu không rời máy; không chứa dữ liệu trẻ.
