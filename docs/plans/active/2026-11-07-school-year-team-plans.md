---
title: Tách Kế hoạch năm học và Kế hoạch tổ
status: approved
owner: người thực hiện theo phân công của user
jira: G94-326 / BE-FEAT-09 (lấy từ nhánh hiện tại, chưa xác nhận task riêng cho delta)
branch: feature/G94-326-BE-FEAT-09-school-year-goals-backup
modules: [education-plan, learning-observation]
rules: [EDU-01, EDU-12, EDU-13, EDU-17, EDU-19, EDU-21, EDU-22, EDU-23, EDU-25, EDU-26, EDU-27, EDU-39, EDU-40, EDU-41, EDU-50, EDU-54, AUTH-23]
risk: high
created: 2026-11-07
updated: 2026-11-08
delta_013_status: done
delta_014_status: in-progress
---
# Kế hoạch năm học / Kế hoạch tổ — plan làn L

## Delta feature014 — gợi ý phân cấp từ Bộ, 08/11/2026

**Trạng thái riêng: IN-PROGRESS.** User08/11 yêu cầu vòng implement→converge đến sạch: phê duyệt triển khai014/FE và tiếp tục các increment không dừng xin lại; không Git/deploy. Header approved và lịch sử012/013 không cấp approval014. [Plan014](../../../specs/014-curriculum-cascade-suggestions/plan.md) / [research](../../../specs/014-curriculum-cascade-suggestions/research.md) / [contract](../../../specs/014-curriculum-cascade-suggestions/contracts/curriculum-cascade.md). Không tạo plan quản trị trùng chủ đề.

### Context / nghiệp vụ014

Actor có quyền soạn cây năm; [B] xác định nhánh → [S] chọn lĩnh vực/con/nhóm/đích → [S] bấm ô nhập, gõ lọc/chọn gợi ý → [S] thêm và lưu tay. EDU-09/22/28/53/54 CONFIRMED làm nền; EDU-55/60/61 PROPOSED theo nguồn nhưng Q1–Q3 của014 đã do user chốt08/11, không tự nâng thành xác nhận trường/team.

Source Web CurriculumGoalsModal.jsx:26 lấy mọi đích, :29–33 chỉ lọc nguồn, :86–95 confirm chưa giao với danh sách nguồn đang hiển thị; curriculum.mjs:88 thiếu context cha. GoalTables.jsx:135 Suggest mở focus nhưng string-only/mouse; expectedResults.js:58 không strict sub. Tham chiếu path đầy đủ trong research014. Đây là lỗ hổng tĩnh phù hợp phản ánh, chưa RED runtime.

### Quyết định và câu hỏi mở014

| Câu hỏi | Chốt | Nguồn |
| --- | --- | --- |
| Đổi cha làm lựa chọn sai | Bỏ lựa chọn sai, giữ dữ liệu đã thêm | User08/11 Q1 |
| Nội dung theo nhóm lấy ở đâu | Nội dung kèm các mục tiêu thuộc nhóm, mục D; không ghép mục C | User08/11 Q2 |
| Khi ấn nhập | Focus mở ngay, gõ lọc tiếp | User08/11 Q3 |

Không còn câu hỏi nghiệp vụ chặn thiết kế. User đã cấp quyền sửa giáo dục FE014 ngày08/11/2026 sau analyze; không xin lại. User đã duyệt vòng implement→converge plan014; source FE đang triển khai, chưa có quyền APP. Delta catalog/migration có plan riêng chờ duyệt. Mã Jira riêng014 chưa xác nhận, không đoán để commit/PR.

### Acceptance / Key decisions014

- AC01–02: lọc đúng actual local branch, reconcile target/checked, guard trước confirm, giữ contents/ticks.
- AC03–05: focus nhóm/goal/content gợi ý đúng source context; content pool theo nhóm.
- AC06–08: empty/error/stale request, readonly/TEAM, optional parents và IDs cùng tên; xem ma trận Q01–Q12 quickstart014.

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Data contract | Dùng catalog/goals và provenance hiện có | Thêm API/DB relation | BE đã đủ dữ liệu; mục C không có nhóm theo Q2 |
| Hierarchy | Local parent từ flat rows, source ID riêng | Match tên hoặc source ID thay local parent | Không sai nhánh khi tên trùng/chuyển dòng |
| UI source | useCurriculum + Suggest/FloatingPanel | Giữ hardcode hoặc thêm library | Đúng nhóm tuổi/source; giữ stack |
| Hàng nhập tay | Chọn ngữ cảnh nguồn tường minh, state phiên | Đoán relation từ text | Không tự chế nghiệp vụ/nguồn |

### Phases014 và test matrix

Dự kiến >8 file FE kể cả test; chia3 increment review riêng. Không mở BE source/DB/API/dependency hoặc refactor ngoài module.
1. Context/helper/mock source ID normalization+parity/modal/confirm/latest-form guard; curriculum.test.mjs + browser Q01–03/07/09. Exit Node targeted exit0 và browser case đạt; dừng review.
2. Inline group/goal/content, enabled catalog, source context, keyboard/touch; cập nhật fixture Enter/scoped CSS. Exit Node education suite/lint/Vite/browser1440/390 đạt, Q04–11; Q10a tại T020, Q10b sau T021/T024 và nghiệm thu T025; dừng review.
3. Live suite014 mới với SCHOOL_YEAR/creator-reviewer hợp lệ/annual route/save-reload/version/readonly, không coi đổi selector legacy TEAM là đủ; native BE targeted khi cần. Exit Q01–12 và SC001–004 có evidence; skip/blocker báo rõ; converge sau implement, dừng review.

Chi tiết lệnh trong quickstart014. Không verify/check wrapper; yêu cầu wrapper còn trong template/lịch sử plan được chỉ đạo07/11 thay bằng native ở delta này, không xóa bằng chứng lịch sử.

### Rủi ro & rollback014

| Rủi ro | Giảm thiểu | Rollback |
| --- | --- | --- |
| Local/source ID trộn hoặc tên trùng | Context scan + fixtures IDs khác/tên trùng | Đảo đúng delta014 qua diff đã review, không reset checkout |
| Hidden checked/target stale | Reconcile + confirm/latest form guard + browser | Giữ dữ liệu kế hoạch, bỏ delta UI có lỗi |
| Enter/blur/touch tạo hai dòng | Browser keyboard/IME/pointer, reused panel | Đảo phần tương tác014, giữ dữ liệu và test hiện có |
| Consumer APP chưa biết | APP không checkout, không transport delta | Chỉ khảo sát nếu có checkout/quyền riêng |

### Validation / Progress014

- Plan source claims có file:line và consumer Web cụ thể;12FR/4SC/8AC có Q01–Q12; không transport/schema delta.
- User đã duyệt vòng implement→converge014 và cấp quyền sửa giáo dục FE014 ngày08/11/2026; delta catalog/migration có plan riêng chờ duyệt. Research agents readonly, review thiết kế theo làn L; kết quả và kiểm artifact ghi VERIFICATION.
- User08/11 đã yêu cầu sửa I1, phân tích lại, rồi duyệt implement→converge đến sạch. Source FE đã triển khai một phần và có test thật; delta catalog/migration phát hiện sau đó có plan riêng chờ duyệt. Không commit/push/deploy.



### Approval triển khai014 — 08/11/2026

- Nguồn user: “dùng vòng lặp speckit-implement speckit-converge đến khi sạch”. Duyệt plan014, giữ quyền FE đã cấp; tự kiểm/review từng increment và tiếp tục vòng lặp. Không cấp thêm API/DB/dependency/Git/deploy.

## Delta feature013 — quyền xem năm và mục tiêu tổ, 08/11/2026

**Trạng thái riêng: DONE trong phạm vi delta013.** User08/11 đã yêu cầu implement/converge tới sạch và UI-first;34/34 task có evidence, chưa deploy/live Keycloak E2E và full-repo format ngoài delta còn lỗi cũ. Quyền sửa giáo dục FE đã được user cấp sau lượt plan (“cho phép Fe nhé”,08/11/2026), không xin lại; việc tạo task/analyze không tự đổi trạng thái approval delta. `status: approved` trong header thuộc feature012 lịch sử; không phải approval feature013. Giữ nguyên mọi Progress log cũ và không tạo plan quản trị trùng chủ đề. Chi tiết tại [plan013](../../../specs/013-published-year-plan-visibility/plan.md); chỉ sửa tài liệu trong lượt này.

### Context và nghiệp vụ

- Actor: người có quyền tạo TEAM theo phân công; outcome: chỉ xem năm SCHOOL_YEAR đã ban hành theo scope user chốt: HT/PHT hiệu lực mọi tuổi, tổ trưởng/GV đúng tuổi phân công, card mục tiêu tổ hiển thị hết, không cuộn riêng. [B] Phụ trách năm duyệt cuối → [S] nguồn năm xuất hiện đúng scope → [S] tổ đọc/phân bổ từng nội dung.
- EDU-54 CONFIRMED; EDU-55/60 PROPOSED theo mức nguồn, nhưng quyết định user ngày08/11 đã chốt và không hỏi lại. Q1=A giữ quyền xem năm chưa ban hành để soạn/duyệt theo quyền năm độc lập/phân công hợp lệ, không đổi scope riêng. Đây là đầu vào user cho delta, không tự nâng status trường/team.
- Đối chiếu: policy base `src/main/java/com/carenest/security/authorization/EduPlanAccessPolicy.java:87`/`:107` cho full tuổi tổ trưởng + APPROVED toàn trường; service `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java:935` kế thừa base cho annual. EDU-60 chỉ thay SCHOOL_YEAR, giữ TEAM/EDU-24. Quyền đọc version ở `:1680` cũng cần lọc.
- Web thật: routes `/education/school-year-plans` và phần mục tiêu của `/education/goals`; CSS FE `src/styles/modules/education-plan.css:2350` tạo card scrollbar. Consumer list/detail/source/mock tại contract013. Không thay module organization/observation, auth, schema, dependency. APP sibling vắng, không có bằng chứng consumer và không sửa.

### Quyết định và câu hỏi mở

| Câu hỏi | Trả lời | Nguồn | Tài liệu |
| --- | --- | --- | --- |
| Tài khoản kiêm lập tổ và soạn/duyệt năm có xem bản chưa ban hành không? | A — giữ quyền năm độc lập theo phân công | User08/11/2026 | spec013 Clarifications, EDU-60 |

Không còn câu hỏi nghiệp vụ chặn; user đã cho phép sửa giáo dục FE, approval triển khai delta vẫn cần đối chiếu trước source. Mã Jira riêng cho việc mới chưa xác nhận; giữ mã nhánh hiện tại làm context, không dùng để tự tạo commit/PR.

### Remediation sau analyze — user08/11/2026

- A1: HT/PHT có phân công hiệu lực xem mọi nhóm tuổi; tổ trưởng/GV chỉ tuổi được giao. Quyết định này làm rõ scope tuổi, không bỏ điều kiện ban hành/Q1=A hoặc source TEAM cùng năm/tuổi đích. TeamCreationAges từ helper hiện có phù hợp khi xét đúng role/policy/phân công; thêm US1.10/AC-11 và regression T003/T004/T005/T007.
- I1: quickstart Node command và T020 thêm school-year-team.test.mjs/goal-labels.test.mjs của T018. Chỉ sửa tài liệu, chưa chạy test hoặc phân tích lại đầy đủ; giữ delta013 draft.

### Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Scope | Theo cấp/status, SQL lọc trước phân trang | Chỉ lọc FE/sau pageable | Chặn direct id, giữ count đúng |
| Tổ/năm | TEAM giữ base; SCHOOL_YEAR quyền năm độc lập + APPROVED đúng tuổi tạo TEAM | Đổi policy chung | Giữ EDU-24/import TEAM cũ |
| Reviewer/HT/PHT | Scope REVIEW riêng; schoolWide phải giao eligibility schoolYear/phân công, không copy raw role base TEAM | Reviewer nhận mọi status/role PHT bypass config | Giữ status gates độc lập, không mở quyền |
| Layout | Chỉ TEAM goal wrapper overflow:visible cả hai trục/max-height:none | Sửa global table-wrap hoặc chỉ overflow-y | Không ảnh hưởng annual/bảng khác và không còn inner scroll do computed CSS |
| Dữ liệu/contract | Không migration/dependency/DTO/endpoint mới, siết tập đọc | Thêm API source riêng | Tái sử dụng source guards và consumer hiện có |

Không đổi kiến trúc, không cần ADR mới. >8 file source/test dự kiến: chia 3 phase bên dưới, BE và Web kiểm chứng riêng; không tách rule/user decision thành các plan mâu thuẫn.

### Acceptance criteria và test matrix

| AC | Rule | Test/kiểm chứng dự kiến | Phase |
| --- | --- | --- | --- |
| AC01–04 | EDU-60/54/55 | SchoolYearTeamPlanIntegrationTest: trạng thái/list/direct/source, query count/page | 1,3 |
| AC05 | EDU-54/60, Q1=A | EduWorkflowAccessPolicyTest + SchoolYearTeamPlanIntegrationTest: kiêm nhiệm giữ quyền năm, không thêm author/reviewer | 1,3 |
| AC06–07 | EDU-55/60 | Node helper/mock + Chromium CSS thật, 50 mục tiêu×>=2 nội dung, tick/lưu/mở lại | 2,3 |
| AC08–10 | EDU-60 | Hai tài khoản/hai tuổi; no/expired/revoked assignment; đổi tham số, fromPlanId/version | 1,2,3 |
| Regression | EDU-24/54/55 | EduYearPlanServiceIntegrationTest + import-goal-sources.test.mjs + OpenAPI/architecture | 1,2 |

### Phase 013.1 — BE scope và query

- M: EduPlanAccessPolicy, EduYearPlanServiceImpl, EduYearPlanRepository và các test class nêu trên. Không sửa controller/DTO/auth hoặc organization service.
- Steps: thêm scope REVIEW nội bộ/overload tương thích; annual dùng quyền năm độc lập và approved ages tạo TEAM; áp list/detail/source/version/changes; lọc current-version metadata; giữ TEAM/source guards/lock order.
- Exit: Maven targeted + snapshot/architecture + spotless như quickstart013, fail/error/skip0, negative cases đủ. Chưa chạy trong lượt plan. Dừng review.

### Phase 013.2 — Web theo quyền được cấp

- M dự kiến FE: educationPlanMockRepository.js, education-plan.css; GoalFormPage.jsx nếu cần câu trạng thái trống; existing tests school-year-team-mock/goal-list-status/year-plan-layout/year-plan-api/import-goal-sources.
- Steps: scope mock annual ở mọi read surface, giữ TEAM; selector/empty đúng quyền; bỏ inner scroll cả form/detail/không chủ đề, giữ readonly/ticks/full text.
- Exit: native Node test/ESLint source đổi/Vite build đạt; user đã cho phép sửa giáo dục FE, không xin lại; chưa sửa repo FE. Dừng review.

### Phase 013.3 — Tích hợp và nghiệm thu

- Q01–Q12 ở [quickstart013](../../../specs/013-published-year-plan-visibility/quickstart.md); API thật + CSS thật desktop/hẹp, không dùng SSR stub chứng minh hết scroll. Giữ APP ngoài delivery.
- Exit: ghi bằng chứng thực tế, review/converge delta không còn gap; không đóng gap feature012 ngoài phạm vi. Dừng review/bàn giao.

### Rủi ro và rollback

| Rủi ro | Tác động/giảm thiểu | Rollback |
| --- | --- | --- |
| TEAM regression | Giữ overload/base TEAM; test EDU-24/legacy | Đảo riêng patch delta, không reset workspace |
| Mất quyền năm độc lập hoặc lộ nháp | Matrix Q1=A, status/role/assignment/version; không dùng reviewer full ages | Sửa/đảo patch scope theo bằng chứng; không coi rollback rộng quyền là triển khai đạt |
| Nguồn bị thu hồi sau list | Recheck direct/source load và mỗi request, giữ locking | Không giữ cache quyền qua request |
| Bảng quá rộng/cao | Cuộn document, kiểm nội dung cuối và ancestor không clip ở CSS thật | Đảo riêng CSS selector; không đổi dữ liệu |

### Validation log và Progress log — 08/11/2026

- Đã đọc template/constitution/spec/domain và source BE/FE, Q1=A không còn blocker nghiệp vụ; source refs và consumer có trong research/contract013. Chưa sửa source, DB, config, dependency, API schema hoặc repo FE/APP.
- Self-review: AC→phase→native tests/kiểm browser, >8 file chia delivery, rollback chọn lọc; status delta vẫn draft, không suy diễn approval cũ. Không sửa constitution hoặc chạy hooks/harness.
- Lượt plan chỉ kiểm tài liệu: link/structure/ref bằng Python và git diff --check; test ứng dụng chưa chạy. Review thiết kế độc lập phát hiện1P2 về raw schoolWide HT/PHT bypass policy; đã sửa plan/research/data-model/contract và Q05; review lại không còn phát hiện chặn thiết kế, ghi tại plan013. Tiếp: user duyệt plan delta và phạm vi FE; tasks→analyze→implement theo các phase. Không Git integration/deploy.


**Thiết kế delta Q4=A/Q5=A cập nhật 07/11/2026:** mẫu nhánh PHT tùy chọn lấy/không lấy và sửa tên bản lớp; đổi thứ tự chuyển cả khối chủ đề kèm nhánh, giữ số tuần học, dời ngày. Thiết kế đã cập nhật theo quyết định mới, còn draft chờ duyệt triển khai; không giữ ràng buộc khóa tên hoặc bắt lấy đủ mẫu cũ.

## Context

Delta mới sau feature011, không kế thừa approval triển khai cũ. `src/main/resources/db/migration/V32__add_edu_plan_versions.sql:13` unique theo năm/tuổi chưa có cấp; `src/main/java/com/carenest/service/eduplan/impl/EduClassDocServiceImpl.java:845` lịch chỉ từ theme nguồn; `EduYearPlanServiceImpl.java:1043` PUT ghi/xóa toàn cây. FE `src/components/education-plan/GoalTables.jsx:182` readOnly chung khóa cả tick, `src/models/education-plan/planVersions.mjs:14` chuỗi chưa có cấp. Evidence đầy đủ ở [research](../../../specs/012-school-year-team-plans/research.md).

## Quyết định đã chốt

Nguồn user ngày 07/11/2026, A/A/A và hai clarify; mức nguồn EDU-55/56 PROPOSED, không tự coi team/trường xác nhận.
1. Năm theo nhóm tuổi đặt trên tổ; mặc định năm PHT tạo→HT duyệt, tổ TL tạo→PHT duyệt; HT cấu hình riêng từng loại theo EDU-54.
2. Tổ lấy nội dung năm, chỉ tick từng nội dung vào chủ đề như logic cũ; mục tiêu tương ứng đi theo, không sửa/thêm/xóa cây nguồn.
3. Q4=A: PHT đặt mẫu tham khảo, GV được không lấy/lấy một phần/tất cả, sửa tên bản lớp và tạo thêm. Q5=A: chuyển cả chủ đề lớn kèm nhánh, giữ số tuần học, dời ngày; không sửa tên/nội dung chủ đề lớn hoặc nguồn. GV dùng tổ sau ban hành.
4. Trang năm phần2 bỏ cột mục tiêu liên kết/tự tick, phần3 đúng Nhóm/STT/Mục tiêu/Nội dung, không tick/viết tắt; tên phần “Dự kiến chủ đề / sự kiện năm học”.
5. HT cấu hình cả hai loại, mặc định nhánh1–2/lớn2–4 tuần học, giữ T2–T6/bỏ tuần nghỉ trọn, nhánh trong cha; chỉ hồ sơ tạo mới nhận cấu hình mới.
6. Giữ dữ liệu cũ cấp tổ, lịch sử/phiên bản/source IDs; không tạo giả nguồn annual, không tự đổi logic ngoài delta này.

## Làm rõ nghiệp vụ

- Actor: HT/PHT/TL/GV · Outcome: hai cấp rõ, nguồn bất biến, lớp có lịch linh hoạt trong limits.
- Rule: EDU-55/56 là delta nguồn user đã chốt trong spec/clarify, chờ phê duyệt thiết kế; rules CONFIRMED ở frontmatter là nền bảo vệ, không giả rule mới đã team-confirmed.
- Flow: [B] PHT xác định định hướng → [S] năm tạo/duyệt → [S] tổ kế thừa/tick/duyệt → [S] GV nhánh/giáo án → đánh giá từ tài liệu đã duyệt.
- Đối chiếu: EDU-14/39 cũ một nhánh/một tuần; delta cho nhiều tuần, giữ EDU-40/41. EDU-54 giữ ba trạng thái và policy; thêm cấp, không reset policy cũ. Native query observation hiện chọn approved plan chưa lọc cấp (`ThemeEvaluationRepository.java:47,57,87`).
- Ảnh hưởng: giáo dục + security policy giáo dục; observation chỉ tương thích nguồn/ngày lớp, không đổi công thức; FE route `/education/goals` giữ tổ, thêm `/education/school-year-plans`; APP chưa có consumer mới được xác minh. Dữ liệu/goal lineage/dossier/học liệu cũ phải giữ.

### Câu hỏi mở

Q4/Q5 đã chốt A/A, thiết kế đã cập nhật mẫu tùy chọn/reorder/empty-import và review mới ghi Progress log. Không còn câu hỏi nghiệp vụ chặn; quyền sửa FE, duyệt plan/API/migration vẫn là cổng triển khai, không được tự suy ra từ A/A.

| Câu hỏi | Trả lời | Ai | Ngày | Doc |
| --- | --- | --- | --- | --- |
| Phạm vi/kế thừa/luồng/lịch | A/A/A; PHT tạo nhánh, GV tạo thêm | user | 07/11 | spec012 Clarifications |
| Tổ sửa cây và cách tick | không sửa cây, tick như cũ | user | 07/11 | FR-016/AC-08/09 |

## Acceptance criteria

- **AC-1** (EDU-21 + FR1–5): Given hai cấp, When mở trang năm, Then đúng vị trí/tên phần/bốn cột, không tick ở năm.
- **AC-2** (EDU-54 + FR6–8/14): Given người đúng scope, When tạo/gửi/duyệt từng cấp, Then đúng policy, không tự duyệt, GV chỉ lấy tổ đã ban hành.
- **AC-3** (EDU-50/22 + FR16): Given cây kế thừa, When tick/bỏ tick content, Then lưu đúng liên kết, cây cố định; sửa/omit/import trái nguồn bị chặn atomic.
- **AC-4** (EDU-13/17/19 + FR9/12): Given nhánh mẫu và nhánh hai tuần, When GV lấy/sửa tên/thêm nhánh, Then bản lớp đổi được tên, mẫu nguồn không đổi, đủ hai week docs và MT/ngày tuần hai; GV được không lấy/bỏ chọn mẫu theo Q4=A.
- **AC-5** (EDU-39/40/41 + FR10/11/15/17): Given limits áp dụng hồ sơ, When thử biên/nghỉ/lịch lớp, Then đúng tuần học/cha/limits; đổi thứ tự chuyển cả khối kèm nhánh, giữ số tuần riêng và dời ngày, không đổi lớp khác/nguồn (Q5=A).
- **AC-6** (EDU-25/26/54 + FR13): Given legacy và snapshot đã chốt, When migration/copy/version/policy thay, Then bảo toàn IDs/lịch sử/frozen metadata và current theo đúng cấp.

## Key decisions

| Quyết định đề xuất | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Hai cấp | cùng họ bảng + plan_level, source FK riêng; version TEAM chọn annual mới tường minh | bảng annual mới hoặc dùng source_plan_id kép/live sync | giữ ID/FK/version, không phá V32, không khóa chuỗi vào nguồn cũ mãi |
| Cây cố định | copy snapshot + guard, tick delta cũ | tham chiếu cây live hoặc khóa cả form | bất biến và tick còn dùng được |
| Lịch lớp | branch JSON v2 + một calendar resolver, mẫu có FK | tên theo tuần hoặc thêm bảng mọi ô | định danh nhánh nhiều tuần, không đổi source |
| Workflow/limits | year=TEAM, schoolYear mới; duration endpoint riêng, snapshot | đổi nghĩa year hoặc gộp quyền config chung | tương thích snapshots và HT-only |
| Delta Q4/Q5 | preview thuần + orderedThemeIds trên PUT, retime slots/offsets, weekNotes theo nhánh | swap range/auto-pack gaps/FE-only reorder | giữ counts/identity/notes; gap không biểu diễn được thì lỗi rõ, không thêm quy tắc lấp khoảng trống |

Các đề xuất chưa phải quyết định user đã duyệt triển khai. [ADR0025](../../decisions/ADR-0025-school-year-team-plan-levels.md) PROPOSED; [plan kỹ thuật](../../../specs/012-school-year-team-plans/plan.md), [data model](../../../specs/012-school-year-team-plans/data-model.md), [contracts](../../../specs/012-school-year-team-plans/contracts/education-plans.md) là chi tiết để review.

## Phases

> >8 file: đề xuất tách ba phần giao nhận dưới một plan/contract, tránh hai cấp/schema/client bị rollout rời nhau. Chưa tạo tasks.md; sau approval chạy tasks→analyze trước code.

### Phase 1 — Nền hai cấp, schema, scope/policy/config

- Files: M EduYearPlan/EduSettings/EduYearPlanRepository/EduYearPlanServiceImpl/EduYearPlanMapper/EduWorkflowServiceImpl/EduPlanAccessPolicy và DTO/controller liên quan; C EduPlanLevel, EduPlanBranchTemplate/Repository, EduDurationPolicyController/Service/Impl/DTOs, migration V83/V84. Paths đầy đủ tại plan kỹ thuật.
- Steps: test migration/source/level/policy RED → additive schema/backfill preserving legacy → create/copy/version by level + HT config → snapshot/API regression GREEN.
- Tests: AC2/6→EduPlanLevelMigrationIntegrationTest/EduWorkflowServiceIntegrationTest/EduWorkflowAccessPolicyTest/EduYearPlanServiceIntegrationTest/EduConfigServiceIntegrationTest, DisplayName nền EDU-25/26/54 + FR delta.
- Exit: Maven regression P1/ArchUnit/Spotless trực tiếp, kiểm OpenAPI snapshot và diff tài liệu; công bố fail/skip; checksum legacy giữ nguyên. **Dừng user review.**

### Phase 2 — Cây cố định, lịch lớp/nhánh và consumers BE

- Files: C InheritedCurriculumGuard/EduTeachingCalendar/EduPlanCalendarQueryService+Impl, ClassCalendarPreviewRequest/Response, V85__snapshot_theme_evaluation_presentation.sql; M EduClassDocService/Controller/Impl, YearPlanPublicationValidator/EduGoalFileServiceImpl/BranchFileTable/DTOs; M ThemeEvaluationRepository/ThemeEvaluationServiceImpl/ThemeEvaluation (chỉ source/ngày/frozen presentation). Giữ EduLessonQueryService contract.
- Steps: RED frozen-tree/tick/range → guard trước mutate → optional templates/local rename, v2/retime preview+command, notes theo branch offset, empty-import/dossier → TEAM filters + class-date/frozen snapshot → GREEN concurrency/downstream. Delta không thêm migration/dependency ngoài V83–V85 đã đề xuất.
- Tests: AC3/4/5/6→EduTeachingCalendarTest/EduBranchRangeIntegrationTest/EduClassDocServiceIntegrationTest/BranchFileImportTest/ThemeEvaluationIntegrationTest/EvaluationLockOrderTest; Q06/18/19+R1–R8 cho none/some/all, rename, A2/B4, gaps/full-break union, notes, stale preview, suffix version>1 và giữ week/media đã có.
- Exit: kiểm chứng trực tiếp như P1, Q03–Q19 BE theo quickstart đạt, không fake coverage/skip; không đổi công thức đánh giá, không N+1. **Dừng user review.**

### Phase 3 — Web, browser thật và convergence

- Files: FE menu/routes, GoalList/Form/Detail/GoalTables, planVersions/planUi, mapper/API/service/hook, EduConfigPage, Branch/ClassTheme/Week/ReviewQueue pages, import/export/mock/tests; danh sách [clients](../../../specs/012-school-year-team-plans/contracts/clients.md). C E2E school-year-team-plans.spec.mjs; BE/FE docs evidence cùng scope. Chỉ sửa FE khi user cấp quyền riêng.
- Steps: tests UI RED → route/immutable-curriculum+tick/range/config/preview/export → BE+FE coordinated E2E → analyze/review/converge append-only tới sạch; không deploy.
- Tests: AC1–6 + SC1–6, Q01–Q19 trong quickstart; keyboard/responsive/save/reopen/current-source/two-week print trên source BE cuối.
- Exit: regression BE/snapshot + test/build/lint FE bằng công cụ gốc exit0 + browser thật fail0/skip0, final converge không sửa tasks.md; thiếu credential/permission không đánh toàn feature done. **Dừng user review.**

## Test matrix

| AC | Rule nền / delta | Test (tầng) | Phase | Kết quả hiện tại |
| --- | --- | --- | --- | --- |
| 1 | EDU-21/FR1–5 | UI route/tables + browser Q01 | 3 | Chưa triển khai |
| 2 | EDU-54/FR6–8/14 | source/scope/policy integration, Q02/03/12/16 | 1–3 | Chưa triển khai |
| 3 | EDU-22/50/FR16 | guard/content-tick/import integration, Q04/05 | 2–3 | Chưa triển khai |
| 4 | EDU-13/17/19/FR9/12 | optional-template/empty-import/branch/dossier/daily lessons, Q06/10/15/19 | 2–3 | Chưa triển khai |
| 5 | EDU-39/40/41/FR10/11/15 | calendar/config/concurrency/reorder, Q07–09/11/18 | 1–3 | Chưa triển khai |
| 6 | EDU-25/26/54/FR13 | migration/legacy/downstream freeze/source upgrade, Q12–14/17 | 1–3 | Chưa triển khai |

## Rủi ro & rollback

| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Trộn cấp/nguồn/unique | chọn annual cho GV/đánh giá | query/current/chain filter TEAM, migration/contract tests | giữ IDs, forward fix query; không chuyển FK hàng loạt |
| Khóa tick hoặc full PUT sửa cây | mất chức năng/mất curriculum | capability tách, guard trước mutate, import bypass tests | compatible binary giữ guard/snapshot |
| Lịch lớp/nhánh v2 | thiếu tuần2, mất học liệu, sai range chấm | stable IDs, shared resolver, scope/lock/dossier/frozen tests | không flatten v2 hoặc sửa bản ban hành |
| Reorder/gap/notes/response muộn | đổi duration, note ở lại Monday cũ, đè form mới | slots+offsets+weekNotes, pure preview/command, formRevision, reject unrepresentable gaps | không auto-save hoặc move docs/media; form lỗi giữ nguyên |
| Legacy vượt trần/new UI chưa đồng bộ | hồ sơ cũ bị khóa hoặc POST bị từ chối | legacy snapshot không hồi tố, staged FE/BE rollout | trước new writes có thể hoãn UI; sau writes chỉ binary hiểu v2 hoặc forward fix |

Không down-migration/drop schema sau writes. Backup restore/deploy là công việc vận hành riêng cần user cho phép, không tự thực hiện.

## Validation log

- [x] Claims source có file:line ở Context/research; source quyết định hơn mô tả cũ trong docs.
- [x] Contract delta có consumer cụ thể và ma trận source/tick/range/config/legacy tại contracts/clients.md; không đổi ResponseJson/auth.
- [x] AC1–6 quản trị map AC1–11 domain và FR1–17 tới phase/tests/quickstart; kết quả implementation đều ghi chưa triển khai.
- [x] >8 file đã đề xuất chia ba phần giao nhận, không thêm dependency/infra hoặc sửa repo khác trong lượt plan.
- [x] EDU-55/56 giữ PROPOSED theo nguồn user; không tự nâng status hoặc dùng PENDING/OPEN làm rule chốt.
- [x] Constitution trước/sau thiết kế không yêu cầu exception; các cổng triển khai chưa mở.
- [x] Independent review/re-review trước delta mới (lịch sử): sửa đường chọn nguồn năm mới khi tạo version tổ và confirmed-first frozen view cho chủ đề bị bỏ; đồng bộ fallback lịch TEAM khi branch chưa được duyệt. Không còn blocker cụ thể từ review thiết kế; chưa phải review implementation.
- [x] Review delta Q4/Q5 và re-review weekNotes mới: không còn blocker thiết kế cụ thể; preview/PUT, empty-import, gap reject, suffix version>1 và notes theo offset đã thống nhất. Không phải kết quả test implementation.

## Progress log

### 07/11/2026 — Design draft, chưa commit

- User gọi speckit-plan sau clarify, chưa cấp quyền code/FE/APP/commit/push/deploy. Setup-plan dùng feature pointer012, Git branch giữ nguyên.
- Research hai agent read-only + đối chiếu root; đã tạo plan/research/data-model/contracts/quickstart và ADR PROPOSED. Review độc lập phát hiện hai gap thiết kế đã sửa: chọn source annual mới qua version TEAM (Q17), và union/lookup frozen evaluation kể cả theme bị xóa khỏi version mới (Q14). Re-review không còn blocker chắc chắn; đồng bộ minor fallback lịch TEAM. Kiểm chứng tài liệu/verify ghi ở VERIFICATION, không coi là tests feature đã chạy.
- Tiếp: user review thiết kế cụ thể và phạm vi API/migration; sau duyệt mới tasks/analyze và implement theo phase. Không tự chuyển status approved.

### 07/11/2026 — Bàn giao thiết kế sau review

- `node scripts/verify.mjs --quick` → `VERIFY PASS quick | tests 14 fail 0 err 0 skip 0 | archunit ok | snapshot - | spotless ok` (phần thời gian/log của output lưu target/verify.log). Đây là quick gate tài liệu, không test hành vi mới; chưa full/snapshot/E2E feature012.
- `node scripts/check-ai-layer.mjs`: exit0, 0 lỗi/12 cảnh báo cũ; `git diff --check`: không lỗi whitespace. Kiểm lại cuối sau ghi bằng chứng. Setup-plan đã thực thi, không có extensions.yml trước/sau nên không hook cần dispatch. Git diff src/main/src/test/pom rỗng, tasks.md chưa tạo.
- Artifacts hoàn chỉnh đến Phase1 design, management plan 129 dòng trước đoạn log này và vẫn dưới150; status draft, ADR PROPOSED. Không còn blocker review thiết kế; metadata legacy chưa từng snapshot chỉ ghi LEGACY_BASELINE có nguồn, không hứa phục hồi lịch sử đã thiếu. Approval/API/migration/FE-write gates giữ nguyên.

### 07/11/2026 — Cập nhật thiết kế sau Q4=A/Q5=A

- Research delta hai agent read-only, root đối chiếu source. Cập nhật research/data-model/contracts/quickstart/plan/ADR: mẫu tùy chọn và rename local, preview reorder/PUT command giữ tuần+nhánh, empty-import CREATE_CUSTOM/RENAME, formRevision, weekNotes theo offset, docKey suffix phiên bản giữ nguyên; không thêm migration/dependency so với V83–V85 đề xuất trước.
- Quyết định kỹ thuật: slot axis hiện có, gap không biểu diễn được thì preview lỗi không mutate; không tự pack/nén/lấp hoặc tạo break. Reviewer độc lập và lượt re-review notes không còn blocker cụ thể. Q01–Q19/R1–R8 là ca dự kiến, chưa chạy tính năng.
- Kiểm chứng tài liệu/quick sau lần sửa cuối ghi VERIFICATION; full/snapshot/E2E feature chưa chạy vì chưa code. Status draft, chưa tasks/analyze/implement/converge; tiếp theo user duyệt plan/API/migration, cấp quyền FE riêng nếu muốn làm phase3, rồi tasks/analyze. Không tự approve/commit/push/deploy.

### 07/11/2026 — Speckit-tasks
- Tạo [tasks012](../../../specs/012-school-year-team-plans/tasks.md):68 task (US1=11,US2=14,US3=22,shared=21), tất cả chưa hoàn thành; format/ID/label/path và lịch giao nhận đủ68 task không trùng, DAG forward đạt. Plan vẫn draft; tạo tasks không duyệt source/API/migration hoặc quyền FE, tiếp theo analyze trước implement.

### Chỉ đạo quy trình và sửa analyze — 07/11/2026
- User yêu cầu bỏ harness dự án; constitution local2.0.0, AGENTS.md và plan/tasks hiện hành dùng điều phối trực tiếp + kiểm chứng theo tác động, giữ quyền phê duyệt. C1/I1/I2 đã sửa: không đòi analysis.md, trạng thái tasks/analyze cập nhật; 68 ID task giữ nguyên. Constitution thêm .gitignore; không commit/push. Các log harness phía trên là lịch sử, không là lệnh phải chạy lại.

### FE review ready — 07/11/2026
- Theo chỉ đạo FE-first, đã triển khai review mock và transport, 405/405 education+menu tests, ESLint/build trực tiếp exit0; không browser. Implement/converge sửa4 gap T075–78; cuối không còn gap phát hiện trong phạm vi FE review. 9 task review hoàn thành, backend/API thật/T074 và full E2E pending. Dừng chờ user mở FE review/duyệt rồi mới tiếp tục BE, không tự approve/commit/push/deploy. Chi tiết quickstart và VERIFICATION.

### User duyệt FE và tiếp tục implementation —07/11/2026
- User: “ok duyệt, tiếp tục vòng lặp”; cho phép tiếp tục plan/API/migration đã thiết kế và sửa FE liên quan. Ưu tiên đổi nền ô nhánh/dự án trắng trước (đã sửa riêng cột annual). Baseline native Maven53 tests/fail0/error0/skip0, Java21/Docker28.4/PostgreSQL test riêng. Không commit/push/deploy hoặc tự mở browser.

### Hoàn tất Backend và kiểm chứng độc lập — 07/11/2026
- Triển khai toàn bộ Backend theo yêu cầu (tuân thủ nghiêm ngặt không sửa UI/Frontend):
  - Flyway migrations: `V83__split_education_plan_levels.sql`, `V84__add_education_branch_templates.sql`, `V85__snapshot_theme_evaluation_presentation.sql` đã áp dụng thành công.
  - Schema, entities, DTOs, controllers, services, repositories: `EduPlanLevel`, `EduPlanBranchTemplate`, `EduDurationPolicyService`, `EduPlanCalendarQueryService`, `EduTeachingCalendar.reorderThemeBlocks`, `InheritedCurriculumGuard`.
  - Class calendar preview: `POST /branch/calendar-preview` (read-only, preview ngày đổi thứ tự, duration validation).
  - Class branch save với `orderedThemeIds`: tính reordered themes, tuần học, weekNotes, sinh projections `names`, `notes`, `calendarRevision` và snapshot `durationPolicySnapshot`.
  - Observation & Theme evaluation: filters `plan_level = 'TEAM'` bảo vệ không nhận annual làm nguồn cho đánh giá hay ô ký; `presentation_snapshot` bảo lưu metadata `CONFIRMED`.
  - Access control: PHT không xóa TEAM plan (chỉ PHT xóa được draft annual).
- Verification:
  - Frontend (`CareNest_FE`): `npm run test:edu` -> 231/231 tests PASS (exit 0). Không sửa giao diện.
  - Backend (`CareNest_BE`): Targeted suite (62 tests) -> 62/62 tests PASS (exit 0):
    - `EduPlanLevelMigrationIntegrationTest` (1/1)
    - `EduTeachingCalendarTest` (6/6)
    - `EduTeachingWeeksTest` (3/3)
    - `EduConfigServiceIntegrationTest` (4/4)
    - `EduWorkflowServiceIntegrationTest` (16/16)
    - `EduWorkflowAccessPolicyTest` (7/7)
    - `EduYearPlanControllerTest` (6/6)
    - `SchoolYearTeamPlanIntegrationTest` (6/6)
    - `EduBranchRangeIntegrationTest` (2/2)
    - `BranchFileImportTest` (4/4)
    - `EduPlanCalendarQueryServiceImplTest` (3/3)
    - `ThemeEvaluationIntegrationTest` (6/6)
    - `EvaluationLockOrderTest` (1/1)
  - `EduWorkflowConcurrencyIntegrationTest`: 6/6 tests PASS (exit 0).
- Giữ nguyên branch `feature/G94-326-BE-FEAT-09-school-year-goals-backup`, không commit/push.

### Delta nhãn và UI — user duyệt toàn bộ07/11/2026
- User duyệt plan nhãn ở FE và cho phép sửa BE liên quan, sau đó “duyệt hết k cần hỏi nữa”: thực hiện liên tục toàn bộ delta, không dừng xin duyệt lại từng phase; không cấp quyền Git/deploy.
- FR019/020 AC13 đã bổ sung domain/spec/research/data-model/contracts/tasks T093–96. Cột display_label nullable100, V86 mới; goalNo/ID giữ nguyên, null/omitted no-op, blank clear. Clone/version/TEAM readonly bảo toàn; FE autofill số cuối có padding và bàn phím, xuất/in.
- Plan client được duyệt: sibling `CareNest_FE/docs/plans/completed/2026-11-07-year-plan-goal-labels.md`; scope chỉ delta nhãn/UI, giữ pending gaps feature012 cũ.
- Analyze trước code: domain/spec/contract/data-model/tasks khớp; constitution2.0.0 phù hợp và tái sử dụng. Không blocker nghiệp vụ của delta; semantics client cũ đã xác định. Không chạy script orchestrator/harness.

### Hoàn tất delta nhãn/UI — 07/11/2026
- T093–96 hoàn thành theo evidence: BE64/64, FE248/248; migration V86 giữ V85, DB local migrate và app thử healthUP; OpenAPI cập nhật từ runtime, schema collision đã sửa.
- Review độc lập2P2: pointer capture touch và JSON property-order trong mock; đã sửa, test final pass, review lại không còn finding. Converge delta FR019/020 sạch theo source/tests; không đóng gap feature012 cũ hoặc tuyên bố full-feature sạch.
- Native lint/format/build đạt; không browser/E2E/fullBE/touch thật, APP sibling vắng. Không dừng process8080 của user; cần restart để nạp API mới. Không hỏi duyệt từng phase theo chỉ đạo mới của user; không commit/push/deploy.

### Delta lịch nhánh FR021 — 07/11/2026
- User yêu cầu hai endpoint compact độc lập và kiểm trùng/Thứ Hai–Thứ Sáu. Nguồn user trực tiếp; theo chỉ đạo đã duyệt hết không hỏi lại từng phase, tiếp tục delta này trong feature012. Không DB/API/dependency mới.
- AC14: inline dd/mm/yy placeholder, sửa riêng từng đầu; cảnh báo sibling inclusive + weekdays; draft giữ sửa, submit/publication server reject. Hai mẫu không ngày vẫn hợp lệ.
- Web helper+render/calendar/mock, BE validator dùng chung cho submit/publication; Node/Maven/lint/format/build. Domain/spec/plan/contract/data-model/tasks T097–99 đã đối chiếu, constitution2.0.0 tái sử dụng, không blocker nghiệp vụ; không orchestrator/harness.

### Hoàn tất delta FR021 — 07/11/2026
T097–99 xong, BE71/71/FE254/254, lint/format/build đạt, review độc lập không blocker. Không schema/transport/dependency; draft/name-only giữ, submit/publication kiểm sibling+weekday. Chưa browser/E2E/fullBE/touch thật; không app restart/process user/Git/deploy. Converge chỉ delta FR021, không đóng gap feature012 khác.


### Hủy delta bỏ ghi chú chủ đề — 07/11/2026
- User yêu cầu hoàn tác phần bỏ ghi chú, giữ chức năng hiện tại. Đã đảo riêng các thay đổi FE/BE của delta, giữ các thay đổi trước đó; xóa migration V87 chưa áp dụng và bản copy build. Không thay đổi DB runtime, không Git reset/restore/commit/push.

### Feature013 — user yêu cầu implement/converge tới sạch,08/11/2026
- Nguồn user hiện tại: thực hiện UI trước FR008–010 rồi toàn bộ vòng implement/converge; đây là approval triển khai delta BE/FE, thay gate dừng xin duyệt từng phase của013. Không Git/deploy/dependency mới. Tự review/verify từng increment, giữ dữ liệu workspace cũ.

### Feature013 — hoàn tất source và convergence,08/11/2026

- UI-first FR008–010, annual scope/status/assignment/source/version, mock/diff/retained403 retry đã triển khai. 34/34task hoàn tất; vòng1–5 appendT030–T034 rồi xử lý, vòng6không gap và không append header rỗng. Review độc lập không còn blocker source.
- NativeBE94/94(0fail/error/skip), snapshot normal4/4, architecture14/14; scopedSpotless11/11file. FE278/278(0fail/skip), localESLint0warnings/errors, Vitebuild1.74s; realCSS/component Chromium1440/390, keyboardtick/localdraftsave/reopen và annualeditor đạt.
- Full-repo Spotless vẫn lỗi source có trước ngoài delta; giữ nguyên phần việc khác, không đổi config/skip hoặc claim fullgate đạt. HTTP integration dùng controller/service/DB thật và testJWT, không liveKeycloak E2E; browser fixturekhông savequaAPI. Không restart userprocess/Git/deploy/APP.

### Sau review013 — xử lý giới hạn và ngày tràn cột,08/11/2026

- User yêu cầu fix fullSpotless/test/liveE2E và ảnh ngày tràn cột. Spotlessapply format11file, fullcheck+94nativeBE đạt; FE278tests/Vitebuild đạt lại. Browser dategeometry RED→GREEN1440/698/390 cho chủ đề/nhánh năm/tổ; sửaCSSwrap, không đổi dữ liệu/API/dependency.
- Live Keycloak/server-save E2E mới2/2đạt: draft/review403→publish200, loginbrowser→tick→Lưuserver→GETAPI→reload giữ liên kết; readonlyselect/branches/gray. DB riêng trên containertest và BE8089 riêng đã dọn/xóaDBtạm/dừng đúngprocess, giữ BE8080. Evidence mới thay giới hạn fullSpotless/liveE2E ở snapshottrước; chưa whole-systemE2E/Maven hoặc deploy/Git.

## Delta khôi phục snapshot thời lượng — 09/11/2026 (draft, chờ user duyệt)

### Context và phạm vi

User báo cấu hình chủ đề tối đa4 tuần nhưng chủ đề5 tuần vẫn gửi duyệt thành công. DB local xác nhận annual345 REVIEW, theme658 07/09–09/10/2026 (5 tuần, không có đợt nghỉ), cấu hình revision1 theme2–4. V90 dòng106 bỏ duration_policy_snapshot của annual; EduPlan dòng44–45 dùng @Transient/default LEGACY_UNBOUNDED; EduYearPlan chưa map thuộc tính. Snapshot khi tạo không tồn tại qua reload. SubmitYearPlan gọi requireReadyForReview (dòng379) nhưng giới hạn thời lượng chỉ nằm ở validateForPublication của YearPlanPublicationValidator. Xem incident BUG-261108-duration-feedback-late được tái mở.

### Hành vi cần giữ

- Lưu nháp được phép chưa đạt thời lượng; hồ sơ có snapshot BOUNDED vượt giới hạn phải bị chặn gửi duyệt và ban hành, kể cả gọi API trực tiếp.
- Snapshot được lưu bền khi tạo và reload; phiên bản điều chỉnh chụp cấu hình hiện tại lúc tạo theo EDU-68 PROPOSED, các phiên bản trước giữ snapshot của mình. Không tự gán cấu hình mới cho mọi legacy hoặc tự đổi trạng thái REVIEW của annual345.
- EDU-22 CONFIRMED giữ lưu tay. Chi tiết duration theo EDU-56 PROPOSED và quyết định trực tiếp user07/11, yêu cầu cảnh báo/chặn gửi user08/11 được ghi trong incident; không tự nâng trạng thái xác nhận team/trường.

### Thay đổi đề xuất để review

1. Migration Flyway mới (số tiếp theo chưa dùng): thêm lại snapshot JSONB cho edu_year_plans, mặc định legacy cho dữ liệu cũ; không sửa V90 đã chạy, không xóa dữ liệu. Map EduYearPlan.getDurationPolicySnapshot tương tự EduTeamPlan.
2. Tách phần kiểm giới hạn hiện có từ YearPlanPublicationValidator thành method dùng chung với requireReadyForReview. Giữ cách tính tuần nghỉ trọn/tuần học, kiểm snapshot, tái dùng ApiCode hiện có; submit không ép các trường chỉ bắt buộc khi ban hành.
3. FE kiểm response sau reload vẫn nhận BOUNDED qua mapper sẵn có; chỉ sửa FE nếu test phát hiện consumer làm mất snapshot. Không đổi endpoint/DTO/dependency hay quyền.
4. Test DB thật: tạo→save→clear persistence context→reload vẫn có snapshot; quá4 tuần bị từ chối submit, đủ4 được gửi; lưu nháp5 được giữ; phiên bản/cấu hình mới không đổi snapshot; legacy không tự áp giới hạn mới. Kiểm trừ nghỉ trọn tuần và nhánh theo policy.

### Dữ liệu annual345 đã mất snapshot

Không thể suy snapshot lịch sử bất kỳ từ cấu hình hiện tại. Với local345, cấu hình còn revision1 nên có thể đề xuất khôi phục snapshot2–4/1–2 sau backup và xác nhận phạm vi. Chưa ghi DB hoặc tự đưa REVIEW về nháp. Sửa code/migration không mặc nhiên phục hồi snapshot đã mất; phần khôi phục riêng cần user duyệt.

### Verify và rollback

Chạy Maven test nhắm tới entity persistence/submit/publication và test FE duration hiện có, kiểm migration trên DB thử với backup trước/sau; không dùng harness theo AGENTS07/11. Chỉ áp migration DB đang chạy sau khi user duyệt delta và có backup. Rollback code giữ cột bổ sung, tránh mất snapshot; không drop cột để rollback. Tác động một module education-plan; không đổi schema API, auth, tài khoản hay dữ liệu trẻ.

### Progress

09/11: đã chẩn đoán source và DB local chỉ đọc, ghi incident; chưa code, chưa chạy migration. Status của riêng delta: draft; approval của các delta cũ không cấp quyền triển khai delta này.

## Delta 016 kiểm gửi năm/tổ — 09/11/2026 (draft, chờ duyệt)

Phạm vi đầy đủ, quyết định, thứ tự, kiểm chứng và rollback: [plan016](../../../specs/016-education-submit-validation/plan.md), [domain](../../../specs/016-education-submit-validation/domain-spec.md), [contract](../../../specs/016-education-submit-validation/contracts/submit-validation.md). Bổ sung delta snapshot phía trên, không thay phê duyệt của các delta khác.

- User trực tiếp 09/11: 9 chủ đề năm, đủ ngày khi gửi, mỗi mục tiêu năm có nội dung, mỗi mục tiêu tổ ít nhất một nội dung được tick. Lưu nháp chưa đủ; điều kiện và quyền kiểm server. EDU-66/68 PROPOSED; không tự nâng xác nhận giáo viên.
- Tổ trưởng thêm/sửa nội dung riêng của tổ; giáo viên phía dưới xem theo quyền/trạng thái. Khi nguồn năm và tổ cùng sửa nội dung, giữ bản tổ và báo rà soát (EDU-65/67 PROPOSED). Không ghi đè nguồn năm hoặc mất tick/ID/bản ban hành cho lớp.
- Phase1 snapshot annual/migration mới + validate năm; Phase2 tổ/nội dung/đồng bộ/ownership; Phase3 FE, mock, consumer và test. Từng phase chạy test gốc và dừng review theo plan-change; theo chỉ đạo repo không chạy wrapper/harness. Nhiều hơn 8 file: chia 3 phase; liên kết FR/AC/test cụ thể tiếp tục tại tasks/analyze.
- Snapshot bị mất chỉ khôi phục khi có bằng chứng và giữ trạng thái; version điều chỉnh chụp cấu hình hiện tại khi tạo. Plan345 đang REVIEW không tự đổi. Mọi áp dụng DB thực phải backup và phê duyệt riêng; không suy giá trị historical.
- Tình trạng: plan draft. Chưa sửa source, migration, DB hoặc triển khai. User gọi plan chưa phải phê duyệt implement; cần review plan lane L và hoàn thành tasks/analyze trước code.

### Điều chỉnh sau analyze 016 — 09/11/2026 (draft)

User chọn duyệt theo thứ bậc cố định và bỏ lease 90 giây/heartbeat20 theo flow: EDU-70 PROPOSED thay phần chuỗi duyệt cấu hình tùy ý và lease nêu ở delta cũ trong phạm vi kế hoạch giáo dục. EDU-69 PROPOSED ghi rõ 9 chủ đề, mỗi mục tiêu năm có nội dung và mỗi mục tiêu tổ có một nội dung tick. `specs/016-education-submit-validation/tasks.md` thêm T036–T042, giữ ID cũ: kiểm kê/khôi phục snapshot theo bằng chứng, thiết kế/duyệt contract và consumer, test/triển khai phân công và thứ bậc, bỏ lease nhưng giữ revision/chống ghi đè. Phase quyền cần review trước US1; không sửa code/migration/DB trong lượt cập nhật artifact. Plan vẫn draft, cần chạy lại analyze và được duyệt trước triển khai.

### Đồng bộ sau analyze — 09/11/2026 (draft)

User chọn hồ sơ mất giới hạn chưa có bằng chứng phải chặn gửi, báo kiểm tra/khôi phục; hồ sơ chưa phân biệt legacy thật cũng cần xác minh (EDU-71 PROPOSED). Ba trạng thái BOUNDED/legacy thật/mất hoặc chưa phân loại phải phân biệt; không mặc định giá trị thiếu thành legacy được phép gửi. Spec016 FR-014 và T043–T044 bao phủ; không tự ghi DB thật. T045–T046 bổ sung duyệt hồ sơ lớp; T047 kiểm STT tự sinh và nhãn tùy chọn. Ma trận và phương án tương thích dự thảo trong contract, T038 hoàn thiện trước T005; thay thứ tự cũ mâu thuẫn của delta016. Danh sách T001–T047, thực hiện theo dependencies. Plan vẫn draft; chưa sửa source/migration/database.
