# Non-Functional Requirements

| Nhóm | Yêu cầu | Status |
| --- | --- | --- |
| Quy mô | 1 trường, 2 campus, 24 lớp, ~506 trẻ, ~60 nhân sự + phụ huynh | CONFIRMED |
| Kiến trúc | 1 instance BE + 1 PostgreSQL đủ; không scale-out | ACCEPTED |
| Availability | Không yêu cầu 24/7 HA, không DR đầy đủ (exclusion) | CONFIRMED |
| Hiệu năng | Nhập điểm danh/quan sát cả lớp (20–25 trẻ) trong 1 thao tác lưu; mục tiêu phản hồi < 2s ở quy mô trên | PROPOSED |
| Thời điểm cao điểm | Buổi sáng (điểm danh + báo ăn trước cut-off) | CONFIRMED (cut-off PENDING) |
| Bảo mật | RBAC + access scope ở BE; HTTPS; mật khẩu băm; audit hành động nhạy cảm | ACCEPTED |
| Privacy | Dữ liệu trẻ tối thiểu quyền; AI chỉ nhận dữ liệu tối thiểu, bí danh; không log nội dung nhạy cảm | ACCEPTED |
| AI | Nghiệp vụ chạy khi AI tắt; AI timeout không chặn request | CONFIRMED |
| Backup | Backup PostgreSQL định kỳ (tần suất PENDING) | PROPOSED |
| Ngôn ngữ | UI tiếng Việt; thông báo lỗi tiếng Việt | PROPOSED |
| Timezone | `Asia/Ho_Chi_Minh` cho ngày nghiệp vụ | ACCEPTED |
