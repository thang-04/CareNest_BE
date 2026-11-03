# Module — nutrition

Feature: `meals` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: sổ báo ăn đã làm (đợt 2 điểm danh), danh mục bán trú L1 (02/11), phần còn lại thiết kế

## Mục đích
Pipeline dinh dưỡng xác định: thực phẩm → món → thực đơn (công bố) → số suất đã xác nhận → định lượng; kho khô rút gọn, kiểm nhận hàng tươi, danh mục NCC, sổ lưu mẫu, ảnh mẫu bữa; màn bếp trên App.

## Owns
Food, NutrientValue (source, sourceVersion), Dish, Recipe (gam/trẻ theo nhóm tuổi), MealPlan (thực đơn: nháp → công bố), MealCount, MealConfirmation (lịch sử xác nhận), FoodQuantityPlan, phân loại Fresh/Stored; Supplier, phiếu nhận kho khô theo lô, trừ kho (một lần khi công bố thực đơn chuẩn ngày + điều chỉnh khi xác nhận lại suất), bản dự trù hàng tươi, phiếu kiểm nhận hàng tươi, FoodSample (lưu/hủy, PHT duyệt), ảnh mẫu bữa theo cơ sở (ADR-0009). Đã bỏ MealPreparation, MealHandover (NUT-14, NUT-15 REMOVED). Tên entity: `docs/business/DOMAIN_MODEL.md`.

## KHÔNG owns
Điểm danh (attendance), dị ứng gốc (child), cấu hình giờ chốt (school-structure).

## Rules
NUT-01..23 (NUT-14, NUT-15 REMOVED), AI-01..06. Flow: `docs/business/flows/meal-management.md`, `docs/business/flows/attendance.md`.

## Phụ thuộc
- Dùng: service `attendance` (trẻ có mặt), service `child` (allergySummary: số trẻ theo chất, mức, lớp — không danh tính), service `organization` (nhóm tuổi, số bữa), `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (meal history), reporting.
- Event nghe (ADR-0004): `AttendanceChangedEvent` sau commit (`MealCountAttendanceListener`) → số của lớp đổi ⇒ sổ đã xác nhận chuyển chờ xác nhận lại.
- Event phát: `MealCountConfirmed`, `MealPlanApproved` (notification bếp).

## API & bảng
`GET /meal-counts`, `POST /meal-counts/{id}/confirm`, `POST /meal-counts/adjustments` (`MealCountController`). Bảng `meal_counts`, `meal_count_lines`, `meal_count_history` (V26), vai trò người xác nhận (V29). Tổng hợp lười: lần đầu mở sổ sau giờ chốt (chưa có job). Code: xác nhận/điều chỉnh theo mã `MEAL_COUNT_CONFIRM` + phạm vi (AUTH-20, ADR-0023, 31/10): HT mọi điểm trường; PHT mặc định ở điểm trường mình, HT thu hồi hoặc GRANT phạm vi cả trường; mọi PHT còn phân công xem số suất mọi điểm trường; điều chỉnh (có lý do) chỉ PHT — đối chiếu lại NUT-01..04 (khảo sát 28/10: có mặt là ăn, HT xác nhận thay khi PHT vắng, sai thì trả GV qua mở lại điểm danh). Bếp chưa có BE: FE bếp đọc số đã xác nhận từ `GET /meal-counts`, `confirmedAt` mới ⇒ nhãn "Mới" thay thông báo.

Danh mục (V54, V55, plan `docs/plans/completed/2026-11-02-day-boarding-l1-catalog.md`): `/nutrition/allergens`, `/nutrition/foods` (+ `/import`, `/{id}/status`), `/nutrition/dishes` (+ `/{id}/copy`, `/{id}/status`), `/nutrition/age-group-configs` (+ `/{ageGroupId}/versions`, `/batch`), `/nutrition/suppliers` — `NutritionFoodController`, `NutritionDishController`, `AgeGroupMealConfigController`, `SupplierController`. Đọc: HT, PHT; ghi: mã `NUTRITION_MANAGE` (HT có sẵn, PHT do HT cấp). Ma trận quyền cả luồng: `docs/business/flows/meal-management-permissions.md`.

Phần chưa có API (thực đơn, kho, hàng tươi, mẫu) — thiết kế bảng (PROPOSED, chưa migration): `MEAL_NUTRITION_AI_SCHEMA.md` (file thiết kế của dev, chưa có trong repo) §3–§6; nháp AI (`ai_drafts`).

## PENDING
P-26 (mẫu sổ lưu mẫu), P-27 (kiểm thực ba bước). Đã đóng 28/10: P-04..P-10; 01/11: P-24, P-25, P-37.

## Known pitfalls
- Kho khô trừ theo ngày, từng cơ sở, khi suất ngày của cơ sở được xác nhận (NUT-11, đóng P-38); lập/công bố tuần chỉ cảnh báo theo sĩ số dự kiến. Thiếu kho không chặn xác nhận suất. Xác nhận lại suất chỉ ghi phần chênh lệch — đừng trừ lại toàn bộ.
- Sĩ số dự kiến chỉ dùng cho bản dự trù hàng tươi (NUT-07), không dùng để trừ kho.
- Bếp chỉ dùng App, cả tổ dùng chung một tài khoản mỗi cơ sở (AUTH-21, AUTH-22); kiểm nhận hàng tươi và nhập kho do PHT ghi trên Web (NUT-11, NUT-12).
- Công bố thực đơn **cả tuần**, một lần cho cả trường, không chờ chốt suất (NUT-08, chốt 02/11); chưa cân đối dinh dưỡng thì chặn. Đã công bố không sửa (ngoại lệ bất khả kháng có quyền riêng — P-39). Đến ngày chỉ thêm ảnh bữa.
- AI không được ghi vào Food/NutrientValue; output AI chỉ là MealPlan DRAFT (NUT-05, AI-01).
- MealCount CONFIRMED bất biến — không recompute ngầm khi attendance đổi (ADR-0005).
- Không gửi tên/ID trẻ cho AI khi xét dị ứng; dùng tổng hợp (AI-04).
- Đơn vị định lượng: thống nhất đơn vị (g/ml/suất) ngay trong Recipe để tránh sai khi nhân với số suất.
- NUT-02 (01/11): `POST /meal-counts/adjustments` chỉ đổi `substitute`; `normal` null = giữ, khác số hiện tại ⇒ 409 `6008` `MEAL_COUNT_NORMAL_NOT_EDITABLE`. `MealCount.replaceClassFigures` (điểm danh đổi) giữ `substitute` đã điều chỉnh. Lịch sử: `ADJUSTED` (delta suất thay thế), `ATTENDANCE_CHANGED`.
- NUT-02 (01/11): xác nhận theo cơ sở–ngày — `POST /meal-counts/{id}/confirm` xác nhận mọi bữa còn chờ cùng cơ sở + ngày + năm; bữa đã xác nhận gọi lại ⇒ 200 trả trạng thái hiện tại (không còn 409 `6005`, để FE gọi lần lượt từng bữa không vỡ). Còn lớp có trẻ đang học chưa điểm danh (`tallyMeals.missing > 0`) ⇒ 409 `6009` `MEAL_COUNT_ATTENDANCE_INCOMPLETE`, câu lỗi liệt kê tên lớp.

## Related issues
Chưa có. Rủi ro dự kiến: CMR-01, CMR-02.

## Đọc thêm khi
Đụng AI menu ⇒ card ai-assistance + ADR-0007/0008 (phạm vi AI chờ khảo sát). Đụng kho/NCC ⇒ ADR-0009 (ACCEPTED, cập nhật 01/11).
