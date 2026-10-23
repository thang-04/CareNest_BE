# Module — learning-observation

Feature: `observation` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Hồ sơ phát triển liên tục của trẻ: quan sát hằng ngày có cấu trúc, đánh giá hằng ngày theo MT của giáo án đã duyệt, follow-up, nhận xét tổng hợp.

## Owns
ObservationCriteria/Option (cấu hình), Observation, đánh giá hằng ngày theo MT (EDU-19), Assessment, FollowUp, Summary (DRAFT/APPROVED), Reward (PROPOSED — OBS-10), DevelopmentProfile (read model).

## KHÔNG owns
Kế hoạch giáo dục, giáo án và hoạt động, MT của chúng (module education-plan, ADR-0017 — chỉ đọc bản đã duyệt), dữ liệu sức khỏe/điểm danh/bữa ăn (chỉ đọc).

## Rules
OBS-01..11, EDU-19, AI-01..06. Flow: `docs/business/flows/child-observation.md`.

## Phụ thuộc
- Dùng: service `child`, service `attendance`, service `meals`, service `health`, service `eduplan` (giáo án đã duyệt) — chỉ query; `AiClient` (`integration/`).
- Được dùng bởi: reporting.
- Module downstream nhất trong child record — không module domain nào được import learning.

## API & bảng
Chưa có. Nhập quan sát theo batch cả lớp.

## PENDING
P-11 (tiêu chí), P-12 (summary có gửi phụ huynh), P-16b (assessment).

## Known pitfalls
- Không enum cứng tiêu chí quan sát (OBS-02).
- DevelopmentProfile không copy dữ liệu nguồn vào bảng riêng; đọc qua service của các feature nguồn.
- Summary template baseline phải chạy khi AI tắt (OBS-04).
- Không lưu bản sao hoạt động/MT của giáo án; tham chiếu giáo án và MT bằng id, đọc qua service education-plan. Lớp chưa có giáo án duyệt thì khóa phần chấm theo MT (EDU-19).

## Related issues
Chưa có.

## Đọc thêm khi
Đụng đánh giá theo MT ⇒ ADR-0006, card education-plan. Đụng AI summary ⇒ card ai-assistance, ADR-0008.
