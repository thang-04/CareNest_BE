---
title: Sửa các lỗi liên thông phát hiện khi kiểm thử UI
status: done
owner: Cần
jira: G94-236
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [organization, attendance, observation]
rules: [ATT-02a, OBS-13, OBS-18, AUTH-20]
risk: medium
created: 2026-11-23
updated: 2026-11-23
---

# Sửa liên thông suất ăn và lỗi bản nhập UI

## Quyết định và phạm vi

User yêu cầu sửa ba lỗi đã báo và test tiếp trực tiếp trên UI (23/11). Không sửa phân quyền/tài khoản hoặc nghiệp vụ kế hoạch giáo dục. Không làm lớp ghép hay tối ưu DB.

- Theo ATT-02a, không tham gia ăn khi điểm danh phải được phản ánh trong đánh giá ngày; không gợi ý Ăn hết và không cho ghi đè bằng request cũ.
- Bộ lọc cấu hình phải mô tả danh sách BE trả về; không tự thu hẹp quyền đọc cấu hình toàn trường của PHT theo vai trò.
- Bỏ bản nhập ở mở lại đánh giá hoặc đổi năm phải xóa lý do cũ; giữ lại khi chọn Ở lại.

## Acceptance criteria

- AC-1: Phiếu ngày trả danh sách bữa không tham gia theo từng trẻ; FE gợi ý Không ăn và khóa các bữa này. BE chuẩn hóa request thành Không ăn cho bữa đã tắt; các bữa tham gia vẫn ghi nhận bình thường.
- AC-2: PHT xem cấu hình có danh sách điểm trường hợp với dữ liệu server, nhãn Tất cả điểm trường không bị hiểu thành điểm trường làm việc.
- AC-3: Bỏ bản nhập rồi đổi năm không giữ lý do cũ và không hỏi bỏ lần nữa khi chưa sửa gì.

## Contract và consumer

DailySheetResponse.Row thêm danh sách skippedMeals (không đổi request/endpoint). FE: assessmentApi.toDailySheet, dailyGeneralDraft, DailyAssessmentCells và tổng quan ngày. APP có thể bỏ qua trường bổ sung; không sửa APP. Không migration.

## Thực hiện và kiểm chứng

Một đợt sửa thuộc ba module của user; regression PostgreSQL cho AC-1, Node test cho draft và các test sẵn có; lint/build FE; kiểm UI trên DB local bản sao, gồm lưu và tải lại. Review độc lập theo workflow L. Ghi rõ các ca chưa chạy và lỗi ngoài phạm vi.

## Rủi ro

Request ở tab cũ có thể mang mức ăn cũ: server phải chuẩn hóa theo điểm danh hiện tại. Không tự sửa dữ liệu lịch sử trong DB. Hủy thay đổi: hoàn nguyên đúng các file của đợt sửa, không đụng migration/dev.

## Progress

- 23/11: user đồng ý sửa và yêu cầu test tiếp UI. Đang triển khai, chưa công bố hoàn tất.
- 23/11: ba ca UI kiểm lại đạt trên DB local bản sao; FE toàn bộ1602 test đạt. User gửi thêm lỗi và giới hạn chỉ phần được giao; BUG-015 thuộc đơn nghỉ được sửa kiểm trùng theo ngày áp dụng, đang chạy regression. Các lỗi token, tiếp nhận và menu chung không sửa.
- 23/11: regression61/61 và API snapshot4/4 đạt, Spotless đạt; review độc lập không có phát hiện cần sửa. Ba lỗi UI của plan đã kiểm lại đạt; BUG-01513/13 test đạt. Các báo cáo ảnh mới còn cần rà riêng đã liệt kê trong báo cáo, không đánh dấu full hệ thống đạt.
