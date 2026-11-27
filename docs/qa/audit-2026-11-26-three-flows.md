# Rà soát ba luồng: cấu hình trường, điểm danh & suất ăn, đánh giá trẻ (26/11/2026)

Nguồn: ba lượt đọc code chỉ-đọc trên nhánh `feature/G94-236-BE-FEAT-29-daily-attendance`. Chưa chạy lại từng kịch bản trên hệ thống thật. Mọi so sánh với quy định của Bộ GD&ĐT (Thông tư 52/2020/TT-BGDĐT, Chương trình GDMN VBHN 01/2021) chỉ dựa trí nhớ, **chưa đối chiếu văn bản gốc**.

Mức: **Cao** = sai dữ liệu hoặc vi phạm rule đã chốt; **TB** = sai trong tình huống hiếm hoặc thiếu kiểm soát; **Thấp** = trải nghiệm, hiệu năng, câu chữ.

## A. Điểm danh và suất ăn

| # | Mức | Vấn đề | Vị trí |
|---|-----|--------|--------|
| A1 | Cao | Race: PHT xác nhận sổ ngay lúc GV lưu ở ngày mở lại. `AttendanceChangedEvent` xử lý sau commit nên sổ đã xác nhận có thể bị chuyển lại "cần xác nhận lại", trái ATT-07. Lỗi listener bị nuốt, sổ lệch. `confirm` không có kiểm số đã xem. | `MealCountAttendanceListener`, `MealCountServiceImpl` confirm/markAttendanceChanged |
| A2 | Cao | Đổi giờ chốt có hiệu lực hôm nay giữa ngày: đơn nghỉ gửi trước đó bị khóa nhầm hoặc mất nhãn dù bản ghi EXCUSED còn; nâng giờ chốt sau khi tổng hợp làm mở lại lưu/tạo/hủy đơn. `confirm` không kiểm `baseLocked`. | `AttendanceCutoffServiceImpl:167`, `AttendanceDayRules:90-123`, `AttendanceServiceImpl` leaveTags |
| A3 | TB | Thôi học hoặc chuyển lớp không xử lý đơn nghỉ và bản ghi EXCUSED đã tạo cho các ngày từ ngày nghỉ. Chuyển lớp bị chặn nhầm bởi bản ghi do đơn tạo, câu báo sai. | `ChildRecordServiceImpl`, `LeaveRequestServiceImpl` |
| A4 | TB | Ngày mở lại đổi trạng thái nhưng không đổi suất thì không phát event: số có mặt/vắng/chưa điểm danh trên sổ lệch, kể cả trẻ không báo ăn. | `AttendanceServiceImpl:491` |
| A5 | TB | `closeMorningLeaves` chỉ xét hôm nay; lỡ lần chạy thì kẹt (tính 1,0 vắng có phép thay 0,5 + 0,5). Một transaction cho mọi lớp: một lớp lỗi làm rollback hết. Lịch sử ghi người tạo đơn là người sửa. Thông báo từng trẻ. | `AttendanceServiceImpl:671-730` |
| A6 | TB | Trả trẻ: không khóa lớp, không `@Version`, dễ mất cập nhật khi chạy cùng lúc với GV lưu hoặc tạo đơn; có thể ghi 2 lần HANDED_OVER; giờ trả chỉ kiểm không ở tương lai (gõ nhầm 10:00 thay 16:00 là đặt vĩnh viễn nghỉ chiều), không có đường sửa. | `PickupServiceImpl:174-254` |
| A7 | TB | Trả sớm giữ nguyên bữa, còn đơn nghỉ nửa buổi thì bỏ bữa xế: hai đường không nhất quán với sổ chưa xác nhận. | `PickupServiceImpl:239-254`, `AttendanceServiceImpl:451` |
| A8 | Thấp-TB | `canRecordDay` và `dayTeacherIds` xét GV chính tại hôm nay, không theo ngày: GV cũ không sửa được ngày cũ được mở lại, GV mới được báo. | `AttendanceDayRules:326-373` |
| A9 | Thấp-TB | Tạo/hủy đơn nghỉ đổi bữa mà không qua `MealChangeGuard`; chỉ an toàn nhờ giờ chốt (xem A2). Chuyển lớp khác điểm trường cùng ngày chỉ báo cho điểm trường mới. | `LeaveRequestServiceImpl:586-746` |
| A10 | Thấp | Thu hồi mở lại không khóa lớp (cửa sổ nhỏ); `getUnlocks` đọc cả bảng; không giới hạn số lần cấp lại. | `AttendanceDayUnlockServiceImpl` |
| A11 | Thấp | Thực tế: không có đường nhận trẻ đến muộn buổi sáng sau giờ chốt; chỉ T2–T6, không có thứ 7 hay học bù; đơn tối đa 30 ngày lịch kể cả nghỉ lễ; đơn không vượt qua hai năm học. | `AttendanceServiceImpl`, `LeaveRequestServiceImpl:201-206` |
| A12 | Thấp | Thông báo "cô ghi con có mặt" gửi lại mỗi lần đổi qua lại; thông báo đóng buổi sáng gửi từng trẻ. | `AttendanceServiceImpl:503-520` |
| A13 | Thấp | Ngoài năm học trả mã `ATTENDANCE_TODAY_ONLY`, câu "chỉ hôm nay" gây hiểu nhầm. | `AttendanceServiceImpl:302-305` |
| A14 | Thấp | Mock FE điểm danh là bộ quy tắc viết riêng, lệch BE với các khái niệm mới; đồng hồ FE poll 30 giây. | `attendanceMockRepository.js`, `AttendancePage.jsx` |

## B. Đánh giá trẻ

| # | Mức | Vấn đề | Vị trí |
|---|-----|--------|--------|
| B1 | Cao | Mất cập nhật giữa hai GV chính: FE gửi cả dòng chưa ghi kèm mặc định (ăn hết, ngủ tốt…), BE ghi đè không kiểm phiên bản; phụ huynh thấy mặc định. Trái OBS-17 (hệ thống không tự ghi). | `dailyGeneralDraft.js:24-27`, `DailyAssessmentServiceImpl:244-266` |
| B2 | Cao | Phụ huynh còn xem đánh giá, nhận xét, khen thưởng, sức khỏe của con đã thôi học (chỉ kiểm `isGuardianOf`). OBS-36 và CHD-04 mâu thuẫn nhau. | `ParentAssessmentServiceImpl:280`, `WeeklyCommentServiceImpl:335` |
| B3 | Cao | Ngày mở lại có thể không ai ghi được khi GV đã đổi phân công (cần phân công hôm nay và phân công theo ngày); thông báo đến người không làm được. | `ObservationDayRules:256-272` |
| B4 | TB-Cao | Ghi chú tự do gửi phụ huynh (tổng kết cuối năm, chủ đề, học kỳ) chỉ kiểm độ dài, không lọc từ chẩn đoán. | `YearEndSheetServiceImpl`, `ThemeEvaluationServiceImpl:1019` |
| B5 | TB | Danh sách từ chẩn đoán yếu và chặn thừa: không chuẩn hóa dấu/NFC, lọt "tự kĩ", "autism"; chặn nhầm "rối loạn tiêu hóa" từ ghi chú y tế điền sẵn làm rollback cả lớp. | `DailyAssessmentServiceImpl:60-71, 231, 246` |
| B6 | TB | Nháp nhận xét tuần bằng AI giữ khóa lớp trong lúc gọi AI tuần tự (có thể vài phút), chặn điểm danh và đánh giá của lớp. | `WeeklyCommentServiceImpl:136-205` |
| B7 | TB | Kiểm tra đầu ra AI tuần yếu hơn kiểm tra kỳ: không kiểm số bịa, markdown, nhãn chủ đề. | `AiWeeklyCommentDraftGenerator:56-65` |
| B8 | TB | Cờ khen thưởng cho phụ huynh bỏ qua điểm danh (không lọc `PRESENT`, không ẩn ngày đang sửa) nên lệch với phiếu. | `ParentAssessmentServiceImpl:236-244` |
| B9 | TB | Trẻ chuyển lớp giữa tuần: GV lớp cũ không gửi được nhận xét các ngày cũ; lớp mới chỉ có phần của mình; danh sách quá hạn đếm mãi. Không sửa được ngày mở lại cho trẻ đã chuyển/nghỉ. | `WeeklyCommentServiceImpl:288-292, 813-836`, `DailyAssessmentServiceImpl:435-441` |
| B10 | TB | Xác nhận kết quả chủ đề/học kỳ cho phép ngay ngày cuối; sửa dữ liệu ngày sau đó không đánh dấu bản chốt lỗi thời. | `ThemeEvaluationServiceImpl:255-262` |
| B11 | TB | Giáo án duyệt muộn: các ngày chưa chấm được vẫn tính Chưa đạt vào tỷ lệ 70% chủ đề. | `ThemeEvaluationServiceImpl:~760-775` |
| B12 | TB | Listener mở lại đánh giá cùng điểm danh (của mình): không kiểm `ASSESSMENT_UNLOCK` và phạm vi, nuốt lỗi nhưng thông báo vẫn nói đã mở, không gửi thông báo riêng, thu hồi không kéo theo. | `AssessmentReopenOnAttendanceUnlockListener`, `AttendanceDayUnlockServiceImpl:140` |
| B13 | Thấp-TB | `presentDays` bỏ qua nửa buổi nên bản nháp ghi "đi học N/M" cao hơn thực; vắng có phép và không phép đều thành Chưa đạt (cần chốt có nên "chưa đủ dữ liệu"). | `EvaluationSources:108-162` |
| B14 | TB | HT duyệt cuối khen thưởng không kiểm `REWARD_REVIEW` trong ma trận quyền động. | `RewardListServiceImpl:334, 415` |
| B15 | TB | Gửi AI ngoài chỉ gỡ tên và ngày sinh của chính trẻ, tên bạn và ghi chú sức khỏe lọt qua. Trái AI-04 (tối thiểu, ẩn danh). | `PeriodCommentFactsBuilder:56-68`, `ChildTextScrubber` |
| B16 | Thấp | Đọc cả bảng mở lại mỗi lần gọi; không có học bù thứ 7. | `EvaluationDeadlineServiceImpl`, `AssessmentDayUnlockServiceImpl` |

## C. Cấu hình trường

| # | Mức | Vấn đề | Vị trí |
|---|-----|--------|--------|
| C1 | Cao | API nhóm tuổi cũ không kèm năm học lách danh mục 6 nhóm chuẩn và sĩ số luật (tạo nhóm 0–72 tháng, sĩ số 60; xóa được nhóm chuẩn chưa dùng). | `AgeGroupServiceImpl:189-320`, `AgeGroupController:64-111` |
| C2 | Cao (cần chốt) | Tốt nghiệp tính theo ≥ 72 tháng tại ngày bắt đầu năm: trẻ sinh 15/12/2020 (68 tháng) học xong lớp 5–6 không cho "ra trường"; trẻ ≥ 72 tháng ở lớp nhỏ không lên lớp được. Quy ước tính theo năm sinh là trí nhớ, chưa đối chiếu. | `YearTransitionServiceImpl:285-290, 614-617` |
| C3 | Cao | Kiểm sĩ số khi xếp lớp, chuyển lớp, tiếp nhận là đếm rồi thêm, không khóa lớp: hai người cùng lúc vượt sĩ số luật. | `PlacementRules:48-67`, `ChildRecordServiceImpl:401`, `ChildIntakeServiceImpl:745` |
| C4 | TB-Cao | Sửa nhóm tuổi tự tạo ở năm đang hoạt động không kiểm trẻ đã xếp; kế hoạch giáo dục ghép theo đúng khoảng tháng. | `AgeGroupServiceImpl:228-252, 382-396, 484-505` |
| C5 | TB | V201 (mình viết) đổi khoảng tháng cả năm đang hoạt động: nhóm 12 tháng chuyển nhóm, dòng chương trình gắn khoảng cũ 13/25 có thể mồ côi; chưa có thông báo cho trẻ dưới 3 tháng. | `V201__nursery_age_groups_month_ranges.sql` |
| C6 | TB | Lịch sử giờ chốt hai cách sắp xếp (id và effectiveFrom): dòng lưu sau nhưng hiệu lực sớm hơn bị che. | `AttendanceCutoffServiceImpl:97-245` |
| C7 | TB | Giờ chốt đầu tiên của năm có thể bắt đầu sau ngày khai giảng; các ngày trước dùng mặc định cấu hình mà màn hình báo đã cài. | `AttendanceCutoffServiceImpl:84-149`, `AttendanceDayRules:91` |
| C8 | TB | Lịch sinh hoạt thiếu: `afternoonStart ≤ latestAfternoonArrival`, `afternoonEnd > pickupEnd`; chưa có CHECK ở DB (FE và BE cùng thiếu). | `DailyScheduleRules:36-60`, `schoolConfigValidation.js:218-229` |
| C9 | TB | Ngừng điểm trường không kiểm quyền ghim theo điểm trường, phân công GV/bếp, trẻ chờ xếp lớp. | `CampusServiceImpl:386-448`, `EffectivePermissionResolver:94-110` |
| C10 | TB | Sửa ở năm đang hoạt động ghi vào bảng dùng chung, ảnh hưởng năm khác (bật tắt điểm trường toàn cục). | `AgeGroupServiceImpl:382-396`, `CampusServiceImpl:409-415` |
| C11 | Thấp-TB | Không kiểm số tuần học tối thiểu (35 tuần, học kỳ 18+17) và ngày nghỉ thêm sau khi năm đã chạy chỉ kiểm có điểm danh. Quy định là trí nhớ. | `SchoolYearServiceImpl:~499-790` |
| C12 | Thấp-TB | Rút ngắn ngày kết thúc năm đang hoạt động chỉ kiểm không nhỏ hơn hôm nay, không kiểm lịch nghỉ học, nhập học đã đặt. | `SchoolYearServiceImpl:~187` |
| C13 | Thấp | Xếp lớp khác nhóm tuổi chỉ gợi ý, không cảnh báo, không ghi dấu (theo rule đã chốt). | `PlacementRules:35-47` |
| C14 | Thấp | Hằng số nhóm tuổi cũ trong FE (`AGE_GROUPS`: 4 nhóm, "24–36 tháng" duy nhất) làm mặc định khi cấu trúc thật không tải được. | `models/School.js:28-35`, `ClassPlacementFields.jsx` |
| C15 | Thấp | Mỗi (người, mã quyền) chỉ một điểm trường ghim, không giao một việc cho hai điểm trường. Có thể cố ý. | `PermissionServiceImpl:524` |

## D. Chưa khớp quy định Bộ (cần đối chiếu văn bản)

- Nhãn nhóm nhà trẻ: Điều 15 Thông tư 52/2020 ghi 13–24 và 25–36 tháng (theo V176); V201 theo yêu cầu dùng 12–24 và 24–36. Nên giữ nhãn khớp văn bản hoặc ghi rõ lý do khi thanh tra.
- Nhóm 3–12 và 12–24 tháng chưa có mục tiêu và nội dung chương trình (nguồn Bộ trong DB chỉ có 24–36 tháng và ba nhóm mẫu giáo).
- Chưa kiểm 35 tuần/năm học và 5 ngày/tuần ở mức dữ liệu (C11).
- Cách tính tuổi tốt nghiệp (C2).
- Gán Chưa đạt tự động cho trẻ vắng và danh hiệu "xuất sắc" có xu hướng xếp hạng; Chương trình GDMN không dùng đánh giá để xếp hạng trẻ (B13, OBS-10/14). Quy định là trí nhớ.
- Ngưỡng 70% chủ đề và 50% cuối năm là cấu hình của trường, không phải quy định Bộ.

## E. Đã đóng trong lượt này

- ATT-07: khóa cứng sau xác nhận; sửa rule, flow doc, test, ẩn nút "Trả lại GV" khi đã xác nhận.
- Đơn nghỉ cả ngày mà trẻ vẫn đến: GV ghi Có mặt ngay, báo phụ huynh.
- Mở lại điểm danh tự mở lại đánh giá cùng lớp–ngày.
- Lỗi CI: kiến trúc (`MealChangeGuard`), 7 test cũ theo ATT-07 và báo nghỉ buổi sáng, snapshot OpenAPI, cổng contract FE, Prettier.
- Nhóm nhà trẻ 3–12, 12–24, 24–36 tháng (V201), kể cả năm đang hoạt động.

## F. Việc cần Cần chốt (26/11/2026)

| # | Vấn đề | Lựa chọn | Đề xuất |
|---|--------|----------|---------|
| Q1 | Phụ huynh xem trẻ đã thôi học (B2) | (a) mất quyền ngay như CHD-04; (b) xem lại dữ liệu cũ chỉ đọc, ẩn sức khỏe như OBS-36 | (a), sửa OBS-36 cho khớp |
| Q2 | Điều kiện ra trường (C2) | (a) ≥ 72 tháng tại ngày bắt đầu năm (hiện tại); (b) học xong lớp nhóm tuổi lớn nhất, HT ghi đè có lý do | (b) |
| Q3 | Ai ghi được ngày cũ khi mở lại (A8, B3) | (a) giữ rule 14/11 (GV chính hiện tại và người dạy thay); (b) thêm GV chính đã dạy lớp ngày đó | (b), báo cả hai người |
| Q4 | Trẻ vắng có phép trong đánh giá (B13) | (a) Chưa đạt như OBS-01; (b) "chưa đủ dữ liệu", không tính vào tỷ lệ | (b) |
| Q5 | Dòng mặc định khi GV lưu đánh giá ngày (B1) | (a) gửi mọi dòng kèm mặc định; (b) chỉ gửi dòng GV đã sửa và kiểm phiên bản | (b) |
| Q6 | Nhận xét tự do gửi phụ huynh (B4, B5) | lọc từ chẩn đoán ở mọi chỗ nhập và chuẩn hóa dấu: có hay không | Có |
| Q7 | Nhắc cân đo hằng tháng cho trẻ dưới 24 tháng (Điều 20.2) | Làm hay không | Làm |
| Q8 | Nhóm/lớp ghép khi dưới 50% sĩ số (Điều 15.2) và tối đa 2 trẻ khuyết tật hòa nhập (Điều 15.3) | Làm cả hai, chỉ nhóm ghép, hoặc để sau | Để sau, ghi vào backlog |
| Q9 | Trẻ đến muộn buổi sáng sau giờ chốt (A11) | (a) giữ ATT-03, không nhận; (b) cho PHT ghi nhận kèm lý do | (a) |
| Q10 | Đơn nghỉ: 30 ngày lịch kể cả nghỉ lễ, không vượt hai năm học (A11) | Giữ hay đổi | Giữ |
| Q11 | Trả sớm giữ suất, nghỉ nửa buổi bỏ suất xế (A7) | Thống nhất theo (a) cả hai giữ suất đã chốt hoặc (b) bỏ xế khi sổ chưa xác nhận | (b) |
| Q12 | Xếp lớp khác nhóm tuổi (C13) | Cảnh báo và ghi dấu hay giữ gợi ý | Cảnh báo và ghi dấu |
