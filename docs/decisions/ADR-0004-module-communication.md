# ADR-0004 — Giao tiếp giữa feature

- Status: ACCEPTED (gọi service) · PROPOSED (Spring event cho phản ứng ngược chiều)
- Date: 2026-10-03

## Bối cảnh
Guide cho phép service gọi service khác, yêu cầu tránh vòng phụ thuộc. Một số luồng cần phản ứng ngược chiều: điểm danh đổi sau khi suất đã xác nhận ⇒ suất cần đánh dấu thay đổi + thông báo, trong khi số suất lại đọc từ điểm danh.

## Quyết định
- Đọc/điều phối: service gọi service theo chiều trong `docs/system/MODULE_MAP.md`; không inject repository của feature khác.
- Phản ứng ngược chiều hoặc tác vụ phụ (thông báo, audit): Spring application event, listener sau commit (`@TransactionalEventListener(AFTER_COMMIT)`), idempotent — PROPOSED, chốt khi implement luồng đầu tiên cần nó.
- Không message broker (guide mục 2).

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Service call + Spring event | Đơn giản, không hạ tầng mới, phá vòng | Event có thể mất khi crash sau commit |
| Chỉ service call | Đơn giản nhất | Tạo vòng attendance ↔ meals |
| Message broker / outbox | Bền vững | Hạ tầng thừa cho V1 |

## Hệ quả
Nếu event quan trọng bị mất, số suất vẫn có thể đối soát lại từ điểm danh (tính lại khi mở màn hình xác nhận).
