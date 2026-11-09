# Verification — bằng chứng trước khi báo xong

## Giao diện cấu hình thời lượng, 08/11/2026

- Đồng bộ bảng thời lượng chủ đề với các bảng cấu hình, bỏ dòng mô tả theo yêu cầu, đánh số bốn mục và cập nhật nhãn tương ứng trong test E2E.
- ESLint các file FE liên quan và `npm run build` exit 0; build có cảnh báo dependency/Vite. Chưa chạy E2E hoặc kiểm tra giao diện trên trình duyệt.

## Danh sách kế hoạch của Hiệu trưởng, 08/11/2026

- Khi Hiệu trưởng là người xem/duyệt, danh sách năm học và tổ lọc bản đã ban hành hoặc thay thế; hàng chờ duyệt giữ ở trang duyệt. Sidebar liên kết tới hai trang tương ứng; đường dẫn tổng quan cũ cũng không còn hiện hồ sơ chờ.
- `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs tests/menu/edu-review-menu.test.mjs`: 301/301 đạt; ESLint file liên quan và `npm run build` exit 0. Build có cảnh báo dependency/Vite; chưa kiểm tra bằng trình duyệt kết nối backend thực.

## Biểu mẫu gửi duyệt kế hoạch năm và tổ, 08/11/2026

- Loại tên vai trò khỏi tiêu đề và nội dung hộp gửi duyệt tại trang lập và chi tiết, giữ nguyên phân quyền và luồng duyệt. Test hồi quy mới thất bại trước sửa vì còn `sendTo`, sau sửa đạt.
- `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs`: 298/298 đạt; ESLint các file FE liên quan và `npm run build` exit 0. Build có cảnh báo dependency/Vite; chưa kiểm tra trên trình duyệt kết nối backend thực.

## Hiển thị hồ sơ giáo dục trả lại, 08/11/2026

- FE giữ trạng thái DRAFT của server và dùng lịch sử/lý do trả lại để hiển thị “Trả lại”; sau gửi duyệt lại hiển thị theo trạng thái hiện hành. Không đổi API hoặc quyền sửa.
- `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs`: 297/297 đạt. ESLint các file FE liên quan exit 0; `npm run build` exit 0, có cảnh báo dependency/Vite. Chưa kiểm tra trình duyệt kết nối backend thực.

## Feature014 — implement/converge tạm dừng tại delta nguồn Bộ, 08/11/2026

- User yêu cầu implement→converge đến sạch và đối chiếu nguyên văn nguồn Bộ. Đã triển khai lọc target/contents, ngữ cảnh nguồn, gợi ý inline theo nhóm và bảo vệ readonly; user đã cho phép FE. Không đổi endpoint/DTO/schema/migration hoặc dependency trong implementation hiện tại.
- RED: curriculum unit20pass/3fail; browser lọc A còn3 lựa chọn thay vì2. Sau sửa unit curriculum25/25; full education283/283 fail0/skip0; browser fixture kiểm lọc, form target bị xóa, nguồn theo nhóm, Enter/touch, >12,1400/390, source context dòng tay/new row/di chuyển nhánh, loading/error hidden options, readonly/retry đều đạt. Chạy lại sau source cuối được ghi ở mục tiếp theo.
- Native ESLint source0error; khi lint cả test có6 no-console warnings tại browser fixture, không tắt rule. Vite build exit0 kèm warning Vite/OXC có trước. Live Keycloak+BE8089+DB014 cô lập: dedicated curriculum-cascade E2E1/1pass (22s), lấy mục tiêu/nội dung, lưu API, reload và so string đúng catalog hiện tại. Chưa kiểm version/TEAM trong live E2E.
- Hai lần live E2E đầu là lỗi fixture/selector: toolbar lấy Bộ nằm trong RowMenu; Ctrl+S không đạt trong fixture, chuyển kiểm nút Lưu. Không coi chúng là RED nghiệp vụ. DB014 và BE8089 do agent tạo đã dừng/xóa sau kiểm, không đụng BE8080.
- Đối chiếu Công báo01/VBHN-BGDĐT: thiếu33 dấu hai chấm và câu ag-2/27 “5 - 7” bị tách thành goal/content. Chưa kiểm đủ cột tuổi tất cả327 mục tiêu; không claim catalog khớp tuyệt đối. Issue BUG-261108-ministry-source-split open. User yêu cầu nguyên văn, nhưng điều chỉnh data catalog/API response và migration mới ngoài plan014 ban đầu: [delta draft](../../specs/014-curriculum-cascade-suggestions/source-correction-plan.md), [SQL review](../../specs/014-curriculum-cascade-suggestions/contracts/ministry-source-correction-draft.sql). SQL chạy trên DB014 với ROLLBACK:33UPDATE/1DELETE/1UPDATE, exit0; chưa áp dụng thật.
- Converge vòng1 phát hiện3 gap → append T031–T033 ở Phase7; T033 regression manual row/hidden option đã đạt browser và được implement đánh dấu. T031 migration/mock cần duyệt delta, T032 kiểm cột tuổi toàn danh mục chưa xong; do đó chưa clean, chưa đánh dấu feature hoàn tất.
- Không commit/push/deploy/APP. Không chạy harness/setup/check wrappers; source012/013 và thay đổi user giữ nguyên. Cần user duyệt delta migration/response được đề xuất để tiếp tục T031; quyền FE đã được cấp và không hỏi lại.

## Tasks feature014 — rà lại sau remediation I1, 08/11/2026

- User gọi lại speckit-tasks: dùng danh sách hiện có, giữ30ID/checkbox chưa thực hiện và4task[P], không nhân đôi task hoặc đánh dấu implementation. US1=6/US2=10/US3=5/chung9. Mỗi story có independent test và MVP=US1.
- Xác nhận Q10a tại T020, Q10b có test T021/guard T024 rồi nghiệm thu T025; sửa câu T002 để rõ quyền FE014 đã cấp/không xin lại, chỉ approval plan014 còn chờ. Thêm generation snapshot, không đổi spec/plan/constitution/source/client.
- Kiểm trực tiếp Python format/ID/story/path/dependency/4P,12FR/4SC/8AC/12Q,link/whitespace và con trỏ/draft/checklist; git diff --check theo tasks/evidence. Chưa chạy test sản phẩm hoặc speckit-analyze đầy đủ trong lượt tasks. Không setup-tasks/harness/hooks, extensions/override/preset không có. Python exit0/PASS; git diff --check exit0 (warning CRLF/LF); bước tiếp speckit-analyze.

## Remediation I1 feature014 và quyền FE — 08/11/2026

- User yêu cầu “sửa” sau analyze và “cchopheps fe”: sửa điểm I1 đã báo, ghi nhận quyền sửa giáo dục CareNest_FE cho014, không xin lại quyền này. Giữ delta_014_status:draft; quyền FE không tự là duyệt triển khai toàn bộ plan hoặc quyền APP/Git/deploy.
- Tách Q10 thành hai phần trong cùng ca: Q10a chọn nguồn cho dòng tay nghiệm thu T020; Q10b readonly/TEAM/no-fetch/no-mutation có test T021, guard T024, nghiệm thu T025. Đồng bộ task T012/T020/T021/T024/T025, checkpoint/coverage, quickstart và plan quản trị; giữ30ID và mọi checkbox chưa thực hiện.
- Đồng bộ trạng thái quyền FE trong spec/domain/research/plan/tasks/quickstart/module/CROSS_REPO_MAP; không đổi Q1–Q3, business rules hoặc constitution. Không sửa source/test FE/BE/APP trong lượt remediation.
- Kiểm Python trực tiếp taskIDs/checkstates/dependency,Q10a-b/checkpoint/coverage,3câu trả lời,16checklist,link/whitespace,con trỏ và hash constitution; git diff --check theo tài liệu đổi. Chưa chạy test ứng dụng vì chỉ sửa tài liệu; không harness/hooks/script điều phối. Python exit0/PASS; git diff --check exit0, chỉ cảnh báo chuẩn hóa CRLF/LF.
- Kiểm lại finding I1 sau sửa: phạm vi T020 không còn đòi Q10b trước khi T021/T024 tồn tại, T025 phụ thuộc T024 và nghiệm thu Q10b. Chưa đánh dấu task implementation hoặc tuyên bố runtime fix.

## Tasks feature014 — generation, 08/11/2026

- Tạo tasks.md30task chưa thực hiện: setup2/foundation2/US1=6/US2=10/US3=5/final5;4task[P] trên file độc lập, dependency/checkpoint theo3increment plan. MVP=US1, giữ guard approval/analyze trước mọi source/test FE.
- Trace12FR/4SC/8AC/12Q; mock identity/parity4ages/rawseed/numericgoal/clone-version, latest-form confirm, inline/group poolD, noC mapping, keyboard/IME/touch/12+options và readonly/no-fetch có task cụ thể. LiveE2E SCHOOL_YEAR là suite mới, thiếu env giữ chưa nghiệm thu; không dùng legacy TEAM suite/fixture làm proof API thật.
- Đồng bộ2chỗ trạng thái tasks trong plan.md, giữ plan014draft và mọi approval lịch sử; spec/domain/3câu trả lời/checklist/con trỏ giữ nguyên. Không sửa source BE/FE/APP, không code/analyze/runtime test, Git/deploy/constitution.
- Kiểm Python trực tiếp format30ID/checkbox/story/path, dependency chỉ về task trước,4P khác file, path existing hoặc2file test mới,coverage IDs/link/whitespace; git diff --check theo tài liệu. Python exit0/PASS; git diff --check exit0, chỉ cảnh báo chuẩn hóa CRLF/LF. Không setup-tasks/harness/hooks, extensions/override/preset không có. Bước tiếp speckit-analyze, không tự approve plan/clientwrites.

## Plan feature014 — thiết kế draft và review, 08/11/2026

- Tạo5artifacts: plan/research/data-model/contracts/curriculum-cascade/quickstart; cập nhật delta_014_status:draft trong plan quản trị012 hiện có, không kế thừa approval012/013. Giữ spec/domain/3câu trả lời/checklist/con trỏ; chưa tasks/source/client/Git/deploy.
- Hai research agents readonly khảo sát BE/Web; review thiết kế đối chiếu source: BE đủ dữ liệu, runtime dự kiến FE-only; không API/DTO/schema/migration/dependency. Review tìm2gap: mock327/327goals thiếu parent IDs, và legacyE2E tạo TEAM/EDITING không kiểm được nhập cây năm. Đã sửa thiết kế mock-only normalization/parity/roundtrip/clone/rawseed và dedicated SCHOOL_YEAR live suite; review lại không blocker,3refinement đã bổ sung.
- Native source inspection tại FE: node --input-type=module -e (import CURRICULUM_SEED, đếm ID) →goals327/domainIds0/subIds0/groupIds0, exit0. Đây là xác minh shape nguồn mock, không test hành vi sản phẩm.
- Python trực tiếp kiểm con trỏ014/5artifact, link nội bộ và line refs nguồn trong research, Markdown fences/placeholder/whitespace, draft riêng/giữ3Q/checklist16,8AC/12FR/4SC/12Q và không tasks; git diff --check theo tài liệu đổi. Python exit0/PASS và git diff --check exit0, chỉ cảnh báo chuẩn hóa CRLF/LF; không chạy Node/Maven/browser/E2E vì lượt plan tài liệu. Lệnh quickstart là hướng dẫn tương lai.
- Cập nhật module/CROSS_REPO_MAP/CROSS_MODULE_ISSUES và Attempts incident open bằng bằng chứng source tĩnh; chưa tái hiện runtime/fix. APP sibling vắng, không tự kết luận consumer Mobile. Template mặc định không override/preset/extensions; constitution2.0.0 tái sử dụng. Không prerequisite/setup/harness/hooks; yêu cầu wrapper lịch sử được chỉ đạo07/11 thay, delta đã ghi rõ, không sửa constitution/lịch sử.
- Tiếp: review plan014, speckit-tasks →speckit-analyze; trước code cần duyệt làn L và quyền sửa FE014. Không phê duyệt thay user.

## Clarify feature014 — 08/11/2026

- Rà soát10 nhóm taxonomy trên spec/domain và constitution2.0.0: không còn mơ hồ nghiệp vụ đáng hỏi;0 câu mới, giữ3 câu trả lời đã chốt, không nhân đôi Clarifications hoặc sửa spec khi không có quyết định mới.
- Scope/behavior, domain, UX, chất lượng phi chức năng trong phạm vi delta, ngoại lệ, constraints, thuật ngữ, completion và placeholders: Clear. Integration/external dependencies: Deferred tới plan để khảo sát consumer Web/Mobile và nhu cầu contract; không chặn planning, không phải quyền sửa client.
- Checklist re-review16/16 →16/16; không newly passing/regression/still unchecked; giữ nguyên byte nội dung checklist. Không đổi source/spec/domain/constitution; chỉ bổ sung bằng chứng này.
- Kiểm Python trực tiếp: con trỏ,12FR/4SC,3 câu trả lời duy nhất,16checkbox,14 mục domain và liên kết nội bộ; git diff --check theo tài liệu. Không chạy test ứng dụng hoặc prerequisite/harness/hooks (extensions.yml không có) theo AGENTS.md. Bước tiếp speckit-plan; lỗi mục tiêu đích vẫn open/chưa tái hiện runtime.

## Specify feature014 — 08/11/2026

- Tạo domain-spec đủ14 mục, spec3user stories/12FR/4SC, checklist16 tiêu chí và con trỏ .specify/feature.json. Ghi đúng ba câu trả lời user vào Clarifications, EDU-61 PROPOSED theo mức nguồn; không còn câu hỏi nghiệp vụ chặn planning.
- Cập nhật BUSINESS_RULES/module/CROSS_REPO_MAP và incident BUG-261108-curriculum-target-filter open/ISSUE_INDEX; chưa tái hiện hoặc kết luận root cause/fix.
- Kiểm trực tiếp bằng Python: JSON con trỏ,14 mục domain,12FR/4SC/3Q,16checkbox,liên kết nội bộ và whitespace; git diff --check theo file task. Python exit0/PASS; git diff --check exit0, chỉ có cảnh báo chuẩn hóa CRLF/LF của working copy. Không chạy test ứng dụng vì chỉ đặc tả; không harness/hooks/setup/check-ai-layer/verify wrapper theo AGENTS.md; extensions.yml không có. Template mặc định, không override/preset; constitution2.0.0 tái sử dụng, không sửa. Chưa plan/code/client/Git integration/deploy.

## Khắc phục sau review và ngày tràn cột —08/11/2026

- User cho phép xử lý full-repo format, chạy lại test/live E2E và bổ sung lỗi ngày chủ đề/nhánh tràn cột năm/tổ. `./mvnw.cmd spotless:apply` sửa11file Java theo formatter hiện có. `./mvnw.cmd spotless:check '-Dtest=SchoolYearTeamPlanIntegrationTest,EduWorkflowAccessPolicyTest,EduYearPlanServiceIntegrationTest,EduYearPlanControllerTest,OpenApiSnapshotTest,ArchitectureRulesTest' test` →BUILD SUCCESS,94tests/fail0/error0/skip0 và full-repo Spotless đạt. Không đổi dependency/config/hook hoặc nghiệp vụ để pass; không phải toàn bộ Maven suite.
- Ngày: browser geometry RED có bốn ngày vượt biên td; bỏ override nowrap và cho endpoint co/wrap trong CSS giáo dục. `node tests/education-plan/team-plan-browser.mjs` GREEN1440/698/390 cho ngày chủ đề/nhánh ở năm editable và tổ readonly; giữ50goals/100contents/select/tick. Không đổi dd/mm/yyyy, giá trị lưu hoặc API. Xem incident BUG-261108-theme-date-column-overflow.
- FE sau CSS: `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs` →278/278,fail/skip0. LocalVite build exit0,built2.30s. ESLint GoalTables/newE2E/browserfixture:0error,4warning no-console tại log có sẵn trong browserfixture; không tắt rule. Vite có warning deprecation/lottie annotation hiện hữu, build vẫn đạt.
- LiveE2E dùng Keycloak8180 thật, BE checkout mới tại8089 và DB mới `carenest_013_e2e_1108` trên container test có sẵn. `node --env-file=.env.e2e.local ./node_modules/@playwright/test/cli.js test tests/e2e/system/published-year-plan-visibility.spec.mjs --project=system --no-deps` với E2E_BE_URL/DEV_BE_TARGET8089 và E2E_PG_CONTAINER/E2E_PG_DB trỏ DB riêng →2passed(23.7s). Kiểm DRAFT/REVIEW403 và listẩn, ban hành200; browser đăng nhập, tickSpace/bấmLưu, GETAPI xác nhận themeIds, reload giữtick; select đầy đủ nội dung, nhánhreadonly/tablegray. Không stub auth/service; password chỉ nạp từ env ignored, không ghi giá trị.
- Các lần E2E đầu lỗi fixture: thiếu năm2027, thiếu revisionheader, chủ đề1tuần không đạt policy2–4 và selector trùng hai bảng. Đã sửa fixture theo contract, không sửa production để chiều test; không coi lỗi setup là RED nghiệp vụ. Cleanup xác nhận0plan[E2E]/0phân côngmarker, dừng đúng BE8089 agent tạo; BE8080/PID8388 giữ nguyên. Log TEMP/carenest013-native-fix.log, carenest013-fe-fix-final.log, carenest013-date-{red,green}.log, carenest013-keycloak-e2e.log; diff--check BE/FE đạt.
- Evidence mới thay giới hạn chưa có liveE2E/fullSpotless ở các snapshot lịch sử bên dưới. Chưa chạy toàn bộ E2E/Maven hệ thống, chưa deploy/commit/push. Hai E2E mới kiểm luồng cụ thể trên, không thay ma trận phân quyền94 native tests.

## Feature013 — implement/converge hoàn tất trong phạm vi delta,08/11/2026

- Rà lại sau tiếp tục phiên08/11: `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/team-plan-ui.test.mjs tests/education-plan/annual-visibility.test.mjs tests/education-plan/provider-access.test.mjs` tại FE →8/8 đạt, fail/skip0; `node tests/education-plan/team-plan-browser.mjs` đạt lại ở1440/390 và keyboard/local draft. Không sửa source. Converge không phát hiện gap mới trong yêu cầu UI; tasks.md giữ nguyên SHA256 `06AEBFC0F81D4F563BF7C0BF4792B93187F3707C2BA4BA06B3944B928DA287DE`. Live E2E chưa chạy: biến `E2E_DEV_PASSWORD` chưa có; không đọc/ghi mật khẩu hoặc tự khởi động lại môi trường user. Vite có warning deprecation/config hiện hữu, tests vẫn exit0; không đổi dependency ngoài phạm vi.
- User yêu cầu vòng implement→converge tới sạch và UI-first; dùng ui-ux-pro-max outcome searches(readonly contrast/visible select labels), ponytail tái sử dụng bảng/native select/CSS/token, không thêm dependency. Phần2 header nguồn xám; phần3 giữ nhánh, toàn bảng xám/readonly và một select mỗi chủ đề có nhãn Xem mục tiêu, đọc full mục tiêu/nội dung tick; annual editing giữ nguyên. Không sửa dữ liệu nguồn để đáp ứng layout.
- Source quyền năm: HT/PHT hợp lệ mọi tuổi, TT/GV đúng tuổi, chỉ quyền tạoTEAM chỉđọc APPROVED; Q1=A giữ quyền năm độc lập. Scope REVIEW nội bộ dùng đồng nhất SQL/list/detail/source/version/changes, TEAM overload/version base giữ cũ. Mock/adapter không mở rộng annual theo role legacy; actor truyền tới diff, provider403 clear retained data để retry/500 không hồi sinh dữ liệu cũ.
- Native BE cuối: `./mvnw.cmd '-Dtest=SchoolYearTeamPlanIntegrationTest,EduWorkflowAccessPolicyTest,EduYearPlanServiceIntegrationTest,EduYearPlanControllerTest,OpenApiSnapshotTest,ArchitectureRulesTest' test` →94/94,fail0/error0/skip0, BUILD SUCCESS. Surefire17+8+38+13+4+14, có HTTP controller/service/PostgreSQL thật với JWT synthetic. `spotless:check '-DspotlessFiles=.*TenFile[.]java'` từng11 Java file sửa →11/11đạt; log TEMP/carenest013-check-*.log. Full-repo Spotless trước đó fail ở source có trước ngoài delta(20+file khác), không sửa ngoài scope/disablehook hoặc coi scoped check là fullgate.
- Native FE cuối: `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs` →278pass/278,fail0/skip0. Localnode_modules ESLint8source đổi0error/0warning; localVite buildexit0,built1.74s. `node tests/education-plan/team-plan-browser.mjs` →realCSS/component1440/390;50goals/100contentsfull, grayheaders/table, noinnercardscroll, selectedtickedcontent, keyboardtick→localdraftsave/reopen và annualeditableinput đạt.
- RED evidence: BE readDRAFT qua leader không bịchặn, UIthiếu nhánh/select, adapter chặn nhầm annual serverauthorized, mixed-role mockreviewer rộngtuổi, Provider403 renderstale đều tái hiện bằng regression trước fix. Fixture errors riêng(window/storage/eduConfig/SSRportal) ghi ở quickstart, không claim chúng là RED nghiệp vụ. Transition403→retrypending→500→authorizedsuccess có regression và review lại.
- Convergence1–5: append-onlyT030(snapshotcollision),T031(TEAMversionpreserve),T032(mixedrolereviewer),T033(403mask),T034(clearretainedonretry); implement xử lý rồi kiểm lại. Vòng6 kiểm10FR/4SC/14AC và plan/constitution không còn gapactionable trong source013; task34/34 hoàn tất, task IDs/historical sections giữ nguyên; không thêm header rỗng khi sạch. Review độc lập các sửa cuối không còn blocker.
- MetadataOpenAPI T030: tách13nestedSchema.name, thêm shape regression rồi sinh lại snapshot, normal snapshot4/4đạt; không đổi JSON/endpoint/DB. Incident BUG-261107-year-plan-openapi-row-collision fixed bằng evidence; nativeWindows/cwdpitfalls ghi ENV-261108-scoped-native-validation. Không weakeningtest/hook, không harness/extension/constitution/dependency/migration/Git/deploy/APP.
- Giới hạn thật: không liveKeycloak E2E/productionserver restart; browser test dùngfixture/localdraft, không API-backed saveE2E. Không tuyên bố feature012/toànrepo sạch hoặc fullSpotless đạt. Source/tests/evidence được giữ trong checkout hiện tại, không commit/push.


## Tasks feature013 — rà soát lại sau remediation, 08/11/2026

- User gọi lại speckit-tasks: cập nhật tasks.md tại feature hiện hành, giữ nguyên24 ID/check states (chưa thực hiện); US1=13/US2=5/chung6,6 task [P]. Không nhân đôi task khi24 task hiện có đã bao phủ quyết định tuổi mới và I1.
- T003/T007 tách rõ quyền năm độc lập với APPROVED mọi tuổi của HT/PHT tạo TEAM; T009/T013/T021 bao phủ AC-11/kiêm nhiệm/phân công quản lý hết hiệu lực. T013/T014 có dependency BE review rõ trong task; thêm ma trận AC-01–11. Quickstart Q06 dẫn AC11; T020 vẫn bắt chạy school-year-team.test.mjs và goal-labels.test.mjs như lệnh Node đã sửa.
- Kiểm Python trực tiếp: task format/24 ID tuần tự/giữ trạng thái/labels/6P/paths, dependencies có trước task hiện tại,11 yêu cầu FR/SC và11AC có mapping, link/two-test command/whitespace/con trỏ. git diff --check theo tài liệu đổi. Chỉ là generation verification, chưa speckit-analyze hoặc test hành vi/runtime.
- Đọc artifact trực tiếp theo AGENTS.md; không setup-tasks/harness/hooks, extensions.yml không tồn tại. Chỉ sửa tasks/quickstart/evidence BE; không source/FE/APP/Git integration/deploy. Quyền FE đã cấp được giữ; không tự duyệt plan delta.


## Remediation feature013 sau analyze — 08/11/2026

- User chốt A1: HT/PHT hiệu lực mọi nhóm tuổi; tổ trưởng/GV đúng tuổi phân công. Đồng bộ spec/domain/plan/research/data-model/contract/tasks/checklist và EDU-60/module/flow/cross-repo/plan quản trị; giữ Q1=A, status gates, quyền tạo TEAM/source cùng năm/tuổi. Thêm US1.10/AC-11 và regression vào task hiện có, giữ nguyên24 ID/checkbox chưa thực hiện.
- Theo yêu cầu sửa I1 MEDIUM: quickstart Node command đã chạy thêm school-year-team.test.mjs và goal-labels.test.mjs; T020 yêu cầu rõ hai file T018. Đây là chỉnh lệnh hướng dẫn, chưa thực thi test.
- Kiểm tài liệu trực tiếp bằng Python: domain14 mục,24 ID/checkbox,7 FR/4SC,11AC,2 câu trả lời Clarifications, scope mới được ghi xuyên artifact, lệnh Node đủ hai test tồn tại, local links/whitespace; git diff --check theo tài liệu đổi. Chưa chạy lại speckit-analyze đầy đủ, test ứng dụng/browser/runtime hoặc sửa source FE/BE. Không harness/hooks/constitution/Git integration/deploy; plan delta giữ draft, quyền FE đã cấp được giữ.


## Tasks feature013 — tạo danh sách công việc, 08/11/2026

- Tạo tasks.md gồm24 task chưa thực hiện: setup1/foundation1/US1=13/US2=5/polish4; tổ chức theo user story và phụ thuộc,6 task test có cơ hội song song. Tái sử dụng test theo spec/plan/constitution; không tạo dependency/migration/endpoint/DTO mới.
- User đã cho phép sửa giáo dục FE trong tin nhắn trước, giữ quyền phiên và ghi vào tasks/plan/plan quản trị; không xin lại quyền FE, không tự nâng approval draft của delta013. Source chờ analyze và phê duyệt làn L theo T002. Không thực hiện task triển khai trong lượt generation.
- Kiểm trực tiếp bằng Python: format checkbox/ID/labels/path,24 ID duy nhất tuần tự, dependency task không trỏ về ID phía sau, task paths hiện có, coverage FR001–007/SC001–004 và Q01–Q12; link địa phương/JSON con trỏ/không placeholder/whitespace. git diff --check theo tài liệu đổi. Đây là kiểm cấu trúc/truy vết, chưa phải speckit-analyze hoặc test hành vi.
- Đọc template trực tiếp, không có override/preset/extensions.yml; bỏ setup-tasks và hooks theo AGENTS.md. Chỉ sửa tài liệu BE; không test ứng dụng, source BE/FE/APP, branch/commit/push/PR/deploy. Bước tiếp: speckit-analyze.


## Plan feature013 — thiết kế draft và review, 08/11/2026

- Tạo plan/research/data-model/contracts/quickstart trong feature013 theo spec Q1=A; dùng lại plan quản trị năm/tổ hiện có, thêm delta_013_status:draft và mục delta riêng, giữ nguyên approval/lịch sử feature012. Không coi approval cũ là quyền triển khai delta mới. Spec có link thiết kế draft, con trỏ vẫn013; không tạo tasks.
- Research agent chỉ đọc FE xác minh CSS/layout/source/mock và consumer. Review agent làn L tìm1P2: raw HT/PHT schoolWide có thể bypass policy năm; đã siết eligibility schoolYear + phân công và bổ sung Q05. Review lại không còn phát hiện chặn thiết kế, chưa chứng minh runtime.
- Không migration/backfill/dependency/endpoint/DTO/ApiCode mới; thay scope đọc là thay hành vi có contract/consumer matrix. TEAM legacy/import và quyền năm độc lập giữ theo Q1=A. Native test/build/CSS-browser commands là hướng dẫn tương lai, chưa chạy ở lượt plan tài liệu.
- Kiểm tài liệu bằng Python: link địa phương, source path/line bounds, placeholder/whitespace, JSON con trỏ, draft delta riêng, không tasks/hooks, đủ plan sections/5 research decisions/12 ca Q01–Q12. Bước đầu bắt và sửa đường dẫn FE shorthand sai từ research (API/mapper/mock/hook), kiểm lại đạt. git diff --check theo tài liệu liên quan exit0. Không dùng kiểm tra tồn tại file như bằng chứng hành vi/semantic correctness; source đọc trực tiếp + review thiết kế là bằng chứng thiết kế.
- Constitution2.0.0 tái sử dụng; extensions.yml không có, không chạy setup-plan/prerequisite wrappers/harness/hooks theo chỉ đạo user. Chưa sửa source BE/FE/APP, chạy test ứng dụng hoặc Git integration/deploy. Cần user duyệt plan delta và quyền sửa giáo dục FE; sau đó tasks→analyze→implement theo phase.


## Clarify feature013 — Q1=A, 08/11/2026

- Hỏi/đáp 1/1: user chọn A, giữ quyền xem năm chưa ban hành để soạn/duyệt theo quyền năm độc lập và phân công hợp lệ; quyền lập tổ chỉ cấp xem năm đã ban hành đúng nhóm tuổi được giao. Ghi đúng một Q/A ở Clarifications; cập nhật Status, US1.6/8, FR-003 và domain actors/open register. Đồng bộ EDU-60/module/flow/cross-repo, giữ mức nguồn PROPOSED, không hỏi lại quyết định đã chốt.
- Rà soát taxonomy: Functional Scope & Behavior, Domain & Data Model, Interaction & UX Flow, Non-Functional Quality Attributes, Edge Cases & Failure Handling, Constraints & Tradeoffs, Terminology & Consistency, Completion Signals, Misc / Placeholders đều Clear hoặc Resolved; Integration & External Dependencies Deferred tới plan để đối chiếu source/consumer/contract. Không còn câu hỏi nghiệp vụ chặn.
- Checklist rà soát lại16/16 →16/16, mới đạt0, hồi quy0, còn chưa đạt0; giữ nguyên toàn bộ bytes checklist vì không marker đổi. Python kiểm Q/A duy nhất, cấu trúc14 mục domain/7FR/4SC, link, con trỏ và nhất quán Q1=A; git diff --check các tài liệu liên quan. Chỉ kiểm tài liệu, chưa test ứng dụng/runtime/UI.
- Không có extensions.yml; không chạy hooks/prerequisite wrapper/harness theo AGENTS.md. Constitution2.0.0 tái sử dụng. Sẵn sàng speckit-plan; chưa sửa code/FE/APP hoặc thực hiện Git integration.


## Specify bổ sung — đúng nhóm tuổi và tài khoản phụ trách, 08/11/2026

- Theo user bổ sung: chỉ xem nguồn năm đã ban hành thuộc đúng nhóm tuổi tài khoản đăng nhập đang có phân công phụ trách hiệu lực. Đồng bộ feature013 domain/spec/checklist và EDU-60/module card/flow/cross-repo; giữ yêu cầu hiển thị đầy đủ mục tiêu.
- FR-001/002, US1.8/9, SC-002, AC-09/10 bao phủ hai tài khoản phụ trách hai nhóm tuổi, đổi đường dẫn/bộ lọc/giả tham số và phân công không có/hết/bị thu hồi. Giữ quyền soạn/duyệt năm độc lập trong scope riêng.
- Kiểm tra trực tiếp sau chỉnh: Python kiểm cấu trúc 14 mục domain, 16 checkbox, 7 FR/4 SC, US1.8/9, AC-09/10, liên kết nội bộ và EDU-60 duy nhất; git diff --check theo các tài liệu đổi. Không chạy harness/test ứng dụng vì chỉ cập nhật tài liệu; chưa xác nhận hành vi runtime, chưa sửa code/UI hoặc Git integration.


## Specify — quyền xem năm và danh sách mục tiêu tổ, 08/11/2026

- Chỉ tài liệu: tạo feature013 gồm domain-spec đủ 14 mục, spec 2 user stories/7 FR/4 SC, checklist 16/16; con trỏ `.specify/feature.json` chuyển sang feature013. Giữ artifact/lịch sử feature012 và mọi source đang sửa trước lượt này.
- Nguồn user: EDU-60 ghi PROPOSED theo mức nguồn, không hỏi lại yêu cầu đã nêu. Đồng bộ `BUSINESS_RULES.md`, module card education-plan, flow education-plan và `CROSS_REPO_MAP.md`. Giữ quyền năm độc lập, scope, cây nguồn chỉ đọc/tick từng nội dung và đọc tổ cũ.
- Kiểm chứng trực tiếp: `git diff --check` exit 0 (cảnh báo CRLF/LF, không lỗi whitespace); Python đọc artifact kiểm tra 14 heading domain, 16 checkbox, 7 FR, 4 SC, không marker câu hỏi trong domain/spec, link nội bộ tồn tại, JSON con trỏ đúng và EDU-60 duy nhất/có tham chiếu ở ba tài liệu liên quan → PASS. Review nội dung từng checklist: đạt 16/16 ở mức đặc tả, không phải kiểm thử chức năng.
- Template trực tiếp `.specify/templates/spec-template.md`; không có override/preset, không có `.specify/extensions.yml` khi kiểm tra. Constitution 2.0.0 phù hợp và không sửa. Bỏ hooks/harness theo chỉ đạo user 07/11/2026; không chạy script setup/check/verify của dự án.
- Chưa chạy Maven, test ứng dụng, browser/E2E vì chỉ đổi tài liệu. Chưa kiểm chứng runtime/backend authorization hoặc UI cuộn. Sẵn sàng speckit-plan, triển khai làn L cần plan được duyệt; không sửa source FE/APP, branch, commit, push, PR hoặc deploy.

## Lịch nhánh — inline compact + kiểm trùng/tuần, 07/11/2026

- FR021/AC14/EDU-59 theo yêu cầu user, giữ chỉ đạo thực hiện liên tục không hỏi từng phase. Web luôn hai DatePickerCell compact dd/mm/yy - dd/mm/yy, placeholder từ đầu và bấm từng đầu sửa riêng, bỏ chế độ thu gọn/mở lại. Sibling dates truyền taken để tô lịch; cảnh báo lệch Thứ Hai–Thứ Sáu và overlap inclusive cùng themeId tại dòng/form; mock cũng chặn chuyển duyệt khi lỗi. Picker mặc định vẫn dd/mm/yyyy/Chọn ngày như cũ.
- Server YearPlanPublicationValidator.validateBranchDates dùng chung cho submit và publication (kể cả tự ban hành khi policy đổi), kiểm khoảng đầy đủ lệch thứ/đảo ngày/overlap siblings, dùng EDUCATION_CALENDAR_INVALID sẵn có. Draft được lưu lịch lệch thứ/trùng; name-only/partial giữ semantics hiện có, bounds/duration cũ giữ. Không đổi DTO/endpoint/schema/dependency.
- RED `mvnw -DskipTests test-compile`: thiếu validateBranchDates như dự kiến (`target/branch-date-validation-red.log`). GREEN `.\mvnw.cmd -Dtest=EduWorkflowPublicationTest,SchoolYearTeamPlanIntegrationTest,EduYearPlanServiceIntegrationTest,EduYearPlanControllerTest test`: **71 test, fail0/error0/skip0**, BUILD SUCCESS, PostgreSQL thật. Có unit siblings/weekday/khác cha/name-only; integration chứng minh draft lưu được và submit bị chặn. `target/branch-date-validation-green.log`.
- Native Spotless apply4 file liên quan thành công; `mvnw -DspotlessFiles=.*[\\/]YearPlanPublicationValidator[.]java spotless:check`: BUILD SUCCESS. Không hook/harness; log `target/branch-date-validation-format.log` và `target/branch-date-validation-format-check.log`.
- FE final native giáo dục **254/254**, fail/skip0; kiểm actual picker compact/default, hai endpoint sửa độc lập/taken/warnings, cấu trúc rowSpan, helper inclusive/weekday/partial/different parent, mock draft và chuyển REVIEW bị chặn không đổi trạng thái. ESLint/Prettier file sửa exit0; Vite build exit0,2.15s. Review độc lập không blocker, agent chạy lại12/12 tests ngày/nhánh đạt.
- Artifact/source đối chiếu/converge delta FR021 không gap phát hiện; T097–99 hoàn tất. Chưa browser/E2E/full BE suite/touch thật; không chạy lại app local hoặc dừng process của user. Cần restart BE đang chạy để nạp validation mới. Không migration/API transport/APP/Git/deploy; không kết luận các gap feature012 cũ đã xong.

## Delta nhãn mục tiêu / tách UI năm học–tổ — 07/11/2026

- User duyệt plan và sửa BE liên quan, sau đó duyệt toàn bộ không hỏi lại từng phase. FR019/020, AC13, EDU-58 theo nguồn user; giữ goalNo/ID, field nhãn riêng displayLabel. V86 mới nullable varchar100, không sửa V85. DTO additive, null/omitted giữ nhãn; blank clear; clone/version giữ, TEAM không đổi nhãn nguồn. FE nhập/kéo tăng số, bàn phím/hủy, adapter/mock/in và bảng chủ đề dùng nhãn.
- RED: `mvnw -DskipTests test-compile` thiếu constructor/accessor displayLabel như dự kiến; đồng thời phát hiện test sẵn có EduDocumentAuthorIntegrationTest thiếu import LocalDate. Chỉ bổ sung import và format file đó để test compile, không đổi assertion/hook. Log `target/goal-label-red.log`.
- GREEN: `.\mvnw.cmd -Dtest=SchoolYearTeamPlanIntegrationTest,EduPlanLevelMigrationIntegrationTest,EduYearPlanServiceIntegrationTest,EduYearPlanControllerTest test`: **64 test, fail0/error0/skip0**, BUILD SUCCESS. PostgreSQL Testcontainers thật; có upgrade V85→V86 giữ goal cũ, giới hạn100 ở DB/Bean Validation, roundtrip/null/clear/ID/goalNo, clone annual→TEAM/version và từ chối sửa nhãn tổ. Log `target/goal-label-regression.log`.
- Format native Spotless apply đúng10 file Java liên quan; `mvnw -DspotlessFiles=.*[\\/]YearPlan.*[.]java spotless:check`: BUILD SUCCESS,5 file sạch. Log `target/goal-label-format.log`, `target/goal-label-final-format-check.log`.
- Runtime: `mvnw -DskipTests -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=8089 spring-boot:run`: validate90 migrations, migrate V86 thành công trên DB local, Started CareNestApplication; `/actuator/health` UP. `target/goal-label-runtime.log`. Phiên thử đã dừng; process BE8080 của user giữ nguyên, cần restart để nạp code API mới.
- OpenAPI runtime `/v3/api-docs`: kiểm label max100, goalNo integer và content themeKeys/themeIds đúng hướng. Sửa collision schema Row/Domain/Content bằng6 tên riêng; cập nhật8 schema kế hoạch từ runtime, Python/YAML assert paths và mọi schema ngoài8 không đổi. `target/goal-label-openapi-runtime.json`; incident BUG-261107-year-plan-openapi-row-collision fixed.
- FE native `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs`: **248/248**, fail/skip0. Có kiểm callback pointer capture/preview/commit/cancel, bàn phím, BigInt/padding/reverse/overflow, API old client/clear, mock TEAM, in escape HTML, khóa bảng tổ và giữ ticks. ESLint/Prettier file sửa exit0; Vite build exit0 (2.04s). Review độc lập phát hiện2 P2 capture/mock property-order, đã sửa và agent kiểm lại mock4/4, không còn finding trong phạm vi review.
- Analyze trước code và converge theo artifact/source trực tiếp: delta FR019/020 đủ coverage T093–96; không kết luận sạch các gap feature012 cũ T083–89/T091. Chưa full BE suite, browser/E2E hoặc thiết bị touch thật; không sửa APP (sibling không có), không harness, commit/push/deploy.

## Backend local — Flyway V85 checksum, 07/11/2026

- Incident: `docs/knowledge/incidents/ENV-261107-v85-local-checksum.md`; index và `TROUBLESHOOTING.md` đã cập nhật. DB local `carenest_merge`, V85 success=true nhưng checksum -449248398 khác source -969916. Cột snapshot jsonb đã tồn tại, bảng đánh giá có 0 dòng.
- Trước xử lý: `.\mvnw.cmd -DskipTests -Dspring-boot.run.profiles=dev spring-boot:run` exit 1, checksum mismatch V85; log `target/flyway-v85-before.log`.
- Sao lưu riêng metadata V85 tại `target/flyway-v85-history-before.json`. Transaction SQL có khóa bảng, chỉ cập nhật checksum khi khớp version/script/checksum cũ/success và bảng đánh giá rỗng. Commit thành công. Không đổi source/schema/migration hoặc tắt validation.
- Sau xử lý: cùng lệnh Maven, `Successfully validated 89 migrations`, schema up to date, Tomcat 8080, `Started CareNestApplication`. `Invoke-RestMethod http://localhost:8080/actuator/health` trả `status=UP`; log `target/flyway-v85-after.log`. Dừng process chạy thử sau kiểm chứng để nhường IDE.
- Không chạy unit/integration suite (chỉ sửa trạng thái môi trường local), không chạy harness theo chỉ đạo user 07/11/2026. Không commit/push hoặc sửa FE/APP. Cách xử lý không tự áp dụng cho DB khác chưa đối chiếu schema/dữ liệu.

## Feature012 — đặc tả tách năm học/tổ, 07/11/2026

### Speckit-tasks — sinh backlog theo story, 07/11/2026

- Chạy `.specify/scripts/powershell/setup-tasks.ps1 -Json`: FEATURE_DIR tuyệt đối feature012; đọc TASKS_TEMPLATE_CONTENT và required spec/plan cùng research/data-model/contracts/quickstart/constitution. Không có tasks.md trước lượt này; tạo đúng một `specs/012-school-year-team-plans/tasks.md`, không thay ID/trạng thái công việc đã có ở feature khác.
- Tổng68 task, tất cả unchecked: Setup3 + Foundation10 + US1(P1)11 + US2(P1)14 + US3(P2)22 + Polish8. Có15 task [P] ở những batch file riêng sau dependencies, kèm ví dụ từng story; không tự dispatch agent từ marker. Có independent-test criteria/story, MVP annual-only US1, bảng trace17FR/11AC/6SC→tasks/Q01–Q19/R1–R8 và dependency graph/DAG.
- Lịch giao nhận đủ68 task mỗi task đúng một lần, giữ P1 nền/nguồn→review, P2 guard/calendar/downstream→review, P3 FE/E2E→review/converge; quyền FE vẫn pending. Phát hiện trong tự kiểm: không viết RED của guard/list-source P2 ở P1 trước full gate; đã đưa RED guard về T028, RED list về T029; direct class-source RED T026 rồi implementation T027. API snapshot cập nhật ngay T018/T046/T047 trước gate full từng phase, T062 là audit cuối chứ không trì hoãn snapshot.
- Format validator PASS:68 unique sequential T001..T068, checkbox/labels đúng story, mọi task có đường dẫn cụ thể; canonical source paths đã đối chiếu rg --files (file mới được ghi rõ create). Kiểm DAG forward cho explicit prerequisites trên lịch delivery đạt; không cycle và không bỏ task. Scope annual UI dùng GoalTable/ThemeEventTable trong GoalTables, không tạo task sửa ThemePlanBody chỉ vì tên giống phần2.
- Plan kỹ thuật link tasks mới; management plan149 dòng vẫn draft, status không đổi; gọi tasks không phải user approve code/API/migration/FE. T001 yêu cầu approval thật và analyze không blocker trước source, bước tiếp đề xuất speckit-analyze. Chưa phân tích chính thức/implement/converge hoặc đánh dấu task hoàn thành; không tự deploy/commit/push/branch.
- `node scripts/verify.mjs --quick` đã chạy PASS14 test fail/error/skip0, ArchUnit/Spotless ok, snapshot không chạy; target/verify.log. `node scripts/check-ai-layer.mjs` exit0,0 lỗi/12 cảnh báo cũ; git diff --check không lỗi whitespace. Cổng chạy lại sau lần cập nhật tài liệu cuối. Chưa full/snapshot/browser/ca chức năng của backlog vì chỉ tạo tài liệu; không báo 68 task đã được thực thi.
- `.specify/extensions.yml` không tồn tại trước/kết thúc tasks generation, không before/after_tasks hook cần dispatch. Git diff src/main/src/test/pom rỗng; không sửa FE/APP hoặc dependency/harness. Các docs/spec thay đổi trước lượt này được giữ nguyên ngoài link/Progress log kế hoạch liên quan.

### Speckit-plan cập nhật delta Q4=A/Q5=A — 07/11/2026

- Chạy setup-plan.ps1 -Json: reuse plan hiện có, không copy đè template. FEATURE_SPEC/IMPL_PLAN/SPECS_DIR thuộc feature012, helper BRANCH là feature slug; Git thực vẫn feature/G94-326-BE-FEAT-09-school-year-goals-backup. Constitution1.0.0 tái sử dụng; design gates trước/sau cập nhật đạt, implementation gate đóng chờ duyệt plan/API/migration và quyền FE riêng. Không tạo task/feature/branch mới.
- Hai research agent read-only rà backend/calendar và FE/consumer; root kiểm source. Bổ sung research delta, data-model mục4.1, contracts calendar-preview/orderedThemeIds và import operation RENAME/CREATE_CUSTOM từ form rỗng, clients manual-save/identity/formRevision, quickstart Q01–Q19/R1–R8, plan/ADR0025 cùng source map. Không thêm bảng/migration/dependency ngoài thiết kế V83–V85 đã đề xuất; chỉ mở rộng JSON và DTO/endpoint dự kiến, chưa thực thi.
- Quyết định kỹ thuật: nhánh lấy mẫu có UUID/source riêng, tùy chọn không lấy/rename local; reorder dùng axis các Monday học đang được sử dụng và offset nhánh, giữ counts/identity, không swap range2/4tuần. Preview thuần không ghi, PUT command dùng cùng helper, khác resize FR-010. Gaps không biểu diễn được bằng ranges/counts giữ nguyên thì trả lỗi, không tự pack/nén/lấp; đây là giới hạn kỹ thuật được nêu trong data-model/research/Key decisions/Q18/R4, không tự thêm quy tắc phủ kín năm học.
- Source FE BranchPlanPage:587/classPlanDoc:223 dùng notes[Monday]; để note không ở lại ngày cũ, v2 branch.weekNotes theo offset tuần học, names/notes root chỉ là projections. Adapter v1 giữ từng note vào offset0 của nhánh tuần, không gộp hoặc ghi DB khi GET. Đã sửa mô tả docKey trong thiết kế theo source EduClassDocServiceImpl:725: version>1 có suffix TEAM planId, không để hai version va khóa.
- Independent design review delta PASS, re-review bổ sung weekNotes/adapter/serializer/R1/management/ADR không còn blocker cụ thể; chỉ rà thiết kế tĩnh, không test implementation. Management plan146 dòng, status draft; ADR PROPOSED, không tự approve. Research hoàn tất không unknown NEEDS CLARIFICATION; spec/checklist nghiệp vụ vẫn17FR/11AC/6SC,16/16 chất lượng, không đổi câu trả lời user.
- `node scripts/verify.mjs --quick` PASS:14 test fail/error/skip0, ArchUnit/Spotless ok, snapshot không chạy; target/verify.log. `node scripts/check-ai-layer.mjs` exit0:0 lỗi/12 cảnh báo link/plan cũ; git diff --check không lỗi whitespace. Chạy lại cổng sau ghi bằng chứng cuối. Chưa full/snapshot/browser/Q01–Q19 hoặc R1–R8 vì chưa code; không lấy quick tài liệu chứng minh hành vi mới.
- Extensions.yml không tồn tại khi kiểm tra trước/kết thúc, không hook before/after_plan để dispatch. Git diff src/main/src/test/pom rỗng, tasks.md chưa có; không sửa FE/APP, không commit/push/deploy. Bước tiếp: user review plan/migration/API, sau approval tasks→analyze→implement theo phase, không dùng plan trước delta thay approval này.

### Clarify delta — hoàn tất Q4=A / Q5=A

- Vòng bổ sung hai câu, trả lời2/2; Q5=A user 07/11/2026: chuyển cả chủ đề lớn kèm nhánh của lớp, giữ số tuần học từng chủ đề/nhánh, dời ngày theo thứ tự mới; tên/nội dung chủ đề lớn/nguồn không đổi. Q4=A trước đó: mẫu nhánh PHT tùy chọn lấy/không lấy, sửa tên bản lớp và tạo thêm. Không thêm câu hỏi hoặc business rule khác.
- Sections spec cập nhật: Status/Design, User Scenarios & Testing (US3.8/9, A2/B4→B4/A2), Edge Cases, FR-017, SC-006 và Clarifications đúng một Q/A cho Q5 (tổng4 dòng Q/A gồm2 của vòng cũ,2 của delta mới). Domain AC-10/11/constraints/open register đồng bộ, không còn marker NEEDS CLARIFICATION. Trace EDU-55/56, module/flow/cross-repo và notice tại thiết kế đã cập nhật; mọi lựa chọn đều giữ nguồn user, không nâng team-confirmed.
- Checklist 11/16 → 16/16; chỉ đổi5 marker, giữ nguyên toàn bộ headings/notes/order/spacing theo skill. Newly passing: No NEEDS CLARIFICATION markers remain; Requirements are testable and unambiguous; All acceptance scenarios are defined; All functional requirements have clear acceptance criteria; Feature meets measurable outcomes defined in Success Criteria (đánh giá spec, không phải sản phẩm). Regressions0, still unchecked0. Notes còn nói Q4/Q5 chưa chốt thuộc snapshot vòng specify lịch sử, trạng thái hiện hành ở spec/đoạn này.
- Coverage: Functional Scope & Behavior=Resolved; Domain & Data Model=Resolved (mẫu tham khảo/source vs bản lớp); Interaction & UX Flow=Resolved; Non-Functional Quality Attributes=Clear (kế thừa policies/gates cũ); Integration & External Dependencies=Deferred đến cập nhật plan/contract/calendar/import-export theo quyết định mới; Edge Cases & Failure Handling=Resolved (giữ block/duration/links, source/lớp khác/bản ban hành không đổi); Constraints & Tradeoffs=Clear; Terminology & Consistency=Clear; Completion Signals=Resolved; Misc / Placeholders=Resolved. Không Outstanding nghiệp vụ chặn. Đề xuất chạy lại speckit-plan trước tasks, không xem chỉnh các notices là plan đã được review lại.
- Đồng bộ ca Q18 trong quickstart cho reorder hai chủ đề khác thời lượng, nhánh rename/custom đi theo đúng cha và giữ tuần; các ca nghiệm thu mới chưa chạy vì chưa triển khai. Plan quản trị vẫn draft137 dòng, ADR PROPOSED; chưa user duyệt code/migration/API hoặc cấp quyền FE trong lượt này.
- Sau sửa nội dung: `node scripts/verify.mjs --quick` PASS, 14 test fail/error/skip0, ArchUnit/Spotless ok, snapshot không chạy; target/verify.log. `node scripts/check-ai-layer.mjs` exit0, 0 lỗi/12 cảnh báo cũ; git diff --check không lỗi whitespace. Chạy lại cổng sau ghi bằng chứng cuối. Không full/snapshot/nghiệm thu chức năng mới, không source/FE/APP/branch/commit/push/deploy.
- `.specify/extensions.yml` không tồn tại khi kiểm tra đầu/kết thúc vòng clarify; không có before/after hooks cần dispatch. Không tạo feature mới, con trỏ vẫn feature012.

### Clarify delta — Q4=A, còn Q5

- User trả lời A cho Q4 ngày 07/11/2026: mẫu nhánh PHT là tham khảo; GV được chọn lấy/không lấy từng mẫu, sửa tên bản lớp và tạo thêm. Cập nhật ngay FR-009, US2.3, Edge Cases và đúng một Q/A mới ở Clarifications; domain AC-10/actor/constraints đồng bộ. Mẫu nguồn không bị sửa và lịch lớp vẫn phải hoàn thiện theo điều kiện gửi hiện có.
- Đồng bộ EDU-55 và các chỗ tham chiếu module/flow/cross-repo, source-template contract/data-model/quickstart và notices plan/ADR. Q4 không còn marker hoặc câu hỏi chặn; chỉ còn một marker Q5 về cách chuyển lịch/thời lượng khi đổi thứ tự chủ đề lớn. Không tự chọn Q5 hoặc triển khai source.
- Checklist rà lại 11/16 → 11/16: không đổi marker, không đổi headings/notes/format theo speckit-clarify; năm mục vẫn mở do Q5. Notes nhắc Q4 trong checklist là lịch sử vòng specify, trạng thái hiện hành ở spec và đoạn này.
- Sau cập nhật nội dung: `node scripts/verify.mjs --quick` PASS, 14 test fail/error/skip0, ArchUnit/Spotless ok; snapshot không chạy. `node scripts/check-ai-layer.mjs` exit0, 0 lỗi/12 cảnh báo cũ; `git diff --check` không lỗi whitespace. Chạy lại cổng sau ghi bằng chứng cuối. Không full/snapshot/nghiệm thu tính năng mới; không đổi source/FE/APP/Git branch/commit/push/deploy.

### Specify delta mới — nhánh sửa tên / đổi thứ tự chủ đề lớn

- User gọi lại speckit-specify để cập nhật feature012: GV lấy nhánh PHT và được sửa tên bản lớp; chủ đề lớn đổi thứ tự dạy nhưng không sửa thông tin nguồn. Yêu cầu mới thay quyết định khóa tên nhánh cũ, không thay quyền mục tiêu/nội dung nguồn cố định hoặc logic tick. Nguồn user 07/11/2026, EDU-55/56 giữ mức PROPOSED theo nguồn, không tự nâng team-confirmed.
- Cập nhật spec FR-009, thêm FR-017/SC-006, US2.3/5 và US3.8, domain AC-10/11. Hai marker Q4/Q5: có được bỏ chọn mẫu PHT không; chuyển cả khối chủ đề/nhánh giữ thời lượng hay hoán đổi khoảng ngày cố định. Không tự chọn business rule cho hai điểm này, chưa sẵn sàng plan/implementation.
- Checklist 16/16 → 11/16, mở đúng 5 mục về clarification/độ rõ/acceptance/readiness. Domain vẫn14 mục, spec hiện17 FR. Không tạo feature013; `.specify/feature.json` vẫn trỏ `specs/012-school-year-team-plans`. Không tạo/chuyển nhánh.
- Đồng bộ quyết định mới ở BUSINESS_RULES, module card, flow, CROSS_REPO_MAP; gỡ chỉ dẫn chặn rename tại lớp trong data-model/contracts/clients/quickstart và ghi delta notice tại plan/research/data-model/contracts/quickstart/ADR. Plan quản trị vẫn draft137 dòng; review/verify thiết kế trước delta chỉ còn ý nghĩa lịch sử, cần cập nhật/rà lại sau Q4/Q5, không dùng approval hoặc review cũ thay thế.
- `node scripts/check-ai-layer.mjs` exit0, 0 lỗi/12 cảnh báo cũ; `git diff --check` không lỗi whitespace. `node scripts/verify.mjs --quick` PASS, 14 test fail/error/skip0, ArchUnit/Spotless ok, snapshot không chạy; `target/verify.log`. Cổng được chạy lại sau ghi bằng chứng cuối. Không full/snapshot/nghiệm thu chức năng mới vì chỉ sửa tài liệu, không source/FE/APP/commit/push/deploy.
- `.specify/extensions.yml` không tồn tại khi kiểm tra trước/sau, không có hooks cần dispatch; chưa hoàn tất specify vì còn Q4/Q5 chờ user trả lời.

### Speckit-plan — thiết kế draft ngày 07/11/2026

- Chạy `.specify/scripts/powershell/setup-plan.ps1 -Json`, resolver trỏ feature012; template được điền Technical Context/Constitution/Project Structure/Complexity Tracking. Branch Git thực giữ `feature/G94-326-BE-FEAT-09-school-year-goals-backup`, không tạo/chuyển nhánh dù helper trả feature slug. Constitution 1.0.0 tái sử dụng, design gates đạt; implementation gate vẫn đóng chờ user duyệt làn L.
- Tạo `specs/012-school-year-team-plans/plan.md`, `research.md`, `data-model.md`, `contracts/education-plans.md`, `contracts/clients.md`, `quickstart.md`; plan quản trị `docs/plans/active/2026-11-07-school-year-team-plans.md` status draft, ADR0025 PROPOSED. Tác động module card giáo dục/observation và CROSS_REPO_MAP đã ghi, không đổi rule user thành team-confirmed.
- Research hai agent read-only persistence/workflow và FE/consumer, root calendar/downstream; dùng Codebase Memory trace và kiểm source đối chiếu, không tin cạnh graph nhập nhằng. Independent reviewer phát hiện hai gap High: không có đường TEAM chọn annual version mới, và current-only projection làm theme đã chốt biến khỏi view khi bị bỏ. Đã sửa explicit-source createVersion/remap lineage/warnings/Q17, confirmed-first union/history lookup/presentation snapshot/Q14. Re-review không còn blocker cụ thể; minor fallback lịch TEAM cho branch chưa APPROVED đã đồng bộ contract/data-model.
- Thiết kế chia3phase: nền schema/quyền/config; curriculum guard/lịch lớp/downstream; FE/E2E/converge. Đề xuất migration V83/V84/V85 mới, API fields/endpoint duration và source-version lựa chọn tường minh; chưa tạo migration/DTO/controller/source mới. Snapshot legacy presentation là baseline có nguồn, không giả tái dựng metadata vốn chưa lưu. Chưa tasks/analyze/implement/converge; không tự coi review plan là analyze hoặc test sản phẩm.
- `node scripts/verify.mjs --quick` PASS: 14 test, fail/error/skip0, ArchUnit/Spotless ok, snapshot không chạy; `target/verify.log`. `node scripts/check-ai-layer.mjs` exit0, 0 lỗi/12 cảnh báo cũ; `git diff --check` không lỗi whitespace. Không full/snapshot/nghiệm thu chức năng mới vì chỉ sửa tài liệu. Cổng chạy lại sau cập nhật bằng chứng cuối; không báo tests trong quickstart đã thực thi.
- Không có `.specify/extensions.yml` trước/kết thúc plan nên không có before/after hooks để dispatch. Git diff `src/main`, `src/test`, `pom.xml` rỗng; không sửa FE/APP, không commit/push/deploy. Giữ các thay đổi spec/tri thức trước lượt này; không xóa chúng.

### Clarify — kết thúc hai câu bổ sung ngày 07/11/2026

- Hai câu hỏi đã được trả lời, lưu đúng hai dòng Q/A mới ở spec: (1) tổ trưởng chỉ phân bổ, không biên soạn lại mục tiêu/nội dung nguồn; (2) giữ tick/bỏ tick từng nội dung như UI/logic cũ, mục tiêu tương ứng đi theo, không tự tick toàn bộ mục tiêu. Chỉ dẫn tiếp theo của user “logic như cũ chỉ làm các thông tin nó cố định là được” được gộp vào câu trả lời 2, không tính thành câu hỏi mới.
- Sections cập nhật: Status, User Scenarios & Testing (US2.8–9), Functional Requirements (FR-016), Assumptions, Clarifications; domain spec bổ sung AC-08/09. Đồng bộ EDU-55, module card, flow, CROSS_REPO_MAP. Không đổi các logic phiên bản hiện có hoặc thêm câu hỏi về quyết định đã chốt; các thay đổi ban đầu của feature vẫn giữ nguyên.
- Checklist trong vòng clarify: 16/16 → 13/16 → 16/16. So lần rà ngay trước trả lời câu 2, ba mục mới đạt là Requirements are testable and unambiguous, All acceptance scenarios are defined, All functional requirements have clear acceptance criteria. Không regression hoặc mục chưa đạt ở vòng cuối. Chỉ thay marker khi trạng thái đổi; notes cũ của checklist giữ nguyên theo skill, mốc “15 FR” trong notes thuộc vòng specify lịch sử, spec hiện có 16 FR.
- Coverage: Functional Scope & Behavior = Resolved; Domain & Data Model = Clear (thiết kế vật lý ở plan); Interaction & UX Flow = Resolved; Non-Functional Quality Attributes = Clear (tái sử dụng cổng/chính sách hiện có); Integration & External Dependencies = Deferred phần contract/nhập-xuất và chuyển dữ liệu cho bước plan, không thêm yêu cầu nghiệp vụ; Edge Cases & Failure Handling = Clear; Constraints & Tradeoffs = Clear; Terminology & Consistency = Clear; Completion Signals = Clear; Misc / Placeholders = Clear. Không Outstanding chặn plan. Không tự đặt SLA hay thêm hạ tầng. Bước tiếp theo speckit-plan, plan làn L phải duyệt trước code.
- Sau lần sửa spec/tri thức: `node scripts/verify.mjs --quick` PASS, 14 test, fail/error/skip 0, ArchUnit/Spotless ok, snapshot không chạy; log `target/verify.log`. `node scripts/check-ai-layer.mjs` exit 0, 0 lỗi, 12 cảnh báo cũ; `git diff --check` không có lỗi whitespace. Không full/snapshot/nghiệm thu hành vi mới vì chưa triển khai. Chạy lại cổng sau cập nhật bằng chứng này.
- `.specify/extensions.yml` không tồn tại khi kiểm tra trước/kết thúc clarify, không có before/after hook phải dispatch. Không sửa product code/FE/APP, không tạo/chuyển branch, commit/push.

### Clarify — câu 1 đã trả lời, vòng hỏi còn tiếp tục

- User chọn A, “không được sửa chỉ được thêm mục tiêu vào chủ đề”. Ghi đúng một Q/A mới trong Clarifications, bổ sung FR-016, US2.8, AC-08 và đồng bộ EDU-55, module card, flow, CROSS_REPO_MAP: tổ trưởng chỉ phân bổ mục tiêu nguồn, không biên soạn lại cây mục tiêu/nội dung; nhập file không vượt quyền này.
- Rà tiếp thấy cần phân biệt chọn cả mục tiêu với chọn từng nội dung của mục tiêu (cơ chế tick cũ theo nội dung). Chưa tự chọn; tiếp tục hỏi từng câu. Checklist vòng này 16/16 → 13/16: mở lại độ rõ của yêu cầu, đủ acceptance scenarios và đủ acceptance criteria; giữ nguyên toàn bộ notes/format của checklist theo skill. Chưa kết thúc clarify hoặc báo sẵn sàng plan.
- Không sửa product code/FE/APP; verify quick và check-ai-layer sẽ chạy sau lần sửa tài liệu cuối. Không có extensions.yml trong lúc kiểm tra, không có before hook cần chạy; after hook sẽ kiểm lại khi kết thúc vòng clarify.

### Vòng 2 — sau câu trả lời user A/A/A

- User chốt Q1=A kèm PHT có quyền tạo nhánh/GV tạo thêm, Q2=A, Q3=A. Đã cập nhật domain spec 14 mục, spec 15 FR, ca nghiệm thu và checklist 16/16; không còn câu hỏi Q1–Q3 chặn plan. Mức nguồn EDU-55/56 giữ PROPOSED vì chưa có xác nhận team/trường; nội dung user đã chọn không còn là câu hỏi mở.
- Đã đồng bộ BUSINESS_RULES, module education-plan, flow education-plan, CROSS_REPO_MAP. Nghiệp vụ đích: năm theo nhóm tuổi → tổ kế thừa → GV dùng tổ đã ban hành; bốn cột có STT; HT cấu hình cả hai loại thời lượng tính tuần học, chỉ hồ sơ tạo mới nhận cấu hình mới; dữ liệu cũ giữ cấp tổ. Không sửa source/FE/APP hoặc Git mutation. Sẵn sàng bước plan, chưa duyệt triển khai làn L.
- `node scripts/check-ai-layer.mjs`: exit 0, 0 lỗi, 12 cảnh báo link/plan cũ; `git diff --check` không có lỗi whitespace. `.specify/extensions.yml` không tồn tại, không có hook trước/sau specify cần chạy. Con trỏ feature vẫn là `specs/012-school-year-team-plans`.
- `node scripts/verify.mjs --quick`: PASS, 14 test, fail/error/skip 0; ArchUnit/Spotless ok, snapshot không chạy; log `target/verify.log`. Chưa chạy full/snapshot hoặc kiểm thử hành vi mới vì chưa triển khai. Sau ghi bằng chứng chạy lại các cổng kiểm tra cuối; kết quả này không phải bằng chứng tính năng mới đã chạy.

### Vòng 1 — bản nháp trước khi user trả lời (lịch sử)

- Tạo `specs/012-school-year-team-plans/domain-spec.md` đủ 14 mục, `spec.md` và `checklists/requirements.md`; cập nhật con trỏ local `.specify/feature.json` (file bị Git ignore theo cấu hình hiện có). Dùng template qua `resolve-template.ps1 spec-template -Json`; tái sử dụng constitution 1.0.0. Không có `.specify/extensions.yml`, nên không có before/after hook để thực thi.
- Checklist draft: 11/16 đạt, 5 mục chờ Q1–Q3 (kế thừa/phạm vi/nguồn và dữ liệu cũ; bố cục cột; lịch/cấu hình). Không sẵn sàng plan triển khai, không tuyên bố đã hoàn tất nghiệp vụ.
- Tri thức: EDU-55/56 PROPOSED, module card, flow và CROSS_REPO_MAP ghi đề xuất cùng xung đột với rule đã xác nhận; không ghi đè rule cũ. Chỉ đọc FE phần liên quan, không sửa FE/APP, không code/migration/contract/branch/commit/push.
- `node scripts/check-ai-layer.mjs`: exit 0, 0 lỗi, 12 cảnh báo link/plan cũ. `git diff --check`: không có lỗi whitespace (Git cảnh báo chuẩn hóa CRLF của CROSS_REPO_MAP).
- `node scripts/verify.mjs --quick`: PASS, 14 test, fail/error/skip 0, ArchUnit/Spotless ok, snapshot không chạy; log `target/verify.log`. Chỉ thay tài liệu nên chưa chạy full/snapshot hoặc nghiệm thu tính năng; kết quả quick không chứng minh hành vi mới đã triển khai. Sau ghi bằng chứng chạy lại các cổng để xác nhận trạng thái cuối.

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

## Đặc tả tách năm/tổ — 09/11/2026

- Feature015 chỉ sửa tài liệu/metadata Spec Kit: domain14 mục, spec, checklist, giải thích DB, EDU-62 và incident nhãn đang open. Chỉ đạo cuối mở trang lập tổ trực tiếp, không modal/tự lập tổ, chọn năm nguồn đã ban hành và tách bảng năm/tổ.
- Kiểm trực tiếp sau ghi: đối chiếu heading, số marker/câu hỏi, JSON feature pointer, link nội bộ và `git diff --check`; kiểm nội dung đạt 14 mục domain/14 FR/2 câu hỏi mở, JSON trỏ đúng feature015 và 13 link nội bộ tồn tại. Lượt diff đầu phát hiện một dòng trống cuối CROSS_REPO_MAP, đã bỏ trước khi kiểm lại. Không chạy test ứng dụng vì chưa sửa code, không chạy hook/harness theo AGENTS chỉ đạo07/11.
- Constitution2.0.0 đã thống nhất kiểm trực tiếp, nhưng các đoạn Iron Law/bảng lệnh bên dưới và feature-spec-loop vẫn còn yêu cầu harness cũ; áp dụng chỉ đạo mới hơn. Không đổi constitution trong lượt specify.
- Chưa kiểm runtime lỗi nhãn, chưa sửa DB/API/FE/APP, chưa chạy migration/E2E. User sau đó trả lời Q1/Q2 ngày09/11: chuyển nguồn mới nhất giữ phần nhập, bản chờ về nháp; chủ đề đã kết thúc không đỏ. Đã cập nhật FR013/014 và US3.6/7, domain, EDU62, module card và checklist; spec hết câu hỏi mở, sẵn sàng plan làn L, chưa phê duyệt triển khai. Kiểm trực tiếp Python đạt: 14 mục domain, 14 FR, checklist16/16, 0 marker mở, ca nghiệm thu Q1/Q2 và link hợp lệ; `git diff --check` tài liệu đổi exit0 (chỉ cảnh báo CRLF/LF). Không chạy test ứng dụng vì chỉ sửa tài liệu.

## Iron Law

### Cảnh báo thời lượng ngay khi nhập — 08/11/2026

- Cảnh báo chủ đề/nhánh khi đủ ngày, dùng thời lượng snapshot và số tuần học trừ nghỉ trọn tuần; kiểm lại trước gửi, vẫn lưu nháp và không áp giới hạn mới cho legacy. Form năm, mẫu nhánh và lịch lớp có cảnh báo cạnh ngày.
- FE `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs`: 295/295, fail/skip 0. ESLint file đổi exit 0, `npm run build` exit 0; Vite/Rolldown có cảnh báo hiện có. Kiểm component SSR, chưa chạy browser/E2E với BE thực. Fixture SSR đầu thiếu `document` tại Sortable thực, đã dùng fixture component có sẵn và chạy lại.

### Nhãn chờ duyệt giáo dục — 08/11/2026

- User chốt tên trạng thái hiển thị “Chờ duyệt”; giữ `REVIEW` và các mã cũ. Test RED xác nhận `REVIEW` trước sửa hiện “Đang duyệt”; GREEN kiểm nhãn, legacy và tổng quan đếm/lọc REVIEW. Lượt đầu SSR assertion kỳ vọng thừa HTML comment, đã chỉnh fixture theo markup thật.
- FE `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs tests/dashboard/*.test.mjs`: 345/345, fail/skip 0. ESLint các file đổi exit 0; `npm run build` exit 0 (cảnh báo Vite/Rolldown hiện có). Không chạy harness, browser hoặc BE runtime; BE chỉ sửa comment enum và tài liệu.

### Feature014 — kiểm chứng tiếp ngày 08/11/2026

- Sau user gọi implement tiếp, thêm V87 và cập nhật seed mock: 33 dấu hai chấm, nối câu ag-2/27; không sửa V34. Test RED FE tái hiện câu bị cắt, sau sửa targeted đạt; test cũ dò câu trước khi thêm dấu được cập nhật theo dữ liệu nguồn mới.
- PostgreSQL17 Testcontainers: `MinistrySourceCorrectionMigrationTest` 2/2, gồm đúng nội dung/ID và rollback khi preflight không khớp; `EduGoalHierarchyMigrationTest` 5/5, gồm nội dung/ticks/ID kế hoạch cũ khi nâng cấp qua V87. Fixture JDBC lượt đầu lỗi chọn schema do URL đã có query; đã sửa fixture và chạy lại. Không có test bị skip.
- `EduCurriculumControllerTest,EduCurriculumServiceIntegrationTest` chạy lại exit 0 sau cập nhật assertion cũ thiếu dấu hai chấm; lượt đầu có 1 assertion thất bại đúng vì dữ liệu nguồn đã thay đổi.
- PostgreSQL thử nghiệm sau V87 so với seed mock: 327 mục tiêu và 100 nội dung D khớp từng ký tự. Node education suite 286/286, ESLint file liên quan exit 0, Vite build exit 0, browser fixture 12 nhóm tình huống đạt.
- Live Playwright với BE8089/Keycloak/PostgreSQL riêng sau V87: 3/3, kiểm import/lọc, chọn lại gợi ý mục tiêu inline, lưu/reload, phiên bản và TEAM readonly. Đã dừng BE8089 và xóa DB thử nghiệm riêng. Lựa chọn inline cả ba cấp có browser fixture riêng; live E2E chỉ chọn ô mục tiêu.
- Audit T032/T035 còn mở: kết quả cột tuổi 851 chuỗi cũ không chứng minh văn bản Bộ đã được nhập đủ hoặc mọi ranh giới/quan hệ đều đúng. Chưa khẳng định catalog chính xác toàn bộ từng chữ.
- Đối chiếu ngược mục C: xem trực tiếp các trang liệt kê trong báo cáo, xác nhận 62 cặp câu–tuổi bị thiếu tại các ô gộp; seed mock và V34 khớp chính xác 423 chuỗi mục C theo tuổi và số lần xuất hiện. `python specs/014-curriculum-cascade-suggestions/audit/reverse-coverage.py` tạo 84 đoạn cần review, không được hiểu là số lỗi; [bằng chứng và giới hạn](../../specs/014-curriculum-cascade-suggestions/audit/reverse-coverage-review.md). T032/T035 chưa hoàn thành, chưa thay catalog cho các mục thiếu mới.
- Tiếp tục kiểm các đoạn nối: `merged-fragments-review.py` đối chiếu 13 cặp, xác nhận thêm 7 cặp mục C thiếu (tổng 69). `reverse-goals-candidates.py` tạo 6 ứng viên mục D; xem trang thật xác nhận 4 mục tiêu 5–6 tuổi thiếu, 2 ứng viên là trích nhầm cột. Hai công cụ không chứng minh độ bao phủ toàn văn; T032/T035 vẫn mở.
- Sau khi trình kế hoạch 73 cặp, user gọi implement. `prepare-addition-review.py` xác nhận 73 mục và 7 trường hợp cần xác minh cha; `plan-addition-placement.py` ghi 59 trường hợp chưa tìm được dòng kề cùng nhóm; `check-pdf-placement.py` tìm điểm tựa cùng trang cho 63/69 nội dung, sáu trường hợp chưa có, không phát hiện xung đột thứ tự trong các điểm tựa. Đây là bằng chứng chuẩn bị, chưa phải kiểm chứng migration hoặc thứ tự đầy đủ. [Chi tiết](../../specs/014-curriculum-cascade-suggestions/audit/placement-refinement.md).
- V88 thêm 71 nội dung C và 4 mục tiêu D: 73 cặp đã review cùng 2 cặp hô hấp xác nhận sau đó; mock đồng bộ. Test RED trước sửa tái hiện thiếu câu 24–36 tháng. Sau sửa FE education 288/288, lint exit 0, Vite build và browser fixture đạt; PostgreSQL Testcontainers migration/rollback 5/5, hierarchy 5/5, service 7/7 và controller 3/3, skip 0. So 494 nội dung C và 331 mục tiêu D giữa PostgreSQL V88 và mock: từng câu, tuổi, cha, thứ tự, section và nội dung con khớp. T032/T035/T038 vẫn mở vì đối chiếu ngược không chứng minh toàn văn đã đủ; không áp dụng V88 vào DB user.
- Sau cập nhật cuối V88: E2E thật riêng 3/3 (lọc/inline/lưu/reload, phiên bản, TEAM readonly); `mvnw.cmd spotless:check` exit 0 sau format đúng một file test Java, sau đó migration test 5/5 chạy lại. Vite build có cảnh báo công cụ hiện có. DB và backend thử nghiệm đã dừng/xóa; không chạy hook/harness hay thay DB user.
- V88 tiếp tục thu hẹp preflight sort vào đúng nguồn Bộ, bổ sung regression nguồn khác có sort_order 5000: migration test 6/6, skip 0; `mvnw.cmd spotless:check` exit 0. Sau V88, phép dò ngược mục C còn 17 mảnh: 15 là label đã có, 2 là lỗi trích cột; mục D còn 2 mảnh trích nhầm cột. Kết quả này không chứng minh toàn văn vì detector không đọc được mọi ô/trang/ranh giới.
- E2E thật chạy lại sau thay đổi preflight cuối: 3/3; FE `npm run build`, ESLint source/test và browser fixture 12 nhóm đều exit 0; `git diff --check` các file tracked liên quan exit 0. DB thử nghiệm riêng và backend8089 đã dọn; không áp dụng migration vào DB user.
- FE sau lần sửa source cuối: `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs` đạt 285/285, không fail/skip; ESLint các file JSX/JS thay đổi exit 0; `npm run build` exit 0, còn cảnh báo Vite/Rolldown hiện có.
- `node tests/education-plan/curriculum-cascade-browser.mjs` đạt 12 nhóm tình huống: lọc phân cấp, xóa lựa chọn không phù hợp, gợi ý, giữ ngữ cảnh khi gõ, bàn phím/cảm ứng, tải lỗi/thử lại, đổi tuổi và catalog rỗng.
- E2E Keycloak/BE/PostgreSQL cô lập trước lần sửa cuối ở GoalTables đạt 3/3: nhập/lưu/tải lại, tạo phiên bản và TEAM chỉ đọc; chưa chạy lại E2E thật sau lần sửa đó. Runtime và DB cô lập đã dọn.
- Audit catalog theo chiều seed→PDF: 842/851 chuỗi đối chiếu tự động đúng cột tuổi, 9 chuỗi kiểm ảnh trang; chưa chứng minh độ đầy đủ PDF→seed hoặc toàn bộ cấu trúc. Phát hiện 33 dấu hai chấm thiếu và một câu bị tách sai; [plan delta](../../specs/014-curriculum-cascade-suggestions/source-correction-plan.md) còn chờ duyệt, catalog thực tế chưa được sửa. SQL dự thảo đã thử trên DB cô lập và rollback.
- Không chạy harness theo chỉ đạo 07/11. Feature014 chưa hoàn tất converge vì còn T031–T032 và cần kiểm lại sau sửa dữ liệu nguồn.

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

### Feature012 — FE review,07/11/2026
- Chỉ đạo FE-first, không harness/browser; kiểm trực tiếp ở CareNest_FE: education+menu405/405,fail0,skip0; ESLint source đổi exit0 không warning, Vite build exit0. Regression legacy snapshot RED→GREEN có output thật, xem feature012 quickstart và log FE. T069–73/T075–78 hoàn tất ở scope review; toàn feature/API thật/migration/E2E chưa kiểm chứng, chờ user duyệt FE. Bổ sung FR018/EDU57 theo user: chỉ cấm PHT xóa TEAM; server T074 pending, mock/UI đã chặn.

### Feature012 — fix404 khi dùng API thật,07/11/2026
- Native regression đã tái hiện getEduConfig FAIL Not found khi duration-policy404; sửa404-only fallback, capability unavailable, giữ legacy TEAM/v1/policy3keys/dedupe, không thay env/mock/auth.403/500 không bị nuốt.
- `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs tests/menu/*.test.mjs`→409 tests,409pass,0fail,0skip; ESLint source đổi exit0, Vite build exit0/built3.57s. Logs FE `target-feature012-fix-tests.log`, `target-feature012-fix-build.log`, filelist `target-feature012-fix-files.log`. Không harness/browser/E2E; user runtime confirmation pending. Xem BE incident BUG-261107-edu-required-future-endpoint; T079 hoàn tất theo automated evidence, backend vẫn chưa được triển khai.

### FE review — nút tạo annual cho PHT,07/11/2026
- Bỏ durationPolicyAvailable khỏi điều kiện hiện nút/quyền soạn preview annual, fallback annual PHT→HT; explicit schoolYearCreationScopes rỗng vẫn chặn. Nút mở form không phụ thuộc yearCreationScopes của TEAM; API chưa có thì form hiện rõ preview và chặn save/submit, không chuyển mock hoặc bypass server.
- SSR regression đã RED absent link→GREEN; `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs`→231pass/231,0fail/0skip. ESLint GoalListPage/GoalFormPage exit0; Vite build exit0. Logs FE target-feature012-create-tests.log và target-feature012-create-build.log. Không browser/API session validation; backend vẫn pending user duyệt. T080 complete, incident BUG-261107-edu-required-future-endpoint cập nhật.

### Feature012 — Backend implementation hoàn tất & converge sạch (07/11/2026)
- Triển khai toàn diện 68 tasks theo `tasks.md` cho feature `specs/012-school-year-team-plans`.
- Kiểm chứng trực tiếp bằng Maven wrapper không qua harness:
  - Full Education Suite: 246/246 tests PASS (0 failures, 0 errors, 0 skipped).
  - `EduYearPlanServiceIntegrationTest`: 38/38 PASS.
  - `SchoolYearTeamPlanIntegrationTest`: 6/6 PASS.
  - `EduGoalHierarchyMigrationTest`: 5/5 PASS (V83/V84/V85 multi-schema migration isolation).
  - `ArchitectureRulesTest`: 14/14 PASS (ArchUnit rules).
  - `OpenApiSnapshotTest`: 3/3 PASS, `docs/api/openapi.yaml` đồng bộ với code thật.
  - `spotless:check`: BUILD SUCCESS (0 lint errors).
- Converge: 0 gap giữa spec, plan, data-model, API contracts và mã nguồn backend.
- Tuân thủ chỉ đạo của user: giữ nguyên giao diện `CareNest_FE` (0 touch), giữ nguyên branch, không commit/push.

### Feature012 — user review UI,07/11/2026

- TEAM lấy cấu trúc view từ FE HEAD ba5c6ec, cố định/màu xám chỉ trong edit, giữ domain xanh/subdomain vàng; info TEAM khóa/xám, bỏ hover highlight info cả annual/TEAM; annual import lọc SCHOOL_YEAR; inline template/class branch date pickers giới hạn parent; nút gửi/xác nhận gửi Gửi duyệt.
- FE `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/year-plan-layout.test.mjs tests/education-plan/branch-review-ui.test.mjs tests/education-plan/branch-template-enter.test.mjs tests/education-plan/year-plan-api.test.mjs tests/education-plan/import-goal-sources.test.mjs tests/menu/table-alignment.test.mjs`:37/37,fail0,skip0. ESLint source đổi exit0; Vite build exit0,2.69s. Logs target-feature012-last-ui-tests/last-lint/last-build.log. `git diff --check` exit0.
- Headless Chromium + real CSS/token regression pass: normal view trắng, fixed edit xám, domain xanh/subdomain vàng, tick trắng, hover info hai loại không đổi. Log FE target-feature012-headless-colors.log. Không mở browser hiển thị.
- Trước sửa text cuối: education+menu419/419; coverage chỉ branchCalendar.mjs + planLevel.mjs:line100%,branch80.92%,functions97.26%, không phải toàn app/backend.
- User yêu cầu không test lại BE cho UI: dừng test JVM14980, native2 không pass. Native full trước đó1148 tests có1 lỗi fixture legacy; targeted3 sau sửa35 tests pass; broad native1 247 có2 fixture update cố sửa dữ liệu nguồn, đã sửa fixture nhưng lượt native2 vướng IDE incremental classes, đã chỉnh source, chưa clean-compile/verify lại. Không kết luận BE sạch. E2E thật thiếu E2E_DEV_PASSWORD (1 failed setup,2 did not run), Q01–Q19 chưa hoàn tất. Không commit/push/deploy.


## Hoàn tác delta bỏ ghi chú chủ đề — 07/11/2026
- Theo yêu cầu user: khôi phục field note trong DTO/entity/mapper/lưu/guard/clone/import và các constructor/test; FE khôi phục cột, API mapper, import/export/history/backup và test. Giữ các thay đổi UI trước yêu cầu bỏ ghi chú. Xóa source và bản build V87 chưa áp dụng; không sửa DB runtime.
- Native `./mvnw.cmd -q -DskipTests test-compile`: exit 0 (log `%TEMP%/carenest-restore-note-compile.log`); chưa chạy BE integration tests. FE `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs`: 256/256 đạt, fail/skip 0 (log `%TEMP%/carenest-restore-note-fe.log`). Kiểm diff ghi nhận các file trước đó sạch như EduTheme, GoalFilePreviewResponse, YearPlanFileTable, importThemes, planHistory, yearPlanAutosave đã hết diff nội dung.
- Không harness, browser, migration runtime hoặc commit/push. Lint/build FE được kiểm sau bước khôi phục.


## Đồng bộ context/business rules giáo dục — ghi nhận09/11/2026

- Nguồn: user trả lời 12 câu hỏi và xác nhận đã được giáo viên phía trường xác nhận; chưa có tên/ngày xác nhận trực tiếp. Biên bản: [education-plan-confirmation-2026-11-09](../business/education-plan-confirmation-2026-11-09.md).
- Đồng bộ BUSINESS_RULES, flow, PROJECT_CONTEXT, module card, CROSS_REPO_MAP và ghi chú ưu tiên spec012/015; EDU-57 chặn mọi người xóa tổ, chuỗi/phạm vi/policy/cập nhật nguồn/rà soát/tuần học/nhập file theo đúng câu trả lời. Giữ mức nguồn riêng ngoài bộ câu hỏi và giữ gap code tách delivery.
- Native kiểm tài liệu bằng Python assert trực tiếp: 8 file UTF-8/fence, liên kết biên bản tồn tại, 59 EDU-ID duy nhất, đủ 12 câu trả lời, các dòng CONFIRMED đúng phạm vi, không ai xóa TEAM. Output: DOC CHECK PASS, exit0.
- Kiểm whitespace: git diff --check trong BE; kết quả sau cập nhật cuối ghi ở chat. Không chạy wrapper/harness theo AGENTS07/11.
- Không đổi source/API/migration, không chạy Maven/FE/browser/DB ở lượt docs; 299/299 test FE là evidence lượt đối chiếu trước, không báo chạy lại. Không commit/push/deploy. Plan/tasks012/015 cần đối chiếu mốc mới khi triển khai, chưa tái sinh hoặc thực thi tasks.


## Spec015 — remediation artifact và kiểm coverage, 09/11/2026

- User yêu cầu đối chiếu context/business rules và “fix” bộ artifact trước kiểm coverage lại. Sửa spec/domain/plan/research/model/contract/tasks/quickstart/database-context/checklist; báo cáo [analysis015](../../specs/015-separate-year-team-plans/analysis.md). Không đổi nghiệp vụ nguồn hoặc constitution.
- Bổ sung FR-016/017, regression cây nguồn API/import, task FE riêng, shared header sequence và transaction cập nhật nguồn đồng bộ là thiết kế đề xuất chờ duyệt; sửa thứ tự rehearsal trước cutover. Đối chiếu V35/V80/V83/V84: unique INDEX, authors class_doc_id không ép owner header, giữ FK ghép cây/theme/templates; không sửa migration local.
- Native Python assert trực tiếp qua PowerShell stdin: `DOC CHECK PASS: 11 Markdown files, 17 FR + 6 SC, 47 unique tasks, 23/23 mapped requirements, links/fences/whitespace valid.` Đã kiểm ID liên tục/refs/coverage table, UTF-8/fences/local links, 9 owner/ngoại lệ authors, NULL legacy/partial index, shared sequence/transaction, query không dùng bảng/cột đã drop.
- `git diff --check` exit0; Git có cảnh báo CRLF→LF trên các file đã thay đổi trước đó, không lỗi whitespace. Artifact spec015 đang untracked nên kiểm riêng trailing whitespace bằng Python, không dựa riêng git diff.
- Coverage 23/23 là coverage tài liệu yêu cầu→task/check, không implementation/test coverage. T001–T037 giữ ID và trạng thái chưa hoàn thành; thêm T038–T047. Chưa chạy Maven/FE/browser/SQL/Flyway; SQL quickstart cần rehearsal thật ở T030. Không commit/push/deploy, không sửa FE/APP. Gate plan/API/DDL/quyền client và APP impact còn phải có bằng chứng trước thực thi theo scope.


## Spec015 — fix G1/U1/U2 sau analyze tiếp theo, 09/11/2026

- User “ok fix”: chỉ sửa artifact, giữ T001–T047/trạng thái, thêm T048–T050. Đồng bộ spec/domain/plan/research/data-model/contract/quickstart/analysis và coverage table; không tái sinh tasks hoặc chạy speckit-tasks.
- T048 chuyển consumer hồ sơ lớp/requireIssuedPlan/isSuperseded/context/copy/lock sang TEAM và rà nguồn lesson-query/observation; T049 legacy NULL nằm ngoài cập nhật tự động bằng FK, không đoán/gán năm/baseline; T050 chuyển nguồn tăng revision/vô hiệu lease/session nguyên tử, form cũ conflict, giữ input, replay/rollback không mất dữ liệu.
- Native Python assert trực tiếp: 11 Markdown files, FR17+SC6, T001–T050 duy nhất/đủ/unchecked, 23/23 yêu cầu mapped; T048–T050 có implementation/check/dependency trước T035/T009; local links/fences/UTF-8/trailing whitespace đạt. git diff --check phạm vi spec015 exit0; artifact untracked được kiểm riêng bằng Python.
- Coverage tài liệu không phải test/code coverage; chưa chạy Maven/FE/browser/SQL/Flyway hoặc sửa source/client/migration. Không commit/push/deploy; phê duyệt plan/API/DDL/quyền client theo scope vẫn cần trước thực thi.

## Spec016 — triển khai một phần, 09/11/2026

- Thêm kiểm gửi năm (9 chủ đề, ngày/giới hạn, mục tiêu/nội dung), phân bổ tổ, sửa nội dung tổ và giữ chỉnh sửa khi nguồn năm đổi; FE thêm kiểm tra, dấu bắt buộc và khôi phục nháp. Chưa hoàn thành bỏ lease/heartbeat, thứ bậc duyệt và toàn bộ ca tích hợp.
- `mvn -Dtest=InheritedCurriculumGuardTest,EduTeamContentMergeTest,EduTeamAllocationTest,YearPlanPublicationValidatorTest,EduTeachingCalendarTest test`: 16 test đạt. FE: `node --test tests/education-plan/submit-requirements.test.mjs tests/education-plan/team-plan-ui.test.mjs`: 10 đạt; `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/school-year-team-mock.test.mjs`: 5 đạt; ESLint file sửa và `npm run build` đạt, Vite có cảnh báo phụ thuộc.
- Docker daemon không kết nối được; chưa chạy integration PostgreSQL, kiểm snapshot reload, toàn bộ test hoặc rehearsal Flyway. V91 mới chỉ là migration trong source, có thể tự chạy khi BE khởi động với Flyway; chưa áp dụng hay xác minh trên database đang dùng. Không xác nhận tính năng hoàn tất.

### Tiếp tục bỏ khóa phiên

- Theo chỉ đạo user, năm/tổ chỉ người được chỉ định soạn, không lấy/gia hạn/nhả lease 90 giây; giữ kiểm revision và request id. Tắt job nhả khóa, giữ các endpoint cũ tương thích, không hiện editor/expiry trong response. Bản lớp tiếp tục kiểm giáo viên chủ nhiệm theo phân công hiện hành.
- `mvn -Dtest=EduYearPlanEditingTest,InheritedCurriculumGuardTest,EduTeamContentMergeTest,EduTeamAllocationTest,YearPlanPublicationValidatorTest,EduTeachingCalendarTest test`: 17 test đạt. `npm run test:edu`: 316 test đạt; ESLint các file FE thay đổi và `npm run build` đạt. Chưa kiểm DB/migration hoặc chạy integration khi Docker chưa kết nối; chưa áp dụng quy tắc thứ bậc cố định trên toàn bộ các đường duyệt. Không đánh dấu hoàn thành feature.


## Đối chiếu task giáo dục — 10/11/2026

BE chọn lọc 44/44 (PostgreSQL Testcontainers); FE giáo dục 320/320; ESLint source sửa và build FE exit0. Đã kiểm chuỗi PHT-only/TT→PHT, thiếu ứng viên giữa/cuối, policy reconcile, reorder save/preview, snapshot unknown và năm xóa mềm. BE mở rộng trước đó còn 5 failure/25 error trên 49 ca do fixture/lease/schema chưa đồng bộ; không coi full BE đạt. Chưa browser/E2E hoặc áp dụng migration V94 lên DB người dùng. Chi tiết ở workspace root: `huong_dan_nghiep_vu/ket_qua_trien_khai_task_ke_hoach_giao_duc_2026-11-10.md`; tasks T002/T006/T015/T019/T020 còn mở.


## Tiếp tục EDU-64 và cấm xóa tổ — 10/11/2026

- BE: 78/78, 0 failure/error, exit0; gồm 33 ca workflow integration (21 tổ hợp cấm xóa tổ), 5 ca future-edit và 7 ca schema. PostgreSQL Testcontainers, không DB runtime.
- FE: 325/325 test giáo dục, exit0; ESLint bảy source sửa exit0; build FE exit0 (còn cảnh báo phụ thuộc hiện có).
- Diff BE đạt; diff toàn FE còn dòng trắng cuối file `src/styles/modules/education-plan.css` trong thay đổi ngoài phạm vi lượt này, không tự sửa CSS đó. Diff các source của lượt tiếp tục được kiểm riêng.
- Không commit/push, restart/deploy, thêm dependency hay áp dụng migration lên DB thực. Chưa kiểm browser/E2E. T002/T015/T020 vẫn mở như mô tả.

Chi tiết lệnh và AC: báo cáo workspace `huong_dan_nghiep_vu/ket_qua_trien_khai_task_ke_hoach_giao_duc_2026-11-10.md`. Đã thực hiện T006/T019: chỉ sửa phần tương lai, reopen đúng dossier, giữ evidence publication trong history; FE/mock cùng quy tắc. Sửa fixture schema qua initializeOwner, không nới constraint.


## Kiểm tra trước commit/push theo user — 10/11/2026

- Maven native chọn lọc: 90/90 test, không failure/error/skipped, exit0. Bộ EDU-72/EDU-64/workflow/schema thêm EduYearPlanEditingTest, InheritedCurriculumGuardTest, EduTeachingCalendarTest. Không khẳng định full BE đạt; annual integration cũ còn khoảng trống đã ghi ở trên.
- EDU-72: tự tạo TEAM DRAFT gắn năm/tuổi/phiên bản mới nhất, giao tổ trưởng duy nhất; activation không trùng/không lùi nguồn. Có regression ban hành thực, giữ nháp và giữ tổ đã ban hành làm nguồn lớp.
- FE giáo dục/menu: 328 test thành công; lint source thay đổi exit0 (một warning availableCreators), build exit0. Chưa browser/E2E/restart.
- User đã yêu cầu commit/push hai repo; BE dùng G94-326, FE user xác nhận G94-278. File docs/TAI_KHOAN_DEMO.md chỉ ghi tài khoản giả local được user yêu cầu; không đưa dump/token/.env vào commit.
- DB test đã reset theo phạm vi user chọn; 89 bảng khác giữ nguyên. Đã kiểm 34 tài khoản qua Keycloak/accounts/me; gateway login runtime trả401, không khẳng định đã sửa gateway.
