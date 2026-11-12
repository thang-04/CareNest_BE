# Quickstart kiểm chứng — feature012

**Thiết kế delta Q4=A/Q5=A cập nhật 07/11/2026:** mẫu nhánh PHT tùy chọn lấy/không lấy và sửa tên bản lớp; đổi thứ tự chuyển cả khối chủ đề kèm nhánh, giữ số tuần học, dời ngày. Thiết kế đã cập nhật theo quyết định mới, còn draft chờ duyệt triển khai; không giữ ràng buộc khóa tên hoặc bắt lấy đủ mẫu cũ.

Đây là guide cho triển khai sau khi plan được duyệt, không phải kết quả tính năng đã chạy. [Plan quản trị](../../docs/plans/active/2026-11-07-school-year-team-plans.md), [API](contracts/education-plans.md), [data model](data-model.md). Commands chạy từ root tương ứng; PowerShell Windows dùng `mvnw.cmd`/`npm.cmd` khi cần.

## Điều kiện

- User duyệt plan làn L, migration/API delta và cấp quyền sửa FE riêng trước client phase. Có tasks/analyze không blocker theo feature-spec-loop; lệnh plan không tạo tasks thay bước đó.
- Java21, Maven wrapper repo, Node22, Docker/Testcontainers PostgreSQL17 theo README. Không cài/thay dependency/version.
- Dữ liệu synthetic: một năm học, hai campus, hai nhóm tuổi/lớp, HT/PHT/TL/GV có phân công hiệu lực, người ngoài scope và người assignment hết hạn. Nạp credential thử qua biến môi trường, không đưa vào chat/log/docs. Không dùng DB thật.
- Migration test có DB trước V90 (V89 đầy đủ fixtures) và DB migrate mới; giữ IDs/checksum các bảng legacy và publication JSON trước/sau. Reuse Testcontainers fixtures/config của suite hiện có.

## Lệnh kiểm chứng từng phase

```powershell
# Chạy từ CareNest_BE, sau source/migration phase 1 hoặc 2
./mvnw.cmd '-Dtest=EduPlanLevelMigrationIntegrationTest,EduYearPlanServiceIntegrationTest,EduWorkflowServiceIntegrationTest,EduWorkflowAccessPolicyTest' test
./mvnw.cmd '-Dtest=EduTeachingCalendarTest,EduBranchRangeIntegrationTest,EduClassDocServiceIntegrationTest,BranchFileImportTest,ThemeEvaluationIntegrationTest,EvaluationLockOrderTest' test
node scripts/verify.mjs
node scripts/check-ai-layer.mjs
git diff --check
```

Ba test mới `EduPlanLevelMigrationIntegrationTest`, `EduTeachingCalendarTest`, `EduBranchRangeIntegrationTest` được tạo trong implement. `ThemeEvaluationIntegrationTest` hiện có cả coverage phiếu cuối năm, mở rộng tại đó thay tạo suite trùng. Exit yêu cầu toàn bộ test đúng phạm vi chạy thật, `VERIFY PASS full`, fail/error/skip0, ArchUnit/snapshot/Spotless ok. Trước implement lệnh có class mới chưa tồn tại không được tính là fail regression của source hiện tại.

```powershell
# Chạy từ CareNest_FE sau khi được phép sửa và triển khai client
npm.cmd run verify
# Bổ sung browser E2E theo bộ tests/e2e hiện có: năm → tổ → lớp → tuần/ngày
```

Exit FE: command exit0, tất cả test thuộc feature fail0/skip0; browser chạy với BE thật và Keycloak/test fixture riêng theo pattern feature011. Lệnh browser cụ thể và test case names sẽ ghi vào tasks khi test được tạo, không lấy ba ca workflow011 làm bằng chứng feature012.

## Ma trận ca nghiệm thu

| Ca | Thao tác / expected outcome | FR / AC |
| --- | --- | --- |
| Q01 | Tạo annual đúng nhóm tuổi PHT, HT duyệt; TL không mặc định tạo annual; sourceMode ROOT, không ticks | FR1–7, AC1–3 |
| Q02 | Tạo TEAM từ annual approved đúng scope, TL soạn/PHT duyệt; giáo viên chỉ dùng TEAM sau ban hành | FR6–9/14, AC3/4 |
| Q03 | Cố tạo TEAM nguồn annual chưa duyệt/khác tuổi/campus ngoài quyền, giả legacy flag hoặc dùng annual cho class doc; reject, không side effect | FR7/8/13/14 |
| Q04 | TEAM inherited tick/bỏ tick một content, lưu/mở lại giữ đúng; câu chữ/cha/order/IDs/các content khác không đổi; content trống không tick | FR16, AC8/9, EDU-50 |
| Q05 | PUT/import thay text, thêm/xóa/omit/reparent/reorder content/goal, sửa source pointer hoặc snapshot; reject trước mutation/author event/revision bump | FR13/16, AC6/8 |
| Q06 | Annual có mẫu nhánh; GV lấy và đổi tên bản lớp được, source identity hợp lệ giữ lại, mẫu nguồn không đổi; thêm custom theo phạm vi. GV được không lấy/bỏ chọn mẫu theo Q4=A; giả source/ngoài scope vẫn chặn | FR9, AC4 |
| Q07 | Nhánh 1/2/3 tuần → đạt/đạt/chặn; lớn 1/2/4/5 → chặn/đạt/đạt/chặn; T2–T6, không overlap, nhánh trong cha, lớn trong năm | FR10/11/15, AC5 |
| Q08 | Hai tuần học cách nhau tuần nghỉ toàn bộ tính hai; nghỉ lẻ vẫn có tuần; đủ ngày còn học, ngày nghỉ không bắt soạn | FR12/15, AC5 |
| Q09 | HT đổi limits; PHT/GV bị chặn, stale revision không ghi, min/max sai bị chặn. Hồ sơ cũ giữ limits/legacy mode, hồ sơ mới dùng limits mới | FR11/13, AC5/6 |
| Q10 | Một nhánh hai tuần, tạo hai week docs/đầy đủ ngày, gửi/duyệt cùng theme dossier, tuần hai vẫn có hoạt động/MT đánh giá | FR12, SC5, EDU-17/19 |
| Q11 | GV đổi lịch lớp A khi branch nháp; lớp B/source dates không đổi, import preview/tuần/in dùng lịch A. Review/approved không sửa, stale calendar/version reject | FR7/10/15 |
| Q12 | Hai cấp cùng year/age/version1, current/version/queue đúng loại, year policy vẫn TEAM, schoolYear PHT→HT; HT đổi một loại không đổi bản đã ban hành/loại khác | FR6/7/14 |
| Q13 | Legacy IDs/docKeys/goal lineage/source predecessor/history/print unchanged sau migration, đọc version cũ/in được, bản đang chờ vẫn đi EDU-54 | FR13, AC6 |
| Q14 | Đánh giá chủ đề dùng đúng lớp/ngày hiệu lực, issuer/phiếu cuối năm từ TEAM; sau confirm rồi ban hành TEAM mới bỏ chủ đề, list/get/link ID cũ và childThemeResults vẫn đọc frozen cells/name/date/goal columns; trẻ từng chuyển lớp dùng lịch từng lớp | FR10/12/13, SC4 |
| Q15 | Import nhánh preview một range hai tuần thành một match, không đổi mẫu/lịch theo file; hủy/error không ghi, export đúng hai tuần | FR9/12/16, EDU-47 |
| Q16 | Không tự duyệt: người PHT soạn annual không duyệt annual; PHT có thể duyệt TEAM do TL soạn vì author ledger tách target; đa vai trò và assignment inactive đúng EDU-54 | FR6/7 |
| Q17 | Annual v1→v2, tạo version TEAM với source mới tường minh remap theo lineage, warnings cho tick/template không còn đích, không orphan; legacy TEAM chọn annual để tạo INHERITED version; absent body giữ clone cũ, snapshot bản issued không đổi | FR13/14/16, EDU-25/26/54 |
| Q18 | Chủ đề A2 tuần/B4 tuần có nhánh riêng, gồm nhánh lấy từ mẫu đã đổi tên và nhánh tự tạo. Đổi B trước A: vẫn B4/A2, nhánh đi theo đúng cha và giữ số tuần, ngày dời theo thứ tự mới; tên/nội dung nguồn/IDs/liên kết không đổi, lưu/mở lại đúng; không tự sửa giáo án/học liệu đã ban hành | FR17, SC6, AC11 |
| Q19 | branches rỗng vì không lấy mẫu: nhập file tạo CREATE_CUSTOM cho tuần trống, lấy một phần/đổi tên/bỏ mẫu rồi lưu/mở lại không auto-add. Preview reorder/import chậm sau rename/chọn mẫu/restore/source switch bị bỏ; save đang chạy không làm mất phần sửa tiếp | FR9/16/17, AC10/11 |

## Vectors calendar/preview bắt buộc của delta

| Vector | Input / invariant | Expected |
| --- | --- | --- |
| R1 | A2/B4 liên tiếp, branches có1/2 tuần, tên local khác mẫu, nhánh2tuần có hai weekNotes khác nhau; order B,A | B4/A2, key/source/theme/ticks/name không đổi, branch offsets/counts/notes theo đúng tuần giữ, notes projection chuyển sang Monday mới; hai tuần hai docKeys |
| R2 | Hai break riêng phủ đủ T2–T6, hoặc một break trọn tuần; partial-break chỉ1–2 ngày | Union/full week bỏ khỏi counts; partial week vẫn tính; nhánh2tuần vẫn2 khi dời qua tuần nghỉ |
| R3 | Permutation thiếu/trùng/foreign theme, stale branch version, sai class/TEAM/source | Reject, không form/DB/history/ledger/revision side effect |
| R4 | Khoảng trống tuần học giữa themes; chia lại slot làm range chứa thêm tuần ngoài axis | Calendar-invalid với lý do; không auto-pack/nén/lấp gap hay tạo break; base và limits giữ nguyên |
| R5 | Ngày/branch range chưa đủ để tính, hoặc ngoài năm/cha/overlap | Preview báo không tính được, giữ form; save nháp thường vẫn theo rule hiện có, submit chưa đủ bị chặn |
| R6 | Preview/apply/reorder cùng order hai lần; PUT có orderedThemeIds và PUT resize không field đó | Idempotent khi input đã retime; resize hợp lệ FR-010 vẫn dùng được; projection readonly không bị persist |
| R7 | Version TEAM>1, đổi thứ tự trong branch nháp; draft/approved week docs cũ tồn tại | Không rename khóa wp_class_Monday_TEAM hoặc move media; dependencies lệch lịch hiện unresolved, không biến mất khỏi response |
| R8 | Không chọn mẫu + import file có tên; snapshot minBranchWeeks>1 | CREATE_CUSTOM từ tuần trống, không tự lấy mẫu/ghép tên hoặc dates từ file; warning duration để user chỉnh trước gửi |

Các vectors là ca nghiệm thu/test dự kiến, không phải kết quả đã chạy. Test helper/preview phải chứng minh không mutate base object và rollback lỗi; API preview mới tuyệt đối không persist hoặc tạo author ledger.

## Concurrency, performance và khôi phục

- Tạo hai phiên bản mở cùng loại đồng thời → chỉ một thành công; hai loại cùng year/age không chặn lẫn do unique cũ. Source eligibility đọc lại trong transaction, không tin preflight FE.
- Concurrent policy/save/approve/branch calendar/week action/media mutation: không deadlock từ lock-order đảo; stale request không partial write; approver cuối và policy reconcile dùng cùng publication validator.
- Fixture danh sách ít nhất 24 hồ sơ giữ benchmark feature011 `<100` SQL, không query mỗi lớp/tuần trong vòng lặp; cả hai cấp và lịch v2 được tính. Không gọi đây là SLA user-facing hoặc báo tỷ lệ coverage chưa đo.
- Chụp counts/checksum legacy và version metadata trước/sau migration. Dữ liệu annual/v2 đã ghi → forward fix/compatible binary, không chạy binary cũ ghi đè payload. Không có lệnh xóa/reset DB hoặc restore backup tự động trong guide.

## Bàn giao và convergence

Sau tasks/analyze, implement theo phase và dừng review từng phase của plan. Browser target Q01–Q19; ghi command/output/log thật vào VERIFICATION/Progress log; fail/skip/thiếu credential phải nêu rõ. Analyze/converge chưa chạy trong lượt plan này. Khi implement xong lặp converge append-only tới sạch, vòng sạch không đổi tasks.md; giữ lịch sử evidence cũ, không lấy quick docs check làm chứng minh feature hoạt động.

## FE review trước backend — 07/11/2026

- User cho phép sửa CareNest_FE trước, yêu cầu không mở trình duyệt và dừng chờ review. Scope review đã triển khai: route `/education/school-year-plans`, menu trên TEAM, annual PHT→HT, TEAM TL→PHT trong mock; cột Chủ đề nhánh / dự án ngay phần2 annual, Enter thêm nhánh; TEAM giữ cột map mục tiêu; annual curriculum bốn cột, TEAM cây kế thừa cố định nhưng tick từng content. FR018/EDU57 cấm PHT xóa TEAM ở UI và fake backend, các chức năng khác giữ nguyên.
- Nhánh v2 giữ UUID/source/name, chọn/bỏ mẫu/tự thêm, range nhiều tuần/ghi chú offset, đổi thứ tự qua facade preview (pure mock hoặc API contract), manual save và late-response guard; các màn lớp/tuần đọc lịch lớp đã duyệt, không tự di chuyển week doc/media. Cấu hình HT-only có revision và snapshot không hồi tố; explicit source rebase giữ tick theo lineage, có warning khi đích đã mất.
- Implement→converge vòng1 phát hiện T075 orphan templates/T076 các mẫu name-only cùng tuần đầu/T077 legacy bị áp snapshot mới; đã sửa và kiểm chứng. Regression T077 từng FAIL expected LEGACY_UNBOUNDED, actual BOUNDED trong `CareNest_FE/target-feature012-red.log`, sau sửa GREEN. Vòng2 T078 giữ tick khi đổi annual source và hiển thị warning đã làm/kiểm. Vòng cuối phạm vi FE review không còn gap code phát hiện; không thêm phase rỗng.
- Native tests: `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs tests/menu/*.test.mjs` →405 tests,405 pass,0 fail,0 skip. Loader chỉ để Node resolve module alias; không chạy orchestrator/harness dự án.
- ESLint trực tiếp trên source được thay đổi (danh sách `CareNest_FE/target-feature012-files.log`) →exit0, không warning. `node node_modules/vite/bin/vite.js build` →exit0, built5.00s; có cảnh báo dependency/plugin/annotation sẵn có, không đổi dependency. Source format bằng Prettier trực tiếp, exit0.
- Logs local (Git ignored): `CareNest_FE/target-feature012-tests.log`, `target-feature012-build.log`, `target-feature012-red.log`. Không chạy wrapper verify/check-ai-layer, không browser/E2E/ảnh QA, không commit/push/deploy. Không dùng kết quả mock để khẳng định API thật đã có hoặc FR018 đã chặn ở server.
- T069–T073/T075–T078 đóng theo bằng chứng FE review. T001–T068 và T074 giữ pending tới implementation/integration tương ứng, không suy done từ source FE. Backend vẫn chờ user duyệt giao diện, không thực thi migration/API.
- User tự mở FE đang chạy: `/education/school-year-plans` (năm), `/education/goals` (tổ), `/education/branches` (nhánh), trang cấu hình giáo dục từ menu. Bản review đầy đủ thao tác cần mock (`VITE_USE_MOCK=true`); API mode cần backend mới sau review. Vite mặc định port5173, `open:false`; agent không tự khởi động hoặc mở trình duyệt.

### Sửa tương thích API cũ sau review —07/11/2026
User mở trang báo404 module-wide. Active mode dùng API thật, duration-policy chưa có trong BE. T079 xử lý riêng404 của endpoint mới, giữ dữ liệu API thật và luồng TEAM/v1 cũ; cấu hình mới khóa khi backend chưa hỗ trợ. Không đổi env hoặc tự chuyển mock. Tests tái hiện404 đã RED→GREEN; bộ education+menu409/409, ESLint source đổi exit0, Vite built3.57s exit0. Logs FE `target-feature012-fix-tests.log`/`target-feature012-fix-build.log`. Chưa mở browser/API phiên user để xác nhận runtime. Chỉ bản review mock có đủ annual/range mới trước khi BE được duyệt và triển khai.

### FE review — nút tạo annual cho PHT,07/11/2026
- Bỏ durationPolicyAvailable khỏi điều kiện hiện nút/quyền soạn preview annual, fallback annual PHT→HT; explicit schoolYearCreationScopes rỗng vẫn chặn. Nút mở form không phụ thuộc yearCreationScopes của TEAM; API chưa có thì form hiện rõ preview và chặn save/submit, không chuyển mock hoặc bypass server.
- SSR regression đã RED absent link→GREEN; `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/*.test.mjs`→231pass/231,0fail/0skip. ESLint GoalListPage/GoalFormPage exit0; Vite build exit0. Logs FE target-feature012-create-tests.log và target-feature012-create-build.log. Không browser/API session validation; backend vẫn pending user duyệt. T080 complete, incident BUG-261107-edu-required-future-endpoint cập nhật.

## Nghiệm thu delta nhãn và hai cấp — 07/11/2026

- Nhập “Mục tiêu 01” vào nhãn annual, kéo qua hai goal kế tiếp:02/03; hủy không đổi, bàn phím có hành vi tương đương. Lưu/tải lại và export giữ nhãn, IDs/số không đổi.
- Gửi PUT client cũ không label: nhãn giữ nguyên. Gửi empty: trở về MTn. Label>100 bị validation chặn.
- Clone annual→TEAM/version giữ nhãn; TEAM không nhập nhãn/cây nguồn, bỏ nút xóa; vẫn tick nội dung. TEAM xám STT đồng nhất, subdomain vàng khóa.
- Hai form/detail info→goals→themes. Lịch nhánh chung component chủ đề, min/max trong cha; thiếu ngày cha khóa.

### Kết quả delta nhãn/UI07/11/2026

BE64/64 native4classes, fail/error/skip0; PostgreSQL V92→V93 upgrade + label roundtrip/compatibility/clone/guard/size. Runtime migrate86/start/healthUP, OpenAPI schemas đúng và đã cập nhật. FE248/248 + ESLint/Prettier/Vite exit0, input/pointer/keyboard/cancel/capture + API/mock/in tests. Independent review2P2 đã sửa; convergence delta FR019/020 không còn gap triển khai phát hiện; chưa browser/E2E/fullBE/touch thật. Giữ pending gaps feature012 cũ. Xem docs/quality/VERIFICATION.md; không harness/Git/deploy.

### FR021 — kết quả07/11/2026
BE71/71 native4classes, PostgreSQL; FE254/254, lint/format/Vite exit0; review độc lập không blocker. Kiểm hai endpoint compact/default, sửa đầu riêng, warnings/taken, sibling overlap/weekday và mock/server draft-vs-submit. Converge delta không gap theo source/tests; chưa browser/E2E/fullBE/touch thật, không harness/Git/deploy.
