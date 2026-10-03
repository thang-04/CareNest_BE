# Flow — Điểm danh & báo ăn (FE-01)

Module: attendance → nutrition. Rule: ATT-01..07, NUT-01..04. `[B]` = ngoài hệ thống, `[S]` = hệ thống.

## Luồng chính

```text
[B] Trẻ đến lớp
[S] GV mở lớp/ngày → danh sách trẻ đang enrollment (child)
[S] GV nhập AttendanceRecord + MealParticipation cho từng trẻ (ATT-01, ATT-02)
      ↓ (cut-off: PENDING P-03 — cấu hình)
[S] nutrition tổng hợp MealParticipation theo campus/ngày/bữa → MealCount DRAFT (NUT-01)
[S] Người có quyền xem tổng hợp theo lớp → xác nhận → MealCount CONFIRMED (NUT-02; ai: PENDING P-05)
[S] Bếp xem suất đã chốt
[B] Bếp nấu
```

## Trẻ đến muộn (ATT-03, ATT-07, NUT-03, NUT-04)

```text
[S] GV cập nhật AttendanceRecord/MealParticipation
      ├─ MealCount còn DRAFT  → tổng hợp lại bình thường
      └─ MealCount đã CONFIRMED
            → attendance phát event AttendanceChanged (không biết trạng thái MealCount)
            → meals đánh dấu số suất đã thay đổi, lưu lịch sử, yêu cầu BGH xác nhận lại (không sửa ngầm)
            → xử lý tiếp (báo bếp? thêm suất?): PENDING P-04
```

## Lưu ý cho implementation

- Đơn vị nhập: lớp/ngày, nhập hàng loạt (20–25 trẻ) — API nên hỗ trợ batch.
- Attendance **không** import/gọi nutrition. Nutrition đọc attendance qua service `attendance`.
- Dữ liệu dẫn xuất: xem `docs/knowledge/CROSS_MODULE_ISSUES.md` (rủi ro stale meal count).
