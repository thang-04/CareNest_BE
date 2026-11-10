# Tasks: Nhập nhánh và Word .doc mục tiêu

Input: [spec.md](spec.md), [plan.md](plan.md), domain-spec.md, research.md, data-model.md, contracts/file-import.md.
Status: hoàn thành 22/22 task; user cho phép chạy hết phase 2/3 và vòng hội tụ không dừng giữa phase. Test bắt buộc theo AC và constitution. BE paths tương đối repo; FE prefix ../CareNest_FE/.

## Phase 1: Setup

- [x] T001 Ghi bằng chứng user duyệt plan/API vào docs/plans/completed/2026-11-01-branch-plan-file-import.md trước source edits; không tự approve.
- [x] T002 Kiểm resolve/dependency tree, thêm poi-scratchpad 6.0.0 trong pom.xml theo duyệt dependency, không tự đổi transitive khác nếu xung đột.

## Phase 2: Foundational — US4 / phase triển khai 1

Goal: bộ đọc OLE2 chung và bước 2 hoạt động; US4 kiểm độc lập bằng .doc mục tiêu và các định dạng cũ.

- [x] T003 [US4] Tạo fixture tổng hợp .doc và expected .docx có ô gộp tại src/test/resources/eduplan/; bỏ toàn bộ author/company/metadata cá nhân, không copy file thật; thêm test RED vào src/test/java/com/carenest/service/eduplan/impl/GoalFileImportTest.java.
- [x] T004 [US4] Đọc OLE2 qua HWPF trong src/main/java/com/carenest/service/eduplan/impl/GoalFileGrid.java; giữ Table/Cell/continued, giới hạn file/dòng/cell/text trong quá trình đọc, file hỏng/mã hóa không ghi; chạy GREEN và hồi quy tất cả định dạng (FR-001/010/011, SC-005).
- [x] T005 [US4] Cập nhật lời lỗi 7033 trong src/main/java/com/carenest/utils/ApiCode.java và hướng dẫn ../CareNest_FE/src/components/education-plan/ImportGoalFileModal.jsx; test service/perms giữ nguyên, không đổi merge (FR-011).
- [x] T006 Ghi full verify BE/FE và check-ai-layer thực tế phase 1 vào docs/plans/completed/2026-11-01-branch-plan-file-import.md, dừng user review.

## Phase 3: US1 — Đọc và xem trước nhánh / phase triển khai 2

Goal: parser đúng tên/chủ đề, không nhận lịch file. Independent test: fixture tổng hợp 35 nhánh, ô gộp, nhãn lặp, dòng nghỉ/khối chữ ký không bị nhận nhầm.

- [x] T007 [US1] Test RED parser .doc/.docx/.xlsx/.csv/.doc HTML, ô gộp, file xấu/giới hạn ở src/test/java/com/carenest/service/eduplan/impl/BranchFileImportTest.java; implement src/main/java/com/carenest/service/eduplan/impl/BranchFileTable.java với grid dùng chung (FR-001/003/010, SC-001).
- [x] T008 [US1] Thêm src/main/java/com/carenest/dto/eduplan/BranchFilePreviewResponse.java và lỗi 7124–7126 trong src/main/java/com/carenest/utils/ApiCode.java theo contracts/file-import.md; error test đúng ngữ cảnh nhánh (FR-002/009).

## Phase 4: US2 — Lịch/quyền server / phase triển khai 2

Goal: server tạo matches theo tuần hiện hành, read-only và scope đúng. Independent test: đổi ngày file cho cùng matches; chủ đề lạ/thừa/mơ hồ không tràn sang chủ đề khác, thiếu không tạo tên.

- [x] T009 [US2] Test RED tại src/test/java/com/carenest/service/eduplan/impl/EduClassDocServiceIntegrationTest.java cho phân công/sai lớp/năm/nhóm tuổi/phiên bản/status, nghỉ trọn/nghỉ lẻ, tên chuẩn hóa/trùng/thừa/thiếu và không ghi DB; implement previewBranchFile trong src/main/java/com/carenest/service/eduplan/EduClassDocService.java và impl/EduClassDocServiceImpl.java dùng teachingWeeks và scope hiện có (FR-003/004/005/009, SC-002/003).
- [x] T010 [US2] Tạo test MockMvc RED mới ở src/test/java/com/carenest/controller/eduplan/EduClassDocControllerTest.java rồi thêm endpoint tại src/main/java/com/carenest/controller/eduplan/EduClassDocController.java, kiểm auth/params/multipart/không cấp quyền goals.edit cho class import; cập nhật có chủ đích docs/api/openapi.yaml (FR-004/009).
- [x] T011 Cập nhật docs/contracts/ERROR_CONTRACT.md, docs/system/CROSS_REPO_MAP.md và docs/modules/education-plan.md đúng endpoint/DTO/mã lỗi; ghi full verify BE skip 0 và snapshot diff phase 2 trong docs/plans/completed/2026-11-01-branch-plan-file-import.md; dừng user review.

## Phase 5: US1 + US2 — Web nhập file / phase triển khai 3

Independent test: chọn/xem trước/Hủy không đổi nháp, xác nhận thay đúng key rồi Lưu/mở lại, không đổi lịch.

- [x] T012 [US1] Thêm facade/API/mock previewBranchFile tại ../CareNest_FE/src/services/education-plan/educationPlanService.js, api/educationPlanApi.js, mock/educationPlanMockRepository.js và mock/classPlanMockRepository.js theo routing mock thực tế; giữ API goals cũ (FR-002).
- [x] T013 [US1] Test patch/overwrite/Hủy/không xóa key ngoài match tại ../CareNest_FE/tests/education-plan/import-branch-file.test.mjs; implement helper ../CareNest_FE/src/models/education-plan/importBranchFile.mjs (FR-005/006/009, SC-003).
- [x] T014 [US1] Tạo ../CareNest_FE/src/components/education-plan/ImportBranchFileModal.jsx, tích hợp ../CareNest_FE/src/pages/education-plan/BranchPlanPage.jsx: loading/error/warnings/tuần hệ thống, xác nhận thay tên, không tự lưu, kiểm còn quyền khi áp dụng; clear AI pending chỉ ở key được thay, test payloadOf không phục hồi tên cũ (FR-002/003/006/009).

## Phase 6: US3 — Hai nút cố định / phase triển khai 3

Independent test: Nháp/Bị trả lại/Chờ duyệt/Đã duyệt và người xem đều có Lưu/gửi, disabled/lý do/chain đúng; caller khác không đổi.

- [x] T015 [US3] Bổ sung kiểm hành vi UI vào ../CareNest_FE/tests/education-plan/; sửa ../CareNest_FE/src/pages/education-plan/BranchPlanPage.jsx và components/education-plan/reviewUi.jsx để Lưu luôn hiện, showSend tùy chọn mặc định false, can.send=false thì disabled có lý do, giữ nhãn chain và các nút duyệt/trả lại (FR-007/008, SC-004).

## Phase 7: Polish, verify, analyze và hội tụ / phase triển khai 3

- [x] T016 Kiểm E2E trong bộ Playwright kế hoạch giáo dục hiện có (theo ../CareNest_FE/playwright.config.mjs: tests/e2e/system/edu-plan-flows.spec.mjs và year-plan-import.spec.mjs), thêm luồng nhập nhánh/Hủy/overwrite/lưu/reload/AI pending/lịch/quyền/bốn trạng thái và .doc mục tiêu; ghi command/x-y thực tế tại specs/008-branch-plan-file-import/quickstart.md (SC-001..005).
- [x] T017 Chạy verify đầy đủ BE/FE, check-ai-layer và snapshot diff sau sửa cuối; cập nhật docs/quality/VERIFICATION.md, plan Progress log, quickstart và knowledge liên quan, công khai fail/skip nếu có.
- [x] T018 Chạy speckit-converge sau implement, chỉ append gap vào cuối specs/008-branch-plan-file-import/tasks.md; implement các task mới rồi converge lại đến sạch, giữ ID/lịch sử cũ và ghi evidence tại quickstart.md; dừng review phase 3.

## Dependencies & opportunities

T001 → T002–T006 (US4) → review phase 1 → T007–T011 (US1/US2 backend) → review phase 2 → T012–T018 (Web/integration).
US3 có thể thực hiện độc lập với modal sau khi phase 3 được phép; không chạy hai người sửa BranchPlanPage cùng lúc.
Các bộ test đọc file độc lập có thể chạy song song với đọc source UI, nhưng sửa grid/API tuần tự để giữ contract thống nhất.
US4 là lát nhỏ nhất dùng được riêng; mục tiêu toàn feature chỉ hoàn tất khi US1–US4 và converge đều đủ bằng chứng.

## Phase 8: Convergence

- [x] T019 Sửa P1 kiểm kích thước trước cấp phát lưới tại src/main/java/com/carenest/service/eduplan/impl/GoalFileGrid.java: XLSX tọa độ/merge, DOCX gridSpan, HTML colspan/rowspan, CSV/hàng/cột; giữ giới hạn 2.000 dòng và cap kỹ thuật 256 cột như OLE2, tránh overflow; thêm RED/GREEN an toàn trong src/test/java/com/carenest/service/eduplan/impl/GoalFileImportTest.java và record BUG-261101-file-grid-allocation-limits theo plan: security/data constraints, FR-001/003/011 (partial), Constitution III/IV. Chạy lại full gate và E2E import với jar mới.

## Phase 9: Bổ sung mẫu tải xuống và đồng bộ modal — user 02/11/2026

- [x] T020 Thêm test RED/GREEN và branchTemplateFile trong ../CareNest_FE/src/utils/education-plan/classPlanDoc.js, dùng docShell/esc và danh sách tuần hiện có, tên nhánh trống/không ngày hoặc dữ liệu nháp; ../CareNest_FE/tests/education-plan/branch-import-template.test.mjs (FR-012, SC-006).
- [x] T021 Đồng bộ ../CareNest_FE/src/components/education-plan/ImportBranchFileModal.jsx với modal mục tiêu: width720, hàng nút chọn/tải mẫu, warning tối đa8 và số còn lại, giữ preview/confirmation; truyền các tuần hiển thị từ ../CareNest_FE/src/pages/education-plan/BranchPlanPage.jsx; test render hành vi nút (FR-013, SC-006).
- [x] T022 Verify FE đầy đủ, kiểm AI layer BE/FE, cập nhật knowledge/feature docs, hội tụ lại không còn gap FR-012/013 và lưu bằng chứng tại quickstart.md; không commit/push.
