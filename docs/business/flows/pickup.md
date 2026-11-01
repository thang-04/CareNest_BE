# Flow — Đón trẻ, xác nhận trả trẻ

Module: attendance (→ notification). Rule: PCK-01..04, ATT-10, PAR-01. `[B]` = ngoài hệ thống, `[S]` = hệ thống.

## Trả trẻ cuối ngày hoặc về sớm

```text
[B] Người đến đón (cuối ngày, đón muộn, hoặc trẻ về sớm vì ốm…)
[S] GV mở màn đón trẻ của lớp, chọn trẻ ⇒ hiện danh sách phụ huynh/người giám hộ của trẻ   (PCK-02)
      ├─ đúng người trong danh sách ⇒ GV xác nhận đón
      └─ người lạ ⇒ GV bấm gọi phụ huynh để xác nhận ⇒ ghi lý do ⇒ xác nhận đón
[S] Lưu người đón, giờ đón thực tế; về sớm ghi "về sớm"                                    (PCK-04)
    Đây là bước xác nhận trả trẻ (ATT-10)
      ↓ event after-commit
[S] Phụ huynh nhận thông báo con đã được đón; xem "ai đã đón con"                          (PCK-04, PAR-01)
```

## Không có

- Phụ huynh đăng ký trước người đón hộ trên App: không làm; phụ huynh báo GV qua gọi/nhắn (PCK-03).

## Còn mở

Đối chiếu ảnh người đón: lấy ảnh từ đâu khi không đăng ký trước (P-33).
