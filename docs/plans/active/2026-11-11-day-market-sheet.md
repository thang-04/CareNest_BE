---
title: Bảng đi chợ theo ngày — API chỉ đọc cho màn FE /kitchen/market-day
status: in-progress
owner: thangnd
jira: G94-192 / BE-FEAT-27   # user chốt 2026-11-11: luồng đi chợ dùng G94-192
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition]
rules: [NUT-03, NUT-07, NUT-10, NUT-11, NUT-12, NUT-13, NUT-17, AUTH-20]
risk: medium
created: 2026-11-11
updated: 2026-11-11
---
# Bảng đi chợ theo ngày

Plan FE: FE `docs/plans/active/2026-11-10-day-market-sheet.md` (nhánh `fix/G94-272-FE-FIX-01-e2e-flow-fixes`).

## Context
Màn FE "Đi chợ theo ngày" đang chạy dữ liệu demo vì BE chưa có endpoint gộp thực phẩm của mọi nhóm tuổi trong một ngày kèm tiền. `POST /nutrition/menu-statistics` chỉ tính một nhóm tuổi; `GET /nutrition/kitchen/day` không có tiền. User yêu cầu 11/11/2026: "xử lý BE, không dùng demo"; viết trên nhánh nutrition có sẵn.

## Quyết định
1. Endpoint mới chỉ đọc `GET /nutrition/day-market?campusId&date`, không bảng mới, không migration — user cho phép sửa BE, 11/11/2026.
2. Dùng lại `MealDemandCalculator`, `PurchaseUnits`, cách tính tiền của thống kê thực đơn (`MealPlanServiceImpl#ingredientsOf`); không thêm rule ID mới.

### Giả định (tự chọn theo chế độ tự chạy, cần user soát)
- G1. Chưa đủ sổ báo ăn đã xác nhận ⇒ tính theo sĩ số đang học × bữa được phục vụ (cùng mặc định bản dự trù hàng tươi, NUT-07). Có sổ nhưng còn sổ chưa xác nhận cũng coi là chưa chốt.
- G2. Suất tính tiền thu của nhóm tuổi = suất bữa trưa; nhóm không ăn trưa lấy bữa đầu tiên có suất (theo hành vi bản demo FE user đã duyệt). Tiền ăn là theo trẻ/ngày (NUT-17).
- G3. Tiền theo lượng thực dùng, không theo lượng đã làm tròn lên đơn vị mua (cùng cách `MenuStatisticsResponse.Ingredient.cost`).
- G4. Hàng DRY coi là xuất kho, FRESH là mua chợ (NUT-10, NUT-11); không đối chiếu tồn kho thực tế.
- G5. Chỉ HT/PHT đọc (như các endpoint đọc của hàng tươi); bếp chưa được mở.
- G6. Còn nhóm có suất chưa có giá tiền ăn ⇒ `balance` null thay vì số lệch.

## Acceptance criteria
- **AC-1** (NUT-07, NUT-10, NUT-11): chưa chốt suất ⇒ `basis=FORECAST`, lượng theo sĩ số; dòng FRESH trước DRY; `freshCost + dryCost = cost`; tổng nhóm tuổi = dòng.
- **AC-2** (NUT-03): mọi sổ đã xác nhận ⇒ `basis=CONFIRMED`, lượng theo suất chốt, có `confirmedAt`; sổ chưa xác nhận không dùng.
- **AC-3** (NUT-12): đơn vị mua đếm được ⇒ `orderQuantity` làm tròn lên, `cost` theo lượng thực dùng.
- **AC-4** (NUT-17): nhóm chưa có giá ⇒ `mealPrice`, `revenue`, `balance` null.
- **AC-5**: thứ Bảy/Chủ nhật/ngày nghỉ ⇒ `DAY_OFF`; tuần nháp hoặc chưa lập ⇒ `NO_MENU`; không có dòng.
- **AC-6** (AUTH-20): HT/PHT còn phân công đọc mọi điểm trường; hết phân công ⇒ `CAMPUS_ACCESS_DENIED`; điểm trường không tồn tại ⇒ `CAMPUS_NOT_FOUND`.

## Files
- C `dto/meals/DayMarketResponse.java` · C `service/meals/DayMarketService.java` · C `service/meals/impl/DayMarketServiceImpl.java` · C `controller/meals/DayMarketController.java`
- C `src/test/java/com/carenest/service/meals/impl/DayMarketIntegrationTest.java`
- M `docs/api/openapi.yaml` (chỉ thêm) · M `docs/modules/nutrition.md` (thêm một đoạn)

## Test matrix
| AC | Test (`DayMarketIntegrationTest`) | Kết quả |
| --- | --- | --- |
| AC-1 | `forecastBasisUsesEnrolmentAndSplitsFreshAndDry` | qua |
| AC-2 | `confirmedBasisUsesConfirmedServings` | qua |
| AC-3 | `countablePurchaseUnitRoundsOrderQuantityOnly` | qua |
| AC-4 | `missingMealPriceLeavesRevenueAndBalanceEmpty` | qua |
| AC-5 | `dayOffAndMissingMenuHaveNoLines` | qua |
| AC-6 | `readFollowsCampusAccess` | qua |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Worktree đang có thay đổi chưa commit của việc biên bản kiểm thực | Lẫn thay đổi khi commit | Chỉ thêm file mới; hai file chung chỉ thêm dòng | Xóa 5 file mới, bỏ đoạn thêm ở `nutrition.md`, sinh lại snapshot |
| Giả định G1–G6 chưa được user chốt | Số trên bảng khác mong đợi | Ghi rõ ở đây và ở báo cáo bàn giao | Sửa `DayMarketServiceImpl` |

## Progress log
### 2026-11-11 — Viết endpoint + test
- Chưa làm đủ bộ `specs/` theo `feature-spec-loop` (chỉ có plan này); cần user quyết có bổ sung không.
- `DayMarketIntegrationTest` 6/6 qua; `OpenApiSnapshotTest` 3/3 qua sau khi cập nhật snapshot bằng `-Dopenapi.snapshot.update=true` (chỉ thêm 163 dòng: path `/api/nutrition/day-market` và schema `DayMarket*`).
- Phân quyền: lúc đầu test giả định PHT bị chặn ở điểm trường khác — sai; AUTH-20 cho HT/PHT còn phân công đọc mọi điểm trường (`CampusAccessPolicy#canReadCampus`).
- Record lồng đặt tên `DayMarket*` vì springdoc đặt schema theo tên ngắn; `SessionMenu` trùng `KitchenDayResponse.SessionMenu`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 1265 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 641s | log target/verify.log`.
- Chạy thử: build image từ worktree này cho stack local cổng 8090 (user cho phép; DB local lên V86), FE cổng 5181 gọi `GET /nutrition/day-market` trả 200, tổng các nhóm tuổi khớp tổng chi.
- User chốt 11/11: commit chung PR với biên bản kiểm thực, mã Jira G94-192.
