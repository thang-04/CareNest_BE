---
id: ENV-261108-annual-visibility-stale-runtime
type: env
modules: [education-plan]
rules: [EDU-60]
status: open
date: 2026-11-08
keywords: [annual, REVIEW, stale-runtime, restart, 8080, 8089]
similar_to: [ENV-261108-scoped-native-validation]
---
# BE đang dùng chưa nạp bản sửa phân quyền năm học

## Symptom
User báo tổ trưởng/GV vẫn xem được năm Nháp/Đang duyệt sau triển khai013. Đọc API thật8080 bằng tài khoản tổ trưởng thử trả200 với1annual REVIEW; cấu hình schoolYear hiện tại chỉ PHTsoạn/HTduyệt, không có quyền năm độc lập cho tổ trưởng.

## Attempts / Root cause
1. FE local đang dùng API thật, không phải mock. API trực tiếp cũng trả REVIEW nên không phải chỉ lỗi lọc/render UI.
2. PID8388 giữ cổng8080 có StartTime07/11/2026 22:10:46, trước bản sửa013 ngày08/11. Source hiện tại dùng annualViewScope; native94tests và E2E thực đã đạt ở BE mới8089/database riêng.
3. Lượt kiểm trước cố ý giữ8080 của user, chỉ chạy/dừng runtime8089. Bản sửa source đã có nhưng chưa áp dụng vào tiến trình UI đang dùng. Không sửa lại nghiệp vụ Q1=A hoặc thêm client-only filter để che runtime cũ.

## Fix / Evidence pending
Đang hỏi user cho phép restart8080 hoặc tự restart trong IDE theo giới hạn tiến trình ở quickstart. Sau restart phải kiểm lại list teacher1 không còn DRAFT/REVIEW và directid trước đó403, HT/PHT giữ quyền năm độc lập. Không ghi password/token/tên hoặc nội dung kế hoạch vào evidence. Chưa ghi fixed khi chỉ có test runtime riêng.

## Lesson
Phân biệt rõ source đã sửa, runtime kiểm thử và runtime UI đang dùng khi bàn giao. Native/E2E runtime riêng không chứng minh process lâu ngày đã nạp code mới.
