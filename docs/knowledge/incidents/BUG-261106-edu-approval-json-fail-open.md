---
id: BUG-261106-edu-approval-json-fail-open
type: bug
modules: [education-plan]
rules: [EDU-54]
status: fixed
date: 2026-11-06
keywords: [approvals, serialization, data loss, fail closed]
---

`EduWorkflowServiceImpl.serializeApprovals` hiện trả `[]` khi serializer lỗi; caller có thể ghi đè lịch sử duyệt thay vì rollback. `parseApprovals` cũng trả rỗng khi đọc JSON lỗi, có thể khiến evaluator bỏ qua kết quả cũ. Phát hiện khi review convergence; đã thêm regression `EduWorkflowSerializationTest`, cần chạy RED, sửa fail-closed và chạy GREEN. Chưa xác nhận runtime vì full verify đang chạy.

Đã sửa fail-closed cho đọc/ghi approvals và policy JSON; không dùng default roles khi policy hỏng. Test RED 2/2 (mất lịch sử), 1/1 (policy fallback); GREEN nhóm 11 tests gồm policy/observation, fail0 err0 skip0 tại target/feature011-json-policy-green.log. Cần full verify sau source cuối.

Kiểm tra tiếp phát hiện ban hành kế hoạch năm nuốt lỗi serialize completion policy và lưu null; regression PostgreSQL tái hiện rồi xác nhận rollback sau sửa (target/feature011-publish-policy-red.log, target/feature011-publish-policy-green.log). Vai trò duyệt không nhận diện trong JSON cũng từng bị bỏ qua âm thầm; chuyển sang báo lỗi để bảo toàn lịch sử, có regression riêng.
