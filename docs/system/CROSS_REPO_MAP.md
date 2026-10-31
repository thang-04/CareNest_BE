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
| Principal / Vice Principal | Web (không dùng App — AUTH-21, user chốt 2026-10-27) | identity-access (HT: phân quyền, cấp tài khoản — AUTH-06, ADR-0017), reporting, nutrition (xác nhận/duyệt), education-plan (PHT lập/ban hành kế hoạch năm, cấu hình; duyệt theo chuỗi — EDU-02..07, EDU-11, EDU-18), learning-observation, health, notification (thông báo của trường — PAR-05) |
| Teacher (kể cả Tổ trưởng khối — AUTH-23) | Web + App (App: điểm danh + nhiệt độ đầu ngày, đón trẻ, sức khỏe, đánh giá, thông báo — ATT-01, PCK-02, HLT-07, HLT-14, AUTH-21) | attendance (gồm đón trẻ), learning-observation, health, education-plan |
| Parent | App (đăng nhập bằng SĐT; nhận tài khoản qua SMS, in phiếu dự phòng — AUTH-18) | attendance (gửi báo nghỉ — ATT-05), đọc: attendance (gồm ai đã đón con — PCK-04), nutrition (thực đơn tuần/ngày, ảnh suất — PAR-01), health, learning-observation, education-plan (tên hoạt động — EDU-37, chưa làm), notification (theo PAR-*) |
| Kitchen Staff | App (chỉ App, màn tối thiểu; gateway Web từ chối — AUTH-21; 1 tài khoản chung/cơ sở — AUTH-22) | nutrition (suất đã xác nhận theo lớp từng bữa, thực đơn, định lượng, số suất dị ứng không tên trẻ — AUTH-05; ảnh mẫu bữa, sổ lưu mẫu — NUT-16, NUT-21) |

## Điểm chạm cần kiểm tra khi đổi BE

| Thay đổi BE | Kiểm tra |
| --- | --- |
| Auth/token/permission | `CareNest_FE/docs/integration/AUTH_FLOW.md`, `CareNest_APP/docs/integration/AUTH_FLOW.md`; quyền mới/đổi mã ⇒ menu/route FE đọc `permissions` từ `/accounts/me` (`CareNest_FE/docs/architecture/ROUTE_MAP.md`, màn `/school/roles`) |
| Endpoint/DTO/error format | `CareNest_FE/docs/integration/BACKEND_INTEGRATION.md`, `CareNest_APP/docs/integration/BACKEND_INTEGRATION.md`, BE `docs/contracts/` |
| Parent visibility (PAR-*) | `CareNest_APP/docs/features/parent/` |
| MealCount/menu | `CareNest_APP/docs/features/kitchen/`, `CareNest_FE/docs/architecture/ROUTE_MAP.md` (màn hình BGH) |
| Event gửi notification | `CareNest_APP/docs/integration/PUSH_NOTIFICATION.md` |
| Cổng/domain deploy VPS (`docs/system/DEPLOYMENT.md`) | `CareNest_FE/docs/integration/DEPLOYMENT.md` (bảng cổng), `CareNest_FE/deploy/caddy/carenest-web.caddy` (`/api/*` ⇒ `127.0.0.1:18310`); APP: issuer `https://auth.carenest.edu.vn/realms/carenest`, API `https://web.carenest.edu.vn/api` |

Repo URL: CareNest_FE `https://github.com/thang-04/CareNest_FE.git`, CareNest_APP `https://github.com/thang-04/CareNest_APP.git`. Không tự sửa repo khác; nêu thay đổi cần thống nhất.

## React auth gateway — Phase 2

BE bổ sung `/auth/**`, PUT `/me/password`, mã HTTP 428, cookie phiên mã hóa/CSRF theo ADR-0013 và ADR-0015 (`/auth/refresh` trả `Set-Cookie` mới; FE dùng `withCredentials`, không cần sửa); chi tiết `docs/contracts/AUTH_CONTRACT.md`. Consumer FE: các form login/forgot/OTP/reset/change-password, AuthContext và HTTP client; tích hợp ở Phase 3, chưa sửa FE trong Phase 2. APP tiếp tục PKCE S256, issuer/audience, role/scope và `/accounts/me` hiện có; không chuyển APP sang Direct Grant. Regenerate types từ OpenAPI sau khi snapshot được kiểm chứng.

Trạng thái tích hợp (2026-10-22): FE `dev` chưa ghép — vẫn mock, gọi `/auth/me`, `/users`, `/me/profile` (BE không có), không CSRF/refresh; source gateway chỉ ở nhánh FE G94-209 và plan merge FE chốt không nhận gateway auth. Chi tiết: `docs/quality/reports/2026-10-22-auth-fe-integration-gap.md`.

## Nhận xét chung lớp — 29/10/2026

- GET daily-assessment thêm `classComment` (classId, date, text, recordedBy, recordedByName, updatedAt).
- PUT General thêm `classComment` optional: thiếu/null = giữ, rỗng = xóa; cho entries rỗng khi chỉ lưu lớp. Lưu cùng transaction với kết quả trẻ, không tạo đánh giá trẻ từ nhận xét lớp.
- GET overview/detail thêm `classComments` theo scope lớp/campus và kỳ; schema lớp đánh giá tên `AssessmentClassRow` để không trùng `ClassRow` của sức khỏe.
- FE: `assessmentApi.js`, mock assessment, `DailyAssessmentPage`, `ClassAssessmentOverviewPage`, component `ClassComments`; mã lỗi 7322/7323 vào classComment. APP không sửa trong task; client cũ giữ tương thích khi bỏ qua field mới.

## Consumer của API kế hoạch giáo dục (G94-226)

| API BE | Consumer FE |
| --- | --- |
| `GET/POST /education-plans/goals`, `GET/PUT/DELETE /education-plans/goals/{planId}`, `POST …/{planId}/submit\|issue\|return\|start-editing\|finish-editing\|editing-heartbeat` | `CareNest_FE/src/services/education-plan/api/educationPlanApi.js` (`getAll`, `saveGoal`, `deleteGoal`, `startEditingGoal`, `finishEditingGoal`), `src/hooks/education-plan/useEducationPlan.jsx` |
| `GET /education-plans/class-docs`, `PUT /education-plans/class-docs/{kind}/{docKey}`, `POST …/{kind}/{docKey}/actions` | `educationPlanApi.js` (`getClassPlans`, `saveClassDoc`, `actOnClassDoc`), `useEducationPlan.jsx`, `components/education-plan/planUi.jsx` (lớp, giáo viên) |
| `GET/PUT /education-plans/config` | `educationPlanApi.js` (`getEduConfig`, `saveEduConfig`), `yearPlanMapper.js` (`toUiEduConfig`); gợi ý nhóm kết quả mong đợi là hằng FE `src/models/education-plan/expectedResults.js`, không có API |
| `GET /accounts/me` (`assignments[].staffRole = AGE_GROUP_TEAM_LEADER`, `ageGroupId`) | FE lấy nhóm tuổi của Tổ trưởng (hiện đọc `user.ageGroupId`) |


Tự lưu mục tiêu năm (27/10/2026): các thao tác ghi goals yêu cầu ba header X-Edit-Session/X-Plan-Revision/X-Request-Id; detail thêm revision/editExpiresAt. Consumer Web: educationPlanApi.js, yearPlanMapper.js, educationPlanService.js, useEducationPlan.jsx, GoalFormPage.jsx; thao tác chi tiết/xóa phải giữ revision mà người dùng đã xem. Không triển khai BE riêng khi client cũ còn ghi. Mobile chưa triển khai màn này trong đợt tự lưu. Từ 27/10 FE bỏ tự ghi lên server (chỉ Lưu/Ctrl+S, gửi/ban hành), contract không đổi; bản giữ ẩn chỉ ở trình duyệt (EDU-22).

## Học liệu / trình chiếu lớp — feature002
Web consumers: ClassThemePlanPage/ActivityResources gọi learning-resources list/upload/bind/detach/content; TeachingPresentationPage mở từ theme hoặc WeekPlanPage theo ngày. API base /education-plans/class-docs/theme/{docKey}/learning-resources, không đổi API cũ. Mobile chưa dùng API này. Tệp S3 private sau backend scope check; binary mock ở IndexedDB chỉ là demo. Xem [contract](../../specs/002-class-plan-teaching-media/contracts/learning-resources.md) và [S3 config](EDUCATION_MEDIA_S3.md).

- Live S3 đã được kiểm từ BE StorageService theo cấu hình user chỉ định ngoài repo:4object bytes-roundtrip, anonymous-denied và dọn sạch. FE không cầm cloud credential. Native PDF viewer tùy trình duyệt; IAB chưa render được nên có hướng dẫn tải/fallback, không tuyên bố PDF inline smoke đạt.

## Lấy mục tiêu kế hoạch năm từ nguồn khác (EDU-24, chốt 28/10/2026)

Web: ImportGoalsModal, GoalFormPage, service đọc kế hoạch nguồn; giữ quyền EDU-06 và luồng sửa ba bước. Chỉ lấy mục tiêu/nội dung, không chép chủ đề/đợt nghỉ/dấu tích. Web đã triển khai list/detail nguồn bằng contract hiện có; không thêm endpoint/DTO. 13/13 E2E thật đã đạt, Mobile chưa thuộc phạm vi. Xem [đặc tả feature003](../../specs/003-import-year-plan-goals/spec.md).
