# Flow — Báo nghỉ (đơn xin nghỉ)

Module: attendance (→ nutrition qua event). Rule: ATT-04, ATT-05, ATT-05b, ATT-07. Chốt 02/10/2026: **thông báo, không duyệt** (P-15 đã đóng).

## Luồng

```text
[B] Phụ huynh quyết định cho trẻ nghỉ
[S] Phụ huynh gửi LeaveRequest qua app (child, từ ngày, đến ngày, lý do) → SUBMITTED   (ATT-05)
[S] attendance đánh dấu vắng có phép + MealParticipation = không các ngày đó            (ATT-04)
[S] GV/BGH trong scope thấy thông báo (notification); không có bước duyệt
      ↓
    tiếp tục flow attendance.md
    Ngày đã qua cut-off hoặc số suất đã xác nhận ⇒ đánh dấu thay đổi, xác nhận lại      (ATT-05b, ATT-07, NUT-03)
```

## Trạng thái

`SUBMITTED → CANCELLED` (phụ huynh hủy). Không có APPROVED/REJECTED. Hủy ⇒ bỏ đánh dấu vắng có phép cho các ngày chưa diễn ra; ngày đã qua xử lý theo ATT-05b.

## Còn mở

Giờ cut-off: P-03. GV tạo thay phụ huynh: PROPOSED (ATT-05).
