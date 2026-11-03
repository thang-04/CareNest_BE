---
title: Bán trú — sắp lại bố cục Web/App theo quy trình 01/11 và lộ trình BE
status: draft
owner: thangnd
jira: G94-254 / BE-FEAT-27
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition, attendance, child, school-structure, ai-assistance]
rules: [NUT-02, NUT-03, NUT-05, NUT-07, NUT-08, NUT-09, NUT-11, NUT-12, NUT-13, NUT-16, NUT-17, NUT-18, NUT-19, NUT-20, NUT-21, NUT-23, ATT-07, AUTH-05, AUTH-20, AUTH-21, AUTH-22, PAR-01, AI-07]
risk: high
created: 2026-11-02
updated: 2026-11-02
---
# Bán trú — sắp lại bố cục Web/App theo quy trình 01/11 và lộ trình BE

Plan ô (umbrella) từ phiên brainstorm. Mỗi lát (slice) khi bắt đầu code tách plan riêng từ `_TEMPLATE.md`, link về đây. Plan này không sửa code.

## Context

Rule bán trú chốt lại 2026-11-01 theo sơ đồ quy trình (commit `12352da`, `docs/business/flows/meal-management.md`). Hiện trạng:

- **BE** chỉ có sổ số suất: `GET /meal-counts`, `POST /meal-counts/{id}/confirm`, `POST /meal-counts/adjustments` (`MealCountController`), bảng V26/V29. Thực đơn, kho, hàng tươi, lưu mẫu, ảnh bữa: chưa có bảng, chưa có API (`docs/modules/nutrition.md`).
- **FE** (`CareNest_FE`, nhánh `fix/G94-272-FE-FIX-01-e2e-flow-fixes`) có ~45 màn bán trú chạy mock (`src/services/kitchen/mock/kitchenMockRepository.js` 2108 dòng, `src/services/menu-planning/mock/menuPlanningMockRepository.js` 1581 dòng). `src/services/kitchen/api/kitchenApi.js` là endpoint PROPOSED theo luồng cũ, BE chưa có route nào. Bố cục dựng trước khảo sát 27–28/10 và trước chốt 01/11.

### Lệch giữa FE hiện tại và rule đã chốt

| # | FE hiện có | Rule hiện hành | Hướng sửa |
| --- | --- | --- | --- |
| 1 | Menu Web cho `KITCHEN_STAFF` (`src/layouts/menuConfig.js` khối `ROLES.KITCHEN_STAFF`) | Bếp chỉ dùng App, gateway Web từ chối (AUTH-21) | Bỏ menu Web của bếp; màn bếp chuyển sang App |
| 2 | "Xuất kho" + bếp chọn lô (`StockIssuePage`, `StockIssueApprovalPage`, `allocateIssueLots`) | Trừ kho tự động một lần khi công bố, lô hết hạn trước (NUT-11) | Bỏ màn xuất kho; thay bằng tab "Lịch sử nhập/trừ" trong Tồn kho |
| 3 | Hàng tươi: bếp ghi, PHT duyệt (`saveFreshReceipt submit`, `reviewFreshReceipt`) | PHT ghi đủ/thiếu/thừa 2 lần: so bản dự trù, so thực đơn chuẩn (NUT-12) | Một màn kiểm nhận của PHT, 2 cột so sánh |
| 4 | Dự trù tuần theo ô campus × tuần (`WeeklyForecastPage`, `weekly-forecasts`) | Bản dự trù: chọn kỳ, cơ sở, thực đơn dự kiến; sĩ số dự kiến điền sẵn theo nhóm tuổi × cơ sở, PHT sửa; lưu, in đặt hàng (NUT-07) | Đổi màn thành "Bản dự trù" có kỳ tùy chọn |
| 5 | "Thực đơn mẫu (ngày)" (`/menu/menus`) | Thực đơn mẫu = bộ 4 tuần có nhãn mùa, theo khối (NUT-13) | Đổi thành "Bộ thực đơn mẫu" 4 tuần |
| 6 | Xuất bản cả tuần, sửa sau xuất bản bằng "phiên bản thay thế" | Công bố cả tuần một lần, không sửa sau công bố (NUT-08, chốt 02/11; ngoại lệ P-39) | Giữ công bố tuần; bỏ "phiên bản thay thế"; đến ngày thêm ảnh bữa |
| 7 | `MealPreparationStatusPage`, `MealPreparationUpdatePage` | NUT-14, NUT-15 REMOVED | Xóa |
| 8 | Lưu mẫu: bếp ghi trên Web, không duyệt | Bếp ghi trên App → PHT duyệt lưu và hủy; PHT in nhãn/biểu mẫu (NUT-21) | Web: hàng chờ duyệt + in |
| 9 | Chưa có ảnh mẫu bữa | Bếp chụp theo cơ sở trong khung giờ HT cấu hình (NUT-16) | App: chụp; Web HT: cấu hình khung giờ; PHT/HT: xem |
| 10 | Thực đơn dị ứng theo "nhóm trẻ áp dụng" (`AllergyMenuFormPage`) | Món thay thế theo chất dị ứng × món, dùng chung, không theo trẻ (NUT-23) | Bảng chất × món, hệ thống gợi ý |
| 11 | Menu PHT tách 4 nhóm (catalog/nutrition/stock/foodReport), 2 trang "quy trình" (`KitchenFlowPage`, `NutritionFlowPage`) | — | Gộp theo việc của PHT, một hub "Bán trú theo ngày" |

## Quyết định đã chốt
1. Toàn bộ rule NUT-* hiện hành, sơ đồ BP bán trú — user 2026-11-01. Không đảo.
2. Phiên này chạy chế độ tự chạy (CLAUDE.md, user chốt 2026-10-23): các lựa chọn ở "Key decisions" là phương án khuyến nghị agent tự chọn, chờ user soát.

## Làm rõ nghiệp vụ
- **Actor:** PHT phụ trách dinh dưỡng (AUTH-20) — lập, công bố, kho, hàng tươi, duyệt mẫu; PHT cơ sở / HT — xác nhận suất (`MEAL_COUNT_CONFIRM`); HT — cấu hình khung giờ ảnh; bếp (App, tài khoản chung mỗi cơ sở — AUTH-22); phụ huynh (App — PAR-01).
- **Outcome:** một ngày bán trú đi trọn: dự trù → đặt hàng tươi → suất chốt → công bố → trừ kho → bếp nấu → lưu mẫu/ảnh → phụ huynh xem.
- **Ảnh hưởng:** nutrition (mới gần hết), attendance (phát event khi xác nhận lại suất), child (`allergySummary` dạng tổng hợp), school-structure (nhóm tuổi, giờ chốt, khung giờ ảnh). FE: các route `/menu/*`, `/kitchen/*`. APP: màn bếp, màn phụ huynh xem thực đơn/ảnh.

### Câu hỏi mở
Không có câu hỏi chặn. Rule còn mở P-26 (mẫu sổ lưu mẫu), P-27 (kiểm thực ba bước) — không làm trong các lát dưới, biểu mẫu in để cấu hình được.

## Thiết kế bố cục Web (PHT dinh dưỡng)

> **Cập nhật 2026-11-02:** menu, luồng, màn hình và ma trận quyền chuẩn nằm ở `docs/business/flows/meal-management-permissions.md` (6 nhóm: Bán trú hôm nay · Thực đơn & dinh dưỡng · Vận hành bếp · Kho thực phẩm · An toàn thực phẩm · Danh mục & thiết lập). Công bố là **cả tuần** (user chốt 02/11) — các chỗ "công bố theo cột ngày" bên dưới đã thay; phần dưới giữ làm lịch sử thiết kế.

Menu nhóm "Bán trú" thay 4 nhóm cũ, sắp theo tần suất dùng:

```text
Bán trú
├─ Bán trú theo ngày        (hub, mở mặc định)
├─ Kế hoạch tuần            (lưới tuần × bữa; công bố theo cột ngày)
├─ Kho khô                  (Tồn theo lô | Phiếu nhập | Lịch sử nhập/trừ)
├─ Hàng tươi                (Bản dự trù | Kiểm nhận)
├─ An toàn thực phẩm        (Duyệt lưu/hủy mẫu | In nhãn, biểu mẫu | Ảnh mẫu bữa)
└─ Danh mục & thiết lập     (Thực phẩm | Món | Bộ thực đơn mẫu | Món thay thế dị ứng |
                             Cấu hình nhóm tuổi: bữa, chuẩn, giá suất | Nhà cung cấp)
```

**Hub "Bán trú theo ngày"** — chọn ngày, hiện các bước dạng thẻ có trạng thái và nút đi thẳng tới việc:

```text
[Ngày 03/11 ▾]
1 Bản dự trù hàng tươi   ✓ đã lưu (kỳ 03–07/11)                 [Xem / In]
2 Số suất                CS1 ✓ 312   CS2 ⏳ chờ xác nhận          [Sổ báo ăn]
3 Thực đơn ngày          Dự kiến ✓ cân đối · ⚠ kho CS2 thiếu 2kg đường
                         [Công bố]  (khóa tới khi 2 cơ sở xác nhận và hết lỗi chặn)
4 Kiểm nhận hàng tươi    lần 1 ✓ · lần 2 —                       [Ghi kiểm nhận]
5 Lưu mẫu / ảnh bữa      2 bản chờ duyệt · ảnh CS1 ✓ CS2 —       [Duyệt]
```

Nguyên tắc thao tác:
- **Một chỗ công bố:** nút Công bố ở hub và ở đầu cột ngày trên Kế hoạch tuần, cùng một hộp thoại: liệt kê lỗi chặn (cân đối, thiếu kho theo cơ sở × thực phẩm) kèm link "Nhập phiếu bổ sung"; cảnh báo không chặn (giá vượt, món lặp). Công bố xong cột ngày khóa.
- **Trạng thái ngày trên lưới tuần:** Nháp → Dự kiến (đã kiểm) → Đã công bố; phụ huynh thấy kế hoạch tuần, ngày đã công bố hiện bản chuẩn.
- **Cảnh báo tại chỗ:** dị ứng, giá, lặp món, thiếu kho hiện ngay ô món khi lập (đã có `AutoChecks`, `WeekChecks` — giữ, nối dữ liệu thật).
- **Kiểm nhận hàng tươi:** một bảng thực phẩm × (cần theo dự trù | cần theo thực đơn chuẩn | thực nhận | đủ/thiếu/thừa); lần 2 chỉ mở sau khi ngày đã công bố.
- **HT:** chỉ xem hub + cấu hình khung giờ ảnh bữa ở "Cấu hình trường".

## Thiết kế App
- **Bếp (theo cơ sở của tài khoản):** Hôm nay = thực đơn đã công bố, số suất theo lớp × bữa, định lượng tổng theo cơ sở, số suất dị ứng/thay thế theo món × lớp (không tên trẻ — AUTH-05); Lưu mẫu (ghi lưu, ghi hủy sau ≥24 giờ, xem trạng thái duyệt); Ảnh bữa (chỉ mở trong khung giờ). Nhãn "Mới" khi suất xác nhận lại sau công bố.
- **Phụ huynh:** Thực đơn tuần (kế hoạch, có nhãn "có thể thay đổi") + ngày đã công bố + ảnh bữa cơ sở con học.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Cách ghép FE↔BE | BE viết contract từ rule, làm theo lát dọc; FE đổi bố cục rồi thay mock bằng API từng lát | (a) BE làm đúng `kitchenApi.js` PROPOSED; (b) làm lại FE hết rồi mới BE | (a) mã hóa luồng cũ (xuất kho, bếp chọn lô, bếp ghi hàng tươi); (b) dồn rủi ro cuối kỳ |
| Mô hình thực đơn | `meal_plan_weeks` (status `DRAFT`→`PUBLISHED`) + món theo ngày × bữa × khối | Trạng thái theo từng ngày (bản 02/11 sáng) | User chốt 02/11: công bố cả tuần |
| Bữa trong ngày | Giữ enum `MORNING/LUNCH/AFTERNOON` của `meal_counts` (V26); cấu hình nhóm tuổi (NUT-17) chọn tập con | Bữa là danh mục cấu hình tự do | Không phải sửa V26 và sổ báo ăn đã chạy; đổi sau bằng migration mới nếu trường cần thêm bữa |
| Sổ kho khô | `stock_lots` + `stock_movements` (`RECEIPT`, `PUBLISH_DEDUCT`, `RECOUNT_ADJUST`), tồn = tổng movement; thiếu khi xác nhận lại suất ghi `stock_shortfalls`, trừ khi có phiếu bổ sung | Cột tồn cập nhật tại chỗ | Lịch sử nhập/trừ là yêu cầu (NUT-11); ledger dễ đối soát |
| Trừ/điều chỉnh kho theo suất | Sổ suất phát `MealCountConfirmedEvent` sau commit; nutrition nghe: lần đầu trừ đủ, xác nhận lại ghi chênh lệch (P-38) | Gọi trực tiếp service nutrition từ `MealCountServiceImpl` | Theo ADR-0004 (event sau commit), tránh phụ thuộc vòng |
| Màn bếp | Chuyển hết sang App, Web bỏ menu bếp | Giữ Web cho bếp | AUTH-21 |

## Lộ trình theo lát (mỗi lát một plan con, verify riêng)

| Lát | Nội dung BE | FE/APP đi kèm | Phụ thuộc |
| --- | --- | --- | --- |
| L1 Danh mục | Cấu hình nhóm tuổi (bữa, chuẩn dinh dưỡng, giá suất, ngày hiệu lực); Thực phẩm (+dinh dưỡng có nguồn, chất dị ứng, Fresh/Stored, hệ số thải bỏ, nhập hàng loạt); Món + định lượng gam/trẻ theo nhóm tuổi (sao chép); NCC | Nhóm "Danh mục & thiết lập" | — |
| L2 Kho khô nhập | Phiếu nhập → lô; tồn theo lô; lịch sử | Kho khô: Tồn, Phiếu nhập, Lịch sử; bỏ Xuất kho | L1 |
| L3 Thực đơn + công bố | Bộ mẫu 4 tuần; kế hoạch tuần + kiểm xác định (NUT-08/09/19/20, cảnh báo kho); món thay thế dị ứng; công bố cả tuần (chặn khi chưa cân đối); trừ kho FIFO khi suất được xác nhận, ghi thiếu, điều chỉnh khi xác nhận lại (P-38) | Kế hoạch tuần, Bán trú hôm nay, hộp thoại công bố | L1, L2, sổ số suất |
| L4 Hàng tươi | Bản dự trù (sĩ số dự kiến điền sẵn); kiểm nhận 2 lần | Hàng tươi: Bản dự trù, Kiểm nhận | L1, L3 |
| L5 Bếp + ATTP | API bếp xem ngày (suất lớp × bữa, định lượng, dị ứng tổng hợp); lưu/hủy mẫu + duyệt + in; ảnh bữa + khung giờ (StorageService) | App bếp; Web duyệt/in; HT cấu hình khung giờ | L3 |
| L6 Phụ huynh | Đọc thực đơn tuần/ngày, ảnh bữa theo cơ sở của con | App phụ huynh | L3, L5 |
| L7 AI (tùy chọn) | Gợi ý kế hoạch tuần qua `integration/` (AI-07), nháp `ai_drafts` | Nút "AI gợi ý" trên Kế hoạch tuần | L3 |

FE làm song song: bước dọn bố cục (mục 1, 2, 3, 7, 11 bảng lệch) không cần BE, làm trước để mock khớp luồng mới.

## Acceptance criteria (mức plan ô — plan con chi tiết hóa)
- **AC-1** (`NUT-08`): Given một ngày trong tuần chưa đạt cân đối, When PHT công bố tuần, Then bị chặn và nêu ngày, nhóm tuổi chưa đạt; đạt hết ⇒ cả tuần PUBLISHED, không sửa được.
- **AC-2** (`NUT-11`, P-38): Given tuần đã công bố, When suất ngày D của cơ sở X được xác nhận, Then kho khô X bị trừ đúng định lượng × suất, lô hết hạn trước, một lần; kho thiếu ⇒ xác nhận vẫn thành công, phần thiếu được ghi.
- **AC-3** (`NUT-11`, `ATT-07`): When suất D của X được xác nhận lại, Then chỉ ghi chênh lệch.
- **AC-4** (`AUTH-05`, `NUT-23`): API bếp không trả tên/ID trẻ.
- **AC-5** (`AUTH-21`): Web không còn menu/route cho bếp.

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Trừ kho sai khi công bố/xác nhận lại | Tồn kho lệch | Ledger + test AC-2/AC-3; công bố trong 1 transaction | Movement đảo (không xóa) |
| FE đổi bố cục khi team khác đang sửa màn bán trú | Conflict | Làm theo nhóm route, PR nhỏ | Revert PR |
| Khối lượng lớn so với thời gian Capstone | Trễ | Lát theo thứ tự; L6/L7 cắt được | — |

## Validation log
- [x] Khẳng định về code có `file:line`/đường dẫn
- [x] Đổi contract ⇒ consumer cụ thể (FE `kitchenApi.js`, `menuPlanningApi.js`, menuConfig; APP màn bếp/phụ huynh)
- [x] >8 file, >3 phase ⇒ tách plan con theo lát
- [x] Không dùng rule PENDING/OPEN như đã chốt (P-26, P-27 để ngoài)
- [ ] Mỗi AC có test — làm ở plan con

## Progress log
### 2026-11-02 — Brainstorm (chờ user soát)
- Commit: chưa commit
- Giả định agent tự chọn: 6 dòng "Key decisions"; thứ tự lát L1→L7; bố cục menu "Bán trú" và hub theo ngày.
- Phát hiện: `meal_counts.session` cố định 3 bữa (V26) trong khi NUT-17 cho cấu hình bữa — chọn giữ enum; chưa có event `MealCountConfirmedEvent` — L3 thêm.
- Tiếp: user soát → tạo plan con L1 (BE) và plan FE dọn bố cục.

### 2026-11-02 tối — User chốt công bố cả tuần
- Đã cập nhật NUT-07/08/11/12, PAR-01, thêm P-38 (thời điểm trừ kho), P-39 (sửa sau công bố) ở `BUSINESS_RULES.md`; flow, module card, GLOSSARY, DOMAIN_MODEL, ADR-0009, CROSS_REPO_MAP, CURRENT_STATE.
- Thêm `docs/business/flows/meal-management-permissions.md`: menu 6 nhóm, luồng 8 bước, màn hình, mã quyền, ma trận quyền (C/P).
- Giả định agent: trừ kho khi xác nhận suất, thiếu không chặn (P-38); mã quyền mới `MENU_MANAGE`, `STOCK_MANAGE`, `FOOD_SAFETY_APPROVE`, `KITCHEN_RECORD`, `MENU_PUBLISHED_EDIT` (P).

### 2026-11-02 — Plan con L1 (chờ user duyệt)
- BE: `docs/plans/completed/2026-11-02-day-boarding-l1-catalog.md`
- FE: `CareNest_FE:docs/plans/active/2026-11-02-day-boarding-l1-catalog-fe.md` (gồm dọn bố cục Phase 1)
