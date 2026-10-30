# Module — nutrition

Feature: `meals` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Pipeline dinh dưỡng xác định: thực phẩm → món → thực đơn (công bố) → số suất đã xác nhận → định lượng; kho khô rút gọn, kiểm nhận hàng tươi, danh mục NCC, sổ lưu mẫu, ảnh mẫu bữa; màn bếp trên App.

## Owns
Food, NutrientValue (source, sourceVersion), Dish, Recipe (gam/trẻ theo nhóm tuổi), MealPlan (thực đơn: nháp → công bố), MealCount, MealConfirmation (lịch sử xác nhận), FoodQuantityPlan, phân loại Fresh/Stored; Supplier, phiếu nhận kho khô theo lô, trừ kho (dự kiến + điều chỉnh), phiếu kiểm nhận hàng tươi, FoodSample, ảnh mẫu bữa (ADR-0009). Đã bỏ MealPreparation, MealHandover (NUT-14, NUT-15 REMOVED). Tên entity: `docs/business/DOMAIN_MODEL.md`.

## KHÔNG owns
Điểm danh (attendance), dị ứng gốc (child), cấu hình giờ chốt (school-structure).

## Rules
NUT-01..23 (NUT-14, NUT-15 REMOVED), AI-01..06. Flow: `docs/business/flows/meal-management.md`, `docs/business/flows/attendance.md`.

## Phụ thuộc
- Dùng: service `attendance` (trẻ có mặt), service `child` (allergySummary: số trẻ theo chất, mức, lớp — không danh tính), service `organization` (nhóm tuổi, số bữa), `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (meal history), reporting.
- Event nghe (PROPOSED, ADR-0004): `AttendanceChanged` → nếu số suất đã xác nhận ⇒ đánh dấu thay đổi, yêu cầu xác nhận lại.
- Event phát: `MealCountConfirmed`, `MealPlanApproved` (notification bếp).

## API & bảng
Chưa có API. Thiết kế bảng (PROPOSED, chưa migration): `docs/database/MEAL_NUTRITION_AI_SCHEMA.md` §3–§6; nháp AI theo ADR-0018 (`ai_drafts`).

## PENDING
P-24 (sĩ số dự kiến lấy trước bao lâu), P-25 (chọn lô khi trừ, kho không đủ), P-26 (mẫu sổ lưu mẫu), P-27 (kiểm thực ba bước). Đã đóng 28/10: P-04..P-10.

## Known pitfalls
- Kho khô trừ 2 bước: trừ dự kiến khi công bố thực đơn, rồi điều chỉnh theo chênh lệch suất đã xác nhận mỗi ngày (NUT-11) — đừng trừ hai lần cùng một lượng.
- Bếp chỉ dùng App, cả tổ dùng chung một tài khoản mỗi cơ sở (AUTH-21, AUTH-22); kiểm nhận hàng tươi và nhập kho do PHT ghi trên Web (NUT-11, NUT-12).
- Thực đơn đã công bố không sửa (NUT-08); chưa cân đối dinh dưỡng thì chặn công bố.
- AI không được ghi vào Food/NutrientValue; output AI chỉ là MealPlan DRAFT (NUT-05, AI-01).
- MealCount CONFIRMED bất biến — không recompute ngầm khi attendance đổi (ADR-0005).
- Không gửi tên/ID trẻ cho AI khi xét dị ứng; dùng tổng hợp (AI-04).
- Đơn vị định lượng: thống nhất đơn vị (g/ml/suất) ngay trong Recipe để tránh sai khi nhân với số suất.

## Related issues
Chưa có. Rủi ro dự kiến: CMR-01, CMR-02.

## Đọc thêm khi
Đụng AI menu ⇒ card ai-assistance + ADR-0007/0008 (phạm vi AI chờ khảo sát). Đụng kho/NCC ⇒ ADR-0009 (ACCEPTED, cập nhật 28/10).
