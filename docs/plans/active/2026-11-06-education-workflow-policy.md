---
title: Cấu hình người tạo/duyệt kế hoạch giáo dục
status: in-progress
owner: Nguyễn Quang Tiền
jira: G94-226 / BE-FEAT-09
branch: feature/G94-226-BE-FEAT-09-school-year-goals
modules: [education-plan]
rules: [EDU-54, EDU-06, EDU-17, EDU-21, EDU-22, EDU-27, EDU-30, EDU-33, EDU-51, EDU-52, EDU-53, AUTH-20, AUTH-23]
risk: high
created: 2026-11-06
updated: 2026-11-06
---
# Cấu hình người tạo/duyệt — plan làn L

## Context
Config hiện cho HT/PHT sửa (`EduConfigController.java:40`, `EduConfigServiceImpl.java:82`); năm duyệt VP cố định, class ưu tiên snapshot (`EduClassDocServiceImpl.java:897`). Đường dẫn đầy đủ/evidence tại [research](../../../specs/011-plan-creator-reviewer/research.md). Đây là delta có gate riêng; approval plan năm cũ không duyệt delta này. Jira/branch lấy từ nhánh hiện có, chưa xác nhận task Jira riêng cho delta, không có lệnh commit/PR.

## Quyết định đã chốt
Nguồn user trong chat 06/11/2026; không đồng nghĩa trường/team đã xác nhận EDU-54.
1. Chỉ giáo dục, chỉ Hiệu trưởng hiệu lực cấu hình; PHT không được cấu hình phần người tạo/duyệt.
2. Theo role và loại hồ sơ; soạn gồm tạo/soạn/sửa/gửi đúng scope. Reviewer không soạn cùng loại dù nhiều role; các nhóm không giao nhau.
3. Pending theo ngay chuỗi mới; giữ phần đã duyệt, không duyệt lại. Mọi bước chuỗi mới đã duyệt thì hoàn tất, không fake human approval. Completed không mở lại.
4. Bước chưa duyệt thiếu người được bỏ nếu còn bước sau; bước cuối chưa duyệt thiếu người giữ chờ.
5. Policy non-empty/distinct; sửa/gửi lại sau trả lại hoặc version mới duyệt từ đầu. Legacy creator baseline và theo dõi coauthor từ nâng cấp, công khai giới hạn.
6. Seed: năm TL soạn→VP duyệt; branch/theme GV soạn→TL→VP duyệt; HT được đổi sau.

## Làm rõ nghiệp vụ
- Actor: HT quản lý; tác giả soạn/gửi; reviewer đúng lượt. Outcome: cấu hình có hiệu lực thật, không chỉ đổi nhãn.
- Rule: EDU-54 PROPOSED ghi các quyết định user; EDU-17/21/22/27/30/33/51/52/53, AUTH-20/23 hiện hành là ràng buộc đã xác nhận. EDU-02/04/05/06/08/11/18 cần delta được duyệt trước code.
- Flow: [B] HT xác định trách nhiệm → [S] lưu policy/version + reconcile → [S] author soạn/gửi → [S] reviewer độc lập duyệt đúng bước.
- Ảnh hưởng: eduplan + batch đọc account/assignment qua service; chỉ sửa producer giáo dục và helper account chỉ đọc được phép tại T009, không source module khác; Web routes/config/queue/mock/print; APP source chưa có. [Consumer cụ thể](../../../specs/011-plan-creator-reviewer/contracts/client-impact.md).
- Dữ liệu: [mô hình/legacy/round](../../../specs/011-plan-creator-reviewer/data-model.md); không sửa Flyway đã commit, không suy coauthor từ updatedBy.

### Câu hỏi mở
- [x] Non-empty/distinct — user chọn bắt buộc 06/11.
- [x] Gửi lại/version mới — user chọn duyệt từ đầu 06/11.
- [x] Legacy provenance — user chấp nhận creator baseline/giới hạn 06/11.
- [x] Seed/activation — user chọn TL→VP và GV→TL→VP 06/11.
User đã chốt các câu nghiệp vụ; U3 đã có ngoại lệ user cho phép riêng helper ACTIVE-ID chỉ đọc tại T009, không mở thêm interface khác. Cổng trước implementation: user duyệt plan/contract/migration, ghi nhận trạng thái quyết định/ADR đúng nguồn; FE/APP write và deploy chưa được cấp phép. Không tự set approved.

## Acceptance criteria
Thiết kế cho EDU-54 còn PROPOSED, chưa tuyên bố yêu cầu đã được team/trường chốt; AC chuẩn và ca test ở [quickstart](../../../specs/011-plan-creator-reviewer/quickstart.md).
- AC-01..03: HT-only, cấu hình hợp lệ/disjoint, role hiệu lực + scope + reviewer-precedence.
- AC-04..05: year nhiều bước, dossier tuần/ngày cùng hồ sơ; union authors, reviewer không lấy lease soạn.
- AC-06..09: chuỗi mới giữ approvals cùng round, thiếu người, round mới khi sửa, migration legacy có coverage.
- AC-10..14: concurrency/revision, publish validation, print/observer, legacy write bypass, manual save/adjustments.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Policy | Local edu policy/revision | Ma trận global | User giới hạn chỉ giáo dục và chuỗi theo loại |
| Storage | Fields nhỏ + contributor ledger FK | Workflow instance/steps platform | Ba loại hồ sơ, không mở rộng V1 |
| Config API | Endpoint riêng + guard legacy | HT-only mọi field config | Không vô tình đổi quyền PHT với các phần khác |
| Consistency | Atomic reconcile/settings lock | Snapshot cũ/job async | User muốn pending theo ngay cấu hình mới |
[ADR-0024 PROPOSED](../../decisions/ADR-0024-education-workflow-policy.md); [API đề xuất](../../../specs/011-plan-creator-reviewer/contracts/workflow-api.md).

## Phases
### Phase 1 — Schema/policy/provenance
- C: migration expand mới, EduWorkflowPolicy/EduWorkflowService(Impl), EduDocumentAuthor/repository, tests; M: settings/year/class entity/repos, candidate lookup qua service; user cho phép đúng helper AccountService.findActiveUserIds và test, không feature tài khoản khác. Tên/số migration chốt lúc implement, hiện repo có V79.
- Steps: RED test evaluator/expand/concurrency → schema/model/ledger tương thích legacy+REVIEW (chưa bỏ enums cũ) → batch candidates/locks/helper được phép → GREEN. T014 review snapshot expand và full verify, P1 chưa cutover. Không expose endpoint mới hoặc rollout source nửa chừng.
- Tests: AC-02/03/05/07/08/09/10 → `EduWorkflowPolicyTest`, `EduWorkflowServiceIntegrationTest` (tên dự kiến, DisplayName EDU-54).
- Exit: `node scripts/verify.mjs` → dự kiến full PASS, skip 0; synthetic backfill/count/FK/lock evidence.
- **Dừng chờ user review.**

### Phase 2 — Backend integration/contract
- M: EduConfig/YearPlan/ClassDoc controllers+services, DTO/mapper/status/policy, import/media/adjustment entry checks, ApiCode descriptions/snapshot, publisher/print snapshot compatibility trong giáo dục. C: workflow controller/DTO và targeted tests khi cần.
- Steps: RED principal-only/bypass/multi-role/reconcile → integrate evaluator/author ledger/validator → year/class/lease/round/source/test chuyển đổi → T047 atomic cutover bỏ legacy enums + migration contract remap/siết CHECK → metadata/config/print → T051 full/snapshot/review.
- Tests: AC-01/04/06/10/11/12/13/14 → EduConfigServiceIntegrationTest/EduYearPlanServiceIntegrationTest/EduClassDocServiceIntegrationTest/ThemeEvaluationIntegrationTest, API MockMvc/real-BE, DisplayName rule tương ứng.
- Exit: full verify PASS skip 0, OpenAPI diff đúng contract, API E2E permission/round/policy race và producer compatibility giáo dục đạt; không sửa test/source observation. Chưa gọi browser E2E nếu chỉ API.
- **Dừng chờ user review.**

### Phase 3 — Web/release verification (quyền sửa riêng)
- Files FE cụ thể trong client-impact, không tự sửa FE/APP lúc này; đề nghị tách implementation plan client liên kết plan BE vì >8 file.
- Steps: user cho phép FE → cập nhật mapper/capabilities/routes/config/mock/print → browser real-BE/multi-tab → APP source review khi có → release phối hợp theo chỉ thị deploy riêng.
- Tests: AC toàn bộ qua Web thật/mock, responsive/keyboard/manual save và snapshot completed print; APP cần báo thực tế, không suy từ docs.
- Exit: Web verify theo script repo + browser real-BE counts thật, BE full PASS skip 0; không rollout với Web cũ/APP chưa đánh giá nếu có consumer.
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| 01/02/13 | EDU-54 | Config service/API invalid+PHT+legacy bypass | 1/2 | Chưa triển khai/chạy |
| 03/05/07 | EDU-54, AUTH-23 | Evaluator/scoped role/author union | 1/2 | Chưa triển khai/chạy |
| 04/06/08/09/10 | EDU-54, EDU-17 | Year/class/legacy/round/concurrency | 1/2 | Chưa triển khai/chạy |
| 11/12/14 | EDU-21/22/27/30/33/51/52/53 | Publish/producer compatibility/print/save | 2/3 | Chưa triển khai/chạy |

## Rủi ro & rollback
| Rủi ro | Giảm thiểu | Rollback |
| --- | --- | --- |
| Legacy thiếu coauthor | Baseline đã user chọn, không đoán danh tính, coverage hiển thị | Giữ ledger/history, không xóa để che giới hạn |
| Race/deadlock config với approve | Shared/exclusive policy lock, thứ tự year/class IDs, full integration race tests | Không bỏ lock; không rollout khi test chưa đạt |
| Auto-publish thiếu dữ liệu hoặc gán nhầm HT là issuer/PHT | Shared publish validator, actual approval/completionMode, print adapter | Policy save rollback toàn transaction |
| Enum/contract mới với Web cũ | Release BE+Web phối hợp, review APP thật | Backup DB+app cùng mốc trước activation; không chạy app cũ trên status mới |

## Validation log
- [x] Khẳng định source có file:line trong research/client-impact; không đọc dữ liệu thật.
- [x] Contract có consumers cụ thể và legacy bypass; không thêm dependency/global permission.
- [x] Mỗi AC có test/phase và Exit; chưa đánh dấu test PASS khi chưa implement.
- [x] >8 file đã đề xuất ba slice + plan client riêng, giữ gate từng phase.
- [x] Không dùng EDU-54 PROPOSED như team/trường đã chốt; approval gate nêu rõ.
- [x] Review độc lập BE và consumer hoàn tất sau sửa: expected class member/root versions, evaluator skip/final-approved, strict per-type precedence, GET snapshot/lease jobs, error text và print snapshot. Không còn finding chặn trong phạm vi thiết kế đã review; chưa có bằng chứng runtime.

## Progress log
### 2026-11-06 — Draft planning, chưa commit
- Đã đọc Progress log cuối plan năm 22/10; delta011 không kế thừa approval cũ. User trả lời bốn điểm hoãn; artifacts tại feature011.
- Chỉ tạo/sửa tài liệu; chưa chạy migration, chưa đổi code/API thật/FE/APP. Cổng tài liệu và quick chạy sau chỉnh cuối, output ở VERIFICATION/bàn giao.
- Tiếp: user review plan/contract, sau approval mới tasks/analyze; chưa tự triển khai hoặc deploy.

- Review vòng 1 phát hiện 2 P1/2 P2 ở thiết kế, đã sửa trong artifact; vòng 2 BE và consumer không còn finding chặn. Không coi review thiết kế là test hoặc analyze implementation. Kết thúc lệnh plan sau Phase 1 design, chưa có tasks.md.

### 2026-11-06 — Tasks generated, status vẫn draft
- User gọi speckit-tasks, tạo [tasks.md](../../../specs/011-plan-creator-reviewer/tasks.md):58task (US1:12, US2:14, US3:12; setup/foundation/polish:20),11task P trong4wave khác file. Ghi dependency, FR/SC/AC, independent tests, map delivery slices và quyền FE riêng. Không chạy task hoặc tự approve plan/ADR.
- Bổ sung rõ đường activation startup/readiness/idempotent trong data model và taskT046, không thêm dependency/endpoint/flag; chờ analyze kiểm chéo trước source. Cổng verify tài liệu chạy sau sửa cuối; tiếp theo user có thể gọi speckit-analyze.

### 2026-11-06 — Delta sau analyze theo user, chưa approved
- A1: chỉ Nháp/Duyệt/Ban hành (DRAFT/REVIEW/APPROVED), REVIEW không soạn/sửa/lease, final approve tự Ban hành; return để sửa về DRAFT giữ lý do/history, lease là metadata. U1: đối soát REVIEW và thu hồi DRAFT-held-leases mất quyền cùng transaction. U2: user đồng ý snapshot ô ký/name/role theo hồ sơ; schema print_snapshot/review.printSnapshot đã chốt trong data-model §9.
- User giới hạn chỉ giáo dục: bỏ sửa account/organization/observation source, taskT009 trước đây là gate ACTIVE-ID, nay user cho phép đúng helper đọc tối thiểu; taskT048 chỉ compatibility producer trong edu. published_by/completed_at là publisher thật, issued_by legacy chỉ actual VP approval hoặc null, final status APPROVED không đổi để consumer đọc cũ tiếp tục dùng. Delta cần review/analyze lại; các review/count cũ chỉ thuộc phiên trước, không tự áp dụng delta.

### 2026-11-06 — Fix I2/U3/I3, vẫn draft
- User yêu cầu fix và cho phép ngoại lệ đúng AccountService.findActiveUserIds chỉ đọc + implementation/test; không API/quyền/nghiệp vụ tài khoản mới, không organization/observation source. U3 có interface/input/output/query/test cụ thể, không còn phương án chưa chọn.
- I2: T006 expand, T007 giữ legacy+REVIEW và switch tương thích; T014 full/snapshot expand thật. Source/test chuyển đổi T027/T028/T030..T032/T045, T047 thêm contract migration và bỏ enum cũ cùng cutover; T051 full/snapshot cuối. Không rollout P1, final chỉ ba trạng thái, không skip gate. I3 chỉ in từ review.printSnapshot. Cần review lại remediation, chưa thực hiện task/migration/source.

- Review độc lập read-only remediation I2/U3/I3: không còn finding chặn trong phạm vi này; xác nhận expand/legacy caller/full gate rồi final cutover, helper exception và printSnapshot nhất quán. Đây là review thiết kế/task, chưa chứng minh runtime/migration. Kiểm tra tài liệu: 0 lỗi/12 cảnh báo cũ; quick: 14 test, fail0/err0/skip0; cổng được chạy lại sau cập nhật log cuối.

### 2026-11-06 — Cập nhật tasks sau analyze sạch
- User gọi speckit-tasks lần nữa; đã đối chiếu template/design hiện tại, giữ58ID/task và11dấu P, không chạy implementation hoặc tự approve. Bổ sung hướng dẫn bàn giao phiên khác, xác nhận feature pointer, approval gate và cách tiếp tục task theo dependency; taskT049 giữ nguồn in review.printSnapshot. US1:12, US2:14, US3:12, setup/foundation/polish:20. Cổng tài liệu và quick chạy sau chỉnh cuối, kết quả ở bàn giao/VERIFICATION.

### 2026-11-06 — Bắt đầu implementation P1 theo lệnh user
- User gọi speckit-implement sau analyze sạch: cho phép thực hiện plan/contract/migration đã trình. User cho phép thêm đúng helper StaffAssignmentService.findActiveEducationReviewerAssignments(LocalDate) chỉ đọc + test, không HTTP/quyền/nghiệp vụ tổ chức; ngoại lệ trước đó AccountService.findActiveUserIds giữ nguyên. Không cấp quyền FE/APP/commit/push/deploy. EDU-54/ADR giữ PROPOSED đúng nguồn user, không coi là trường/team chốt.
- Checklist requirements16/16. Branch hiện hữu đúng; dirty baseline chỉ docs/feature011, không reset. Java21/Maven/PostgreSQL17; Docker server28.4.0. V79 hiện cuối: bind V80__expand_edu_workflow_policy.sql; contract dự kiến V81__contract_edu_workflow_status.sql, phải đối chiếu lại trước tạo nếu số đã bị dùng.
- Phát hiện gap đọc assignment: findCampusConfirmerIds chỉ fallback HT khi không PHT, findActiveStaffUserIds chỉ campus/GV; không dùng để enumerate đầy đủ HT. Đã hỏi và user cho phép helper read-only; không đọc repository chéo từ edu. Sửa T009 theo ngoại lệ và ghi research để phiên khác không hỏi lại. P1 không rollout; hết T014 dừng review.

### 2026-11-06 — Hoàn thành Phase 3: User Story 1 (T015..T026), dừng chờ user review
- Thực hiện đầy đủ các task backend của User Story 1 (Cấu hình trách nhiệm theo loại hồ sơ):
  - T015: Controller test & security contract `EduWorkflowControllerTest` (12/12 tests GREEN) cho `GET /education-plans/workflow-policy` và `PUT /education-plans/workflow-policy`:
    - Chỉ Hiệu trưởng (HT) có phân công hiệu lực (`hasActivePrincipalAssignment`) mới có quyền cấu hình (`canConfigure = true`).
    - Phó Hiệu trưởng (PHT) và Giáo viên (GV) nhận `canConfigure = false` ở GET và bị từ chối 403 Forbidden ở PUT.
    - HT hết hạn phân công bị từ chối 403 với errorCode 7201 (`EDU_CONFIG_FORBIDDEN`).
    - Revision cũ (stale) trả 409 Conflict với errorCode 9012 (`CONCURRENT_UPDATE`).
    - Body sai (key lạ như "week", vai trò không trong allowlist, rỗng, hoặc creators và reviewers giao nhau) trả 400 Bad Request với errorCode 7202 (`SIGNERS_INVALID`).
    - Payload no-op (trùng policy hiện tại) trả 200 và giữ nguyên revision, không ghi history.
  - T016: Integration test `EduConfigServiceIntegrationTest` (10/10 tests GREEN) trên Testcontainers PostgreSQL 17:
    - Lưu policy mới cập nhật `workflow_policy`, tăng `workflow_revision`, lưu timestamp và người sửa.
    - `getEduConfig` trả `workflow`, `canConfigure` và `yearCreationScopes` theo đúng `currentUser`.
    - Tự động reconcile các kế hoạch năm đang `REVIEW`: giữ approvals cũ của các vai trò còn trong chuỗi mới; cập nhật `waitingRole`.
    - Hoàn tất tự động (`APPROVED`) khi bước duyệt cuối đã có approval, gán `completionMode = POLICY_CHANGE`.
    - Rollback toàn bộ transaction khi prospective publication validation thất bại (vi phạm điều kiện sẵn sàng ban hành EDU-51/52/53).
    - Chặn mọi caller sửa `signers` qua endpoint cũ `PUT /education-plans/config` (403 `EDU_CONFIG_FORBIDDEN`), nhưng chấp nhận echo signers trùng projection server để sửa các trường khác (domainLabels, extraDomains, weekRows).
  - T018: Tạo DTOs `EduWorkflowPolicyRequest`, `EduWorkflowPolicyResponse` và controller `EduWorkflowController`.
  - T019: Triển khai `savePolicy` và `getPolicy` trong `EduWorkflowServiceImpl` với lock độc quyền `EduSettings`, validate allowlist/disjoint/non-empty, cập nhật revision và projection signers, reconcile pending atomically; chuẩn hóa mô tả ApiCode 7201, 7202, 7204 trung tính, bỏ ép PHT qua 7203.
  - T020: Cập nhật `EduConfigService`, `EduConfigServiceImpl`, `EduConfigController`, `EduConfigResponse` để expose `workflow`, `canConfigure`, `yearCreationScopes` và bảo vệ endpoint cũ.
  - T024: Cập nhật snapshot OpenAPI trong `docs/api/openapi.yaml`, chạy `ControllerSecurityCoverageTest` (4/4 tests GREEN) và `OpenApiSnapshotTest` (3/3 tests GREEN).
  - T025 & T026: Kiểm chứng toàn diện:
    - `node scripts/verify.mjs`: **1,082 / 1,082 tests GREEN** (0 fail, 0 error, 0 skip, archunit ok, snapshot ok, spotless ok).
    - `node scripts/check-ai-layer.mjs`: 0 lỗi, 12 cảnh báo baseline cũ.
    - Định dạng Spotless sạch 100%.
    - Ranh giới FE: Các task FE (T017, T021..T023) giữ nguyên trạng thái chưa thực hiện do user giới hạn phạm vi chỉ backend.
- Dừng đúng Checkpoint Phase 3 (US1) để User review trước khi bắt đầu Phase 4 (US2).
- Thực hiện đầy đủ các foundational primitives T004..T014 theo plan làn L và spec 011:
  - T004: Unit test `EduWorkflowPolicyTest` kiểm tra evaluator thuần với 7 test cases bao phủ đầy đủ các kịch bản roles disjoint, candidate exclusion, skip intermediate missing step, hold final step thiếu candidate, và completion khi final step đã approve.
  - T005 & T006: Viết migration expand `V80__expand_edu_workflow_policy.sql` và integration test `EduWorkflowMigrationIntegrationTest`. Thêm columns policy/revision, round/approvals/print_snapshot/published_by, bảng `edu_document_authors`, nới CHECK status hỗ trợ REVIEW cùng legacy enums. Kiểm thử Testcontainers PostgreSQL 17 GREEN cả 2 mốc legacy và expand.
  - T007: Cập nhật entities `EduSettings`, `EduYearPlan`, `EduClassDoc`, tạo entity `EduDocumentAuthor` và repository `EduDocumentAuthorRepository`; thêm enum `REVIEW` vào `EduPlanStatus` và `EduClassDocStatus`; thêm switch cấm sửa `REVIEW` trong `EduYearPlanServiceImpl`.
  - T008: Triển khai evaluator thuần `EduWorkflowPolicy` đánh giá round, approvals, excludedAuthorIds, candidates, skip bước giữa và hoàn tất bước cuối.
  - T009: Bổ sung helper chỉ đọc `AccountService.findActiveUserIds` (kèm `AccountServiceImplTest`) và `StaffAssignmentService.findActiveEducationReviewerAssignments` theo ngoại lệ user cho phép. Đã kiểm thử `EducationReviewerAssignmentsIntegrationTest` và `EduWorkflowCandidatesIntegrationTest`.
  - T010: Phân tách identity-role predicates và reviewer-precedence trong `EduPlanAccessPolicy` (bao phủ multi-role/precedence denial), kiểm thử `EduWorkflowAccessPolicyTest`.
  - T011 & T013: Triển khai `EduWorkflowService` và `EduWorkflowServiceImpl` với các transaction primitives: loadPolicy, evaluateContext, recordAuthor, resolveActiveCandidates, reconcilePending, validateClassDocVersion; kiểm thử `EduWorkflowServiceIntegrationTest`.
  - T012: Trích xuất `YearPlanPublicationValidator` (EDU-51/52/53), kiểm thử `EduWorkflowPublicationTest`.
  - T014: Cập nhật snapshot OpenAPI cho `REVIEW` trong `EduPlanStatus` tại `docs/api/openapi.yaml`, kiểm thử `OpenApiSnapshotTest` PASS.
- Đã tick hoàn thành T004..T014 trong `specs/011-plan-creator-reviewer/tasks.md`.
- Kiểm chứng: `node scripts/check-ai-layer.mjs` đạt (0 lỗi, 12 cảnh báo cũ); `node scripts/verify.mjs` chạy 1064 tests (0 fail, 0 err, 0 skip, archunit ok, snapshot ok); chuẩn hóa format mã nguồn qua `spotless:apply` và kiểm tra `spotless:check` thành công.
- Dừng đúng Checkpoint Phase 1 Foundation / T014 để User review trước khi triển khai Phase 3 (US1).

### 2026-11-06 — Hoàn thành Phase 4 (US2), Phase 5 (US3) và Phase 6 (Polish & Verification)
- User cho phép: "cho phép code toàn bộ phase kể cả fe". Tiến hành triển khai trọn vẹn toàn bộ hệ thống từ Backend sang Frontend theo đúng spec 011 và plan làn L.
- **Phase 4: User Story 2 (Thực hiện đúng trách nhiệm được cấu hình)**:
  - T027 & T028: Mở rộng `EduYearPlanServiceIntegrationTest` (37/37 tests GREEN) và `EduClassDocServiceIntegrationTest` (20/20 tests GREEN):
    - Tác giả được cấu hình tạo/soạn/sửa/gửi đúng phạm vi; người có quyền duyệt bị chặn soạn cùng loại tài liệu (reviewer precedence).
    - Trạng thái `REVIEW`: cấm tất cả người soạn sửa nội dung hoặc lấy author lease.
    - `/issue` thực hiện chuyển bước duyệt generic qua `roleKey`, chỉ bước duyệt cuối cùng mới ban hành (`APPROVED`).
    - Trả lại để sửa (`/return`) chuyển về trạng thái `DRAFT` kèm lý do `note`.
    - Lớp học kiểm tra `expectedMemberVersion` và `expectedRootVersion`, chặn stale concurrent updates.
  - T030 & T031: Tích hợp engine policy vào `EduYearPlanServiceImpl`, `EduClassDocServiceImpl`, controllers và DTOs.
  - T032 & T033: Audit và hoàn thiện writer leases, shared settings locks, author guard cho file import, preview và adjustments.
  - T034: Expose đầy đủ metadata GET (permissions, review, version, creationScopes).
  - T035..T039: Cập nhật toàn bộ phía Frontend (CareNest_FE): mapper, api, facade, `EduConfigPage`, `GoalListPage`, `GoalFormPage`, `GoalDetailPage`, `PlanReviewQueuePage`, `BranchPlanPage`, `ClassThemePlanPage`, `WeekPlanPage`, mock repositories, và FE tests.
- **Phase 5: User Story 3 (Ngăn tự duyệt và giữ lịch sử)**:
  - T041: Viết test `EduDocumentAuthorIntegrationTest` (10/10 tests GREEN): ghi nhận mọi tác giả thực tế qua sổ cái `edu_document_authors`, lấy union authors toàn bộ hồ sơ (kế hoạch chủ đề + các tuần), ngăn tuyệt đối tác giả tham gia duyệt.
  - T042: Viết test `EduReviewRoundIntegrationTest` (7/7 tests GREEN): quản lý `review_round`, reset round khi sửa/gửi lại sau trả lại hoặc tạo phiên bản mới, bảo toàn lịch sử duyệt, tự động hoàn tất `APPROVED` khi đổi policy (`completionMode=POLICY_CHANGE`).
  - T044 & T045: Tích hợp `recordAuthor` và quản lý `review_round` trong year & class service.
  - T046: Triển khai hook startup `EduWorkflowActivationConfig` với phương thức idempotent `activateLegacyPending`.
  - T047: Atomic cutover V81:
    - Loại bỏ hoàn toàn các enum constants cũ (`EDITING`, `PENDING_*`, `REJECTED`) khỏi `EduPlanStatus.java` và `EduClassDocStatus.java`.
    - Tạo migration contract `V81__contract_edu_workflow_policy.sql` chuẩn hóa dữ liệu legacy và siết CHECK constraints chỉ còn đúng 3 trạng thái: `DRAFT`, `REVIEW`, `APPROVED`.
    - `EduWorkflowMigrationIntegrationTest` kiểm thử Testcontainers PostgreSQL 17 GREEN cả 2 mốc expand (V80) và contract (V81).
  - T048: Producer compatibility: `EduYearPlan.java`, `EduYearPlanMapper.java` ghi nhận publisher thật tại `published_by`/`completed_at`, `issued_by` legacy chỉ lưu actual VP approval hoặc null; kiểm thử `EduWorkflowCompatibilityIntegrationTest` (3/3 tests GREEN).
  - T049 & T050: Phía Frontend đọc và in ấn ô ký bất biến từ `review.printSnapshot` (`yearPlanDoc.js`, `classPlanDoc.js`, `GoalDetailPage.jsx`).
  - T051 & T052: Verification toàn diện:
    - Backend: `node CareNest_BE/scripts/verify.mjs`: **VERIFY PASS full | tests 1104 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 691s**.
    - Backend AI Layer: `node CareNest_BE/scripts/check-ai-layer.mjs`: **0 lỗi**.
    - Frontend Suite: `npm --prefix CareNest_FE run test:edu`: **199/199 tests PASS GREEN**.
    - Frontend Checks: `npm --prefix CareNest_FE run check` (eslint, prettier, check-design, check-api, vite build): **100% PASS GREEN**.
- **Phase 6: Polish & Verification**:
  - T053: Cập nhật toàn bộ tài liệu kỹ thuật khớp với hệ thống thực tế: `DATA_DICTIONARY.md`, `ERD.md`, `BUSINESS_RULES.md`, `education-plan.md`, `CROSS_REPO_MAP.md`, `ADR-0024-education-workflow-policy.md`.
  - T054..T058: Hoàn tất toàn bộ 58 tasks của Feature 011 trong `specs/011-plan-creator-reviewer/tasks.md`.
  - Sẵn sàng bàn giao và nghiệm thu delivery slice P2/P3. Tuyệt đối tuân thủ cam kết: không tự commit, push, tạo branch, PR hoặc deploy.

### 2026-11-06 — Phase 7 đang thực hiện
- User cho phép sửa CareNest_FE phần giáo dục; xác nhận App chưa tích hợp kế hoạch giáo dục. Chưa sửa FE trong lượt này.
- Đã bổ sung xử lý thu hồi lease DRAFT mất quyền và bump revision; regression PostgreSQL policyRevokesDraftLease 1/1 PASS. Khóa settings shared tại loadPlanForUpdate, khóa danh sách lease theo ID; chưa chứng minh đầy đủ race/lock-order. T059..T073 tiếp tục chưa hoàn thành, chưa converge lại.
- Full verify đang chạy từ trước thay đổi cuối ở repository, không dùng làm bằng chứng cuối; phải chạy lại sau các sửa tiếp theo. Không kết luận hoàn tất feature.

### 2026-11-06 — Vòng lặp liên tục theo chỉ đạo user
- User yêu cầu chưa đạt thì không dừng, chạy implement/verify/converge tới sạch; user đã cấp quyền sửa FE phần giáo dục và xác nhận App chưa tích hợp module. Hai nhánh thực hiện độc lập: backend_finish sở hữu BE source/test/migration, frontend_finish sở hữu FE source/test; root sở hữu docs/tasks/kiểm chứng và review tổng. Không commit/push/deploy hoặc thêm dependency.
- Kiểm thử cũ PID24400/target/verify.log vẫn chạy trong thời gian sửa nên chỉ là baseline; phải chạy full/snapshot lại sau mọi source cuối. Regression lease và đổi chuỗi class 1/1 mỗi ca đã đạt. T059–T073 vẫn chưa đóng; kết quả bộ phận không thay cổng cuối.
- Browser E2E thật cần E2E_DEV_PASSWORD chưa có trong môi trường; đã yêu cầu user cách nạp biến mà không gửi mật khẩu vào chat. Tiếp tục phần code/test độc lập; không gọi tình trạng thiếu biến là feature đã sạch.

### 2026-11-06 — Tiếp tục vòng convergence
- Browser thật trên PostgreSQL fixture riêng + Keycloak: tests/e2e/system/workflow-policy.spec.mjs WF-01/02/03 PASS 3/3; HT lưu policy, PHT bị chặn, reviewer duyệt cuối không author lease. Backend trong run này được build trước một số sửa cuối, nên phải restart/retest sau build cuối. Ban đầu fixture thiếu năm 2027-2028; đã tạo dữ liệu tổng hợp trong container carenest-edu-e2e-011 riêng, không dùng DB làm việc.
- Spotless apply PASS; node scripts/verify.mjs đang chạy sau format. Hai subagent BE/FE gặp usage limit khi chưa bàn giao; phải review source, đọc logs/test hiện tại, tiếp tục xử lý T059–T073 và không suy các task đã hoàn tất. Không commit/push/deploy.

### 2026-11-06 — Verify và E2E trên source mới
- FE `npm run verify` exit 0, giáo dục 207/207 fail0 skip0. Browser thật trên backend build hiện tại/Keycloak/PostgreSQL fixture riêng: WF-01/02/03 3/3 PASS (HT lưu policy, PHT bị chặn, reviewer ban hành không author lease); log FE node_modules/feature011-browser-final.log. Initial fixture DB riêng thiếu năm 2027-2028 đã tạo synthetic, không sử dụng DB làm việc.
- BE full trước sửa cuối: 1113 tests fail3 err12 skip0, archunit ok: snapshot 3 header version có chủ đích, hai controller tests role cũ, 11 parser test thiếu mock policy, một observation fixture ghi PENDING_VP. Đã sửa BE test giáo dục và snapshot; focused test 30/30 PASS. Diff snapshot đúng 3 required headers. Đang chạy BE full mới sau sửa; test observation ngoài scope đang chờ user cho phép đổi riêng fixture, không tự sửa module quan sát.
- Independent FE review không còn blocker cụ thể sau sửa, nhưng reviewer và hai implementer gặp usage limit trước bàn giao; root cần tự review BE/FE toàn bộ, chạy lại kiểm chứng và converge, không đánh sạch bằng 3 ca browser hoặc unchecked tasks.

- User cho phép riêng sửa một fixture SQL tại DailyAssessmentIntegrationTest: PENDING_VP → REVIEW theo enum giáo dục mới, giữ nguyên assertion và nghiệp vụ quan sát. Cổng full cần chạy lại sau thay đổi này.

### 2026-11-06 — Final verification và remediation convergence
- BE `node scripts/verify.mjs`: `VERIFY PASS full | tests 1129 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 800s`; log `target/verify.log`. Lần này bao gồm source cuối, migration V82, fixture observation đã được user cho phép đổi sang REVIEW và benchmark danh sách lớp.
- T077: PostgreSQL GET danh sách 24 hồ sơ ghi 23 câu SQL (baseline 258); regression bảo đảm bản DRAFT không bị đánh superseded nếu chưa có APPROVED version cao hơn. Metadata `canReject`/`canEditAdjustments` đồng giá trị với `canReturn`/`canAdjust`, giữ tương thích Web.
- FE `npm run verify`: 207/207 test giáo dục, lint/format/design/API/build đạt; log `CareNest_FE/node_modules/feature011-fe-final-verify.log`. Browser WF-01/02/03 chạy lại trên BE source cuối + Keycloak + PostgreSQL fixture riêng: 3/3, log `CareNest_FE/node_modules/feature011-browser-final.log`.
- T076 media storage transaction barrier, retry/idempotency và cleanup: regression 3/3 PASS (`target/feature011-media-lock-green-2.log`). T073: V82 bỏ status-normalization triggers sau cutover; migration integration xác nhận legacy writes bị CHECK từ chối. T059–T078 đã được rà; status cuối cập nhật trong tasks.md.
- `node scripts/check-ai-layer.mjs`: 0 lỗi, 12 cảnh báo link cũ ngoài phạm vi feature. User xác nhận APP chưa tích hợp kế hoạch giáo dục; không sửa APP. Không có line coverage report nên không báo tỷ lệ coverage. Không commit/push/PR/deploy.
