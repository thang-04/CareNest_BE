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
| Sĩ số dự kiến | Expected headcount | Số trẻ dùng cho bản dự trù để đặt hàng tươi; hệ thống điền sẵn sĩ số đang học, PHT sửa (NUT-07). Không dùng để trừ kho |
| Bản dự trù | Food estimate | Lượng thực phẩm cần theo thực đơn dự kiến × sĩ số dự kiến cho một kỳ và cơ sở; lưu, in danh sách đặt hàng (NUT-07, NUT-12) |
| Thực đơn dự kiến ngày | Provisional daily menu | Thực đơn ngày đã cân đối, lưu trước khi chốt suất; chỉ cảnh báo kho, chưa trừ (NUT-08, NUT-11) |
| Thực đơn tuần đã công bố | Published weekly menu | Thực đơn cả tuần PHT công bố một lần cho cả trường; không công bố theo ngày, đến ngày chỉ thêm ảnh bữa (NUT-08, chốt 02/11; thay "thực đơn chuẩn ngày") |
| Kiểm tra đầu ngày | Morning health check | GV đo nhiệt độ, quan sát khi nhận trẻ, nhập cùng điểm danh (HLT-07) |
| Đo sức khỏe / cân đo | HealthRecord | Chiều cao, cân nặng, tình trạng, ghi chú theo đợt; lưu mọi lần đo (HLT-13) |
| Phân loại WHO | Growth classification | Hệ thống tự phân loại theo chuẩn WHO (HLT-03): mức BMI theo tuổi của kỳ cân đo ở sổ sức khỏe (HLT-18); cân nặng/tuổi, chiều cao/tuổi chỉ trong báo cáo tăng trưởng (HLT-20). GV chỉnh tay kèm lý do còn mở (P-40) |
| Báo cáo tăng trưởng (mẫu 5.1) | Growth report | Bảng tổng hợp theo tháng, lớp, MG/NT cho HT/PHT (HLT-20): PTBT = phát triển bình thường; SDDNC = suy dinh dưỡng nhẹ cân (cân nặng/tuổi < −2SD); SDDTC = suy dinh dưỡng thấp còi (chiều cao/tuổi < −2SD); SDD thể gầy còm = BMI/tuổi < −2SD |
| Mức BMI theo tuổi | BMI-for-age status (`HealthBmiStatus`) | Gầy còm nặng, gầy còm, bình thường, nguy cơ thừa cân, thừa cân, béo phì theo z-score WHO; chỉ số đo kỳ cân đo, tính khi đọc (HLT-18) |
| Cảnh báo sức khỏe | Health warning | Kết luận bác sĩ (đợt khám) hoặc mức BMI (kỳ cân đo) lưu ở sổ sức khỏe; không có phiếu hay bước xử lý (HLT-19) |
| Phiếu theo dõi sức khỏe | Health follow-up | Đã bỏ 2026-11-04 (HLT-16 REMOVED); dữ liệu cũ giữ trong DB |
| Công bố (kết quả) | Publish | Bước GV/PHT cho phụ huynh thấy dữ liệu có công bố (PAR-03) |
| Sức khỏe trong ngày | Daily health incident | Triệu chứng, xử trí, tai nạn nhỏ trong ngày; báo phụ huynh ngay (HLT-14) |
| Đón trẻ / trả trẻ | Pickup | GV xác nhận người đón, giờ đón; là bước xác nhận trả trẻ (PCK-04, ATT-10) |
| Tiêu chí đánh giá | ObservationCriteria | 7 mặt đánh giá ngày; cố định hay trường cấu hình: P-11 (OBS-02) |
| Đánh giá ngày | Observation | Mức Đạt / Cần cố gắng / Chưa đạt theo từng mặt + nhận xét tùy chọn, 1 trẻ/ngày; khóa hết ngày (OBS-01, OBS-12) |
| Kế hoạch giáo dục | Education plan (`eduplan`) | Kế hoạch năm học (mục tiêu năm học) → chủ đề nhánh → kế hoạch chủ đề → giáo án tuần/ngày (EDU-01..28; khảo sát: EDU-29) |
| Kế hoạch giáo dục năm học (mục tiêu năm học) | EduYearPlan | 1 kế hoạch / (năm học, nhóm tuổi), áp dụng toàn trường; Tổ trưởng và Phó HT cùng soạn, Phó HT ban hành |
| Nhóm tuổi / khối | AgeGroup | Nhà trẻ, Mẫu giáo bé, nhỡ, lớn; đơn vị của kế hoạch năm |
| Tổ trưởng (khối) / tổ trưởng chuyên môn | AGE_GROUP_TEAM_LEADER (phân công) | GV được HT phân công phụ trách một nhóm tuổi; cùng soạn, gửi kế hoạch năm, duyệt tài liệu lớp theo chuỗi (EDU-02, EDU-18, AUTH-23) |
| Lĩnh vực | EduDomain | 5 lĩnh vực của Chương trình GDMN (I–V) + lĩnh vực trường thêm |
| Lĩnh vực con | EduGoalRow kind=sub | Dải tiêu đề trong lĩnh vực (vd. Phát triển vận động, KPKH, LQVT) |
| Nhóm kết quả mong đợi | EduPlanExpectedResult | Tầng giữa lĩnh vực con và mục tiêu, theo Chương trình GDMN |
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
| Ngữ cảnh hoạt động | — | Hoạt động và MT trong giáo án tuần/ngày đã duyệt của lớp; nguồn của đánh giá theo MT (ADR-0006, OBS-07) |
| Hồ sơ phát triển | DevelopmentProfile | Read model tổng hợp lịch sử theo trẻ; không có mục theo dõi riêng (OBS-03) |
| Nhận xét định kỳ | Summary | Nhận xét theo trẻ tuần/tháng/học kỳ/năm; nháp (mẫu nền/AI) → GV xác nhận, không qua BGH duyệt (OBS-05) |
| Phiếu bé ngoan | Reward | Phiếu tuần: ≥4 ngày đi học + ≥3 cờ; 3 phiếu/tháng = xuất sắc (OBS-10). Khen cuối năm: OBS-14 |
| Đánh giá | Assessment | Dùng thay "feedback" khi nói về nhận xét trẻ (OBS-11) |
| Bản nháp AI | AI draft | Output AI chưa chính thức, cần người duyệt |
| Bước nghiệp vụ / hệ thống | [B] / [S] | [B] người làm ngoài hệ thống; [S] hệ thống thực hiện |
