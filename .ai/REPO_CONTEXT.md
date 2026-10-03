# CareNest — bối cảnh Backend

Nguồn: brief CareNest do chủ dự án cung cấp ngày 23/09/2026. Các nghiệp vụ dưới đây là định hướng hiện tại; chi tiết chưa được ghi rõ không được tự biến thành business rule.

## Vai trò repository

CareNest_BE sở hữu API, business logic, dữ liệu, authorization và tri thức cấp hệ thống. Web (`CareNest_FE`) và mobile (`CareNest_APP`) là client của cùng hợp đồng API. Dự án có đúng ba repository. Source hiện là skeleton nền móng (Java 21, Spring Boot 4.1.1): cấu trúc package, `ApiCode`/`ResponseJson`/`GlobalExceptionHandler`, prefix API cấu hình được, OpenAPI, Docker Compose. Chưa có authentication/authorization (team đang chốt) và chưa có entity/migration nghiệp vụ. Tài liệu trong `docs/`: `backend-coding-guide.md`.

## Nghiệp vụ cần giữ khi thiết kế

- Các nhóm chính: Dinh dưỡng & Sức khỏe trẻ, Học tập & hoạt động, Cơ sở vật chất & tài sản; cùng các phần dùng chung như danh tính, cơ sở, lớp, trẻ, nhân sự, thông báo, phê duyệt, báo cáo và audit.
- Điểm danh đã xác nhận ảnh hưởng số suất ăn, nhà bếp và báo cáo. Đơn xin nghỉ sau xử lý ảnh hưởng điểm danh rồi số suất ăn. Thay đổi dữ liệu nguồn phải xem xét dữ liệu dẫn xuất và bên nhận thông báo.
- Dị ứng của trẻ phải được xét khi gợi ý/chọn thực đơn. Dữ liệu thành phần dinh dưỡng nền phải lấy từ nguồn đã lưu và được kiểm chứng. AI chỉ đề xuất; chính sách nghiệp vụ xác định và kiểm tra kết quả.
- Có định hướng nhiều cơ sở: dữ liệu và quyền truy cập gắn với cơ sở cần được xem xét đúng phạm vi. Thông tin sức khỏe trẻ chỉ phục vụ hỗ trợ theo dõi, không mặc nhiên là chẩn đoán.

## Quyết định kỹ thuật v1

Nguồn: `CareNest_Backend_Architecture_v2.docx` (ngoài repo). Backend là layered monolith 3 lớp (controller → service → repository) trên Spring Boot; PostgreSQL + Spring Data JPA + Flyway; auth định hướng JWT + kiểm tra role và campus/class (chưa chốt, chưa triển khai); REST API với prefix cấu hình được, response chung `{code, desc, data}` với `code` trùng HTTP status; adapter riêng cho AI, storage, notification. Phạm vi một trường, hai cơ sở; không làm SaaS nhiều trường, không chẩn đoán y tế, không thay PMS/GoKids.

Chi tiết và quy tắc code ở `docs/backend-coding-guide.md`. Khi source khác guide, báo xung đột trước khi sửa.

