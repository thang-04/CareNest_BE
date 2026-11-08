# Quickstart validation — feature014

Đây là hướng dẫn chạy sau triển khai, chưa là kết quả test. Xem [contract](contracts/curriculum-cascade.md), [data model](data-model.md) và [plan](plan.md).

## Điều kiện

- User đã cấp quyền sửa giáo dục FE014 và yêu cầu implement→converge ngày 08/11/2026. Sau khi delta sửa nguồn được trình, user gọi implement các task còn lại; V87 và mock đã được triển khai và đang kiểm chứng.
- Node/node_modules của Web hiện có; không tự install/upgrade dependency. BE cần JDK21/Maven wrapper; integration Testcontainers cần Docker/PostgreSQL17.
- E2E cần môi trường test được phép, config/credential qua env ignored, tài khoản có phân công hợp lệ; không in secret hoặc dùng dữ liệu trẻ thật.
- Không tự restart user server hoặc migrate DB thật; nếu cần môi trường riêng thì chỉ tạo khi đã được phép theo phạm vi triển khai. Không sửa auth/test để bỏ qua lỗi setup.

## Lệnh native

Chạy tại CareNest_FE (PowerShell, mỗi dòng là lệnh riêng):

~~~powershell
node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/curriculum.test.mjs tests/education-plan/branch-template-enter.test.mjs
node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs
node ./node_modules/eslint/bin/eslint.js src/components/education-plan/CurriculumGoalsModal.jsx src/components/education-plan/GoalTables.jsx src/models/education-plan/curriculum.mjs src/hooks/education-plan/useCurriculum.js src/pages/education-plan/GoalFormPage.jsx src/services/education-plan/mock/curriculumMockRepository.js
node ./node_modules/vite/bin/vite.js build
node tests/education-plan/curriculum-cascade-browser.mjs
~~~

Browser fixture curriculum-cascade-browser.mjs là output triển khai dự kiến, chưa tồn tại ở lượt plan. Mong đợi Node fail/error/skip0, lint0error, buildexit0 và browser fixture đạt các ca browser trong ma trận (Q12 kiểm riêng bằng live suite). Mọi file test/component mới hoặc fixture sửa phải thêm vào lint/test phù hợp khi tasks chốt, không bỏ các test đang có.

Live E2E tại FE, chỉ khi env test đã cấu hình và services có sẵn:

~~~powershell
node --env-file=.env.e2e.local ./node_modules/@playwright/test/cli.js test tests/e2e/system/curriculum-cascade.spec.mjs --project=system --no-deps
~~~

curriculum-cascade.spec.mjs là suite mới cần tạo ở implementation, dùng SCHOOL_YEAR + actor creator/reviewer có phân công/policy hiệu lực, route annual và3trạng thái hiện hành; lấy pattern từ published-year-plan-visibility.spec.mjs. Không sửa global policy môi trường user. Hai suite ministry/subdomain legacy tạo TEAM/expect EDITING nên không đủ bằng cách đổi selector. Mong đợi import mục tiêu/nội dung, save/reload/version và filter mới đạt. Không coi pass suite cũ là coverage014 nếu chưa thêm assertions. Nếu env/test data không sẵn, báo chưa chạy cùng lý do; không ghi pass.

BE contract regression tại CareNest_BE, khi cần xác nhận response nguồn/provenance hoặc implementation phát sinh thay đổi mapping:

~~~powershell
./mvnw.cmd '-Dtest=EduCurriculumControllerTest,EduCurriculumServiceIntegrationTest' test
./mvnw.cmd '-Dtest=EduYearPlanServiceIntegrationTest,SchoolYearTeamPlanIntegrationTest' test
~~~

Lệnh BE thứ hai chỉ cần khi có thay đổi/lo ngại save/provenance/immutable nguồn; không tự chạy toàn suite. Mong đợi BUILD SUCCESS, tests không fail/error; kiểm Surefire skip vì Testcontainers disabledWithoutDocker có thể skip, không gán là đã kiểm DB.

## Ma trận nghiệm thu

| Ca | Kịch bản và kết quả mong đợi | Trace / tầng |
| --- | --- | --- |
| Q01 | Hai lĩnh vực, mỗi bên hai con: lọc A/A1 chỉ còn nhóm/mục tiêu đúng nhánh và tuổi | AC01/FR001–003/SC001; helper+browser |
| Q02 | Chọn đích A1 rồi chuyển A2: bỏ đích/selection sai, chặn confirm; contents/ticks đã thêm giữ nguyên | AC02/FR004–005/SC001–002; helper+browser |
| Q03 | Đổi cha mà con vẫn hợp lệ: giữ con, không tự chọn mục đầu | AC02/FR004; helper |
| Q04 | Focus ô nhóm khi query rỗng: mở nhóm đúng nhánh; gõ không lộ nhánh khác | AC03/FR006/SC003; browser |
| Q05 | Nhóm R1/R2 có tên trùng: ô goal chỉ nhận goal R1, chọn giữ provenance; mock source IDs ổn định/không va chạm cả4tuổi, mock save/reload/clone/version giữ nguồn, raw seed không mutate và goal.id numeric giữ nguyên; API mode dùng IDs thật | AC04/FR007/SC003–004; helper+browser+save |
| Q06 | Ô content của R1 hiện hợp contents của các goal R1, không nội dung R2/C, không tự tick | AC05/FR008/SC003–004; helper+browser |
| Q07 | Chưa chọn gợi ý/gõ tìm/đổi cha: dòng khác và nội dung đã có không bị thay; trùng theo cơ chế hiện có | AC06/FR005/010/SC002; helper+browser |
| Q08 | Tải scope cũ muộn/retry lỗi: không hồi sinh lựa chọn cũ, giữ bản soạn | AC06/FR009; browser controlled service fixture |
| Q09 | ALL khác NONE; dòng trước sub đầu, thiếu group, source/local IDs khác và tên trùng | AC08/FR012; helper+browser |
| Q10 | Q10a: dòng tay chưa linked có hướng dẫn/chọn nguồn tường minh, không đoán tên — nghiệm thu tại T020. Q10b: readonly/TEAM không sửa source hoặc gọi catalog mới chỉ vì render — test T021, guard T024 và nghiệm thu tại T025 | Q10a: AC08/FR012; Q10b: AC07/FR011; browser+BE guard hiện có |
| Q11 | Keyboard Tab/Up/Down/Enter/Escape, IME và pointer/touch: chọn đúng một lần, không Enter chèn thêm dòng; mọi kết quả khớp truy cập được | AC03–06/FR006–010; browser1440/390 |
| Q12 | API thật: chọn nhóm→goal→content, lưu và tải lại/version giữ text/nguồn/ticks; nhóm tuổi khác không lẫn, bản trước giữ nguyên | AC01–08/SC001–004; liveE2E |

## Bằng chứng source Bộ bổ sung

[Audit cột tuổi 851 chuỗi](audit/age-column-review.md) và [delta sửa nguồn](source-correction-plan.md) tách rõ 842 ca tự động, 9 ca xem render, 33 dấu hai chấm và câu 5 - 7 bị tách sai. V87 và mock đã sửa các sai lệch này; T032/T035 tiếp tục kiểm chiều PDF→catalog và cấu trúc. Evidence hiện hành xem `docs/quality/VERIFICATION.md`.

## Bằng chứng cần ghi

Ghi cwd, lệnh, exit code, số test/fail/error/skip, browser viewport và phạm vi API thật/fixture. Giữ RED trước sửa cho stale target/hidden selections và GREEN sau sửa; lỗi fixture/setup không là RED nghiệp vụ. Cập nhật incident open chỉ khi có evidence; chưa code/runtime thì không ghi fixed.

Không chạy project harness/verify/check wrappers. Chỉ kiểm cấu trúc/link/diff trong lượt plan; chưa có test sản phẩm014 được thực thi.

## Baseline / approval — 08/11/2026

User duyệt implement→converge đến sạch sau analyze0finding; quyền FE014 đã cấp. Giữ checkout BE feature/G94-326-BE-FEAT-09-school-year-goals-backup và FE feature/G94-278-FE-FEAT-41-lesson-assessment-parent-backup, không tạo branch/commit. Cả hai có thay đổi từ trước, không reset. Ignore BE target/log/env, FE node_modules/dist/env/log và ESLint config ignores đã có; không sửa ignore ngoài scope. Node dependencies có sẵn, test PostgreSQL container55439 và Keycloak8180 đang chạy; chưa restart server user. Checklist16/16, con trỏ014, không extensions/harness/setup.
