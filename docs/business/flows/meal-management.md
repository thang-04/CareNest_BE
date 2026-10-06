# Flow — Thực đơn, dinh dưỡng, định lượng, bếp

Module: nutrition (+ ai-assistance, child cho dị ứng, attendance cho suất). Rule: NUT-*, AI-*. ADR: 0005, 0007, 0008, 0009 (OPEN).

## Pipeline xác định

```text
Food master + NutrientValue (có source — NUT-05)
      ↓
Dish / Recipe (định lượng/suất; nguyên liệu Fresh | Stored — NUT-10)
      ↓
MealPlan DRAFT (ngày, bữa; phạm vi campus/nhóm tuổi: PENDING P-07)
      ↓ người có quyền duyệt (NUT-08)
MealPlan APPROVED → tính dinh dưỡng (NUT-06)
      ↓
MealCount CONFIRMED (flow attendance.md) × Recipe → FoodQuantityPlan (NUT-07)
      ↓
[S] Bếp xem kế hoạch định lượng theo Fresh / Stored
[B] Đặt/nhận hàng NCC, xuất kho khô — ngoài hệ thống cho tới khi chốt ADR-0009 (P-08, P-09)
      ↓
    Chuẩn bị bếp + bàn giao suất (mục dưới)
```

## AI gợi ý thực đơn (tùy chọn, không bắt buộc)

```text
Input tối thiểu: food/nutrition data, nhóm tuổi, thực đơn gần đây, mùa, khẩu vị vùng,
thời tiết, dị ứng/lưu ý sức khỏe tạm thời DẠNG TỔNG HỢP (không tên trẻ — AI-04)
      ↓ ai-assistance (port AiClient)
MealPlan DRAFT (source=AI) → kiểm tra xác định: món có trong master, dị ứng (NUT-09), dinh dưỡng
      ↓
Người có quyền review/sửa/duyệt → APPROVED
AI tắt/lỗi → lập MealPlan thủ công như bình thường (AI-03)
```

## Chuẩn bị bếp + bàn giao suất — PROPOSED (NUT-14..16, BP-BT-03)

```text
[S] Bếp xem: thực đơn đã duyệt, số suất CONFIRMED, định lượng, dị ứng cần lưu ý (theo campus)
[S] Bếp cập nhật: WAITING_TO_COOK → COOKING                                    (NUT-14)
[B] Bếp nấu / chuẩn bị
[S] Bếp cập nhật READY_FOR_HANDOVER; tùy chọn chụp ảnh món (NUT-16)
[S] Hệ thống hiển thị số suất cần chia cho từng lớp
[B] Bếp chia suất, mang tới lớp
[S] GV xác nhận số suất nhận                                                    (NUT-15)
      ├─ đúng số  → hệ thống ghi HANDED_OVER cho lớp
      └─ thiếu/sai → bếp bổ sung [B] → bàn giao lại → GV xác nhận lại
```

Còn mở: số suất của lớp lấy từ MealCount CONFIRMED (campus) chia theo MealParticipation của lớp; trẻ đến muộn sau chốt ⇒ P-04.

## Kho thực phẩm — OPEN (ADR-0009)

BP-BT-03 có luồng kho (kiểm tồn → nhập từ NCC → PHT duyệt xuất → bếp nhận → đối soát). Chưa chốt có làm hay không ⇒ không tự thêm entity kho/NCC; gặp yêu cầu kho ⇒ dừng, hỏi.
