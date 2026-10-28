# Cross-Repo Map

## Ownership

| Hạng mục | CareNest_BE | CareNest_FE | CareNest_APP |
| --- | --- | --- | --- |
| Business rule, domain model | **Owner** | Không | Không |
| Authorization / access scope | **Owner** (kiểm tra thật) | Ẩn/hiện UI theo dữ liệu BE | Ẩn/hiện UI theo dữ liệu BE |
| API contract (`docs/contracts/`) | **Owner** | Consumer | Consumer |
| Màn hình, route/navigation, client state | — | Owner (Web) | Owner (Mobile) |
| Push notification | Gửi qua provider | — | Nhận, đăng ký token |
| Engineering memory nghiệp vụ/contract | **Owner** (`docs/knowledge/`) | Lỗi UI riêng | Lỗi UI riêng |

Lỗi liên quan contract/nghiệp vụ phát hiện ở FE/APP ⇒ ghi ở BE `docs/knowledge/CROSS_MODULE_ISSUES.md`.

## Actor → client → module BE

| Actor | Client | Module BE chính |
| --- | --- | --- |
| System Admin | Web | identity-access (kỹ thuật: tài khoản PRINCIPAL, khóa/mở), school-structure, child (cấu hình) |
| Principal / Vice Principal | Web (App: sau, nếu có yêu cầu) | identity-access (HT: phân quyền, cấp tài khoản — AUTH-06, ADR-0017), reporting, nutrition (xác nhận/duyệt), learning-observation, education-plan, health, facility-issue |
| Teacher (kể cả Tổ trưởng khối) | Web + App | attendance, learning-observation, education-plan, health, facility-issue |
| Parent | App (đăng nhập bằng SĐT — AUTH-18) | attendance (gửi báo nghỉ — ATT-05), đọc: attendance, nutrition, health, learning (theo PAR-*) |
| Kitchen Staff | App (+Web) | nutrition (suất đã chốt, thực đơn, định lượng) |

## Điểm chạm cần kiểm tra khi đổi BE

| Thay đổi BE | Kiểm tra |
| --- | --- |
| Auth/token/permission | `CareNest_FE/docs/integration/AUTH_FLOW.md`, `CareNest_APP/docs/integration/AUTH_FLOW.md`; quyền mới/đổi mã ⇒ menu/route FE đọc `permissions` từ `/accounts/me` (`CareNest_FE/docs/architecture/ROUTE_MAP.md`, màn `/school/roles`) |
| Endpoint/DTO/error format | `CareNest_FE/docs/integration/BACKEND_INTEGRATION.md`, `CareNest_APP/docs/integration/BACKEND_INTEGRATION.md`, BE `docs/contracts/` |
| Parent visibility (PAR-*) | `CareNest_APP/docs/features/parent/` |
| MealCount/menu | `CareNest_APP/docs/features/kitchen/`, `CareNest_FE/docs/architecture/ROUTE_MAP.md` (màn hình BGH) |
| Event gửi notification | `CareNest_APP/docs/integration/PUSH_NOTIFICATION.md` |

Repo URL: CareNest_FE `https://github.com/thang-04/CareNest_FE.git`, CareNest_APP `https://github.com/thang-04/CareNest_APP.git`. Không tự sửa repo khác; nêu thay đổi cần thống nhất.

## React auth gateway — Phase 2

BE bổ sung `/auth/**`, PUT `/me/password`, mã HTTP 428, cookie phiên mã hóa/CSRF theo ADR-0013 và ADR-0015 (`/auth/refresh` trả `Set-Cookie` mới; FE dùng `withCredentials`, không cần sửa); chi tiết `docs/contracts/AUTH_CONTRACT.md`. Consumer FE: các form login/forgot/OTP/reset/change-password, AuthContext và HTTP client; tích hợp ở Phase 3, chưa sửa FE trong Phase 2. APP tiếp tục PKCE S256, issuer/audience, role/scope và `/accounts/me` hiện có; không chuyển APP sang Direct Grant. Regenerate types từ OpenAPI sau khi snapshot được kiểm chứng.

Trạng thái tích hợp (2026-10-22): FE `dev` chưa ghép — vẫn mock, gọi `/auth/me`, `/users`, `/me/profile` (BE không có), không CSRF/refresh; source gateway chỉ ở nhánh FE G94-209 và plan merge FE chốt không nhận gateway auth. Chi tiết: `docs/quality/reports/2026-10-22-auth-fe-integration-gap.md`.

## Consumer của API kế hoạch giáo dục (G94-226)

| API BE | Consumer FE |
| --- | --- |
| `GET/POST /education-plans/goals`, `GET/PUT/DELETE /education-plans/goals/{planId}`, `POST …/{planId}/submit\|issue\|return\|start-editing\|finish-editing` | `CareNest_FE/src/services/education-plan/api/educationPlanApi.js` (`getAll`, `saveGoal`, `deleteGoal`, `startEditingGoal`, `finishEditingGoal`), `src/hooks/education-plan/useEducationPlan.jsx` |
| `GET /education-plans/class-docs`, `PUT /education-plans/class-docs/{kind}/{docKey}`, `POST …/{kind}/{docKey}/actions` | `educationPlanApi.js` (`getClassPlans`, `saveClassDoc`, `actOnClassDoc`), `useEducationPlan.jsx`, `components/education-plan/planUi.jsx` (lớp, giáo viên) |
| `GET/PUT /education-plans/config`, `GET /education-plans/expected-results` | `educationPlanApi.js` (`getEduConfig`, `saveEduConfig`), `yearPlanMapper.js` (`toUiEduConfig`), `src/models/education-plan/expectedResults.js` (hiện là hằng FE) |
| `GET /accounts/me` (`assignments[].staffRole = AGE_GROUP_TEAM_LEADER`, `ageGroupId`) | FE lấy nhóm tuổi của Tổ trưởng (hiện đọc `user.ageGroupId`) |

