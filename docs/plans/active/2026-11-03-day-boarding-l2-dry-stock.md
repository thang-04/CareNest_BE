---
title: Bán trú L2 — kho khô theo lô, phiếu nhập, sổ nhập/trừ (BE)
status: in-progress
owner: thangnd
jira: G94-192 / BE-FEAT-27   # FE: G94-191 / FE-FEAT-48
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition, identity-access]
rules: [NUT-10, NUT-11, NUT-12, AUTH-02, AUTH-05, AUTH-20, AUTH-21, SCH-06]
risk: high
created: 2026-11-03
updated: 2026-11-03
---
# Bán trú L2 — kho khô theo lô, phiếu nhập, sổ nhập/trừ (BE)

Plan con của `2026-11-02-day-boarding-redesign.md` (lát L2). Làm cùng đợt với L3 (`2026-11-03-day-boarding-l3-weekly-menu.md`) — user chọn "L2 + L3 đủ" ngày 03/11. Plan FE: `CareNest_FE:docs/plans/active/2026-11-03-day-boarding-l2-l3-fe.md`.

## Context
L3 trừ kho khô khi suất được xác nhận (P-38) nên cần lô và sổ bút toán trước. Hiện `dev` (`0cfc1c6`) chỉ có danh mục L1: `foods.storage` `FRESH|DRY` (`V54__create_nutrition_catalog.sql:47`), `foods.purchase_unit`/`purchase_unit_size`, `suppliers` (V54). Không có bảng kho (grep `stock_` trong `db/migration` = 0). Quyền theo điểm trường đã có cơ chế: `CampusAccessPolicy.canEditCampus(user, permission, campusId)` (`security/authorization/CampusAccessPolicy.java:72`), `Permission.MEAL_COUNT_CONFIRM` là mẫu quyền `campusScoped` (`utils/enums/Permission.java`). FE có màn demo `DryStockPage`, `StockReceiptListPage/FormPage` (route `/kitchen/dry-stock`, `/kitchen/stock-receipts`).

## Quyết định đã chốt
1. NUT-11 (lô, không chuyển cơ sở, không kiểm kê, Capstone: nhập, trừ tự động, tồn theo lô, lịch sử), P-38 — user 2026-10-21, 2026-11-02.
2. Làm L2 + L3 một đợt, nhánh L1 fast-forward lên `dev`, mã Jira như L1 — user 2026-11-03.

## Làm rõ nghiệp vụ
- **Actor:** PHT có `STOCK_MANAGE` (điểm trường, HT cấp, mặc định tắt) nhập phiếu; HT có sẵn; PHT khác xem mọi điểm trường (AUTH-20); bếp không dùng Web (AUTH-21).
- **Outcome:** tồn kho khô theo lô từng điểm trường, lịch sử nhập/trừ đối soát được; L3 trừ theo lô hết hạn trước.
- **Flow:** [B] NCC giao hàng khô, bếp kiểm → [S] PHT lập phiếu nháp (NCC, ngày nhận, dòng: thực phẩm DRY, số lô, hạn dùng, số lượng, giá) → [S] xác nhận ⇒ tạo lô + bút toán `RECEIPT` → (L3) trừ tiếp phần thiếu. Sai phiếu đã xác nhận ⇒ hủy bằng bút toán đảo khi lô chưa bị trừ, lý do bắt buộc.
- **Ảnh hưởng:** chỉ module nutrition (bảng mới). FE: `/kitchen/dry-stock`, `/kitchen/stock-receipts*`, thêm `/kitchen/stock-history`. APP: không. Dữ liệu cũ: không.

### Câu hỏi mở
Không có câu hỏi chặn. Hai điểm ma trận quyền ghi P (sửa/xóa phiếu nháp, hủy bằng bút toán đảo) làm theo đề xuất, ghi ở Key decisions để user soát.

## Thiết kế
**V56__create_dry_stock.sql** (mọi bảng `created_at`; bút toán không sửa, không xóa):
- `stock_receipts(campus_id FK, supplier_id FK NOT NULL, received_date, status DRAFT|CONFIRMED|CANCELLED, note, confirmed_by/at, cancelled_by/at, cancel_reason, created_by, updated_by, updated_at)`.
- `stock_receipt_lines(receipt_id FK CASCADE, food_id FK, lot_no, expiry_date, quantity NUMERIC(14,2) > 0, unit_price NUMERIC(12,2) ≥ 0)` — `quantity` theo đơn vị gốc của thực phẩm (g/ml), `unit_price` theo đơn vị mua (khớp `foods.unit_price`).
- `stock_lots(campus_id, food_id, receipt_line_id UK, supplier_id, lot_no, expiry_date, quantity, unit_price, received_date)`; index `(campus_id, food_id, expiry_date, id)`.
- `stock_movements(lot_id FK NOT NULL, campus_id, food_id, movement_type RECEIPT|DEDUCT|ADJUST|SHORTFALL_DEDUCT|RECEIPT_CANCEL, quantity ≠ 0 có dấu, meal_date NULL, receipt_id NULL, reason, created_by NULL)` — 3 loại trừ dùng ở L3, khai sẵn CHECK để L3 không phải ALTER.
- Tồn lô = Σ `stock_movements.quantity` của lô (không cột tồn cập nhật tại chỗ — Key decision plan ô).

**Quyền:** `Permission.STOCK_MANAGE` (module `meals`, default `PRINCIPAL`, configurable `VICE_PRINCIPAL`, `campusScoped = true`), không seed `role_permissions` ⇒ PHT mặc định tắt. Đọc: HT, PHT còn phân công (`canReadCampus`). Bếp/GV: 403.

**API** (`/nutrition/stock`, `ResponseJson`):

| Method · path | Quyền | Ghi chú |
| --- | --- | --- |
| `GET /receipts?campusId&from&to&status` · `GET /receipts/{id}` | đọc | Có `canManage` theo điểm trường |
| `POST /receipts` · `PUT /receipts/{id}` · `DELETE /receipts/{id}` | MANAGE (điểm trường) | Chỉ `DRAFT`; dòng chỉ nhận thực phẩm `ACTIVE` + `DRY` (NUT-10); hạn dùng ≥ ngày nhận |
| `POST /receipts/{id}/confirm` | MANAGE | Tạo lô + `RECEIPT`; khóa campus (`SELECT … FOR UPDATE`) để tuần tự với trừ kho L3 |
| `POST /receipts/{id}/cancel` `{reason}` | MANAGE | Chỉ `CONFIRMED` và mọi lô của phiếu chưa có bút toán khác `RECEIPT` ⇒ `RECEIPT_CANCEL` âm, `CANCELLED` |
| `GET /lots?campusId&foodId&includeEmpty` | đọc | Tồn từng lô (Σ bút toán), sắp hạn dùng tăng dần; kèm đơn vị mua để FE quy đổi |
| `GET /movements?campusId&from&to&foodId&type` | đọc | Lịch sử nhập/trừ, mới nhất trước, phân trang `page,size` |

`ApiCode` 6027–6033: không tìm thấy phiếu, phiếu không ở trạng thái cho phép, thực phẩm không phải kho khô, hạn dùng trước ngày nhận, phiếu rỗng, không hủy được vì lô đã bị trừ, không có quyền kho điểm trường.

## Acceptance criteria
- **AC-1** (`AUTH-20`): Given PHT không có `STOCK_MANAGE` ở điểm trường X, When `POST /nutrition/stock/receipts` cho X, Then 403; `GET /lots?campusId=X` vẫn 200.
- **AC-2** (`NUT-10`): Given dòng phiếu là thực phẩm `FRESH`, Then 400 mã 6029.
- **AC-3** (`NUT-11`): Given phiếu nháp 2 dòng, When xác nhận, Then 2 lô + 2 bút toán `RECEIPT`; tồn lô = số lượng; phiếu khóa sửa (409).
- **AC-4** (`NUT-11`): Given phiếu đã xác nhận có lô đã bị trừ, When hủy, Then 409 mã 6032; lô chưa bị trừ ⇒ bút toán đảo, tồn = 0, lý do bắt buộc.
- **AC-5** (`NUT-11`): `GET /lots` trả tồn = Σ bút toán, sắp hạn dùng tăng dần; `GET /movements` lọc được theo loại.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Đơn vị lưu | Đơn vị gốc g/ml; FE nhập/hiện theo đơn vị mua (`purchase_unit_size`) | Lưu theo đơn vị mua (L1 ghi "kho tính theo đơn vị mua") | Trừ kho = gam × suất, giữ số chính xác, không làm tròn hai lần |
| Phiếu nháp | `DRAFT` sửa/xóa được, xác nhận mới tạo lô | Tạo là xác nhận luôn | Ma trận quyền mục 5 (P) |
| Sửa sau xác nhận | Hủy cả phiếu bằng bút toán đảo khi lô chưa bị trừ | Sửa dòng tại chỗ | Không sửa bút toán (NUT-11, mục 6 "Ngoại lệ") |
| Tuần tự hóa | Khóa dòng `campuses` FOR UPDATE cho mọi thao tác kho của điểm trường | Advisory lock Postgres | JPA thuần, test Testcontainers chạy được, lượng ghi nhỏ |

## Phases
### Phase 1 — Schema, quyền, nhập phiếu
- Files: C `db/migration/V56__create_dry_stock.sql` · M `utils/enums/Permission.java`, `utils/ApiCode.java` · C `entity/meals/{StockReceipt,StockReceiptLine,StockLot,StockMovement}.java`, `utils/enums/{StockReceiptStatus,StockMovementType}.java` · C `repository/meals/{StockReceipt,StockLot,StockMovement}Repository.java` · C `service/meals/StockReceiptService.java` + impl · C `controller/meals/StockController.java` · C `dto/meals/Stock*`
- Tests: AC-1..AC-4 → `StockReceiptServiceImplTest` (unit), `DryStockIntegrationTest` (Testcontainers)
### Phase 2 — Tồn theo lô, lịch sử, contract
- Files: M `StockController`, C `service/meals/StockQueryService.java` + impl · M `docs/api/openapi.yaml` (snapshot), `docs/modules/nutrition.md`, `docs/database/DATA_DICTIONARY.md`, `docs/system/CROSS_REPO_MAP.md`, `db/dev/R__dev_seed.sql` (vài phiếu đã xác nhận để demo)
- Tests: AC-5 → `DryStockIntegrationTest#lotsSortedByExpiryWithLedgerBalance`; `OpenApiSnapshotTest`
- Exit (cả 2 phase): `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0; `node scripts/check-ai-layer.mjs` 0 lỗi. **Dừng chờ review** (gộp review với L3 Phase 1 nếu user muốn chạy một mạch).

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | AUTH-20 | `DryStockIntegrationTest#receiptRequiresStockManageButLotsAreReadable` | 1 | PASS |
| AC-2 | NUT-10 | `DryStockIntegrationTest#rejectsFreshFoodAndExpiryBeforeReceipt` | 1 | PASS |
| AC-3 | NUT-11 | `DryStockIntegrationTest#confirmCreatesLotsAndReceiptMovements` | 1 | PASS |
| AC-4 | NUT-11 | `DryStockIntegrationTest#cancelReversesUntouchedLotsAndIsBlockedAfterDeduction` | 1 | PASS |
| AC-5 | NUT-11 | `DryStockIntegrationTest#confirmCreatesLotsAndReceiptMovements`, `#confirmDeductsFifoOnce` | 2 | PASS |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Nhánh khác cũng thêm V56 trước khi merge | Trùng số migration | Kiểm `origin/dev` trước khi mở PR; đổi số khi chưa lên remote (tiền lệ L1) | Đổi số file |
| Tồn tính bằng Σ chậm khi dữ liệu lớn | Chậm màn tồn | Index theo lô; dữ liệu 2 điểm trường nhỏ | Thêm cột tồn cache bằng migration mới |

## Validation log
- [x] Khẳng định về code có `file:line`/đường dẫn
- [x] Đổi contract ⇒ consumer cụ thể: FE `src/pages/kitchen/{DryStockPage,StockReceiptListPage,StockReceiptFormPage}.jsx` (nay demo)
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder

## Progress log
### 2026-11-03 — Plan duyệt (user thangnd, chạy một mạch; chặn công bố khi chưa đặt định mức hoặc thiếu số liệu — user chọn)
- Commit: chưa commit. Nhánh đã fast-forward lên `origin/dev` `0cfc1c6`.

### 2026-11-03 — Phase 1–2 done (chạy một mạch theo user)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 777 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 377s`
- Khác plan: AC-1, AC-2 kiểm ở `DryStockIntegrationTest` (Testcontainers) thay test unit riêng — cùng hành vi, đỡ mock `CampusAccessPolicy`; `GET /movements` mặc định 30 ngày gần nhất; khóa campus bằng truy vấn native `select … for update` trong `StockLotRepository` (meals không được dùng repository organization — ArchUnit).
- Giả định agent (chờ soát): bỏ qua lô đã hết hạn trước ngày ăn khi trừ; đổi điểm trường của phiếu nháp ⇒ 409 6028 (lập phiếu mới).
- Seed dev: chưa thêm phiếu nhập — demo lập qua màn Phiếu nhập.
