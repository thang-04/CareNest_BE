# Module — learning-observation

Feature: `observation` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Hồ sơ phát triển liên tục của trẻ: đánh giá hằng ngày có cấu trúc, nhận xét định kỳ, khen thưởng; nhận xét chung về lớp mỗi ngày cho báo cáo BGH (RPT-02).

## Owns
ObservationCriteria/Option (cấu hình — P-11), đánh giá ngày (mọi trẻ có mặt, 7 mặt, 3 mức, khóa hết ngày — OBS-01, OBS-02, OBS-12), nhận xét định kỳ (tuần/tháng/học kỳ/năm, DRAFT → GV xác nhận — OBS-05, OBS-06), cờ ngày + phiếu bé ngoan (OBS-10), khen cuối năm (OBS-14), nhận xét lớp hằng ngày (RPT-02), DevelopmentProfile (read model — OBS-03).

## KHÔNG owns
Kế hoạch giáo dục, mục tiêu hoạt động (education-plan — chỉ đọc, OBS-07); dữ liệu sức khỏe/điểm danh/bữa ăn (chỉ đọc).

## Rules
OBS-01..14 (OBS-08 REMOVED), AI-01..08, AI-10, PAR-03. Flow: `docs/business/flows/child-observation.md`.

## Phụ thuộc
- Dùng: service `child`, service `attendance`, service `meals`, service `health`, service `eduplan` (chỉ query), `AiClient` (`integration/`).
- Được dùng bởi: reporting.
- Module downstream nhất trong child record — không module domain nào được import learning.

## API & bảng
Chưa có. Nhập đánh giá ngày theo batch cả lớp.

## PENDING
P-11 (tiêu chí cố định theo Bộ hay trường cấu hình), P-30 (mẫu nhận xét định kỳ của trường), P-31 (làm cả phiếu bé ngoan và khen cuối năm).

## Known pitfalls
- Không enum cứng tiêu chí đánh giá khi P-11 chưa chốt (OBS-02).
- Trẻ vắng không có đánh giá ngày; trẻ có mặt mặc định "bình thường", GV chỉ sửa trẻ khác thường (OBS-01).
- Đánh giá ngày khóa sau hết ngày (OBS-12); không mở API sửa ngày cũ.
- Nhận xét định kỳ không có bước BGH duyệt; GV xác nhận là xong (OBS-05).
- Hệ thống không gợi ý trẻ nhận phiếu bé ngoan; GV tự chọn (OBS-10).
- Không có mục theo dõi/can thiệp riêng trong hồ sơ phát triển (OBS-03).
- DevelopmentProfile không copy dữ liệu nguồn vào bảng riêng; đọc qua service của các feature nguồn.
- Mẫu nhận xét nền phải chạy khi AI tắt (OBS-04).

## Related issues
Chưa có.

## Đọc thêm khi
Đụng ngữ cảnh hoạt động ⇒ card education-plan, ADR-0019. Đụng AI nháp nhận xét ⇒ card ai-assistance, ADR-0008. Đụng hiển thị phụ huynh ⇒ ADR-0010.
