# Tasks: Quyền xem năm đã ban hành và mục tiêu tổ đầy đủ

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contract](contracts/visibility-and-layout.md), [quickstart.md](quickstart.md).

**Created**: 2026-11-08. **Updated**: 2026-11-08 sau implement/converge. **Status**: Implemented — 34/34 task có bằng chứng hoàn thành trong phạm vi delta;24 task gốc +5 UI-first +5 convergence. Các ghi chú generation trước đây là snapshot lịch sử; xem kết quả hiện hành ở cuối file và quickstart.

**Authorization**: User cho phép sửa giáo dục FE và sau đó yêu cầu “implement/converge chạy đến khi sạch”, ưu tiên UI trong tin nhắn08/11/2026. Đây là approval triển khai delta BE/FE và thứ tự UI-first; không xin lại từng phase, vẫn tự review/verify. Không cấp quyền branch/commit/push/PR/deploy hoặc thêm dependency. Các ghi chú gate/draft của generation bên dưới là lịch sử, đã qua bằng evidence T002.

**Tests**: Có test tasks vì spec nêu Independent Test/Acceptance Scenarios/SC-001–004, plan/quickstart chỉ định regression và constitution yêu cầu automated checks cho phân quyền. Tái sử dụng test hiện có; test hành vi có ý nghĩa, không test chỉ phản chiếu implementation. Viết ca hồi quy trước patch tương ứng, ghi RED/GREEN thật; không sửa/bỏ test để che lỗi.

## Format: `[ID] [P?] [Story] Description`

- Mọi task có checkbox, ID tuần tự và đường dẫn chính xác. Story phases bắt buộc [US1]/[US2]; setup/foundation/polish không gắn story.
- [P] nghĩa là có thể thực hiện cùng các task khác ở cùng đợt khi tiền nhiệm đã xong, không cùng sửa file. Không tự spawn agent chỉ vì có [P].
- Path `src/...`, `docs/...`, `specs/...` tính từ BE; `../CareNest_FE/...` là client đã được user cho phép. Không sửa APP.
- Không chạy harness/orchestrator/setup/check/verify scripts hoặc extension hooks. Dùng native Maven/Node/ESLint/Vite/Chromium như quickstart; không thêm dependency/migration/schema/endpoint/DTO/ApiCode.

## Phase 1: Setup — giữ baseline và phạm vi

**Purpose**: Tiếp tục đúng checkout đang có thay đổi, không tạo project hoặc dependency mới.

- [X] T001 Đối chiếu baseline Git read-only và quyền FE đã được cấp, ghi các file đang thay đổi ngoài delta cần giữ cùng giới hạn môi trường vào `specs/013-published-year-plan-visibility/quickstart.md`; giữ branch hiện tại, không commit/push/reset hoặc sửa repo ngoài scope.

## Phase 2: Foundational — điều kiện trước triển khai

**Purpose**: Cổng phân tích/phê duyệt trước mọi source patch; không xây framework hay dữ liệu lưu mới.

- [X] T002 Hoàn tất phân tích chéo domain/spec/plan/data-model/contract/tasks bằng speckit-analyze chỉ đọc, xử lý blocker trong lượt sửa được phép riêng và đối chiếu phê duyệt plan delta013 làn L; ghi nguồn approval thực sự và kết quả phân tích vào `docs/plans/active/2026-11-07-school-year-team-plans.md` và `docs/quality/VERIFICATION.md` sau lượt chỉ đọc, không tự approve; nếu chưa có approval thì hoàn thiện tài liệu và chờ trước code, không hỏi lại quyền FE đã cấp.

**Checkpoint**: Không source task nào được bắt đầu khi analyze còn blocker hoặc plan delta013 chưa được duyệt. T001/T002 là công việc tương lai, không đánh dấu chỉ vì tài liệu task đã được tạo.

## Phase 3: User Story 1 — Chỉ xem căn cứ năm học hợp lệ (Priority: P1, MVP)

**Goal**: Khi chỉ có quyền lập tổ, xem annual APPROVED: HT/PHT hiệu lực mọi nhóm tuổi, tổ trưởng/GV đúng tuổi phân công; giữ quyền năm độc lập Q1=A, source guards và TEAM cũ.

**Independent Test**: Dùng hai tài khoản tổ trưởng/GV phụ trách hai nhóm tuổi khác nhau và tài khoản HT/PHT có quyền đọc hiệu lực; HT/PHT xem mọi tuổi, tổ trưởng/GV không xem chéo. Kiểm DRAFT/REVIEW/APPROVED, phân công hiện tại/tương lai/hết/thu hồi, kiêm nhiệm quản lý, annual author/reviewer và policy loại PHT; chỉ tạo TEAM vẫn chỉ xem APPROVED dù scope tuổi toàn trường. Kiểm list/count/page/detail/source/changes/version metadata; dùng task US1 và Q01–Q07/Q10–Q11, không cần đổi layout US2.

### Tests for User Story 1

- [X] T003 [P] [US1] Bổ sung regression EDU-60/Q1=A trong `src/test/java/com/carenest/service/eduplan/impl/EduWorkflowAccessPolicyTest.java`: hai tổ trưởng/GV khác tuổi không xem chéo, HT/PHT hiệu lực được mọi nhóm tuổi khi có quyền đọc hợp lệ kể cả chỉ tạo TEAM và không có quyền năm độc lập thì chỉ APPROVED; mất phân công HT/PHT không giữ scope toàn trường; tập quyền theo status/level, reviewer tuổi-scoped không nhận DRAFT từ TEAM, annual creator/reviewer giữ scope độc lập, PHT active bị loại khỏi policy schoolYear không đọc DRAFT/REVIEW bằng role; nếu vẫn có quyền tạo TEAM thì được APPROVED ở mọi tuổi, phân công hết/tương lai/thu hồi không cấp quyền (FR-001–003, AC-05/08–11, SC-001/002, Q05–Q07).
- [X] T004 [P] [US1] Bổ sung fixtures và ca integration vào `src/test/java/com/carenest/service/eduplan/impl/SchoolYearTeamPlanIntegrationTest.java`: list lọc trước count/page kể cả không truyền age; HT/PHT hợp lệ thấy APPROVED ở mọi tuổi, tổ trưởng/GV chỉ tuổi được giao, direct id403/unknown404, duyệt trung gian/cuối, nguồn năm APPROVED cùng năm/tuổi/current source, không dùng nháp kể cả annual author, kiểm hai đầu changes và không lộ draft version/id; dùng Clock cố định và biên validFrom/validTo (FR-001–004, AC-01–05/08–11, SC-001/002, Q01–Q07/Q10).
- [X] T005 [P] [US1] Bổ sung test annual mock ở `../CareNest_FE/tests/education-plan/school-year-team-mock.test.mjs`: list/detail/source/version cùng scope, hai tổ trưởng/GV/hai tuổi/ba status, thêm HT/PHT hợp lệ mọi tuổi và hết phân công quản lý không giữ scope toàn trường, không có hoặc hết/thu hồi phân công theo fixture hiện có, Q1=A và custom schoolYear policy loại PHT; giữ TEAM ngoài siết mới (FR-001–004, AC-01–05/08–11, SC-001/002).
- [X] T006 [P] [US1] Bổ sung regression consumer trong `../CareNest_FE/tests/education-plan/year-plan-api.test.mjs` và `../CareNest_FE/tests/education-plan/goal-list-status.test.mjs`: filter/requested age không mở quyền, source chỉ APPROVED/cùng tuổi/năm được cấp, API403 không fallback mock/không giữ nội dung nguồn cũ, scopes rỗng không suy diễn toàn trường, list rỗng có thông báo phù hợp (FR-001–004, AC-01–05/08–11).

### Implementation for User Story 1

- [X] T007 [US1] Mở rộng ViewScope nội bộ và nhánh scope SCHOOL_YEAR trong `src/main/java/com/carenest/security/authorization/EduPlanAccessPolicy.java`: thêm reviewAgeGroupIds với constructor3 tham số tương thích; annual union quyền năm độc lập hợp lệ và APPROVED ∩ quyền tạo TEAM theo phân công CurrentUser/Clock; scope tuổi của HT/PHT hiệu lực là mọi nhóm tuổi, tổ trưởng/GV là tuổi được giao như user chốt sau analyze, dùng policy schoolYear/year đúng cấp; không copy raw base TEAM schoolWide/fullAges, không cấp DRAFT chỉ vì reviewer có REVIEW; nhánh quyền năm độc lập của HT/PHT schoolWide phải giao eligibility schoolYear/phân công và status gates cũ; tách nhánh APPROVED từ quyền tạo TEAM có thể toàn trường cho HT/PHT hợp lệ mà không cần eligibility schoolYear; giữ TEAM/EDU-24 và reviewer-precedence (sau T003; FR-001–003, Q1=A).
- [X] T008 [US1] Thêm điều kiện REVIEW theo nhóm tuổi vào search overload trong `src/main/java/com/carenest/repository/eduplan/EduYearPlanRepository.java`, giữ overload TEAM cũ ủy quyền tập review rỗng; cùng predicate với ViewScope, lọc quyền trước pageable/count, không tăng lượt truy vấn hoặc thêm schema/index hoặc lọc sau phân trang (sau T004/T007; FR-001/002, Q01/Q06).
- [X] T009 [US1] Dùng scope theo level/status thống nhất cho getYearPlans/loadViewablePlan/loadPlanForUpdate trong `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java`; tính policy/assignment scope một lần mỗi request và tái sử dụng, không cache quyền giữa request hoặc fallback base TEAM cho annual; giữ permission/status/lock guards và quy ước log hiện hành (sau T007/T008; FR-001–003, AC-01–03/05/08–11).
- [X] T010 [US1] Hoàn thiện read projections trong `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java`: visible version chain/metadata chỉ chứa bản được phép xem, changes kiểm cả hai id, không biến compareVersions nội bộ thành public bypass; bảo toàn current-source validation APPROVED/cùng năm/tuổi/current và thứ tự khóa, quyền xem nháp không cho dùng nháp lập tổ (sau T009; FR-002–004, Q04/Q10).
- [X] T011 [US1] Thêm hoặc tái sử dụng ca regression TEAM legacy/source/version trong `src/test/java/com/carenest/service/eduplan/impl/EduYearPlanServiceIntegrationTest.java` và `../CareNest_FE/tests/education-plan/import-goal-sources.test.mjs`; chứng minh EDU-24 cross-age APPROVED TEAM, hồ sơ tổ cũ, trạng thái/ticks/phiên bản không đổi, giữ tests hiện hữu (sau T009/T010; scope/FR-002, Q11).
- [X] T012 [US1] Chạy native BE targeted tests gồm EduWorkflowAccessPolicyTest/SchoolYearTeamPlanIntegrationTest/EduYearPlanServiceIntegrationTest rồi EduYearPlanControllerTest/OpenApiSnapshotTest/ArchitectureRulesTest và spotless:check theo quickstart; ghi output/fail/error/skip vào `docs/quality/VERIFICATION.md`, giữ OpenAPI/schema nếu không đổi thật, Docker-skip là chưa kiểm chứng; dừng review phase013.1 trước phần client US1 (sau T007–T011; SC-001/002).
- [X] T013 [US1] Siết duy nhất SCHOOL_YEAR ở mọi read surface của `../CareNest_FE/src/services/education-plan/mock/educationPlanMockRepository.js`: getAll/list/detail/source/version dùng predicate annual chung từ actor/phân công/cấu hình, Q1=A giữ quyền năm độc lập; HT/PHT hiệu lực mọi tuổi trong quyền đọc được cấp, tổ trưởng/GV đúng tuổi phân công; scope mọi tuổi không mở DRAFT/REVIEW khi chỉ tạo TEAM; không gán permissions cho bản ngoài quyền hoặc sửa sourceVisible TEAM; phản ánh thay đổi phân công ở lần đọc sau, không tạo store quyền mới (sau T005/T012 và review BE; FR-001–004, SC-001/002).
- [X] T014 [US1] Đối chiếu và chỉ sửa consumer cần thiết tại `../CareNest_FE/src/pages/education-plan/GoalFormPage.jsx`, `../CareNest_FE/src/pages/education-plan/GoalDetailPage.jsx`, `../CareNest_FE/src/pages/education-plan/GoalListPage.jsx` và `../CareNest_FE/src/services/education-plan/api/educationPlanApi.js`: source annual APPROVED/year/age/server scope, lý do nguồn trống rõ, đọc403 không render lại dữ liệu bị cấm/fallback mock, không mở quyền qua filter/role; nếu code đã đúng ghi evidence thay vì patch, không refactor provider page200 hay response mapper ngoài scope (sau T006/T012/T013 và review BE; FR-001–004, AC-04).
- [X] T015 [US1] Sau review BE, chạy native Node suite US1/import-source regression, ESLint source JS/JSX thực sự đổi và Vite build; ghi evidence thực tế cho Q01–Q07/Q10/Q11 tự động ở `specs/013-published-year-plan-visibility/quickstart.md` và `docs/quality/VERIFICATION.md`, không coi mock là nghiệm thu API thật (sau T013/T014/T012; FR-001–004, SC-001/002).

**Checkpoint**: MVP US1 có scope server đúng, client/mock không mở quyền, native regression đủ; test skip/fail hoặc chưa có API thật phải được ghi rõ. Phase BE dừng review tại T012; phần Web US1 và US2 thuộc delivery013.2, kết thúc review ở T020.

## Phase 4: User Story 2 — Đọc đầy đủ mục tiêu trong kế hoạch tổ (Priority: P2)

**Goal**: Card mục tiêu TEAM giãn theo dữ liệu, cuộn document, đủ nhãn/cây/nội dung/hàng chưa tick; giữ source readonly và tick từng nội dung.

**Independent Test**: Dùng một TEAM được phép xem có 50 mục tiêu × ít nhất2 nội dung, nhiều nhóm và chữ dài; cả màn soạn/chi tiết, có/không chủ đề, desktop/màn hẹp. Đếm hàng/nội dung và đọc tới cuối bằng cuộn document; tick/lưu/mở lại chỉ đổi đúng liên kết. Có thể test layout với dữ liệu TEAM hợp lệ mà không phụ thuộc thay đổi scope US1.

### Tests for User Story 2

- [X] T016 [P] [US2] Mở rộng `../CareNest_FE/tests/education-plan/year-plan-layout.test.mjs` bằng ca TEAM form/detail nhiều nhóm/nhãn/chữ dài/hàng chưa tick và không chủ đề/nhiều chủ đề; dùng GoalTable thật cho ca đếm cây nếu test runner hỗ trợ, không dùng stub hiện tại để khẳng định đủ nội dung hoặc hết scroll; fixture50 mục tiêu×>=2 nội dung dùng lại cho browser T019, không thêm thư viện (FR-005/006, AC-06, SC-003/004, Q08/Q12).
- [X] T017 [US2] Sửa selector `.ga-team-plan .table-wrap:has(> .ga-goal-table)` trong `../CareNest_FE/src/styles/modules/education-plan.css`: max-height:none và overflow:visible cả hai trục, bỏ sticky thead riêng của vùng cuộn cũ; bao phủ không có chủ đề, giữ min-width, nội dung nhiều dòng, shared table-wrap và bảng annual; document sở hữu cuộn dọc/ngang khi cần (sau T016; FR-005/006, AC-06, SC-003, Q08/Q12).
- [X] T018 [P] [US2] Bổ sung hoặc tái sử dụng regression trong `../CareNest_FE/tests/education-plan/school-year-team.test.mjs` và `../CareNest_FE/tests/education-plan/goal-labels.test.mjs`: source chỉ đọc, nhãn/phân nhóm/thứ tự/hàng chưa tick giữ nguyên, tick một nội dung không tự tick mọi nội dung của mục tiêu, lưu/mở lại đúng liên kết; không sửa logic tick/renderer nếu không có gap (FR-005/007, AC-07, SC-004, Q09).
- [X] T019 [US2] Kiểm Q08/Q09/Q12 bằng Chromium/CSS thật trên form/detail TEAM được phép xem, fixture50×>=2, có/không chủ đề ở desktop/hẹp; assert computed overflow cả hai trục visible/max-height none, wrapper scrollTop/Left không sở hữu cuộn, document đưa hàng/cột cuối vào view và không ancestor clip; tick bằng bàn phím/lưu/mở lại bảo toàn dữ liệu, annual không bị ảnh hưởng; ghi kết quả/ảnh hoặc log đã khử thông tin nhạy cảm vào `specs/013-published-year-plan-visibility/quickstart.md` (sau T017/T018; FR-005–007, SC-003/004).
- [X] T020 [US2] Chạy native Node tests giáo dục liên quan US1+US2 theo lệnh quickstart đã bao gồm tests/education-plan/school-year-team.test.mjs và tests/education-plan/goal-labels.test.mjs của T018, ESLint source đổi/Vite build theo quickstart và kiểm CSS browser; ghi bằng chứng/skip thật vào `docs/quality/VERIFICATION.md` và Progress log `docs/plans/active/2026-11-07-school-year-team-plans.md`, dừng review delivery013.2; thiếu browser thật không được đánh SC-003/004 đạt (sau T015/T019; FR-005–007).

**Checkpoint**: US2 đọc đủ 100% fixture và không có vùng cuộn card, tick/source không đổi; SSR/regex không thay thế kiểm layout thật.

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Tích hợp thực tế và bàn giao delta, tương ứng phase013.3 của plan, không mở rộng nghiệp vụ.

- [X] T021 Nghiệm thu API thật Q01–Q07/Q10/Q11 bằng các user thử được phân công hợp lệ, kiểm hai tổ trưởng/GV khác tuổi, HT/PHT hợp lệ mọi tuổi, mất phân công quản lý/kiêm nhiệm không giữ scope toàn trường, ba status/custom policy và thu hồi phân công, list/count/page/detail/source/version/changes cùng scope; ghép evidence CSS Q08/Q09/Q12 của T019 vào `specs/013-published-year-plan-visibility/quickstart.md`, ghi phần chưa chạy nếu thiếu môi trường/quyền, không tự restart process user hoặc xuất secret (sau T015/T020; SC-001–004).
- [X] T022 Đồng bộ mô tả runtime sau kiểm chứng vào `docs/business/BUSINESS_RULES.md`, `docs/modules/education-plan.md`, `docs/business/flows/education-plan.md` và `docs/system/CROSS_REPO_MAP.md`; giữ EDU-60 PROPOSED theo mức nguồn dù user đã chốt, phân biệt đã làm/chưa kiểm chứng, không claim thay đổi APP hoặc đóng gap012 không liên quan (sau T021).
- [X] T023 Review delta source bằng bằng chứng thật và thực hiện converge sau implement, ghi gap→task mới append-only trong `specs/013-published-year-plan-visibility/tasks.md` cùng evidence ở `specs/013-published-year-plan-visibility/quickstart.md`; giữ nguyên ID/trạng thái/lịch sử task cũ, không tạo mục rỗng khi sạch; gặp thay đổi quyết định/contract ngoài plan thì xử lý theo quyền hiện hành trước patch (sau T021/T022).
- [X] T024 Hoàn tất DoD delta sau khi mọi gap được xử lý, ghi lệnh/native results/review/converge/giới hạn thật vào `docs/quality/VERIFICATION.md` và Progress log `docs/plans/active/2026-11-07-school-year-team-plans.md`, chỉ cập nhật trạng thái delta013 theo evidence; giữ approval/lịch sử012, không Git integration/deploy hoặc đánh xong khi còn fail/skip không được nêu (sau T023 và mọi task append nếu có).

## Coverage Summary

| Yêu cầu | Tasks chính | Nghiệm thu |
| --- | --- | --- |
| FR-001 | T003/T004/T007/T008/T009/T013/T014/T012/T015/T021 | AC01/03/08–11; Q01–Q03/Q05–Q07 |
| FR-002 | T003/T004/T006/T008–T011/T013/T014/T012/T021 | AC01/02/08–10; Q01/Q02/Q06/Q07/Q10/Q11 |
| FR-003 | T003/T004/T005/T007/T009/T013/T012/T015/T021 | AC05; Q05 |
| FR-004 | T004/T005/T006/T010/T013/T014/T015/T021 | AC04; Q04 |
| FR-005 | T016/T017/T018/T019/T020 | AC06/07; Q08/Q09/Q12 |
| FR-006 | T016/T017/T019/T020 | AC06; Q08/Q12 |
| FR-007 | T018/T019/T020 | AC07; Q09 |
| SC-001 | T003/T004/T006/T012/T015/T021 | Quyền đọc năm chưa ban hành |
| SC-002 | T003–T005/T007–T010/T013/T012/T015/T021 | Đúng tuổi/user/phân công, Q1=A |
| SC-003 | T016/T017/T019/T020 | Đủ50×>=2, không card scroll |
| SC-004 | T016/T018/T019/T020 | Đủ nhãn/cây/chữ/ticks, lưu/mở lại |

### Acceptance coverage sau remediation

| AC | Tasks | Ca kiểm chứng |
| --- | --- | --- |
| AC-01–03 | T004/T006–T010/T012/T021 | Q01–Q03 |
| AC-04 | T004/T006/T010/T013–T015/T021 | Q04 |
| AC-05 | T003–T005/T007/T009/T013/T021 | Q05 |
| AC-06 | T016/T017/T019/T020 | Q08/Q12 |
| AC-07 | T018–T020 | Q09 |
| AC-08–09 | T003–T010/T013/T014/T021 | Q02/Q06 |
| AC-10 | T003–T005/T007/T009/T013/T021 | Q07 |
| AC-11 | T003–T005/T007/T009/T013/T021 | Q06/Q07, US1.10: HT/PHT hiệu lực mọi tuổi, TT/GV đúng tuổi; giữ status và Q1=A |

## Dependencies & Execution Order

### Phase dependencies

- Setup T001 → Foundational T002 → source tasks. T002 là gate, không tự đồng nghĩa approved khi có file tasks.
- US1 test tranche T003/T004/T005/T006 có thể song song sau T002; test/code nằm file khác nhau. BE: T003→T007, T004+T007→T008→T009→T010→T011→T012 (review).
- FE US1: sau review T012 mới thực hiện T013/T014; T005→T013, T006+T013→T014; T013+T014+T012→T015.
- US2 là độc lập về dữ liệu/hành vi với US1, nhưng delivery mặc định P1 trước P2: T015→T016/T018; T016→T017; T017+T018→T019→T020. Có thể chuẩn bị test US2 sau T002 nếu tách người/file và không vượt gate review; không tự thay thứ tự source delivery đã chốt.
- T015+T020→T021→T022→T023→T024. Nếu converge có gap, append task mới và hoàn thành trước T024.

### User story dependencies

US1 có thể phát hành MVP mà không cần layout US2. US2 có thể kiểm chứng riêng trên TEAM hợp lệ với scope cũ; việc thực thi tuần tự US1→US2 là thứ tự ưu tiên/review, không phải yêu cầu tạo dữ liệu mới hoặc phụ thuộc logic.

### Parallel opportunities & examples

- US1: sau T002, T003 (policy test), T004 (integration test), T005 (mock test) và T006 (API/list test) dùng bốn nhóm file khác nhau, có thể chia làm tối đa bốn lượt làm việc độc lập nếu được điều phối; cùng fixture convention nhưng không cùng sửa file. Chạy native test processes tuần tự nếu tranh runtime/database.
- US2: T016 (layout test) và T018 (tick/label regression) làm song song khi tiền nhiệm đến lượt xong; T017/T019 đợi test đầu vào. T016/T018 không sửa CSS/source repository của nhau.
- Không đánh [P] cho nhiệm vụ source có dependency chưa hoàn tất hoặc cùng sửa EduYearPlanServiceImpl/verification log. [P] không phải quyền tự tạo nhánh, chat hoặc subagent.

## Implementation Strategy

1. Tạo baseline và hoàn tất analyze/approval trước source. Tái sử dụng stack, fixture/test hiện có, không chạy script setup/harness.
2. MVP là US1: BE scope/query/read surfaces → native regression/review → FE mock/selector → regression. Kiểm thật SC-001/002 tại phase013.3, không dùng mock để claim server đạt.
3. US2 thay CSS có scope, giữ renderer/tick nếu đã đúng, kiểm browser thật; hoàn thành review013.2 rồi nghiệm thu tích hợp.
4. Đồng bộ tri thức, review/converge delta theo evidence, append gap khi cần và bàn giao. Không tự claim toàn feature012 sạch.

## Generation Validation

24 tasks: setup1, foundation1, US1=13, US2=5, polish4. Tất cả chưa thực hiện; ID T001–T024 tuần tự, story labels/path đầy đủ. Bốn task test US1 và hai task test US2 có cơ hội song song theo điều kiện nêu trên. Mọi FR-001–007/SC-001–004 và AC-01–11/Q01–Q12 đều có task truy vết.

Đọc trực tiếp template `.specify/templates/tasks-template.md`, không có override/preset; `.specify/extensions.yml` không tồn tại lúc tạo, không chạy before/after hooks hoặc setup-tasks theo chỉ đạo AGENTS.md. Cần speckit-analyze đọc chéo sau lượt tạo này; generation validation chỉ chứng minh cấu trúc/truy vết tài liệu, không phải analyze hoặc test chức năng.

### Regeneration log — 08/11/2026

Rà soát lại theo user gọi speckit-tasks sau remediation: giữ T001–T024 và mọi checkbox, không tạo task trùng. Làm rõ T003/T007 về nhánh quyền năm độc lập so với APPROVED toàn trường của HT/PHT tạo TEAM; T009/T013/T021 bao phủ AC-11 và kiêm nhiệm/hết phân công quản lý. Thêm mapping AC-01–11, explicit review dependencies cho T013/T014. T020 và quickstart đã bao gồm cả hai test T018 còn thiếu; không cần task mới. US1=13/US2=5/chung6,6 cơ hội song song giữ nguyên; MVP vẫn US1. Kiểm cấu trúc/truy vết không thay analyze hoặc test ứng dụng.

## Phase 6: UI-first delta — user08/11/2026

Theo chỉ đạo mới, thực hiện phase này trước US1; giữ task cũ/ID. User đã yêu cầu triển khai cả vòng tới sạch; quyền FE đã cấp, không dừng xin lại approval từng phase. Review/verify nội bộ giữ đủ, source vẫn giới hạn feature013.

- [X] T025 [US2] Thêm regression bảng TEAM thật cho cột nhánh/select mục tiêu và annual không đổi trong `../CareNest_FE/tests/education-plan/team-plan-ui.test.mjs` theo FR-008–010/AC-12–14, quan sát fail trước implementation.
- [X] T026 [US2] Trong `../CareNest_FE/src/components/education-plan/GoalTables.jsx` giữ cột nhánh cho TEAM và thay MT string bằng một native select có nhãn Xem mục tiêu và chi tiết mục tiêu/nội dung đã tick; readonly table chỉ select thao tác, không đổi annual hoặc data (FR-009/010).
- [X] T027 [US2] Dùng selector hẹp trong `../CareNest_FE/src/styles/modules/education-plan.css` cho header bốn cột nguồn phần2 xám và toàn bảng phần3 xám, select rõ/tương phản; bỏ inner card scroll như T017 (FR-005/006/008/010).
- [X] T028 [US2] Chạy Node regression/ESLint/Vite và headless Chromium với CSS/component thật, xem select bằng bàn phím/chi tiết và style readonly/header/annual/full tree; ghi evidence vào `specs/013-published-year-plan-visibility/quickstart.md` (FR-005–010).
- [X] T029 Đồng bộ rule/plan/knowledge và review UI delta rồi kiểm convergence UI sau implement, ghi kết quả trong `docs/quality/VERIFICATION.md`; gap actionable chỉ append task mới trong `specs/013-published-year-plan-visibility/tasks.md`, không coi UI sạch là cả013 sạch.

## Phase 7: Convergence

- [X] T030 Sửa va chạm tên schema tài liệu trong các DTO giáo dục tại `src/main/java/com/carenest/dto/eduplan/GoalFilePreviewResponse.java`, `EduConfigResponse.java`, `YearPlanChangesResponse.java`, `YearPlanRequest.java`, `YearPlanDetailResponse.java` bằng tên Schema riêng cho nested records trùng; thêm regression cấu trúc ở `src/test/java/com/carenest/contract/OpenApiSnapshotTest.java`, rồi đồng bộ `docs/api/openapi.yaml` từ runtime và kiểm snapshot bình thường, không đổi field JSON/endpoint per plan: contract/snapshot verification, Constitution IV (contradicts).

## Phase 8: Convergence

- [X] T031 Giữ nguyên cách lọc version chain của TEAM trong `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java`: chỉ SCHOOL_YEAR dùng scope mới, TEAM dùng base scope như trước; kiểm regression TEAM cùng test tích hợp liên quan per FR-002, plan: TEAM compatibility (contradicts).

## Phase 9: Convergence

- [X] T032 Giới hạn mock annual reviewer theo chính vai trò được cấu hình trong `../CareNest_FE/src/services/education-plan/mock/educationPlanMockRepository.js`, không cho role quản lý ngoài policy mở REVIEW của vai trò tổ trưởng sang nhóm khác; khóa regression mixed-role trong `../CareNest_FE/tests/education-plan/annual-visibility.test.mjs` per FR-002/003, Q1=A (contradicts).

## Phase 10: Convergence

- [X] T033 Chặn render dữ liệu kế hoạch được giữ lại sau refresh403 trong `../CareNest_FE/src/hooks/education-plan/useEducationPlan.jsx` và kiểm regression `../CareNest_FE/tests/education-plan/provider-access.test.mjs`, giữ behavior lỗi mạng500 hiện có per FR-002, contract: no stale data after denial (partial).

## Phase 11: Convergence

- [X] T034 Xóa retained data khi Provider nhận403 trong `../CareNest_FE/src/hooks/education-plan/useEducationPlan.jsx`, kiểm transition403→retry pending→500→authorized success trong `../CareNest_FE/tests/education-plan/provider-access.test.mjs`, không chỉ che children tạm thời hoặc đổi hook useAsync chung per FR-002, contract: denied source must not reappear (partial).

## Execution summary — 08/11/2026

34/34 tasks hoàn tất trong phạm vi delta; ID/đơn vị công việc cũ giữ nguyên, chỉ chuyển checkbox theo evidence. User yêu cầu UI-first và chạy liên tục đã thay các điểm dừng xin review cũ của T002/T012/T020; vẫn có review độc lập và native checks từng increment, không tự thực hiện Git/deploy.

- FR-001–004 / SC-001–002: policy/query/source/version/diff và provider403 retry, test thựcDB + HTTP stack thật(testJWT), mock/adapter regression. T030–T034 xử lý gap quan sát được, không thêm yêu cầu nghiệp vụ khác.
- FR-005–007 / SC-003–004: cây50 mục tiêu/100 nội dung đủ, không inner card scroll; tick bàn phím→local draft save/reopen. SSR không được dùng thay computed layout.
- FR-008: T025/T027/T028; FR-009: T025/T026/T028; FR-010: T025–T028, AC-12–14. Annual editor và TEAM readonly được kiểm browser/component thật.
- BE native94/94 tests, fail/error/skip0; snapshot bình thường4/4, architecture14/14; scoped Spotless11/11 file sửa đạt. Full-repo Spotless còn lỗi format có trước ngoài delta, không gọi full gate đạt.
- FE native278/278 tests, fail/skip0; ESLint source đổi0errors/0warnings, Vite build đạt; browser1440/390 và bàn phím/local draft đạt. Chưa live Keycloak E2E/server save qua trình duyệt hoặc restart/deploy user API.
- Evidence chi tiết tại quickstart và docs/quality/VERIFICATION.md, log TEMP/carenest013-*.log. Review cuối không còn blocker source trong delta. Convergence cuối chỉ đọc, không append khi không gap.
