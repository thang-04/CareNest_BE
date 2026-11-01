# Pre-implementation analysis — 02/11/2026

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
