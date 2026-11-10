---
title: Cổng tích hợp FE↔BE — chặn màn non-mock không nối BE
status: in-progress
owner: thangnd
jira: G94-272
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [cross-repo]
rules: []
risk: high
created: 2026-11-01
updated: 2026-11-01
---
# Cổng tích hợp FE↔BE — chặn màn non-mock không nối BE

## Context

Incident `docs/knowledge/incidents/BUG-261101-web-nonmock-unwired-screens.md`. Web prod chạy `VITE_USE_MOCK=false` + gateway; 224/334 lời gọi HTTP của FE (`origin/dev` + 2 màn vừa nối) không có route BE; `schoolConfigService` lặng lẽ dùng mock khi gateway. Khoảng trống đã ghi ở ≥5 tài liệu nhưng không cổng nào chặn: harness FE chỉ chạy mock, smoke deploy không chạm API nghiệp vụ, quyết định bật cờ gắn với "BE lên" chứ không với mức tích hợp từng màn.

## Kết quả rà soát (2026-11-01, tĩnh — chưa đăng nhập prod)

| Nhóm menu | Trạng thái prod | Nguyên nhân |
| --- | --- | --- |
| Cấu hình trường: Năm học, Xếp lớp năm mới, Nhóm tuổi & lớp, Phòng ban, Giờ chốt, Điểm trường, Phân công GV | Route khớp BE (qua `beClient`) | — |
| Cấu hình trường: Tài khoản nhân sự, Phân công PHT | **Dữ liệu giả** (localStorage), lưu không tới BE | `NOT_IN_BE` fallback; BE có `/accounts`, `/staff-assignments` |
| Vai trò & quyền | Đã nối (FE G94-272) | Trước: 501 cứng |
| Quản lý trẻ: Tài khoản phụ huynh | Đã nối (FE G94-272); deep link `?child=` còn lỗi | Trước: 501 cứng; `/children/{id}/profile` BE không có |
| Quản lý trẻ: Danh sách, Tiếp nhận, Xếp lớp, Khám/Theo dõi/Xu hướng sức khỏe | Lỗi 404 | BE chỉ có `GET /children`, `GET /children/{id}`; chưa có health-check |
| Điểm danh, Tổng hợp, Mở lại, Sổ báo ăn, Sức khỏe trong ngày | Route khớp BE (qua `beClient`) | Bàn giao bữa (`/meal-handovers`) 404 |
| Đón trẻ | Lỗi 404 | BE chưa có pickup |
| Kế hoạch giáo dục | Xem được; sửa/xóa chủ đề, giáo án, AI gợi ý tên nhánh, bước giáo án lỗi | 8/15 endpoint chưa có |
| Đánh giá trẻ (toàn bộ) | Lỗi 404 | BE chưa có assessment |
| Danh mục thực phẩm, Dinh dưỡng, Bếp (toàn bộ) | Lỗi 404 / 501 | BE chưa có menu/kitchen |
| Chung: chuông thông báo, chữ ký, giao diện trường, `getUsers` (`/users`) | Lỗi 404 | BE chưa có / lệch đường dẫn (`/accounts`) |
| CSVC | Ẩn (`FACILITY_ENABLED=false`, ngoài V1) | — |

Route khớp ≠ DTO khớp: 110 lời gọi khớp đường dẫn chưa được kiểm shape (cần smoke thật).

## Phương án

- **A. Cổng contract tĩnh (khuyến nghị làm trước).** Script FE `scripts/check-api-contract.mjs` trong `verify`: trích mọi lời gọi `axiosClient`/`beClient` → so với route BE (`BE:docs/api/openapi.yaml`, đã có snapshot) → lời gọi không có route phải nằm trong `src/config/api-pending.json` (module, endpoint, task/owner). Cấm fallback mock khi `!USE_MOCK` (test). Rẻ, chạy mỗi lần verify, bắt đúng lớp lỗi này. Không bắt lệch DTO.
- **B. Trạng thái tích hợp theo module (đi cùng A).** Thay một cờ toàn cục bằng manifest module → `api` | `mock` | `off`, sinh từ A. Module chưa có BE **vẫn hiển thị menu và màn** (user chốt 2026-11-01: không ẩn); cách hiển thị chờ user chọn (câu hỏi mở). Không còn fallback mock lặng lẽ: chế độ nào cũng phải thấy rõ trên màn. Quyết định chuyển module sang `api` = manifest xanh, không phải "BE lên".
- **C. Smoke E2E sau deploy.** Playwright đăng nhập user seed, đi mọi route menu, fail nếu `ErrorState`/4xx/5xx. Bắt cả lệch DTO; cần tài khoản test + secret CI, chạy chậm. Làm sau A+B.

Khuyến nghị: A + B ngay (FE, 1 lượt), C khi có tài khoản test cho CI. Kèm quy trình: `.ai/workflows/update-api.md` bước 7 BE ⇒ endpoint mới cập nhật `api-pending`/task FE thay vì chỉ "liệt kê"; FE DoD: màn mới non-mock phải có route BE hoặc khai `api-pending`.

## Việc khẩn (độc lập phương án)

- Bỏ fallback mock lặng lẽ của Tài khoản nhân sự, Phân công PHT (nối `/accounts`, `/staff-assignments` hoặc báo rõ "chưa có").
- 2 màn (phân quyền, phụ huynh) + cổng: repo FE, G94-272 (nhánh `dev`); web deploy khi push `dev` FE.

## Câu hỏi mở

- [x] Phương án: A + B (chế độ tự chạy chọn khuyến nghị; user chốt hiển thị demo). Mã Jira: G94-272 (user chốt 2026-11-01).
- [x] Màn chưa có BE: dữ liệu demo kèm nhãn "Dữ liệu demo" (user chọn 2026-11-01).

## Progress log

- 2026-11-01: rà soát tĩnh FE `origin/dev` + worktree vs BE `dev`; ghi incident; plan draft chờ user chọn.
- 2026-11-01: user chốt module chưa có BE không ẩn, vẫn hiển thị.
- 2026-11-01: làm A + B ở worktree FE `CareNest_FE-gateway-auth-vps` (FE G94-272):
  - Manifest `src/config/integrationModules.js` (8 module `api`, 16 `demo`, 24 lời gọi `pending`); `src/services/demoSource.js` (`sourceOf`, tài khoản mẫu cùng vai trò cho user BE, `demoModuleOfPath`, `needsDemoLabel`).
  - Facade module demo chuyển sang `sourceOf`; bỏ fallback mock lặng lẽ `NOT_IN_BE` (Tài khoản nhân sự, Phân công PHT thành module demo có nhãn); `/users` → `master-data-users` demo; bàn giao bữa → `meal-handover` demo.
  - Nhãn: `src/layouts/DemoDataBanner.jsx` trên layout theo route; SignaturePicker; khung "Việc hôm nay của lớp" (dashboard GV).
  - Cổng `scripts/check-api-contract.mjs` trong `npm run check`, ảnh chụp `docs/integration/be-api-routes.json` (119 route từ `docs/api/openapi.yaml`). Test `tests/hardening/integration-gate.test.mjs` (5, gồm fixture lỗi phải fail).
  - Kiểm chứng: `node scripts/verify.mjs` FE PASS (378 test); trình duyệt local `VITE_USE_MOCK=false` + auth mock: `/kitchen/suppliers`, `/school/staff` hiện nhãn + dữ liệu demo, `/school/years` (api) không nhãn; console chỉ lỗi kết nối BE (không chạy BE local).
  - Chưa làm: C (smoke E2E sau deploy); chưa kiểm với user BE thật trên prod (map tài khoản mẫu chỉ có unit test). Module `api` (attendance, daily-health, education-plan, account) vẫn dùng `USE_MOCK` toàn cục — tương đương manifest, chưa chuyển sang `sourceOf`.
- 2026-11-01: user chốt commit G94-272 thẳng lên `dev` cả 2 repo. BE: commit tài liệu lên `dev`. FE (user: pull `dev` trước, nghiệp vụ ưu tiên bản nqtienvn): cất WIP checkout chính vào stash, merge `origin/dev` (13 xung đột chỉ ở phần đăng nhập — commit local cũ `8896d90` bị thay bằng bản đã review trên `origin/dev`, kết quả trùng cây `origin/dev`), cherry-pick 3 commit G94-272, đồng bộ route BE (120). `node scripts/verify.mjs` FE PASS (386 test); cần `npm ci --legacy-peer-deps` vì node_modules thiếu `@dnd-kit` upstream mới thêm. WIP cũ để lại trong stash (16/22 file khác bản mới).
