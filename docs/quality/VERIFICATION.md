# Verification — bằng chứng trước khi báo xong

## BUG-261106-edu-legacy-approval-time — 06/11/2026

- Tái hiện trước sửa: `EduWorkflowSerializationTest.legacyLocalApprovalTimestampIsReadAsSchoolTime` lỗi tại `Instant.parse` với timestamp legacy `2026-11-04 06:28`.
- Sau sửa, full verify chạy hết **1.130 test, 0 fail/error/skip**; ArchUnit và OpenAPI snapshot đạt. Lượt này chỉ fail Spotless do format của regression test.
- Format test và chạy `node scripts/verify.mjs --quick`: **PASS, 19 test, 0 fail/error/skip; ArchUnit ok; Spotless ok** (`target/verify.log`).
- `node scripts/check-ai-layer.mjs`: 0 lỗi, 12 cảnh báo liên kết/plan cũ ngoài phạm vi thay đổi.

## Prompt playbook cho Spec Kit và harness — 06/11/2026

- Tạo `docs/quality/SPEC_AND_HARNESS_PROMPTS.md` với cách chọn Spec Kit/workflow và harness có sẵn theo loại task/làn, prompt copy-paste, lệnh kiểm chứng và giới hạn khi báo cáo.
- Kiểm tra tài liệu: `node scripts/check-ai-layer.mjs` exit 0; 0 lỗi, 12 cảnh báo link/plan cũ ngoài phạm vi file mới. `git diff --check` không phát hiện lỗi whitespace. Không đổi source nên không chạy full application verify.

## Spec011 — kết quả vòng implement/converge cuối, 06/11/2026

User yêu cầu lặp implement/verify/converge tới khi không còn gap; cho phép sửa FE phần giáo dục và xác nhận APP chưa tích hợp. Không commit/push/deploy. Kết quả cuối: BE `node scripts/verify.mjs` PASS — 1129 test, fail 0, err 0, skip 0; ArchUnit, OpenAPI snapshot, Spotless đều đạt (`target/verify.log`). FE `npm run verify` PASS — 207/207 test giáo dục, lint/format/design/API/build đạt (`CareNest_FE/node_modules/feature011-fe-final-verify.log`). Browser WF-01/02/03 trên source BE cuối, Keycloak và PostgreSQL fixture riêng đạt 3/3 (`CareNest_FE/node_modules/feature011-browser-final.log`). GET danh sách 24 hồ sơ: 23 câu SQL so với baseline 258, dưới ngưỡng <100. `node scripts/check-ai-layer.mjs`: 0 lỗi, 12 cảnh báo đường dẫn/plan cũ ngoài feature. APP không tích hợp theo xác nhận của user; không sửa APP. Không đo line coverage nên không báo phần trăm.

Kết quả convergence: T059–T078 đã được đối chiếu và hoàn thành bằng source, regression và/hoặc documentation phù hợp. Các đoạn “58/58” và các blocker cũ bên dưới là nhật ký lịch sử trước convergence; bằng chứng hiện hành nằm ở phần này và log cuối nói trên.

## Spec011 — Hoàn thành toàn diện Feature 011 (BE + FE + Cutover V81), 06/11/2026

- Đã hoàn thành 58/58 tasks của Feature 011 theo đúng spec, plan và các quyết định nghiệp vụ của User:
  - **3 trạng thái chính thức**: `DRAFT` (Nháp) → `REVIEW` (Duyệt / Đang duyệt) → `APPROVED` (Ban hành).
  - **Trạng thái REVIEW**: Người soạn tuyệt đối không được sửa nội dung, cấm lấy author lease.
  - **Duyệt & Trả lại**: Duyệt bước cuối cùng thì tự động Ban hành (`APPROVED`); trả lại để sửa thì chuyển về Nháp (`DRAFT`) kèm lý do `note`.
  - **Snapshot bản in**: Bản đã ban hành đóng băng `print_snapshot` (ô ký, vai trò, tên người) bất biến, cấu hình sau này không làm thay đổi bản in cũ.
  - **Ngăn tự duyệt**: Sổ cái `edu_document_authors` ghi nhận tác giả thực tế, cấm người soạn duyệt nội dung mình soạn; ưu tiên vai trò reviewer (reviewer precedence) ngăn người duyệt soạn cùng loại tài liệu.
  - **Chỉ Hiệu trưởng cấu hình**: Chỉ HT có phân công hiệu lực được cấu hình `workflow-policy`.
  - **Cutover V81**: Đã xóa toàn bộ constants cũ (`EDITING`, `PENDING_*`, `REJECTED`) khỏi `EduPlanStatus.java` và `EduClassDocStatus.java`. Migration contract `V81__contract_edu_workflow_policy.sql` siết CHECK chỉ còn đúng 3 trạng thái.
  - **Producer compatibility**: `EduYearPlan.java`, `EduYearPlanMapper.java` ghi nhận publisher thật tại `published_by`/`completed_at`, `issued_by` legacy chỉ lưu actual VP approval hoặc null.
- **Bằng chứng kiểm thử Backend (CareNest_BE)**:
  - `node CareNest_BE/scripts/verify.mjs`:
    ```text
    VERIFY PASS full | tests 1104 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 691s | log target/verify.log
    ```
  - `node CareNest_BE/scripts/check-ai-layer.mjs`: **0 lỗi**.
  - Các bộ test trọng yếu mới:
    - `EduWorkflowPolicyTest`: 7/7 tests GREEN.
    - `EduWorkflowControllerTest`: 12/12 tests GREEN.
    - `EduWorkflowMigrationIntegrationTest`: GREEN cả mốc V80 expand và V81 contract.
    - `EduWorkflowAccessPolicyTest`: 4/4 tests GREEN.
    - `EduWorkflowCandidatesIntegrationTest`: GREEN.
    - `EduWorkflowPublicationTest`: 3/3 tests GREEN.
    - `EduWorkflowServiceIntegrationTest`: GREEN.
    - `EduWorkflowCompatibilityIntegrationTest`: 3/3 tests GREEN.
    - `EduDocumentAuthorIntegrationTest`: 10/10 tests GREEN.
    - `EduReviewRoundIntegrationTest`: 7/7 tests GREEN.
    - `EduYearPlanServiceIntegrationTest`: 37/37 tests GREEN.
    - `EduClassDocServiceIntegrationTest`: 20/20 tests GREEN.
    - `EduConfigServiceIntegrationTest`: 10/10 tests GREEN.
    - `EduYearPlanControllerTest`: 13/13 tests GREEN.
    - `EduYearPlanSchemaIntegrationTest`: 8/8 tests GREEN.
    - `OpenApiSnapshotTest`: 3/3 tests GREEN (khớp 100% với schema 3 trạng thái).
- **Bằng chứng kiểm thử Frontend (CareNest_FE)**:
  - `npm --prefix CareNest_FE run test:edu`:
    ```text
    1..199
    # tests 199
    # suites 0
    # pass 199
    # fail 0
    ```
  - `npm --prefix CareNest_FE run check`:
    - eslint: 0 errors.
    - format:check: All matched files use Prettier code style!
    - check:design: ok · hex 22/43 · cssPx 16/16 · inlineStyle 37/37 (hiện tại/baseline).
    - check:api: 0 lỗi.
    - vite build: ✓ built in 17.81s (exit code 0).
- **Cập nhật tài liệu kỹ thuật**: Đã hoàn thiện đồng bộ `DATA_DICTIONARY.md`, `ERD.md`, `BUSINESS_RULES.md`, `education-plan.md`, `CROSS_REPO_MAP.md`, `ADR-0024-education-workflow-policy.md`.
- Tuyệt đối tuân thủ cam kết: không tự commit, push, tạo branch, PR hoặc deploy.

## Spec011 — Hoàn thành Phase 3: User Story 1 (T015..T026), 06/11/2026

- Đã hoàn thành các task backend của User Story 1:
  - `T015 [P] [US1]`: Tạo test `EduWorkflowControllerTest.java` (12/12 tests GREEN) cho `GET /education-plans/workflow-policy` và `PUT /education-plans/workflow-policy`, bao gồm kiểm tra vai trò Hiệu trưởng (HT) có phân công hiệu lực (`hasActivePrincipalAssignment`), từ chối PHT/Giáo viên (403), từ chối HT hết phân công (403 errorCode 7201), stale revision (409 errorCode 9012), invalid payload (400 errorCode 7202), và no-op request.
  - `T016 [P] [US1]`: Mở rộng `EduConfigServiceIntegrationTest.java` (10/10 tests GREEN) trên Testcontainers PostgreSQL 17: lưu policy mới tăng revision, GET config trả `workflow`, `canConfigure` và `yearCreationScopes`; lưu policy tự động reconcile kế hoạch năm đang `REVIEW`, giữ approvals hợp lệ, hoàn tất `APPROVED` với `completionMode=POLICY_CHANGE`; rollback toàn bộ transaction khi publication validator ném lỗi; từ chối sửa `signers` qua endpoint legacy `PUT /education-plans/config` (403) nhưng chấp nhận echo signers trùng server projection.
  - `T018 [US1]`: Tạo DTOs `EduWorkflowPolicyRequest`, `EduWorkflowPolicyResponse` và controller `EduWorkflowController`.
  - `T019 [US1]`: Triển khai `savePolicy` và `getPolicy` trong `EduWorkflowServiceImpl` với lock độc quyền `findForUpdate()`, validate allowlist/disjoint/non-empty, reconcile pending atomically trong cùng transaction; chuẩn hóa mô tả mã lỗi trong `ApiCode.java` (7201, 7202, 7204 trung tính).
  - `T020 [US1]`: Cập nhật `EduConfigService`, `EduConfigServiceImpl`, `EduConfigController`, `EduConfigResponse` để expose `workflow`, `canConfigure`, `yearCreationScopes` và chặn sửa workflow qua endpoint legacy.
  - `T024 [US1]`: Cập nhật snapshot OpenAPI trong `docs/api/openapi.yaml`, chạy `ControllerSecurityCoverageTest` (4/4 tests GREEN) và `OpenApiSnapshotTest` (3/3 tests GREEN).
  - `T025 & T026 [US1]`: Chạy toàn bộ verify: `VERIFY PASS full | tests 1082 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 650s`. Chạy `check-ai-layer.mjs`: 0 lỗi.
  - Ranh giới FE: Các task FE (T017, T021..T023) giữ nguyên trạng thái chờ theo yêu cầu user (không tự sửa FE).
- Dừng lại tại Checkpoint User Story 1 để chờ User review trước khi chuyển sang Phase 4 (User Story 2).

## Spec011 — Hoàn thành Phase 1 Foundations (T004..T014), 06/11/2026

- Đã hoàn thành toàn bộ mã nguồn và kiểm thử cho các foundational primitives T004..T014:
  - `EduWorkflowPolicyTest`: 7/7 tests GREEN (FR-003/006/009/011/012, AC-02/06/07).
  - `EduWorkflowMigrationIntegrationTest`: Testcontainers PostgreSQL 17 kiểm thử mốc Flyway V80 expand schema/seeds/constraints, bảo toàn legacy compatibility.
  - `AccountServiceImplTest`: 5/5 tests GREEN cho helper `findActiveUserIds`.
  - `EducationReviewerAssignmentsIntegrationTest` & `EduWorkflowCandidatesIntegrationTest`: GREEN cho helper `findActiveEducationReviewerAssignments` và candidate resolution.
  - `EduWorkflowAccessPolicyTest`: 4/4 tests GREEN cho identity-role predicates và reviewer-precedence.
  - `EduWorkflowPublicationTest`: 3/3 tests GREEN cho `YearPlanPublicationValidator` (EDU-51/52/53).
  - `EduWorkflowServiceIntegrationTest`: 2/2 tests GREEN cho lock order, author ledger, version validation.
  - `OpenApiSnapshotTest`: Cập nhật snapshot `docs/api/openapi.yaml` bổ sung enum `REVIEW` trong `EduPlanStatus`.
- Cổng tài liệu: `node scripts/check-ai-layer.mjs` đạt (0 lỗi, 12 cảnh báo cũ).
- Cổng verify làn S: `node scripts/verify.mjs --quick` (chuyển full do có migration V80) xác nhận 1064 tests đạt (0 fail, 0 err, 0 skip, archunit ok, snapshot ok). Định dạng code chuẩn hóa qua `mvnw spotless:apply` (spotless:check đạt).
- Dừng lại tại Checkpoint Phase 1 / T014 để chờ User review theo đúng quy trình; không tự ý commit/push/deploy/tạo PR.

## Spec011 — cập nhật tasks sau analyze, 06/11/2026

- setup-tasks.ps1 -Json xác nhận feature011/template; đối chiếu bộ thiết kế hiện tại, giữ T001..T058/checkbox chưa thực hiện/11task P. US1:12, US2:14, US3:12, phần chung20. Bổ sung handoff tự đủ ngữ cảnh cho phiên khác, không thay nghiệp vụ/contract/migration hoặc quyền FE/APP; plan vẫn draft.
- Kiểm checklist/ID/label/path/dependency và cổng check-ai-layer + verify --quick sau chỉnh cuối; output thật được báo ở bàn giao và target/verify.log. Chưa chạy task implementation/full/migration/E2E.

## Spec011 — sửa diễn đạt I3 LOW, 06/11/2026

- Theo lệnh fix sau analyze: data-model xác định completion_policy chỉ phục vụ metadata/lịch sử; T049 và research D5 thống nhất bản in dùng review.printSnapshot đã lưu, giữ role/label/names và origin PUBLICATION/LEGACY_BASELINE. Không thay nghiệp vụ, ID/task, trạng thái approval hoặc source/FE/APP.
- Kiểm tra tài liệu và verify --quick chạy sau chỉnh cuối, kết quả thật được báo tại bàn giao và target/verify.log; không thay full/migration/E2E.

## Spec011 — fix I2/U3/I3, 06/11/2026

- User yêu cầu fix báo cáo analyze; user cho phép riêng đúng helper chỉ đọc AccountService.findActiveUserIds(Collection<Long>) và implementation/test, không nghiệp vụ/HTTP/permission tài khoản mới. Đã ghi ngoại lệ vào spec/domain/rule54/plan/research/ADR/contracts/tasks; chưa thêm method source.
- I2 sửa bằng hai migration expand/contract, không deploy trung gian: T006/T007 giữ legacy+REVIEW/compatible constraints/callers và exhaustive switch để full/snapshot T014 đạt; T027/T028/T030..T032/T045 chuyển source/tests; T047 atomic enum cleanup + contract SQL remap/siết CHECK, T051 full cuối. Mục tiêu phát hành vẫn đúng3trạngthái, không bỏ/skip gate. Số migration cấp lúc implement, không sửa file đã commit.
- U3 có signature/query/input/output/empty/missing/inactive/test/batching rõ, chỉ lookupIDs qua service; không bypass repository từ edu hay giả actor. I3 thống nhất nguồn in review.printSnapshot, completionPolicy chỉ policy metadata. Giữ58ID/task chưa thực hiện; plan/ADR vẫn draft/PROPOSED, chưa có approval implementation.
- Chỉ sửa artifact; chưa source/migration/OpenAPI/FE/APP, chưa test chức năng mới. Review và cổng tài liệu sau chỉnh cuối được báo trong bàn giao; không dùng quick để tuyên bố full L/migration/E2E đạt. Kết quả verify thật ở target/verify.log.

- Review độc lập read-only sau fix: không còn finding chặn trong phạm vi I2/U3/I3; không thay analyze toàn bộ hoặc test runtime. Cổng đã chạy: check-ai-layer 0 lỗi/12 cảnh báo cũ; verify --quick PASS, tests14/fail0/err0/skip0, archunit/spotless ok, snapshot không chạy. Sau cập nhật log, chạy lại hai cổng; output cuối ở bàn giao và target/verify.log.

## Spec011 — user remediation sau analyze, 06/11/2026 (lịch sử trước ngoại lệ helper; đã thay bằng mục fix phía trên)

- User chốt A1: người soạn không sửa khi đang duyệt; đúng ba trạng thái Nháp/Duyệt/Ban hành, Duyệt=đang duyệt và cuối tự Ban hành. User chốt U2: lưu ô ký/vai trò/tên riêng lúc ban hành, config/tên mới không đổi bản cũ. Giữ phạm vi implementation chỉ giáo dục, không source module khác.
- Spec18FR/9SC, domain/plan/data-model/contracts/tasks/rule54/module card/ADR/cross-repo đã cập nhật. U1 xác định riêng REVIEW và DRAFT-held-leases mất quyền; U2 có JSON print_snapshot/review.printSnapshot, tách completion_policy. Target enums year/class DRAFT/REVIEW/APPROVED, role tới lượt là metadata, EDITING không là business state, return để sửa ánh xạ về DRAFT giữ lý do.
- TaskT009 không tự sửa AccountService hoặc organization, giữ dependency gate vì service hiện tại chưa expose active account IDs; không bypass repository/impersonate. T048 chỉ kiểm producer edu: published_by/completed_at actual publisher, issued_by legacy actual VP approval hoặc null, APPROVED giữ để consumer hiện hữu đọc. Chưa triển khai phương án này, cần review/analyze lại; không lấy kết luận sạch/coverage cũ áp cho delta.
- Review read-only delta đã chỉ ra cần nêu riêng EduClassDocStatus trong T007 và transition class trong T028/T031; đã cập nhật task, giữ58ID/checkbox chưa chạy. Không code/migration/OpenAPI/FE/APP hoặc test chức năng mới trong lượt này. Plan/ADR chưa approved, chưa đánh task implementation hoàn thành.
- Cổng tài liệu chạy sau sửa cuối: check-ai-layer và verify --quick, output thật ở bàn giao/target/verify.log. Không thay cho full/DB/E2E hoặc speckit-analyze toàn bộ delta; ACTIVE-account dependency vẫn mở.

## Spec011 — tasks generation, 06/11/2026

- User gọi speckit-tasks, đã chạy setup-tasks.ps1 -Json đúng feature pointer011 và dùng template chuẩn. Tạo tasks.md với58task chưa checked: setup3/foundation11/US1 12/US2 14/US3 12/polish6;11task P trong4wave file riêng. Mỗi task có ID/label/path và dependency/FR/SC/AC/independent test; generated tasks không đồng nghĩa đã implement.
- Tự kiểm checklist regex/ID liên tiếp T001..T058, story label theo phase, reference dependency tồn tại; 15FR/8SC/14AC plan đều có trace. Plan/ADR giữ draft/PROPOSED, không sửa source/migration/OpenAPI/FE/APP; không coi tạo tasks là user duyệt code. Chưa chạy speckit-analyze hoặc thực hiện bất kỳ task nào.
- Data model/task làm rõ startup activation với readiness/idempotence, không endpoint/dependency/flag mới. Source bindings mock/facade/cleanup đã đối chiếu file hiện có. Không có extensions.yml trước/sau tasks nên không hook cần chạy.
- Sau chỉnh tài liệu cuối chạy check-ai-layer và verify --quick; output thật ở bàn giao/target/verify.log. Đây là cổng delta tài liệu; chưa full/migration/chức năng mới/E2E.

## Spec011 — planning/design, 06/11/2026

- User trả lời bốn điểm hoãn: policy non-empty/distinct; vòng mới sau sửa/gửi lại/version; legacy creator baseline/công bố giới hạn; seed năm TL→VP, lớp GV→TL→VP. Spec/domain/rule EDU-54 đã cập nhật; giữ PROPOSED đúng nguồn user, không tự nâng thành team/trường chốt. Checklist 15/16 → 16/16; không còn câu hỏi nghiệp vụ chặn thiết kế.
- Tạo plan.md/research.md/data-model.md/contracts/workflow-api.md/contracts/client-impact.md/quickstart.md, ADR-0024 PROPOSED và plan quản trị draft theo branch hiện tại; liên kết delta vào Progress log plan năm. Helper trả feature slug, không tạo nhánh; branch thực `feature/G94-226-BE-FEAT-09-school-year-goals`.
- Hai lượt review thiết kế BE/consumer: vòng 1 có 2 P1/2 P2, đã sửa class expected member/root version, evaluator skip-before-approved-final, precedence theo loại, read isolation/lease jobs; đồng thời chính xác shape metadata/error descriptions và printable legacy snapshots. Vòng 2 không còn finding chặn trong phạm vi tài liệu đã review.
- Không sửa source ứng dụng, migrations, snapshot OpenAPI, FE/APP; chưa tạo tasks.md hoặc chạy analyze/implement. Thiết kế thừa nhận ảnh hưởng Web/API, APP source không có cục bộ. Chưa migration/backfill/runtime/full/E2E, không dùng cổng tài liệu để tuyên bố chức năng mới chạy được.
- Sau lần sửa tài liệu cuối chạy `node scripts/check-ai-layer.mjs` và `node scripts/verify.mjs --quick`; kết quả thực tế ở bàn giao và target/verify.log. Không có extensions.yml trước/sau plan nên không hook để chạy. Plan/ADR chưa được approve.

## Spec011 — clarify, 06/11/2026

- 5/5 câu đã trả lời; có retry giải thích câu 4, không tính thêm câu. User bổ sung chỉ Hiệu trưởng cấu hình người tạo/duyệt của giáo dục, Phó HT không được cấu hình phần này. Các quyết định đã lưu vào spec/domain-spec, EDU-54, module card và cross-repo map; chưa sửa source ứng dụng/FE/APP.
- Chốt tách soạn/duyệt theo loại; bản đang chờ theo ngay chuỗi mới, giữ phần đã duyệt; mọi bước chuỗi mới đều đã duyệt thì hoàn tất theo kết quả cũ. Thiếu người ở bước cuối chưa duyệt vẫn giữ chờ. Không tạo lịch sử duyệt giả.
- Checklist trước clarify 16/16; khi phát hiện mơ hồ còn 13/16; sau 5 câu là 15/16. Hai mục mới đạt so với trạng thái giữa phiên: yêu cầu rõ/kiểm được và FR có AC; so đầu phiên một mục hồi quy là đầy đủ mọi acceptance scenarios. Còn thiếu ca/chính sách cho dữ liệu cũ, bản sửa sau trả lại/phiên bản mới và cấu hình rỗng; hoãn do hết 5 câu, phải khảo sát/trình quyết định trong plan trước implement. Không đánh dấu toàn bộ checklist đạt.
- Coverage: scope/hành vi, tương tác, quyền/từ vựng đã làm rõ; mô hình nghiệp vụ và tiêu chí hoàn tất rõ cho các quyết định đã chốt. Thiết kế chuyển tiếp dữ liệu, tích hợp Web/App, hiệu năng/khả dụng và ca biên nêu trên hoãn sang plan. Không có extensions.yml ở lượt kiểm cuối nên không có hook after_clarify.
- Cổng sau chỉnh tài liệu cuối chạy trong lượt này: `node scripts/check-ai-layer.mjs` và `node scripts/verify.mjs --quick`; kết quả thực tế ở bàn giao và `target/verify.log`. Không dùng quick để tuyên bố chức năng mới đã hoạt động; chưa full/E2E, chưa implement.

## Spec011 — người tạo/duyệt, 06/11/2026

- Chỉ đặc tả: domain spec 14 mục, spec, checklist, feature pointer và tri thức EDU-54; không sửa ứng dụng/FE/APP. Constitution 1.0.0 được tái sử dụng; không có extensions.yml nên không có pre/post hook.
- User đã chọn theo vai trò, áp dụng năm/lớp, cấm tự duyệt; quyền tạo gồm soạn/sửa/gửi đúng phạm vi; bước thiếu người được bỏ qua khi còn bước sau, bước cuối giữ chờ. Checklist vòng 1: 12/16; vòng 2 sau đáp án: 16/16, sẵn sàng plan, chưa triển khai. Nguồn: [feature011](../../specs/011-plan-creator-reviewer/spec.md).
- Kiểm chứng vòng 1: `node scripts/check-ai-layer.mjs` → 0 lỗi, 12 cảnh báo có sẵn; `node scripts/verify.mjs --quick` → 14 test, 0 fail/error/skip, ArchUnit/Spotless đạt, snapshot không chạy, 32s. Sau bổ sung bằng chứng này chạy lại cổng; kết quả cuối báo trong bàn giao và `target/verify.log`. Đây là kiểm tra cho delta tài liệu, không phải test chức năng tạo/duyệt mới; chưa chạy full/E2E và chưa triển khai.

## Tự đóng đợt khám và mở lại với hạn nhập bù — 2026-11-01

- Nguồn user 2026-11-01 (HLT-17): EXAM tự đóng khi qua ngày kết thúc theo giờ trường, chạy lúc 00:00 và khi ứng dụng khởi động để xử lý thời gian tắt máy. Tái sử dụng đóng tay cho vắng/chưa kết luận/khóa kết quả; phiếu theo dõi vẫn mở; lịch sử đóng tự động ghi actor hệ thống. Kỳ cân đo giữ nguyên.
- User chốt đợt mở lại vẫn tự đóng: request reopen nhận thêm ngày `endDate`; đợt khám đã hết hạn phải chọn ngày mới từ hôm nay. Web đã thêm ô ngày; request cũ chỉ có `reason` vẫn dùng với đợt còn hạn/kỳ cân đo; không đổi endpoint hoặc response. Snapshot OpenAPI chỉ thêm `endDate: string/date` trong `HealthCheckReopenRequest`.
- Flyway V67 nới actor đóng/historical user cho hệ thống, không sửa migration cũ; PostgreSQL Testcontainers đã chạy migration và kiểm closure, reopen, lịch sử, kết quả và phiếu theo dõi thật. 25/25 test health + snapshot đạt, không skip. Snapshot cập nhật có chủ đích và kiểm diff 3 dòng; cổng full sau đó ghi kết quả trong `target/verify.log` và output bàn giao.
- Chrome trên Web mock đã kiểm tự đóng, mở lại với ngày mới `dd/mm/yyyy`, validation và tự đóng lần hai. Chưa chạy UI nối API thật. Không commit/push.
## Delta006 EDU-48 và ellipsis — 02/11/2026

- User chốt xóa cả nhánh và select một dòng, sau đó duyệt plan L bằng “Duyệt kế hoạch và triển khai tới khi sạch”. Domain14/spec/clarify/plan/research/data-model/contracts/tasks giữ lịch sửT001..T011, thêmT012..T022; constitution1.0.0 không đổi. Analyze/review không blocker, prefix lịch sử4artifact/14mục/22task unique đã kiểm trước code; sự cố sửa trạng thái docs được phục hồi và ghi ENV-261102-powershell-replacement-pairs.
- Delta chỉ production FE và hồ sơ BE; không source/API/DB/dependency/APP backend. FE11unit mới/44targeted/full386fail0skip0 và browser4viewport đạt. Real-BE API-only E2E1/1(15.7s) xác nhận/Quay lại/xóa/lưu/mở lại/ID-content-theme-break preservation/ellipsis; cleanup ownedmarkers0, không SQL. Xem [FE evidence](../../../CareNest_FE/docs/quality/VERIFICATION.md) và [quickstart006](../../specs/006-year-plan-goal-hierarchy/quickstart.md).
- BE verify --quick trong lượt:14tests fail0err0skip0, archunit/spotlessok, snapshot không chạy; không dùng quick để nhận lại toàn bộ migration/contract. Cổng cuối: check-ai-layer hai repo và verify theo scope sau hồ sơ; output bàn giao/target/verify.log.
- Converge chỉ đánh giá deltaFR009..015/SC005..008, không lấy completionDB29/10 thay evidence mới; kết quả/hash ở quickstart006. Không commit/push/deploy hoặc sửa dữ liệu thật, giữ .tmp_doc_edit ngoài phạm vi.

## Hồ sơ sửa LVC và giao diện chọn từ Bộ — 02/11/2026

- User cho commit/push bản fix trước rồi triển khai UI/commit/push FE và BE. Fix FE994002c đã push; RED4ca ban đầu và1ca nguồn hỗn hợp → GREEN39targeted, FEfull375/375fail0/skip0. BE chỉ đồng bộ incident/index và bằng chứng; không thay source backend/API/DB/dependency/APP.
- UI dựa ảnh thứ hai đã duyệt, chỉ trình bày chức năng hiện có EDU-24/28: chọn tất cả mục tiêu đang hiển thị, đếm, tìm/lọc, phân cấp, xem nội dung đi kèm; thẻ nội dung chọn đích/lọc/tick từng dòng, nhắc lưu tay. Xem [bằng chứng FE](../../../CareNest_FE/docs/quality/VERIFICATION.md). Không coi đề xuất đầu hoặc ảnh AI là feature đã code.
- Browser FE source/CSS thật với hook/dữ liệu/callback giả đạt tại360/390/768/1440,0lỗiJS; bước ghép dùng helper thật. Chưa kiểm persistence/API với BE thật. Cổng BE cuối cho delta tài liệu: check-ai-layer và verify --quick, kết quả xem output bàn giao/target/verify.log; không dùng quick để tuyên bố toàn bộ test API đã chạy.

## Pre-PR monthly roster fixes — 02/11/2026

- User approved four findings from FE pre-PR report. Two monthly regressions RED against previous source → GREEN, covering first listing after transfer, leavers/joiners, name/history, repeated listing and teacher/VP scopes. MONTH uses period overlap; YEAR retains current roster.
- After final source edits: `VERIFY PASS full | tests 731 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 427s | log target/verify.log`. DailyAssessmentIntegrationTest: 11/11. FE: full 487 tests and Chrome demo smoke 9/9. No real browser-to-BE/mobile E2E in this follow-up. No commit/push/PR or local database reset.

## Feature009 — phase backend nhập đầy đủ kế hoạch năm (02/11/2026)

- User duyệt plan/ba quyết định nghiệp vụ và bổ sung phần1dạngbảng; [spec/phase evidence](../../specs/009-full-year-plan-file-import/quickstart.md). Preview additive metadata/themes/breaks; không đổi request/endpoint/schema lưu, dependency hoặc migration.
- Source/test cuối: `VERIFY PASS full | tests 659 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 278s | log target/verify.log`; log lưu target/feature009-final-full.log. Snapshot53dòng chỉ thêm3schema/3field preview, giữ schema Theme/Break request bằng tên mới riêng. Review cuối không còn lỗi chặn.
- RED/GREEN domain4ca và preview/boundary/ranges, full cuối bao phủ11test full-preview mới +4domainvariants; sample cục bộ5lĩnh vực83MT203nội dung389ticks/10chủđề đúngngày+mã/2đợt nghỉ/metadata/0warning. Oracle độc lập cho sốMT/ngày/mã; không coi countscontent/ticks là oracle độc lập. Không copy file gốc/người ký.
- Chưa phaseWeb: preview/merge/chặn tuổi/dời năm trên UI, hàng nút/phần1dạngbảng/browser/real-BE E2E/save-reopen/converge; không gọi toàn feature xong. Không commit/push/deploy. Cổng AI-layer và verify hồ sơ cuối chạy sau ghi bằng chứng; output ở bàn giao.

## Feature008 hoàn tất — bàn giao 02/11/2026

- 19/19 task, 11 FR/5 SC được kiểm; implement/converge vòng1 append T019 sửa giới hạn lưới, vòng2 sạch, không mất lịch sử task. [Progress log](../plans/completed/2026-11-01-branch-plan-file-import.md), [bằng chứng/lượt thử/giới hạn](../../specs/008-branch-plan-file-import/quickstart.md).
- BE sau source cuối: `VERIFY PASS full | tests 644 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 248s | log target/verify.log`. FE full verify342 tests, 0 fail/skip, lint/format/design/build đạt.
- Real-BE jar cuối: branch3/3 (gồm mobile375, overwrite, lịch hệ thống, chuỗi duyệt, quyền và lỗi); goal/import17/17 (gồm .doc bước2). Hồi quy edu-flow19/19 đạt lượt trước decoderguard; đây là39 kịch bản có kết quả đạt qua nhiều lượt, không phải một lượt39/39 xanh.
- File mẫu gốc đọc được35 tên,0cảnh báo parser; chỉ probe cục bộ, không copy file thật/người ký vào repo. Fixtures đều tổng hợp. Chốt3 incident fixed về guard/parser mock và E2E ID domain; review cuối không P1/P2.
- Không migration/commit/push/deploy. E2E dùng runtime/DB riêng đã dọn; BE8080/Keycloak8180 đang dùng giữ nguyên. Mock vẫn chỉ nhập CSV; Word/Excel dùng BE thật.

## Feature008 phase 1 — Word .doc mục tiêu, 01/11/2026

- User duyệt plan và bắt đầu phase 1; T001–T006 done, còn phase 2/3. [Progress log](../plans/completed/2026-11-01-branch-plan-file-import.md), [chi tiết RED/GREEN và giới hạn](../../specs/008-branch-plan-file-import/quickstart.md).
- Sau sửa source cuối: `node scripts/verify.mjs` → `VERIFY PASS full | tests 633 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 260s | log target/verify.log`; OpenAPI diff exit 0. Targeted parser/service: 13 test, 0 fail/error/skip; .doc ô gộp tương đương .docx, malformed/encrypted/no-table/limits, quyền và privacy fixture.
- FE full verify đạt sau định dạng câu lỗi mock; check-ai-layer BE 0 lỗi/12 cảnh báo cũ, FE 0 lỗi/14 cảnh báo cũ. Review độc lập không có P1/P2 chặn.
- Chưa browser E2E, chưa hỗ trợ .doc bằng mock parser Web (CSV như cũ), chưa API nhánh/hai nút luôn hiện; không commit/push. Dừng review phase 1 theo plan, không đánh dấu toàn feature hoàn tất.

Auth React Phase2 (2026-10-12): `VERIFY PASS full | tests 196 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`. Extension38 tests/0 skip; real-container provider E2E26/26. Phạm vi, RED/GREEN, review và các phần chưa kiểm chứng: báo cáo Phase2 (chưa có trong repo).

## Iron Law
Không nói "xong / pass / đã sửa / API không đổi / migration OK" khi chưa có **output lệnh chạy trong lượt này, sau lần sửa cuối**. Kết quả lượt trước, suy luận, "chắc chạy được" không phải bằng chứng. **Skip (Docker tắt) hoặc chưa chạy = chưa kiểm chứng** — báo đúng như vậy (`Verify: chưa chạy — <lý do>`).

Lệnh chuẩn: `node scripts/verify.mjs` (full = `mvnw verify`: test + ArchUnit + snapshot OpenAPI + Spotless) hoặc `--quick` (test liên quan file đổi + ArchUnit + Spotless). Output ≤10 dòng; log đầy đủ ở `target/verify.log` — chỉ grep khi cần, không đọc cả file.

## Bằng chứng theo làn
| Làn | Lệnh | Bằng chứng tối thiểu |
| --- | --- | --- |
| S | `node scripts/verify.mjs --quick` | Dòng `VERIFY PASS quick …` |
| M | `node scripts/verify.mjs` | `VERIFY PASS full …`, `skip 0` |
| L | như M + bước kiểm trong plan | `VERIFY PASS full`, skip 0 + diff snapshot (nếu đổi API) + e2e `x/y` |
| Bug fix (mọi làn) | regression test | Output **fail trước fix** (khớp triệu chứng) + **pass sau fix**; tên test ghi vào incident (nếu có) |

## Claim → bằng chứng
| Claim | Bằng chứng |
| --- | --- |
| API không đổi | Snapshot pass (không skip) + `git diff --exit-code docs/api/openapi.yaml` |
| API đổi có chủ đích | `mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true` + diff trong báo cáo + consumer FE/APP cụ thể |
| Migration OK | Test Testcontainers chạy thật (skip 0) — Flyway + `ddl-auto: validate` |
| Kiến trúc đúng | `ArchitectureRulesTest` pass |
| Format | Spotless pass (`mvnw.cmd spotless:apply` để sửa) |
| AI layer nhất quán | `node scripts/check-ai-layer.mjs` exit 0 |

## Gate fail
Sửa nguyên nhân. Không xóa/`@Disabled`/skip test, không nới rule ArchUnit, không `--no-verify`, không regenerate snapshot cho qua. Rule sai thật ⇒ đề xuất sửa rule + lý do, hỏi user. Snapshot đổi ngoài ý muốn = bug contract.

## Báo cáo cuối task
```text
Làn S (≤3 dòng):  Đã làm: … · Verify: VERIFY PASS quick (12 tests) · Memory: <chỉ khi có trigger>
Làn M (≤8 dòng):  Đã làm · Rule/AC · Verify (dòng VERIFY) · Chưa kiểm chứng: … · Ảnh hưởng còn chờ · Memory: …
Làn L:            ghi đủ vào Progress log của plan; chat chỉ tóm tắt + link plan
```

## Lấy mục tiêu từ kế hoạch năm có sẵn — 28/10/2026

- Feature003: 22/22 tasks, converge không còn gap; giữ lịch sử task. Chỉ Web source thay đổi, Java/OpenAPI không sửa.
- FE gate: VERIFY PASS full | npm run verify | log verify.log; education-plan 59/59. BE gate: VERIFY PASS full | tests 410 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 153s.
- Playwright feature003 trên BE/Keycloak thật: 13 passed (1.0m), zero fail/skip sau hardening fixture. Đúng DB runtime qua E2E_PG_DB; cleanup 0 bản import còn sót. Các fault injection được ghi riêng, không giả thành lỗi phát sinh thật ở server.
- Scope/command/failed attempts/giới hạn: [quickstart](../../specs/003-import-year-plan-goals/quickstart.md); plan hoàn tất tại docs/plans/completed/2026-10-28-import-year-plan-goals.md.

## Ba bảng mục tiêu cha–con — 29/10/2026
- Feature006 / phần DB-1 plan mục tiêu năm đã được user duyệt; thực hiện bằng vòng lặp Spec Kit, 11/11 tasks done, converge không còn gap, review độc lập PASS.
- `node scripts/verify.mjs`: `VERIFY PASS full | tests 433 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 247s | log target/verify.log`.
- PostgreSQL thật: 5/5 test V34→V35, gồm thiếu cấp cha, nhiều lĩnh vực/phiên bản, ID/lineage/nội dung/dấu tích, shared sequence và dữ liệu bất hợp lệ rollback.
- CRUD/copy regression: chuyển cha rồi xóa tiêu đề cũ giữ nội dung/dấu tích; remap parent ID/lineage trên phiên bản mới; ID sai plan/type/lặp bị từ chối. Class-doc/học liệu và snapshot đạt trong full gate.
- `git diff --exit-code -- docs/api/openapi.yaml`: exit 0. `node scripts/check-ai-layer.mjs`: 0 lỗi, 0 cảnh báo; E6 bỏ qua theo output công cụ.
- Lỗi migration deferred DDL có RED/GREEN tại BUG-261029-hierarchy-deferred-ddl. Chi tiết: [quickstart feature006](../../specs/006-year-plan-goal-hierarchy/quickstart.md).
- Chưa kiểm chứng: migration trên DB dev/production đang dùng, browser E2E Web. Không chạy migration DB thật trong task; không commit/push/tạo nhánh.
