# Workflow — Build & deploy

> Hạ tầng chưa chốt (`docs/system/DEPLOYMENT.md` là SKELETON). Không suy đoán server, domain, secret — hỏi người dùng.

1. **Build:** lệnh build của project (kiểm tra Maven/Gradle thực tế).
2. **Test:** chạy toàn bộ test + test kiến trúc; không deploy khi fail.
3. **Docker:** build image theo Dockerfile trong repo (khi có).
4. **Environment:** biến môi trường theo danh sách tên biến trong DEPLOYMENT.md; secret không nằm trong repo/image.
5. **Deploy:** theo DEPLOYMENT.md. Thao tác lên server thật, migration DB production, xóa dữ liệu ⇒ xác nhận với người dùng trước.
6. **Verify:** health check, đăng nhập, 1 luồng chính (điểm danh → số suất), log không lỗi.
7. **Memory:** lỗi build/deploy tốn >15 phút ⇒ `docs/knowledge/TROUBLESHOOTING.md` + `ENV-xxx` trong ISSUE_INDEX. Cập nhật DEPLOYMENT.md khi quy trình thay đổi.
