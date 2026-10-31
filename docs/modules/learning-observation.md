# Module — learning-observation

Feature: `observation` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Hồ sơ phát triển liên tục của trẻ: đánh giá hằng ngày có cấu trúc, đánh giá hằng ngày theo MT của giáo án đã duyệt, nhận xét định kỳ, khen thưởng; nhận xét chung về lớp mỗi ngày cho báo cáo BGH (RPT-02).

## Owns
ObservationCriteria/Option (cấu hình — P-11), đánh giá ngày (mọi trẻ có mặt, 7 mặt, 3 mức, khóa hết ngày — OBS-01, OBS-02, OBS-12), đánh giá hằng ngày theo MT (EDU-19, OBS-07), nhận xét định kỳ (tuần/tháng/học kỳ/năm, DRAFT → GV xác nhận — OBS-05, OBS-06), cờ ngày + phiếu bé ngoan (OBS-10), khen cuối năm (OBS-14), nhận xét lớp hằng ngày (RPT-02), DevelopmentProfile (read model — OBS-03).

## KHÔNG owns
Kế hoạch giáo dục, giáo án và hoạt động, MT của chúng (module education-plan, ADR-0020 — chỉ đọc bản đã duyệt, OBS-07); dữ liệu sức khỏe/điểm danh/bữa ăn (chỉ đọc).

## Rules
OBS-01..22 (OBS-08 REMOVED), EDU-19, AI-01..08, AI-10, PAR-03. Flow: `docs/business/flows/child-observation.md`.

## Phụ thuộc
- Dùng: service `child`, service `attendance`, service `meals`, service `health`, service `eduplan` (giáo án đã duyệt) — chỉ query; `AiClient` (`integration/`).
- Được dùng bởi: reporting.
- Module downstream nhất trong child record — không module domain nào được import learning.

## API & bảng
- `GET /classes/{classId}/daily-assessment?date=` (GV/PHT/HT), `PUT .../daily-assessment/general`, `PUT .../daily-assessment/skills` (GV lớp).
- `GET|POST /assessment/day-unlocks`, `POST /assessment/day-unlocks/{id}/revoke` (PHT/HT).
- `GET /assessment/overview?period=DAY|WEEK|MONTH`, `GET /assessment/overview/classes/{classId}` (PHT/HT).
- Bảng: `daily_observations`, `daily_activity_results`, `assessment_day_unlocks` (V39). Mã lỗi 73xx.
- Nhận xét chung lớp: `class_daily_comments` (V40), PUT General thêm `classComment` optional (thiếu/null giữ, rỗng xóa); cho phép entries rỗng khi chỉ lưu nhận xét. GET sheet có `classComment`, overview/detail có `classComments` theo đúng lớp/ngày; kèm người ghi/thời gian, không tính vào số trẻ đã đánh giá (OBS-19).
- Đọc giáo án đã duyệt qua `EduLessonQueryService` (education-plan); điểm danh / sức khỏe qua `AttendanceService.findDayStatuses`, `DailyHealthService.findLatestDaySigns`.

## PENDING
P-11 (tiêu chí cố định theo Bộ hay trường cấu hình; tạm dùng enum theo danh sách FE đã chốt với Cần, lệch OBS-02 cho tới khi có màn cấu hình), P-30 (mẫu nhận xét định kỳ của trường), P-31 (làm cả phiếu bé ngoan và khen cuối năm).

## Known pitfalls
- Không enum cứng tiêu chí đánh giá khi P-11 chưa chốt (OBS-02).
- Trẻ vắng không có đánh giá ngày; trẻ có mặt mặc định "bình thường", GV chỉ sửa trẻ khác thường (OBS-01).
- Đánh giá ngày khóa sau hết ngày (OBS-12); không mở API sửa ngày cũ, ngoại lệ là lần mở lại còn hiệu lực do PHT/HT cấp (OBS-18, OBS-19).
- Nhận xét định kỳ không có bước BGH duyệt; GV xác nhận là xong (OBS-05).
- Hệ thống không gợi ý trẻ nhận phiếu bé ngoan; GV tự chọn (OBS-10).
- Không có mục theo dõi/can thiệp riêng trong hồ sơ phát triển (OBS-03).
- DevelopmentProfile không copy dữ liệu nguồn vào bảng riêng; đọc qua service của các feature nguồn.
- Mẫu nhận xét nền phải chạy khi AI tắt (OBS-04).
- Không lưu bản sao hoạt động/MT của giáo án; tham chiếu giáo án và MT bằng id, đọc qua service education-plan. Lớp chưa có giáo án duyệt thì khóa phần chấm theo MT (EDU-19).

## Related issues
Chưa có.

## Đọc thêm khi
Đụng đánh giá theo MT / ngữ cảnh hoạt động ⇒ ADR-0006, card education-plan. Đụng AI nháp nhận xét ⇒ card ai-assistance, ADR-0008. Đụng hiển thị phụ huynh ⇒ ADR-0010.
