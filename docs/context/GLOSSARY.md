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
| Báo ăn / tham gia ăn | MealParticipation | Trẻ có ăn bữa nào trong ngày (GV nhập cùng điểm danh) |
| Số suất ăn | MealCount | Tổng hợp MealParticipation theo campus/ngày; DRAFT → CONFIRMED |
| Suất đã chốt | MealCount CONFIRMED | Snapshot bất biến, bếp dùng để nấu |
| Xác nhận suất / lịch sử | MealConfirmation | Lần xác nhận số suất; thay đổi sau xác nhận ⇒ lịch sử + xác nhận lại |
| Thực phẩm | Food | Master data thực phẩm, có nguồn dữ liệu dinh dưỡng |
| Thành phần dinh dưỡng | NutrientValue | Giá trị dinh dưỡng của Food, từ nguồn kiểm chứng |
| Món ăn / công thức | Dish / Recipe | Món + định lượng nguyên liệu/suất |
| Thực đơn | MealPlan | Món theo ngày/bữa; nháp → duyệt (endpoint `/meal-plans/{id}/approve`) |
| Định lượng thực phẩm | FoodQuantityPlan | Suất đã chốt × định lượng công thức |
| Thực phẩm tươi / khô | Fresh / Stored ingredient | Tươi: NCC giao hằng ngày. Khô: có kho |
| Nhà cung cấp | Supplier | PENDING — không có module V1 |
| Chuẩn bị bữa | MealPreparation | Trạng thái bếp theo campus/bữa: chờ nấu → đang nấu → sẵn sàng bàn giao (PROPOSED) |
| Bàn giao suất | MealHandover | Bếp giao suất cho lớp, GV xác nhận số nhận (PROPOSED) |
| Đo sức khỏe | HealthRecord | Chiều cao, cân nặng, tình trạng, ghi chú theo đợt |
| Tiêu chí quan sát | ObservationCriteria | Tiêu chí trường cấu hình (tâm trạng, ăn, ngủ, hợp tác...) |
| Quan sát hằng ngày | Observation | Giá trị theo tiêu chí + ghi chú tùy chọn, 1 trẻ/ngày |
| Ngữ cảnh hoạt động | — | Hoạt động và MT trong giáo án tuần/ngày đã duyệt của lớp; nguồn của đánh giá theo MT (ADR-0006) |
| Hồ sơ phát triển | DevelopmentProfile | Read model tổng hợp lịch sử theo trẻ |
| Nhận xét tổng hợp | Summary | Nhận xét ngày/tuần/kỳ; DRAFT (template/AI) → APPROVED bởi GV |
| Phiếu bé ngoan | Reward | Khen thưởng tuần/tháng; "bé ngoan toàn diện" cuối năm (PROPOSED) |
| Đánh giá | Assessment | Dùng thay "feedback" khi nói về nhận xét trẻ (OBS-11) |
| Kế hoạch giáo dục năm học (mục tiêu năm học) | EduYearPlan | 1 kế hoạch / (năm học, nhóm tuổi), áp dụng toàn trường; Tổ trưởng và Phó HT cùng soạn, Phó HT ban hành |
| Nhóm tuổi / khối | AgeGroup | Nhà trẻ, Mẫu giáo bé, nhỡ, lớn; đơn vị của kế hoạch năm |
| Tổ trưởng (khối) | AGE_GROUP_TEAM_LEADER | Giáo viên được phân công phụ trách một nhóm tuổi |
| Lĩnh vực | EduDomain | 5 lĩnh vực của Chương trình GDMN (I–V) + lĩnh vực trường thêm |
| Lĩnh vực con | EduGoalRow kind=sub | Dải tiêu đề trong lĩnh vực (vd. Phát triển vận động, KPKH, LQVT) |
| Nhóm kết quả mong đợi | EduGoalRow kind=group / EduExpectedResult | Tầng giữa lĩnh vực con và mục tiêu, theo Chương trình GDMN |
| Mục tiêu (MT n) | EduGoalRow kind=goal, goalNo | Mục tiêu giáo dục, đánh số 1..n liên tục cả năm; sợi dây nối năm → chủ đề → tuần/ngày → đánh giá |
| Nội dung giáo dục | EduGoalContent | Nội dung của một MT, tích vào các chủ đề sẽ dạy |
| Chủ đề | EduTheme | Chủ đề trong năm (tên, viết tắt, lễ hội, từ – đến ngày) |
| Đợt nghỉ | EduBreak | Tết, ôn tập, nghỉ lễ; tuần trùng đợt nghỉ không tính vào chủ đề |
| Ban hành | issue | Phó HT duyệt kế hoạch năm; sau đó khóa sửa |
| Trả lại | return | Người duyệt trả về, bắt buộc lý do ⇒ Bị trả lại |
| Đang sửa | EDITING | Khóa soạn kế hoạch năm cho một người (EDU-10) |
| Chủ đề nhánh | — | Tên từng tuần học trong một chủ đề, GV lớp đặt cho cả năm |
| Kế hoạch (giáo dục) chủ đề | — | MT + nội dung + hoạt động giáo dục + môi trường giáo dục của một chủ đề cho một lớp |
| Giáo án tuần | — | Kế hoạch tuần (mục đích, chuẩn bị, dòng hoạt động × Thứ 2–6) + 5 kế hoạch ngày |
| Hồ sơ chủ đề | — | Kế hoạch chủ đề + giáo án tất cả nhánh của chủ đề; gửi và duyệt một lần |
| Chuỗi người ký | signers | Thứ tự người duyệt từng loại tài liệu, cấu hình được (mặc định Tổ trưởng → Phó HT) |
| Sự cố CSVC | FacilityReport | Báo hỏng/thiếu/không đủ thiết bị; BGH theo dõi |
| Bản nháp AI | AI draft | Output AI chưa chính thức, cần người duyệt |
| Bước nghiệp vụ / hệ thống | [B] / [S] | [B] người làm ngoài hệ thống; [S] hệ thống thực hiện |
