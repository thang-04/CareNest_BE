# Module — health

Feature: `health` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Hồ sơ sức khỏe định kỳ có cấu trúc, trend xác định, diễn giải AI có duyệt.

## Owns
HealthRecord, trend calculation, HealthInterpretation (DRAFT/APPROVED, source).

## KHÔNG owns
Dị ứng (child), chẩn đoán/điều trị (exclusion).

## Rules
HLT-01..06, AI-01..06. Flow: `docs/business/flows/health-check.md`.

## Phụ thuộc
- Dùng: service `child`, identity (scope), `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (DevelopmentProfile), reporting.

## API & bảng
Chưa có. API nhập theo lớp/đợt đo.

## PENDING
P-13 (ngưỡng tham chiếu), người nhập measurement.

## Known pitfalls
- Chưa có chuẩn tham chiếu ⇒ không gắn nhãn "suy dinh dưỡng/thừa cân". Chỉ hiện số và chênh lệch.
- Prompt + kiểm tra output AI phải chặn ngôn ngữ chẩn đoán (HLT-04).
- Không hard-code chu kỳ 3 tháng.

## Related issues
Chưa có.

## Đọc thêm khi
Đụng AI ⇒ card ai-assistance. Đụng hiển thị phụ huynh ⇒ ADR-0010.
