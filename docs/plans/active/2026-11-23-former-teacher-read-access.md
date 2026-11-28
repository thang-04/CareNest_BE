---
title: Đọc lịch sử lớp đúng khoảng phân công
status: in-progress
owner: Cần
jira: G94-236
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [organization, attendance, observation]
rules: [AUTH-03, AUTH-20, OBS-18]
risk: high
created: 2026-11-23
updated: 2026-11-23
---

# Lịch sử lớp theo phân công

User yêu cầu sửa các phát hiện trong báo cáo23/11. Không sửa role editor, policy chung, tài khoản hoặc nghiệp vụ giáo án. Không thay quyền ghi đã chốt.

## Acceptance

1. GV hết phân công vẫn thấy lớp từng dạy của năm đã chọn; mở đúng ngày trong đợt được, ngoài đợt bị từ chối. Áp dụng cả ACTIVE/CLOSED.
2. Danh sách lớp phân biệt lớp lịch sử chỉ đọc với lớp đang dạy để dashboard không hiểu là lớp làm việc hiện tại. Ngày mặc định của lớp lịch sử nằm trong đợt được xem.
3. Tổng hợp/kỳ không được trả dữ liệu ngoài khoảng có quyền. Khoảng đọc phải được phủ đủ bởi phân công (gộp các đợt liền nhau); khoảng vượt quyền bị từ chối, không âm thầm trả tổng thiếu ngày.
4. GV hiện tại/BGH giữ phạm vi hiện hành; lịch sử không cấp quyền ghi. Tổng quan lớp cho GV thường vẫn chặn theo AUTH-23.
5. Hai view đánh giá ngày hiển thị trạng thái ngày không học thay lỗi tải. Không đổi dashboard của người khác.

## Thực hiện

TeacherReadAccessService trong tổ chức đọc findAssignments hiện có; chỉ tính khoảng và ngày, không sửa phân công. Guard attendance/observation tách quyền đọc cấp lớp, ngày và khoảng. Các reader theo kỳ phải kiểm khoảng trước khi tải số liệu. FE chỉ dùng cờ/ngày BE cung cấp.

AttendanceClassResponse bổ sung historicalReadOnly, lastReadableDate nullable; consumer FE hook điểm danh/đánh giá, bộ lọc lớp. APP bỏ qua trường bổ sung, không sửa APP. Endpoint/request không đổi, không migration.

## Kiểm chứng

Regression: hết phân công trong năm, đọc trước/trong/sau đợt, năm đóng, hai đợt có khoảng hở, summary vượt khoảng bị chặn, ghi lịch sử vẫn bị chặn. Node và UI hai view ngày nghỉ; snapshot API; giữ test scope PHT/HT. Review độc lập trước báo hoàn tất.

## Progress

- 23/11: user nói sửa đi sau báo cáo; audit guard đã có4 kết quả sai, bắt đầu sửa trong phạm vi được giao.

- 23/11 bổ sung theo ảnh/user: mọi màn lịch sử thuộc attendance/assessment dùng `historyStatus` từ enrollment, tên hiển thị `Tên trẻ (Đã chuyển lớp)` hoặc `(Đã nghỉ học)`. Không suy trạng thái từ boolean historical. Giữ dữ liệu/snapshot đã chốt, chỉ bổ sung nhãn hiện tại trong năm.
- Trẻ rời lớp: khóa ô cuối chủ đề; API chặn thay đổi ô, chặn đánh giá ngày/phiếu cuối năm/phát phiếu từ lớp cũ. Payload lưu cả lớp loại dòng chỉ xem. Bảng học kỳ chưa chốt giữ roster tại ngày tổng hợp; snapshot đã chốt giữ roster và nhận nhãn động.
- Review độc lập phát hiện cờ lịch sử đang suy theo permission ghi điểm danh; đã tách phân công thực tế khỏi ATTENDANCE_RECORD để GV chỉ có quyền đánh giá không bị gắn nhãn lịch sử nhầm.
- Native tests: lần đầu có lỗi fixture nghỉ học thiếu lý do, fixture GV năm cũ lệch ngày và mock chưa cập nhật phụ thuộc; ghi lại trước sửa. Test lịch sử cũ kỳ vọng teaches=true được đổi sang false theo metadata mới, dữ liệu vẫn phải đọc được trong khoảng phân công. Test reset persistence context sau SQL để không dùng entity phân công cached trước cập nhật.

- User điều chỉnh 23/11: chuyển lớp không khóa toàn bộ ô cuối chủ đề. Cô lớp cũ vẫn sửa/chốt các MT đã có hoạt động trong thời gian trẻ học lớp đó, đúng quyền/hạn và bảng chưa xác nhận. Cell.canEdit từ BE; API dùng cùng guard. MT chưa học ở lớp cũ không được thêm kết quả. Trẻ nghỉ học giữ chế độ chỉ xem như yêu cầu trước; ngày/phiếu cuối năm không mở quyền chỉ vì có lịch sử.

- Bổ sung API riêng GET /assessment/daily/classes trong module observation: danh sách lớp đánh giá dùng ASSESSMENT_VIEW/UNLOCK và đúng scope, không lệ thuộc quyền ATTENDANCE_VIEW. DTO AttendanceClassResponse tái sử dụng, các cờ canRecord/canUnlock trả theo quyền đánh giá. FE assessment đổi endpoint; attendance giữ nguyên; APP không đổi. Lý do: HT cấp quyền đánh giá độc lập thì cô vẫn mở được lớp mà không cần quyền điểm danh.
- Hồ sơ phát triển: xác minh quyền với trẻ hiện ở lớp mình trước khi lọc thời gian, hỗ trợ tháng trước ngày chuyển. Nhánh cô lớp cũ kiểm khoảng phân công và cắt đúng thời gian trẻ ở lớp; danh sách GV hết phân công chỉ lộ trẻ có giao thời gian phụ trách. Bổ sung nhãn ở hồ sơ, bản in/Excel và sức khỏe ngày.
- User yêu cầu merge dev mới nhất và xác nhận đổi số migration tuần171→174 khi dev thêm171 nhận xét AI. Merge giữ historyStatus+aiEnabled, không bỏ guard lịch sử/quyền. Kết quả kiểm chứng: docs/quality/2026-11-23-latest-dev-merge-verification.md.

## Bổ sung 23/11: lịch sử ngay trên màn ngày/tuần — đã triển khai

- Nguồn yêu cầu: cô lớp mới chọn ngày/tuần trước chuyển đến vẫn phải xem được kết quả của chính trẻ chuyển đến, ngay trên hai màn hiện có.
- Hiện trạng: DailyAssessmentServiceImpl#getDailySheet lấy roster theo ngày ở lớp đang chọn; WeeklyCommentServiceImpl#getBoard lấy nhận xét theo lớp/tuần. DevelopmentResponse chỉ có tổng hợp tuần, chưa có nội dung nhận xét tuần đã gửi cho cô lớp nhận trẻ.
- AC1: lớp B hiện phụ trách trẻ X; X đã học lớp A trong ngày D trước chuyển. GV lớp B có ASSESSMENT_VIEW mở ngày D: thấy riêng kết quả đã lưu của X tại A, lớp/cô ghi nhận; không thấy trẻ khác của A, không sửa/xác nhận kết quả cũ.
- AC2: chọn tuần cũ: thấy nhận xét SENT tại A của X, ngày gửi và người gửi; không lộ DRAFT. Tuần chuyển giữa tuần hiện đồng thời lịch sử A và phần hiện hành B, không ghi đè hay gộp nội dung.
- AC3: quyền bị thu hồi, trẻ không thuộc lớp B hiện tại, dữ liệu khác năm, ngoài khoảng enrollment nguồn: bị chặn hoặc không trả về; không mở quyền ghi/đọc toàn lớp A, không tạo bản nháp/đánh giá khi đọc lịch sử.
- AC4: chưa có bản ghi thì hiện chưa có dữ liệu, không tự suy thành bình thường/đạt/chưa đạt. Mục tiêu và hoạt động hiển thị theo giáo án nguồn, không ánh xạ sang giáo án B.
- Phương án: thêm GET /classes/{classId}/transferred-assessment-history?from=YYYY-MM-DD&to=YYYY-MM-DD, bảo vệ ASSESSMENT_VIEW và scope lớp hiện tại. DTO chỉ đọc: từng trẻ, lớp nguồn, ngày/tuần, người ghi/gửi, daily timeline và nhận xét tuần SENT. Tái sử dụng EnrollmentService và EvaluationSources để giới hạn ngày học nguồn; lấy nhận xét tuần theo batch. Không migration, không sửa account/education/enrollment business.
- Consumer FE: DailyAssessmentPage, DailyGoalAssessmentPage, WeeklyCommentsPage thêm khu vực lịch sử chỉ xem theo ngày/tuần đang chọn; không đưa vào payload Lưu/Viết nháp/Gửi. APP chưa có caller nên không đổi contract đang sử dụng; API mới có thể dùng lại khi làm app.
- Kiểm: test ngày trước chuyển, tuần trước/chuyển giữa tuần, nhiều lần chuyển, khác năm/campus, thu hồi VIEW, DRAFT/SENT, không phát sinh write; FE kiểm hiển thị và loại lịch sử khỏi thao tác ghi; OpenAPI snapshot, architecture, native lint/build/test và review độc lập.
- Đã triển khai: `GET /classes/{classId}/transferred-assessment-history` trả đúng trẻ hiện thuộc lớp nhận, chỉ dữ liệu trong các đợt ở lớp nguồn và chỉ đọc. DailyAssessmentIntegrationTest kiểm cả điểm danh, sức khỏe, mục tiêu/chủ đề đã xác nhận, nhận xét tuần SENT; nháp chủ đề không lộ và dữ liệu trẻ khác không lộ. Consumer FE đã hiển thị khu vực chỉ xem theo ngày/tuần đang chọn.
