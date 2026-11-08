**Execution approval07/11/2026:** user đã duyệt FE và yêu cầu tiếp tục implement/converge. Các đoạn draft/chờ duyệt dưới là lịch sử thiết kế; triển khai trong phạm vi plan/contract/delta đã chốt, giữ review theo phase và không deploy/commit/push.

# Implementation Plan: Kế hoạch năm học và Kế hoạch tổ

**Thiết kế delta Q4=A/Q5=A cập nhật 07/11/2026:** mẫu nhánh PHT tùy chọn lấy/không lấy và sửa tên bản lớp; đổi thứ tự chuyển cả khối chủ đề kèm nhánh, giữ số tuần học, dời ngày. Thiết kế đã cập nhật theo quyết định mới, còn draft chờ duyệt triển khai; không giữ ràng buộc khóa tên hoặc bắt lấy đủ mẫu cũ.

**Branch**: `feature/G94-326-BE-FEAT-09-school-year-goals-backup` | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: FR-001..017, domain spec 14 mục, hai vòng clarify2/2 mỗi vòng đã chốt, gồm Q4=A/Q5=A. Lượt setup-plan mới tái sử dụng plan hiện có, `BRANCH=012-school-year-team-plans` là feature slug; Git branch thực như trên, không tạo/chuyển nhánh. **Status:** design draft cập nhật delta, chưa duyệt triển khai.

**Goal:** thêm cấp năm do PHT soạn/HT duyệt, giữ cấp tổ làm nguồn GV, cây kế thừa cố định nhưng tick như cũ, mẫu nhánh tùy chọn lấy/sửa tên, nhánh bổ sung và lịch lớp có giới hạn cấu hình.

**Architecture:** mở rộng họ EduYearPlan bằng level và snapshot nguồn, giữ workflow/version/ID; một calendar resolver cho lớp để nhánh nhiều tuần vẫn có các week docs riêng. Client route/mapper/capability đổi phối hợp với BE. Tương thích observation chỉ ở nguồn TEAM/ngày lớp, không đổi công thức đánh giá.

**Tech Stack:** Java21, Spring Boot4.1.1, Maven wrapper, PostgreSQL17/JPA/Flyway, React client hiện có; không thêm/nâng dependency hoặc hạ tầng.

**Execution:** tasks và analyze đã thực hiện; C1/I1/I2 được sửa theo yêu cầu user 07/11/2026. Agent tự điều phối theo constitution2.0.0, không chạy harness dự án. Sau user duyệt triển khai, implement theo phase và converge; giữ review từng phase, không tự commit/push.

## Điều chỉnh thứ tự theo user — 07/11/2026

User cho phép sửa CareNest_FE để review trước. Ưu tiên FE/mock và transport theo contract đã thiết kế, kiểm chứng trực tiếp rồi dừng chờ user duyệt giao diện; không mở trình duyệt. Thứ tự FE-first này thay phụ thuộc review P2/BE-first trong lịch cũ cho phạm vi review, không coi API thật đã có hoặc duyệt backend/migration. Sau review FE mới tiếp tục phần BE và nghiệm thu tích hợp. Các task gốc chỉ hoàn thành khi đủ bằng chứng toàn phần.

## Summary

Giữ ID legacy và default `/goals` là TEAM; thêm SCHOOL_YEAR riêng, `curriculumSourcePlanId` không lẫn `sourcePlanId` predecessor. TEAM source-backed clone cây annual đã ban hành; server guard cố định cây, cho delta tick từng content. Giữ workflow key year=TEAM, thêm schoolYear. Nhánh mẫu thuộc annual/tổ; branch lớp v2 có key/range/source và lịch chủ đề lớp; week docs giữ khóa Monday. Duration HT-only có snapshot lúc tạo, legacy không bị áp trần hồi tố.

Delta Q4/Q5 không thêm schema/migration: chọn mẫu là tạo bản nhánh local UUID/source, bỏ chọn không sửa nguồn, name local mutable. Reorder dùng pure helper trên slots tuần học đang dùng và offsets nhánh; preview không ghi, PUT branch có orderedThemeIds để server thực thi cùng helper, resize thường giữ riêng. A2/B4→B4/A2; gap không biểu diễn được thì trả lỗi, không tự pack/nén. File preview tạo được custom từ form rỗng, không chỉ rename nhánh có sẵn. Identity+local form revision giữ preview/save responses không đè thay đổi mới.

**Phạm vi cần duyệt:** [plan quản trị làn L](../../docs/plans/active/2026-11-07-school-year-team-plans.md), [contract](contracts/education-plans.md), [ADR-0025 PROPOSED](../../docs/decisions/ADR-0025-school-year-team-plan-levels.md). Phase FE cần quyền sửa `CareNest_FE` riêng; không kế thừa permission feature011. Không sửa APP, không deploy trong plan.

## Technical Context

**Language/Version**: Java21/Boot4.1.1 xác minh `pom.xml:9,20`; Node22 theo README và scripts verify; stack FE giữ repo hiện tại.

**Primary Dependencies**: Spring MVC, Spring Data JPA, Resource Server/Keycloak, Flyway, springdoc, Jackson; JUnit/Testcontainers/ArchUnit/Spotless hiện có. Không chọn library mới.

**Storage**: PostgreSQL17; ba migration mới sau V82, giữ bảng cây ADR-0021, JSONB class docs, metadata nhánh mẫu relational. Không binary DB, không đổi S3 học liệu.

**Testing**: regression unit/service/controller/migration chạy trực tiếp Maven, kiểm OpenAPI snapshot; FE test/build/lint trực tiếp bằng công cụ gốc và browser E2E trên BE thật + synthetic fixture. Kết quả mong đợi trong [quickstart](quickstart.md), chưa phải test đã chạy.

**Target Platform**: API/VPS hiện có và Web teacher/management; không thêm mobile screen.

**Project Type**: modular monolith backend + client Web được triển khai phối hợp, không module/service hạ tầng mới.

**Performance Goals**: giữ baseline list 24 hồ sơ dưới 100 SQL đã dùng feature011; batch source/template/calendar reads, không N+1 theo lớp/tuần. Không tự hứa SLA mới hoặc mở scale đa trường.

**Constraints**: 1 trường/2 campus, server assignment scope, EDU-54 chống tự duyệt/REVIEW read-only, manual save/lease/revision/idempotency giữ; không sửa history/snapshot đã ban hành hoặc freeze đã xác nhận. Source-backed TEAM không sửa cây; legacy không giả nguồn năm.

**Scale/Scope**: năm/nhóm tuổi, hai cấp có chuỗi riêng; nhánh lớp có 1..2 tuần mặc định, lớn2..4 tuần, các giá trị cấu hình được. Hai BE modules bị ảnh hưởng (education-plan, observation compatibility), security policy giáo dục, schema/API và nhiều FE consumers → lane L.

## Constitution Check

| Gate | Trước research | Sau thiết kế |
| --- | --- | --- |
| Business truth | Q1–Q3, hai clarify đầu và Q4=A/Q5=A có nguồn user | FR1–17→AC1–11→phase/test/Q01–Q19+R1–R8; không mandatory template/name equality, reorder giữ tuần; EDU-55/56 vẫn PROPOSED theo nguồn, user phải duyệt delta trước code |
| Existing stack/boundaries | Không thêm dependency | Layered service/repository giữ, projection eduplan→observation chỉ theo chiều đọc, ADR0020/21 giữ |
| Security/privacy | Scope hiện có phải giữ | Filter TEAM+level/query ID/source, assignment roles/author ledger theo document, fixture synthetic |
| Verification/human control | Plan-only | Chưa được phép code/contract/migration/client; từng phase kiểm chứng trực tiếp/snapshot, review, source changes chỉ sau approval |
| Traceable spec loop | Domain/constitution/spec/clarify đủ | research/data-model/contracts/quickstart/tasks đã tạo; analyze đã báo cáo và sửa C1/I1/I2; implement/converge chưa chạy |

Không có exception constitution được yêu cầu hoặc chấp nhận. **Design gate sau research delta PASS**: Q4/Q5 được cụ thể hóa, không còn unknown kỹ thuật hoặc câu nghiệp vụ chặn; independent review mới được ghi trong Progress log quản trị trước bàn giao. **Implementation gate CLOSED** đến khi user duyệt plan/migration/API delta và cấp phạm vi FE; review/approval trước delta không thay cổng này. Jira metadata lấy nhánh hiện tại, không tự khẳng định task Jira mới riêng hoặc commit.

## Project Structure

### Documentation (this feature)

```text
specs/012-school-year-team-plans/
  domain-spec.md, spec.md, plan.md, research.md, data-model.md, quickstart.md
  contracts/education-plans.md, contracts/clients.md
  checklists/requirements.md
  tasks.md
docs/plans/active/2026-11-07-school-year-team-plans.md
docs/decisions/ADR-0025-school-year-team-plan-levels.md
```

`tasks.md` đã được tạo bởi speckit-tasks sau lượt plan:68 task chưa hoàn thành, chia theo US1/US2/US3 và có lịch giao nhận P1/P2/P3 giữ gate. Analyze đã thực hiện và các finding C1/I1/I2 đã được sửa theo yêu cầu user; tạo tasks không duyệt code/API/migration/FE và không thay implementation status của feature011.

### Source Code (repository root)

| Nhóm file | Trách nhiệm / phase |
| --- | --- |
| M entity/eduplan/EduYearPlan.java, EduClassDoc.java, EduSettings.java; C utils/enums/EduPlanLevel.java | level/source/limits snapshot, P1/P2 |
| C entity/eduplan/EduPlanBranchTemplate.java, repository/eduplan/EduPlanBranchTemplateRepository.java; M repository/eduplan/EduYearPlanRepository.java, EduThemeRepository.java | template FK, source-aware version queries, P1 |
| C resources/db/migration/V83__split_education_plan_levels.sql, V84__add_education_branch_templates.sql; P2 V85__snapshot_theme_evaluation_presentation.sql | legacy/unique-by-level/limits/templates P1; frozen evaluation presentation P2 |
| M service/eduplan/impl/EduYearPlanServiceImpl.java, YearPlanPublicationValidator.java, YearPlanVersionDiff.java; C InheritedCurriculumGuard.java | create/copy/frozen-tree guard/tick/version/publication, P1/P2 |
| M service/eduplan/impl/EduWorkflowServiceImpl.java, EduWorkflowPolicy.java; security/authorization/EduPlanAccessPolicy.java | policy by level, scope/actions/reconcile, P1 |
| M controller/eduplan/EduYearPlanController.java, EduWorkflowController.java, EduClassDocController.java; dto/eduplan/YearPlanRequest.java, YearPlanDetailResponse.java, YearPlanSummaryResponse.java, ClassDocsResponse.java, BranchFilePreviewResponse.java | level/source/capability/calendar contract, P1/P2 |
| C controller/eduplan/EduDurationPolicyController.java; service/eduplan/EduDurationPolicyService.java + impl; dto/eduplan/EduDurationPolicyRequest.java, EduDurationPolicyResponse.java | HT-only revisioned config, P1 |
| C service/eduplan/impl/EduTeachingCalendar.java; M EduClassDocServiceImpl.java, EduGoalFileServiceImpl.java, BranchFileTable.java | effective calendar, v1/v2 adapter, source guards/import, P2 |
| M service/eduplan/EduClassDocService.java; C dto/eduplan/ClassCalendarPreviewRequest.java, ClassCalendarPreviewResponse.java | previewClassCalendar(CurrentUser,request), pure preview+orderedThemeIds command, P2; expose qua EduClassDocController có sẵn |
| C service/eduplan/EduPlanCalendarQueryService.java + impl/EduPlanCalendarQueryServiceImpl.java | approved class-specific theme projection, P2 |
| M repository/observation/ThemeEvaluationRepository.java, service/observation/impl/ThemeEvaluationServiceImpl.java, entity/observation/ThemeEvaluation.java | TEAM filtering, effective date reads, confirmed-first union/lookup + presentation snapshot, P2 |
| M mapper/eduplan/EduYearPlanMapper.java, utils/ApiCode.java, docs/api/openapi.yaml | response/error contracts and mapper metadata, P1/P2 |
| M client files tại contracts/clients.md | routes/year/team/curriculum locks/range/config/import/export, P3 sau permission |

Các đường Java ở bảng tính từ `src/main/java/com/carenest/`, resources từ `src/main/`. Dùng service interfaces có sẵn và helper nhỏ cho logic mới, không tạo service monolith thứ hai hoặc refactor cả module. Test paths tương ứng dưới `src/test/java/com/carenest/`.

## Phases và test mapping

Phạm vi >8 files: đề xuất ba phần giao nhận tuần tự dưới một plan để bảo toàn source/migration/API/client contract thống nhất. Mỗi phần có exit/review riêng; không tách repo/schema rời rollout. Chi tiết source evidence tại research.

| Phase | Deliverable / FR / AC | Test RED→GREEN dự kiến |
| --- | --- | --- |
| P1 — nền hai cấp | FR1–8/11/13/14 (BE metadata/quyền), AC1/3/6/7; schema/level/version/source/config/workflow | C repository/eduplan/EduPlanLevelMigrationIntegrationTest: preserveLegacyIdsAndSnapshots, independentOpenVersions; M EduYearPlanServiceIntegrationTest: teamRequiresIssuedAnnual, immutableSourcePointer, explicitAnnualVersionRebaseAndLegacyAdoption; M EduWorkflowServiceIntegrationTest/EduWorkflowAccessPolicyTest: separatePolicyKeysAndAuthors; M EduConfigServiceIntegrationTest: principalOnlyDurationSnapshot |
| P2 — nguồn cố định và lịch | FR9–17/13, AC4/5/6/8/9/10/11; mẫu tùy chọn/rename, calendar v2/reorder, import từ rỗng, immutable-tree và downstream | C EduTeachingCalendarTest: twoWeekBranchSkipsFullBreak, reorderPreservesThemeAndBranchWeekCounts, gapRetimeRejectsWithoutPacking, partialBreakCounts, parentBounds; C EduBranchRangeIntegrationTest: optionalTemplatesAndLocalRename, noneSelectedCanImportCustom, calendarPreviewIsReadOnly, reorderCommandAndResizeAreDistinct, versionedWeekKeysRemainStable, classSchedulesIsolated, v1DoesNotFlattenV2; M EduYearPlanServiceIntegrationTest: frozenTreeAllowsContentTicksOnly, omittedGoalRejected; M BranchFileImportTest/EduClassDocServiceIntegrationTest: oneBranchTwoWeekDossier, previewUsesUnsavedCalendarWithoutWriting; M ThemeEvaluationIntegrationTest: teamSourceClassDatesAndFrozenMetadata, confirmedThemeRemainsReadableAfterSourceRemoval; M EvaluationLockOrderTest |
| P3 — Web và nghiệm thu phối hợp | FR1–17/SC1–6/AC1–11; route/forms/tick/range/config/print/real browser/rollout | FE tests/education-plan thêm school-year-team.test.mjs, branch-range.test.mjs, mở rộng tests tick/config/import/version; C tests/e2e/system/school-year-team-plans.spec.mjs Q01–Q19 theo quickstart, BE/Keycloak thật |

Mỗi phase bắt đầu bằng failing regression của logic thay đổi, đọc fail đúng nguyên nhân rồi implement tối thiểu và chạy GREEN. Tên method ở bảng là dự kiến cho tests mới/bổ sung, không tuyên bố hiện đã tồn tại. Mọi DisplayName dùng rule đã xác nhận liên quan + FR nguồn user, không giả EDU55/56 là team-confirmed.

Exit P1/P2: chạy trực tiếp Maven cho các regression của phase, ArchUnit/Spotless và kiểm OpenAPI snapshot/migration/source; ghi fail/error/skip thật và kiểm diff tài liệu. P3: regression BE liên module + test/build/lint FE trực tiếp + browser Q01–Q19 trên server build cuối, dữ liệu test cleanup đúng owned IDs. Không gọi harness hoặc wrapper verify/check của dự án. **Dừng user review sau mỗi phase.** Không bỏ qua failed gate vì phase chưa dùng UI.

## Review Focus

1. Level không có trong chain/current helper hoặc native SQL → annual bị chọn làm source lớp: P1 independentOpenVersions, P2 teamSourceClassDatesAndFrozenMetadata, P3 selector test.
2. `readOnly` khóa cả tick hoặc full PUT xóa cây bằng omission → P2 frozenTreeAllowsContentTicksOnly/omittedGoalRejected, P3 UI tick test.
3. Mẫu bị auto-add/khóa tên, form rỗng không nhập được, reorder mất nhánh hoặc đổi tuần → P2 optionalTemplatesAndLocalRename/noneSelectedCanImportCustom/reorderPreservesThemeAndBranchWeekCounts/gapRetimeRejectsWithoutPacking; P3 late-response/manual-save/export/source-switch tests.
4. Old config/v1 payload reset schoolYear hoặc flatten ranges → P1 separatePolicyKeysAndAuthors, P2 v1DoesNotFlattenV2, P3 mapper roundtrip.
5. Reconcile/media/calendar lock order hoặc metadata đánh giá đã chốt bị đổi → P2 concurrency và EvaluationLockOrderTest/frozen metadata, P3 E2E/reload.

## Complexity Tracking

Không có vi phạm constitution cần miễn trừ. Thêm một bảng mẫu nhánh và JSON lịch lớp, không thêm tenant, broker, cache hoặc workflow engine. [ADR0025](../../docs/decisions/ADR-0025-school-year-team-plan-levels.md) ghi lựa chọn cùng phương án thay thế, trạng thái PROPOSED chờ duyệt. Phần rollback và ma trận acceptance đầy đủ tại plan quản trị/quickstart.

### Delta FE review đã chốt
- FR004: nhánh/dự án nhập tại cột phần 2 năm, Enter thêm dòng; mẫu không bắt lịch cấp năm. TEAM giữ cột map. FR018/EDU57: UI/mock/server cấm riêng PHT xóa kế hoạch tổ; backend chưa làm, không suy đã bảo vệ API thật.

## Delta nhãn mục tiêu — approved by user07/11/2026

Bổ sung displayLabel nullable varchar100 trên edu_plan_goals bằng V86 mới (không đổi V85); request Row @Size100, response Row field additive, copyFor bảo toàn nhãn. Null/absent giữ nguyên, blank clear. Guard từ chối đổi nhãn TEAM; goalNo/ID không đổi. FE adapter, input + drag-fill/keyboard, mock/export giữ nhãn và fallback MTn. Hai loại bảng tách fixed mode; reorder sections và shared date picker. Xem plan client `../CareNest_FE/docs/plans/completed/2026-11-07-year-plan-goal-labels.md` (tính từ repo root: sibling FE).
Domain/spec/data-model/contract/tasks đã bổ sung delta FR019/020, AC13; constitution2.0.0 tái sử dụng, không đổi. User duyệt toàn bộ liên tục; thay bước dừng review mỗi phase trong các plan cũ. Verify native Maven/Node/ESLint/Prettier/Vite, không harness.

### Kết quả delta nhãn/UI07/11/2026

BE64/64 native4classes, fail/error/skip0; PostgreSQL V85→V86 upgrade + label roundtrip/compatibility/clone/guard/size. Runtime migrate86/start/healthUP, OpenAPI schemas đúng và đã cập nhật. FE248/248 + ESLint/Prettier/Vite exit0, input/pointer/keyboard/cancel/capture + API/mock/in tests. Independent review2P2 đã sửa; convergence delta FR019/020 không còn gap triển khai phát hiện; chưa browser/E2E/fullBE/touch thật. Giữ pending gaps feature012 cũ. Xem docs/quality/VERIFICATION.md; không harness/Git/deploy.

## Delta FR021 — thực hiện theo chỉ đạo user07/11/2026
DatePickerCell có mode compact tùy chọn (default giữ cũ), branch dùng hai trigger riêng. Dùng helper validation Web cho cảnh báo/submit/mock và validator BE dùng chung submit/publication để kiểm weekday/overlap sibling; API transport giữ nguyên, dùng EDUCATION_CALENDAR_INVALID. Test pure/helper/render/DatePicker + native Maven validator/integration; không harness/browser.

### FR021 — kết quả07/11/2026
BE71/71 native4classes, PostgreSQL; FE254/254, lint/format/Vite exit0; review độc lập không blocker. Kiểm hai endpoint compact/default, sửa đầu riêng, warnings/taken, sibling overlap/weekday và mock/server draft-vs-submit. Converge delta không gap theo source/tests; chưa browser/E2E/fullBE/touch thật, không harness/Git/deploy.
