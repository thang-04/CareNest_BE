# Module — nutrition

Feature: `meals` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: sổ báo ăn đã làm (đợt 2 điểm danh), danh mục bán trú L1 (02/11), kho khô L2 + kế hoạch tuần, công bố, trừ kho, Bán trú hôm nay L3 (03/11), hàng tươi L4: dự trù theo kỳ + kiểm nhận 2 lần (03/11); bếp hôm nay, lưu/hủy mẫu, khung giờ + ảnh bữa L5 (03/11); phụ huynh xem thực đơn L6 hoãn cùng App

## Mục đích
Pipeline dinh dưỡng xác định: thực phẩm → món → thực đơn (công bố) → số suất đã xác nhận → định lượng; kho khô rút gọn, kiểm nhận hàng tươi, danh mục NCC, sổ lưu mẫu, ảnh mẫu bữa; màn bếp trên App.

## Owns
Food, NutrientValue (source, sourceVersion), Dish, Recipe (gam/trẻ theo nhóm tuổi), MealPlan (thực đơn: nháp → công bố), MealCount, MealConfirmation (lịch sử xác nhận), FoodQuantityPlan, phân loại Fresh/Stored; Supplier, phiếu nhận kho khô theo lô, trừ kho (khi suất điểm trường–ngày được xác nhận + điều chỉnh chênh lệch khi xác nhận lại — P-38), bản dự trù hàng tươi, phiếu kiểm nhận hàng tươi, FoodSample (lưu/hủy, PHT duyệt), ảnh mẫu bữa theo cơ sở (ADR-0009). Đã bỏ MealPreparation, MealHandover (NUT-14, NUT-15 REMOVED). Tên entity: `docs/business/DOMAIN_MODEL.md`.

## KHÔNG owns
Điểm danh (attendance), dị ứng gốc (child), cấu hình giờ chốt (school-structure).

## Rules
NUT-01..25 (NUT-14, NUT-15 REMOVED), AI-01..06. Flow: `docs/business/flows/meal-management.md`, `docs/business/flows/attendance.md`.

## Phụ thuộc
- Dùng: service `attendance` (trẻ có mặt), service `child` (allergySummary: số trẻ theo chất, mức, lớp — không danh tính), service `organization` (nhóm tuổi, số bữa), `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (meal history), reporting.
- Event nghe (ADR-0004): `AttendanceChangedEvent` sau commit (`MealCountAttendanceListener`) → số của lớp đổi ⇒ sổ đã xác nhận chuyển chờ xác nhận lại.
- Event nghe: `MealCountService.MealCountConfirmedEvent` sau commit (`StockDeductionListener`) → `StockLedgerService.syncDay` trừ kho khô ngày đó (P-38); lỗi không làm hỏng xác nhận suất.
- Event phát: `MealCountConfirmedEvent` (khi `confirm` chuyển số chờ sang chốt). Dự kiến: `MealPlanApproved` (notification bếp).

## API & bảng
`GET /meal-counts`, `POST /meal-counts/{id}/confirm`, `POST /meal-counts/adjustments` (`MealCountController`). Bảng `meal_counts`, `meal_count_lines`, `meal_count_history` (V26), vai trò người xác nhận (V29). Tổng hợp lười: lần đầu mở sổ sau giờ chốt (chưa có job). Code: xác nhận/điều chỉnh theo mã `MEAL_COUNT_CONFIRM` + phạm vi (AUTH-20, ADR-0023, 31/10): HT mọi điểm trường; PHT mặc định ở điểm trường mình, HT thu hồi hoặc GRANT phạm vi cả trường; mọi PHT còn phân công xem số suất mọi điểm trường; điều chỉnh (có lý do) chỉ PHT — đối chiếu lại NUT-01..04 (khảo sát 28/10: có mặt là ăn, HT xác nhận thay khi PHT vắng, sai thì trả GV qua mở lại điểm danh). Bếp chưa có BE: FE bếp đọc số đã xác nhận từ `GET /meal-counts`, `confirmedAt` mới ⇒ nhãn "Mới" thay thông báo.

Danh mục (V54, V55, plan `docs/plans/completed/2026-11-02-day-boarding-l1-catalog.md`): `/nutrition/allergens`, `/nutrition/foods` (+ `/import`, `/{id}/status`), `/nutrition/dishes` (+ `/{id}/copy`, `/{id}/status`), `/nutrition/age-group-configs` (+ `/{ageGroupId}/versions`, `/batch`), `/nutrition/suppliers` — `NutritionFoodController`, `NutritionDishController`, `AgeGroupMealConfigController`, `SupplierController`. Đọc: HT, PHT; ghi: mã `NUTRITION_MANAGE` (HT có sẵn, PHT do HT cấp). Ma trận quyền cả luồng: `docs/business/flows/meal-management-permissions.md`.

Kho khô L2 (V56, plan `docs/plans/active/2026-11-03-day-boarding-l2-dry-stock.md`): `/nutrition/stock/receipts` (+ `/{id}/confirm`, `/{id}/cancel`), `/nutrition/stock/lots`, `/nutrition/stock/movements`, `/nutrition/stock/shortfalls` — `StockController`; ghi: `STOCK_MANAGE` theo điểm trường (`CampusAccessPolicy.canEditCampus`). Mọi ghi kho của một điểm trường khóa dòng `campuses` FOR UPDATE.

Thực đơn L3 (V57, plan `docs/plans/active/2026-11-03-day-boarding-l3-weekly-menu.md`): `/nutrition/meal-plans` (+ `/{weekStart}`, `PUT`, `/{weekStart}/publish`), `/nutrition/day-board` — `MealPlanController`; ghi: `MENU_MANAGE`. Kiểm tự động ở `MealPlanEvaluator` (cùng công thức FE `menuCalculations.js`); lỗi chặn: `NO_DISHES`, `NOT_BALANCED`, `INCOMPLETE_DATA`, `NORM_NOT_SET` (user chốt 03/11: chưa đặt định mức/thiếu số liệu cũng chặn); cảnh báo: `OVER_BUDGET`, `DISH_REPEATED`, `SESSION_KCAL_OFF`; thông tin `ALLERGENS`.

Định mức theo gam (V58, user chốt 03/11 — NUT-24, NUT-25): `age_group_meal_configs` lưu kcal (khoảng), cơ cấu P:L:G (% năng lượng), gam/trẻ/ngày cho đạm ĐV/TV, béo ĐV/TV, glucid, % năng lượng từng bữa và (V62) `daily_kcal_need` = nhu cầu năng lượng cả ngày. `MealPlanEvaluator`: 4 dòng chính (kcal + P:L:G) cần đủ mới đánh giá ngày (thiếu ⇒ `NORM_NOT_SET`); 5 dòng gam `optional`, đã nhập thì lệch khoảng ⇒ `NOT_BALANCED`; `sessions[]`: `percent` = kcal bữa ÷ `dailyKcalNeed` (chưa nhập nhu cầu ⇒ `percent` null, `status` NA), lệch ⇒ `SESSION_KCAL_OFF` (cảnh báo). Đánh giá tính lại mỗi lần đọc theo định mức hiệu lực — tuần đã công bố cũng hiện theo định mức gam mới.

Thống kê thực đơn (user chốt 03/11): `POST /nutrition/menu-statistics` {ageGroupId, date?, campusId?, children?, items[{session, dishId}]} — `MealPlanController`, đọc HT/PHT, không lưu. Trả bảng thực phẩm × N trẻ (gam/trẻ, lượng ăn, thải bỏ, lượng mua, số đơn vị mua, đơn giá, tiền), tổng tiền, tiền/trẻ, giá suất và `evaluation` cùng dạng bảng kiểm tuần (định mức gam, P:L:G, % năng lượng từng bữa). N: nhập > suất đã xác nhận của điểm trường–ngày (đã chốt thì dùng số chốt, kể cả 0) > sĩ số đang học. Công thức thải bỏ dùng chung `MealDemandCalculator.purchased`.

Bộ thực đơn mẫu (V59, NUT-13, user chốt 03/11 "bộ 4 tuần thật + sao vào tuần"): `/nutrition/menu-templates` (`GET`, `GET /{id}`, `POST`, `PUT /{id}` ghi đè cả lưới, `PATCH /{id}/status`) — `MenuTemplateController`; đọc HT/PHT, ghi `MENU_MANAGE`. Bảng `menu_template_sets` (tên, nhãn mùa, khối, ACTIVE/INACTIVE), `menu_template_items` (tuần 1–4 × thứ 1–5 × bữa × món). Kiểm như kế hoạch tuần: bữa phải là bữa khối đang phục vụ (`MEAL_PLAN_SESSION_INVALID`), món phải đang dùng (`MEAL_PLAN_DISH_INACTIVE`). Sao vào tuần làm ở FE: chép món của khối vào bản nháp kế hoạch, người lập Lưu nháp (BE kiểm lại khi lưu) — không có endpoint copy.

Hàng tươi L4 (V60, NUT-07, NUT-10, NUT-12, plan `docs/plans/active/2026-11-03-day-boarding-l4-fresh-goods.md`): `FreshGoodsController` — `GET /nutrition/fresh-forecasts?campusId` (mới nhất trước, kèm `foodCount`), `POST /fresh-forecasts/preview` (không lưu; `counts` null ⇒ điền sẵn sĩ số đang học, nhóm tuổi không gửi cũng lấy sĩ số đang học), `POST /fresh-forecasts` (201), `GET|DELETE /fresh-forecasts/{id}`, `GET /fresh-checks?campusId&date` (cả hai lần), `PUT /fresh-checks?campusId&date&round=1|2`. Đọc: HT, PHT (preview cũng là đọc); ghi (lưu/xóa dự trù, ghi kiểm nhận): `STOCK_MANAGE` theo điểm trường, thiếu quyền ⇒ 6033. Lượng = Σ món của ngày × suất × gam/trẻ ÷ (1 − thải bỏ%) cho thực phẩm `FRESH`, làm tròn 2 số lẻ theo (ngày, thực phẩm), cùng `MealDemandCalculator` với trừ kho khô. Dự trù: kỳ ≤ 31 ngày (6041), tuần nháp hoặc đã công bố đều tính, thứ Bảy/Chủ nhật/ngày nghỉ không có lượng, tuần chưa lập không có dòng; suất = sĩ số dự kiến × bữa của nhóm tuổi phục vụ. Kiểm nhận lần 1 so dòng của ngày trong bản dự trù lập mới nhất có kỳ chứa ngày; lần 2 (tuần đã công bố, ngày không nghỉ, mọi sổ báo ăn ngày đã xác nhận) so nhu cầu FRESH theo suất đã chốt; chưa đủ điều kiện ⇒ `available=false` + `unavailableReason`, ghi ⇒ 409 6042. Mỗi lần ghi phải đủ một dòng cho từng thực phẩm cần nhận, không thiếu/lạ/trùng (6043); PHT chọn đủ/thiếu/thừa, số thực nhận không bắt buộc; ghi lại cùng (điểm trường, ngày, lần) là ghi đè. Hub `freshReceiptStatus`: `DAY_OFF`, `NO_FORECAST`, `ROUND1_PENDING`, `ROUND1_DONE`, `ROUND2_PENDING`, `DONE`.
Bếp hôm nay + lưu mẫu + ảnh bữa L5 (V61, NUT-03, NUT-16, NUT-21, plan `docs/plans/active/2026-11-03-day-boarding-l5-l6-kitchen-parent.md`): `KitchenRecordsController` (`/nutrition`). `GET /kitchen/day?date&campusId` (bếp: điểm trường theo phân công `KITCHEN_STAFF`; HT/PHT: bắt buộc `campusId`) → món theo bữa × khối chỉ khi tuần PUBLISHED, suất **đã xác nhận** theo lớp × bữa (`normal`, `substitute`; số chờ xác nhận lại và bữa chưa xác nhận không hiện; không tên/ID trẻ), tổng lượng thực phẩm FRESH + DRY (`MealDemandCalculator`, đơn vị gốc, đã cộng thải bỏ). Lưu mẫu (NUT-21): `GET /food-samples?campusId&from&to` (≤ 93 ngày), `POST|PUT /food-samples` (bếp, mã `KITCHEN_RECORD`; sửa khi `RECORDED` và chưa ghi hủy, ngược lại 6061), `POST /food-samples/{id}/dispose` (hủy từ 24 giờ sau `sampledAt`, sớm hơn ⇒ 6062), `POST /food-samples/{id}/approve|approve-disposal` (`FOOD_SAFETY_APPROVE` theo điểm trường, thiếu ⇒ 6069; sai trạng thái ⇒ 6070). Ảnh (NUT-16): `GET|PUT /meal-photo-windows` (PUT chỉ HT, 6072; mỗi bữa `startTime < endTime`, 6064), `POST /meal-photos` (multipart `file` + `session`; ngày = hôm nay, trong khung giờ — chưa cấu hình 6066, ngoài khung 6065; JPEG/PNG/WebP ≤ 10 MB nhận diện theo byte đầu, sai ⇒ 6067; đăng lại thay ảnh của cùng điểm trường–ngày–bữa, xóa tệp cũ sau commit, xóa tệp mới nếu rollback), `GET /meal-photos?campusId&date`, `GET /meal-photos/{id}/content`. Quyền: `KITCHEN_RECORD` (mặc định + cấu hình được: bếp; seed V61), `FOOD_SAFETY_APPROVE` (mặc định HT, cấu hình được PHT, `campusScoped`). Hub `foodSampleStatus`: `PENDING_APPROVAL` (còn lưu mẫu/hủy mẫu chờ duyệt của điểm trường, mọi ngày) · `UP_TO_DATE` · `NONE`, kèm `foodSamplePendingCount`. L6 phụ huynh (`/children/{childId}/week-menu`) hoãn, làm cùng App.

Phần chưa có API (món thay thế dị ứng, lưu mẫu, ảnh bữa, sửa sau công bố P-39) — thiết kế bảng (PROPOSED): `MEAL_NUTRITION_AI_SCHEMA.md` (file thiết kế của dev, chưa có trong repo) §3–§6; nháp AI (`ai_drafts`).

## PENDING
P-26 (mẫu sổ lưu mẫu), P-27 (kiểm thực ba bước). Đã đóng 28/10: P-04..P-10; 01/11: P-24, P-25, P-37.

## Known pitfalls
- Kho khô trừ theo ngày, từng cơ sở, khi suất ngày của cơ sở được xác nhận (NUT-11, đóng P-38); lập/công bố tuần chỉ cảnh báo theo sĩ số dự kiến. Thiếu kho không chặn xác nhận suất. Xác nhận lại suất chỉ ghi phần chênh lệch — đừng trừ lại toàn bộ.
- Sĩ số dự kiến chỉ dùng cho bản dự trù hàng tươi (NUT-07), không dùng để trừ kho.
- Trừ kho (03/11): nhu cầu = Σ (suất đã chốt `normal + substitute` × gam/trẻ theo nhóm tuổi) ÷ (1 − thải bỏ%) cho thực phẩm `DRY`; bỏ qua lô hết hạn trước ngày ăn; `stock_daily_demands` giữ nhu cầu đã áp — `syncDay` chỉ ghi chênh lệch nên gọi lại an toàn. Suất thay thế đang tính theo món chuẩn (chưa có món thay thế dị ứng).
- Lưu lưới và công bố khóa dòng tuần (`findForUpdateByWeekStart`), nếu không công bố có thể ra PUBLISHED với lưới chưa kiểm (test `MealPlanPublishConcurrencyIntegrationTest`).
- Trừ kho sau commit lỗi ⇒ hub `NOT_SYNCED` (so nhu cầu tính lại với `stock_daily_demands`), vá bằng `POST /nutrition/stock/days/{date}/sync` — đừng để hub hiện `NOTHING_TO_DEDUCT` khi thiếu dòng nhu cầu.
- Lưới kế hoạch tuần sửa tại chỗ (`MealPlanWeek.changeDays`): xóa rồi chèn lại cùng khóa trong một flush sẽ vỡ khóa duy nhất vì Hibernate chèn trước khi xóa.
- Record lồng trong DTO phải có tên duy nhất toàn app (springdoc gộp schema theo tên đơn giản): `Line`, `Totals` đã có ở `MealCountResponse`; hàng tươi dùng tiền tố `Fresh*` (`FreshForecastCount`, `FreshForecastLine`, `FreshCheckRound`, `FreshCheckLine`...).
- Công thức nhu cầu theo suất chỉ có một nơi: `MealDemandCalculator` (kho khô DRY theo suất chốt, dự trù FRESH theo sĩ số dự kiến, kiểm nhận lần 2 FRESH theo suất chốt). Đừng sao chép lại công thức vào service khác — lệch làm hàng tươi và kho khô tính khác nhau.
- Ghi đè kiểm nhận (`FreshCheck.record`) sửa dòng tại chỗ theo thực phẩm; xóa hết rồi chèn lại cùng khóa (kiểm, thực phẩm) trong một flush sẽ vỡ khóa duy nhất vì Hibernate chèn trước khi xóa. Ghi kiểm nhận khóa dòng `campuses` FOR UPDATE để bấm lưu hai lần không đụng khóa duy nhất.
- Sĩ số đang học của bản dự trù lấy từ `ClassroomService.countActiveChildren` (module meals không inject repository organization — ArchUnit) tại ngày bắt đầu kỳ; chỉ lớp năm học đang hoạt động của điểm trường.
- Xóa bản dự trù không xóa lịch sử kiểm nhận (`fresh_checks.forecast_id` về null, `fresh_check_lines.expected_qty` đã chụp); kiểm nhận lần 1 đã ghi vẫn hiện theo dòng đã lưu dù sau đó có bản dự trù mới.
- Số version Flyway không đoán trước được khi nhiều luồng cùng làm trên một nhánh: kiểm `ls db/migration` ngay trước khi tạo file; hai file cùng version làm Flyway chặn khởi động, bản stale trong `target/classes` còn giữ file cũ sau khi đổi tên.
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
