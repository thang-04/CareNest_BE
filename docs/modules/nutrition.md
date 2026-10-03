# Module — nutrition

Feature: `meals` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Pipeline dinh dưỡng xác định: thực phẩm → món → thực đơn → số suất đã chốt → định lượng; màn hình bếp.

## Owns
Food, NutrientValue (source, sourceVersion), Dish, Recipe, MealPlan (nháp/duyệt), MealCount, MealConfirmation (lịch sử xác nhận), FoodQuantityPlan, phân loại Fresh/Stored. Tên entity theo guide mục 12 (nháp).

## KHÔNG owns
Điểm danh/báo ăn (attendance), dị ứng gốc (child), tồn kho/NCC (ngoài V1 — ADR-0009).

## Rules
NUT-01..13, AI-01..06. Flow: `docs/business/flows/meal-management.md`, `docs/business/flows/attendance.md`.

## Phụ thuộc
- Dùng: service `attendance` (MealParticipation), service `child` (allergySummary), service `organization`, `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (meal history), reporting.
- Event nghe (PROPOSED, ADR-0004): `AttendanceChanged` → nếu số suất đã xác nhận ⇒ đánh dấu thay đổi, yêu cầu xác nhận lại.
- Event phát: `MealCountConfirmed`, `MealPlanApproved` (notification bếp).

## API & bảng
Chưa có.

## PENDING
P-04, P-05, P-06, P-07, P-08, P-09, P-10.

## Known pitfalls
- AI không được ghi vào Food/NutrientValue; output AI chỉ là MealPlan DRAFT (NUT-05, AI-01).
- MealCount CONFIRMED bất biến — không recompute ngầm khi attendance đổi (ADR-0005).
- Không gửi tên/ID trẻ cho AI khi xét dị ứng; dùng tổng hợp (AI-04).
- Đơn vị định lượng: thống nhất đơn vị (g/ml/suất) ngay trong Recipe để tránh sai khi nhân với số suất.

## Related issues
Chưa có. Rủi ro dự kiến: CMR-01, CMR-02.

## Đọc thêm khi
Đụng AI menu ⇒ card ai-assistance + ADR-0007/0008. Đụng kho/NCC ⇒ dừng, hỏi (ngoài V1).
