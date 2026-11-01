# API contract — feature 004

Prefix `/api/v1` như hiện hành, bọc `ResponseJson`. Tác động: CareNest_FE (education-plan); CareNest_APP không dùng module này.

## Mới

### POST /education-plans/goals/{planId}/versions
Tạo phiên bản n+1 chép từ `planId`. Header `X-Edit-Session`, `X-Plan-Revision` (revision của bản nguồn), `X-Request-Id` như POST tạo kế hoạch (EDU-10). Trả `YearPlanDetailResponse` (201) của phiên bản mới, người gọi giữ phiên sửa.
- 403 `YEAR_PLAN_CREATE_FORBIDDEN` (7003): không phải PHT/TT nhóm tuổi.
- 409 `YEAR_PLAN_VERSION_SOURCE_INVALID` (7027): nguồn chưa ban hành hoặc không phải phiên bản ban hành mới nhất.
- 409 `YEAR_PLAN_OPEN_VERSION_EXISTS` (7028, `{0}` = mã phiên bản đang soạn).
- Cùng `X-Request-Id` gửi lại ⇒ trả phiên bản đã tạo.

### GET /education-plans/goals/{planId}/changes?fromPlanId=
So sánh `planId` với `fromPlanId` (mặc định `sourcePlanId`) cùng chuỗi theo lineage. Quyền xem cả hai theo EDU-06. 400 `YEAR_PLAN_VERSION_COMPARE_INVALID` (7029) khi khác chuỗi hoặc không có nguồn. Trả `YearPlanChangesResponse` (data-model §4).

### PUT /education-plans/class-docs/week/{docKey}/adjustments
Body `{ "adjustments": { "2026-09-07": "…" } }` (chỉ ngày Thứ 2–6 của tuần; ≤4000 ký tự/ngày). Mọi trạng thái; chỉ giáo viên lớp. Trả node tài liệu. Lỗi: 7101 không có tài liệu, 7103 không phải GV lớp, 7123 `CLASS_DOC_ADJUSTMENT_INVALID` ngày ngoài tuần/quá dài.

## Đổi

- `YearPlanSummaryResponse` + `versionNo`, `sourcePlanId`, `current` (boolean: phiên bản ban hành mới nhất).
- `YearPlanDetailResponse` + `versionNo`, `sourcePlanId`, `current`, `versions[]` (VersionSummary, theo phạm vi xem), `permissions.canCreateVersion`.
- Class doc node + `yearPlanVersion` (số), `sourceDocKey` (hoặc vắng), `adjustments` (object), `superseded` (boolean).
- `PUT /class-docs/{kind}/{docKey}`: body nhận `sourceDocKey` khi tạo; khóa tuần phiên bản ≥2 `wp_<lớp>_<thứ Hai>_<yearPlanId>`; 409 `YEAR_PLAN_SUPERSEDED` (7122) khi kế hoạch của tài liệu đã bị thay thế; 400 7102 khi `sourceDocKey` sai lớp/loại/chuỗi.
- `POST /class-docs/{kind}/{docKey}/actions` submit: 409 7122 khi đã thay thế; 409 `CLASS_DOC_VERSION_FIX_REQUIRED` (7121, `{0}` = số mục) khi bản chép còn ô đỏ (research R5). approve/reject không đổi.
- `POST /education-plans/goals` (tạo v1): 409 7002 `YEAR_PLAN_EXISTS` khi chuỗi đã có (không đổi hành vi).
