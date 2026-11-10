# Tasks — Feature009

Mỗi task giữ ID; phase phụ thuộc tuần tự Design → BE → review → FE → convergence. Không subagent cùng sửa chung file.

## Phase1 — Specification/design (approved)
- [x] T001 Domain14/spec + ba quyết định nghiệp vụ và user duyệt plan, kiểm constitution1.0.0.
- [x] T002 Research/plan/data-model/contract/tasks; phân tích chéo và đóng lỗi chặn trước code.

## Phase2 — Backend (US1/US2, AC-3/5/6/7)
- [x] T003 (`src/test/java/com/carenest/service/eduplan/impl/YearPlanDomainVariantTest.java`, probes cục bộ trong `target/`) Tái hiện fidelity trên synthetic fixture và đối chiếu độc lập sample cục bộ; giữ riêng warning nguồn thật và lỗi parser.
- [x] T004 (`src/test/java/com/carenest/service/eduplan/impl/FullYearPlanFileImportTest.java`) RED tests metadata/lịch/nghỉ/khớp mã/mơ hồ/oldformats/bounds, service integration.
- [x] T005 (`src/main/java/com/carenest/service/eduplan/impl/GoalFileTable.java`, `GoalFileGrid.java`, `YearPlanFileTable.java`, `EduGoalFileServiceImpl.java`; DTO `src/main/java/com/carenest/dto/eduplan/GoalFilePreviewResponse.java`) Sửa fidelity tối thiểu và mở rộng DTO/interpreter/service, không đổi branch reader semantics.
- [x] T006 (`docs/api/openapi.yaml`, `docs/business/BUSINESS_RULES.md`, `docs/modules/education-plan.md`, `docs/system/CROSS_REPO_MAP.md`, `docs/quality/VERIFICATION.md`, incident và `quickstart.md`) Snapshot OpenAPI và docs rule/card/consumer/incident; full BE verify, review độc lập, ghi bằng chứng và dừng user review.

## Phase3 — Web (sau user review BE; US1/US2/US3, AC-1..7)
- [ ] T007 (`FE:tests/education-plan/import-goal-file.test.mjs`, tests UI nhập file, `FE:tests/e2e/system/year-plan-import.spec.mjs`) RED merge tests bảo toàn nhập tay/id/lineage/ticks, nhóm khác/trống, năm dời, source freshness/cancel/lostlease.
- [ ] T008 (`FE:src/models/education-plan/importGoalFile.mjs`, `FE:src/components/education-plan/ImportGoalFileModal.jsx`, `FE:src/pages/education-plan/GoalFormPage.jsx`) Helper merge chủ đề/nghỉ trước mục tiêu/ticks; metadata chỉ ô trống; modal preview đầy đủ + xác nhận bổ sung, async race guard.
- [ ] T009 (`FE:src/pages/education-plan/GoalFormPage.jsx`, `FE:src/components/education-plan/reviewUi.jsx`, `FE:src/styles/modules/education-plan.css`, tests UI liên quan) Một hàng nút đồng kiểu và phần1 nhập dạng bảng; giữ quyền/lưu/gửi/guard và label, tests/callers/E2E liên quan.
- [ ] T010 (`FE:tests/e2e/system/year-plan-import.spec.mjs`, `FE:tests/e2e/system/edu-plan-flows.spec.mjs`, `quickstart.md`) FE/BE full verify và browser375/768/1440, real-BE import/save/reopen/guards; ghi fail/skip rõ.
- [ ] T011 (`tasks.md`, `quickstart.md`, `analysis.md`, plan quản trị) Implement/converge đối chiếu spec/plan/tasks; append gap nếu có, không đánh dấu sạch khi phase còn mở.

## Dependencies và test trace
T003→T004→T005→T006→user review→T007→T008/T009→T010→T011. Phase2 parser và service tests map AC3/6; age/year draft display merge tests map AC5/7; preservation/race map AC4; browser layout map AC1/2. Synthetic fixtures không chép dữ liệu thật. Không thêm checklist chưa được review để vượt gate.

Phase2 evidence đến hiện tại: domain RED4→GREEN4; preview RED5→GREEN5 rồi thêm boundary/warnings/ranges/docx tests; reviewed source có11test full preview mới. Snapshot31test đạt trước sửa after-table cuối; probe RED→GREEN cho boundary sau bảng. Full verify cuối đang chạy lại; chưa đánh dấu T006 khi chưa có output.

Phase2 exit ngày02/11/2026: `VERIFY PASS full | tests 659 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 278s | log target/verify.log` sau source/test cuối. Review độc lập không còn phát hiện chặn. Bản log full lưu `target/feature009-final-full.log` trước verify cuối hồ sơ. T007..T011 chưa làm, chờ user review backend theo plan.

## Delta mới nhất — hai chế độ thay nội dung (user duyệt02/11/2026)
T001..T011 và evidence trên là lịch sử, không sửa/xóa/đánh dấu đã xong chỉ để khớp quyết định mới. Phần yêu cầu “bổ sung/giữ nhập tay” trong T007/T008 được thay bằng T013..T017 bên dưới; các phần test chưa có evidence vẫn chưa hoàn thành. Active delta hiện tại là T012..T020, thực hiện tuần tự, không phân công nhiều agent cùng file.

### Delta phase1 — Specification/design (US1/US2/US3)
- [x] T012 Cập nhật đủ Domain14/spec/clarifications/plan/research/data-model/contracts/EDU-46/module/cross-repo và analyze tại specs/009-full-year-plan-file-import; giữ constitution1.0.0 và task history; kiểm check-ai-layer BE/FE, dừng user review phase1.

### Delta phase2 — US1/US2: model + modal + page (sau review)
- [x] T013 [US1] RED tests hai mode/empty phần/no-mutation/protected fields/file cũ/remap ID-ticks tại FE:tests/education-plan/full-year-file-import.test.mjs và FE:tests/education-plan/import-goal-file.test.mjs; FR01–08/12, AC3/4/6/7/9.
- [x] T014 [US1] Implement helper pure hai mode thay nguyên phần, metadata theo mode, remap tick và báo liên kết mất tại FE:src/models/education-plan/importGoalFile.mjs; GREEN T013, không đổi nút lấy kế hoạch/Bộ; AC3/4/6/7/9.
- [x] T015 [US1] RED/GREEN modal radio/default-full/preview thay-xóa-giữ/đổi mode khi đọc file/confirm mode mới/cancel/race/double click/lost permission tại FE:tests/education-plan/import-goal-file-modal.test.mjs và FE:src/components/education-plan/ImportGoalFileModal.jsx; FR02/06/07, AC4/6/8.
- [x] T016 [US2] RED/GREEN năm hệ thống/shift/no-config/out-of-year và age/fixed tại FE:tests/education-plan/full-year-file-import.test.mjs; truyền cùng mode/yearRange thật vào preview và apply tại FE:src/pages/education-plan/GoalFormPage.jsx, cập nhật callback/report; FR08/09, AC5/7.
- [x] T017 [US1] Cập nhật assertions E2E nhập file còn kỳ vọng additive tại FE:tests/e2e/system/year-plan-import.spec.mjs; browser cả hai mode, missing từng phần, save/discard, fields cố định và tick-remap, desktop/mobile; FR06–12, AC4–9/SC02/03. Có BE thật thì save/reopen, nếu chưa có phải ghi rõ chưa kiểm.

### Delta phase3 — US3 và convergence
- [ ] T018 [US3] Kiểm hiển thị tick ghép QH-DN-BH/T&M ở sửa/chỉ xem, cột hẹp, chuột/bàn phím và không đổi abbr tại FE:src/components/education-plan/GoalTables.jsx, FE:src/styles/modules/education-plan.css và browser source/CSS thật; FR10/13, AC1/2/10.
- [ ] T019 Chạy FE:node scripts/verify.mjs và check-ai-layer sau lần sửa cuối, ghi kết quả thật vào specs/009-full-year-plan-file-import/quickstart.md cùng FE:docs/quality/VERIFICATION.md; AC1..10/SC01..03; không lấy verify parser cũ thay test modes mới, dừng review.
- [ ] T020 Implement/converge so code với domain/spec/plan/tasks; append gap nếu có vào specs/009-full-year-plan-file-import/tasks.md và ghi quickstart/analysis; không gọi convergence sạch khi còn task hoặc lỗi chưa phân loại.

Dependencies: T012 → user review → T013 → T014 → T015 → T016 → T017 → T018/T019 → T020. Mỗi story có test độc lập: US1 hai mode+race+remap; US2 tuổi/năm/range; US3 wrap/no-overflow/keyboard. Không parallel execution vì user yêu cầu tuần tự. MVP là US1+US2 đồng bộ vì mode không được apply lịch khi thiếu range; US3 hiển thị đã có thay đổi riêng nhưng T018 chưa đóng khi chưa kiểm đủ read-only/keyboard.

### Phase2 delta evidence02/11/2026 — chờ review
User duyệt phase1 trong chat bằng “duyệt”, triển khai T013..T017. Model RED10fail/11→GREEN11/11; modal RED3fail/7→GREEN7/7, targeted28/28, suite giáo dục133/133, full FE369test fail0/skip0/VERIFY PASS full. Model mới dùng full/present thay nguyên phần, title/description theo mode, fixed fields/range config và ID/tick-remap; modal callback/state/race cùng mode, page nối yearRange thật.

Browser source/CSS thật, service/lease/save giả: cả hai mode1440/375, preview/cancel không mutate, full clear/present keep, ticks đúng, thiếu config khóa apply, readonly split QH-DN-BH và keyboard tick,0lỗiJS. Test EDU-46 E2E đổi selectors/cách chọn present/expectation thay cây; syntax check đạt. BE/Keycloak đang listen nhưng E2E_DEV_PASSWORD chưa cấu hình, nên không chạy test BE thật/save-reopen; không gọi đó là pass hay skip của node suite. T017 hoàn tất phạm vi thay assertions và browser khi môi trường E2E chưa sẵn, giới hạn phải được giữ khi bàn giao.

T018..T020 vẫn mở, chưa chạy converge hoặc đánh dấu toàn feature sạch. Dừng review phase2 theo kế hoạch user đã duyệt. BE chỉ cập nhật tài liệu; source/API/DB/APP/dependency không đổi, không commit/push.

Review phase2 độc lập: model/modal18/18, không findings mới có đủ bằng chứng; đã xem mapper, không xác nhận BE thật. Source E2E thêm full clear/save/reopen, syntax check đạt; cả3EDU-46 E2E chưa thực thi do credential thiếu. T018..20 vẫn mở theo gate review, không làm convergence giả.
