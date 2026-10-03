# Workflow sửa lỗi Backend

1. Tái hiện triệu chứng bằng dữ liệu tối thiểu và xác định hành vi mong đợi từ nguồn hiện có.
2. Đọc source/test gần lỗi; tra `docs/knowledge/ISSUE_INDEX.md` nếu file đã tồn tại, dùng incident phù hợp như manh mối.
3. Chứng minh nguyên nhân; xét tác động đến dữ liệu dẫn xuất, API, FE/APP và quyền truy cập trước khi sửa.
4. Sửa hẹp theo `../../docs/backend-coding-guide.md`, chạy regression test phù hợp, chạy Checklist mục 17 trên diff và báo đúng phạm vi đã kiểm chứng.
5. Ghi incident/pattern khi lỗi có giá trị tái sử dụng và nơi lưu tri thức đã được tạo; không tạo hồ sơ giả cho lỗi chưa xác nhận.

