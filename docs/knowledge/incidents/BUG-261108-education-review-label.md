---
id: BUG-261108-education-review-label
type: bug
modules: [education-plan]
rules: [EDU-54]
status: fixed
date: 2026-11-08
keywords: [REVIEW, chờ duyệt, đang duyệt, thống kê]
---

# Nhãn và số hồ sơ chờ duyệt không nhất quán

User yêu cầu hồ sơ giáo dục đã gửi duyệt hiển thị “Chờ duyệt”, không dùng trạng thái “Đang duyệt”. Mã `REVIEW` và luồng phê duyệt được giữ nguyên; các trạng thái chờ cũ vẫn cần hiển thị và được đếm đúng.

Nguyên nhân: FE dùng nhãn chung `REVIEW: Đang duyệt`, còn tổng quan toàn trường và một số dashboard đếm các mã `PENDING_*` cũ nhưng bỏ qua `REVIEW`.

Khắc phục: dùng “Chờ duyệt” cho `REVIEW` và các mã chờ cũ; thống kê tổng quan/dashboard đếm cả `REVIEW`, giữ mã API và quy tắc duyệt. Regression `review-status-label.test.mjs` tái hiện nhãn cũ trước sửa; sau sửa kiểm cả nhãn và thẻ/lọc toàn trường. Education + dashboard 345/345, lint và build đạt. Lượt đầu fixture SSR kỳ vọng thừa HTML comment, sửa đúng theo HTML thực và chạy lại.
