---
id: BUG-261108-returned-status-draft
type: bug
modules: [education-plan]
rules: [EDU-54]
status: fixed
date: 2026-11-08
keywords: [trả lại, DRAFT, trạng thái, hồ sơ]
---

# Hồ sơ đã trả lại hiển thị Nháp

Backend lưu `DRAFT` sau trả lại để mở lại quyền sửa; lịch sử có sự kiện `RETURN`/`reject` và kế hoạch năm có lý do trả lại. FE hiển thị trực tiếp `DRAFT`, làm người soạn không phân biệt hồ sơ mới với hồ sơ bị trả lại. User yêu cầu hiển thị “Trả lại”.

Hướng sửa: chỉ suy trạng thái hiển thị từ lịch sử trả lại khi mã hiện tại là `DRAFT`; giữ nguyên mã backend, quyền và luồng gửi/duyệt. Trạng thái `REVIEW`/`APPROVED` sau gửi lại không kế thừa nhãn trả lại.

FE suy nhãn từ sự kiện chuyển trạng thái gần nhất hoặc lý do trả lại của kế hoạch năm. Đã kiểm thử 297/297 test giáo dục, ESLint các file liên quan và build FE đều đạt; chưa kiểm tra trên trình duyệt kết nối backend thực.
