# ADR-0019 — Ba bảng mục tiêu năm học cha–con
- Status: ACCEPTED
- Date: 2026-10-29
- Nguồn: user yêu cầu ba bảng, chọn cha tùy chọn, duyệt plan DB-1 và vòng lặp spec.
## Bối cảnh
EduGoalRow trước đây lưu sub/group/goal trong cùng bảng, quan hệ suy ra từ sort_order. User yêu cầu FK cha–con tường minh.
## Quyết định
Ba bảng edu_plan_sub_domains → edu_plan_expected_results → edu_plan_goals. Cấp cha tùy chọn; goal có sub_domain_id để giữ MT trực tiếp dưới lĩnh vực con. FK ghép kiểm cùng plan/domain và sub_domain_key chuẩn hóa NULL. FK cha deferred; cập nhật trước khi xóa cha cũ.
Mapped superclass EduGoalRow chứa định danh, text và thứ tự chung; ba entity/repository riêng, không có bảng cha chung. API rows/kind giữ nguyên và ID duy nhất xuyên ba loại bằng sequence chung allocationSize=1.
V32 backfill từng plan/domain, giữ id/lineage/sort_order, liên kết Bộ và đổi goal_row_id thành goal_id. Dữ liệu thứ tự mơ hồ hoặc nội dung trỏ tiêu đề làm migration fail transaction trước drop bảng.
## Phương án đã cân nhắc
| Phương án | Lợi ích | Hạn chế |
| --- | --- | --- |
| Ba bảng + mapped superclass | FK thật, đúng yêu cầu, giữ mapper/API | Ba truy vấn lấy các loại dòng |
| Một bảng + parent_id | Ít sửa code | Không đáp ứng ba bảng |
| TABLE_PER_CLASS + repository polymorphic | Truy vấn dùng chung | UNION/delete phức tạp, khó kiểm soát |
| DTO cây lồng nhau | Cha rõ ngay tại API | Buộc đổi Web đồng thời, ngoài yêu cầu hiện tại |
## Hệ quả
Bảo toàn tham chiếu giáo án/học liệu qua ID; copy phiên bản remap FK cha bằng ID mới, giữ lineage. Schema/source phát hành đồng thời; không hạ source riêng sau migration. Cần sao lưu trước nâng DB dùng chung; task này chỉ kiểm trên PostgreSQL Testcontainers.
