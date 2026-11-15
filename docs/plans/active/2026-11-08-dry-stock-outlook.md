---
title: Bản dự trù gồm cả hàng khô cần nhập thêm + cảnh báo kho khô sắp thiếu (BE + FE)
status: in-progress
owner: thangnd
jira: chưa có — cần 1 task BE-FEAT + 1 task FE-FEAT trước khi commit
branch: feature/G94-191-FE-FEAT-48-nutrition-management   # FE; BE: feature/G94-254-BE-FEAT-27-day-boarding
modules: [kitchen, menu-planning]
rules: [NUT-07, NUT-08, NUT-11, NUT-12, NUT-22, P-24, P-38, AUTH-05]
risk: high
created: 2026-11-08
updated: 2026-11-08
---
# Bản dự trù gồm cả hàng khô cần nhập thêm + cảnh báo kho khô sắp thiếu (BE + FE)

Plan liên repo. Phần BE cần user cho phép sửa `CareNest_BE`; khi được phép, chép mục "Phase 1–2" sang `BE:docs/plans/active/2026-11-08-dry-stock-outlook.md` và để plan này chỉ còn Phase 3 + link.

## Context
**Vấn đề:** kho khô chỉ phát hiện thiếu **đúng ngày thiếu**, lúc chốt suất sáng hôm đó. Bếp đã thiếu hàng thật, PHT phải mua gấp qua Zalo (NUT-22). Luật đã chốt yêu cầu cảnh báo sớm hơn nhưng chưa ai làm.

- Luật: NUT-11 (`BE:docs/business/BUSINESS_RULES.md:113`), P-38 (`:381`): "lập/công bố thực đơn tuần ⇒ cảnh báo thiếu kho theo sĩ số dự kiến, chưa trừ". Flow: `BE:docs/business/flows/meal-management.md:30`.
- Bị hoãn: plan L3 ghi "cảnh báo thiếu kho theo sĩ số dự kiến khi công bố — làm cùng L4" (`BE:docs/plans/active/2026-11-03-day-boarding-l3-weekly-menu.md:30`). Plan L4 hàng tươi không làm (`…/2026-11-03-day-boarding-l4-fresh-goods.md`).
- Code hiện tại:
  - Công bố tuần không đụng kho, chỉ trừ ngay những ngày đã chốt suất trước khi công bố (`BE:src/main/java/com/carenest/service/meals/impl/MealPlanServiceImpl.java:173-193`).
  - Trừ theo ngày khi chốt suất: `StockLedgerServiceImpl.syncDay` (`…/StockLedgerServiceImpl.java:181`), nhu cầu `dryDemand` chỉ lấy tuần đã công bố × suất đã chốt (`:364`). Thiếu ⇒ ghi `shortfall_qty` (`:281-303`), phiếu nhập trừ bù ngày cũ trước (`settleShortfalls` `:245`).
  - Bản dự trù chỉ tính hàng tươi:
    - `FreshForecastResponse` có `counts` (sĩ số đang học / dự kiến theo nhóm tuổi), `days`, `lines` (`BE:src/main/java/com/carenest/dto/meals/FreshForecastResponse.java`);
    - tính suất = số dự kiến × bữa của nhóm tuổi (`FreshGoodsServiceImpl.calculate` `…/FreshGoodsServiceImpl.java:336`), sĩ số đang học `enrolledByAgeGroup` (`:368`);
    - công thức nhu cầu dùng chung `MealDemandCalculator.compute` (`…/MealDemandCalculator.java:119`), lọc theo loại bảo quản.
  - Bảng kiểm thực đơn tuần: `MealPlanResponse.Issue` (`BE:src/main/java/com/carenest/dto/meals/MealPlanResponse.java:95`) không có dòng nào về kho.
  - Hub "Bán trú hôm nay": `DayBoardResponse.DryStock` (`BE:src/main/java/com/carenest/dto/meals/DayBoardResponse.java`) chỉ nói về hôm nay.
  - FE:
    - menu "Bản dự trù hàng tươi" `/kitchen/forecast` (`src/layouts/menuConfig.js:355`);
    - chi tiết bản dự trù gồm 2 thẻ "Số trẻ theo nhóm tuổi" và "Thực phẩm cần đặt", nút "In danh sách đặt hàng" (`src/components/kitchen/FreshForecastDetail.jsx:52-97`);
    - dòng Kho khô ở hub `src/utils/boarding/dayBoardRows.js:79`;
    - bảng kiểm `src/components/menu-planning/MealPlanIssueList.jsx`, hộp công bố `PublishWeekDialog.jsx:14` (đã hiện cảnh báo do BE trả).

**Ví dụ của user:** tồn gạo 25 kg, mỗi ngày cần 10 kg ⇒ đủ thứ Hai, thứ Ba, thiếu từ thứ Tư. Mục tiêu: PHT lập bản dự trù tuần (thường thứ Sáu tuần trước) là thấy luôn "Gạo: cần 50 kg, tồn 25 kg, nhập thêm 25 kg, thiếu từ thứ Tư", in chung một danh sách đặt hàng với hàng tươi.

## Quyết định đã chốt
1. Thiếu kho **không chặn** công bố, không chặn chốt suất — P-38, user chốt 02/11 (bỏ P-25 chặn công bố). Không đảo.
2. Đặt hàng với NCC làm **ngoài hệ thống**; người đặt là PHT; hệ thống chỉ ghi phiếu nhập — NUT-11, NUT-12.
3. Bếp không dùng Web (AUTH-21); cảnh báo chỉ cho HT, PHT.
4. **Một nơi để dự trù và đặt hàng:** không làm tab "Sắp thiếu" riêng; hàng khô cần nhập thêm nằm ngay trong bản dự trù, cạnh hàng tươi — user 08/11 ("có tab dự trù hàng tươi lại thêm tab sắp thiếu rất phiền, phải vào 2 nơi"). Đổi phạm vi P-24 ("bản dự trù chỉ dùng đặt hàng tươi") ⇒ Phase 2 cập nhật rule BE.

## Làm rõ nghiệp vụ
- **Actor:** PHT (lập bản dự trù, công bố tuần, đặt NCC, nhập phiếu), HT xem. **Outcome:** một bản dự trù cho biết cả hàng tươi cần đặt theo ngày lẫn hàng khô cần nhập thêm (thiếu bao nhiêu, **thiếu từ ngày nào**); một danh sách in để gửi NCC.
- **Flow:**
  - [S] PHT lập bản dự trù kỳ (sĩ số dự kiến điền sẵn, PHT sửa — P-24). Bản dự trù hiện 3 thẻ: số trẻ · hàng tươi cần đặt · **hàng khô cần nhập thêm**.
  - [S] In danh sách đặt hàng gồm cả 2 phần ⇒ [B] gửi NCC.
  - [B] NCC giao hàng khô ⇒ [S] PHT nhập phiếu ⇒ thẻ hàng khô tự cập nhật (tính lại theo tồn lúc xem).
  - Bảng kiểm thực đơn tuần, hộp công bố, hub chỉ **nhắc** ("kho khô sắp thiếu …") và dẫn về bản dự trù. Không phải nơi thao tác thứ hai.
- **Đối chiếu tài liệu:**
  - P-24 (`BUSINESS_RULES.md:404`) và NUT-12: bản dự trù chỉ cho hàng tươi. Nay user đổi: bản dự trù **thêm** phần hàng khô cần nhập thêm. Phần này không trừ kho, không lưu: tính khi xem theo tồn hiện tại.
  - NUT-11 "cảnh báo theo sĩ số dự kiến" ⇒ dùng đúng sĩ số dự kiến của bản dự trù.
  - Cách tính cộng dồn theo ngày để ra "thiếu từ ngày nào": là cách tính, không đổi luật.
- **Ảnh hưởng:**
  - Module nutrition (fresh-goods, stock, meal-plan, day-board).
  - Quyền: đọc như bản dự trù hiện tại (`CampusAccessPolicy`); HT hai điểm trường, PHT điểm trường của mình.
  - Dữ liệu dẫn xuất: tính khi đọc, không lưu, không đổi sổ kho.
  - `FE: /kitchen/forecast*`, `/menu/weekly`, `/boarding/today`.
  - APP: không.
  - Dữ liệu cũ: bản dự trù đã lưu xem lại vẫn có phần hàng khô, tính theo tồn hiện tại.

### Câu hỏi mở
Không còn câu hỏi chặn. Giả định agent tự chọn nằm ở Key decisions, cột "Vì sao" — user soát.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Ai phát hiện, ai đặt bổ sung, đặt lúc nào, có ảnh hưởng công bố không | Hệ thống tính trong bản dự trù và nhắc ở bảng kiểm, hộp công bố, hub; PHT đặt NCC ngoài hệ thống, trước "ngày bắt đầu thiếu"; không chặn công bố | agent theo NUT-11, P-38 | 2026-11-08 | Bổ sung flow BE ở Phase 2 |
| Tab "Sắp thiếu" riêng hay gộp | Gộp vào bản dự trù, không tab riêng | user | 2026-11-08 | P-24, NUT-12 ở Phase 2 |

## Acceptance criteria
- **AC-1** (`NUT-11`, `P-38`, `NUT-07`, `P-24`)
  - Given: điểm trường X tồn gạo 25 kg; tuần đã công bố, theo sĩ số dự kiến của bản dự trù mỗi ngày cần 10 kg gạo; chưa ngày nào chốt suất.
  - When: thứ Hai xem (hoặc xem trước khi lập) bản dự trù kỳ thứ 2–6.
  - Then: `dryLines` có gạo `requiredQty=50`, `availableQty=25`, `shortQty=25`, `shortFrom=<thứ 4>`.
  - And: `lines` hàng tươi không đổi so với trước.
- **AC-2** (`NUT-11`): Given lô hết hạn trước ngày ăn D, Then lô đó không tính cho D và các ngày sau D. Đây cũng là luật lọc của `takeFromLots` (`StockLedgerServiceImpl.java:315`).
- **AC-3** (`NUT-11`, `P-38`)
  - Given: ngày D đã chốt suất (đã trừ thật) và còn phần thiếu đang mở 5 kg.
  - Then: D không tính lại nhu cầu dự kiến; 5 kg thiếu đang mở trừ vào tồn trước (vì phiếu nhập bù phần thiếu cũ trước — `settleShortfalls`).
  - And: ngày chưa chốt nằm giữa hôm nay và đầu kỳ cũng tiêu tồn: tính theo bản dự trù phủ ngày đó nếu có, không có thì theo sĩ số đang học.
- **AC-4** (`NUT-11`): Given kỳ dự trù đã qua hết, Then `dryLines` rỗng (không tính lại quá khứ).
- **AC-5** (`NUT-11`, `P-38`, `NUT-08`)
  - Given: kế hoạch tuần W (nháp hoặc đã công bố) làm kho X thiếu.
  - When: `GET /nutrition/meal-plans/{W}`.
  - Then: `issues` có `WARNING` `DRY_STOCK_SHORT` cho từng điểm trường user đọc được. `date` = ngày bắt đầu thiếu sớm nhất; `message` nêu điểm trường, số thực phẩm, ngày.
  - And: `POST …/publish` vẫn thành công.
- **AC-6** (`NUT-11`): Given ngày D, When `GET /nutrition/day-board?date=D`, Then `dryStock` có thêm `upcomingShortFoodCount`, `upcomingShortFrom` (khung từ D tới hết tuần sau); đủ kho ⇒ `0` / `null`.
- **AC-7** (FE)
  - Chi tiết bản dự trù có thẻ thứ 3 "Hàng khô cần nhập thêm". Bảng: Thực phẩm · Đơn vị · Cần trong kỳ · Tồn dùng được · Nhập thêm · Thiếu từ ngày. Mỗi thông tin một cột, giá trị ngắn căn giữa; chỉ dòng thiếu; đủ ⇒ `EmptyState` "Kho khô đủ cho kỳ này".
  - Bản xem trước khi lập (`FreshForecastCreateModal`) cũng hiện thẻ này.
  - "In danh sách đặt hàng" in cả 2 phần, bật được khi có ít nhất 1 phần.
  - Menu đổi tên "Bản dự trù hàng tươi" → "Bản dự trù".
- **AC-8** (FE)
  - Hub, dòng Kho khô: `upcomingShortFoodCount > 0` ⇒ thêm câu "Sắp thiếu n thực phẩm từ thứ Tư dd/MM" và nút "Bản dự trù" (mở bản dự trù phủ ngày thiếu nếu có, không thì danh sách bản dự trù). Trạng thái hôm nay giữ nguyên.
  - Bảng kiểm `/menu/weekly` và hộp công bố hiện `DRY_STOCK_SHORT` kèm link "Xem bản dự trù".
  - **Không** có tab/route mới trong trang Kho khô.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nơi PHT xem và đặt hàng khô | Thẻ "Hàng khô cần nhập thêm" trong bản dự trù, in chung danh sách | Tab "Sắp thiếu" riêng trong Kho khô | User chốt 08/11: một nơi; PHT vốn lập bản dự trù để đặt hàng |
| Cách tính | Cộng dồn theo ngày, lô hết hạn trước, lô hết hạn trước ngày ăn không tính | Gộp tổng cả kỳ so với tồn | Chỉ cách theo ngày trả lời được "thiếu từ ngày nào"; cùng luật lô với trừ thật |
| Số trẻ để tính | Trong bản dự trù: `expectedCount` của bản đó. Bảng kiểm, hub và ngày ngoài kỳ: bản dự trù đã lưu phủ ngày nếu có, không có thì sĩ số đang học | Luôn sĩ số đang học | NUT-11 nói "sĩ số dự kiến" = số PHT đã chốt trong bản dự trù (P-24); fallback để vẫn nhắc được khi chưa lập |
| Lưu hay tính khi xem | Phần hàng khô tính khi xem, không lưu (hàng tươi giữ lưu như cũ) | Lưu cùng bản dự trù | Tồn đổi sau mỗi phiếu nhập/chốt suất; tính lại thì số nhập thêm luôn đúng |
| Ngày nào được tính | Từ hôm nay tới cuối kỳ; bỏ ngày nghỉ, ngày đã chốt suất; tuần nháp hoặc đã công bố | Chỉ tuần đã công bố | Bản dự trù hàng tươi đã tính cả tuần nháp (`FreshForecastDay.planStatus`); giữ nhất quán |
| Thời gian giao hàng của NCC | Không cấu hình; hiện "thiếu từ ngày" | Cấu hình số ngày giao | YAGNI; luật chưa có. **User soát** |
| Thông báo đẩy | Không; nhắc ở bảng kiểm, hộp công bố, hub | Thông báo trong app/Zalo | Không chat thay Zalo (V1); hub là nơi PHT xem hằng ngày |
| Chỗ đặt logic | Lớp tính thuần `DryStockOutlookCalculator`, dùng chung cho bản dự trù, meal-plan, day-board | Nhét vào `MealPlanEvaluator` hoặc `FreshGoodsServiceImpl` | Evaluator là cân đối dinh dưỡng; tách để test riêng, 3 nơi dùng chung |

## Phases
### Phase 1 — BE: tính hàng khô cần nhập thêm, trả trong bản dự trù (cần user cho phép sửa BE)
- Files:
  - Tạo mới: `service/meals/impl/DryStockOutlookCalculator.java`.
  - Sửa:
    - `dto/meals/FreshForecastResponse.java`: thêm `List<DryLine> dryLines` (`food`, `requiredQty`, `availableQty`, `shortQty`, `shortFrom`).
    - `service/meals/impl/FreshGoodsServiceImpl.java`: gắn `dryLines` vào xem trước và chi tiết.
    - `service/meals/StockLedgerService.java` (+Impl): thêm `outlook(campusId, from, to, servingsOf)` dùng nội bộ, đọc lô, số dư, phần thiếu mở, ngày đã chốt.
    - `service/meals/impl/MealDemandCalculator.java`: chuyển `enrolledByAgeGroup` từ FreshGoods sang dùng chung.
    - `docs/api/openapi.yaml`.
- Steps:
  1. Calculator thuần với input:
     - lô (foodId, hạn dùng, số dư);
     - phần thiếu đang mở theo thực phẩm;
     - danh sách ngày (date → nhu cầu DRY).

     Calculator lấy phần thiếu mở trước, rồi đi từng ngày tăng dần, lấy lô hết hạn trước (chỉ lô có hạn ≥ ngày). Output theo thực phẩm: `requiredQty` (chỉ ngày trong kỳ), `availableQty`, `shortQty`, `shortFrom`.
  2. `outlook()`: ngày từ `max(today, …)` tới `to`.
     - Bỏ ngày nghỉ, ngày điểm trường đã chốt suất, tuần chưa lập.
     - Suất theo `servingsOf(date)`: trong kỳ của bản dự trù là `expectedCount` của bản đó; ngoài kỳ là bản dự trù đã lưu phủ ngày, không có thì sĩ số đang học.
  3. Bản dự trù: `dryLines` = `outlook(campus, today, periodTo)`, chỉ dòng `shortQty > 0`, xếp `shortFrom` tăng dần. Kỳ đã qua ⇒ rỗng.
- Tests: AC-1..AC-4 → `DryStockOutlookCalculatorTest` (unit, `@DisplayName("NUT-11: …")`) + `FreshGoodsIntegrationTest` (thêm ca kho khô; ca hàng tươi giữ nguyên kỳ vọng); hồi quy `DryStockIntegrationTest`.
- Exit: BE `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0.
- **Dừng chờ user review.**

### Phase 2 — BE: nhắc trong kế hoạch tuần + hub, cập nhật luật
- Files (sửa): `MealPlanServiceImpl.java` (`toResponse` nối thêm issue kho), `DayBoardServiceImpl.java`, `dto/meals/DayBoardResponse.java` (`DryStock` thêm 2 trường), `docs/api/openapi.yaml`, `docs/business/BUSINESS_RULES.md` (P-24, NUT-12: bản dự trù thêm phần hàng khô cần nhập thêm, không trừ kho, không lưu; NUT-11: cảnh báo cộng dồn theo ngày), `docs/business/flows/meal-management.md:30` và `:44`, `docs/context/CURRENT_STATE.md`.
- Steps:
  1. Khi đọc kế hoạch tuần W: với từng điểm trường user đọc được, gọi `outlook(campus, today, cuối W)`; có thiếu ⇒ thêm 1 `Issue(WARNING, "DRY_STOCK_SHORT", shortFrom sớm nhất, …)`. Thông báo mẫu: "Điểm trường X: kho khô thiếu 3 thực phẩm từ thứ Tư 12/11 — xem bản dự trù".
  2. Day-board: `outlook(campus, date, thứ Sáu tuần sau)` ⇒ đếm số thực phẩm thiếu, `shortFrom` sớm nhất.
  3. Ghi luật đã đổi (user 08/11).
- Tests: AC-5 → `MealPlanIntegrationTest` (có cảnh báo, publish 200, PHT chỉ thấy điểm trường mình); AC-6 → `DayBoardIntegrationTest`.
- Exit: BE verify PASS; BE `node scripts/check-ai-layer.mjs` 0 lỗi.
- **Dừng chờ user review.**

### Phase 3 — FE: thẻ hàng khô trong bản dự trù, in chung, nhắc ở hub/bảng kiểm
- Files (sửa):
  - `src/components/kitchen/FreshForecastTables.jsx` (thêm `ForecastDryTable`), `src/components/kitchen/FreshForecastDetail.jsx` (thẻ thứ 3, điều kiện bật nút in), `src/components/kitchen/FreshForecastCreateModal.jsx` (xem trước), `src/components/kitchen/FreshForecastPrint.jsx` (phần hàng khô);
  - `src/layouts/menuConfig.js:356` (đổi nhãn), `src/utils/boarding/dayBoardRows.js` (câu + nút "Bản dự trù"), `src/components/menu-planning/MealPlanIssueList.jsx` (link theo `code === 'DRY_STOCK_SHORT'`);
  - test hiện có của hub, bản dự trù.
- Steps:
  1. Viết bảng hàng khô theo `DESIGN.md` §6.3.
  2. Thêm thẻ vào chi tiết và vào xem trước.
  3. In chung một danh sách: đổi tiêu đề "Danh sách đặt hàng tươi" → "Danh sách đặt hàng", tên file `Dat_hang_<kỳ>`.
  4. Đổi nhãn menu, breadcrumb.
  5. Hub thêm câu, nút.
  6. Link trong bảng kiểm.
  7. Không viết mock cho contract mới (giống plan `2026-11-03-day-boarding-l2-l3-fe.md`).
- Tests: AC-7 → test ánh xạ dòng hàng khô + điều kiện nút in; AC-8 → test `dayBoardRows` + test link issue; E2E thật trên stack BE nhánh (ảnh bản dự trù, bản in, hub, hộp công bố).
- Exit: FE `node scripts/verify.mjs` → `VERIFY PASS`, skip 0; ảnh đối chiếu ở role HT và PHT.
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | NUT-11, P-38, NUT-07, P-24 | `DryStockOutlookCalculatorTest`, `FreshGoodsIntegrationTest` | 1 | chưa chạy |
| AC-2 | NUT-11 | `DryStockOutlookCalculatorTest` | 1 | chưa chạy |
| AC-3 | NUT-11, P-38 | `DryStockOutlookCalculatorTest`, `FreshGoodsIntegrationTest` | 1 | chưa chạy |
| AC-4 | NUT-11 | `FreshGoodsIntegrationTest` | 1 | chưa chạy |
| AC-5 | NUT-11, P-38, NUT-08 | `MealPlanIntegrationTest` | 2 | chưa chạy |
| AC-6 | NUT-11 | `DayBoardIntegrationTest` | 2 | chưa chạy |
| AC-7 | NUT-11, P-24 | test bản dự trù FE | 3 | chưa chạy |
| AC-8 | NUT-11 | test `dayBoardRows`, test link `MealPlanIssueList` | 3 | chưa chạy |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Thêm phần hàng khô làm lệch số hàng tươi | Sai số đặt hàng tươi | Phần khô tách riêng `dryLines`; giữ nguyên kỳ vọng hàng tươi trong `FreshGoodsIntegrationTest` | Bỏ `dryLines`, giữ hàng tươi |
| Cách tính dự kiến lệch luật trừ thật (lô, hết hạn, thải bỏ) | Số nhập thêm sai | Dùng chung `MealDemandCalculator.compute` + cùng điều kiện hạn dùng `takeFromLots` | Tắt `dryLines` + issue + trường hub |
| GET kế hoạch tuần/bản dự trù chậm hơn | Màn chậm | 2 điểm trường × ≤10 ngày, nạp món một lần (`dishesOf`) | Bỏ cảnh báo khỏi GET kế hoạch tuần, chỉ giữ trong bản dự trù |
| Bản dự trù cũ xem lại ra số hàng khô khác lúc lập | PHT bối rối | Câu phụ dưới thẻ: "Tính theo tồn kho lúc xem" | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: `FreshForecastResponse.dryLines` (mới; FE `FreshForecastDetail`, `FreshForecastCreateModal`, `FreshForecastPrint`); `MealPlanResponse.issues` thêm mã `DRY_STOCK_SHORT` (FE `MealPlanIssueList`, `PublishWeekDialog` hiện chung theo severity); `DayBoardResponse.DryStock` thêm 2 trường (FE `dayBoardRows.js:79`). Không endpoint mới. APP không dùng.
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] >8 file hoặc >3 phase ⇒ đã tách theo repo (Phase 1–2 BE, Phase 3 FE); 3 phase
- [x] Không dùng rule PENDING/OPEN như đã chốt (NUT-11, P-38 đã đóng; P-24 đổi phạm vi theo user 08/11, cập nhật ở Phase 2)
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan · không câu mơ hồ

## Progress log
### 2026-11-08 — Lập plan (draft)
- Commit: chưa commit
- Verify: chưa chạy (chỉ có tài liệu)
- Bản đầu có tab "Sắp thiếu" riêng trong Kho khô + endpoint `/stock/outlook`. User bác vì phải vào 2 nơi ⇒ gộp vào bản dự trù, bỏ tab và endpoint.
- Giả định agent tự chọn, user soát: số trẻ = sĩ số dự kiến của bản dự trù (fallback sĩ số đang học); cộng dồn theo ngày; phần khô tính khi xem; không cấu hình thời gian giao hàng; không thông báo đẩy.
- Tiếp: user cho phép sửa `CareNest_BE` + cấp mã Jira ⇒ tạo nhánh từ `dev`, làm Phase 1.

### 2026-11-08 — Chuẩn bị Phase 1 (chưa code)
- User cho phép sửa BE. Checkout BE chính đang ở `feature/G94-60-BE-FEAT-01-authentication` và có thay đổi docs chưa commit của việc khác ⇒ tạo worktree detached `CareNest_BE-dry-outlook` tại `origin/dev` (3decab1), không tạo nhánh. Đã chép plan vào worktree.
- Dừng do hết hạn mức sử dụng. Chưa sửa code BE/FE. Còn: Phase 1–3, verify, mã Jira, nhánh.

### 2026-11-08 — Phase 1–3 làm xong (chờ user soát)
- Nhánh: user bảo cập nhật nhánh nutrition theo dev. BE `feature/G94-254-BE-FEAT-27-day-boarding` (worktree `CareNest_BE-day-boarding`) chậm 47 commit, 0 commit riêng, có 25 file sửa dở 04/11 + 4 file mới (trong đó `V74__menu_template_single_day.sql` trùng số V74 trên dev) ⇒ user chọn stash: 2 stash "WIP day-boarding 04/11 …" rồi fast-forward lên `origin/dev` 3decab1. FE `feature/G94-191-FE-FEAT-48-nutrition-management` sạch, chậm 41 ⇒ fast-forward lên `origin/dev` 8759b2d. Worktree tách rời `CareNest_BE-dry-outlook` đã gỡ.
- BE: `DryStockOutlook` (tính thuần `project` + đọc lô, phần thiếu mở, ngày đã chốt, bản dự trù phủ ngày / sĩ số đang học); `FreshForecastResponse.dryLines`; issue `DRY_STOCK_SHORT` trong `MealPlanServiceImpl.toResponse`; `DayBoardResponse.DryStock.upcomingShortFoodCount/upcomingShortFrom`; `MealDemandCalculator.enrolledByAgeGroup/expectedServings` (chuyển từ FreshGoods). Docs: NUT-11, NUT-12, P-24 (`BUSINESS_RULES.md`), `flows/meal-management.md`, `CURRENT_STATE.md`, snapshot `openapi.yaml`.
- FE: thẻ "Hàng khô cần nhập thêm" (`ForecastDryCard`) ở chi tiết và hộp lập bản dự trù; in chung "DANH SÁCH ĐẶT HÀNG" (hàng tươi + hàng khô, cột "Cần trước"); đổi nhãn "Bản dự trù hàng tươi" → "Bản dự trù" (menu, breadcrumb, tiêu đề, nút); hub dòng Kho khô thêm câu "Sắp thiếu n thực phẩm khô từ thứ X" + nút "Bản dự trù" (tông cam); bảng kiểm thêm link "Xem bản dự trù" cho `DRY_STOCK_SHORT`; `formatWeekdayDate` trong `utils/format.js`.
- Verify:
  - BE test liên quan: `mvnw test -Dtest=DryStockOutlookTest,DryStockOutlookIntegrationTest,MealPlanIntegrationTest,FreshGoodsIntegrationTest,DryStockIntegrationTest,StockDeduction*IntegrationTest,MealPlanPublishConcurrencyIntegrationTest,ArchitectureRulesTest` → `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`. BE `check-ai-layer` 0 lỗi.
  - FE `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify`.
- Khác plan:
  - Gộp calculator và phần đọc dữ liệu vào một lớp `DryStockOutlook` (package-private, dùng chung 3 nơi), không thêm method vào `StockLedgerService`.
  - `requiredQty` = nhu cầu từ hôm nay tới hết kỳ (gồm ngày chưa chốt trước kỳ) để cộng trừ khớp "Nhập thêm"; cột FE ghi "Cần đến dd/MM".
  - Ngày có bất kỳ sổ suất đã chốt của điểm trường thì bỏ cả ngày (chốt từng bữa lệch nhau hiếm, chấp nhận).
  - Test tích hợp tách lớp mới `DryStockOutlookIntegrationTest` (đồng hồ cố định 08/11/2026) thay vì sửa các lớp cũ chạy đồng hồ thật.
  - AC-5: kiểm cảnh báo là `WARNING` và hết khi đủ kho; chưa có test gọi `publish` thật (lưới test bị chặn bởi định mức), `publishable` vẫn chỉ lấy từ bảng kiểm dinh dưỡng.
- BE `node scripts/verify.mjs` → `VERIFY PASS full | tests 1177 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 565s`.

### 2026-11-08 — Kiểm E2E trên stack thật (chờ user soát)
- Stack riêng `carenest_dry` dựng từ nhánh BE day-boarding (image `carenest_be:dry-outlook`, API :8092, Keycloak :8192, Postgres :5442, seed dev), FE chạy dev ở :3010 qua proxy `/be-api`, `/kc` (file cấu hình tạm đã xóa). Không đụng stack `carenest_be` (:8080), `carenest_main_seed` (:8090).
- HT dev: hộp lập bản dự trù CS1 kỳ 09–13/11 — kho seed đủ ⇒ "Kho khô đủ cho kỳ này"; đổi dự kiến MG lớn 43 → 900, Tính lại ⇒ 4 dòng thiếu (gạo nếp từ T2 09/11, gạo tẻ từ T3 10/11: cần 308,656 − tồn 77,159 = nhập thêm 231,497 kg; dầu đậu nành, miến dong từ T4 11/11). Lưu ⇒ chi tiết có thẻ hàng khô; In danh sách đặt hàng ⇒ phần "Hàng tươi" + "Hàng khô cần nhập thêm" (cột "Cần trước").
- Bán trú hôm nay: CS1 "Sắp thiếu 4 thực phẩm khô từ thứ Hai 09/11 — đặt thêm theo bản dự trù." + nút "Bản dự trù"; CS2 đủ kho không nhắc.
- Kế hoạch tuần 09/11 › Bảng kiểm › Xem tất cả lưu ý: "Cơ sở 1 – MN Thượng Hồng: kho khô thiếu 4 thực phẩm từ thứ Hai 09/11 — xem bản dự trù để đặt thêm" + link "Xem bản dự trù".
- PHT CS1 (viceprincipal): thấy cảnh báo CS1 ở bảng kiểm và hub.
- Sửa sau kiểm: tiêu đề hộp "Lập bản dự trù", toast "Đã lưu bản dự trù.", trống "Chưa có bản dự trù"; hub giữ tông theo trạng thái hôm nay (trước đó đổi sang cam làm nhãn "Ngày nghỉ" màu cam, dễ hiểu nhầm).
- Verify sau sửa: FE `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify`.
- Ghi nhận ngoài phạm vi: cảnh báo kho nằm lẫn trong danh sách lưu ý của tuần (67 dòng) — muốn nổi hơn thì là việc sau; console lỗi CORS `education-plans/config` gọi :8080 do cấu hình môi trường thử, không liên quan.
- Chưa làm: commit (cần mã Jira); stack `carenest_dry` còn chạy.

### 2026-11-08 — Làm tròn lên nguyên đơn vị mua (user hỏi "hộp sao lại lẻ")
- Vấn đề: bản dự trù hiện "Sữa chua 11,4 hộp 100 g", "Trứng 882,376 quả" — BE trả gam, FE chia quy cách, không chỗ nào làm tròn.
- Quyết định (chế độ tự chạy, chờ user soát): BE `PurchaseUnits.roundUp` — đơn vị mua có chữ đầu không phải đơn vị cân đo (kg, g, lít, ml, lạng) ⇒ đếm được ⇒ làm tròn lên bội số quy cách. Hàng tươi làm tròn **từng ngày** (giao theo ngày), tổng kỳ = cộng các ngày; hàng khô chỉ làm tròn "Nhập thêm" (`shortQty`), "Cần"/"Tồn dùng được" giữ số thật. Không thêm cột/cờ cho thực phẩm (đơn vị mua là chữ tự do, đoán theo chữ đầu đủ cho dữ liệu hiện có).
- Giới hạn: bản dự trù đã lưu trước giữ số cũ tới khi lập lại; kiểm nhận lần 2 (so suất đã chốt) và trang thống kê thực đơn chưa làm tròn.
- FE không đổi (đổi đơn vị mua ra số nguyên sẵn).
- Test BE: `PurchaseUnitsTest` (17), thêm 1 test vào `FreshGoodsIntegrationTest`, 1 vào `DryStockOutlookIntegrationTest` (cần `entityManager.clear()` vì thực phẩm đã nạp vào phiên JPA trước khi sửa bằng SQL); focused 37/37 pass. Docs: NUT-12, `docs/modules/nutrition.md`.
- Verify BE sau sửa: `VERIFY PASS full | tests 1196 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`. Kiểm trên :3010 (BE dựng lại): lập bản dự trù mới ⇒ "Sữa chua 12 hộp 100 g", "Trứng gà ta 86 quả", hàng kg giữ số lẻ.
- User chọn "Làm tròn luôn": thêm làm tròn cho lượng cần nhận lần 2 (`servingsExpected`) và cột "Mua theo ĐVT" ở thống kê thực đơn (`purchaseQty`); "Tiền ăn" vẫn theo lượng thực dùng (phần dư trong hộp không phải tiền ăn bữa đó). Test: 1 thêm vào `FreshGoodsIntegrationTest`, 1 vào `MenuStatisticsIntegrationTest`; focused 38/38 pass. FE không đổi.
