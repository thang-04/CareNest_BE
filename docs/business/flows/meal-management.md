# Flow — Thực đơn, dinh dưỡng, định lượng, kho, bếp

Module: nutrition (+ ai-assistance, child cho dị ứng, attendance cho suất). Rule: NUT-*, AI-*. ADR: 0005, 0007, 0008, 0009.

## Lập và công bố thực đơn (NUT-08, NUT-09, NUT-13, NUT-18..20, NUT-23)

```text
Food master + NutrientValue (nguồn Viện Dinh dưỡng — NUT-05, NUT-18)
      ↓
Dish = nguyên liệu + gam/trẻ theo nhóm tuổi; nguyên liệu Fresh | Stored (NUT-10, NUT-18)
      ↓
[S] PHT phụ trách dinh dưỡng lập thực đơn chung hai cơ sở:
    nhà trẻ khác mẫu giáo, bộ 4 tuần xoay vòng, có bộ theo mùa                        (NUT-13)
[S] Kiểm tra xác định:
    cân đối theo nhu cầu của Bộ — chưa đạt ⇒ chặn công bố                           (NUT-08, NUT-18)
    giá vượt giá suất/ngày ⇒ cảnh báo                                                 (NUT-19)
    món lặp trong tuần ⇒ cảnh báo                                                     (NUT-20)
    dị ứng                                                                            (NUT-09)
[S] Món có chất gây dị ứng mức nặng: PHT chọn món thay thế, hệ thống gợi ý            (NUT-23)
      ↓
[S] PHT công bố — công bố là duyệt, không cần HT; đã công bố thì không sửa            (NUT-08)
      ├─ trừ kho khô dự kiến = gam/trẻ × sĩ số dự kiến nhóm tuổi từng cơ sở           (NUT-07, NUT-11)
      └─ bếp nhận thực đơn; phụ huynh xem thực đơn tuần/ngày                          (PAR-01)
```

## Hằng ngày

```text
[S] Số suất đã xác nhận (flow attendance.md) × định lượng → lượng cần dùng theo cơ sở, Fresh/Stored (NUT-07)
[S] Điều chỉnh kho khô theo chênh lệch suất đã xác nhận và sĩ số dự kiến            (NUT-11)
[B] Sau giờ chốt PHT báo NCC tăng/giảm hàng tươi                                      (NUT-12)
[B] Bếp kiểm hàng tươi, báo PHT
[S] PHT ghi đủ/thiếu/thừa: lần 1 so thực đơn dự kiến, lần 2 so thực đơn đã công bố    (NUT-12)
[S] Bếp (App) xem thực đơn, số suất theo lớp từng bữa, định lượng,
    số suất dị ứng/thay thế theo lớp — không có tên trẻ                               (AUTH-05, NUT-17, NUT-23)
[B] Bếp nấu, chia suất theo danh sách lớp
    (không theo dõi trạng thái nấu, không ghi bàn giao — NUT-14, NUT-15 REMOVED)
[S] Bếp chụp ảnh mẫu mỗi bữa, ghi sổ lưu mẫu trên App                                 (NUT-16, NUT-21)
[B] Thiếu nguyên liệu, thiếu/thừa suất: báo PHT qua Zalo, điện thoại                  (NUT-22)
```

## Kho khô (ADR-0009)

```text
[S] PHT nhập phiếu nhận trên Web: lô, hạn dùng, giá, NCC (danh mục NCC chung — NUT-12) (NUT-11)
[S] Trừ dự kiến khi công bố thực đơn, điều chỉnh theo suất đã xác nhận mỗi ngày        (NUT-11)
[S] Xem tồn theo cơ sở. Không chuyển liên cơ sở, không kiểm kê; bếp không dùng Web
```

Còn mở: chọn lô khi trừ và kho không đủ lúc công bố (P-25); sĩ số dự kiến lấy trước bao lâu (P-24); mẫu sổ lưu mẫu (P-26); kiểm thực ba bước (P-27).

## AI gợi ý thực đơn (tùy chọn, không bắt buộc)

Phạm vi AI trong Capstone chờ khảo sát mảng AI (bộ khảo sát team, AI-01..AI-09).

```text
Input tối thiểu: food/nutrition data, nhóm tuổi, thực đơn gần đây, mùa, khẩu vị vùng,
thời tiết, dị ứng/lưu ý sức khỏe tạm thời DẠNG TỔNG HỢP (không tên trẻ — AI-04)
      ↓ ai-assistance (port AiClient)
Thực đơn nháp (source=AI) → kiểm tra xác định như trên (NUT-09)
      ↓
PHT review/sửa → công bố (NUT-08)
AI tắt/lỗi → lập thực đơn thủ công như bình thường (AI-03)
```
