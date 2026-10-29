# Module — health

Feature: `health` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Cân đo định kỳ (mặc định 3 lần/năm, cấu hình được — HLT-01), phân loại theo chuẩn WHO (HLT-03, HLT-10), trend xác định, diễn giải AI có duyệt; kiểm tra sức khỏe đầu ngày nhập cùng màn điểm danh (HLT-07); sức khỏe trong ngày báo phụ huynh ngay (HLT-14).

## Owns
HealthRecord (mỗi lần đo; sửa kèm lý do, có lịch sử — HLT-13), kết quả phân loại WHO + điều chỉnh của GV kèm lý do (HLT-10), đánh dấu suy dinh dưỡng/thừa cân (HLT-12), trạng thái công bố cho phụ huynh (HLT-11), đợt khám bác sĩ (HLT-08), ghi nhận sức khỏe trong ngày (triệu chứng, xử trí, tai nạn nhỏ — HLT-14), trend calculation, HealthInterpretation (DRAFT/APPROVED, source).

## KHÔNG owns
Dị ứng (child — HLT-06), chẩn đoán/điều trị (exclusion), quy trình can thiệp dinh dưỡng (HLT-12).

## Rules
HLT-01..05, HLT-07..14, AI-01..06, AI-07 (giải thích xu hướng sức khỏe). Flow: `docs/business/flows/health-check.md`.

## Phụ thuộc
- Dùng: service `child`, identity (scope), `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (DevelopmentProfile), reporting (trẻ ốm theo lớp — RPT-01).
- Event: ghi nhận sức khỏe trong ngày, công bố kết quả cân đo ⇒ notification.

## API & bảng
Chưa có. API nhập theo lớp/đợt đo; GV nhập cho lớp mình (HLT-09).

## PENDING
P-21 (triệu chứng chọn nhanh), P-22 (trẻ bất thường lúc nhận), P-23 (ưu tiên sức khỏe đầu ngày). Nội dung ghi lại từ đợt khám bác sĩ (HLT-08), ngoài dị ứng: chưa có rule.

## Known pitfalls
- Phân loại tự động theo WHO; GV chỉnh phân loại bắt buộc lý do (HLT-10).
- Suy dinh dưỡng/thừa cân chỉ đánh dấu; không làm quy trình theo dõi/can thiệp (HLT-12).
- Phụ huynh chỉ thấy kết quả cân đo sau khi GV/PHT công bố (HLT-11); sức khỏe trong ngày thì báo ngay (HLT-14).
- Sửa số đo không ghi đè mất dấu: lưu lịch sử + lý do (HLT-13).
- Prompt + kiểm tra output AI phải chặn ngôn ngữ chẩn đoán (HLT-04).
- Không hard-code chu kỳ đo (HLT-01).

## Related issues
Chưa có.

## Đọc thêm khi
Đụng AI ⇒ card ai-assistance. Đụng hiển thị phụ huynh ⇒ ADR-0010.
