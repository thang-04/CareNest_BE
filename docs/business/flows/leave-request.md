# Flow — Báo nghỉ (đơn xin nghỉ)

Module: attendance (→ nutrition qua event). Rule: ATT-02, ATT-04, ATT-05, ATT-05b, ATT-09. Chốt 02/10/2026: **thông báo, không duyệt** (P-15 đã đóng). Khảo sát team 27/10: GV không tạo đơn thay phụ huynh.

## Luồng

```text
[B] Phụ huynh quyết định cho trẻ nghỉ
[S] Phụ huynh gửi LeaveRequest trên App (trẻ, từ ngày, đến ngày, lý do) → SUBMITTED   (ATT-05)
[S] attendance ghi sẵn "Vắng có phép" cho các ngày đó                                 (ATT-04, ATT-09)
    Không có suất vì suất tính theo điểm danh                                          (ATT-02)
[S] GV/BGH trong scope thấy thông báo; không có bước duyệt
      ↓
    tiếp tục flow attendance.md
    Đơn gửi sau giờ chốt của một ngày: không đổi điểm danh, số suất ngày đó;
    áp từ ngày kế tiếp                                                                 (ATT-05b, PROPOSED)
```

## Trạng thái

`SUBMITTED → CANCELLED` (phụ huynh hủy). Không có APPROVED/REJECTED. Hủy ⇒ bỏ "Vắng có phép" cho các ngày chưa qua giờ chốt; ngày đã qua giờ chốt xử lý theo ATT-05b.

## Còn mở

Trẻ có đơn nghỉ vẫn đến lớp — GV sửa điểm danh, hay hệ thống khóa điểm danh trẻ đó tới khi phụ huynh hủy đơn: P-20.
