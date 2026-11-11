# Thiết kế — hộp thoại gợi ý thực đơn tuần dạng bảng, kéo thả món, chấm lại ngay

Ngày: 2026-11-11 · Người duyệt: user (Thangnd04), duyệt bản mẫu 7:3 trong chat 11/11/2026 · Trạng thái: chờ user soát spec.
Liên quan: plan `docs/plans/active/2026-11-10-boarding-menu-ai.md`, contract `specs/014-boarding-menu-ai/contracts/menu-ai-drafts.md`, ADR-0024, thiết kế cổng giá trị `2026-11-09-boarding-ai-quality-gate-design.md`.

## 1. Vấn đề

User thử màn "AI gợi ý" (11/11/2026) và phản hồi: "UI quá xấu và load dữ liệu món ăn gợi ý chưa chuẩn, thiết kế lại UI chuẩn theo table thực đơn và 1 bên có thể có 1 phần kéo thả các món ăn vào".

Hiện trạng (`CareNest_FE-menu-ai`, `MenuAiDraftResult.jsx`): mỗi ngày một dòng, món của một bữa gộp thành chuỗi chữ; không theo hàng loại món, không có kcal, không sửa được trước khi dùng.

Khảo sát user (4 câu, 11/11/2026):

| Câu hỏi | User chọn |
| --- | --- |
| Chỗ đặt kết quả | Giữ hộp thoại, làm rộng |
| Kéo thả | Trong hộp thoại, chia 7:3, phần 3 là danh sách món kéo thả vào bảng |
| "Dữ liệu chưa chuẩn" là gì | Món không đúng hàng loại món; tên món là dữ liệu test; món được chọn không hợp lý |
| Sau khi đổi món | Chấm lại ngay khi đổi món |

## 2. Phạm vi

Trong phạm vi (một plan triển khai, BE + FE):

1. Hộp thoại gợi ý **cả tuần** (`MENU_WEEKLY`) dạng bảng 7:3 có kéo thả và chấm lại.
2. BE: trả vị trí ô của từng món, danh sách món ứng viên theo ô, API chấm thử, dùng nháp kèm lưới đã sửa.
3. Mẫu ngày (`MENU_DAILY`) và thay một món (`MENU_DAY_FIX`): chỉ đổi cách hiển thị sang cùng bảng (một cột), **không** sửa được trong hộp thoại.

Ngoài phạm vi (việc riêng, nêu ở mục 9):

- Món được chọn không hợp lý (các ngày gần giống nhau, 0/5 ngày đạt): lỗi ở cách chọn món, chưa rõ nguyên nhân ⇒ điều tra riêng.
- Tên món `[E2E]…`: dữ liệu thử trong bản sao DB local, không phải lỗi code ⇒ dọn dữ liệu local.
- Kéo thả trên bảng Kế hoạch tuần ngoài hộp thoại AI.

## 3. Bố cục và tương tác (FE)

Hộp thoại kết quả rộng gần kín màn hình (kích thước mới của `Modal`, vd. `full`: rộng `min(1400px, 96vw)`, cao `92vh`, thân cuộn trong hộp thoại). Form nhập mùa/mong muốn trước khi gợi ý giữ nguyên cỡ `md`.

```
┌ Tiêu đề: Gợi ý thực đơn · <khối> · <tuần>                      [x] ┐
│ Thanh trạng thái: nhãn nháp · nguồn · điểm · 4 tiêu chí · Xem 13   │
├──────────────── 7 ────────────────┬────────────── 3 ──────────────┤
│ Bảng: cột = 5 ngày, hàng = bữa ×  │ Món của trường                │
│ loại món; ô = thẻ món + kcal;     │ ô tìm · lọc loại món          │
│ đầu cột: Đạt / N lỗi;             │ danh sách thẻ món kéo được    │
│ hàng cuối: kcal ngày + lỗi đầu    │                               │
├───────────────────────────────────┴───────────────────────────────┤
│ Còn N ngày chưa đạt…                [Bỏ] [Gợi ý lại] [Dùng cho tuần]│
└───────────────────────────────────────────────────────────────────┘
```

**Bảng (trái).** Component mới `MenuAiDraftGrid`, dùng lại class của bảng tuần (`table--grid`, `td-week__dishes`, `td-dish` + `dishTypeClass`, `DishKcal`). Hàng lấy từ `slots` BE trả (mục 4.1): mỗi ô (`slot.key`) một hàng, nhóm theo bữa; nhãn hàng = loại món của ô (`DISH_TYPE_LABELS`, nhiều loại thì nối bằng `/`). Mỗi ô đúng một món. Ngày nghỉ: cột xám, không nhận thả. Đầu cột: ngày + `StatusBadge` "Đạt" hoặc "N lỗi" (đếm issue `BLOCKING` của ngày đó). Hàng cuối "Cả ngày": kcal của ngày từ `evaluations` và câu lỗi chặn đầu tiên nếu có. Lý do của model (`day.reason`) hiện dạng chú thích dưới đầu cột.

**Cột món (phải).** Component mới `MenuAiDishPalette`. Nguồn: `GET …/{id}/candidates` (mục 4.2), không dùng danh sách món đang dùng của FE vì ứng viên đã được BE lọc đủ dữ liệu (NUT-05, NUT-09). Có ô tìm theo tên (lọc tại chỗ, không gọi BE) và nút lọc theo loại món. Bấm một ô trong bảng ⇒ ô đó thành "ô đang chọn", danh sách tự lọc theo ứng viên của ô đó. Thẻ món: tên, kcal, nhãn "hợp mùa" / "trường đã nấu" / "kho khô hết" (chữ, không chỉ màu). Món đã có trong tuần: làm mờ kèm chữ "đã dùng <thứ>", vẫn kéo được.

**Kéo thả.** `@dnd-kit/core` (đã có trong repo, đang dùng ở `components/ui/Sortable.jsx`). Nguồn kéo: thẻ ở cột món và thẻ trong bảng. Đích thả: ô của bảng.

- Thả món từ cột món vào ô ⇒ thay món của ô.
- Thả thẻ trong bảng sang ô khác **cùng hàng** ⇒ hai ô đổi món cho nhau.
- Chỉ thả được khi món thuộc ứng viên của ô đích (cùng `slot.key`). Khi đang kéo: ô hợp lệ có viền nét đứt màu nhấn, ô không hợp lệ mờ và con trỏ `not-allowed`.
- Ngưỡng kéo 6px để không kéo nhầm khi bấm; `DragOverlay` hiện bản sao thẻ; tôn trọng `prefers-reduced-motion`.
- Không dùng chuột: Tab tới ô, Enter/Space chọn ô; Tab sang cột món, Enter trên món ⇒ thay vào ô đang chọn. Cảm biến bàn phím của `@dnd-kit` bật kèm. Mọi thẻ và ô có `aria-label` đầy đủ (ngày, bữa, loại món, tên món).
- Hoàn tác: nút "Hoàn tác" trên thanh trạng thái lùi một bước đổi món; "Về gợi ý gốc" bỏ mọi chỉnh sửa.

**Thanh trạng thái.** Nhãn "Bản nháp – cần người duyệt"; nguồn (`sourceText`) và khi đã sửa thêm "đã sửa N ô"; điểm hiện tại, kèm "gợi ý gốc <điểm>" khi đã sửa; bốn tiêu chí dạng nhãn: `NORM_BALANCE` (x/y ngày), `OVER_BUDGET`, `DISH_REPEATED`, `DRY_STOCK_OUT`; liên kết "Xem 13 tiêu chí" mở phần bảng kiểm đầy đủ (danh sách `checks` + `MealPlanIssueList`) dạng vùng gập dưới bảng. Đang chấm lại: điểm hiện spinner nhỏ + "Đang chấm lại…", bảng vẫn thao tác được.

**Chân hộp thoại.** Câu trạng thái: "Mọi ngày đạt định mức." hoặc "Còn N ngày chưa đạt định mức. Vẫn dùng được để sửa tiếp, chưa công bố được." Nút: Bỏ · Gợi ý lại · **Dùng cho tuần** (nút chính duy nhất). "Dùng cho tuần" khóa khi màn Kế hoạch tuần còn thay đổi chưa lưu (như hiện tại) hoặc khi lần chấm gần nhất lỗi. Đóng/Gợi ý lại khi đã sửa ⇒ hỏi xác nhận bỏ chỉnh sửa.

**Màn nhỏ.** Dưới 1100px: cột món thành ngăn trượt từ phải, mở bằng nút "Chọn món"; bảng chiếm hết ngang, cuộn ngang trong `table-wrap`. Dưới 768px: không kéo thả, chỉ dùng cách chọn ô rồi chọn món.

**Mẫu ngày / thay một món.** Dùng `MenuAiDraftGrid` chế độ chỉ xem (một cột), không có cột món; nút và luồng giữ nguyên.

## 4. Thay đổi BE

Mọi endpoint cần `MENU_MANAGE` như hiện có. Nháp gốc (`output_json`, `score_json`, `issues_json`) **không đổi sau khi tạo** (ADR-0024: giữ để đo giá trị AI).

### 4.1 `MenuAiDraftResponse` — thêm trường (chỉ thêm)

- `days[].items[].slot`: khóa ô, vd. `LUNCH.2` (đã lưu trong `output_json`, hiện chưa trả).
- `slots`: `[{ key, session, index, dishTypes[] }]` theo thứ tự hiển thị. Chỉ có với `MENU_WEEKLY`, `MENU_DAILY`; `MENU_DAY_FIX` trả một ô.
- `editedCells`: số ô khác gợi ý gốc lúc dùng (0 khi chưa dùng hoặc dùng nguyên bản).

`slots` luôn suy từ chính lưới đã lưu (khóa ô trong `output_json` + `dishType` của món đang nằm ở ô), không đọc lại ngữ cảnh: nháp cũ vẫn hiển thị đúng hàng dù khung ô của bộ mẫu đã đổi (đổi khi triển khai 11/11 cho đơn giản). Loại món đầy đủ của ô lấy từ `candidates`.

### 4.2 `GET /nutrition/menu-ai-drafts/{id}/candidates`

Chỉ `MENU_WEEKLY` đang `PENDING_REVIEW` (khác ⇒ 409 `6081`/`6083` như hiện có). Dựng lại ngữ cảnh tuần bằng `MenuAiContextLoader.weekly` với mùa/mong muốn trong `request_json`.

```json
{ "slots": [ { "key": "LUNCH.2", "session": "LUNCH", "index": 1, "dishTypes": ["MAIN"],
               "dishes": [ { "dishId": 31, "dishName": "…", "dishType": "MAIN",
                             "seasonal": true, "familiar": false, "outOfStock": false } ] } ] }
```

Thứ tự món = thứ tự ưu tiên của `MenuContext.candidates`. Không trả số liệu dinh dưỡng (FE lấy kcal từ danh mục món đang có).

### 4.3 `POST /nutrition/menu-ai-drafts/{id}/evaluate` — chấm thử, không lưu

Request: `{ "days": [ { "date": "2026-11-16", "items": [ { "slot": "LUNCH.2", "dishId": 31 } ] } ] }` — đủ mọi ngày phục vụ của tuần, mỗi ô đúng một món.

Kiểm (sai ⇒ 400 `9001` kèm lỗi theo ô, không chấm): ngày thuộc tuần và không nghỉ; khóa ô thuộc khung ô; món thuộc ứng viên của ô; không thiếu, không thừa ô.

Response 200:

```json
{ "feasible": false, "editedCells": 1,
  "score": { "total": 71, "breakdown": { "norm": 30, "cost": 20, "variety": 14, "warnings": 7 } },
  "evaluations": [ … như MealPlanResponse.evaluations của khối … ],
  "issues": [ … ], "checks": [ … ] }
```

Chấm bằng đúng đường của engine: `MenuGrid.evaluate` + cảnh báo kho khô + `MenuScore.score` + `MenuCriteriaReport.build`. Dòng tiêu chí `VALUE_GATE` khi `editedCells > 0` ghi "Người duyệt đã sửa N ô so với gợi ý". Không gọi model, không ghi DB, không cần idempotency. Engine thêm một method công khai trong gói (vd. `MenuSuggestionEngine.assess(ctx, grid)`) trả issues/score/checks; `decide`/`chosen` dùng lại method này để không có hai cách chấm.

Nháp không còn `PENDING_REVIEW` ⇒ 409 `6081`.

### 4.4 `POST /nutrition/menu-ai-drafts/{id}/apply` — nhận lưới đã sửa

Body tùy chọn, cùng dạng 4.3. Không có body ⇒ hành vi hiện tại (dùng nguyên gợi ý). Có body:

- Kiểm như 4.3 (400 `9001`), rồi kiểm món còn dùng/bữa còn phục vụ như hiện có (409 `6082`).
- Không chặn theo lỗi `BLOCKING`: lưới đã sửa có thể chưa đạt, công bố vẫn bị chặn ở kế hoạch tuần (NUT-09, mã `6039`) — cùng quy tắc với nháp `feasible=false` đã chốt 11/11.
- Ghi tuần qua `MealPlanService.saveMealPlan` với lưới đã sửa (chỉ khối của nháp, giữ khối khác và ngày nghỉ như `mergedWeek`).
- Lưu `edited_cells` = số ô khác `output_json`; nháp thành `APPLIED`. Gọi lại khi đã `APPLIED` ⇒ trả tuần hiện tại, không ghi lại (như cũ).

Chỉ áp dụng cho `MENU_WEEKLY`; `MENU_DAY_FIX` gửi body ⇒ 409 `6083` (mã sẵn có cho thao tác sai loại gợi ý).

### 4.5 Schema

Migration mới thêm cột `edited_cells INT NOT NULL DEFAULT 0` vào `menu_ai_drafts`, kèm `CHECK (edited_cells >= 0)`. Không sửa V82. **Số migration:** `origin/dev` đã có `V83__consolidate_boarding_tables.sql` (kiểm 11/11/2026), nên dùng số lớn hơn số lớn nhất trên `dev` tại lúc làm (hiện là V84) và merge `dev` vào nhánh trước khi viết migration.

Lý do có cột: bảng nháp dùng để đo giá trị AI; phải phân biệt "dùng nguyên gợi ý" với "dùng sau khi sửa nhiều ô". Nhãn nguồn (`source`) không đổi; FE hiển thị "đã sửa N ô" để không gắn nhãn AI cho phần người sửa (AI-01, AI-02).

## 5. Luồng dữ liệu (FE)

1. Tạo nháp như hiện tại ⇒ `draft`. Nếu là `MENU_WEEKLY` và `PENDING_REVIEW`: gọi `candidates`.
2. State trong `MenuAiWeekModal` qua hook mới `useMenuAiDraftEditor(draft)`: `cells` (ngày → khóa ô → dishId, khởi tạo từ `draft.days`), `history` (để hoàn tác), `assessment` (ban đầu lấy từ `draft`: điểm, issues, checks; `evaluations` chưa có ⇒ gọi `evaluate` một lần khi mở).
3. Mỗi lần đổi món: cập nhật `cells` ngay (giao diện phản hồi tức thì), rồi gọi `evaluate` sau 400ms không thao tác; chỉ nhận kết quả của yêu cầu mới nhất (bỏ kết quả cũ về muộn).
4. "Dùng cho tuần": `apply` kèm `days` nếu `editedCells > 0`, không kèm nếu chưa sửa; thành công ⇒ nạp lại kế hoạch tuần, đóng hộp thoại.
5. Hàm thuần đặt ở `utils/menu-planning/menuAiDraft.js`: `cellsOf(draft)`, `replaceCell`, `swapCells`, `editedCount(cells, draft)`, `canDrop(slotKey, dishId, candidates)`, `toEvaluateRequest(cells)`.

Service: thêm `getCandidates(id)`, `evaluateDraft(id, days)`, `applyDraft(id, days?)` vào `services/menuAiDraftApi.js`; khai thêm 2 lời gọi `pending` trong `integrationModules.js`.

## 6. Lỗi và trạng thái biên

| Tình huống | Xử lý |
| --- | --- |
| `candidates` lỗi | Bảng vẫn hiện, cột món báo lỗi kèm nút "Thử lại"; vẫn dùng được gợi ý gốc |
| `evaluate` lỗi mạng/5xx | Giữ điểm cũ, gắn nhãn "Chưa chấm lại được" + nút "Chấm lại"; khóa "Dùng cho tuần" tới khi chấm lại được |
| `evaluate` 400 (ô sai) | Hoàn tác bước vừa làm, toast lỗi BE |
| Nháp hết `PENDING_REVIEW` giữa chừng (409) | Toast lỗi BE, khóa sửa, chỉ còn nút đóng |
| `apply` 409 `6082` | Hiện danh sách lỗi theo ngày của BE, giữ hộp thoại và bản đang sửa |
| `NO_FEASIBLE` | Không có bảng, không có cột món; giữ cảnh báo đỏ và danh sách nguyên nhân như hiện tại |
| AI tắt | Như cũ: phương án theo quy tắc; sửa và chấm lại vẫn chạy (AI-03) |

## 7. Kiểm thử

BE (JUnit, Testcontainers như `MenuAiDraftIntegrationTest`):

- `evaluate`: lưới gốc ⇒ điểm/issue trùng nháp, `editedCells=0`; đổi một ô ⇒ `editedCells=1`, điểm thay đổi, không ghi DB (đếm dòng trước/sau); món ngoài ứng viên của ô, thiếu ô, ngày nghỉ ⇒ 400; nháp đã bỏ ⇒ 409; thiếu `MENU_MANAGE` ⇒ 403.
- `apply` kèm lưới: tuần lưu đúng lưới đã sửa, khối khác giữ nguyên, `edited_cells` đúng, gọi lại không ghi thêm; lưới đã sửa còn lỗi chặn ⇒ vẫn lưu, công bố bị chặn; không body ⇒ như cũ.
- `candidates`: đúng khung ô, thứ tự ưu tiên, cờ tín hiệu; loại nháp khác ⇒ 409.
- Response có `slot`, `slots`; nháp cũ có khung ô đã đổi vẫn trả đủ hàng.
- Controller test mã lỗi/HTTP; `ApiCodeTest`; snapshot OpenAPI (chỉ thêm).

FE (node test như `tests/menu/menu-ai-draft.test.mjs`): hàm thuần ở mục 5 (thay ô, đổi chỗ, đếm ô đã sửa, luật thả, dựng request); client gọi đúng 3 endpoint; kiểm nguồn trang: nút chính khóa khi `dirty` hoặc chấm lỗi. Kiểm tay trên localhost: kéo thả chuột, bàn phím, màn 1024px và 768px.

Cổng: `node scripts/verify.mjs` ở cả hai repo, `check-ai-layer`.

## 8. Tác động và rủi ro

| Rủi ro | Giảm thiểu |
| --- | --- |
| Đổi contract (3 trường thêm, 2 endpoint mới, `apply` nhận body) | Chỉ thêm; `apply` không body giữ hành vi cũ. Consumer: chỉ FE nhánh `menu-ai`; APP không gọi. Cập nhật contract + `CROSS_REPO_MAP` |
| Nhánh AI lệch `dev` 5 commit, `dev` vừa gộp bảng bán trú (V83) | Merge `dev` trước khi code; chạy lại test meals; đặt số migration sau khi merge |
| Chấm lại mỗi lần đổi món làm chậm | `evaluate` không gọi model, chỉ đọc ngữ cảnh + tính; debounce 400ms; bỏ kết quả cũ |
| Hai cách chấm lệch nhau (engine vs evaluate) | Một method `assess` dùng chung, test "lưới gốc ⇒ trùng nháp" |
| Bảng 5 cột + cột món chật ở màn 1280px | Thẻ món gọn (tên + kcal), cột món thành ngăn trượt dưới 1100px |
| Người sửa xong vẫn bị hiểu là "AI gợi ý" | Nhãn "đã sửa N ô", cột `edited_cells`, dòng `VALUE_GATE` ghi rõ |

Rollback: FE quay về `MenuAiDraftResult` cũ; BE các endpoint mới không ảnh hưởng luồng cũ; cột `edited_cells` vô hại khi không dùng.

## 9. Việc tách riêng (không thuộc spec này)

1. **Chất lượng chọn món của phương án theo quy tắc.** Triệu chứng (tuần 16/11, Nhà trẻ, DB bản sao): 4/5 ngày bữa trưa gần giống nhau, 0/5 ngày đạt định mức. Chưa có nguyên nhân; cần điều tra `RuleBasedMenuPlanner`, `MenuPlanRepairer`, khung ô và dữ liệu món của khối trước khi sửa. Làm sau spec này vì bảng mới giúp nhìn rõ lỗi theo ngày.
2. **Dữ liệu thử `[E2E]…`.** Ngừng dùng các món đó trong bản sao DB local `carenest_ai` để xem cho sạch; không đụng DB gốc, không đổi code.

## 10. Quyết định agent chọn (chờ user soát)

- Chấm lại bằng API chấm thử không lưu (phương án A đã trình bày), không lưu từng lần thả món.
- Kéo thả trong bảng chỉ đổi chỗ trong cùng hàng; mỗi ô đúng một món; không thêm/bớt ô trong hộp thoại.
- Món đã dùng trong tuần vẫn kéo được (hệ thống cảnh báo lặp món, không cấm).
- Lưới đã sửa còn lỗi chặn vẫn dùng được vào tuần nháp.
- Thêm cột `edited_cells` (migration mới) để đo giá trị AI.
- Mẫu ngày và thay một món chỉ đổi cách hiển thị, chưa cho sửa trong hộp thoại.
- Debounce 400ms; ngưỡng ngăn trượt 1100px; tắt kéo thả dưới 768px.
