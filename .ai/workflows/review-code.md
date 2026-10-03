# Workflow review Backend

Đọc mục tiêu thay đổi và diff; đối chiếu luồng nghiệp vụ, authorization/campus scope, API, dữ liệu dẫn xuất, transaction và test. Ưu tiên lỗi có thể gây sai hành vi hoặc lộ dữ liệu; mỗi phát hiện phải có vị trí, kịch bản xảy ra và cách sửa khả thi. Phân biệt lỗi đã chứng minh với câu hỏi hoặc giả định; không tuyên bố đã chạy runtime nếu chỉ review tĩnh.

Đối chiếu diff với Checklist mục 17 của `../../docs/backend-coding-guide.md`: phụ thuộc giữa các lớp, không trả entity, service không dùng HTTP/ResponseJson, prefix API không hardcode, mọi response (kể cả lỗi mới) đúng `{code, desc, data}` với code = HTTP status, logging không lộ dữ liệu nhạy cảm, không catch rỗng, migration Flyway mới. Vi phạm guide ghi kèm số mục guide.

