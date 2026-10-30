# ADR-0009 — Phạm vi kho/nhà cung cấp trong Nutrition

- Status: **ACCEPTED — phương án B mở rộng** (kho khô theo lô, trừ dự kiến khi công bố thực đơn + điều chỉnh theo suất chốt; kiểm nhận hàng tươi do PHT ghi; danh mục NCC; sổ lưu mẫu). Lịch sử: ACCEPTED "không kho trong V1" 03/10 → OPEN 04/10 → ACCEPTED B (bản gọn) 21/10 → thu hẹp 23/10 khi user giới hạn thiết kế mới khoảng 10–15 bảng → sửa 28/10 theo khảo sát team (user chốt).
- Date: 2026-10-04 (cập nhật 2026-10-28)
- Liên quan: NUT-07, NUT-10..13, NUT-16, NUT-21, NUT-22; P-08, P-09 đã đóng 28/10; còn P-24, P-25, P-26; thiết kế bảng `MEAL_NUTRITION_AI_SCHEMA.md` (file thiết kế của dev, chưa có trong repo) §5

## Bối cảnh

- Khảo sát: trường tự nấu. Thực phẩm tươi do NCC giao mỗi sáng, thừa thì trả lại. Đồ khô (gạo...) có kho, hết thì mua thêm. Giá NCC cố định theo hợp đồng theo kỳ hoặc năm.
- Review 24/09:
  - Thực đơn phải nối liền chọn món → dinh dưỡng → lượng mua.
  - PMS không tách dữ liệu theo điểm trường. Giảng viên gợi ý quản lý vật tư tập trung (một kho chung ở cơ sở chính) rồi xuất riêng cho từng điểm trường theo số suất của điểm đó.
  - Kiểm kê: số trên hệ thống phải khớp số thực tế; nếu lệch thì tìm nguyên nhân.
- BP-BT-03 (02/10): luồng kho đầy đủ nằm ngay trước bước chuẩn bị bếp: yêu cầu nguyên liệu → kiểm tồn → thiếu thì NCC giao, PHT kiểm và nhập → PHT duyệt xuất → bếp nhận → ghi tồn → đối soát.
- Roadmap: kho xếp **Should**, sẽ dời sang W12 nếu không kịp.
- Team 02/10: kho = **UNRESOLVED**.

## Phương án

| Phương án | Mô tả | Ưu | Nhược |
| --- | --- | --- | --- |
| A | Không kho; chỉ FoodQuantityPlan phân loại Fresh/Stored | Nhỏ, chắc làm xong | Không có chuỗi "xuất theo điểm trường" mà giảng viên nhấn mạnh |
| B | Kho tối thiểu: tồn kho khô, nhập, xuất theo điểm trường, đối soát có lý do lệch | Khớp BP-BT-03 và review | Thêm entity/màn hình; cần khảo sát sổ nhập-xuất-tồn (P-08) |
| C | B + NCC, giá hợp đồng, đổi/trả hàng tươi | Đầy đủ | Vượt sức team 5 người trong V1 |

## Quyết định

Bản 28/10 (khảo sát team 27/10: KIT-01..KIT-08, KIT-11, KIT-12, ORG-01; user chốt 2026-10-28). Thay các điểm của bản 21–23/10 được ghi ở cuối mục.
- Kho khô mỗi cơ sở để hàng riêng, PHT dinh dưỡng quản lý chung; theo lô (số lô, NSX, HSD, đơn giá, NCC). Không chuyển hàng liên cơ sở. **Không kiểm kê / đối soát.**
- Phiếu nhận DRY: PHT lập và xác nhận trên Web. Xác nhận xong, mỗi dòng thành một lô có tồn còn lại. Bếp không dùng Web.
- Trừ kho 2 bước (NUT-07, NUT-11):
  - PHT công bố thực đơn ⇒ hệ thống trừ dự kiến = gam/trẻ × sĩ số dự kiến của nhóm tuổi tại từng cơ sở.
  - Mỗi ngày số suất được xác nhận ⇒ điều chỉnh theo chênh lệch với sĩ số dự kiến.
  - Bếp nhận đúng lượng theo thực đơn, không nhận thừa/thiếu.
  - Còn chờ: chọn lô khi trừ (đề xuất hết hạn trước), kho không đủ lúc công bố (P-25); sĩ số dự kiến lấy trước bao lâu (P-24).
- Hàng tươi (NUT-12):
  - Đặt theo sĩ số dự kiến; sau giờ chốt PHT báo NCC tăng/giảm.
  - Bếp kiểm hàng rồi báo PHT. PHT ghi đủ/thiếu/thừa 2 lần: so thực đơn dự kiến, rồi so thực đơn đã công bố.
  - Không cộng kho.
- Danh mục NCC dùng chung hai cơ sở: tên, SĐT, địa chỉ, CCCD, người giao hàng.
- Sổ lưu mẫu thức ăn và ảnh mẫu mỗi bữa: bếp ghi trên App (NUT-16, NUT-21).
- Thiếu nguyên liệu: bếp báo PHT qua Zalo/điện thoại, hệ thống không ghi (NUT-22).
- **Ngoài phạm vi:** dự báo tuần gửi NCC, giá hợp đồng, kho chung liên cơ sở, kiểm kê, ghi nhận bàn giao suất, trạng thái nấu.

**Thay so với bản 21–23/10:**
- Bỏ phiếu xuất theo ngày (bếp chọn lô, thiếu hàng chặn cả phiếu); thay bằng trừ dự kiến + điều chỉnh.
- Kiểm nhận hàng tươi do PHT ghi, thay cho "bếp ghi, PHT duyệt".
- Thêm danh mục NCC, thay cho "chỉ ghi tên NCC dạng chữ".

## Hệ quả

- Bảng đề xuất ở `MEAL_NUTRITION_AI_SCHEMA.md` (file thiết kế của dev, chưa có trong repo) §5: `stock_receipts`, `stock_receipt_lines`, `stock_issues`, `food_samples`. Bản 28/10 cần thêm bảng NCC và đổi `stock_issues` sang bút toán trừ dự kiến + điều chỉnh (§13 của file thiết kế). Vượt giới hạn 15 bảng ⇒ báo user khi thiết kế lại.
- Tồn hiện tại = tổng tồn còn lại của các dòng DRY; không có bảng tồn riêng.
- FE có màn Nhà cung cấp: giữ được, chỉnh theo danh mục mới. Màn Dự báo tuần: vẫn không có bảng BE.
- FoodQuantityPlan vẫn tính khi đọc, tách theo campus và FRESH/DRY.

## Còn chờ

- P-24: sĩ số dự kiến lấy trước bao lâu (1 tuần hay 1 tháng).
- P-25: chọn lô khi trừ; kho không đủ lúc công bố.
- P-26: mẫu sổ lưu mẫu, giấy tờ bếp hằng ngày.
