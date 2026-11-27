---
title: BR read scope and parent password follow-up
status: in-progress
owner: project maintainer
jira: G94-180
branch: fix/G94-180-BE-FIX-10-school-year-scope
modules: [child, health, attendance]
rules: [GEN-01, GEN-02, CP-21, CP-22, HLT-01, HLT-12]
risk: high
created: 2026-11-27
updated: 2026-11-28
---
# Đồng bộ code với BR — phần bổ sung 27/11/2026

- Phạm vi: mục 31–35 của `../report/sua_sau/CODE-can-sua-theo-BR-2026-11-26.md`.
- Nhánh BE: `fix/G94-180-BE-FIX-10-school-year-scope`; FE: `fix/G94-180-FE-FIX-14-school-year-scope`.
- Nguồn yêu cầu: người dùng yêu cầu sửa code theo file trên trong chat 27/11/2026; thực hiện trong phạm vi đó.
- Các mục 1–30 đã đánh dấu hoàn thành được giữ và kiểm hồi quy khi liên quan.
- Trạng thái: hoàn tất thay đổi local và kiểm chứng mục 31–35.

## Hành vi và tác động

1. CP-22/GEN-01: PHT còn phân công đọc danh sách, hồ sơ, ảnh và tài khoản PH ở mọi điểm trường. Quyền ghi tiếp tục theo điểm trường được giao, tách khỏi quyền đọc.
2. HLT-01/HLT-12: PHT đọc đợt khám/kỳ cân đo và báo cáo tháng toàn trường, lọc được từng điểm trường. Không nới quyền tạo/sửa/hủy/đóng/mở lại.
3. CP-21: cấp lại mật khẩu thông qua SMS tới username; response chỉ có userId, username, smsStatus. FE phân biệt gửi thành công và thất bại.

Consumer: Web children/ParentActivationPage, ChildrenListPage, HealthCheckListPage, GrowthReportPage; service guardianApi. Tra thêm consumer của ParentPasswordResponse/temporary-password trước khi đổi DTO. Không đổi endpoint, dependency hoặc migration.

## Các bước

- Tách policy đọc/ghi, mở các đường đọc và giữ cờ quản lý theo phạm vi ghi.
- Cập nhật bộ lọc/ẩn nút FE; sửa gửi SMS và contract kết quả.
- Test hồi quy đọc chéo điểm trường được phép, ghi chéo bị chặn, PHT hết phân công bị chặn; gửi SMS thành công/thất bại, không lộ mật khẩu.
- Kiểm tra native Maven, Node tests, build FE; ghi kết quả và giới hạn thực tế.

## Tiến độ

- 27/11: hai worktree đúng nhánh và sạch trước thay đổi. File plan 26/11 đang mở trong IDE không còn trên đĩa; lập bản theo dõi cho đúng phần bổ sung hiện tại.
- 27/11: plan 26/11 được tìm thấy trong `docs/plans/completed/`; giữ các mục 1–30 đã hoàn thành. Đã sửa mục 31–35, tách scope đọc/ghi và cập nhật contract SMS cùng consumer FE.
- 27/11: review độc lập phát hiện hai bảng tổng quan có thể mở quyền đọc tài khoản cho GV được cấp thêm permission. Đã thêm guard vai trò/phân công và test; lượt review lại không còn blocker.
- 27/11: FE đạt 588 test hồ sơ/sức khỏe + 127 test auth, lint/build. BE lượt tích hợp đầu chạy 206 test, 203 pass; ba assertion cũ còn yêu cầu PHT không xem được điểm trường khác. Cập nhật regression để kiểm được xem nhưng không được ghi; giữ test GV ngoài phạm vi bị chặn.
- 27/11: regression đợt/phiếu khám cập nhật đã đạt; tổng hợp các báo cáo native hiện tại là 220 test, không lỗi/skip, gồm 4 OpenAPI và 14 kiến trúc. Đã cập nhật trạng thái mục 31–35 trong file yêu cầu và bằng chứng ở `docs/quality/VERIFICATION.md`. Thay đổi giữ local để review.
