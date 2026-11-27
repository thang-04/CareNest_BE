---
title: Sửa ngoại lệ một ngày của thực đơn tuần đã công bố
status: in-progress
owner: Thắng
jira: G94-414 / BE-FEAT-60
branch: feature/G94-414-BE-FEAT-60-meal-plan-amend
modules: [nutrition]
rules: [NUT-08, P-39, P-38]
risk: high
created: 2026-11-18
updated: 2026-11-18
---
# Sửa ngoại lệ một ngày của thực đơn tuần đã công bố

- Ngày: 2026-11-18
- Trạng thái: user duyệt thiết kế và spec 18/11; đang làm
- Module: nutrition (`meals`) — owner Thắng
- Luật liên quan: NUT-08, P-39 (sửa lại theo spec này)

## 1. Bối cảnh

Hiện công bố tuần (`POST /nutrition/meal-plans/{weekStart}/publish`) khóa hẳn thực đơn: `saveMealPlan` ném `MEAL_PLAN_PUBLISHED`, không có API sửa hay hủy công bố. Khi bất khả kháng (NCC hết hàng, thực phẩm hỏng) thực đơn phụ huynh xem và kho trừ theo món không còn đúng; chỉ sửa được bằng tay trong DB.

P-39 (02/11) cho phép sửa ngoại lệ nhưng chưa làm. User chốt lại 18/11:

- PHT phụ trách dinh dưỡng toàn quyền: người có `MENU_MANAGE` sửa thẳng, **không cần duyệt**, bỏ quyền ngoại lệ `MENU_PUBLISHED_EDIT`.
- Sửa **cả ngày** (danh sách món + ghi chú), không giới hạn đổi một món.
- Bắt buộc lý do, lưu lịch sử thay đổi.
- Cân đối dinh dưỡng khi sửa ngoại lệ: **chỉ cảnh báo, không chặn**.

## 2. Luật

| # | Luật |
|---|------|
| R1 | Chỉ người có `MENU_MANAGE` gọi được; thiếu quyền ⇒ `FORBIDDEN`. |
| R2 | Chỉ tuần `PUBLISHED`. Tuần `DRAFT` ⇒ `MEAL_PLAN_NOT_PUBLISHED` (dùng `PUT /meal-plans/{weekStart}` như cũ). |
| R3 | `date` phải thuộc tuần (thứ Hai – thứ Sáu của `weekStart`) ⇒ dùng `MEAL_PLAN_DATE_INVALID` sẵn có. |
| R4 | `date > hôm nay`: sửa được. `date == hôm nay`: chỉ khi giờ hiện tại < giờ chốt. `date < hôm nay` hoặc hôm nay đã qua giờ chốt ⇒ `MEAL_PLAN_DAY_LOCKED`. |
| R5 | Giờ chốt lấy từ `attendanceService.getActiveYearDayInfo(date).cutoff()` (cùng nguồn `MealBookReminderServiceImpl`). Ngày không phải ngày học hoặc không có giờ chốt ⇒ coi như khóa với hôm nay (`MEAL_PLAN_DAY_LOCKED`); ngày tương lai không cần giờ chốt. |
| R6 | Không đổi cờ ngày nghỉ (`off`). Ngày đang là ngày nghỉ ⇒ `MEAL_PLAN_DAY_LOCKED`. |
| R7 | `reason` bắt buộc, sau trim dài 10–500 ký tự ⇒ sai thì `MEAL_PLAN_REASON_REQUIRED`. |
| R8 | Món kiểm như lưu nháp: món `ACTIVE`, bữa hợp lệ với khối (`MEAL_PLAN_DISH_INACTIVE`, `MEAL_PLAN_SESSION_INVALID` sẵn có). Dùng lại logic của `toDays`. |
| R9 | Chạy `MealPlanEvaluator` cho ngày đó; issue `BLOCKING` **không chặn**, chỉ trả về trong response và lưu vào lịch sử. |
| R10 | Thực đơn sau sửa giống hệt trước (món, thứ tự, ghi chú) ⇒ `MEAL_PLAN_AMEND_NO_CHANGE`, không ghi lịch sử, không báo. |
| R11 | Tuần vẫn giữ `PUBLISHED`, `publishedAt`/`publishedBy` không đổi. |

## 3. Dữ liệu

Migration `V{next}__create_meal_plan_day_revisions.sql` (số tiếp theo lúc làm; hiện cuối là V143):

```sql
CREATE TABLE meal_plan_day_revisions (
    id              BIGSERIAL PRIMARY KEY,
    week_id         BIGINT       NOT NULL REFERENCES meal_plan_weeks (id),
    menu_date       DATE         NOT NULL,
    before_snapshot JSONB        NOT NULL,
    after_snapshot  JSONB        NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    issues_snapshot JSONB        NOT NULL DEFAULT '[]'::jsonb,
    changed_by      BIGINT       NOT NULL,
    changed_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_meal_plan_day_revisions_week_date ON meal_plan_day_revisions (week_id, menu_date);
```

(Kiểu cột khóa/thời gian khớp `meal_plan_weeks` khi viết migration.)

Snapshot ngày (jsonb): `{ "note": "...", "items": [ { "session", "block", "dishId", "dishName" } ] }` theo thứ tự hiển thị. Lưu `dishName` để lịch sử đọc được kể cả khi món đổi tên sau này.

- Entity `MealPlanDayRevision`: chỉ tạo mới; không setter sửa, không xóa.
- Repository `MealPlanDayRevisionRepository.findByWeekIdOrderByChangedAtDesc`.
- `MealPlanWeek.amendPublishedDay(MealPlanDay day, Long userId)`: ném `MEAL_PLAN_NOT_PUBLISHED` nếu chưa công bố, giữ `off` cũ, gọi `replaceItems` + cập nhật ghi chú, set `updatedBy`. Không đổi `status`.

## 4. API

### 4.1 Sửa ngày

`PUT /nutrition/meal-plans/{weekStart}/days/{date}/amend` — `@PreAuthorize("@permissionGuard.has('MENU_MANAGE')")`

Body:

```json
{ "note": "string|null", "items": [ { "session": "LUNCH", "block": "NURSERY", "dishId": 12 } ], "reason": "NCC báo hết cá basa" }
```

Trả về `MealPlanResponse` của tuần (issues gồm cảnh báo cân đối của ngày vừa sửa).

### 4.2 Lịch sử

`GET /nutrition/meal-plans/{weekStart}/revisions` — `hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')` (giống xem thực đơn).

Trả về danh sách, mới nhất trước: `id, menuDate, before, after, reason, issues, changedBy, changedByName, changedAt`. Tuần chưa có ⇒ `[]`.

### 4.3 Thay đổi response tuần

`MealPlanResponse` ngày thêm `canAmend` (boolean) = có `MENU_MANAGE` ∧ tuần `PUBLISHED` ∧ ngày không nghỉ ∧ đạt R4/R5. Thêm `revisionCount` của tuần để giao diện hiện nút lịch sử.

## 5. Luồng `amendMealPlanDay` (một transaction)

1. `checkCanManage`, `checkMonday`, kiểm `date` thuộc tuần.
2. `weekRepository.findForUpdateByWeekStart(weekStart)` (khóa hàng, hai người sửa cùng lúc chạy lần lượt); không có ⇒ `MEAL_PLAN_NOT_PUBLISHED`.
3. Kiểm R2, R4–R7.
4. Dựng `MealPlanDay` mới từ request (logic `toDays`, R8).
5. `before = snapshot(week.day(date))`; `week.amendPublishedDay(...)`; `after = snapshot(...)`; nếu `before == after` ⇒ R10.
6. `evaluate` cho tuần, lấy issues của `date` (R9).
7. Lưu `MealPlanDayRevision`, `saveAndFlush` tuần.
8. Với mỗi điểm trường có `MealCount` đã xác nhận của `date` ⇒ `stockLedgerService.syncDay(campusId, date)` (giống `syncConfirmedDays`, giới hạn một ngày).
9. Đăng ký thông báo chạy sau commit (`TransactionSynchronization.afterCommit` hoặc `@TransactionalEventListener(AFTER_COMMIT)` — theo mẫu đang dùng trong repo). Lỗi gửi thông báo chỉ log, không rollback.

## 6. Thông báo

- `NotificationType.MENU_CHANGED` mới, tham số `ngay` (dd/MM), `lyDo`.
- Người nhận: phụ huynh của trẻ đang học (giống `notifyParents`) + tài khoản vai trò `KITCHEN_STAFF` đang hoạt động.
- Cần thêm mẫu nội dung cho `MENU_CHANGED` theo cách `MENU_PUBLISHED` đang khai báo (rule/template thông báo).

## 7. Mã lỗi mới (dải 60xx, lấy số tiếp theo còn trống)

| Mã | HTTP | Nội dung |
|----|------|----------|
| `MEAL_PLAN_NOT_PUBLISHED` | 409 | Thực đơn tuần chưa công bố, sửa ở bản nháp |
| `MEAL_PLAN_DAY_LOCKED` | 409 | Ngày {0} đã qua giờ chốt hoặc là ngày nghỉ, không sửa được |
| `MEAL_PLAN_REASON_REQUIRED` | 400 | Cần nhập lý do sửa (10–500 ký tự) |
| `MEAL_PLAN_AMEND_NO_CHANGE` | 400 | Thực đơn ngày {0} không có thay đổi |

## 8. Test

Integration (`MealPlanIntegrationTest` hoặc file mới cùng gói), `Clock` cố định:

1. Sửa ngày mai ⇒ thành công, món đổi, 1 dòng lịch sử đúng before/after/lý do/người sửa, tuần vẫn `PUBLISHED`.
2. Sửa hôm nay trước giờ chốt ⇒ thành công; sau giờ chốt ⇒ `MEAL_PLAN_DAY_LOCKED`.
3. Sửa hôm qua ⇒ `MEAL_PLAN_DAY_LOCKED`.
4. Ngày nghỉ ⇒ `MEAL_PLAN_DAY_LOCKED`.
5. Tuần nháp ⇒ `MEAL_PLAN_NOT_PUBLISHED`.
6. Không `MENU_MANAGE` ⇒ `FORBIDDEN`.
7. Lý do rỗng / 9 ký tự ⇒ `MEAL_PLAN_REASON_REQUIRED`.
8. Món ngừng dùng ⇒ `MEAL_PLAN_DISH_INACTIVE`.
9. Không thay đổi ⇒ `MEAL_PLAN_AMEND_NO_CHANGE`, không có dòng lịch sử.
10. Ngày thiếu cân đối ⇒ vẫn lưu, response và lịch sử có issue `BLOCKING`.
11. Ngày đã xác nhận suất ⇒ sổ kho của ngày tính lại theo món mới.
12. Thông báo `MENU_CHANGED` tới phụ huynh + bếp; lỗi gửi không rollback.
13. `GET /revisions` trả đúng thứ tự mới nhất trước.
14. Hai request song song cùng tuần ⇒ cả hai thành công lần lượt, 2 dòng lịch sử (theo mẫu `MealPlanPublishConcurrencyIntegrationTest`).

Unit: `canAmend` theo R4/R5 (trước/sau giờ chốt, không có giờ chốt, ngày nghỉ).

## 9. Tài liệu cần sửa

- `docs/business/BUSINESS_RULES.md`: NUT-08 và P-39 — người sửa là PHT có `MENU_MANAGE`, không cần duyệt; cân đối chỉ cảnh báo khi sửa ngoại lệ; ghi "user chốt 18/11".
- `docs/business/flows/meal-management-permissions.md`: bỏ dòng `MENU_PUBLISHED_EDIT`, cập nhật dòng "Update (ngoại lệ)" sang `MENU_MANAGE`.
- `docs/business/flows/meal-management.md`: bước sửa sau công bố.
- `docs/modules/nutrition.md`: bỏ "sửa sau công bố P-39" khỏi phần chưa có API, thêm 2 endpoint.

## 10. Ngoài phạm vi

- Giao diện web (form sửa ngày, xem lịch sử) — spec FE riêng.
- Hủy công bố / mở lại cả tuần.
- Sửa ngày đã qua, đổi ngày nghỉ.
- Bước duyệt.

## Progress log

- 2026-11-18 — Jira G94-414, nhánh `feature/G94-414-BE-FEAT-60-meal-plan-amend` (worktree từ `dev`). Đã code BE + test + tài liệu; chưa commit.
- Lệch so với bản nháp ở trên (chốt khi làm):
  - Migration là `V145__create_meal_plan_day_revisions.sql` (V144 đã có trên `dev`); cột tách `before_note`/`before_items`, `after_note`/`after_items`, `issues` thay vì một snapshot jsonb, dùng `JsonListConverter` sẵn có. Cùng migration thêm `MENU_CHANGED` vào `ck_notifications_type`.
  - R5: "sửa được" = ngày học (`schoolDay`) và chưa khóa (`!baseLocked`) theo `AttendanceService.getActiveYearDayInfo` — cùng nguồn giờ chốt/ngày học với điểm danh; ngày không phải ngày học cũng bị khóa.
  - Thông báo `MENU_CHANGED` ghi trong cùng transaction như `MENU_PUBLISHED` (notification lưu DB, push đi qua outbox) — không cần `afterCommit`. Mẫu câu khai báo ở `NotificationRuleCatalog`, bắt buộc (HT không tắt được), biến `ngay`, `lyDo`; nội dung tự cắt 500 ký tự.
  - Mã lỗi: `MEAL_PLAN_NOT_PUBLISHED` 6091, `MEAL_PLAN_DAY_LOCKED` 6092, `MEAL_PLAN_REASON_REQUIRED` 6093, `MEAL_PLAN_AMEND_NO_CHANGE` 6094.
  - DTO lịch sử dùng record lồng `RevisionDay`, `RevisionItem`, `RevisionIssue` (tránh trùng tên schema `Item`/`Issue` trong OpenAPI).
- Test: `MealPlanAmendIntegrationTest` (5 ca: sửa ngày tới + lịch sử + báo bếp; cân đối chỉ cảnh báo; khóa theo giờ chốt/ngày qua/ngày nghỉ; từ chối tuần nháp/thiếu quyền/thiếu lý do/không đổi/món ngừng dùng/ngoài tuần; tính lại kho ngày đã chốt suất). Chưa có ca hai request song song riêng cho sửa ngoại lệ — dùng chung khóa `findForUpdateByWeekStart` đã có test với công bố.
- Còn lại: FE (màn hình sửa ngày + xem lịch sử) — task riêng.
