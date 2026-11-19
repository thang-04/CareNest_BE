# Tasks: Phân biệt kế hoạch năm học và kế hoạch giáo dục tổ (tách lưu trữ)

> **Quyết định mới user 10/11/2026 — EDU-72:** Bỏ thao tác lập kế hoạch tổ lần đầu. Khi kế hoạch năm được ban hành, hệ thống tự tạo một TEAM DRAFT cùng năm học/nhóm tuổi, gắn phiên bản năm đã ban hành mới nhất và giao tổ trưởng duy nhất đang phụ trách tuổi đó soạn. User làm rõ “chỉ có một tổ trưởng của một nhóm tuổi, gửi cho người đó soạn”. Quyết định này thay phần “không tự lập tổ/giữ nút lập tổ/chọn nguồn thủ công” ở EDU-62 và tài liệu09/11; các quy tắc giữ nội dung đang soạn, rà soát và giữ tổ ban hành cũ làm nguồn lớp vẫn áp dụng. Đây là chỉ đạo user, không suy là xác nhận mới từ giáo viên trường.


**Input**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/api.md](./contracts/api.md), [quickstart.md](./quickstart.md)

**Branch**: `feature/G94-326-BE-FEAT-09-school-year-goals-backup` (không tạo/chuyển nhánh mới)

**Ghi chú định dạng**: `- [ ] T### [P?] [US#] Mô tả + đường dẫn file`. `[P]` = chạy song song được (khác file, không phụ thuộc task chưa xong). Setup/Foundational/Polish không có label [US]. **Chỉ 2 loại hồ sơ: kế hoạch năm học (year) + kế hoạch giáo dục tổ (team)** — không có loại "môn học".

**⚠️ Chốt trước khi code**: T001/T047 duyệt thiết kế/API/DDL và quyền repo theo scope. V96 local đã tồn tại, phải kiểm history trước sửa; không chạy SQL trong lượt tài liệu. T009 production là mốc cuối sau rehearsal/BE/FE, không phải prerequisite để viết US trên scratch.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Cột mốc chuẩn, không init lại project (đã có sẵn).

- [ ] T001 [DUYET] Trình plan làn L, DDL/data-model (legacy NULL, 9 owner, baseline/review, sequence chung, cutover) và contracts/api.md cùng impact FE/APP; ghi bằng chứng phê duyệt theo T047, không coi lệnh fix tài liệu là duyệt code/API/DDL.
- [ ] T002 [P] Xác nhận artifact hiện có đầy đủ và liên kết hợp lệ trong specs/015-separate-year-team-plans/; không tạo lại project hoặc suy đã nghiệm thu.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Chuẩn bị schema đích/code trên scratch; cutover production T009 được hoãn tới sau mọi gate, không chặn việc viết/test US trên scratch.

- [ ] T003 [DUYET] Review/hoàn thiện bản expand src/main/resources/db/migration/V89__separate_team_plans.sql sau T001: 9 bảng owner, header TEAM/review-items/baseline nullable; giữ year_plan_id/plan_level, app cũ tiếp tục là writer duy nhất. Kiểm history trước sửa: đã commit/applied thì migration mới. Ghi nhận lịch sử task cũ nói “V96 đã tạo + validate DB scratch V93”; đây là evidence cũ chưa chạy lại, không chứng nhận thiết kế sửa.
- [ ] T004 Tạo entity `src/main/java/com/carenest/entity/eduplan/EduTeamPlan.java` (cột workflow/lock/approvals/print_snapshot theo data-model §1.2, self-FK `source_plan_id`)
- [ ] T005 Sửa entity src/main/java/com/carenest/entity/eduplan/EduYearPlan.java cho schema đích chỉ năm; giữ version/self-source/approvals/lease; cấu hình ID theo sequence chung cùng EduTeamPlan. Chỉ chạy code mới với schema đích scratch hoặc cutover đồng bộ, không dùng placeholder ownership trên header.
- [ ] T006 Sửa 9 entity owner trong src/main/java/com/carenest/entity/eduplan/: EduPlanGoal, EduPlanSubDomain, EduPlanExpectedResult, EduTheme, EduBreak, EduPlanEvent, EduDocumentAuthor, EduPlanBranchTemplate, EduClassDoc. EduGoalRow mapped superclass chỉ sửa nếu mapping kế thừa cần; không EduPlanApproval/bảng approvals riêng. goal_contents/ticks suy owner qua cha; test không cross-owner; authors class_doc_id giữ owner header NULL, CHECK chọn đúng một trong ba target và unique tương ứng (V87); tái tạo FK/unique ghép cây/theme/templates V35/V91.
- [ ] T007 Tạo repository/eduplan/EduTeamPlanRepository.java và cập nhật EduYearPlanRepository + repository 9 owner theo data-model §2; unique/version theo bảng riêng, truy vấn scope đúng; bỏ truy vấn planLevel ở code schema đích, không query cột đã drop.
- [ ] T008 Tách service: tạo interface `src/main/java/com/carenest/service/eduplan/EduTeamPlanService.java` + `impl/EduTeamPlanServiceImpl.java` (hành vi tổ: lập/soạn/submit/update, dùng `EduTeamPlan`); sửa `impl/EduYearPlanServiceImpl.java` chỉ giữ hành vi năm; dispatch toàn bộ detail/PUT/DELETE/actions/lease/version/copy ở controller theo ID chung, TEAM DELETE luôn chặn server (FR-016), không hard-code role thay policy (ADR-0014, giữ log `LOG_PREFIX`)
- [ ] T009 [DUYET] Review và thực hiện cutover production V97 (hoặc version mới theo history) CHỈ sau T001/T030/T031/T034/T035/T037/T047: maintenance dừng writer/job, backup/restore rehearsal, refresh owner/copy TEAM giữ ID, sequence chung, kiểm trước xóa, chuyển mọi FK rồi contract theo data-model §2; kiểm sau chuyển trước mở app mới. Candidate SQL được chuẩn bị/test bởi T030 trên scratch; không tạo cutover sau khi đã ship.
- [ ] T010 Unit/integration test src/test/java/com/carenest/service/eduplan/impl/: tách năm/tổ, unique một bản mở/chuỗi, owner/coauthor không tự duyệt, giữ workflow/lease/revision/print_snapshot và trạng thái REVIEW cấm sửa (FR-012). Chạy trước hoàn thiện implementation tương ứng; test guard TT riêng T039; FR-016 kiểm DELETE TEAM bị chặn cho owner/HT/PHT/TT ở DRAFT/REVIEW/APPROVED, không đổi children; DELETE annual giữ EDU-07.

**Checkpoint**: Schema đích rehearsal và code nền tảng sẵn sàng trên scratch; T009 là mốc production cuối, không phải prerequisite để viết/test các US.

---

## Phase 3: User Story 1 - Nhận đúng loại + tên hồ sơ chờ duyệt (Priority: P1) 🎯

**Goal**: HT/PHT nhận đúng loại (năm/tổ), đúng tên riêng, mở đúng hồ sơ (FR-001); view-scope tổ trưởng/GV chỉ đúng tuổi phân công (FR-015/EDU-60).

**Independent Test**: `GET /education-plans/goals?planType=SCHOOL_YEAR` và `=TEAM` — mỗi row đúng `planType`/`planTypeLabel`, `title` là tên riêng, `GET /{planId}` mở đúng bản; người ngoài quyền → 403; tổ trưởng tuổi A không thấy bản tuổi B dù có trong DB.

### Implementation for US1
- [ ] T011 [P] [US1] Thêm `planType` (enum SCHOOL_YEAR/TEAM) + `planTypeLabel` (tiếng Việt) vào `src/main/java/com/carenest/dto/eduplan/YearPlanSummaryResponse.java` và `YearPlanDetailResponse.java` (suy từ **bảng vật lý**, không từ `title` — R-3)
- [ ] T012 [US1] Thêm tham số lọc `planType` vào `GET` trong `src/main/java/com/carenest/controller/eduplan/EduYearPlanController.java` + service (giữ `planLevel` 1 giai đoạn transition cho FE chưa đổi)
- [ ] T013 [US1] Sửa logic danh sách "chờ duyệt" (retrieval theo `status=REVIEW`) hiển thị đúng loại + tên riêng, không suy loại từ tên (FR-001) — cập nhật trong `impl/EduYearPlanServiceImpl.getYearPlans` (và phần team trong `EduTeamPlanServiceImpl`)
- [ ] T014 [P] [US1] Test unit/integration US1: đúng loại/nên + 403 (SC-001) — `src/test/java/com/carenest/service/eduplan/`
- [ ] T015 [US1] Apply view-scope FR-015/EDU-60 trên list/detail/source/version/chờ duyệt trong EduYearPlanServiceImpl/EduTeamPlanServiceImpl dùng EduPlanAccessPolicy.ViewScope; TT/GV đúng tuổi, kiêm quản lý hiệu lực mới mọi tuổi, mất phân công đổi scope ngay. Làm tuần tự với T013 do cùng service; regression hai tài khoản khác tuổi trong T014.

**Checkpoint**: BE US1 có evidence; chỉ hoàn thành UI khi T044/T046 đạt, TT approve/return theo T039.

---

## Phase 4: User Story 2 - Lập tổ trên trang soạn, 1 nguồn (Priority: P1)

**Goal**: Lập tổ vào thẳng trang soạn (không modal), nhập tên riêng + chọn 1 nguồn năm đã ban hành (FR-002/003/004).

**Independent Test**: Lập tổ từ nguồn hợp lệ, tên khác năm, tick 1 nội dung, lưu/mở lại; list tổ đủ 5 cột; không liên kết nhiều nguồn; chưa gửi → ngày "—".

### Implementation for US2
- [ ] T016 [P] [US2] Thêm `planType` + giữ `curriculumSourcePlanId` vào `src/main/java/com/carenest/dto/eduplan/YearPlanRequest.java`; server chặn nguồn không phải bản năm ban hành hiện hành/cùng năm-tuổi/trong quyền (R-7, `requireCurriculumSource` trong `impl/EduTeamPlanServiceImpl`)
- [ ] T017 [US2] POST /education-plans/goals planType=TEAM tạo tổ khi người dùng lưu form, không tạo row khi chỉ điều hướng. Server chỉ nhận một nguồn hợp lệ (FR-002/003); không tự lập lần đầu khi năm ban hành. UI trực tiếp là T043, không coi controller mở được trang FE.
- [ ] T018 [US2] List tổ đủ 5 cột (FR-006): `title`, ageGroup label, `sentAt` (chưa gửi = null), owner name/title, `status` + action theo quyền — `dto/eduplan/YearPlanSummaryResponse` + mapper
- [ ] T019 [US2] Test BE tại src/test/java/com/carenest/service/eduplan/: tên độc lập, nguồn không hợp lệ/bị thay thế/mất quyền, lưu/mở lại tick, sentAt/sentBy.at khớp và null khi chưa gửi (SC-003, phần response SC-004); SC-002/UI SC-004 kiểm bởi T046, không gán service test chứng minh số lần bấm.

**Checkpoint**: BE US2 có evidence; luồng UI chỉ hoàn thành khi T043/T046 đạt và T038 bảo vệ cây nguồn.

---

## Phase 5: User Story 3 - Rà soát tổ khi năm ban hành bản mới (Priority: P1)

**Goal**: Bản team-update nháp tự sinh hoặc bản mở được reuse khi năm ban hành (D2), "phần đỏ" so nguồn cũ, bản ban hành cũ + lớp giữ nguyên (FR-008/009/013/014).

**Independent Test**: Ban hành năm mới → server sinh team v2 nháp trỏ năm mới; nội dung đổi/loại bỏ → item đỏ (chủ đề đã kết thúc không đỏ); resolve + submit; team ban hành cũ vẫn là nguồn lớp.

### Tests for US3 (viết trước, fail trước)
- [ ] T020 [US3] Viết integration regression trước implementation tại src/test/java/com/carenest/service/eduplan/impl/EduTeamUpdateGeneratorTest: ban hành/cập nhật tổ cùng transaction, không có tổ không tạo, có bản mở reuse, REVIEW về DRAFT (FR-008/012/013). T042 thêm rollback/replay/concurrent.

### Implementation for US3
- [ ] T021 Tạo EduTeamReviewItem entity/repository và mapping previous_source_plan_id trên EduTeamPlan theo data-model §1.2/§3; DDL trong expand T003 hoặc migration mới nếu đã commit/applied. Chỉ một bảng review-items, không JSONB review song song; phân biệt reviewItemId/itemId.
- [ ] T022 Tạo service/eduplan/impl/EduPlanReviewDiff.java: so hai nguồn năm baseline/new theo lineage, đổi/xóa/liên kết ảnh hưởng, miễn chủ đề đã kết thúc, mục mới nhãn riêng; nhiều lần chuyển hợp các unresolved vẫn sai, không tự mất tiến độ (FR-009/014); regression T028/T041.
- [ ] T023 Tạo service/eduplan/impl/EduTeamUpdateGenerator.java đồng bộ trong transaction duyệt cuối năm, thay đề xuất AFTER_COMMIT cũ; khóa chuỗi ổn định rồi team ID tăng dần, reuse bản mở trước, chỉ copy APPROVED thành n+1 khi không có bản mở, không chuỗi thì không lập. Lỗi rollback cả lần ban hành (data-model §4, FR-008/012/013); test T020/T042.
- [ ] T024 [US3] Tích hợp generator trong EduYearPlanServiceImpl và EduTeamPlanServiceImpl: năm mới chuyển nguồn bản mở, lưu previous_source_plan_id, giữ phần nhập/review hợp lệ, REVIEW về DRAFT với vòng duyệt mới khi gửi lại; không ghi đè tổ APPROVED/class docs (FR-008/013). Cùng transaction T023, không listener riêng.
- [ ] T025 [US3] Chặn submit nếu unresolved hoặc tham chiếu chưa hợp lệ; POST /{teamPlanId}/review/{reviewItemId}/resolve tại EduYearPlanController kiểm DRAFT/policy/lease/revision/requestId/expectedSourcePlanId và ownership item, không nhận flag resolve để bỏ qua lỗi (FR-009); T041 regression.
- [ ] T026 [US3] YearPlanDetailResponse + mapper: reviewItems với reviewItemId khác itemId/sourcePlanId; curriculumSourcePlanId/previousSourcePlanId/contentRevision/newItems theo contracts §4, baseline lưu bền trong header tổ, newItems tính từ nguồn/lineage (FR-009/014).
- [ ] T027 [US3] Versions/changes/create-version chỉ SCHOOL_YEAR; TEAM trả conflict nhất quán tại controller/service (FR-007). Giữ lịch sử nội bộ/nguồn lớp; ẩn UI thực hiện T044, không coi chặn GET thay được UI.
- [ ] T028 [P] [US3] Test US3: tự sinh update, phần đỏ, chủ đề đã kết thúc không đỏ, bản chờ về nháp (SC-005) — `src/test/java/com/carenest/service/eduplan/`

**Checkpoint**: US3 xong — năm đổi nguồn tổ tự cập nhật + rà soát đúng quy tắc.

---

## Phase 6: User Story 4 - Tách dữ liệu + bảo toàn hồ sơ (Priority: P1)

**Goal**: Hai bảng riêng, chuyển đổi giữ dữ liệu legacy, không tạo năm giả (FR-011/SC-006).

**Independent Test**: Trên data tổng hợp có team legacy không nguồn: chạy V96/V97, đối chiếu trước/sau (tên, tick, lineage, snapshot, approvals, class docs) giữ nguyên.

### Implementation for US4
- [ ] T029 [US4] TRƯỚC T009: inventory và hoàn thiện backfill trong expand/candidate cutover theo data-model §2; legacy nguồn NULL, không năm giả, giữ mọi payload/ID/owner/lineage/class docs/approvals/print_snapshot. Sau commit/applied không sửa V96, dùng migration mới; bộ seed/snapshot tổng hợp đặt src/test/resources/eduplan/.
- [ ] T030 [US4] TRƯỚC T009: chuẩn bị candidate contract migration V97 (hoặc slot mới) và DB integration tests trong src/test/java/com/carenest/service/eduplan/; rehearsal V96→V97 trên scratch có dữ liệu legacy/nhiều version/ghi sau expand. Kiểm FK/unique/sequence/không cascade mất dữ liệu, toàn payload EXCEPT hai chiều trước xóa và sau cutover, lỗi rollback/restore; không chỉnh migration đã commit/applied.
- [ ] T031 [US4] TRƯỚC T009: chạy quickstart §0/§4 trên DB scratch với candidate SQL T030; snapshot 9 owner/cây/ticks/docs, payload/checksum và ID hai chiều, gồm authors class_doc_id không mất qua JOIN và FK ghép cây/theme/templates không cross-owner. Ghi lệnh/kết quả thật trong quickstart, fail/skip chặn cutover, không lấy evidence V96 cũ làm pass.
- [ ] T032 [P] [US4] Cập nhật `docs/database/DATA_DICTIONARY.md` + `docs/database/ERD.md` (bảng mới `edu_team_plans`, `edu_team_review_items`, 2 cột sở hữu, bỏ `plan_level`) theo rule `.claude/rules/database.md`

**Checkpoint**: US4 xong — tách DB + bảo toàn dữ liệu đạt SC-006.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T033 [P] Đánh giá tác động `CareNest_FE` (không sửa repo FE): `GoalTables.jsx`, `planUi.jsx`, `YearPlanSourceCard.jsx`, list chờ duyệt — đọc `planTypeLabel`, ẩn bảng phiên bản khi `planType=TEAM`, trang lập tổ trực tiếp (contracts/api.md §Impact)
- [ ] T034 [P] Kiểm tra `CareNest_APP` qua `docs/system/CROSS_REPO_MAP.md` (chỉ khi có màn năm/tổ) — ghi nhận impact, không tự sửa
- [ ] T035 TRƯỚC T009: chạy quickstart đầy đủ US1–US4 trên schema đích scratch + FE được phép; T038–T046 và T048–T050 đã xong, APP impact T034 đã rõ; ghi fail/skip thật, không kết luận SC-002/004 đạt từ BE-only. Sau T009 kiểm smoke/bảo toàn theo §4 rồi mới mở writer.
- [ ] T036 [P] Log theo quy ước `LOG_PREFIX` (ADR-0016) cho các service mới (`EduTeamPlanServiceImpl`, `EduTeamUpdateGenerator`, `EduPlanReviewDiff`)
- [ ] T037 TRƯỚC T009: chạy Maven suites eduplan/migration/architecture/API snapshot phù hợp bằng mvnw.cmd test chọn lọc + spotless:check; cập nhật docs/api/openapi.yaml cùng implementation contract và kiểm snapshot; FE test/lint/build trong T046. Ghi lệnh/kết quả thật; không harness/commit/push.

---

## Phase 8: Bổ sung sau analyze — giữ ID/task cũ

Các task sau là kế hoạch, chưa thực thi. T047 phê duyệt phạm vi phải đi trước code; đánh số không quyết định thứ tự chạy.

- [ ] T038 [US2] Server regression FR-004 tại InheritedCurriculumGuard/EduTeamPlanServiceImpl/controller file-preview và src/test/java/com/carenest/service/eduplan/: chặn request giả/import thêm/sửa/xóa cây/nhãn nguồn, giữ tick từng nội dung hợp lệ, không tự tick cả MT hoặc đổi annual; bảo toàn quyền preview annual (EDU-55).
- [ ] T039 [US1] Controller EduYearPlanController issue/return + EduPlanAccessPolicy/service: FR-017 cho TT role TEACHER đúng policy/tuổi/lượt; tests allow/deny tuổi khác/mất phân công/sai lượt/owner/coauthor/trả lại thiếu lý do, REVIEW không sửa; không mở quyền module khác (EDU-54).
- [ ] T040 [US4] Migration candidate + entity/repository ID trong eduplan: shared sequence cả header theo data-model §1.3; test giữ ID cũ, create/copy năm+tổ đồng thời/restart không trùng, default/JPA dùng cùng sequence; inventory trùng phải abort, numeric detail/actions không chọn sai bảng (FR-010/011/012).
- [ ] T041 [US3] Regression src/test/java/com/carenest/service/eduplan/impl/: reviewItemId/itemId khác nhau, item khác tổ, nguồn/revision/lease cũ, resolve khi mapping còn lỗi, source removed cần remap/gỡ tick, reload baseline/tiến độ, nhiều lần nguồn đổi giữ lỗi chưa xử lý, chỉ đánh lại số không đỏ, mục mới riêng, chủ đề kết thúc không đỏ (FR-009/014).
- [ ] T042 [US3] Regression EduTeamUpdateGeneratorTest: lỗi giữa copy/diff/lưu rollback cả ban hành năm và tổ; lặp/concurrent ban hành+lập tổ không hai bản mở; có DRAFT/REVIEW thì reuse, chỉ APPROVED thì n+1, chưa có tổ không tự lập; năm v3 không bỏ tiến độ v2 chưa xử lý (FR-008/012/013).
- [ ] T043 [US2] [FE-GATE T047] CareNest_FE/src/pages/education-plan/GoalListPage.jsx + GoalFormPage.jsx + YearPlanSourceCard.jsx và services/education-plan/api/: nút lập mở form trực tiếp, tên riêng/chọn một nguồn hợp lệ, không POST khi chỉ mở form; giữ input khi nguồn lỗi; cây readonly/tick từng nội dung (FR-002/003/004/005, SC-002/003).
- [ ] T044 [US1] [FE-GATE T047] CareNest_FE GoalListPage/GoalDetailPage/PlanReviewQueuePage + planUi/VersionWidgets/VersionHistory + yearPlanMapper: đúng loại/tên/link; list 5 cột, sentAt tương thích sentBy.at/chưa gửi “—”; ẩn versions/create/compare/delete TEAM, action duyệt TT theo quyền server (FR-001/006/007/016/017, SC-001/004). Tuần tự T043 do cùng GoalListPage.
- [ ] T045 [US3] [FE-GATE T047] CareNest_FE GoalDetailPage/GoalFormPage + GoalTables + educationPlanApi/yearPlanMapper: reviewItems/newItems, lý do chữ kèm màu, tick/remap rồi resolve với nguồn/revision/lease hiện tại, conflict giữ phần nhập và tải lại trạng thái, lưu/mở lại giữ tiến độ; không submit khi còn lỗi (FR-008/009/013/014, SC-005). Tuần tự T043/T044 do trùng file.
- [ ] T046 [FE-GATE T047] Tests education-plan hiện có trong CareNest_FE/tests và browser E2E: SC-001..005 với API thật/seed tổng hợp, một lần bấm/no modal, 5 cột/ngày, nguồn readonly/kể cả hàng chưa tick, ẩn phiên bản/xóa tổ, nhãn review/new-items/reload/conflict/giữ nguồn lớp; native test/lint/build đúng file, không coi mock/unit thay E2E. Ghi lệnh/kết quả, thiếu credentials hoặc skip phải báo chưa đạt.
- [ ] T047 [DUYET] Ghi nhận trong plan/quickstart bằng chứng user duyệt plan làn L/API/DDL/cutover và quyền sửa repo FE; T034 xác định APP impact, nếu có consumer phải append task/quyền tương ứng trước cutover. Lệnh “fix” hiện tại chỉ duyệt sửa artifact; không xin lại quyền đã có bằng chứng phù hợp.

- [ ] T048 [US4] Chuyển consumer hồ sơ lớp trong src/main/java/com/carenest/service/eduplan/impl/EduClassDocServiceImpl.java: requireIssuedPlan/isSuperseded, batch LoadContext/plans, source-copy/expectedKey, khóa header và caller còn dùng EduYearPlanRepository/EduYearPlanService cho nguồn tổ sang TEAM/service/repository hiện có; cập nhật truy vấn theo owner tương ứng. Rà EduLessonQueryServiceImpl/caller observation chỉ đổi đường còn phụ thuộc header chung. Giữ API yearPlanId/docKey/ID lớp và lịch sử; test đọc/in/copy hồ sơ cũ, tạo/preview/submit hồ sơ mới chỉ từ TEAM APPROVED hiện hành đúng lớp-năm-tuổi; năm mới ban hành chưa làm tổ cũ superseded, tổ cập nhật duyệt mới thay nguồn; nguồn giáo án/MT/lineage đánh giá vẫn đúng (FR-007/008/011, SC-005/006; EDU-12/19/26/62). Cần T006/T007/T008 và schema đích T030; trước T035/T009.
- [ ] T049 [US3] EduTeamUpdateGenerator/Team repository + EduTeamUpdateGeneratorTest chọn tổ liên quan bằng FK curriculum_source_plan_id thuộc chuỗi annual, không đoán từ tuổi/tên. Fixture legacy nguồn NULL (cả bản mở và ban hành): năm mới ban hành vẫn giữ legacy/nguồn/baseline/nội dung/mapping/trạng thái/nguồn lớp, không tạo update hoặc review giả; không gán năm hoặc mở luồng liên kết legacy mới. Tổ có nguồn hợp lệ cùng tuổi vẫn cập nhật đúng; ban hành lặp không làm thay legacy (FR-011/013, SC-006; data-model §4).
- [ ] T050 [US3] Khi generator thực sự chuyển nguồn bản tổ đang mở: tăng content_revision và vô hiệu edit_session_id/editing lease nguyên tử, giữ sổ tác giả/lịch sử; PUT/submit/resolve/heartbeat/finish-editing phiên/revision cũ conflict, không replay save-cache bỏ qua revision. FE GoalFormPage/GoalDetailPage và API error flow (quyền FE theo T047) giữ input để đối chiếu, đọc detail và lấy lease mới trước ghi, không tự gửi lại payload cũ. Test race save với chuyển nguồn, form/heartbeat cũ, acquire phiên mới, source replay không tăng revision/hủy lease lần hai; generator lỗi rollback cả nguồn/revision/lease (FR-009/012/013; T041/T042/T045/T046, data-model §4).

## Dependencies & Execution Order

1. T001/T002/T033/T034/T047: thiết kế, impact và phê duyệt trước implementation trong phạm vi tương ứng.
2. T003 → T029 → T030/T040: expand, candidate contract và rehearsal bảo toàn/sequence trên scratch. Phần DDL review-items T021 thuộc T003, entity T021 sau T004.
3. T004/T005 → T006/T007 → T008/T010; T006/T007 không giả định song song nếu chung mapping/repository. Test tương ứng viết trước implementation hoàn thiện.
4. T011 → T012 → T013 → T015; T014/T039 kiểm US1. T016 → T017 → T018 → T019/T038 kiểm US2. Code/tests chạy schema đích scratch, không ship giữa expand và contract.
5. T020 (test trước) → T021/T022 → T023 → T024/T025/T026/T027 → T028/T041/T042; T023 cần có T008 và schema T030; T049 lọc legacy và T050 vô hiệu phiên là phần hoàn thiện generator T023/T024, tests tương ứng viết trước. T048 sau T006/T007/T008 và schema T030, trước T035/T009. Tests cùng suite/file thực hiện tuần tự, không ép [P].
6. Sau gate FE và contract: T043 → T044 → T045/T050 (phần FE) → T046; T049/T050 phần BE hoàn thiện trước T042/T035; T033 chỉ impact không thay các task này. APP impact nếu có phải được xử lý trước production.
7. T031/T032/T036 → T035 → T037 trước T009. T031 rehearsal bảo toàn; T035 full scratch E2E; T037 native tests/API snapshot, không phải yêu cầu production đã cutover.
8. T009 production cuối cùng, kiểm smoke/bảo toàn sau chuyển trước mở writers theo quickstart §4. Không có task sửa V96/V97 sau đã commit/applied.

## Parallel opportunities

Chỉ song song việc thực sự khác file và đủ prerequisite: impact FE/APP T033/T034, cập nhật DB docs T032 sau chốt model, hoặc test suites độc lập sau fixtures. Không chạy các task service/controller/FE page trùng file song song. Marker [P] ở task cũ không vượt dependency ở đây.

## MVP scope

US1+US2 là demo increment trên scratch có FE, chưa hoàn thành feature hoặc quyền production. Full feature cần US1–US4/SC-001..006 + FR-016/017, T038–T050 và T009 đã kiểm chứng. Không bỏ cutover khỏi định nghĩa hoàn thành tách bảng.

## Coverage — Rule → Requirement → Task → Check

| Rule/context | Requirement | Task triển khai/kiểm chứng | Quickstart |
|---|---|---|---|
| EDU-62 tên/loại | FR-001, SC-001 | T011–T014, T044, T046 | §1 |
| EDU-62 lập lần đầu | FR-002, SC-002 | T017, T043, T046 | §2 |
| EDU-55/60 nguồn hợp lệ | FR-003, SC-003 | T016/T019, T043/T046 | §2 |
| EDU-55 cây/tick | FR-004 | T008/T038, T043/T046 | §2 |
| EDU-62 tên độc lập | FR-005 | T004/T019, T043/T046 | §2 |
| EDU-62 list | FR-006 | T018/T019, T044/T046 | §1/§2 |
| EDU-25/62 lịch sử/UI | FR-007, SC-004 | T027/T048, T044/T046 | §2/§3 |
| EDU-26/62 nguồn lớp | FR-008 | T023/T024/T028/T042/T048, T045/T046 | §3 |
| EDU-62 review | FR-009, SC-005 | T021/T022/T025/T026/T028/T041/T048/T050, T045/T046 | §3 |
| EDU-62 tách bảng | FR-010 | T003–T009/T030/T040 | §0/§4 |
| EDU-55/62 legacy | FR-011, SC-006 | T029/T030/T031/T040/T048/T049 | §0/§4 |
| EDU-01/10/54 một chuỗi/quyền | FR-012 | T007/T010/T015/T039/T042/T050 | §1/§3/§4 |
| EDU-62 chuyển bản mở | FR-013 | T020/T023/T024/T028/T042/T049/T050, T045/T046 | §3 |
| EDU-26/62 baseline/kết thúc | FR-014 | T022/T026/T028/T041, T045/T046 | §3 |
| EDU-60 tuổi/phân công | FR-015 | T014/T015/T039 | §1 |
| EDU-07/57 cấm xóa tổ | FR-016 | T008/T044/T046, test T010 mở rộng mọi role/trạng thái | §1/§4 |
| EDU-54 TT duyệt/trả lại | FR-017 | T039/T044/T046 | §1 |
| EDU-63 nguồn/chủ nhiệm | FR-018, SC-007 | T051/T052/T054 | §5 |
| EDU-64 trang/lọc/duyệt chung | FR-020, SC-009 | T051–T054 | §5 |

## Notes và bằng chứng

- T001/T047 phê duyệt thiết kế/DDL/API/quyền repo trước thực thi theo scope; không tự commit/push/PR/deploy.
- Giữ 37 ID task cũ, không đánh hoàn thành từ việc sửa tài liệu; bổ sung T038–T047. Các mô tả cũ được tinh chỉnh theo user “fix”, không phải lượt converge tự viết lại lịch sử.
- Không sửa migration đã commit/applied. V96 local được ghi lịch sử nhưng cần kiểm lại trước triển khai; không khẳng định DB scratch cũ chứng minh thiết kế hiện tại.
- Mọi claim pass phải có bằng chứng mới; task coverage không là implementation/test coverage. [analysis.md](analysis.md) ghi lần đối chiếu tài liệu sau sửa.

- Lần fix sau analyze tiếp theo: giữ nguyên T001–T047 và trạng thái; bổ sung T048–T050 cho consumer hồ sơ lớp, legacy NULL và phiên soạn cũ. Tổng 50 task, không chạy lại speckit-tasks hoặc tái sinh/đổi số danh sách cũ. Analyze chỉ đọc; sửa task trong lượt này được user cho phép bằng “ok fix”.

## Phase 9: Chủ đề / Chủ đề nhánh lớp — rule trực tiếp 09/11

Các task bổ sung này chỉ bắt đầu sau T001/T047 cập nhật phê duyệt phạm vi API/DDL/FE; giữ T001–T050 và lịch sử nguyên vẹn.

- [ ] T051 [US5] Viết BE regression trước tại `src/test/java/com/carenest/service/eduplan/impl/`: annual bị từ chối với GV; cả hai GV lớp đọc đúng dữ liệu lớp; chỉ `is_homeroom=true` ghi nhánh; PHT/TT lọc đúng scope; chủ đề lớn/mẫu TEAM/PHT/lớp khác không đổi khi chủ nhiệm sửa nhánh; nhánh thiếu chỉ chặn gửi hồ sơ chủ đề, không chặn tạo/lưu nháp.
- [ ] T052 [US5] Sửa `EduClassDocServiceImpl`, `EduWorkflowServiceImpl`, repository/policy và DTO/controller liên quan: nguồn lớp chỉ TEAM APPROVED; lấy/chỉ cho ghi nhánh theo chủ nhiệm lớp và chuyển quyền khi đổi chủ nhiệm; chủ đề lớn chỉ đọc ở server; tạo/ghi nhánh lớp từ mẫu TEAM nhưng bảo toàn mẫu và lớp khác; bỏ action/status/workflow/signers riêng cho `branch`; tạo/lưu `theme` không cần branch APPROVED, khi gửi theme kiểm/snapshot nhánh cùng chủ đề; sửa phần chưa đến ngày dạy của hồ sơ APPROVED đưa phần liên quan về DRAFT để duyệt lại, phần đã đến ngày dạy từ chối.
- [ ] T053 [US5] Sửa FE `src/layouts/menuConfig.js`, `src/pages/education-plan/BranchPlanPage.jsx`, `ClassPlanPage.jsx`, `ClassThemePlanPage.jsx`, `src/components/education-plan/BranchCalendarEditor.jsx`, `reviewUi.jsx`, model/mock/API mapper và route: một trang “Chủ đề / Chủ đề nhánh” chỉ bảng Tháng/Chủ đề/Chủ đề nhánh/Tuần/Ghi chú, bỏ card tóm tắt lịch cũ/đổi thứ tự chủ đề và mọi nút/lịch sử gửi duyệt nhánh; PHT lọc mọi tuổi/lớp, TT lọc lớp đúng tuổi, GV chỉ đúng lớp; hai GV xem nhưng chỉ chủ nhiệm có control ghi; chủ đề lớn xám; hồ sơ chủ đề là nơi gửi/duyệt chung.
- [ ] T054 [US5] Thêm BE/FE/E2E regression: hai lớp dùng cùng mẫu PHT nhưng chủ nhiệm lớp A sửa không đổi lớp B/mẫu; giáo viên không chủ nhiệm ghi bị 403; GV không đọc annual/lớp khác; PHT/TT filter đúng scope; nháp theme và nhánh song song; gửi theme kiểm/snapshot nhánh, không còn endpoint/UI gửi nhánh riêng; sửa phần chưa đến ngày dạy chỉ đưa theme chứa phần sửa về DRAFT, phần đã đến ngày dạy bị từ chối; giáo viên gửi thủ công, nhắc tổ trưởng từ 7 ngày trước ngày bắt đầu và vẫn duyệt sớm; kiểm bảng duy nhất và không có khối tóm tắt ảnh 2. Chạy native Maven test chọn lọc và `npm run test:edu`, lint, build theo thay đổi.
