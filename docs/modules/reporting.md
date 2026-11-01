# Module — reporting

Feature: `report` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Báo cáo/tổng hợp chỉ đọc theo lớp / campus / toàn trường cho BGH: báo cáo ngày (RPT-01) và định kỳ (RPT-03); xuất Excel và PDF để nộp/nhập lại vào hệ thống ngành (không tích hợp trực tiếp — EX-01).

## Owns
Report query, export. Có thể có read model/view tối ưu cho báo cáo.

## KHÔNG owns
Bất kỳ business data nào. **Không ghi/sửa dữ liệu domain.** Nhận xét lớp hằng ngày của GV (RPT-02) lưu ở learning-observation; reporting chỉ đọc.

## Rules
RPT-01..03, ATT-11, NUT-19, AUTH-08 (chỉ dữ liệu trong scope người xem).

## Phụ thuộc
- Dùng: service của mọi feature (chỉ đọc), `AccessScopeService`.
- Được dùng bởi: không ai (module lá).
- Được phép JOIN SQL xuyên bảng module trong read-only view nếu cần hiệu năng — đánh dấu rõ trong code, không ghi.

## PENDING
P-34 (tổng kết năm học trong hệ thống tới đâu).

## Known pitfalls
- Tổng hợp campus phải theo enrollment/campus tại ngày dữ liệu, không theo lớp hiện tại của trẻ.
- Quên áp scope ⇒ Hiệu phó thấy số liệu campus khác.

## Related issues
Chưa có.

## Đọc thêm khi
Báo cáo cần dữ liệu mới từ module ⇒ thêm method đọc vào service của feature đó, không đọc bảng trực tiếp (trừ view read-only).
