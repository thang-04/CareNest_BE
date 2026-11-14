---
title: Phân tích xu hướng theo khoảng thời gian và thông báo phụ huynh khi duyệt
status: in-progress
owner: G94 team
jira: G94-180 / BE-FEAT-43 (BE, Web) · G94-334 (APP)
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [health, notification, ai-assistance]
rules: [HLT-02, HLT-05, HLT-21, PAR-03, PAR-07, AI-04]
risk: high
created: 2026-11-14
updated: 2026-11-14
---
# Phân tích xu hướng theo khoảng thời gian và thông báo phụ huynh khi duyệt

Plan liên repo: BE (phase 1–2), Web `CareNest_FE` (phase 3), App `carenest_app` (phase 4).

## Context
- AI nhận mọi lần đo đã công bố của trẻ, không giới hạn thời gian (`HealthTrendServiceImpl.java:94-96`).
- Màn Xu hướng có bộ lọc biểu đồ 6 tháng / 12 tháng / Toàn bộ, mặc định Toàn bộ, tính lùi từ hôm nay (`FE: src/pages/children/HealthTrendPage.jsx:27-37,90-94`). AI chưa theo bộ lọc này.
- GV duyệt xong thì phụ huynh chỉ thấy bản duyệt khi tự mở sổ sức khỏe; không có thông báo (`HealthTrendServiceImpl.java:168-205`).
- Hạ tầng thông báo có sẵn: `HealthNotifier` (`HealthNotifier.java:35`), push che nội dung loại sức khỏe (`PushDispatchListener.java:32-36,65-67`), CHECK `ck_notifications_type` (`V128__edu_resource_personal_library.sql:46-54`).

## Quyết định đã chốt
1. AI phân tích theo đúng khoảng đang chọn trên biểu đồ (6 tháng / 12 tháng / Toàn bộ) — user, 14/11/2026.
2. Tổng quan nói rõ khoảng thời gian, ví dụ "Trong 6 tháng qua, …" hoặc nói về toàn bộ quá trình — user, 14/11/2026.
3. Gửi thông báo cho phụ huynh mỗi lần GV duyệt một bản nháp (có sửa hay không); bấm duyệt lại bản đã duyệt không gửi thêm — user, 14/11/2026.
4. Mỗi tài khoản phụ huynh liên kết với trẻ nhận 1 thông báo (gia đình dùng chung tài khoản thì 1 thông báo) — user, 14/11/2026.
5. Loại `HEALTH_TREND_APPROVED`; nội dung trong app = tổng quan đã duyệt (phương án A); push trên màn khóa vẫn che như các loại sức khỏe khác — user, 14/11/2026.
6. Đồng ý migration mới (`V131`; `V129__health_interpretation_content` đổi thành `V130` vì trùng `dev` — user chọn A 14/11) — user, 14/11/2026.
7. App sửa `app_notification.dart` trên nhánh G94-334 — user, 14/11/2026.

## Làm rõ nghiệp vụ
- **Actor · outcome:** nhân sự chọn khoảng rồi bấm Phân tích ⇒ AI chỉ đọc số đo trong khoảng; GV lớp duyệt ⇒ phụ huynh nhận thông báo có tổng quan, mở App thấy bản đầy đủ (trừ gợi ý theo dõi).
- **Rule:** HLT-21 (PROPOSED, user chốt 14/11) cần thêm khoảng thời gian và thông báo; PAR-07 (mọi thông báo đều push).
- **Ảnh hưởng:** BE health + notification; Web `/children/health-trend`; App danh sách thông báo, màn Sức khỏe. Dữ liệu cũ: bản phân tích đã lưu nhận `period = ALL`.

### Câu hỏi mở
- [x] Q1 Mốc tính khoảng: đề xuất tính lùi từ hôm nay (giờ Việt Nam), giống biểu đồ hiện tại. Hệ quả: khi một lần đo cũ trôi ra khỏi khoảng, bản phân tích bị đánh dấu `stale` và banner đổi thành "Số đo trong khoảng phân tích đã thay đổi — phân tích lại".
- [x] Q2 Khoảng có dưới 2 lần đo: đề xuất trả `NOT_ENOUGH_DATA`, màn hiện "Chưa đủ số đo trong 6 tháng — chọn khoảng dài hơn", không gọi AI.
- [x] Q3 Mở trang chỉ đọc bản mới nhất, không tự gọi AI; AI chạy khi bấm nút. Bản mới nhất hiện nhãn khoảng ("Phân tích 12 tháng"); khác khoảng đang chọn thì nút ghi "Phân tích lại (6 tháng)".
- [x] Q4 Phụ huynh thấy nhãn khoảng cạnh bản đã duyệt (`period` trong `interpretation` của API sổ sức khỏe): đề xuất có.
- [x] Q5 Nhánh App: G94-334 = "[APP-FEAT-52] Thông báo – App" (Giáp, In Progress, hạn 18/11). Code thông báo App đã làm trên nhánh `feature/G94-194-FE-FEAT-49-app-ui-redraw` (commit `47a0651` mang mã G94-334), khác nhiều so với `dev` (5 file, +279/−227). Đề xuất: commit trên nhánh đó với mã `G94-334`, không tạo nhánh mới từ `dev`.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Q1 | Tính lùi từ hôm nay, như biểu đồ | User | 14/11/2026 | HLT-21 (phase 1) |
| Q2 | Dưới 2 lần đo trong khoảng ⇒ `NOT_ENOUGH_DATA`, không gọi AI | User | 14/11/2026 | HLT-21 (phase 1) |
| Q3 | Chỉ phân tích khi bấm nút; mở trang đọc bản mới nhất | User | 14/11/2026 | — (đã đúng hiện trạng) |
| Q4 | Phụ huynh thấy nhãn khoảng | User | 14/11/2026 | HLT-21 (phase 1) |
| Q5 | Phương án A: commit trên nhánh `feature/G94-194-FE-FEAT-49-app-ui-redraw`, mã G94-334 | User | 14/11/2026 | — |

## Acceptance criteria
- **AC-1** (HLT-21): Given trẻ có lần đo cách đây 10 và 2 tháng, When phân tích `period=MONTHS_6`, Then AI chỉ nhận lần đo 2 tháng và các lần trong 6 tháng; dưới 2 lần ⇒ `NOT_ENOUGH_DATA`, không gọi AI.
- **AC-2** (HLT-21): Given phân tích bất kỳ, Then response và bản lưu có `period`; không gửi `period` ⇒ `ALL` (tương thích client cũ).
- **AC-3** (HLT-21, AI-04): prompt ghi khoảng phân tích và bắt tổng quan mở đầu bằng cụm khoảng ("Trong 6 tháng qua", "Trong 12 tháng qua", "Trong toàn bộ quá trình theo dõi"); không có ngày đo.
- **AC-4** (HLT-21): Given bản 6 tháng, When có lần đo mới trong khoảng hoặc lần đo cũ trôi khỏi khoảng, Then `stale = true`.
- **AC-5** (HLT-21, PAR-03): Given bản nháp, When GV lớp duyệt, Then mỗi tài khoản phụ huynh liên kết có 1 thông báo `HEALTH_TREND_APPROVED`, title "Nhận định xu hướng sức khỏe mới", body = tổng quan đã duyệt; duyệt lại bản đã duyệt không tạo thêm.
- **AC-6** (PAR-07): push của `HEALTH_TREND_APPROVED` dùng câu che chung, không có tổng quan.
- **AC-7** (FE): bấm Phân tích gửi khoảng đang chọn; màn hiện nhãn khoảng; mock giống BE.
- **AC-8** (APP): App nhận loại `HEALTH_TREND_APPROVED`, bấm mở màn Sức khỏe của trẻ.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Truyền khoảng | Query `?period=MONTHS_6|MONTHS_12|ALL` trên POST hiện có | Endpoint mới | Không phá client cũ, thêm 1 tham số |
| Lưu khoảng | Cột `period` trong `health_interpretations` | Không lưu | Cần cho nhãn, `stale`, phụ huynh |
| Migration | Một file `V131`: cột `period` + CHECK thông báo | Hai file | Cùng một thay đổi nghiệp vụ |

## Phases
### Phase 1 — BE: khoảng thời gian
- Files: C `utils/enums/HealthTrendPeriod.java`, `db/migration/V131__health_trend_period_and_notice.sql` (đổi tên `V129__health_interpretation_content` ⇒ `V130`) · M `HealthTrendController.java`, `HealthTrendService.java`, `HealthTrendServiceImpl.java` (lọc điểm, prompt, `stale`), `HealthInterpretation.java`, `HealthTrendAnalysisResponse.java`, `PublishedHealthResponse.java` (`ApprovedInterpretation.period`), `HealthMeasurementServiceImpl.java`, docs (HLT-21, `health.md`, `ai-assistance.md`, `DATA_DICTIONARY.md`, `openapi.yaml`).
- Tests: AC-1..4 → `HealthTrendServiceImplTest` (lọc, thiếu điểm, prompt), `HealthGrowthTest` hoặc service test (`stale`), `HealthRecordIntegrationTest` (cột `period`, giá trị cũ = `ALL`).
- Exit: `mvnw test -Dtest=HealthTrend*,HealthRecordIntegrationTest,OpenApiSnapshotTest` pass. **Dừng chờ review.**

### Phase 2 — BE: thông báo khi duyệt
- Files: M `NotificationType.java`, `HealthNotifier.java` (`trendApproved`), `HealthTrendServiceImpl.approve`, `PushDispatchListener.java` (thêm vào `HEALTH_TYPES`), `V131` (CHECK — đã làm ở phase 1), docs (`notification.md`, HLT-21, PAR-07 nếu cần).
- Tests: AC-5 → `HealthTrendServiceImplTest` (gửi 1 lần, duyệt lại không gửi), `HealthRecordIntegrationTest` (dòng `notifications`); AC-6 → test của `PushDispatchListener`.
- Exit: test trên + `NotificationServiceImplTest` pass. **Dừng chờ review.**

### Phase 3 — Web
- Files: M `HealthTrendPage.jsx` (truyền `period`), `AiTrendPanel.jsx` (nhãn, nút, thông báo thiếu điểm), `childrenApi.js`, `childrenService.js`, `childrenMockRepository.js`, `childrenConstants.js` (nhãn khoảng), `tests/health/health-trend.test.mjs`, `docs/integration/FE_API_CALLS.md`.
- Exit: `npm run test:health`, ESLint, Prettier pass. **Dừng chờ review.**

### Phase 4 — App
- Files: M `lib/features/notifications/domain/app_notification.dart` (+ điều hướng màn Sức khỏe nếu cần), test thông báo của App.
- Exit: `flutter test` các test thông báo, `flutter analyze` sạch. **Dừng chờ review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1..4 | HLT-21, AI-04 | Service + integration | 1 | |
| AC-5, AC-6 | PAR-03, PAR-07 | Service + integration | 2 | |
| AC-7 | HLT-21 | node:test | 3 | |
| AC-8 | PAR-07 | flutter test | 4 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Nhánh khác cũng sửa CHECK `ck_notifications_type` hoặc dùng `V130`/`V131` | Lỗi Flyway khi merge `dev` | Kiểm `dev` trước khi push, đổi số version nếu trùng | Migration mới khôi phục CHECK |
| Tổng quan dài 500 ký tự | Vừa khít cột `body` (500) | Lấy đúng tổng quan đã kiểm, không thêm hậu tố | — |
| `stale` bật khi lần đo cũ trôi khỏi khoảng | GV thấy banner dù không có số đo mới | Câu banner nói "số đo trong khoảng đã thay đổi" | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer: Web `childrenApi.analyzeHealthTrend`, App `app_notification.dart`; `openapi.yaml`
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] >3 phase: giữ 1 plan vì cùng một yêu cầu, mỗi phase một repo hoặc một phần BE
- [x] HLT-21 là PROPOSED (user chốt), ghi rõ
- [x] Không placeholder ngoài Q5 (mã công việc App chưa có)

## Progress log
### 2026-11-14 — duyệt plan
- User trả lời Q1–Q5 (Q5 = A) và chọn A khi được hỏi "chọn A hay B cho Q5 và cho tôi duyệt plan" ⇒ coi là duyệt.

### 2026-11-14 — Phase 1 (chờ review)
- Commit: chưa commit
- Verify: `mvnw test -Dtest=HealthTrendContentRulesTest,HealthTrendServiceImplTest,HealthRecordControllerTest,HealthGrowth*` → 52 pass; `-Dtest=HealthRecordIntegrationTest,HealthExamIntegrationTest,DevSeedIntegrationTest,OpenApiSnapshotTest` → 26 pass; `ArchitectureRulesTest` → 14 pass. Snapshot `openapi.yaml` cập nhật bằng `-Dopenapi.snapshot.update=true` (thêm query `period`, field `period` ở 2 schema).
- Khác plan: `V131` (lúc đầu là `V130`) có luôn CHECK `HEALTH_TREND_APPROVED` (phase 2 dùng) để không phải sửa migration sau khi commit phase 1.
- Phát hiện: `origin/dev` đã có `V129__parent_activation_codes.sql`, trùng số với `V129__health_interpretation_content.sql` của nhánh này (đã push). Merge vào `dev` sẽ lỗi Flyway (2 migration cùng version). Cần user quyết cách đánh lại số.
- Tiếp: Phase 2 (thông báo khi duyệt).
- Xử lý (user chọn A): `git mv` `V129__health_interpretation_content.sql` ⇒ `V130__…`; migration mới ⇒ `V131__health_trend_period_and_notice.sql`; sửa tham chiếu trong code, test, `DATA_DICTIONARY.md`. DB local đã chạy `V129__health_interpretation_content` của nhánh này sẽ bị Flyway báo lệch ⇒ dựng lại DB local.

### 2026-11-14 — Phase 2 (chờ review)
- Commit: chưa commit
- Verify: `mvnw test -Dtest=HealthTrend*Test,HealthRecordControllerTest,PushDispatchListenerTest,NotificationServiceImplTest,HealthCheckServiceImplTest,HealthRecordIntegrationTest,HealthExamIntegrationTest,DevSeedIntegrationTest,OpenApiSnapshotTest,ArchitectureRulesTest` → 85 chạy, chỉ `OpenApiSnapshotTest` lệch (enum thông báo thêm `HEALTH_TREND_APPROVED`, có chủ đích) ⇒ cập nhật snapshot ⇒ chạy lại `OpenApiSnapshotTest,HealthRecordIntegrationTest,PushDispatchListenerTest,HealthTrendServiceImplTest` → 29 pass.
- Gặp lỗi: `target/classes` còn bản copy migration tên cũ ⇒ Flyway "Found more than one migration with version 130"; xóa 2 file cũ trong `target/classes/db/migration` rồi chạy lại.
- Tiếp: Phase 3 (Web).

### 2026-11-14 — Commit BE phase 1–2 (chưa xong)
- Commit đổi tên `V129` ⇒ `V130` bị pre-commit chặn ("migration đã commit là bất biến"); chạy lại với `CARENEST_ALLOW_MIGRATION_EDIT=1` (cờ hook dành cho trường hợp user đã duyệt) bị bộ phân quyền tự động của công cụ từ chối ⇒ chờ user tự chạy commit này.

### 2026-11-14 — Phase 3 Web (chờ review)
- Commit: chưa commit (repo `CareNest_FE`)
- Verify: `npm run test:health` → 124/124 pass; ESLint, Prettier sạch trên file đã sửa; `vite build` exit 0.
- Thay đổi: `TREND_PERIODS` dùng giá trị BE (`MONTHS_6`/`MONTHS_12`/`ALL`) cho cả bộ lọc biểu đồ và phân tích; `trendPeriodSince`/`withinTrendPeriod` tính lùi từ hôm nay giờ địa phương như BE `minusMonths` (bỏ `toISOString` lệch múi giờ của trang); panel gửi `period`, hiện nhãn "Phân tích 6 tháng", nút "Phân tích lại (6 tháng)" khi khác khoảng, khoảng thiếu số đo giữ bản cũ và báo; mock lọc theo khoảng, lưu `period`, `stale` theo khoảng.
- Khác plan: mock Web không tạo thông báo phụ huynh (phụ huynh không dùng Web). `FE_API_CALLS.md` không đổi (endpoint giữ nguyên, chỉ thêm query).
- Tiếp: Phase 4 (App).

### 2026-11-14 — Commit phase 1–3
- BE: `f97faabb` (đổi số migration, user tự cho phép cờ hook trong lượt này), `e0061985` (khoảng thời gian + thông báo). Push BE bị công cụ chặn vì lượt này user chỉ yêu cầu commit ⇒ chưa push.
- Web: `901ef1d0`, chưa push.

### 2026-11-14 — Phase 4 App (chờ review)
- Commit: chưa commit (repo `carenest_app`, nhánh `feature/G94-194-FE-FEAT-49-app-ui-redraw`, mã G94-334)
- Verify: `flutter test test/features/notifications` → 9 pass; `flutter analyze` → No issues; `flutter test` → 220 pass.
- Thay đổi: `NotificationType.healthTrendApproved('HEALTH_TREND_APPROVED')`, icon sức khỏe, lối mở "Xem sức khỏe của bé"; dữ liệu giả `p5`; `PUSH_NOTIFICATION.md` thêm dòng loại mới (BE: nhánh G94-180).
- Chưa chạy `node scripts/verify.mjs`/`check-ai-layer.mjs` của App.

