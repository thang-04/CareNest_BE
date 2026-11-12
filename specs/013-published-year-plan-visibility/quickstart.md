# Quickstart — kiểm chứng feature013

## Trạng thái

Đây là hướng dẫn nghiệm thu sau triển khai, chưa chạy ca chức năng feature013 trong lượt plan. Không dùng kết quả feature012 thay bằng chứng delta này. Không chạy script orchestrator/setup/check/verify của dự án.

## Điều kiện

- User duyệt plan làn L và cho phép sửa FE trước khi triển khai client; tasks/analyze không còn blocker.
- BE: Java21, Docker đang chạy cho PostgreSQL Testcontainers; dùng Maven wrapper. API local đã cấu hình auth/DB theo README, không chép secret vào lệnh/log/tài liệu.
- FE: dependency hiện có đã cài, native Node/ESLint/Vite và Chromium hiện có; không thêm thư viện kiểm browser nếu chưa được phép.
- Chỉ dùng fixture giả: userA tạo TEAM tuổiX, userB tạo TEAM tuổiY; userNoAssignment/userExpired/userRevoked; annual author và reviewer hợp lệ (gồm kiêm nhiệm TEAM); DRAFT/REVIEW/APPROVED cùng năm ở X/Y. Test Clock cố định và phân công validFrom/validTo để kiểm biên. Giữ TEAM legacy cùng version/ticks để regression.

## Native commands dự kiến

Chạy ở BE, từng lệnh, kiểm exit code và Surefire fail/error/skip thật:

```powershell
.\mvnw.cmd '-Dtest=EduWorkflowAccessPolicyTest,SchoolYearTeamPlanIntegrationTest,EduYearPlanServiceIntegrationTest' test
.\mvnw.cmd '-Dtest=EduYearPlanControllerTest,OpenApiSnapshotTest,ArchitectureRulesTest' test
.\mvnw.cmd spotless:check
```

Docker thiếu dẫn tới testcontainers skip thì ghi chưa kiểm chứng integration, không gọi PASS. Chỉ broaden suite khi source thay đổi/failure/gap yêu cầu. Không thay test/hook để che lỗi.

Chạy ở FE sau khi được phép sửa (chỉ các test có thật):

```powershell
node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/school-year-team-mock.test.mjs tests/education-plan/goal-list-status.test.mjs tests/education-plan/year-plan-layout.test.mjs tests/education-plan/year-plan-api.test.mjs tests/education-plan/import-goal-sources.test.mjs tests/education-plan/school-year-team.test.mjs tests/education-plan/goal-labels.test.mjs
npx eslint src/services/education-plan/mock/educationPlanMockRepository.js src/pages/education-plan/GoalFormPage.jsx
npx vite build
```

Auth loader là module nạp test hiện có, không phải orchestrator/hook Spec Kit. ESLint chỉ liệt kê source JS/JSX thực sự đổi; bỏ file không đổi khỏi command thực tế. CSS kiểm bằng browser/CSS thật và build. Không dùng npm workflow wrapper của dự án. Không khởi động/dừng/restart process của user nếu chưa được phép; browser dùng phiên thử local phù hợp.

## Ma trận nghiệm thu

| Ca | AC / FR | Thao tác và kỳ vọng | Bằng chứng đích |
| --- | --- | --- | --- |
| Q01 | AC01/FR001–002 | userA list annual: chỉ APPROVED tuổiX; count/page không chứa Y hoặc DRAFT/REVIEW | Integration service + query, API thật |
| Q02 | AC02/FR002 | Direct id DRAFT/REVIEW, đổi filter X→Y hoặc giả userId: bị403/không mở scope; unknown id giữ404 | Integration + controller/API |
| Q03 | AC03/FR001 | Duyệt cuối hợp lệ; tải lại thấy nguồn tuổiX; duyệt trung gian vẫn không thấy | Integration + API thật |
| Q04 | AC04/FR004 | Không có nguồn hợp lệ: source trống, lý do rõ; source nháp không dùng tạo TEAM được, kể cả annual author | Integration + form/selector |
| Q05 | AC05/FR003 | Kiêm nhiệm TEAM+annual author/reviewer vẫn xem theo scope/status được phép; hết phân công không bypass. Custom policy schoolYear loại PHT khỏi cả creator/reviewer: PHT còn phân công/quyền TEAM không đọc được annual DRAFT/REVIEW nhờ role; reviewer tổ trưởng tuổi-scoped không được cấp DRAFT từ quyền TEAM | Policy + integration + mock/API |
| Q06 | AC08–09/AC11/FR001–002 | Hai tài khoản tổ trưởng/GV khác tuổi không xem chéo list/detail/source. HT/PHT hiệu lực có quyền đọc hợp lệ xem mọi nhóm tuổi, kể cả chỉ có quyền tạo TEAM mà không có quyền năm độc lập; khi đó chỉ xem APPROVED. Filter không thay scope/status | Policy + integration + mock |
| Q07 | AC10/FR001–002 | Chưa phân công/tương lai/hết hạn/thu hồi: lần đọc tiếp theo không xem nguồn cũ; validFrom/validTo tính inclusive theo repository | Policy + integration với Clock/fixture |
| Q08 | AC06/FR005–006 | Form+detail TEAM 50MT×>=2 nội dung, nhiều nhóm, nhãn/chữ dài/hàng chưa tick: đọc đủ đầu/cuối; không có scroll trong card | Real CSS Chromium desktop+hẹp |
| Q09 | AC07/FR007 | Tick một nội dung, lưu/mở lại: đúng một liên kết đổi; nguồn readonly, nhãn/thứ tự/nhóm/hàng còn đủ | Existing helper+integration + browser |
| Q10 | FR002, edge version | Annual APPROVED có draft version mới: list/detail/version metadata/changes không lộ draft/id; fromPlanId cấm vẫn bị chặn | Integration + API |
| Q11 | Scope/EDU24 | TEAM legacy và import nguồn TEAM liên tuổi giữ nguyên, source current/version và cấu hình workflow không đổi | EduYearPlanServiceIntegrationTest + import-goal-sources |
| Q12 | FR005–006 | TEAM không chủ đề và nhiều chủ đề, annual cùng viewport: TEAM card không scroll, trang đọc đủ; annual không bị rule CSS TEAM tác động | Browser/CSS thật + layout regression |

Các tên scenario mới sẽ được đặt ở bước tasks/implement, `@DisplayName("EDU-60: …")`; không ghi như test đã tồn tại. Tận dụng class test hiện có, không tạo suite trùng logic chỉ để tăng số test.

## Layout check thực tế

Ở màn soạn và chi tiết, inspect wrapper chứa bảng mục tiêu TEAM: computed max-height không giới hạn; overflow-x/y visible, scrollHeight không bị clientHeight cắt thành vùng cuộn. Kiểm thẻ card/ancestor không clip. Tới nội dung cuối bằng cuộn document, so số goal/content với fixture; ở màn hẹp/nhiều chủ đề cuộn trang ngang đọc được cột cuối. Kiểm focus/tick bàn phím, nội dung nhiều dòng và readonly cells. SSR tests đang stub GoalTable không thể chứng minh các điều này; nếu thiếu browser/CSS thật ghi chưa nghiệm thu SC003/004.

## Kết thúc

Ghi lệnh/kết quả/skip và Q01–Q12 thực tế vào VERIFICATION.md + Progress log. Review từng phase; chỉ converge delta EDU-60 sau implement, không đóng gap feature012 không liên quan. Không commit/push/branch/PR/deploy khi chưa được user cho phép trong tin nhắn hiện tại. Rollback thay đổi source chọn lọc theo delta nếu cần, không reset workspace hoặc revert migration (không có migration mới).

## Bằng chứng thực thi — 08/11/2026

- Baseline đã đọc trước patch: BE/FE có nhiều thay đổi feature012 sẵn, gồm source entity/observation/calendar/DTO/tests và tài liệu. Không reset/restore/commit; giữ các file ngoài delta, không sửa APP hoặc process user. Ignore files hiện có đã bao phủ target/node_modules/dist/log/env, không thêm ignore/dependency.
- User yêu cầu UI-first và implement→converge tới sạch: hoàn thiện FR008–010 trước, sau đó scope năm. Checklist requirements16/16 được đọc và giữ marker nguyên trạng. Phân tích trực tiếp domain/spec/plan/data-model/contract/tasks sau remediation A1/I1/UI delta: yêu cầu có task; không còn câu hỏi nghiệp vụ chặn trước code, không chạy extension/setup/harness.
- UI RED đúng hành vi: test bảng TEAM thiếu cột nhánh/select; GREEN sau patch. Fixture SSR đầu cần window/localStorage, editable DnD dùng portal nên chuyển ca editable sang browser thật, không sửa production để chiều test.
- BE RED đúng hành vi: annualVisibilityMatchesPublicationAndAssignment ban đầu fail vì tổ trưởng đọc được DRAFT. Scope mới làm test qua list/detail/publication/khác tuổi/thu hồi; bổ sung future/expired/validTo-inclusive, hidden-version/changes và HTTP controller/service/repository/PostgreSQL thật với JWT test synthetic.
- Mock: regression riêng cho list/source/detail/diff, đúng403/7004 và unknown404; Q1=A/HT-PHT mọi tuổi/chỉ TEAM creator chỉAPPROVED; mixed-role reviewer scoped theo role trong policy. First fixture thiếu eduConfig đã sửa trong test, không claim lỗi fixture là RED nghiệp vụ. Adapter RED thật cho annual server-authorized quản lý kiêm tổ trưởng, đã giới hạn UX cross-age legacy cho TEAM.
- Browser native: `node tests/education-plan/team-plan-browser.mjs` tại FE, component/CSS thật ở1440×900 và390×900; 50 mục tiêu/100 nội dung, header nguồn và toàn bảng phần3 xám, không inner scroll, nhánh nguồn, label/select/chi tiết chỉ nội dung tick, bàn phím tick và local-draft save/reopen, annual còn control nhập. Đây là component/UI fixture, không phải E2E lưu qua API/Keycloak thật.
- Native test cuối và giới hạn được ghi tại VERIFICATION.md. Không khởi động/restart API của user. Phần HTTP là full Spring context + real DB qua MockMvc với test JWT, không giả service hoặc thay production auth; chưa chạy E2E bằng phiên Keycloak thật.
- Full-repo Spotless có lỗi ở file thay đổi từ trước ngoài delta. Chỉ apply/check11 Java file thực sự sửa ở lượt này bằng `mvnw.cmd spotless:check '-DspotlessFiles=.*TenFile[.]java'` lần lượt; không sửa cấu hình/skip/hook và không coi scoped check là full-repo pass. Windows property là regex, dấu slash hoặc pipe qua mvnw.cmd cần tránh; log ở TEMP/carenest013-check-*.log.

### Convergence log

| Vòng | Gap có bằng chứng | Task append | Kết quả |
| --- | --- | --- | --- |
| 1 | OpenAPI preview/config/diff trỏ nhầm generic schema, snapshot fail | T030, Phase7 | Tách Schema.name metadata, thêm shape regression rồi sinh lại snapshot; không đổi field JSON/endpoint |
| 2 | Dùng scope mới cho version chain TEAM có thể thay hành vi legacy | T031, Phase8 | Scope mới chỉ SCHOOL_YEAR, TEAM giữ base như trước |
| 3 | Role quản lý ngoài annual policy mở rộng REVIEW của reviewer tổ trưởng trong mock | T032, Phase9 | Test mixed-role RED→GREEN; predicate theo role trong policy |

Các task cũ/ID/trạng thái được giữ đến khi có evidence rồi đánh dấu; không rewrite/renumber trong converge. Vòng cuối chỉ đọc code/artifact, không thêm header rỗng hoặc sửa tasks nếu không gap.

### Kết quả cuối — 08/11/2026

- BE: `./mvnw.cmd '-Dtest=SchoolYearTeamPlanIntegrationTest,EduWorkflowAccessPolicyTest,EduYearPlanServiceIntegrationTest,EduYearPlanControllerTest,OpenApiSnapshotTest,ArchitectureRulesTest' test` →94tests,0fail/0error/0skip, BUILD SUCCESS. Surefire:17+8+38+13+4+14. Sau sửa source cuối; không dùng snapshot-update ở lượt cuối.
- `spotless:check -DspotlessFiles=.*TenFile[.]java` chạy lần lượt11 file sửa →11/11 BUILD SUCCESS; full-repo format check trước đó fail ở source khác có từ trước, không sửa phần ngoài scope và không tuyên bố full gate đạt.
- FE: `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs` →278pass/278,0fail/0skip. Bao gồm school-year-team/goal-labels, annual-visibility, provider-access và team-plan-ui; local eslint8file source đổi0errors/0warnings; local vite build exit0/built1.74s.
- `node tests/education-plan/team-plan-browser.mjs` →1440/390 thựcCSS/component đạt,50goal/100content đủ; keyboardtick/local draftsave/reopen và annual editable controls đạt. Không coi local draft là E2E backend save.
- Vòng4→T033:403 mask dữ liệu cũ; review phát hiện retry clear error có thể làm data trở lại. Vòng5→T034: Provider clear retained data khi403, regression403→retry pending→500→authorized success đạt, không đổi useAsync chung.
- Vòng6 cuối: kiểm10FR/4SC,14AC và các quyết định plan/5principles constitution, không còn gap actionable trong phạm vi nguồn013; tasks không append header rỗng. Không claim cả012 hoặc toànrepo sạch.
- Một lượt lệnhFE chạy nhầmcwdBE đã dừng/kết thúc; không có dependency file nào đổi, npx chỉ dùng cache ngoài repo. Lượt cuối dùng binary đã cài củaFE; không thêm dependency. Chi tiết native validation pitfalls lưu engineering memory.

## Sau review — native checks và liveE2E08/11/2026

User yêu cầu khắc phục các giới hạn trên: full-repo Spotless được phép sửa,11file format đã xử lý bằng spotless:apply. Full spotless:check cùng6suite Maven94tests đạt lại sau format. FE278tests đạt sau CSS, localVite build2.30s;4warning no-console trong browserfixture,0eslint errors. Xem evidence/lệnh thật tại BE/FE VERIFICATION; các giới hạn cũ ở trên là snapshot lịch sử.

Live E2E mới `../CareNest_FE/tests/e2e/system/published-year-plan-visibility.spec.mjs` đạt2/2 với Keycloak thật và save/reload qua API. Chạy bằng native Playwright CLI `node --env-file=.env.e2e.local ./node_modules/@playwright/test/cli.js test tests/e2e/system/published-year-plan-visibility.spec.mjs --project=system --no-deps`, không mock auth/service. Cần DB riêng có prefix `carenest_013_e2e_*` đã migrate/seeddev, BE profiledev mới trên cổng riêng, E2E_BE_URL/DEV_BE_TARGET cùngBEđó; E2E_PG_CONTAINER/E2E_PG_DB trỏ đúngDB riêng. Test tự bổ sung năm/class synthetic2027, yêu cầu header revision và theme2tuần theo policy; mật khẩu chỉ từ envignored. Không chạy cleanup fixture trên database đang dùng.

Phiên này dùng8089 và DB `carenest_013_e2e_1108` trên container test có sẵn; cleanup0planE2E/0phân côngmarker, dừng BE8089 và xóa đúngDBtạm đã tạo, giữ container và BE8080 của user. Không deploy/commit/push. Hai E2E kiểm DRAFT/REVIEW denial→publish read, browserlogin→tick→Lưuserver→GETAPI→reload và readonly select/branch/gray; không thay toàn bộ ma trận94native tests.

User bổ sung ảnh ngày tràn cột: sửa CSS cho range wrap và endpoint co, không cắt dd/mm/yyyy. Browser regression textgeometry RED trướcCSS/GREENsauCSS ở1440/698/390 cho chủ đề/nhánh ở năm/tổ; giữ các assertionfulltree/tick/select trước đó. Incident BUG-261108-theme-date-column-overflow.
