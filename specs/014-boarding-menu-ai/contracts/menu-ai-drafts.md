# Contract — `/nutrition/menu-ai-drafts`

Tất cả cần `MENU_MANAGE` (thiếu ⇒ 403 `1101`). Bọc `ResponseJson` như API hiện có. Controller riêng dưới `/nutrition` như `MealPlanController` (`controller/meals/MealPlanController.java:33`); không đặt dưới `/meal-plans/` vì trùng `/meal-plans/{weekStart}`.

## `POST /nutrition/menu-ai-drafts`
```json
{ "kind": "MENU_WEEKLY", "block": "KINDERGARTEN", "weekStart": "2026-11-16",
  "season": "Mùa đông", "wishes": "nhiều rau xanh",
  "targetDate": null, "targetSession": null, "fromDishId": null }
```
- `season` ≤ 50, `wishes` ≤ 300 ký tự (vượt ⇒ 400 `9001`). `MENU_WEEKLY`/`MENU_DAY_FIX` cần `weekStart` (thiếu ⇒ 400 `9001`, ô `weekStart`), là thứ Hai (sai ⇒ 400 `6034` như kế hoạch tuần); `MENU_DAY_FIX` cần `targetDate`, `targetSession`, `fromDishId`. Thiếu `targetDate`/`targetSession`/`fromDishId` ⇒ 400 `9001` theo ô. `MENU_DAILY` không cần tuần (định mức hiệu lực hôm nay).
- `MENU_DAY_FIX`: tuần phải đã có kế hoạch (không ⇒ 400 `9001`, ô `weekStart`), `targetDate` trong tuần (sai ⇒ 400 `6036`), không phải ngày nghỉ, `fromDishId` phải có trong bữa `targetSession` của khối ngày đó (không ⇒ 400 `9001`, ô `fromDishId`). Ứng viên: món cùng loại, chưa có trong bữa đó.
- Tuần đã PUBLISHED ⇒ 409 `6035`. Trả 201 kèm `MenuAiDraftResponse` (kể cả `NO_FEASIBLE` và nháp `feasible=false`).

## `MenuAiDraftResponse`
```json
{ "id": 12, "kind": "MENU_WEEKLY", "block": "KINDERGARTEN", "weekStart": "2026-11-16",
  "targetDate": null, "targetSession": null, "fromDishId": null,
  "status": "PENDING_REVIEW", "feasible": true, "source": "AI", "fallbackReason": null,
  "model": "gemini-…", "promptVersion": "menu/weekly_v1", "createdAt": "…",
  "score": { "ai": 84, "baseline": 78, "breakdown": { "norm": 40, "cost": 18, "variety": 19, "warnings": 7 } },
  "days": [ { "date": "2026-11-16", "off": false, "reason": "…",
              "items": [ { "session": "LUNCH", "dishId": 31, "dishName": "…", "dishType": "MAIN" } ] } ],
  "issues": [ { "severity": "WARNING", "code": "…", "date": "…", "message": "…" } ],
  "checks": [ { "code": "NORM_BALANCE", "status": "PASS", "label": "…", "detail": "5/5 ngày đạt…", "rules": ["NUT-24"] } ] }
```
- `checks`: bảng kiểm tiêu chí của phương án được chọn (`PASS`/`WARN`/`INFO`/`FAIL`), danh sách mã và căn cứ: `docs/quality/ai-menu-quality-criteria.md`. Issue có thêm mã `DRY_STOCK_OUT` (WARNING): món dùng thực phẩm kho khô đang hết ở mọi điểm trường.
- `checks` có thêm dòng `KCAL_REACH` (`FAIL`, thêm 11/11/2026) chỉ khi còn ngày chưa đạt định mức và món ứng viên chứng minh được là không tới được khoảng năng lượng định mức của một nhóm tuổi (`detail` nêu nhóm tuổi, mức cao/thấp nhất đạt được, mức định mức). FE hiện `detail` dưới bảng sửa tuần. Không đổi schema.
- `feasible` (thêm 11/11/2026): `true` khi nháp có lưới món và không còn issue `BLOCKING`. `MENU_WEEKLY`/`MENU_DAILY` mà cả AI lẫn phương án theo quy tắc không đạt định mức ⇒ vẫn `PENDING_REVIEW`, `source=RULE_BASED`, `days` đủ món, `feasible=false`, `issues` có `BLOCKING` theo ngày, check `NORM_BALANCE=FAIL`: dùng làm điểm bắt đầu để sửa tay, tuần chưa công bố được (NUT-09). `NO_FEASIBLE` chỉ còn khi không dựng được lưới (ô không có món ứng viên, khối không có nhóm tuổi, tuần nghỉ hết) hoặc `MENU_DAY_FIX` không có món thay đạt; khi đó `feasible=false`, `days` không có món.
- `MENU_DAY_FIX`: `days` có đúng một ngày, một item = món thay (`dishId`); rỗng khi `NO_FEASIBLE`; `checks` nói về ngày đó. `MENU_DAILY`: `weekStart` null, `days` một phần tử, `date` null.

- Thêm 11/11/2026 (sửa món trên nháp): `days[].items[].slot` = khóa ô của món (vd. `LUNCH.2`); `slots` = `[{ key, session, index, dishTypes[] }]` là các hàng của bảng gợi ý theo thứ tự bữa rồi thứ tự ô, suy từ chính lưới đã lưu (rỗng khi `NO_FEASIBLE`); `editedCells` = số ô người duyệt đã sửa so với gợi ý lúc dùng (0 khi chưa dùng hoặc dùng nguyên gợi ý).

## `GET /nutrition/menu-ai-drafts/{id}` — 200 | 404 `6091`.
## `GET /nutrition/menu-ai-drafts/{id}/candidates`
- Chỉ `MENU_WEEKLY` đang `PENDING_REVIEW` (loại khác ⇒ 409 `6094`; trạng thái khác ⇒ 409 `6092`; tuần đã công bố ⇒ 409 `6035`). 200: `{ slots: [{ key, session, index, dishTypes[], dishes: [{ dishId, dishName, dishType, seasonal, familiar, outOfStock }] }] }`; món theo thứ tự ưu tiên của hệ thống, đọc trên dữ liệu hiện tại. Không trả số liệu dinh dưỡng.
## `POST /nutrition/menu-ai-drafts/{id}/evaluate` — chấm thử, không lưu, không gọi model
- Body `{ days: [{ date, items: [{ slot, dishId }] }] }`: đủ đúng các ngày phục vụ của tuần, mỗi ô của khung đúng một món; món phải thuộc ứng viên của ô (hoặc là món gợi ý gốc của chính ô đó), không lặp trong một bữa. Sai ⇒ 400 `9001`, `data` = `[{ field: "days[<ngày>].<ô>" | "days[<ngày>]" | "days", message }]`.
- 200: `{ feasible, editedCells, score: { total, breakdown }, evaluations, issues, checks }`; `evaluations` cùng dạng `MealPlanResponse.evaluations` (chỉ khối của nháp). Cùng evaluator, cảnh báo kho khô, điểm và bảng kiểm với lúc tạo nháp; `editedCells > 0` ⇒ dòng `VALUE_GATE` ghi người duyệt đã sửa N ô. Điều kiện loại/trạng thái như `candidates`.
## `POST /nutrition/menu-ai-drafts/{id}/dismiss` — 200; trạng thái khác `PENDING_REVIEW` ⇒ 409 `6092`.
## `POST /nutrition/menu-ai-drafts/{id}/apply`
- `MENU_WEEKLY`, `MENU_DAY_FIX`: 200 trả `MealPlanResponse` của tuần sau khi ghi. Đã `APPLIED` ⇒ 200 trả tuần hiện tại (không ghi lại). `DISMISSED`/`NO_FEASIBLE` ⇒ 409 `6092`; tuần PUBLISHED ⇒ 409 `6035`. Dữ liệu đổi (fingerprint khác) và món ngừng dùng/bữa không còn phục vụ/phát sinh `BLOCKING` ⇒ 409 `6093`, `data` = `[{ field: ngày ISO hoặc "week", message }]`. Nháp `feasible=false` dùng được: ghi vào tuần DRAFT, không kiểm lại `BLOCKING` (vốn đã có), vẫn kiểm món ngừng dùng/bữa không còn phục vụ; công bố tuần vẫn bị chặn `6039`.
- Body tùy chọn (thêm 11/11/2026), cùng dạng `evaluate`, chỉ cho `MENU_WEEKLY` đang `PENDING_REVIEW`: ghi lưới người duyệt đã sửa thay cho lưới gợi ý, lưu `editedCells`; kiểm lưới như `evaluate` (400 `9001`), món ngừng dùng/bữa không còn phục vụ ⇒ 409 `6093`; không chặn theo `BLOCKING` (công bố vẫn chặn `6039`). Không body ⇒ hành vi cũ. `MENU_DAY_FIX` kèm body ⇒ 409 `6094`.
- `MENU_DAY_FIX`: chỉ đổi đúng ô; món cũ đã bị sửa tay khỏi ô ⇒ 409 `6093`; kiểm lại lỗi chặn của ngày đó trên dữ liệu mới.
- `MENU_DAILY` ⇒ 409 `6094` (dùng `mark-used`).

## `POST /nutrition/menu-ai-drafts/{id}/mark-used`
- Chỉ `MENU_DAILY` (loại khác ⇒ 409 `6094`): đánh dấu `APPLIED`, trả `MenuAiDraftResponse`; gọi lại khi đã `APPLIED` trả như cũ. Web điền form thực đơn mẫu từ `days[0]` rồi lưu qua `/nutrition/menu-templates` (kiểm tra của mẫu vẫn áp dụng).

## Tác động client
FE `CareNest_FE`: `AiWeekComposer`, `AiTemplateComposer`, gợi ý thay món trong `WeeklyMenuPage` đổi từ contract demo `/menu/ai-suggestions` sang API này (plan FE riêng, cần user cho phép sửa repo FE). APP: không.
