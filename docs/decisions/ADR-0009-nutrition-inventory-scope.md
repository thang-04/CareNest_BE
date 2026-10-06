# ADR-0009 — Phạm vi kho/nhà cung cấp trong Nutrition

- Status: **OPEN** (trước đây ACCEPTED "không kho trong V1", ngày 03/10; mở lại 04/10 vì có bằng chứng mới)
- Date: 2026-10-04
- Liên quan: NUT-10..12, NUT-14..16, P-08, P-09

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

## Hướng an toàn trong khi OPEN

- Không tự tạo entity kho/NCC. Gặp yêu cầu kho thì dừng lại và hỏi người dùng.
- FoodQuantityPlan tách theo campus và Fresh/Stored, để sau này nối phương án B mà không phải sửa lại.
- Đề xuất (chưa quyết): chọn **B** nếu còn thời gian sau Must, bỏ C. Trước khi chọn B phải có đủ:
  - lý do chênh lệch tồn kho và người xác nhận;
  - nhánh hàng bị từ chối khi kiểm nhận;
  - người quản lý kho chung (PHT cơ sở chính?).

## Còn chờ

- P-08: sổ nhập-xuất-tồn thực tế.
- P-09: quy trình NCC.
- Team chốt Must/Should.
