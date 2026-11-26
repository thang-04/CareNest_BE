---
title: Cân đối khẩu phần theo ngày (hai lần) cho luồng bán trú
status: in-progress
owner: thangnd
jira: G94-488 / BE-FEAT-185   # chuỗi G94-487…493; FE: FE-FEAT-185
branch: feature/G94-488-BE-FEAT-185-day-ration-balance
modules: [nutrition]
rules: [NUT-07, NUT-08, NUT-11, NUT-12, NUT-19, NUT-24, NUT-27, NUT-31, INS-01, P-39]
risk: high
created: 2026-11-26
updated: 2026-11-26
---
# Cân đối khẩu phần theo ngày (hai lần) cho luồng bán trú

## Context
Dự trù, đi chợ, kiểm nhận và trừ kho tính từ công thức món trong kế hoạch tuần: không sửa được gam theo ngày, không làm
tròn được lượng mua. Luồng đích: kế hoạch tuần (chỉ chọn món) → cân đối lần 1 → công bố → dự trù → kiểm nhận lần 1 →
chốt suất → cân đối lần 2 → kiểm nhận lần 2. Plan FE: `FE:docs/plans/active/2026-11-26-day-ration-balance.md`.

## Quyết định đã chốt
1. Công bố vẫn bắt buộc cân đối đạt — user, 26/11/2026.
2. Tiền đạt khi nằm trong biên ± % quanh ngân sách thực phẩm, cấu hình được (mặc định 5%) — user, 26/11/2026.
3. Nhập số trẻ dự kiến theo điểm trường trước khi cân đối; cân đối một lần cho cả trường — user, 26/11/2026.
4. P:L:G tính như phần mềm tham chiếu, tổng 100% — user, 26/11/2026.
5. Làm tròn trên tổng toàn trường rồi tính ngược gam và chia điểm trường — user, 26/11/2026.
6. Lần 2 giữ gam mỗi trẻ, tính lại theo suất chốt; chưa đạt chỉ cảnh báo — user, 26/11/2026.
7. Kiểm nhận lần 2 gợi ý và ghi cách xử lý chênh lệch — user, 26/11/2026.
8. Định mức gam theo chất chỉ để tham khảo — user, 26/11/2026.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** PHT phụ trách dinh dưỡng cân đối và công bố; PHT điểm trường kiểm nhận; hệ thống tự lập dự trù và
  trừ kho. Rule NUT-31 (mới) và các bổ sung 26/11 trong `BUSINESS_RULES.md`.
- **Ảnh hưởng:** FE `/menu/weekly`, `/menu/nutrition`, `/menu/statistics`, `/kitchen/day-market`, `/kitchen/forecast`,
  `/kitchen/fresh-receipts`; APP đọc dữ liệu bếp, hình dạng response bếp không đổi. Tuần đã công bố không có bản cân đối
  vẫn tính theo công thức.

## Acceptance criteria
- **AC-1** (`NUT-24`): P:L:G tổng 100%, tính từ ba chất.
- **AC-2** (`NUT-19`): tiền đạt đúng trong biên cấu hình.
- **AC-3** (`NUT-31`): làm tròn trên tổng toàn trường, làm tròn lại không cộng dồn, phần các điểm trường khớp tổng.
- **AC-4** (`NUT-08`): thiếu, cũ hoặc chưa đạt cân đối thì chặn công bố; công bố xong có bản dự trù.
- **AC-5** (`NUT-31`): lần 2 chỉ mở khi mọi điểm trường chốt suất; chốt lại thì bản lần 2 cũ.
- **AC-6** (`NUT-12`): dòng lệch phải chọn cách xử lý; hệ thống tính số thực nhận và đủ / thiếu / thừa.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nơi lưu | Bảng `day_balances*` riêng (V199) | Ghi đè công thức món | Công thức dùng chung nhiều ngày |
| Trạng thái "cũ" | Tính khi đọc | Cột trạng thái | Không phải đồng bộ khi đổi món, chốt lại suất |
| Nguồn nhu cầu | `DayDemand` dùng chung | Mỗi service tự đọc | Một nơi quyết định lần 2, lần 1 hay công thức |
| Việc sau công bố, sau lưu | Listener sau commit | Gọi trực tiếp | Lỗi dự trù, kho không hủy công bố, cân đối |

## Phases
### Phase 1 — Đánh giá (G94-488)
- `MealPlanEvaluator` ba kết luận, hệ số quy đổi; V198 biên tiền.
### Phase 2 — Bản cân đối lần 1 (G94-489)
- `DayBalance*`, `/nutrition/day-balances`, V199.
### Phase 3 — Công bố và nguồn số liệu (G94-491)
- `DayBalanceGate`, `DayDemand`, tự lập dự trù.
### Phase 4 — Lần 2 và kiểm nhận (G94-492)
- Cân đối lần 2, xử lý chênh lệch, V200.
### Phase 5 — Tài liệu và kiểm chạy (G94-493)
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`; chạy luồng trên trình duyệt.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2 | NUT-24, NUT-19 | `MealPlanEvaluatorTest` | 1 | xem Progress log |
| AC-3 | NUT-31 | `DayBalanceCalculatorTest`, `DayBalanceIntegrationTest` | 2 | xem Progress log |
| AC-4 | NUT-08 | `MealPlanIntegrationTest`, `DayBalanceIntegrationTest` | 3 | xem Progress log |
| AC-5, AC-6 | NUT-31, NUT-12 | `DayBalanceIntegrationTest` | 4 | xem Progress log |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Một điểm trường chốt suất muộn | Lần 2 của cả trường chờ | Kiểm nhận hiện lý do chờ | — |
| Nhánh khác dùng V197 trong khi nhánh này dùng V198–V200 | Flyway lệch thứ tự khi merge | Kiểm số migration trước khi merge, đổi số nếu cần | Migration chưa lên môi trường chung thì đổi số an toàn |
| FE chưa deploy cùng | Công bố bị chặn mà chưa có màn cân đối | Deploy BE cùng FE | Revert PR BE |

## Validation log
- [x] Đổi contract ⇒ consumer: FE các route ở mục Ảnh hưởng
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] >3 phase ⇒ tách task Jira G94-487…493

## Progress log
### 2026-11-26 — Phase 1–4 (chờ review)
- Commit: chưa commit.
- Verify: ghi sau khi chạy `./mvnw verify`.
- Khác plan: màn cân đối chưa có nút in; công tắc đồng bộ bữa chung không áp cho gợi ý tuần bằng AI.
