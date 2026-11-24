---
id: BUG-261123-assessment-meals-and-ui-drafts
type: bug
modules: [organization, attendance, observation]
rules: [ATT-02a, AUTH-20, OBS-18]
status: fixed
date: 2026-11-23
keywords: [không ăn, campus, bỏ bản nhập, mở lại đánh giá]
---

# Không ăn và bản nhập mở lại chưa đồng bộ

## Root cause

- Phiếu ngày không truyền bữa trẻ không tham gia. FE luôn gợi ý Ăn hết; MealsCell đã có khóa theo skippedMeals nhưng thiếu dữ liệu nối vào.
- Nhóm tuổi & lớp lấy lớp từ server theo quyền đọc cấu hình, nhưng PHT chỉ có lựa chọn campus làm việc và nhãn Điểm trường của tôi.
- Đổi năm ở form mở lại chỉ reset lớp/ngày/hạn; giữ reason. Hook bỏ bản nhập chỉ điều hướng nên cùng màn vẫn giữ reason.

## Fix

- DailySheetResponse.Row thêm skippedMeals; DailyAssessmentService đọc lựa chọn từ attendance và chuẩn hóa lưu/đọc Không ăn cho bữa đã tắt. Request cũ cũng được chuẩn hóa trong khóa lớp dùng chung với điểm danh.
- FE draft tôn trọng skippedMeals; ô ăn khóa sẵn có được kích hoạt. Không backfill DB lịch sử.
- Bộ lọc dùng danh mục server; nhãn Tất cả điểm trường và chip campus cho danh sách nhiều campus. Không đổi policy server.
- Xóa reason khi đổi năm hoặc chọn Không lưu; chọn Hủy giữ bản nhập.

## Kiểm chứng

- UI clone DB local: teacher2 mở ngày 20/11, trẻ đã tắt ăn có hai bữa Không ăn bị khóa; lưu và reload giữ đúng. Trẻ tham gia khác vẫn Ăn hết.
- PHT: tất cả 11 lớp, lọc Cơ sở1 còn6, Cơ sở2 còn5; số liệu lấy từ server.
- PHT: nhập reason → đổi năm → Hủy giữ reason; đổi lại → Không lưu xóa reason; chọn năm hiện tại lần nữa không xuất hiện dialog và reason trống.
- 1602/1602 Node tests đạt, lint/build FE đạt. Maven61/61 gồm22 daily,13 leave,8 curriculum,4 API,14 architecture đạt; snapshot API chạy lại4/4 đạt sau đặt tên riêng DailyAssessmentRow để tránh schema Row của module khác.

## Attempts

1. Lượt BE đầu có hai assertion cũ chỉ xét LUNCH, bỏ qua AFTERNOON được cấu hình nhưng không chọn ăn. Cập nhật test để chứng minh hai bữa đúng với dữ liệu fixture, giữ kiểm bữa ngoài cấu hình bị loại.
2. Fixture chương trình nguồn 24–36 dùng code quá20 ký tự; rút code test còn12 ký tự, không đổi source giáo án.
