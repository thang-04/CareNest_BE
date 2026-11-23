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
