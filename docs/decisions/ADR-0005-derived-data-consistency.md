# ADR-0005 — Nhất quán số suất ăn sau khi xác nhận

- Status: ACCEPTED (nguyên tắc, guide 13.1) · chi tiết thông báo/thêm suất PENDING P-04
- Date: 2026-10-03
- Liên quan: ATT-03, ATT-07, NUT-01..04, CMR-01

## Bối cảnh
Số suất ăn dẫn xuất từ điểm danh + đăng ký ăn. Sau khi BGH xác nhận và bếp nhận số, trẻ đến muộn/nghỉ có thể làm đổi số. Bếp cần số ổn định và biết khi có thay đổi.

## Quyết định (theo guide 13.1)
- Số suất = trẻ **có mặt** và **có đăng ký ăn**; không tính từ sĩ số.
- Bếp chỉ thấy số **đã xác nhận**; chưa xác nhận trả trạng thái chờ, không trả như số cuối.
- Thay đổi sau khi BGH xác nhận: **không sửa ngầm** bản đã xác nhận; lưu lịch sử thay đổi, đánh dấu số liệu đã thay đổi, **yêu cầu xác nhận lại**, thông báo vai trò cần xử lý.
- PENDING P-04: giờ chốt, bếp xử lý thay đổi muộn thế nào (thêm/bớt suất thực tế), ai nhận thông báo.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Lịch sử + xác nhận lại (chọn) | Có dấu vết, bếp không bị đổi số bất ngờ | Thêm bước xác nhận |
| Recompute ngầm | Đơn giản | Bếp thấy số thay đổi không báo; mất dấu vết |
| Khóa tuyệt đối sau xác nhận | Đơn giản | Không phản ánh trẻ đến muộn |

## Hệ quả
Test bắt buộc kịch bản trẻ đến muộn sau xác nhận. Báo cáo suất ăn dùng bản xác nhận cuối + lịch sử.
