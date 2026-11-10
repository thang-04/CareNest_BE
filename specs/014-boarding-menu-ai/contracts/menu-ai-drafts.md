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
- Tuần đã PUBLISHED ⇒ 409 `6035`. Trả 201 kèm `MenuAiDraftResponse` (kể cả `NO_FEASIBLE`).

## `MenuAiDraftResponse`
```json
{ "id": 12, "kind": "MENU_WEEKLY", "block": "KINDERGARTEN", "weekStart": "2026-11-16",
  "targetDate": null, "targetSession": null, "fromDishId": null,
  "status": "PENDING_REVIEW", "source": "AI", "fallbackReason": null,
  "model": "gemini-…", "promptVersion": "menu/weekly_v1", "createdAt": "…",
  "score": { "ai": 84, "baseline": 78, "breakdown": { "norm": 40, "cost": 18, "variety": 19, "warnings": 7 } },
  "days": [ { "date": "2026-11-16", "off": false, "reason": "…",
              "items": [ { "session": "LUNCH", "dishId": 31, "dishName": "…", "dishType": "MAIN" } ] } ],
  "issues": [ { "severity": "WARNING", "code": "…", "date": "…", "message": "…" } ],
  "checks": [ { "code": "NORM_BALANCE", "status": "PASS", "label": "…", "detail": "5/5 ngày đạt…", "rules": ["NUT-24"] } ] }
```
- `checks`: bảng kiểm tiêu chí của phương án được chọn (`PASS`/`WARN`/`INFO`/`FAIL`), danh sách mã và căn cứ: `docs/quality/ai-menu-quality-criteria.md`. Issue có thêm mã `DRY_STOCK_OUT` (WARNING): món dùng thực phẩm kho khô đang hết ở mọi điểm trường.
- `MENU_DAY_FIX`: `days` có đúng một ngày, một item = món thay (`dishId`); rỗng khi `NO_FEASIBLE`; `checks` nói về ngày đó. `MENU_DAILY`: `weekStart` null, `days` một phần tử, `date` null.

## `GET /nutrition/menu-ai-drafts/{id}` — 200 | 404 `6080`.
## `POST /nutrition/menu-ai-drafts/{id}/dismiss` — 200; trạng thái khác `PENDING_REVIEW` ⇒ 409 `6081`.
## `POST /nutrition/menu-ai-drafts/{id}/apply`
- `MENU_WEEKLY`, `MENU_DAY_FIX`: 200 trả `MealPlanResponse` của tuần sau khi ghi. Đã `APPLIED` ⇒ 200 trả tuần hiện tại (không ghi lại). `DISMISSED`/`NO_FEASIBLE` ⇒ 409 `6081`; tuần PUBLISHED ⇒ 409 `6035`. Dữ liệu đổi (fingerprint khác) và món ngừng dùng/bữa không còn phục vụ/phát sinh `BLOCKING` ⇒ 409 `6082`, `data` = `[{ field: ngày ISO hoặc "week", message }]`.
- `MENU_DAY_FIX`: chỉ đổi đúng ô; món cũ đã bị sửa tay khỏi ô ⇒ 409 `6082`; kiểm lại lỗi chặn của ngày đó trên dữ liệu mới.
- `MENU_DAILY` ⇒ 409 `6083` (dùng `mark-used`).

## `POST /nutrition/menu-ai-drafts/{id}/mark-used`
- Chỉ `MENU_DAILY` (loại khác ⇒ 409 `6083`): đánh dấu `APPLIED`, trả `MenuAiDraftResponse`; gọi lại khi đã `APPLIED` trả như cũ. Web điền form thực đơn mẫu từ `days[0]` rồi lưu qua `/nutrition/menu-templates` (kiểm tra của mẫu vẫn áp dụng).

## Tác động client
FE `CareNest_FE`: `AiWeekComposer`, `AiTemplateComposer`, gợi ý thay món trong `WeeklyMenuPage` đổi từ contract demo `/menu/ai-suggestions` sang API này (plan FE riêng, cần user cho phép sửa repo FE). APP: không.
