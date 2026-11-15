# ADR-0024 — Biên bản kiểm thực và chữ ký ảnh bất biến sau khi ký

- Status: **ACCEPTED** (user duyệt plan 2026-11-10; bảng một-jsonb chờ user soát — xem Quyết định 3)
- Date: 2026-11-10
- Liên quan: INS-01..04, SIG-01, NUT-21, AUTH-20..22; plan `docs/plans/active/2026-11-10-inspection-records-signatures.md`; domain spec `specs/014-inspection-records-signatures/domain-spec.md`; ADR-0009 (kho, lưu mẫu), V83 gộp bảng bán trú.

## Bối cảnh

Nhà trường lưu hồ sơ kiểm thực ba bước (QĐ 1246/QĐ-BYT Mẫu 1–3) và sổ lưu mẫu (Mẫu 4, 5) để thanh tra. PMS cho nhập bảng trên trang in, ký bằng họ tên gõ chữ, không khóa sau ký, không lịch sử. User yêu cầu ký bằng **ảnh chữ ký** của người ký, lưu kèm biên bản, xuất In/PDF/Excel; PHT điền M1–M3, bếp ghi lưu mẫu (2026-11-09/10).

## Quyết định

1. **Ảnh chữ ký là tệp trong kho tệp**, không phải data URL trong DB hay JSON. Mỗi tài khoản tối đa 5 ảnh (SIG-01); loại tệp nhận diện theo byte đầu, không tin loại khai báo; tải về chỉ qua API có xác thực.
2. **Chữ ký trên biên bản là bản sao riêng**, không tham chiếu ảnh của tài khoản; ký cũng chụp các dòng. Nhờ đó biên bản đã ký không đổi khi tài khoản đổi/xóa chữ ký hay dữ liệu nguồn (suất, thực đơn, kiểm nhận, lưu mẫu) đổi (INS-03).
3. **Một bảng `inspection_sheets`** theo (điểm trường, ngày, mẫu), dòng, chữ ký, bản chụp và lịch sử là cột jsonb; kiểm kiểu từng ô ở service. Lý do: nhánh bán trú vừa gộp bảng cho ERD gọn (V83), biên bản luôn đọc/ghi nguyên cả mẫu và không truy vấn theo ô. Đổi lại mất FK và khó thống kê theo ô; nếu sau này cần báo cáo kiểm thực thì tách bảng dòng.
4. **Khóa sau ký; "Sửa lại" cần lý do và để lại lịch sử** (INS-04); sửa trực tiếp biên bản đã ký bị từ chối. Ảnh chữ ký của lần ký cũ giữ trong lịch sử để xem lại.
5. **Ảnh chữ ký không phải chữ ký số có giá trị pháp lý**; chỉ thay họ tên gõ chữ trong hồ sơ nội bộ. Bản in vẫn để ô ký tay cho bếp và nhân viên y tế/thanh tra.

## Hệ quả

- Thêm tệp trong kho cho mỗi lần ký; xóa tệp sau commit như ảnh bữa ăn; cần chính sách giữ ảnh của lịch sử (không xóa khi tài khoản xóa chữ ký).
- Quyền dùng lại `FOOD_SAFETY_APPROVE` theo điểm trường (không thêm mã mới).
- `fresh_checks` thêm nhà cung cấp; `food_samples` thêm `dish_details` (khối lượng, dụng cụ chứa, nhiệt độ) cho Mẫu 5.
- FE/APP: màn kiểm thực đổi từ dữ liệu mẫu sang API khi BE sẵn sàng; App bếp thêm ba trường lưu mẫu.

## Phương án đã loại

- Bảng chuẩn hóa dòng/chữ ký/lịch sử riêng (6 bảng): ngược hướng ERD gọn.
- Chữ ký tham chiếu ảnh tài khoản: biên bản đổi theo, mất tính chứng từ.
- Chữ ký số thật (CA): ngoài phạm vi V1.
