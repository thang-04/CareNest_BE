# Workflow đổi dữ liệu Backend

1. Xác định chủ sở hữu dữ liệu, campus scope, dữ liệu nhạy cảm, quan hệ và các dữ liệu dẫn xuất bị ảnh hưởng.
2. Kiểm tra schema và dữ liệu thực tế đang tồn tại. Thay đổi schema bằng migration Flyway mới trong `src/main/resources/db/migration` (mục 12 của `../../docs/backend-coding-guide.md`); không sửa migration đã chạy.
3. Thiết kế đường nâng cấp, ràng buộc và khả năng tương thích trước khi thay đổi dữ liệu; xác nhận ảnh hưởng API và các module dùng dữ liệu.
4. Kiểm chứng migration/query bằng test phù hợp với stack đã chọn; báo rủi ro dữ liệu và cách rollback nếu có thay đổi triển khai.

