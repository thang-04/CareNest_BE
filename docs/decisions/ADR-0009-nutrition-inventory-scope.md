# ADR-0009 — Phạm vi kho/nhà cung cấp trong Nutrition

- Status: **ACCEPTED — phương án B mở rộng** (kho khô theo lô, trừ một lần khi công bố thực đơn chuẩn ngày theo suất đã xác nhận; bản dự trù và kiểm nhận hàng tươi do PHT ghi; danh mục NCC; sổ lưu mẫu có PHT duyệt). Lịch sử: ACCEPTED "không kho trong V1" 03/10 → OPEN 04/10 → ACCEPTED B (bản gọn) 21/10 → thu hẹp 23/10 khi user giới hạn thiết kế mới khoảng 10–15 bảng → sửa 28/10 theo khảo sát team (user chốt) → sửa 01/11 theo sơ đồ quy trình bán trú của team (user chốt). **Sửa 02/11 (user chốt):** công bố thực đơn cả tuần, bỏ công bố thực đơn chuẩn theo ngày ⇒ các ý "trừ một lần khi công bố thực đơn chuẩn ngày" bên dưới thay bằng trừ theo ngày khi suất cơ sở được xác nhận, thiếu kho không chặn (NUT-11, P-38 user duyệt 02/11).
- Date: 2026-10-04 (cập nhật 2026-11-01)
- Liên quan: NUT-07, NUT-08, NUT-10..13, NUT-16, NUT-21, NUT-22; P-08, P-09 đã đóng 28/10; P-24, P-25 đã đóng 01/11; còn P-26; thiết kế bảng `MEAL_NUTRITION_AI_SCHEMA.md` (file thiết kế của dev, chưa có trong repo) §5, §13, §14

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

Bản 01/11 (user chốt 2026-11-01 khi đối chiếu sơ đồ `Business process for day-boarding.drawio` của team, trang 1.–5.5). Nền là bản 28/10 (khảo sát team 27/10: KIT-01..KIT-08, KIT-11, KIT-12, ORG-01). Các điểm thay so với bản trước được ghi ở cuối mục.
- Kho khô mỗi cơ sở để hàng riêng, PHT dinh dưỡng quản lý chung; theo lô (số lô, NSX, HSD, đơn giá, NCC). Không chuyển hàng liên cơ sở. **Không kiểm kê / đối soát.**
- Phiếu nhận DRY: PHT chọn cơ sở, NCC, lập và xác nhận trên Web. Xác nhận xong, mỗi dòng thành một lô có tồn còn lại. Bếp không dùng Web.
- Trừ kho một lần (NUT-07, NUT-08, NUT-11):
  - Thực đơn dự kiến ngày: hệ thống cảnh báo thiếu kho, chưa trừ.
  - Số suất của cả hai cơ sở đã xác nhận ⇒ PHT công bố thực đơn chuẩn của ngày, một lần cho cả trường ⇒ hệ thống trừ kho từng cơ sở = định lượng × số suất đã xác nhận của cơ sở, lấy lô hết hạn trước.
  - Cơ sở nào không đủ hàng ⇒ chặn công bố, hiện phần thiếu; PHT nhập phiếu bổ sung rồi công bố lại.
  - Số suất xác nhận lại sau khi công bố ⇒ ghi một lần điều chỉnh theo chênh lệch, báo bếp. Suất tăng mà kho không đủ: không chặn xác nhận lại; PHT đặt NCC mua thêm (ngoài hệ thống), nhập bổ sung rồi trừ phần còn thiếu.
  - Bếp nhận đúng lượng theo thực đơn, không nhận thừa/thiếu.
  - Xem tồn theo lô và lịch sử nhập/trừ.
- Hàng tươi (NUT-07, NUT-12):
  - Bản dự trù: hệ thống điền sẵn sĩ số đang học theo nhóm tuổi × cơ sở, PHT sửa, chọn kỳ và thực đơn dự kiến; bản dự trù được lưu. PHT tra cứu, in danh sách đặt hàng, gửi NCC ngoài hệ thống; sau giờ chốt báo NCC tăng/giảm.
  - Bếp kiểm hàng rồi báo PHT. PHT ghi đủ/thiếu/thừa 2 lần: so thực đơn dự kiến (bản dự trù), rồi so thực đơn chuẩn đã công bố.
  - Không cộng kho.
- Danh mục NCC dùng chung hai cơ sở: tên, SĐT, địa chỉ, CCCD, người giao hàng.
- Sổ lưu mẫu (NUT-21): bếp ghi lưu mẫu và hủy mẫu (sau ≥24 giờ) trên App, PHT duyệt từng bản ghi; hệ thống tạo nhãn và biểu mẫu lưu/hủy, PHT xuất/in, ký giấy ngoài hệ thống.
- Ảnh mẫu bữa (NUT-16): theo từng cơ sở, chỉ đăng/sửa trong khung giờ HT cấu hình.
- Thiếu nguyên liệu: bếp báo PHT qua Zalo/điện thoại, hệ thống không ghi (NUT-22).
- **Ngoài phạm vi:** gửi đơn cho NCC qua hệ thống, giá hợp đồng, kho chung liên cơ sở, kiểm kê, ghi nhận bàn giao suất, trạng thái nấu.

**Thay so với bản 28/10:**
- Bỏ trừ dự kiến theo sĩ số dự kiến + điều chỉnh hằng ngày; thay bằng trừ một lần khi công bố thực đơn chuẩn ngày, theo suất đã xác nhận.
- Công bố thực đơn theo ngày, sau khi chốt suất (trước đây: công bố thực đơn rồi mới có suất).
- Kho không đủ ⇒ chặn công bố; trừ lô hết hạn trước (đóng P-25).
- Sĩ số dự kiến chỉ dùng cho bản dự trù hàng tươi; bản dự trù được lưu và in (đóng P-24; trước đây "dự báo tuần" ngoài phạm vi, không có bảng).
- Sổ lưu mẫu thêm bước PHT duyệt, hủy mẫu, in biểu mẫu.

**Thay so với bản 21–23/10:**
- Bỏ phiếu xuất theo ngày (bếp chọn lô, thiếu hàng chặn cả phiếu); thay bằng trừ dự kiến + điều chỉnh.
- Kiểm nhận hàng tươi do PHT ghi, thay cho "bếp ghi, PHT duyệt".
- Thêm danh mục NCC, thay cho "chỉ ghi tên NCC dạng chữ".

## Hệ quả

- Bảng đề xuất ở `MEAL_NUTRITION_AI_SCHEMA.md` (file thiết kế của dev, chưa có trong repo) §5: `stock_receipts`, `stock_receipt_lines`, `stock_issues`, `food_samples`. Cần thêm bảng NCC (§13), đổi `stock_issues` sang bút toán trừ khi công bố + điều chỉnh khi xác nhận lại, thêm chỗ lưu bản dự trù, trạng thái duyệt lưu/hủy mẫu (§14). Vượt giới hạn 15 bảng ⇒ báo user khi thiết kế lại.
- Tồn hiện tại = tổng tồn còn lại của các dòng DRY; không có bảng tồn riêng. Lịch sử nhập/trừ đọc từ phiếu nhận và bút toán trừ.
- FE có màn Nhà cung cấp: giữ được, chỉnh theo danh mục mới. Màn Dự báo tuần: đổi thành màn bản dự trù (lưu, in danh sách đặt hàng).
- Lượng thực phẩm cần vẫn tính khi đọc, tách theo campus và FRESH/DRY; chỉ phần đầu vào sĩ số dự kiến của bản dự trù là được lưu.

## Còn chờ

- P-26: mẫu sổ lưu mẫu, giấy tờ bếp hằng ngày.
