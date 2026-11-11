# Tasks: Lọc phân cấp và gợi ý từ chương trình Bộ

**Input**: [spec](spec.md), [plan](plan.md), [research](research.md), [data model](data-model.md), [contract](contracts/curriculum-cascade.md), [quickstart](quickstart.md), [domain spec](domain-spec.md).

**Created**: 2026-11-08

**Status**: Implementing; user08/11 đã duyệt vòng implement→converge014 đến sạch; user đã cấp quyền sửa giáo dục FE014 ngày08/11/2026.

**Tests**: Có task kiểm hồi quy theo plan, acceptance scenarios và constitution: stale target/hidden selection, non-trivial source identity và tương tác bàn phím phải có bằng chứng. Viết ca kiểm trước phần sửa tương ứng, ghi RED thật; lỗi fixture/setup không là RED nghiệp vụ.

**Format**: - [ ] Tnnn [P tùy chọn] [USn trong story phase] mô tả + đường dẫn. [P] chỉ là khả năng làm song song sau prerequisites; không yêu cầu tự mở agent.

## Path Conventions

- Đường dẫn task tương đối với CareNest_BE; ../CareNest_FE là repo Web sibling.
- Chỉ source/test FE dự kiến thay đổi runtime; BE giữ catalog/API/DTO/schema/quyền hiện có.
- Hai file mới dự kiến: ../CareNest_FE/tests/education-plan/curriculum-cascade-browser.mjs và ../CareNest_FE/tests/e2e/system/curriculum-cascade.spec.mjs. Các file còn lại dùng lại source/test hiện có.
- Không tạo migration/dependency, không sửa raw curriculumSeed.js hoặc suy nguồn của dòng nhập tay theo tên.
- Chưa được sửa FE kể cả fixture/test trước T002. Lệnh tasks này không thực hiện T001–T030, không tự duyệt plan hoặc cấp quyền Git.

## Phase 1: Setup

**Purpose**: Chốt baseline và điều kiện bắt đầu đúng feature, không khởi tạo lại dự án.

- [x] T001 Ghi baseline branch/dirty files, con trỏ014, artifact đầu vào và môi trường Node/Docker/E2E vào specs/014-curriculum-cascade-suggestions/quickstart.md; giữ thay đổi workspace có trước, không in secret, cài dependency, chạy harness hoặc restart server user.
- [x] T002 Ghi kết quả speckit-analyze không còn blocker và phê duyệt triển khai plan014 vào delta014 tại docs/plans/active/2026-11-07-school-year-team-plans.md; giữ quyền sửa giáo dục FE user đã cấp08/11/2026, không xin lại; nếu chưa có phê duyệt plan thì dừng trước source/test FE, không kế thừa approved012/013 hoặc tự đổi status; phụ thuộc T001.

**Checkpoint**: Chỉ bắt đầu foundation khi T002 đủ điều kiện. Sinh tasks không phải approval triển khai.

## Phase 2: Foundational — nguồn mock dùng chung

**Purpose**: Đảm bảo nguồn mock và API đều có danh tính phân cấp trước hai luồng chính.

- [ ] T003 Viết ca regression normalization/parity cho4nhóm tuổi trong ../CareNest_FE/tests/education-plan/curriculum.test.mjs: source parent IDs stable qua reload, không va chạm tuple/tên trùng/nhánh null; C và D cùng sub key, C không bị gán group; goal.id numeric và raw seed không đổi; mock save/reload/clone/version giữ provenance; phụ thuộc T002, trace research R7/Q05.
- [ ] T004 Enrich parent IDs chỉ tại ../CareNest_FE/src/services/education-plan/mock/curriculumMockRepository.js từ tuple hierarchy nguồn với namespace mock ổn định độc lập thứ tự; giữ numeric goal.id/raw seed và không đổi API mapper để nhận mock IDs; kiểm T003 đạt, phụ thuộc T003.

**Checkpoint**: Mock source shape dùng được cho lọc và inline; chưa đổi server contract. Dữ liệu source-only identity không là phép ghép nguồn cho cây kế hoạch nhập tay.

## Phase 3: User Story 1 — Lọc đúng mục tiêu đích (Priority: P1) — MVP

**Goal**: Bộ lọc nguồn/đích nhất quán, không thể thêm bằng target hoặc nội dung đã tick bị ẩn ngoài phạm vi.

**Independent Test**: Hai lĩnh vực, mỗi bên hai con và nhóm/mục tiêu khác nhau; đổi cha từ A1 sang A2 bỏ target cũ, chặn thêm, giữ contents/ticks; con còn hợp lệ giữ nguyên. Đối chiếu Q01–03/07/09 trên cả mock và API-shaped fixture.

### Tests for User Story 1

- [x] T005 [P] [US1] Mở rộng ../CareNest_FE/tests/education-plan/curriculum.test.mjs cho actual parent từ flat rows, local/source ID khác nhau, cùng tên khác nhánh, ALL/NONE/VALUE, mục tiêu không nhóm, reconcile target/checked và immutable form; ghi RED stale target trước sửa, phụ thuộc T004; FR-001..005/012, AC-01/02/08.
- [ ] T006 [P] [US1] Tạo ../CareNest_FE/tests/education-plan/curriculum-cascade-browser.mjs theo mẫu team-plan-browser.mjs với modal thật/CSS thật, service fixture tổng hợp hai nhánh; ca target/checked ẩn, confirm sau form đổi/xóa đích và kiểm bảng không mất dữ liệu trước khi sửa UI; phụ thuộc T004, song song T005; Q01–03/07/09.

### Implementation for User Story 1

- [x] T007 [US1] Mở rộng goalTargets và helper thuần tại ../CareNest_FE/src/models/education-plan/curriculum.mjs để quét actual local sub/group context, giữ provenance riêng, lọc ALL/NONE/VALUE và tính giao selection hợp lệ không mutate form; không match sub/group chỉ theo tên; phụ thuộc T005; FR-001..005/012.
- [x] T008 [US1] Sắp xếp lĩnh vực → lĩnh vực con → kết quả mong đợi → mục tiêu đích tại ../CareNest_FE/src/components/education-plan/CurriculumGoalsModal.jsx; lọc cả targets/nguồn, reconcile khi cha/form/scope đổi, disabled và confirm dùng cùng eligibility; mục C chỉ domain/sub, group chỉ lọc đích; phụ thuộc T006/T007; FR-001..005.
- [ ] T009 [US1] Kiểm lại membership tại boundary addMinistryContents của ../CareNest_FE/src/pages/education-plan/GoalFormPage.jsx bằng importState.current.form và context/snapshot hiện hành, từ chối stale target/checked trước mutation; cập nhật callback props của ../CareNest_FE/src/components/education-plan/CurriculumGoalsModal.jsx nếu cần, không đổi payload API; phụ thuộc T008; FR-003..005/010.
- [ ] T010 [US1] Chạy Node targeted curriculum và browser Q01–03/07/09 tại FE theo specs/014-curriculum-cascade-suggestions/quickstart.md; ghi output RED→GREEN thật/giữ contents-ticks/SC-001..002 vào docs/plans/active/2026-11-07-school-year-team-plans.md delta014 rồi tự review increment modal và tiếp tục vòng lặp theo user; phụ thuộc T009.

**Checkpoint**: MVP US1 hoạt động độc lập với inline suggestions; code guard phải được kiểm cả khi gọi confirm trực tiếp, không dựa disabled button.

## Phase 4: User Story 2 — Gợi ý ngay tại ô nhập (Priority: P1)

**Goal**: Focus nhóm/goal/content mở gợi ý đúng source context, gõ lọc tiếp, chọn một lần đúng ô và giữ nguồn.

**Independent Test**: Hai nhóm R1/R2 cùng nhánh, có tên trùng; focus query rỗng ở cả ba loại ô, chọn bằng chuột/bàn phím/touch; nội dung R1 gồm pool các goal R1, không lẫn C/R2, không tự chèn con/tick.

### Tests for User Story 2

- [x] T011 [P] [US2] Bổ sung tests index source và options object tại ../CareNest_FE/tests/education-plan/curriculum.test.mjs: nhóm đúng sub/age, goal đúng nhóm, nội dung D từ mọi goal trong nhóm, source IDs khác local IDs, không ghép C hoặc text trùng; phụ thuộc T004/T010 để tránh sửa cùng file với US1; FR-006..008/010, AC-03..06.
- [ ] T012 [P] [US2] Bổ sung ca focus/gõ/chọn nhóm-goal-content và explicit source context cho dòng tay vào ../CareNest_FE/tests/education-plan/curriculum-cascade-browser.mjs; thêm Tab/Up/Down/Enter/Escape/Shift+Enter/IME/pointer-touch, dropdown portal và truy cập kết quả vượt12, kiểm không chọn hai lần hoặc Enter chèn thêm dòng; phụ thuộc T010, song song T011; Q04–07/Q10a/Q11.

### Implementation for User Story 2

- [x] T013 [US2] Thêm source index/options có key và provenance tại ../CareNest_FE/src/models/education-plan/curriculum.mjs, lấy group/goal/content D theo catalog IDs/age/source tuple và giữ thứ tự nguồn; không fallback sang nhánh khác; phụ thuộc T011; FR-006..008/010.
- [x] T014 [US2] Thêm tùy chọn enabled tương thích default cho ../CareNest_FE/src/hooks/education-plan/useCurriculum.js để GoalTable chỉ load một catalog ở cấp bảng editable/age hợp lệ; dùng lại service facade và scope guard useAsync, không request từng ô hoặc sửa hook chung chỉ để tiện; phụ thuộc T013; FR-009/011.
- [ ] T015 [US2] Nâng Suggest trong ../CareNest_FE/src/components/education-plan/GoalTables.jsx từ string sang object key/text/metadata; giữ FloatingPanel, mở focus query rỗng, list truy cập đủ kết quả, aria/keyboard/pointer và xử lý Enter chỉ commit option active; giữ IME/Enter bảng khi không chọn suggestion; phụ thuộc T012/T014; FR-006..010.
- [ ] T016 [US2] Thay expectedResultsFor hardcode trong ../CareNest_FE/src/components/education-plan/GoalTables.jsx bằng catalog cho ô nhóm hiện có/mới; khi chọn điền text và curriculumExpectedResultId/curriculumText đúng dòng, explicit source context tạm theo row cho sub/group nhập tay chưa linked, không tự ghép tên/gắn source; phụ thuộc T015; FR-006/010/012.
- [x] T017 [US2] Gắn gợi ý vào ô mục tiêu và nội dung hiện có/mới tại ../CareNest_FE/src/components/education-plan/GoalTables.jsx: goal theo source group, content pool theo Q2; chọn goal giữ curriculumGoalId/text/sectionRef, chọn nội dung chỉ áp ô đích theo bỏ trùng hiện có, không tạo con/tick hoặc sửa hàng khác; phụ thuộc T016; FR-007..008/010.
- [ ] T018 [US2] Hoàn thiện scoped style gợi ý/focus/empty helper và vị trí panel tại ../CareNest_FE/src/styles/modules/education-plan.css cho1440/390, dùng token hiện có, không sửa shared table/readonly layout ngoài014; phụ thuộc T017; AC-03..06/Q11.
- [ ] T019 [US2] Cập nhật fixture imports/service data trong ../CareNest_FE/tests/education-plan/branch-template-enter.test.mjs theo GoalTable mới và bổ sung assertion Enter/Shift+Enter/IME không tạo hai dòng khi chọn suggestion; không bỏ assertion/test hoặc stub bỏ qua quyền; phụ thuộc T018; Q11.
- [ ] T020 [US2] Chạy Node education suite, ESLint mọi file JS/JSX/test đổi, Vite build và browser Q04–07/Q10a/Q11 theo specs/014-curriculum-cascade-suggestions/quickstart.md; ghi mock/real-shaped source parity, source metadata và SC-003..004 tại docs/plans/active/2026-11-07-school-year-team-plans.md delta014 rồi tự review increment inline và tiếp tục vòng lặp theo user; phụ thuộc T019.

**Checkpoint**: Gợi ý hoạt động khi đủ nguồn; dòng tay còn nhập được, nguồn chọn tường minh không đổi cha thực; readonly/TEAM vẫn phải giữ guard hiện có nhưng bằng chứng Q10b được nghiệm thu tại T025 sau T021/T024. Tất cả matched options truy cập được, không giữ trần12 ẩn.

## Phase 5: User Story 3 — Giữ bản soạn khi rỗng/lỗi và giữ quyền (Priority: P2)

**Goal**: Phân biệt thiếu cha/rỗng/loading/error, retry không mất bản soạn và response cũ không hồi sinh lựa chọn.

**Independent Test**: Service fixture điều khiển hai scope A/B và retry500; trả A muộn sau B rồi reload. Chụp bản soạn trước/sau và đếm request/mutation trên viewOnly/fixedCells/canEditCurriculum=false.

### Tests for User Story 3

- [ ] T021 [US3] Bổ sung controlled-service regressions trong ../CareNest_FE/tests/education-plan/curriculum-cascade-browser.mjs cho thiếu cha/nhánh rỗng/nhóm tuổi không dữ liệu/loading/error/retry, A→B response muộn, form/age/session đổi, và readonly/TEAM không request catalog mới hoặc mutation; phụ thuộc T020; FR-009/011/012, AC-06..08/Q08–09/Q10b.

### Implementation for User Story 3

- [ ] T022 [US3] Hoàn thiện states loading/error/retry/empty/missing-parent và reset source context tạm theo form/row/age trong ../CareNest_FE/src/components/education-plan/GoalTables.jsx; giữ bản soạn và con hợp lệ, không show catalog scope cũ hoặc tự rộng filter; dùng scope guard hiện có, phụ thuộc T021; FR-009/012.
- [ ] T023 [US3] Hoàn thiện states/reconciliation modal trong ../CareNest_FE/src/components/education-plan/CurriculumGoalsModal.jsx khi request scope đổi/lỗi/thử lại hoặc target không còn tồn tại; confirm không gọi callback với lựa chọn hết hiệu lực và dữ liệu đã thêm giữ nguyên; phụ thuộc T022; FR-004..005/009.
- [x] T024 [US3] Hoàn thiện editable/age enabled và mutation guard trong ../CareNest_FE/src/components/education-plan/GoalTables.jsx cùng ../CareNest_FE/src/hooks/education-plan/useCurriculum.js; readOnly/fixedCells/canEditCurriculum=false không load mới chỉ vì render hoặc sửa cây nguồn, giữ tick TEAM theo capability cũ; phụ thuộc T023; FR-011/Q10b.
- [ ] T025 [US3] Chạy browser Q08–09/Q10b sau test T021 và guard T024; nghiệm thu readonly/TEAM không request mới hoặc mutation, rồi chạy lại Q10a và toàn bộ ca014 đã có tại specs/014-curriculum-cascade-suggestions/quickstart.md, ghi request/mutation counts và bản soạn/ticks bất biến vào docs/quality/VERIFICATION.md; nếu thay useAsync ngoài plan phải nêu delta trước, không sửa vì tiện; phụ thuộc T024.

**Checkpoint**: Cả ba story đạt kiểm độc lập, lỗi/retry không thay dữ liệu. Phase tasks theo story chia phần increment inline của plan thành US2 rồi US3; không tự bỏ checkpoint review sau native nghiệm thu.

## Phase 6: Polish & Cross-Cutting Validation

- [ ] T026 Tạo suite ../CareNest_FE/tests/e2e/system/curriculum-cascade.spec.mjs theo pattern published-year-plan-visibility.spec.mjs: SCHOOL_YEAR/annual route/creator-reviewer có phân công/policy hợp lệ/DRAFT–REVIEW–APPROVED; kiểm Q12 gồm nguồn số thật, filter/inline/save/reload/version/giữ ticks và TEAM readonly, cleanup theo marker, không đổi global policy/legacy TEAM suites để ép pass; phụ thuộc T010/T020/T025.
- [ ] T027 Chạy dedicated live E2E T026 và các lệnh native liên quan tại specs/014-curriculum-cascade-suggestions/quickstart.md; báo cwd/exit/test-fail-skip và API thật so với fixture, không tự restart server user; nếu env/auth thiếu ghi chưa chạy, task còn chưa hoàn thành nghiệm thu live; Maven targeted chỉ khi cần xác minh contract/provenance/guard, báo Testcontainers skip thật; phụ thuộc T026.
- [ ] T028 Review diff014 so spec/contract/data model và ma trận8AC/12FR/4SC/12Q trong specs/014-curriculum-cascade-suggestions/quickstart.md; xử lý finding đúng phạm vi và chạy lại kiểm liên quan sau sửa, ghi native output/matrix vào docs/quality/VERIFICATION.md; không gán suite cũ hoặc kiểm artifact thành coverage runtime; phụ thuộc T027.
- [ ] T029 Sau implement chạy speckit-converge theo specs/014-curriculum-cascade-suggestions/tasks.md: gap mới chỉ append ID tiếp theo, giữ30ID/check state/history hiện có, implement và kiểm lại tới sạch; nếu không gap giữ tasks nguyên byte, không thêm header rỗng; ghi evidence convergence ở specs/014-curriculum-cascade-suggestions/quickstart.md; phụ thuộc T028.
- [ ] T030 Bàn giao bằng chứng cuối trong docs/quality/VERIFICATION.md và delta014 của docs/plans/active/2026-11-07-school-year-team-plans.md; chỉ cập nhật docs/knowledge/incidents/BUG-261108-curriculum-target-filter.md cùng docs/knowledge/ISSUE_INDEX.md sang fixed khi có root cause/RED→GREEN/regression thật, ghi phần chưa kiểm chứng và tự review kết quả; không tự commit/push/PR/deploy hoặc đóng012/013; phụ thuộc T029.

## Dependencies & Execution Order

### Phase dependencies

- Setup T001 → T002 (approval/analyze gate) → foundation T003 → T004.
- US1 T005/T006 → T007 → T008 → T009 → T010 (MVP/review).
- US2 T011/T012 → T013 → T014 → T015 → T016 → T017 → T018 → T019 → T020 (inline/review).
- US3 T021 → T022 → T023 → T024 → T025.
- Final T026 → T027 → T028 → T029 → T030; thực hiện thứ tự, không đánh dấu liveE2E nếu chỉ có fixture.

### Story independence và thứ tự tích hợp

US1 là MVP hoàn chỉnh riêng (không cần inline). US2 có thể kiểm nguồn/ô nhập riêng sau foundation bằng fixture nhưng triển khai khuyến nghị sau US1 vì cùng curriculum.mjs và browser file. US3 là increment reliability riêng kiểm bằng controlled fixture, tích hợp sau US2 vì cùng GoalTables/modal. Đây là phụ thuộc chia sẻ file/tích hợp, không thay priority (US1=P1,US2=P1,US3=P2).

Trong mỗi story: tests trước implementation; fixture errors cần sửa fixture và ghi thật, không coi là RED nghiệp vụ. Tất cả task code/test FE bị T002 chặn.

### Parallel examples theo story

- US1: sau T004, T005 (curriculum.test.mjs) và T006 (browser file mới) có thể viết song song, chưa sửa source. T007 chờ test helper, T008 chờ cả browser và helper.
- US2: sau T010, T011 (helper tests) và T012 (browser tests) có thể viết song song; GoalTables T015–017 và fixture T019 phải tuần tự.
- US3: T021–025 cùng browser/GoalTables/modal nên không có task [P] an toàn trong story; chuẩn bị dữ liệu tổng hợp không là một task source riêng.
- Có4task [P] tổng cộng; không chạy các story đồng thời trên file dùng chung hoặc coi [P] là quyền sửa repo client.

## Acceptance Coverage

| Trace | Tasks | Kiểm độc lập |
| --- | --- | --- |
| FR-001/002/003, AC-01, SC-001 | T005–009/T026–028 | Q01/Q02/Q09 |
| FR-004/005, AC-02, SC-001/002 | T005–010/T023/T026–028 | Q02/Q03/Q07 |
| FR-006, AC-03, SC-003 | T011–016/T018–020/T026–028 | Q04/Q11 |
| FR-007, AC-04, SC-003/004 | T003–004/T011–017/T019–020/T026–028 | Q05/Q11/Q12 |
| FR-008, AC-05, SC-003/004 | T011–013/T017/T020/T026–028 | Q06/Q12 |
| FR-009/010, AC-06, SC-002/004 | T009/T012/T015–025/T026–028 | Q07/Q08/Q11/Q12 |
| FR-011, AC-07 | T014/T021/T024–028 | Q10b/Q12 |
| FR-012, AC-08 | T005–007/T016/T021–025 | Q09/Q10a/Q10b |

Research R7/mock parity và identity: T003–004/T011/T026–028. Q12 chỉ nghiệm thu bằng live T027, không browser fixture. Contract không có transport/schema delta nên không có task thay OpenAPI/migration hoặc API implementation.

## Implementation Strategy

1. T001–004 chuẩn bị baseline, approval và nguồn mock đúng shape; không cài stack hoặc tạo branch.
2. MVP T005–010 giải quyết lỗi đích stale và giữ dữ liệu, review increment1.
3. T011–025 hoàn thiện inline rồi reliability/accessibility và review increment2; chỉ một catalog cho mỗi scope editable, không load từng ô/readonly.
4. T026–030 nghiệm thu live/review/converge/bàn giao increment3; ghi mọi skip/gap thật, không claim sạch khi còn Q12 chưa chạy.
5. User đã yêu cầu vòng implement→converge đến sạch: tự review mỗi checkpoint rồi tiếp tục trong cùng phiên. Không tự mở rộng approval012/013. Không push/deploy hoặc tạo commit tự động theo notes của template.

## Generation Snapshot

30 task chưa thực hiện: Setup2, Foundation2, US1=6, US2=10, US3=5, Final5. Bốn task [P]. Đọc template/artifact trực tiếp theo AGENTS.md; extensions.yml/override/preset không tồn tại, không setup-tasks/harness/hooks. Snapshot này mô tả generation, không phải bằng chứng implement/analyze/runtime pass.

Rà lại speckit-tasks ngày08/11/2026 sau remediation I1: giữ nguyên30ID/checkbox/4[P], không thêm task trùng; Q10a nghiệm thu T020, Q10b sau T021/T024 tại T025. Quyền sửa FE014 đã được user cấp; plan014 vẫn draft. Danh sách đủ phạm vi hiện tại, bước tiếp speckit-analyze; chưa thực hiện task.

Execution08/11: user duyệt vòng lặp đến sạch; các checkpoint vẫn tự review và ghi bằng chứng, tiếp tục không hỏi lại. Trạng thái các task chỉ đổi theo bằng chứng thực hiện.

## Phase 7: Convergence

- [x] T031 CRITICAL Sau khi user duyệt [delta dữ liệu nguồn](source-correction-plan.md), tạo migration Flyway mới và đồng bộ mock theo [SQL draft](contracts/ministry-source-correction-draft.sql), sửa 33 dấu hai chấm và câu ag-2/27 bị tách sai; giữ ID mục tiêu, bản kế hoạch đã chép và DTO, kiểm preflight/rollback/response thật theo EDU-28, FR-008/010 và yêu cầu user 08/11 (contradicts).
- [ ] T032 Đối chiếu đúng cột tuổi và ranh giới mục tiêu/nội dung cho toàn bộ danh mục Bộ trong specs/014-curriculum-cascade-suggestions/source-colon-audit.md và kết quả nguồn chính thức, ghi từng sai lệch có trang/cột và bổ sung task tiếp theo nếu phát hiện; không coi so chuỗi con là đủ (FR-002/008/010, partial).
- [x] T033 Bổ sung regression trong ../CareNest_FE/tests/education-plan/curriculum-cascade-browser.mjs cho catalog cùng scope đã tải rồi reload pending/lỗi: ArrowDown/Enter không commit option đang ẩn, retry giữ bản soạn; kèm ca gõ ký tự đầu tại ô mới sau chọn ngữ cảnh nguồn và giữ scope (FR-009/010/012, partial).

## Phase 8: Convergence

- [x] T034 CRITICAL Hoàn thành T031 sau khi delta nguồn được duyệt: sửa nguyên văn 33 dấu hai chấm và câu ag-2/27 trong catalog thật và mock, giữ dữ liệu kế hoạch đã chép; kiểm response và rollback theo FR-008/010 (contradicts).
- [ ] T035 Hoàn thành T032 và đối chiếu bổ sung theo chiều văn bản Bộ → catalog: kiểm mục bị thiếu và quan hệ lĩnh vực/con/nhóm, ranh giới câu ở từng nhóm tuổi, ghi nguồn trang/cột cùng mọi sai lệch trước khi xác nhận nguồn theo FR-002/008 và plan: catalog source (partial).
- [x] T036 Chạy lại live E2E T026 sau lần sửa source cuối và sau thay đổi catalog được duyệt; kiểm filter/inline/save/reload/version/TEAM và ghi kết quả thực tế theo plan: validation, SC-001..004 và Constitution IV (partial).

## Phase 9: Bổ sung catalog sau đối chiếu nguồn

- [x] T037 Bổ sung 69 nội dung C và 4 mục tiêu D được xác nhận trong báo cáo, cùng hai nội dung hô hấp phát hiện sau đó; giữ ID cũ, thứ tự nguồn, mock parity và preflight V95, kiểm PostgreSQL và FE theo FR-002/008/010.
- [ ] T038 Tiếp tục T032/T035: đối chiếu ngược toàn bộ ô gộp, mục C/D, câu gãy trang và quan hệ cha theo văn bản Bộ; xử lý các sai lệch còn lại sau khi xác minh, không coi số ứng viên tự động là chứng nhận đầy đủ.
