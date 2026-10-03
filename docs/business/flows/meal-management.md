# Flow — Thực đơn, dinh dưỡng, định lượng, bếp

Module: nutrition (+ ai-assistance, child cho dị ứng, attendance cho suất). Rule: NUT-*, AI-*. ADR: 0005, 0007, 0008, 0009.

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
[B] Đặt/nhận hàng NCC, xuất kho khô, nấu — ngoài hệ thống V1 (P-08, P-09)
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

## Ngoài phạm vi V1

Tồn kho kho khô, nhập/xuất kho, quản lý NCC, giá hợp đồng, đổi/trả thực phẩm (ADR-0009). Không tự thêm entity cho các phần này.
