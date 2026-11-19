---
id: BUG-261110-released-branch-leak
type: bug
modules: [education-plan]
rules: [EDU-73, EDU-74]
status: fixed
date: 2026-11-10
keywords: [nhánh, học kỳ, ban hành, lộ nháp, branches]
---

# BUG-261110-released-branch-leak

- **Module:** education-plan · `EduClassDocServiceImpl#releasedBranchNode` (EDU-73, EDU-74 "người chỉ xem chỉ thấy tên nhánh của học kỳ có gói đã ban hành").
- **Triệu chứng:** HT mở "Chủ đề / Chủ đề nhánh" của lớp: vừa thấy thông báo "Chưa có lịch chủ đề nhánh đã duyệt của học kỳ 1" vừa thấy tên nhánh HK1 bản Nháp.
- **Root cause:** released view chỉ thay `names` bằng tên của bản ban hành, còn `branches` và `effectiveCalendar.branches` vẫn là nội dung đang soạn. Web lịch v2 (`schemaVersion: 2`) dựng `names` lại từ `branches` ⇒ lộ nháp.
- **Phát hiện:** e2e `CareNest_FE/tests/e2e/system/edu-semester-flow-ui.spec.mjs` (ST-002-07) — test API cũ chỉ kiểm `names` nên không bắt được.
- **Sửa:** lọc `branches`/`effectiveCalendar.branches` chỉ giữ nhánh có tuần trong bản ban hành, tên theo bản ban hành, bỏ ghi chú nháp; thêm assert vào `EduClassDocServiceIntegrationTest#viewerSeesOnlyReleasedSemesterBranchNames`.
- **Bài học:** quyền xem theo bản ban hành phải lọc mọi trường suy ra được nội dung, không chỉ trường hiển thị chính.
