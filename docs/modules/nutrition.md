# Module — nutrition

Feature: `meals` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: sổ báo ăn đã làm (đợt 2 điểm danh), phần còn lại thiết kế

## Mục đích
Pipeline dinh dưỡng xác định: thực phẩm → món → thực đơn → số suất đã chốt → định lượng; màn hình bếp, trạng thái chuẩn bị và bàn giao suất (PROPOSED).

## Owns
Food, NutrientValue (source, sourceVersion), Dish, Recipe, MealPlan (nháp/duyệt), MealCount, MealConfirmation (lịch sử xác nhận), FoodQuantityPlan, phân loại Fresh/Stored; MealPreparation, MealHandover (PROPOSED). Tên entity: `docs/business/DOMAIN_MODEL.md`.

## KHÔNG owns
Điểm danh/báo ăn (attendance), dị ứng gốc (child), tồn kho/NCC (OPEN — ADR-0009, chưa làm).

## Rules
NUT-01..16, AI-01..06. Flow: `docs/business/flows/meal-management.md`, `docs/business/flows/attendance.md`.

## Phụ thuộc
- Dùng: service `attendance` (MealParticipation), service `child` (allergySummary), service `organization`, `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (meal history), reporting.
- Event nghe (ADR-0004): `AttendanceChangedEvent` sau commit (`MealCountAttendanceListener`) → số của lớp đổi ⇒ sổ đã xác nhận chuyển chờ xác nhận lại.
- Event phát: `MealCountConfirmed`, `MealPlanApproved` (notification bếp).

## API & bảng
`GET /meal-counts`, `POST /meal-counts/{id}/confirm`, `POST /meal-counts/adjustments` (`MealCountController`). Bảng `meal_counts`, `meal_count_lines`, `meal_count_history` (V23). Tổng hợp lười: lần đầu mở sổ sau giờ chốt (chưa có job). Xác nhận, điều chỉnh (có lý do): chỉ PHT điểm trường; HT chỉ xem (Cần chốt 24/10/2026). Bếp chưa có BE: FE bếp đọc số đã xác nhận từ `GET /meal-counts`, `confirmedAt` mới ⇒ nhãn "Mới" thay thông báo.

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
Đụng AI menu ⇒ card ai-assistance + ADR-0007/0008. Đụng kho/NCC ⇒ dừng, hỏi (ADR-0009 OPEN).
