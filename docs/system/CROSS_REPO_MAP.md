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
| Kitchen Staff | App (chỉ App, màn tối thiểu; gateway Web từ chối — AUTH-21; 1 tài khoản chung/cơ sở — AUTH-22) | nutrition (suất đã xác nhận theo lớp từng bữa, thực đơn, định lượng, số suất dị ứng không tên trẻ — AUTH-05; ảnh mẫu bữa theo khung giờ, ghi lưu/hủy mẫu — NUT-16, NUT-21) |

## Điểm chạm cần kiểm tra khi đổi BE

| Thay đổi BE | Kiểm tra |
| --- | --- |
| Auth/token/permission | `CareNest_FE/docs/integration/AUTH_FLOW.md`, `CareNest_APP/docs/integration/AUTH_FLOW.md`; quyền mới/đổi mã ⇒ menu/route FE đọc `permissions` từ `/accounts/me` (`CareNest_FE/docs/architecture/ROUTE_MAP.md`, màn `/school/roles`) |
| Endpoint/DTO/error format | `CareNest_FE/docs/integration/BACKEND_INTEGRATION.md`, `CareNest_APP/docs/integration/BACKEND_INTEGRATION.md`, BE `docs/contracts/` |
| Parent visibility (PAR-*) | `CareNest_APP/docs/features/parent/` |
| MealCount/menu | `CareNest_APP/docs/features/kitchen/`, `CareNest_FE/docs/architecture/ROUTE_MAP.md` (màn hình BGH) |
| Event gửi notification | `CareNest_APP/docs/integration/PUSH_NOTIFICATION.md` |
| Cổng/domain deploy VPS (`docs/system/DEPLOYMENT.md`) | `CareNest_FE/docs/integration/DEPLOYMENT.md` (bảng cổng), `CareNest_FE/deploy/caddy/carenest-web.caddy` (`/api/*` ⇒ `127.0.0.1:18310`); APP: issuer `https://auth.carenest.edu.vn/realms/carenest`, API `https://web.carenest.edu.vn/api` |

## Bán trú và kho — đối chiếu sơ đồ quy trình (user chốt 2026-11-01)

- **API danh mục đã có (02/11, V54–V55):** `/nutrition/allergens|foods|dishes|age-group-configs|suppliers`. FE `src/services/menu-planning/api/menuPlanningApi.js` (đang đề xuất `/menu/foods`, `/menu/dishes`, `/menu/meal-prices`, `/menu/nutrition-setups`) và `src/services/kitchen/api/kitchenApi.js` (`/kitchen/suppliers`) cần đổi sang `/nutrition/*`; giá suất gộp vào `age-group-configs`; `storage` `FRESH`/`DRY`; thực phẩm có `origin` `ANIMAL`/`PLANT` bắt buộc (NUT-24, lỗi 6026); chi tiết món trả 6 chỉ số (`animalProtein`, `plantProtein`, `animalLipid`, `plantLipid`, `glucid`, `kcal` + tổng `protein`, `lipid`); cấu hình nhóm tuổi thêm `animalProteinPct*`, `animalLipidPct*`; `POST /nutrition/age-group-configs/batch` lưu cấu hình nhiều nhóm tuổi nguyên khối (sửa bản chưa hiệu lực cùng ngày, chưa có thì tạo); `GET /nutrition/dishes` trả đủ định lượng, dinh dưỡng, dị ứng (không cần gọi từng món); `POST /nutrition/foods/import` nhận `items[{id?, data}]` thêm + sửa nguyên khối, trả `{created, updated}`; `code` thực phẩm/món để trống ⇒ BE tự sinh `TPnnn`/`MAnnn` (FE không có ô mã); quyền sửa theo `permissions` chứa `NUTRITION_MANAGE`. Plan FE: `CareNest_FE (nhánh feature/G94-191-FE-FEAT-48-nutrition-management):docs/plans/active/2026-11-02-day-boarding-l1-catalog-fe.md`. APP chưa dùng.

Rule đổi: NUT-07, NUT-08, NUT-11, NUT-12, NUT-13, NUT-16, NUT-17, NUT-21, NUT-23, AI-07; đóng P-24, P-25 (ADR-0009 bản 01/11). Chưa có API — client cần chỉnh khi BE làm:
- FE (Web, PHT dinh dưỡng): kế hoạch tuần (kiểm từng ngày, cảnh báo kho) và công bố thực đơn cả tuần (chốt 02/11, thay công bố ngày); trừ kho khô theo ngày khi suất được xác nhận, phần thiếu hiện cho PHT (P-38); màn Dự báo tuần ⇒ bản dự trù (sĩ số điền sẵn, sửa được, lưu, in danh sách đặt hàng); kiểm nhận lần 2 so nhu cầu theo thực đơn tuần × suất đã xác nhận; tồn theo lô + lịch sử nhập/trừ; duyệt lưu/hủy mẫu, in nhãn và biểu mẫu; PHT cấu hình bữa, chuẩn dinh dưỡng, giá suất theo nhóm tuổi; HT cấu hình khung giờ ảnh; món thay thế theo chất dị ứng. Đối chiếu `CareNest_FE/docs/features/nutrition-inventory.md`, `CareNest_FE/docs/architecture/ROUTE_MAP.md`.
- APP (bếp, phụ huynh): bếp đăng/sửa ảnh trong khung giờ, ghi hủy mẫu, xem trạng thái duyệt; phụ huynh xem ảnh bữa của cơ sở con học; thực đơn tuần lấy từ kế hoạch tuần (có thể đổi), thực đơn ngày lấy từ thực đơn chuẩn đã công bố (P-37). Đối chiếu `CareNest_APP/docs/features/kitchen/README.md`, `CareNest_APP/docs/features/parent/README.md`.

Repo URL: CareNest_FE `https://github.com/thang-04/CareNest_FE.git`, CareNest_APP `https://github.com/thang-04/CareNest_APP.git`. Không tự sửa repo khác; nêu thay đổi cần thống nhất.

## React auth gateway — Phase 2

BE bổ sung `/auth/**`, PUT `/me/password`, mã HTTP 428, cookie phiên mã hóa/CSRF theo ADR-0013 và ADR-0015 (`/auth/refresh` trả `Set-Cookie` mới; FE dùng `withCredentials`, không cần sửa); chi tiết `docs/contracts/AUTH_CONTRACT.md`. Consumer FE: các form login/forgot/OTP/reset/change-password, AuthContext và HTTP client; tích hợp ở Phase 3, chưa sửa FE trong Phase 2. APP tiếp tục PKCE S256, issuer/audience, role/scope và `/accounts/me` hiện có; không chuyển APP sang Direct Grant. Regenerate types từ OpenAPI sau khi snapshot được kiểm chứng.

Trạng thái tích hợp (2026-10-22): FE `dev` chưa ghép — vẫn mock, gọi `/auth/me`, `/users`, `/me/profile` (BE không có), không CSRF/refresh; source gateway chỉ ở nhánh FE G94-209 và plan merge FE chốt không nhận gateway auth. Chi tiết: `docs/quality/reports/2026-10-22-auth-fe-integration-gap.md`.

## Nhận xét chung lớp — 29/10/2026

- GET daily-assessment thêm `classComment` (classId, date, text, recordedBy, recordedByName, updatedAt).
- PUT General thêm `classComment` optional: thiếu/null = giữ, rỗng = xóa; cho entries rỗng khi chỉ lưu lớp. Lưu cùng transaction với kết quả trẻ, không tạo đánh giá trẻ từ nhận xét lớp.
- GET overview/detail thêm `classComments` theo scope lớp/campus và kỳ; schema lớp đánh giá tên `AssessmentClassRow` để không trùng `ClassRow` của sức khỏe.
- FE: `assessmentApi.js`, mock assessment, `DailyAssessmentPage`, `ClassAssessmentOverviewPage`, component `ClassComments`; mã lỗi 7322/7323 vào classComment. APP không sửa trong task; client cũ giữ tương thích khi bỏ qua field mới.

Trạng thái tích hợp (2026-11-01, đối chiếu `origin/dev` FE và BE): web.carenest.edu.vn build `VITE_AUTH_MODE=gateway`, `VITE_USE_MOCK=false`; gateway chạy (`/api/auth/csrf` 200). `VITE_USE_MOCK` là cờ toàn cục ⇒ màn nào FE chưa ghép hoặc BE chưa có API đều lỗi. 224/317 lời gọi HTTP của FE không có route BE: (1) BE có, FE trả cứng 501 khi tắt mock — `permissionService` (màn `/school/roles`, lịch sử, ngoại lệ quyền), `guardianService` (2 màn đã nối, G94-272, nhánh `dev` FE; cổng `check-api-contract` + module demo có nhãn cùng commit); mở lại điểm danh không lỗi vì gateway đi `beClient`; (1b) gateway bật `SCHOOL_CONFIG_API` ⇒ `schoolConfigService` hàm trong `NOT_IN_BE` (Tài khoản nhân sự, Phân công PHT) **lặng lẽ dùng mock localStorage**; (2) lệch đường dẫn — FE `/users*` vs BE `/accounts*`, FE `GET /classes` vs BE `/school-structure`, `vice-principal-assignments`, `/notifications`; (3) BE chưa có module — assessment, health-checks, facility, facility-transfers, inspections, kitchen, menu, pickups, signatures, appearance. Deploy BE chỉ chạy từ `main` (từ 2026-10-31) mà `main` chưa có controller nào ⇒ bản BE trên VPS là lần deploy cuối từ `dev`.

## Consumer của API kế hoạch giáo dục (G94-226)

| API BE | Consumer FE |
| --- | --- |
| `GET/POST /education-plans/goals`, `GET/PUT/DELETE /education-plans/goals/{planId}`, `POST …/{planId}/submit\|issue\|return\|start-editing\|finish-editing\|editing-heartbeat` | `CareNest_FE/src/services/education-plan/api/educationPlanApi.js` (`getAll`, `saveGoal`, `deleteGoal`, `startEditingGoal`, `finishEditingGoal`), `src/hooks/education-plan/useEducationPlan.jsx` |
| `GET /education-plans/class-docs`, `PUT /education-plans/class-docs/{kind}/{docKey}`, `POST …/{kind}/{docKey}/actions` | `educationPlanApi.js` (`getClassPlans`, `saveClassDoc`, `actOnClassDoc`), `useEducationPlan.jsx`, `components/education-plan/planUi.jsx` (lớp, giáo viên) |
| `POST /education-plans/goals/file-preview` (EDU-46, multipart; 01/11/2026 thêm .doc nhị phân, shape/quyền không đổi) | `educationPlanApi.js` (`previewGoalFile`), `components/education-plan/ImportGoalFileModal.jsx` (hướng dẫn .doc), `models/education-plan/importGoalFile.mjs` (hai chế độ thay nội dung EDU-46 Web đã implement, chờ review/converge), `mock/eduMockErrors.js` (lời lỗi 7033); Mobile chưa dùng |
| `POST /education-plans/class-docs/branch/file-preview` (EDU-47, file + classId/yearPlanId) | `educationPlanApi.js`/`educationPlanService.js` (`previewBranchFile`), `ImportBranchFileModal.jsx`, `BranchPlanPage.jsx`, `models/education-plan/importBranchFile.mjs`; server trả matches tuần hệ thống/warnings, Web xác nhận thay rồi lưu bằng API cũ. Mock CSV: `classPlanMockRepository.js`. Mobile chưa có màn nhập này, không đổi repo App |
| `GET/PUT /education-plans/config` | `educationPlanApi.js` (`getEduConfig`, `saveEduConfig`), `yearPlanMapper.js` (`toUiEduConfig`); gợi ý nhóm kết quả mong đợi là hằng FE `src/models/education-plan/expectedResults.js`, không có API |
| `GET /accounts/me` (`assignments[].staffRole = AGE_GROUP_TEAM_LEADER`, `ageGroupId`) | FE lấy nhóm tuổi của Tổ trưởng (hiện đọc `user.ageGroupId`) |


Tự lưu mục tiêu năm (27/10/2026): các thao tác ghi goals yêu cầu ba header X-Edit-Session/X-Plan-Revision/Idempotency-Key (đổi từ X-Request-Id ngày 02/11/2026 vì proxy hosting VPS ghi đè X-Request-Id thành ULID, xem [BUG-261102-proxy-overrides-x-request-id](../knowledge/incidents/BUG-261102-proxy-overrides-x-request-id.md)); detail thêm revision/editExpiresAt. Consumer Web: educationPlanApi.js, yearPlanMapper.js, educationPlanService.js, useEducationPlan.jsx, GoalFormPage.jsx; thao tác chi tiết/xóa phải giữ revision mà người dùng đã xem. Không triển khai BE riêng khi client cũ còn ghi. Mobile chưa triển khai màn này trong đợt tự lưu. Từ 27/10 FE bỏ tự ghi lên server (chỉ Lưu/Ctrl+S, gửi/ban hành), contract không đổi; bản giữ ẩn chỉ ở trình duyệt (EDU-22).

## Học liệu / trình chiếu lớp — feature002
Web consumers: ClassThemePlanPage/ActivityResources gọi learning-resources list/upload/bind/detach/content; TeachingPresentationPage mở từ theme hoặc WeekPlanPage theo ngày. API base /education-plans/class-docs/theme/{docKey}/learning-resources, không đổi API cũ; upload gửi khóa chống gửi trùng ở header Idempotency-Key (Web: learningResourceService.js). Mobile chưa dùng API này. Tệp S3 private sau backend scope check; binary mock ở IndexedDB chỉ là demo. Xem [contract](../../specs/002-class-plan-teaching-media/contracts/learning-resources.md) và [S3 config](EDUCATION_MEDIA_S3.md).

- Live S3 đã được kiểm từ BE StorageService theo cấu hình user chỉ định ngoài repo:4object bytes-roundtrip, anonymous-denied và dọn sạch. FE không cầm cloud credential. Native PDF viewer tùy trình duyệt; IAB chưa render được nên có hướng dẫn tải/fallback, không tuyên bố PDF inline smoke đạt.

## Lấy mục tiêu kế hoạch năm từ nguồn khác (EDU-24, chốt 28/10/2026)

Web: ImportGoalsModal, GoalFormPage, service đọc kế hoạch nguồn; giữ quyền EDU-06 và luồng sửa ba bước. Chỉ lấy mục tiêu/nội dung, không chép chủ đề/đợt nghỉ/dấu tích. Web đã triển khai list/detail nguồn bằng contract hiện có; không thêm endpoint/DTO. 13/13 E2E thật đã đạt, Mobile chưa thuộc phạm vi. Xem [đặc tả feature003](../../specs/003-import-year-plan-goals/spec.md).

## Quyết định review E2E — tác động FE/APP (user chốt 2026-11-01)

Nguồn: `CareNest_CODE/plans/reports/final-261101-1407-carenest-e2e-business-flow-review-report.md` mục "Đã chốt". Đã vào code (2026-11-01): phần FE của D1 (`/kitchen/meal-count` đọc `GET /meal-counts` qua module FE `kitchen-meal-count`, sửa câu toast); phần BE của D2, D3/AUTH-24, D6, D8, D10 (plan `docs/plans/active/2026-11-01-e2e-review-p1-fixes.md`). Phần FE/APP các dòng đó chưa.

| Quyết định | BE | FE | APP |
| --- | --- | --- | --- |
| Bếp nhận số suất (D1) | — (giữ AUTH-21, NUT-03) | HT/PHT xem "số bếp sẽ dùng" từ `GET /meal-counts`; sửa câu "Bếp đã nhận được số liệu" | Màn bếp + auth thật, làm sau |
| `meals` trong điểm danh (D8, ATT-02) | **Đã làm:** giữ field deprecated, bỏ qua giá trị; `record.meals` trả về suy từ `status` | Bỏ cột tick "Ăn" | Bỏ chọn bữa ăn |
| Vắng có phép (D6, ATT-09) | **Đã làm:** `EXCUSED` mới ⇒ 400 `4018`; gửi lại `EXCUSED` đang có được | Ẩn lựa chọn; hiện câu lỗi `4018` | Ẩn lựa chọn |
| Sửa suất (D2, D10, NUT-02) | **Đã làm:** `normal` null = giữ, khác ⇒ 409 `6008`; `substitute` không bị điểm danh xóa; xác nhận theo cơ sở–ngày (một lần cho mọi bữa, gọi lại bữa đã xác nhận ⇒ 200); còn trẻ chưa điểm danh ⇒ 409 `6009` kèm tên lớp | Gửi `normal: null`, bỏ ô sửa suất thường; nút xác nhận từng bữa nay xác nhận cả ngày (đổi câu chữ); hiện câu lỗi `6009` | — |
| Phân công GV (D3, AUTH-24) | **Đã làm:** `/staff-assignments` GV áp cùng luật `PUT /classes/{id}/teachers` (409 `2306`, 400 `2304`, 409 `2305`, 409 `2003`); dạy thay = cờ `substitute: true` trong `POST /staff-assignments` (chỉ GV ⇒ 400 `2320`, bắt buộc `validTo` ⇒ 400 `2321`), response có `substitute`; dạy chính `validTo` trống/sau hết năm bị cắt về hết năm; ghi `assigned_by` | Màn Nhân sự (khi nối API): ô "Dạy thay" + ngày kết thúc bắt buộc; hiện câu lỗi BE | — |
| Đổi vai trò, sửa hồ sơ nhân sự (D4, AUTH-25) | API mới (đổi contract) | Màn Nhân sự nối API thật | — |
| Hold khung chờ (D9) | — | Bỏ giữ tới 700ms khi đã có dữ liệu/lỗi, giữ trễ hiện 300ms; không progress bar (`src/hooks/loadingTiming.js`, `DESIGN.md` §9) | — |
| Màn demo trái rule REMOVED (D11) | — | Ẩn riêng các màn REMOVED (bàn giao suất, trạng thái nấu…); màn demo khác giữ | — |


## Nhập đầy đủ kế hoạch năm — feature009 (02/11/2026)
BE preview additive cùng `POST /education-plans/goals/file-preview`: giữ domains/themeAbbrs/warnings, thêm metadata(title/year/khoảng tuổi), themes(name/abbr/festivals/start/end/note), breaks(name/start/end/note). Không đổi API lưu hoặc schema; xem [contract](../../specs/009-full-year-plan-file-import/contracts/full-year-plan-preview.md). Parser/preview BE đã có evidence cũ; quyết định hai chế độ thay nội dung Web mới được user duyệt02/11 và đã implement/verify FE phase2 và chờ review trước convergence; chưa gọi toàn feature hoàn tất.
Consumer Web cần cập nhật: educationPlanApi.js → educationPlanService.js → ImportGoalFileModal.jsx → GoalFormPage.jsx/importGoalFile.mjs. Preview/merge Web giữ năm đích, dời ngày, chặn lệch nhóm tuổi/nhận nhóm khi trống theo quyền, ghép/remap chủ đề trước ticks theo hai chế độ: full xóa phần thiếu, present giữ phần thiếu, phần có thay nguyên phần. Giữ API/DB; chỉ sửa tài liệu BE trong delta này. File cũ metadata null/themes[]/breaks[] phải preview rõ phần sẽ xóa/giữ; không mặc định additive. Thiếu yearRange cấu hình thật khóa apply. Mobile chưa có consumer và không sửa APP. Màn phần1 user yêu cầu nhập dạng bảng như phần2/3; toàn bộ nút desktop cùng hàng/định dạng.
