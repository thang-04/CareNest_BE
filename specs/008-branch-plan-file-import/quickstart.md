# Validation guide — Feature 008

Status: hoàn thành feature, 22/22 task gồm bổ sung mẫu/modal ngày 02/11. Hướng dẫn là cách tái kiểm chứng; kết quả đã chạy tách riêng ở Evidence log.

## Điều kiện

Plan làn L/API được user duyệt; Java 21, Node, Maven wrapper và Docker cho Testcontainers.
Fixture Word .doc/.docx tổng hợp, không dữ liệu người thật; kế hoạch đã ban hành có chủ đề/tuần/nghỉ và giáo viên được phân công.

## Automated checks

1. TDD parser: test nhánh/goal .doc thất bại đúng vì OLE2 chưa hỗ trợ trước implementation; sau sửa đạt cùng ca. Kiểm ô gộp, lặp số nhánh, hỏng/mã hóa/quá cỡ, toàn bộ định dạng cũ.
2. Service/MockMvc: scope lớp, trạng thái, đúng năm/nhóm tuổi/phiên bản; không ghi DB khi preview; mapping không nhận ngày file, không tràn chủ đề, cảnh báo mơ hồ/thừa.
3. Web node:test: patches chỉ đổi key khớp, Hủy không đổi, giữ key ngoài match, xác nhận overwrite, AI pending bị settle ở key thay, caller khác của ReviewActions không đổi.
4. `node scripts/verify.mjs` tại BE: kỳ vọng VERIFY PASS full, skip 0, ArchUnit/snapshot/Spotless ok.
5. `node scripts/verify.mjs` tại FE: kỳ vọng VERIFY PASS full. `node scripts/check-ai-layer.mjs` tại repo có sửa tài liệu: kỳ vọng exit 0.
6. Sinh lại snapshot OpenAPI có chủ đích cho endpoint/DTO/mã lỗi mới, review diff; không regenerate chỉ để che lỗi.

## Browser checks

Chọn file → xem danh sách tuần hệ thống/cảnh báo → Hủy: tên và dirty state không bị đổi.
Nhập file có ngày năm khác: ngày và lịch nghỉ hiện tại không đổi. Chủ đề lạ/tên thừa không nhận sang chủ đề sau.
Đích có tên: xem phần sẽ thay → Hủy giữ nguyên → xác nhận thay đúng key → Lưu → mở lại giữ kết quả.
Còn tên AI pending: nhập thay rồi lưu không khôi phục tên cũ; giữ/bỏ AI của key khác hoạt động.
Nháp/Bị trả lại/Chờ duyệt/Đã duyệt và người xem: hai nút đều hiện, lý do khóa rõ, chuỗi/người nhận duyệt đúng; backend vẫn chặn trái quyền.
Bước 2 chọn .doc mục tiêu: xem trước/thêm/bỏ trùng/lưu như cũ. Hồi quy .docx/.xlsx/.csv/.doc HTML.

## Evidence log

### Bổ sung 02/11/2026 — mẫu tải xuống và modal cùng thiết kế

- T020–T022 append sau các task cũ. Thêm branchTemplateFile trong classPlanDoc, dùng docShell/downloadDoc như mục tiêu; một dòng mỗi tuần học đang hiển thị, hai cột Chủ đề/Chủ đề nhánh, không mang tên nháp/ngày/người ký. Hàng không có tuần có mẫu bảng trống.
- Modal dùng cùng cấu hình width720/size lg, hàng Chọn file/Tải file mẫu, footer và tối đa8 cảnh báo + số còn lại; giữ nguyên preview và xác nhận thay tên. Không sửa BE source, API, DB, thư viện hoặc quyền.
- RED: branch-import-template.test.mjs 0/2 (chưa có helper); branch-import-modal.test.mjs 0/2 (size/nút/cảnh báo). GREEN cả4/4; `node scripts/verify.mjs` FE → VERIFY PASS full, 346 test, fail0/skip0, lint/format/design/build đạt.
- Browser component harness với source/CSS thật: file tải `Mẫu nhập chủ đề nhánh.doc`, header đúng và3 dòng mẫu trống, không có date/tên nháp; nút chọn/tải cùng hàng, modal cùng chiều rộng thực với modal mục tiêu, mobile375 không tràn, 0 pageerror. Đã xem ảnh desktop/mobile tại FE/test-results/branch-import-template-*.png. Đợi animation/font ổn định trước so kích thước, không sửa CSS để che phép đo giữa animation.
- Đây là kiểm tra component/tải xuống và FE full gate; không gọi API thật hay chạy lại E2E nhập/duyệt ở lượt UI bổ sung. Bằng chứng real-BE trước đó được giữ riêng bên dưới. Harness/Vite5199 tạm đã dọn; không commit/push.
- Hội tụ bổ sung không còn gap FR-012/013/SC-006; giữ lịch sử T001–T019.

Kết quả dưới đây chạy trên source trong task này; không suy ra đã commit, merge hoặc deploy.

### Bàn giao 02/11/2026 — kiểm chứng chạy ngày 01/11/2026

- BE sau lần sửa source cuối: `node scripts/verify.mjs` → `VERIFY PASS full | tests 644 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 248s | log target/verify.log`.
- FE sau lần sửa source cuối: `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify | log verify.log`, 342 tests (98 menu + 50 kitchen + 46 merge + 30 hardening + 12 dashboard + 106 education), fail/skip 0; lint/format/design/build đạt. Có 14 test mới cho import patches/API/mock/nút, gồm CSV dùng chung và mã lỗi/quyền/trạng thái.
- OpenAPI diff đúng phạm vi: 76 dòng thêm cho endpoint preview nhánh và các schema response; không xóa hoặc đổi shape API cũ. Không migration.
- Browser real-BE: nhập nhánh 3/3 đạt trên jar cuối (gồm .doc/.docx khác ngày ra cùng tuần, Hủy/xác nhận thay/lưu/mở lại, không đổi lịch/notes/key khác, 7124/7125/7126/403, chuỗi Tổ trưởng→PHT, trả lại/gửi lại, mọi trạng thái/quyền xem có nút khóa). Màn 375×800 không tràn ngang, Lưu/gửi ở viewport trước/sau cuộn. Log target/feature008-e2e-final-branch3.log.
- Browser real-BE mục tiêu/import 17/17 đạt trên jar cuối, gồm .doc nhị phân bước 2 xem trước/Hủy/thêm/bỏ trùng/lưu/mở lại. Log target/feature008-e2e-final-goal17.log. Hồi quy edu-plan-flows 19/19 đạt ở lượt trước bản vá decoder, source luồng không đổi; log target/feature008-e2e-all.log. Tổng 39 kịch bản khác nhau có kết quả đạt, không gọi là một lượt 39/39 xanh.
- Đọc riêng file .doc gốc user cung cấp bằng probe cục bộ: 35 tên nhánh, 0 cảnh báo parser. Không in/copy tên người ký, không đưa file thật vào repo; tests/fixtures đều tổng hợp.
- Hội tụ vòng 1: P1 giới hạn lưới trước cấp phát → append T019; RED 7 assertion failures bằng input nhỏ, GREEN 20 tests liên quan, full gate cuối 644/644. Vòng 2: đối chiếu 11 FR/5 SC, không gap code còn lại; SHA256 tasks.md trước/sau converge giống nhau, không thêm tiêu đề/task rỗng. Implement đánh dấu hoàn thành 19/19 ở bước theo dõi riêng.
- Review độc lập không còn P1/P2 sau sửa guard và parity CSV mock. Knowledge: BUG-261101-file-grid-allocation-limits, BUG-261101-branch-csv-demo-slots, BUG-261101-e2e-goal-id-domains đều fixed.
- Runtime E2E riêng: BE8082 + PostgreSQL carenest-feature008-e2e/carenest_feature008_e2e, dùng Keycloak dev thật. Sau test e2e_plans=0, temporary_leader=0, shifted_assignment=0; đã dừng BE8082 và xóa container/volume riêng. BE8080/Keycloak8180 đang dùng giữ nguyên.
- Không commit/push/branch/deploy; local VITE_USE_MOCK=false không đổi config. Demo vẫn chỉ nhập CSV; Word/Excel dùng BE thật và modal có hướng dẫn.

### Các lần thử và sửa kiểm thử

- Lượt hồi quy đầu 34 passed, 1 failed, 4 chưa chạy: test EDU-24 so rowId với contentId chung Set trên DB mới gây false positive; tách miền ID, giữ kiểm ID mới cùng loại. Lượt mục tiêu cuối 17/17 đạt.
- Helper review E2E dùng substring bắt nhầm nút Gửi … duyệt luôn hiện thay Duyệt; dùng exact cho label string, regex giữ nguyên. Không đổi logic ứng dụng; branch suite cuối 3/3 đạt.
- Một lượt E2E bị ERR_CONNECTION_REFUSED asset Vite5181 và một probe resize giữ drawer cũ; chạy tuần tự với server sạch, dùng mobile context mới như suite hiện có. Lượt cuối có 3/3 branch + 17/17 goal, không tính các lượt lỗi là đạt.
- Guard XLSX/DOCX/HTML/CSV được thêm sau full gate 643/643; đã rebuild jar, chạy lại full gate 644/644 và import E2E trên jar mới. Không dùng kết quả cũ để thay kiểm sau sửa.
- Test UI SSR dùng transformWithOxc đã cài trong Vite8 thay vì API cần esbuild tách riêng; không thêm dependency. CSV parser tách nguyên trạng sang utils/csv để không import chéo module, giữ re-export tuyển sinh và test cùng hàm/quote/multiline/BOM.

### 01/11/2026 — Phase 1

- TDD RED: `mvnw.cmd -B test -Dtest=GoalFileImportTest#binaryDocMatchesDocx` → 1 test error, GOAL_FILE_UNSUPPORTED vì OLE2 chưa được đọc. Bản .docx cùng fixture đã qua các assert mục tiêu/ô gộp trước điểm fail .doc; log target/phase1-red.log.
- GREEN sau sửa cuối: `mvnw.cmd -B test -Dtest=GoalFileImportTest,EduGoalFileServiceImplTest` → 13 tests, failures 0, errors 0, skipped 0; log target/phase1-green.log. Gồm .doc/.docx tương đương, .doc không bảng/mã hóa/quá 2.000 dòng, >2 MB, sai đuôi, privacy metadata, các định dạng cũ và quyền/cấu hình service.
- Dependency tree: poi-scratchpad/poi 6.0.0, log4j-api 2.25.5, commons-math3 3.6.1, commons-codec 1.21.0 (Boot quản lý), commons-io 2.22.0; không nâng transitive khác. Log target/phase1-dependency-tree.txt.
- Review độc lập phase 1: không có P1/P2 chặn; không đổi merge mục tiêu hoặc shape API. Snapshot diff source hiện trống, full gate xác nhận riêng khi kết thúc.
- FE full verify: lần 1 dừng vì Prettier ở eduMockErrors.js; format đúng file và chạy lại → `VERIFY PASS full | npm run verify | log verify.log`.
- BE full gate: `node scripts/verify.mjs` → `VERIFY PASS full | tests 633 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 260s | log target/verify.log`. Sau lần sửa source cuối; `git diff --exit-code -- docs/api/openapi.yaml` exit 0.
- `node scripts/check-ai-layer.mjs`: BE 0 lỗi/12 cảnh báo cũ; FE 0 lỗi/14 cảnh báo cũ. Diff whitespace không lỗi.
- Phase 1 đạt AC-5 và phần AC-6 thuộc bộ đọc .doc; review độc lập không có P1/P2 chặn. Không gọi toàn feature đã hội tụ: nhập nhánh/nút cố định và E2E còn T007–T018 ở phase 2/3 đã lập kế hoạch, không thêm task trùng.
- Browser/E2E và .doc trong chế độ mock Web chưa kiểm chứng ở phase này; mock vẫn chỉ đọc CSV như trước. Không commit/push/branch.
