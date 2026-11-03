---
id: BUG-261101-web-nonmock-unwired-screens
type: bug
modules: [identity-access, child, cross-repo]
rules: [AUTH-18, AUTH-19]
status: open
date: 2026-11-01
keywords: [Không tải được dữ liệu, VITE_USE_MOCK, mock, 501, cần thống nhất contract BE, NOT_IN_BE, beClient, SCHOOL_CONFIG_API, 404, tích hợp FE]
similar_to: []
---

# BUG-261101-web-nonmock-unwired-screens — Web production báo "Không tải được dữ liệu" / hiện dữ liệu giả dù đã dùng DB thật

## Symptom
web.carenest.edu.vn (build `VITE_AUTH_MODE=gateway`, `VITE_USE_MOCK=false`), màn `/school/roles`:
```text
Không tải được dữ liệu — Màn phân quyền này chỉ là demo; cần thống nhất contract BE.
```
User báo 2026-11-01. Các màn khác lỗi 404 hoặc **lặng lẽ đọc mock localStorage** (không báo lỗi).

## Điều kiện tái hiện
Build FE `origin/dev` với `VITE_AUTH_MODE=gateway`, `VITE_USE_MOCK=false`, BE `dev` trên VPS; đăng nhập HT; mở các menu. Kiểm tĩnh: đối chiếu lời gọi `axiosClient/beClient` của FE với `@*Mapping` BE (script so route, 2026-11-01: 224/334 lời gọi FE không có route BE).

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Mở trang production không đăng nhập | Chỉ thấy trang login | Agent không được nhập tài khoản trên site thật ⇒ chuyển sang kiểm tĩnh bundle + source |
| 2 | Đối chiếu route trên repo FE local | Kết quả sai | Checkout FE local chậm 87 commit so với `origin/dev`; phải dùng `git archive origin/dev` hoặc worktree khớp `origin/dev` |
| 3 | Chỉ xét cờ `USE_MOCK` | Sót lỗi | Prod chạy tổ hợp 3 chế độ: `USE_MOCK=false` (axiosClient), `SCHOOL_CONFIG_API=true` khi gateway (beClient), và fallback mock `NOT_IN_BE` trong `schoolConfigService` |

## Root cause
1. Một cờ toàn cục `VITE_USE_MOCK=false` bật cho mọi màn khi "BE lên" (quyết định FE 2026-10-19, `FE:docs/plans/active/2026-10-19-vps-docker-deploy.md:25`), không theo mức tích hợp từng module.
2. FE còn 3 kiểu không nối: service non-mock trả cứng 501 (`permissionService`, `guardianService` — đã sửa 2026-11-01, commit trên repo FE, G94-272 (nhánh `dev`); `kitchenService` legacy), gọi endpoint BE không có (assessment, health-checks, kitchen, menu, pickup, signatures, notifications, `/users*`, `GET /classes`, …), và fallback mock lặng lẽ khi gateway (`FE:src/services/school-config/schoolConfigService.js` `NOT_IN_BE` ⇒ Tài khoản nhân sự, Phân công PHT dùng localStorage).
3. Harness chỉ chạy mock: `FE:scripts/auth-test-loader.mjs` cố định `USE_MOCK = true`, `SCHOOL_CONFIG_API = false`; `permission-center.test.mjs` còn khẳng định 501 là đúng. Smoke deploy FE chỉ `curl` HTTPS 200, BE chỉ issuer/`/api/auth/csrf`.
4. Khoảng trống đã được ghi ở ≥5 chỗ (`CROSS_REPO_MAP.md` 2026-10-22, `docs/quality/reports/2026-10-22-auth-fe-integration-gap.md`, plan `2026-10-16-permission-management.md` "chưa thử FE↔BE thật", `FE:docs/context/CURRENT_STATE.md`, `FE:docs/features/permission-center-demo.md`) nhưng chỉ là ghi chú, không phải điều kiện chặn bật cờ; không task/owner cho bước ghép.

## Fix
Đang mở (code commit trên repo FE, G94-272 (nhánh `dev`); plan `docs/plans/active/2026-11-01-fe-be-integration-gate.md`):
- Nối màn phân quyền + tài khoản phụ huynh vào BE.
- Thay cờ toàn cục bằng manifest từng module ở repo FE (`integrationModules.js` trong thư mục config của src): module chưa có BE vẫn hiển thị, dùng dữ liệu demo + nhãn "Dữ liệu demo" (user chốt), tài khoản mẫu cùng vai trò thay user BE; bỏ fallback mock lặng lẽ.
- Cổng repo FE `check-api-contract.mjs` (thư mục scripts) trong `npm run check`: lời gọi của module `api` phải có route BE (ảnh chụp từ `docs/api/openapi.yaml`) hoặc khai `pending`.
- `.ai/workflows/update-api.md` bước 7 nhắc đồng bộ manifest FE khi BE thêm API.

## Regression test
Repo FE `tests/auth/permission-api.test.mjs`, `tests/auth/guardian-api.test.mjs` (2 màn đã nối; commit trên repo FE, G94-272 (nhánh `dev`)). Cổng toàn cục: chưa có (xem plan).

## Ảnh hưởng
Web production: phần lớn menu ngoài Cấu hình trường / Điểm danh / Kế hoạch giáo dục lỗi; Tài khoản nhân sự và Phân công PHT hiển thị, lưu dữ liệu giả trong trình duyệt (người dùng tưởng đã lưu). Không có dữ liệu BE bị ghi sai.

## Lesson
Ghi chú "chưa tích hợp" trong tài liệu không chặn được gì; trạng thái tích hợp phải là dữ liệu máy kiểm được (route FE ⊆ route BE, hoặc khai báo module chưa nối) và cổng phải chạy đúng cấu hình build production.
