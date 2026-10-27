# Tasks: Tự lưu mục tiêu năm học

**Input**: [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md), [contract](contracts/year-plan-autosave.md).
**Approval**: Tiền duyệt sửa BE/FE ngày 27/10/2026. Không commit/push/tạo branch.

## Phase 1: Setup

- [x] T001 Ghi đặc tả và hai câu trả lời nghiệp vụ trong specs/001-year-plan-autosave/spec.md; ghi duyệt trong docs/plans/active/2026-10-22-muc-tieu-nam-hoc.md.
- [x] T002 Viết thiết kế phiên/revision và hợp đồng trong specs/001-year-plan-autosave/data-model.md và contracts/year-plan-autosave.md.

## Phase 2: Foundational

- [x] T003 Thêm migration V24 và trường phiên/revision/idempotency trong src/main/resources/db/migration/V24__add_edu_year_plan_edit_sessions.sql và entity/eduplan/EduYearPlan.java.
- [x] T004 Truyền context qua dto/eduplan/YearPlanEditRequest.java, service/eduplan/EduYearPlanService.java và controller/eduplan/EduYearPlanController.java.
- [x] T005 Đổi TTL 90s và truy vấn cleanup có khóa trong config/EduPlanProperties.java, application.yml, repository/eduplan/EduYearPlanRepository.java.

## Phase 3: US1 — Soạn không nhớ bấm Lưu

**Independent test**: Mở lại sau tự lưu thấy nguyên nội dung, nhập tiếp trong lúc lưu không bị mất.

- [x] T006 [US1] Thêm queue tuần tự/request ID/ánh xạ ID trong FE src/models/education-plan/yearPlanAutosave.mjs và tests/education-plan/year-plan-autosave.test.mjs.
- [x] T007 [US1] Nối useYearPlanAutosave vào FE src/pages/education-plan/GoalFormPage.jsx, giữ nội dung dở và đóng băng nhập lúc gửi/ban hành.
- [x] T008 [US1] Nới validation nháp và chuyển kiểm tra đầy đủ sang gửi/ban hành trong BE dto/eduplan/YearPlanRequest.java và service/eduplan/impl/EduYearPlanServiceImpl.java.
- [x] T009 [US1] Chạy ca thiếu tiêu đề/mục tiêu, mất phản hồi và retry trong BE src/test/java/com/carenest/service/eduplan/impl/EduYearPlanServiceIntegrationTest.java; lưu kết quả sau sửa cuối.

## Phase 4: US2 — Một phiên sửa

**Independent test**: Hai tab cùng/khác tài khoản, chỉ một phiên ghi; release cũ không nhả khóa mới.

- [x] T010 [US2] Kiểm phiên/revision tại mọi thao tác ghi và giữ PENDING_VP độc lập trong BE service/eduplan/impl/EduYearPlanServiceImpl.java và entity/eduplan/EduYearPlan.java.
- [x] T011 [US2] Heartbeat/nhả quyền và đồng bộ headers trong FE services/education-plan/api/educationPlanApi.js, facade và provider.
- [x] T012 [US2] Kiểm context stale/missing, cùng tài khoản hai tab và quá hạn 90s trong BE controller/service tests và FE tests/education-plan/year-plan-api.test.mjs.
- [x] T013 [US2] Đồng bộ mock về phiên và TTL mới trong FE src/services/education-plan/mock/educationPlanMockRepository.js; tránh demo khác contract thật.
- [x] T014 [US2] Sinh snapshot OpenAPI có chủ đích và đối chiếu consumer trong docs/api/openapi.yaml và docs/system/CROSS_REPO_MAP.md.

## Phase 5: US3 — Phục hồi khi sự cố

**Independent test**: Ngoại tuyến, tải lại, hai phiên cùng tài khoản có bản riêng; kết nối lại không ghi đè bản mới.

- [x] T015 [US3] Thêm service phục hồi theo tài khoản/kế hoạch/slot trong FE src/services/education-plan/yearPlanRecovery.js; UI không gọi localStorage trực tiếp.
- [x] T016 [US3] Cảnh báo lưu tạm/lỗi lưu và phục hồi/tải bản để đối chiếu trong FE useYearPlanAutosave.js và GoalFormPage.jsx.
- [x] T017 [US3] Kiểm cách ly tài khoản/hai slot/quota và reconnect trong FE tests/education-plan/year-plan-autosave.test.mjs và kiểm thử trình duyệt.

## Phase 6: Polish and quality gates

- [x] T018 Cập nhật EDU-10/22/23, flow/card và quy tắc FE tương ứng, ghi nguồn user duyệt trong docs/business/BUSINESS_RULES.md và tài liệu liên quan.
- [x] T019 Chạy check-ai-layer hai repo và verify BE/FE sau sửa cuối; ghi rõ mọi skip/fail, không lách gate.
- [x] T020 Rà soát làn L sau sửa cuối; ghi kết quả và các phần chưa kiểm chứng trong plan CareNest.

## Dependencies and execution

T001 → T002 → T003..005 → US1/US2 → US3 → gates. US1 cần cơ chế fencing US2 trước khi mở cho người dùng; không triển khai BE mới riêng khi FE cũ vẫn ghi.
Các ca kiểm dữ liệu thuần FE và integration BE có thể kiểm độc lập, nhưng không chạy hai Maven verify cùng lúc. Root triển khai inline, subagent chỉ review theo AGENTS.md.

## Requirement coverage

| Spec | Tasks |
| --- | --- |
| FR-001 | T004, T007, T010, T018 |
| FR-002 | T006, T007, T009 |
| FR-003 | T007, T016, T017 |
| FR-004 | T003, T006, T009, T012 |
| FR-005 | T003, T010, T011, T012 |
| FR-006 | T005, T010, T011, T012 |
| FR-007 | T015, T017 |
| FR-008 | T015, T016, T017 |
| FR-009 | T010, T012, T016, T017 |
| FR-010 | T007, T008, T009 |
| FR-011 | T006, T007, T016 |
| FR-012 | T008, T009 |
| FR-013 | T010, T011, T012 |
| FR-014 | T003, T009, T014 |
| FR-015 | T008, T009, T020 |

## Convergence history

Các vòng sau chỉ nối task mới với ID lớn hơn vào cuối file, không xóa/đánh số lại task cũ. Task hoàn tất chỉ đánh dấu sau khi có bằng chứng phù hợp; đánh dấu code có mặt không đồng nghĩa gate đã đạt.

### Converge 1 — 2026-10-27

Đối chiếu spec và code sau lượt triển khai đầu, giữ nguyên task T001..T020. Review tìm thấy các tình huống cần khóa lại bằng kiểm thử:

- [x] T021 [US1] Xác nhận nhịp debounce/max-wait và tự retry lỗi tạm thời bằng clock giả trong FE tests/education-plan/year-plan-autosave.test.mjs, không đổi request ID của cùng snapshot.
- [x] T022 [US3] Chỉ xóa bản phục hồi nguồn sau khi bản thay thế đã được giữ thành công trong FE useYearPlanAutosave.js; kiểm nhiều slot cùng tài khoản và khác tài khoản.
- [x] T023 [US2] Kiểm thử thao tác chi tiết từ revision cũ không ghi lại nội dung cũ và luôn nhả quyền tạm trong FE tests/education-plan/year-plan-api.test.mjs.
- [x] T024 [US1] Kiểm mục tiêu rỗng không vượt điều kiện gửi/ban hành sau khi cho phép lưu nháp rỗng trong BE EduYearPlanServiceIntegrationTest.java.

Analyze 1: 15/15 FR có task; không có xung đột yêu cầu mới với quyền đọc/bản đã ban hành. Các mục T009/T012..014/T017..024 vẫn mở cho đến khi có bằng chứng. Constitution Spec Kit chưa ratify; AGENTS.md tiếp tục là ràng buộc, không tự tạo chính sách mới.

- [x] T025 [US2] Cho phép đúng ba header phiên/revision/request ID qua CORS trong BE config/SecurityConfig.java; test preflight từ origin được phép trong SecurityConfigTest.java. RED đã xác nhận server chỉ trả Authorization.
- [x] T026 Xử lý fixture Date của test AI tuần để verify chuẩn không phụ thuộc ngày seed, không đổi nghiệp vụ thực đơn; ghi FE-CASE-261027-ai-week-seed-date.

- [x] T027 [US1] Không lấy lại khóa sau tạo khi phiên đã giữ quyền; bỏ phản hồi acquire cũ hơn revision đã xác nhận trong FE GoalFormPage.jsx. Rà soát phản hồi đến sai thứ tự sau tạo mới.

- [x] T028 [US3] Nhả quyền của POST tạo hoàn tất sau unmount và chuyển bản phục hồi sang ID thật; tra bản tạm cùng năm/nhóm tuổi khi mở bản đã tạo. FE GoalFormPage.jsx, useYearPlanAutosave.js, yearPlanRecovery.js và year-plan-recovery.test.mjs.

- [x] T029 [US1] Sau lỗi validation 400, bỏ snapshot bị từ chối để nội dung đã sửa được lưu; giữ request ID khi retry lỗi mạng/5xx. RED đã tái hiện trong FE year-plan-autosave.test.mjs.
- [x] T030 [US2] Bàn giao quyền một lần khi /new đổi sang /id/edit, không để unmount nhả quyền của trang mới, không tái sử dụng bàn giao khi Back/reload/tab khác. FE GoalFormPage.jsx; kiểm trực tiếp trình duyệt.

- [x] T031 [US2] Siết CHECK của migration V24 để editing_from NULL không lọt logic ba giá trị của SQL; thêm schema regression thật.

- [x] T032 [US3] Sửa thông điệp LeaveConfirmModal riêng GoalFormPage.jsx: phân biệt chưa lưu lên hệ thống với bản đã giữ trên máy, không nói chắc rằng rời trang sẽ mất toàn bộ. Browser đã kiểm khôi phục sau khi rời trước nhịp 2s.

### Converge 2 — 2026-10-27

- Không còn thiếu sót triển khai P1/P2 trong phạm vi 001 sau review cuối. Giữ nguyên T001..T032, chỉ cập nhật trạng thái bằng chứng.
- BE full 380 test, fail 0, error 0, skip 0; snapshot/ArchUnit/Spotless đạt. FE verify chuẩn đạt, bộ giáo dục có 23 test; fixture Date đã được cố định.
- Browser mock đã kiểm tự lưu, chuyển ID giữ quyền, hai tab cùng tài khoản, nhả/nhận lại quyền, rời trước nhịp lưu rồi mở lại và khôi phục bản trên máy. Offline/reconnect được mô phỏng ở node:test; server fencing/expiry qua PostgreSQL thật.
- Chưa kiểm browser với BE thật hoặc SLA mạng production; các giới hạn môi trường này được ghi rõ, không coi là bằng chứng đã có.
