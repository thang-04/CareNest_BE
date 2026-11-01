# Quickstart — feature 004

## Lệnh
- BE: `cd CareNest_BE && node scripts/verify.mjs` (spotless/compile/test; integration Testcontainers cần Docker).
- FE: `cd CareNest_FE && node scripts/verify.mjs`; riêng `node --test tests/education-plan/plan-versions.test.mjs`.
- E2E real-BE (khi dev stack chạy): `npx playwright test tests/e2e/system` trong CareNest_FE.

## Ma trận chấp nhận
| Ca | Mong đợi | Kiểm ở |
| --- | --- | --- |
| Lưu ô điều chỉnh khi APPROVED | 200, status/approvals/content giữ, history +1 | BE IT, FE mock test |
| GV khác lớp lưu ô điều chỉnh | 7103 | BE IT |
| Tạo v2 từ v1 APPROVED | v2 DRAFT đủ cây, lineage | BE IT |
| Tạo v3 khi v2 đang soạn | 7028 | BE IT |
| Tạo từ bản không mới nhất | 7027 | BE IT |
| So sánh đổi chữ/xóa/tích/chủ đề/chèn MT | phân loại đúng, đánh số lại không đổi | BE unit diff, FE mock test |
| Lưu tài liệu trên v1 sau khi v2 ban hành | 7122 | BE IT |
| Chép kế hoạch chủ đề v1 → v2 | remap id, ô đỏ khớp | FE model test |
| Gửi bản chép còn ô đỏ | 7121; hết ô đỏ thì gửi được | BE IT, FE test |
| Chủ đề đã kết thúc | không đỏ, không chặn | FE model test, BE IT |

## Kết quả Playwright real-BE (T035, 28/10/2026)
- Chạy trên BE dựng từ working copy (cổng 8081, DB bản sao `carenest_e2e005`), cùng đợt feature 005.
- `tests/e2e/system/edu-plan-version-ministry.spec.mjs`: ST-13 tạo phiên bản điều chỉnh từ bản đã ban hành (bản cũ giữ APPROVED), ST-14 sửa V2 + lấy từ Chương trình của Bộ + Lưu, ST-15 gửi → Phó HT ban hành V2 (`current` chuyển sang V2) — pass.
- Bắt được lỗi FE: V2 bị coi là trùng nhóm tuổi ⇒ không sang bước 2, không Lưu được (FE `FE-BUG-261028-version-plan-dup-age-group`, đã sửa).
- Toàn bộ suite: 49 test; lần cuối 44 pass + ST-08 chập chờn (đăng nhập quá giờ), chạy lại system 17/17 pass.
