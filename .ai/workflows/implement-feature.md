# Workflow làm tính năng Backend

1. Chốt actor, mục tiêu, quy tắc đã được xác nhận và điều kiện hoàn thành; nêu rõ phần chưa biết.
2. Lần theo luồng nghiệp vụ, boundary module, quyền, dữ liệu, API và tác động FE/APP; nâng context theo `../ESCALATION.md`.
3. Thiết kế thay đổi nhỏ nhất giữ rule xác định ở BE; AI chỉ tạo đề xuất có thể kiểm tra.
4. Implement theo `../../docs/backend-coding-guide.md` cùng test cho hành vi chính, lỗi quan trọng và trường hợp bị từ chối quyền; kiểm tra hợp đồng/tài liệu đã tồn tại có cần cập nhật không.
5. Chạy Checklist mục 17 của guide trên diff; sửa mục chưa đạt.
6. Báo những gì đã chạy, kết quả Checklist (mục không áp dụng/chưa đạt), phần chưa được xác minh và tác động liên repo còn chờ.

