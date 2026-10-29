# Module — learning-observation

Feature: `observation` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Hồ sơ phát triển liên tục của trẻ: quan sát hằng ngày có cấu trúc, đánh giá hằng ngày theo MT của giáo án đã duyệt, follow-up, nhận xét tổng hợp.

## Owns
ObservationCriteria/Option (cấu hình), Observation, đánh giá hằng ngày theo MT (EDU-19), Assessment, FollowUp, Summary (DRAFT/APPROVED), Reward (PROPOSED — OBS-10), DevelopmentProfile (read model).

## KHÔNG owns
Kế hoạch giáo dục, giáo án và hoạt động, MT của chúng (module education-plan, ADR-0017 — chỉ đọc bản đã duyệt), dữ liệu sức khỏe/điểm danh/bữa ăn (chỉ đọc).

## Rules
OBS-01..19, EDU-19, AI-01..06. Flow: `docs/business/flows/child-observation.md`.

## Phụ thuộc
- Dùng: service `child`, service `attendance`, service `meals`, service `health`, service `eduplan` (giáo án đã duyệt) — chỉ query; `AiClient` (`integration/`).
- Được dùng bởi: reporting.
- Module downstream nhất trong child record — không module domain nào được import learning.

## API & bảng
- `GET /classes/{classId}/daily-assessment?date=` (GV/PHT/HT), `PUT .../daily-assessment/general`, `PUT .../daily-assessment/skills` (GV lớp).
- `GET|POST /assessment/day-unlocks`, `POST /assessment/day-unlocks/{id}/revoke` (PHT/HT).
- `GET /assessment/overview?period=DAY|WEEK|MONTH`, `GET /assessment/overview/classes/{classId}` (PHT/HT).
- Bảng: `daily_observations`, `daily_activity_results`, `assessment_day_unlocks` (V32). Mã lỗi 73xx.
- Nhận xét chung lớp: `class_daily_comments` (V33), PUT General thêm `classComment` optional (thiếu/null giữ, rỗng xóa); cho phép entries rỗng khi chỉ lưu nhận xét. GET sheet có `classComment`, overview/detail có `classComments` theo đúng lớp/ngày; kèm người ghi/thời gian, không tính vào số trẻ đã đánh giá (OBS-19).
- Đọc giáo án đã duyệt qua `EduLessonQueryService` (education-plan); điểm danh / sức khỏe qua `AttendanceService.findDayStatuses`, `DailyHealthService.findLatestDaySigns`.

## PENDING
P-11 (tiêu chí — tạm dùng enum theo danh sách FE đã chốt với Cần, lệch OBS-02 cho tới khi có màn cấu hình), P-12 (summary có gửi phụ huynh), P-16b (assessment).

## Known pitfalls
- Không enum cứng tiêu chí quan sát (OBS-02).
- DevelopmentProfile không copy dữ liệu nguồn vào bảng riêng; đọc qua service của các feature nguồn.
- Summary template baseline phải chạy khi AI tắt (OBS-04).
- Không lưu bản sao hoạt động/MT của giáo án; tham chiếu giáo án và MT bằng id, đọc qua service education-plan. Lớp chưa có giáo án duyệt thì khóa phần chấm theo MT (EDU-19).

## Related issues
Chưa có.

## Đọc thêm khi
Đụng đánh giá theo MT ⇒ ADR-0006, card education-plan. Đụng AI summary ⇒ card ai-assistance, ADR-0008.
