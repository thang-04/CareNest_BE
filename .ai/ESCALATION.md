# Mở rộng context có điều kiện

- **L1 — cục bộ:** đọc source, test, cấu hình và thay đổi gần khu vực được yêu cầu. Nếu chưa có source, ghi nhận trạng thái thay vì suy đoán.
- **L2 — domain:** thêm `REPO_CONTEXT.md`, luồng/nghiệp vụ, API, dữ liệu và test liên quan khi những nguồn đó đã tồn tại.
- **L3 — nhiều domain/repo:** lập danh sách điểm chạm; kiểm tra các module BE liên quan và FE/APP khi thay đổi contract hoặc hành vi client. Luồng điểm danh → suất ăn → báo cáo là ví dụ cần xem xét cùng nhau.
- **L4 — hệ thống:** đọc đầy đủ các nhóm nguồn có liên quan cho thay đổi kiến trúc, auth/RBAC, đa cơ sở, dữ liệu trẻ em hoặc vận hành lớn. Ưu tiên đầy đủ bằng chứng trước kết luận.

Nâng mức khi phát hiện dependency ngoài phạm vi đang đọc; không quét toàn repo chỉ vì tên task có chữ “feature”. Với nguồn chưa tồn tại, ghi thiếu dữ liệu, hỏi/chốt quyết định cần thiết hoặc giới hạn kết luận. Khi code, tài liệu và yêu cầu khác nhau: nêu rõ xung đột, xác minh intended behavior rồi mới sửa.

