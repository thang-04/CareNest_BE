# Module — health

Feature: `health` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đợt khám/kỳ cân đo và số đo trong kỳ đã có (2026-10-31); phiếu khám, phân loại WHO, trend, AI: thiết kế

## Mục đích
Cân đo theo kỳ cân đo do HT/PHT mở (thường 3 tháng/lần, cảnh báo khi sát nhau — HLT-01), đợt khám bác sĩ 1 lần/năm học (HLT-08), phân loại theo chuẩn WHO (HLT-03, HLT-10), trend xác định, diễn giải AI có duyệt; kiểm tra sức khỏe đầu ngày nhập cùng màn điểm danh (HLT-07); sức khỏe trong ngày báo phụ huynh ngay (HLT-14).

## Owns
HealthRecord (mỗi lần đo; sửa kèm lý do, có lịch sử — HLT-13), kết quả phân loại WHO + điều chỉnh của GV kèm lý do (HLT-10), đánh dấu suy dinh dưỡng/thừa cân (HLT-12), trạng thái công bố cho phụ huynh (HLT-11), đợt khám bác sĩ (HLT-08), ghi nhận sức khỏe trong ngày (triệu chứng, xử trí, tai nạn nhỏ — HLT-14), trend calculation, HealthInterpretation (DRAFT/APPROVED, source).

## KHÔNG owns
Dị ứng (child — HLT-06), chẩn đoán/điều trị (exclusion), quy trình can thiệp dinh dưỡng (HLT-12).

## Rules
HLT-01..05, HLT-07..14, AI-01..06, AI-07 (giải thích xu hướng sức khỏe). Flow: `docs/business/flows/health-check.md`. G94-180/HLT-08 (lọc đợt khám, phiếu theo dõi theo năm học, chốt 2026-10-28 — `BUSINESS_RULES` mục G94-180): plan `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md` mục 9.

## Phụ thuộc
- Dùng: service `child`, identity (scope), `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (DevelopmentProfile), reporting (trẻ ốm theo lớp — RPT-01).
- Event: ghi nhận sức khỏe trong ngày, công bố kết quả cân đo ⇒ notification.

## API & bảng
Plan: `docs/plans/completed/2026-10-31-health-check-rounds.md` (phạm vi B1, mặc định B2–B8).
- Bảng (V40): `health_check_campaigns`, `health_check_campaign_history`, `health_check_absences`, `health_measurements`, `health_measurement_edits` (`docs/database/DATA_DICTIONARY.md`).
- `/health-checks` (GET danh sách lọc `campusId`, `kind`, `status`, `schoolYear`; POST tạo), `/health-checks/schedule-warnings`, `/health-checks/{id}` (GET, PUT), `/{id}/classes/{classId}/children`, `/{id}/cancel|close|reopen` — HT, PHT quản lý theo điểm trường; GV xem điểm trường của lớp mình.
- `/children/{childId}/health` (GET sổ sức khỏe rút gọn), `/children/{childId}/health/measurements` (POST), `/{measurementId}` (PUT, bắt buộc lý do) — GV phụ trách lớp nhập trong kỳ cân đo đang diễn ra (HLT-09).
- Mã lỗi 5009–5026 (`docs/contracts/ERROR_CONTRACT.md`).
- Chưa có: phiếu khám Phụ lục II, phiếu theo dõi, phân loại WHO, trend, AI, thông báo khi mở đợt (chưa có module thông báo).

## PENDING
P-21 (triệu chứng chọn nhanh), P-22 (trẻ bất thường lúc nhận), P-23 (ưu tiên sức khỏe đầu ngày). Nội dung ghi lại từ đợt khám bác sĩ (HLT-08), ngoài dị ứng: chưa có rule.

## Known pitfalls
- Phân loại tự động theo WHO; GV chỉnh phân loại bắt buộc lý do (HLT-10).
- Suy dinh dưỡng/thừa cân chỉ đánh dấu; không làm quy trình theo dõi/can thiệp (HLT-12).
- Phụ huynh chỉ thấy kết quả cân đo sau khi GV/PHT công bố (HLT-11); sức khỏe trong ngày thì báo ngay (HLT-14).
- Sửa số đo không ghi đè mất dấu: lưu lịch sử + lý do (HLT-13).
- Prompt + kiểm tra output AI phải chặn ngôn ngữ chẩn đoán (HLT-04).
- Không có chu kỳ đo cấu hình: HT/PHT mở kỳ cân đo, hệ thống chỉ cảnh báo khi ngày bắt đầu cách đợt/kỳ khác dưới 2 tháng 15 ngày (HLT-01, sửa 2026-10-31).
- Mỗi điểm trường một đợt/kỳ chưa đóng, tính chung hai loại (index UNIQUE có điều kiện).
- Đợt/kỳ thuộc năm học của ngày bắt đầu; lớp của đợt = lớp điểm trường trong năm đó (`findYearClassrooms(yearId)`, không truyền null vì null = năm đang hoạt động).
- Tên record lồng trong DTO phải duy nhất toàn hệ thống: springdoc đặt tên schema theo tên đơn giản, trùng (vd. `History`) sẽ ghi đè schema của module khác trong `docs/api/openapi.yaml`.
- HLT-11 ghi "GV hoặc PHT công bố" nhưng user chốt 2026-10-23 tự công bố khi lưu ⇒ BE theo user (plan B2), chờ user xác nhận sửa HLT-11.

## Related issues
Chưa có.

## Đọc thêm khi
Đụng AI ⇒ card ai-assistance. Đụng hiển thị phụ huynh ⇒ ADR-0010.
