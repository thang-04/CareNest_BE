# Glossary — thuật ngữ thống nhất

Code dùng tên tiếng Anh; UI/docs dùng tiếng Việt.

| Tiếng Việt | Code / EN | Định nghĩa |
| --- | --- | --- |
| Trường | School | Trường Mầm non Thượng Hồng — duy nhất trong hệ thống |
| Điểm trường | Campus | Cơ sở vật lý thuộc trường (hiện 2). Không phải tenant |
| Lớp | Classroom | Lớp thuộc một campus, theo năm học |
| Năm học | SchoolYear | Kỳ gắn lớp, enrollment, assignment |
| Ban giám hiệu (BGH) | School Management | Principal + Vice Principal |
| Hiệu trưởng | Principal | Scope toàn trường |
| Hiệu phó | Vice Principal | Scope theo campus được phân công |
| Phân công | StaffAssignment | Gán nhân sự vào School/Campus/Class, có hiệu lực từ–đến |
| Liên kết phụ huynh | GuardianChildLink | Phụ huynh ↔ trẻ; nguồn của Parent scope |
| Ghi danh | Enrollment | Trẻ thuộc lớp nào trong khoảng thời gian nào |
| Phạm vi truy cập | Access scope | SCHOOL / CAMPUS / CLASS / CHILD |
| Điểm danh | AttendanceRecord | Trạng thái có mặt của 1 trẻ trong 1 ngày |
| Đơn xin nghỉ / báo nghỉ | LeaveRequest | Phụ huynh thông báo nghỉ cho 1 trẻ trong khoảng ngày; không có bước duyệt (ATT-05) |
| ~~Báo ăn / tham gia ăn~~ | ~~MealParticipation~~ | Bỏ 28/10: có mặt là ăn mọi bữa của nhóm tuổi, GV không nhập riêng (ATT-02) |
| Số suất ăn | MealCount | Số trẻ có mặt × bữa của nhóm tuổi, theo campus/ngày; DRAFT → CONFIRMED |
| Suất đã chốt | MealCount CONFIRMED | Snapshot bất biến, bếp dùng để nấu |
| Xác nhận suất / lịch sử | MealConfirmation | Lần xác nhận số suất; thay đổi sau xác nhận ⇒ lịch sử + xác nhận lại |
| Thực phẩm | Food | Master data thực phẩm, có nguồn dữ liệu dinh dưỡng |
| Thành phần dinh dưỡng | NutrientValue | Giá trị dinh dưỡng của Food, từ nguồn kiểm chứng |
| Món ăn / công thức | Dish / Recipe | Món + định lượng nguyên liệu/suất |
| Thực đơn | MealPlan | Món theo ngày/bữa; nháp → duyệt (endpoint `/meal-plans/{id}/approve`) |
| Định lượng thực phẩm | FoodQuantityPlan | Suất đã chốt × định lượng công thức |
| Thực phẩm tươi / khô | Fresh / Stored ingredient | Tươi: NCC giao hằng ngày. Khô: có kho |
| Nhà cung cấp | Supplier | Danh mục NCC dùng chung hai cơ sở (NUT-12) |
| Giờ chốt | Cut-off | Mốc khóa điểm danh và chốt suất cả ngày; HT cấu hình theo mùa (ATT-06) |
| Sĩ số dự kiến | Expected headcount | Số trẻ dùng khi công bố thực đơn để đặt hàng tươi và trừ kho dự kiến (NUT-07) |
| Kiểm tra đầu ngày | Morning health check | GV đo nhiệt độ, quan sát khi nhận trẻ, nhập cùng điểm danh (HLT-07) |
| Đo sức khỏe / cân đo | HealthRecord | Chiều cao, cân nặng, tình trạng, ghi chú theo đợt; lưu mọi lần đo (HLT-13) |
| Phân loại WHO | Growth classification | Hệ thống tự phân loại theo chuẩn WHO; GV chỉnh kèm lý do (HLT-03, HLT-10) |
| Công bố (kết quả) | Publish | Bước GV/PHT cho phụ huynh thấy dữ liệu có công bố (PAR-03) |
| Sức khỏe trong ngày | Daily health incident | Triệu chứng, xử trí, tai nạn nhỏ trong ngày; báo phụ huynh ngay (HLT-14) |
| Đón trẻ / trả trẻ | Pickup | GV xác nhận người đón, giờ đón; là bước xác nhận trả trẻ (PCK-04, ATT-10) |
| Tiêu chí đánh giá | ObservationCriteria | 7 mặt đánh giá ngày; cố định hay trường cấu hình: P-11 (OBS-02) |
| Đánh giá ngày | Observation | Mức Đạt / Cần cố gắng / Chưa đạt theo từng mặt + nhận xét tùy chọn, 1 trẻ/ngày; khóa hết ngày (OBS-01, OBS-12) |
| Kế hoạch giáo dục | Education plan (`eduplan`) | Mục tiêu năm học → chủ đề nhánh → chủ đề tuần → kế hoạch ngày (EDU-01) |
| Tổ trưởng chuyên môn | Team leader (phân công) | GV được phân công phụ trách nhóm tuổi; duyệt kế hoạch bước giữa (EDU-03, AUTH-23) |
| Ngữ cảnh hoạt động | Kế hoạch ngày | Hoạt động + mục tiêu trong kế hoạch ngày; dùng để đánh giá kiến thức–kỹ năng (OBS-07) |
| Hồ sơ phát triển | DevelopmentProfile | Read model tổng hợp lịch sử theo trẻ; không có mục theo dõi riêng (OBS-03) |
| Nhận xét định kỳ | Summary | Nhận xét theo trẻ tuần/tháng/học kỳ/năm; nháp (mẫu nền/AI) → GV xác nhận, không qua BGH duyệt (OBS-05) |
| Phiếu bé ngoan | Reward | Phiếu tuần: ≥4 ngày đi học + ≥3 cờ; 3 phiếu/tháng = xuất sắc (OBS-10). Khen cuối năm: OBS-14 |
| Đánh giá | Assessment | Dùng thay "feedback" khi nói về nhận xét trẻ (OBS-11) |
| Bản nháp AI | AI draft | Output AI chưa chính thức, cần người duyệt |
| Bước nghiệp vụ / hệ thống | [B] / [S] | [B] người làm ngoài hệ thống; [S] hệ thống thực hiện |
