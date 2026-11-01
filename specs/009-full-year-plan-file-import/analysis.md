# Pre-implementation analysis — 02/11/2026

Các mục đầu là lịch sử thiết kế trước quyết định hai chế độ; report delta mới nhất bên dưới mới áp cho phần thay nội dung hiện hành.

Scope: domain14/spec/plan/research/data-model/contract/tasks/constitution và plan quản trị. User đã xác nhận ba câu nghiệp vụ và duyệt plan bằng "chốt".

- Constitution: giữ stack/schema/security/privacy/human review, không thay nguyên tắc.
- Coverage: UR1/2→FR10/US3→AC1/2→T009/10; UR3/4→FR1..8/US1→AC3/4/6→T003..8/10; UR5/6→FR8/9/US2→AC5/7→T004/7/10.
- A1 (resolved): metadata tuổi không có DB identity, contract dùng khoảng tháng; Web so nhóm theo cấu hình, save BE vẫn kiểm scope.
- A2 (resolved): không đoán viết tắt theo tên; chỉ ordinal khi quan hệ duy nhất/đủ STT và số cột, mơ hồ báo.
- A3 (resolved): file thiếu ngày không suy từ số tuần; thời gian nghỉ chỉ parse từ text nghỉ có ngày rõ.
- A4 (resolved): target year giữ nguyên, source dates chỉ dời ở preview/merge Web theo helper, không tự căn ngày tuần.
- A5 (resolved): không tự thừa kế approval plan cũ; user 02/11 đã duyệt phần bổ sung; phase BE cần dừng review trước FE.

Bổ sung user sau duyệt: phần1 nhập dạng bảng như phần2/3, thay đề xuất form grid trước đó; scope UX không thay contract/backend. Không còn câu hỏi nghiệp vụ chặn phase2 hoặc xung đột schema/contract. Shape additive nằm trong phạm vi preview đã duyệt. Research fidelity còn nhiệm vụ điều tra kỹ thuật T003, không phải business rule tự chọn. Converge chưa chạy; phase3 còn mở.

- A6 (resolved, quality pass): chuyển mô tả endpoint/fields/hàm ra contract/plan; thêm Given/When/Then7AC và checklist chất lượng spec16/16, không coi phase Web đã xong.

## Phase2 review/verification — 02/11/2026
Backend đáp ứng phần đọc preview của US1/2; full659fail0err0skip0, snapshot additive53dòng, review không còn lỗi chặn. US1/2 phần merge/age/year Web và US3 toàn bộ UI còn T007..11. Converge chưa chạy vì phase Web chưa implement; không append gap đã có task hoặc tuyên bố feature sạch.

## Specification Analysis Report — delta hai chế độ02/11/2026
Scope read-only analysis: domain14/spec/plan/research/data-model/contract/tasks/constitution1.0.0 và EDU-46/module/consumer-impact đã cập nhật. Report này được lưu sau bước analyze bằng workflow ghi evidence, không sửa code hoặc tự remediate trong bước analyze.

| ID | Category | Severity | Location | Summary | Recommendation |
| --- | --- | --- | --- | --- | --- |
| D1 | Coverage | Informational | spec FR07/12, tasks T013..17 | Hai mode/partial presence/remap có test tasks; code còn additive | Chỉ triển khai sau phase1 review |
| D2 | Constraints | Informational | spec FR02/09, data-model, contract | Description chưa có DTO; yearRange phải từ config, missing khóa apply | Giữ scope/API hiện có theo plan được duyệt |
| D3 | History | Informational | tasks delta header | T007/T008 preservation cũ được superseded rõ, task/evidence cũ không bị sửa | Chỉ dùng T012..20 làm active delta, không tự đánh dấu task cũ xong |

| Requirement Key | Has Task? | Task IDs | Notes |
| --- | --- | --- | --- |
| FR01 | Có | T013/T017/T019 | Reader/limits cũ là baseline, regression luồng nhập mới |
| FR02 | Có | T014/T015 | Metadata/description fixed scope |
| FR03 | Có | T013/T014/T017 | Cây dữ liệu/dấu tích |
| FR04 | Có | T013/T014/T017 | Mã rõ/mơ hồ và ID |
| FR05 | Có | T013/T014/T016 | Reuse preview dates; không sửa parser |
| FR06 | Có | T015/T017 | Race/confirm/rights |
| FR07 | Có | T013/T014/T015 | Hai mode, phần thiếu, file rỗng |
| FR08 | Có | T013/T014/T016 | Fixed fields/age |
| FR09 | Có | T016/T017 | Năm đích/range config/shift |
| FR10 | Có | T018/T019 | UX cũ giữ baseline |
| FR11 | Có | T012/T019 | Scope/constitution/checks |
| FR12 | Có | T013/T014/T017 | ID/tick-remap |
| FR13 | Có | T018 | Display-only wrap |
| SC02 | Có | T013..T019 | Buildable automated/browser checks |
| SC03 | Có | T017/T018/T019 | Responsive/save/reopen giới hạn môi trường |

Constitution alignment: không sửa principle/stack/source BE/API/DB/APP, không branch/commit, giữ user approval/review/verify gates. Existing constitution valid được tái sử dụng. Hooks: không có extensions.yml, không chạy hook tạo branch. Prerequisites/resolve-template/setup-plan/setup-tasks thành công với feature009; existing plan không bị overwrite.

Metrics:13functional requirements;9active delta tasks (20task IDs unique toàn lịch sử);13/13 FR có task =100%;2buildable success criteria có task, SC01 là baseline fidelity không phải feature mới; ambiguity blocking0, duplication blocking0, critical0, high0; unmapped active tasks0. Domain14/quality-checklist16 checked được kiểm cấu trúc riêng. Đây là manual semantic review + structural script, không phải app tests hoặc converge.

No critical ambiguities worth further formal clarification. Câu hỏi nghiệp vụ đã hỏi/được trả lời:1 (file thiếu phần); user yêu cầu cả hai chế độ và duyệt plan/docs BE. Coverage taxonomy: functional/data/UX/constraints/terminology/error/integration/completion clear; security/privacy và NFR kế thừa constitution + existing contracts, tests theo task; BE thật/persistence verification là implementation evidence còn chưa có, không phải câu hỏi chưa chốt.

Next: phase1 review theo kế hoạch; sau đó speckit-implement T013..19, implement/converge T020 append-only. Không gọi analyze/snapshot/check-ai-layer là kiểm chứng application mode mới. Lỗi targetName trong FE baseline trước delta còn mở, phải phân loại/cover theo expectation mới khi implement.

## Phase2 implementation evidence —02/11/2026 (không phải convergence)
User duyệt phase1; T013..T017 được triển khai trong FE và có model/modal/browser/full verify. Trace/limits theo quickstart. Model prepare/apply cùng mode, fixed fields và yearRange config thật; UI có radio/preview/callback mode; tests giữ ca conflict theo replacement, không áp criterion additive đã superseded. Source/API/DB BE không đổi. T018..T020 còn mở; chưa có report convergence cuối. Không dùng evidence browser giả để tuyên bố E2E BE thật, credential kiểm thử chưa được cấu hình.
