# Module — learning-observation

Feature: `observation` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Hồ sơ phát triển liên tục của trẻ: quan sát hằng ngày có cấu trúc, ngữ cảnh hoạt động, đánh giá, follow-up, nhận xét tổng hợp.

## Owns
ObservationCriteria/Option (cấu hình), Observation, ActivityContext (source-agnostic), ActivityParticipation, Assessment, FollowUp, Summary (DRAFT/APPROVED), Reward (PROPOSED — OBS-10), DevelopmentProfile (read model).

## KHÔNG owns
Giáo án soạn/duyệt (GoKids — OBS-08), dữ liệu sức khỏe/điểm danh/bữa ăn (chỉ đọc).

## Rules
OBS-01..11, AI-01..06. Flow: `docs/business/flows/child-observation.md`.

## Phụ thuộc
- Dùng: service `child`, service `attendance`, service `meals`, service `health` (chỉ query), `AiClient` (`integration/`).
- Được dùng bởi: reporting.
- Module downstream nhất trong child record — không module domain nào được import learning.

## API & bảng
Chưa có. Nhập quan sát theo batch cả lớp.

## PENDING
P-01/OBS-07 (ActivityContext source — ADR-0006 OPEN), P-11 (tiêu chí), P-12 (summary có gửi phụ huynh), P-16b (assessment).

## Known pitfalls
- Không enum cứng tiêu chí quan sát (OBS-02).
- DevelopmentProfile không copy dữ liệu nguồn vào bảng riêng; đọc qua service của các feature nguồn.
- Summary template baseline phải chạy khi AI tắt (OBS-04).
- Không mở rộng thành quản lý giáo án khi làm ActivityContext.

## Related issues
Chưa có.

## Đọc thêm khi
Đụng ActivityContext ⇒ ADR-0006 (bắt buộc). Đụng AI summary ⇒ card ai-assistance, ADR-0008.
