# Module — health

Feature: `health` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đợt khám/kỳ cân đo, số đo trong kỳ, phiếu khám Phụ lục II, phiếu theo dõi, mức theo dõi đã có (2026-10-31); phân loại WHO (hoãn), trend, AI: thiết kế

## Mục đích
Cân đo theo kỳ cân đo do HT/PHT mở (thường 3 tháng/lần, cảnh báo khi sát nhau — HLT-01), đợt khám bác sĩ 1 lần/năm học (HLT-08), phân loại theo chuẩn WHO (HLT-03, HLT-10), trend xác định, diễn giải AI có duyệt; kiểm tra sức khỏe đầu ngày nhập cùng màn điểm danh (HLT-07); sức khỏe trong ngày báo phụ huynh ngay (HLT-14).

## Owns
HealthRecord (mỗi lần đo; sửa kèm lý do, có lịch sử — HLT-13), phiếu khám Phụ lục II và mức theo dõi từ kết luận bác sĩ (HLT-08, HLT-10), phiếu theo dõi sức khỏe + ảnh kết quả chuyên khoa (HLT-16), kết quả phân loại WHO + điều chỉnh của GV kèm lý do (HLT-10, hoãn), đánh dấu suy dinh dưỡng/thừa cân (HLT-12), trạng thái công bố cho phụ huynh (HLT-11), đợt khám bác sĩ (HLT-08), ghi nhận sức khỏe trong ngày (triệu chứng, xử trí, tai nạn nhỏ — HLT-14), trend calculation, HealthInterpretation (DRAFT/APPROVED, source).

## KHÔNG owns
Dị ứng (child — HLT-06), chẩn đoán/điều trị (exclusion), quy trình can thiệp dinh dưỡng (HLT-12).

## Rules
HLT-01..05, HLT-07..14, HLT-16, AI-01..06, AI-07 (giải thích xu hướng sức khỏe). Flow: `docs/business/flows/health-check.md`. G94-180/HLT-08 (lọc đợt khám, phiếu theo dõi theo năm học, chốt 2026-10-28 — `BUSINESS_RULES` mục G94-180): plan `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md` mục 9.

## Phụ thuộc
- Dùng: service `child` (mã trẻ, hồ sơ, lớp hiện tại; đọc/ghi tiền sử khai báo `ChildIntakeService.findMedicalHistory`/`updateMedicalHistory`), identity (scope), `HealthImageStorage` (S3 ảnh kết quả chuyên khoa), `AiClient` (`integration/`).
- Nghe: `YearTransitionService.ChildrenLeftSchoolEvent` (trẻ ra trường ⇒ phiếu theo dõi đang mở tự đóng).
- Được dùng bởi: learning-observation (DevelopmentProfile), reporting (trẻ ốm theo lớp — RPT-01).
- Event: ghi nhận sức khỏe trong ngày, công bố kết quả cân đo ⇒ notification.

## API & bảng
Plan: `docs/plans/completed/2026-10-31-health-check-rounds.md` (phạm vi B1, mặc định B2–B8), `docs/plans/completed/2026-10-31-health-exam-follow-ups.md` (mặc định C1–C10).
- Bảng (V40): `health_check_campaigns`, `health_check_campaign_history`, `health_check_absences`, `health_measurements`, `health_measurement_edits`; (V41) cột phiếu khám ở `health_measurements`, `health_follow_ups`, `health_follow_up_events`, `health_follow_up_images` (`docs/database/DATA_DICTIONARY.md`).
- `/health-checks` (GET danh sách lọc `campusId`, `kind`, `status`, `schoolYear`; POST tạo), `/health-checks/schedule-warnings`, `/health-checks/{id}` (GET, PUT), `/{id}/classes/{classId}/children`, `/{id}/cancel|close|reopen` — HT, PHT quản lý theo điểm trường; GV xem điểm trường của lớp mình.
- `/children/{childId}/health` (GET sổ sức khỏe rút gọn), `/children/{childId}/health/measurements` (POST), `/{measurementId}` (PUT, bắt buộc lý do) — GV phụ trách lớp nhập trong kỳ cân đo đang diễn ra (HLT-09).
- `/health-checks/{id}/children/{childId}/exam` (GET; PUT lưu nháp/kết luận — GV lớp; POST `/review-preview`) — phiếu khám Phụ lục II của đợt khám.
- `/health-follow-ups` (GET lọc `campusId`, `status`, `overdue`, `schoolYear` ⇒ `{ items, counts }`), `/{id}` (GET), `/{id}/parent-contacts`, `/{id}/external-results` (ảnh data URL, tối đa 3), `/{id}/close` (POST — GV lớp), `/{id}/images/{imageId}` (GET ảnh).
- Mã lỗi 5009–5039 (`docs/contracts/ERROR_CONTRACT.md`).
- Chưa có: phân loại WHO (hoãn), báo cáo 5.1, 5.3 (hoãn), trend, AI, thông báo (mở đợt, phiếu theo dõi, cập nhật kết quả — chưa có module thông báo), API phụ huynh xem kết quả.

## PENDING
P-21 (triệu chứng chọn nhanh), P-22 (trẻ bất thường lúc nhận), P-23 (ưu tiên sức khỏe đầu ngày).

## Known pitfalls
- Mức theo dõi chỉ từ kết luận bác sĩ của phiếu đã kết luận (`HealthExamOutcome`); số đo của kỳ cân đo, phiếu nháp luôn `UNAVAILABLE`. Phân loại WHO hoãn (HLT-10).
- Phiếu khám nháp không phải kết quả: không vào sổ sức khỏe, lần khám trước, mức hiện tại, phiếu theo dõi; danh sách lớp hiện `IN_PROGRESS` (đợt đã đóng: `NOT_CONCLUDED`). Đọc lần đo ở chỗ mới phải lọc `isDraft()`.
- Nháp vẫn chiếm ngày đo (UNIQUE `(child_id, date)`) và vẫn kiểm khoảng chiều cao/cân nặng/vòng đầu để không vi phạm CHECK.
- Trạng thái phiếu theo dõi, hạn 7 ngày, quá hạn tính khi đọc từ dòng thời gian (`HealthFollowUpState`), không lưu, không job nhắc. Hạn tính từ lúc **lưu** kết quả mở vòng, không từ ngày khám.
- Sửa phiếu đã kết luận không mở, đóng hay gỡ phiếu theo dõi; chỉ cập nhật ngày khám, cờ bất thường mới, lời trao đổi tại buổi khám (`HealthFollowUpTracker`).
- Đọc nhiều trẻ một lần qua `HealthRecords.load` (snapshot), tránh truy vấn theo từng trẻ.
- Ảnh kết quả chuyên khoa: kiểm chữ ký tệp, không tin loại khai báo; không trả key/URL bucket.
- Vòng đầu đo khi trẻ chưa quá 60 tháng: tới trước ngày đủ 61 tháng (`HealthRecords.headCircumferenceUntil`).
- Suy dinh dưỡng/thừa cân chỉ đánh dấu; không làm quy trình theo dõi/can thiệp (HLT-12).
- Lưu là công bố (HLT-11): số đo khi lưu, phiếu khám khi kết luận; sức khỏe trong ngày thì báo ngay (HLT-14).
- Sửa số đo không ghi đè mất dấu: lưu lịch sử + lý do (HLT-13).
- Prompt + kiểm tra output AI phải chặn ngôn ngữ chẩn đoán (HLT-04).
- Không có chu kỳ đo cấu hình: HT/PHT mở kỳ cân đo, hệ thống chỉ cảnh báo khi ngày bắt đầu cách đợt/kỳ khác dưới 2 tháng 15 ngày (HLT-01, sửa 2026-10-31).
- Mỗi điểm trường một đợt/kỳ chưa đóng, tính chung hai loại (index UNIQUE có điều kiện).
- Đợt/kỳ thuộc năm học của ngày bắt đầu; lớp của đợt = lớp điểm trường trong năm đó (`findYearClassrooms(yearId)`, không truyền null vì null = năm đang hoạt động).
- Tên record lồng trong DTO phải duy nhất toàn hệ thống: springdoc đặt tên schema theo tên đơn giản, trùng (vd. `History`) sẽ ghi đè schema của module khác trong `docs/api/openapi.yaml`.

## Related issues
Chưa có.

## Đọc thêm khi
Đụng AI ⇒ card ai-assistance. Đụng hiển thị phụ huynh ⇒ ADR-0010.
