# Flow — Điểm danh, sức khỏe đầu ngày & báo ăn (FE-01)

Module: attendance → nutrition (health nhận ghi nhận đầu ngày). Rule: ATT-01..11, HLT-07, NUT-01..04, NUT-17. `[B]` = ngoài hệ thống, `[S]` = hệ thống.

## Luồng chính

```text
[B] Phụ huynh đưa trẻ tới lớp trước giờ chốt (ATT-06)
[B] GV quan sát, đo nhiệt độ bằng nhiệt kế (HLT-07)
[S] GV mở lớp/ngày trên App → danh sách trẻ đang học (child);
    trẻ có đơn nghỉ đã hiện sẵn "Vắng có phép" (ATT-04, ATT-09)
[S] GV ghi Có mặt / Vắng + nhiệt độ, biểu hiện cho từng trẻ (ATT-01, ATT-09, HLT-07)
      ↓ giờ chốt do HT cấu hình, chung toàn trường (ATT-06): điểm danh khóa
[S] nutrition tính số suất = trẻ có mặt × các bữa của nhóm tuổi, theo lớp và cơ sở (ATT-02, NUT-01, NUT-17)
[S] PHT đang phân công tại cơ sở xem theo lớp → xác nhận → MealCount CONFIRMED (NUT-02)
      ├─ số sai → trả GV sửa qua mở lại điểm danh (ATT-08)
      └─ PHT vắng → HT xác nhận thay
[S] Bếp (App) xem số suất đã xác nhận theo lớp, từng bữa (AUTH-05, NUT-17)
[B] Bếp nấu, chia suất theo danh sách lớp (không ghi bàn giao — NUT-15 REMOVED)
[S] Cuối ngày GV xác nhận trả trẻ cho từng trẻ: người đón, giờ đón (ATT-10, PCK-04) — flow pickup.md
```

## Sau giờ chốt (ATT-03, ATT-07, ATT-08, NUT-03, NUT-04)

```text
[B] Trẻ đến sau giờ chốt → trường không nhận, phụ huynh đưa về (ATT-03)
[S] GV quên hoặc ghi sai → PHT mở lại điểm danh của lớp, bắt buộc lý do, lưu nhật ký (ATT-08)
[S] GV sửa AttendanceRecord
      ├─ MealCount chưa xác nhận → tính lại bình thường
      └─ MealCount đã CONFIRMED
            → attendance phát event AttendanceChanged (không biết trạng thái MealCount)
            → nutrition đánh dấu số suất đã thay đổi, lưu lịch sử, PHT xác nhận lại (không sửa ngầm)
            → bếp làm theo số xác nhận lại; kho khô điều chỉnh theo NUT-11
[B] Thiếu/thừa suất thực tế: bếp báo PHT qua Zalo, điện thoại (NUT-22)
```

## Còn mở

Ngày nghỉ tự khóa điểm danh: P-19. Trẻ có đơn nghỉ vẫn đến lớp: P-20. Triệu chứng chọn nhanh, trẻ bất thường có nhận không: P-21, P-22.

## Lưu ý cho implementation

- Đơn vị nhập: lớp/ngày, nhập hàng loạt (20–25 trẻ) — API nên hỗ trợ batch.
- Không có MealParticipation nhập riêng: suất suy từ điểm danh và cấu hình bữa của nhóm tuổi (ATT-02, NUT-17).
- Attendance **không** import/gọi nutrition. Nutrition đọc attendance qua service `attendance`.
- Dữ liệu dẫn xuất: xem `docs/knowledge/CROSS_MODULE_ISSUES.md` (rủi ro stale meal count).
