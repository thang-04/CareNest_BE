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
