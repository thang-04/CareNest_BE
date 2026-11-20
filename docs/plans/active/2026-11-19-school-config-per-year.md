---
title: Cấu hình riêng theo năm học
status: in-progress
owner: Nhữ Đình Cần
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [organization, attendance, meals, observation]
rules: [CFG-YR-01, CFG-YR-02, CFG-YR-03, CFG-YR-05]
risk: high
created: 2026-11-19
updated: 2026-11-19
---
# Cấu hình riêng theo năm học

Status: in-progress
Branch: feature/G94-236-BE-FEAT-29-daily-attendance
Nguồn phê duyệt phạm vi: user 19/11/2026 yêu cầu hoàn thiện cấu hình theo năm, nhóm tuổi, trạng thái điểm trường, phòng, sao chép, kích hoạt, FE, kiểm thử local và PR vào dev. Migration ban đầu là V153, đổi thành V154 khi merge `dev` do `dev` đã có V153.

## Nghiệp vụ và phạm vi

- CFG-YR-01/02: năm đã đóng chỉ đọc; dữ liệu lịch sử giữ bản chụp.
- Mỗi năm còn mở có thông tin nhóm tuổi, phòng và trạng thái điểm trường riêng; sửa năm mới không đổi năm đang học.
- Danh mục chung tiếp tục cung cấp cấu hình năm ACTIVE cho các consumer hiện có.
- Sao chép giữ cấu hình đích đã có; giờ đến muộn nhất đi cùng giờ chốt; ngày nghỉ được dời theo năm và kiểm tra giới hạn năm đích.
- Kích hoạt đóng/chụp năm cũ trước, áp cấu hình năm mới trong cùng transaction.
- Không sửa tài khoản nhân sự, vai trò/quyền hoặc kế hoạch giáo dục.

## Tác động

- BE: organization repositories/services/controllers và migration mới V154; tham số schoolYearId tùy chọn cho thao tác phòng/trạng thái điểm trường, giữ đường gọi cũ.
- FE: adapter cấu hình trường, nhóm tuổi/lớp, phòng, điểm trường và wizard truyền đúng ID năm được chọn.
- APP: đường gọi cũ mặc định năm ACTIVE; không thay endpoint hoặc field đang có.
- Kiểm tra code theo năm ở ClassManagement/YearSetup/snapshots để không dùng thông tin danh mục chung cho năm PLANNED.

## Acceptance và kiểm chứng

1. Sửa/thêm/xóa ở năm PLANNED: năm ACTIVE và năm CLOSED không đổi.
2. Xóa hết danh sách của một năm không làm danh mục chung tự xuất hiện lại.
3. Chọn phòng/lớp và giới hạn sĩ số dùng cấu hình của đúng năm.
4. Đổi trạng thái điểm trường chỉ xét ràng buộc của năm đích.
5. Sao chép giờ chốt mang theo latestAfternoonArrival; ngày nghỉ được kiểm tra và không ghi đè dữ liệu đích.
6. Kích hoạt áp cấu hình mới và giữ bản chụp năm cũ.
7. BE integration PostgreSQL, controller compatibility; FE adapter tests/lint/build; local UI/API với từng ca và kết quả thật.

## Progress

- 19/11: đọc trạng thái repo và rà đường gọi. Phát hiện phần nhóm tuổi V153 đã được bắt đầu; chưa hoàn thiện phòng, trạng thái điểm trường, activation và copy. Có dấu hiệu file BE tiếp tục thay đổi trong lúc kiểm tra, đang xác minh trước khi chạm vùng đó.
- 19/11: hoàn thiện cấu hình nhóm tuổi, điểm trường và phòng theo năm; copy ngày nghỉ/giờ đến muộn; activation áp cấu hình năm mới; FE luôn truyền năm đang chọn.
- 19/11: BE full suite 2050/2050, FE check/build và children tests 192/192 đạt; smoke test DB sạch xác nhận ba năm độc lập và rollback không làm đổi dữ liệu năm khác.
- 19/11: tối ưu/hardening migration cấu hình theo năm (V154 sau khi merge `dev`): unique theo năm ở nhóm tuổi/điểm trường/phòng, ràng buộc phòng thuộc điểm trường cùng năm, FK người phụ trách và các CHECK tương ứng bảng chung; bỏ join/coalesce thừa ở truy vấn mã/tên điểm trường.
