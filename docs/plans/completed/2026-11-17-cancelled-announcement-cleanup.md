---
title: Xóa thông báo đã hủy và nhắc điểm danh theo giờ khóa
status: done
owner: Tiền
jira: G94-288 / BE-FEAT-55 / FE-FEAT-55
branch: feature/G94-288-BE-FEAT-55-notifications-integrated
modules: [notification, observation]
rules: [PAR-07, PAR-13, OBS-30]
risk: high
created: 2026-11-17
updated: 2026-11-17
---
# Xóa thông báo đã hủy và nhắc điểm danh

Yêu cầu và tiếp tục triển khai: Tiền 17/11/2026; bulk theo bộ lọc mọi trang đã được user chốt. Domain/spec/plan/data/contracts/tasks/analyze tại [delta spec 019](../../../specs/019-school-notifications/domain-spec.md). Giữ constitution/stack hiện có. Phạm vi gồm BE, FE, API/mock và nhắc điểm danh, không App/dependency/migration. Test native theo AC; không e2e, không thao tác dữ liệu thật, không commit/push khi chưa yêu cầu mới.

## Progress log
- Đã đọc callers, role/scope, repositories/FK, API/mock/UI và reminder/dedupe trước sửa; xác nhận PAR-07 đã loại sender. Thiết kế PAR-13 mới và cập nhật OBS-30 theo chỉ đạo, không dựa vào code cũ làm chuẩn.

## Kết quả / bàn giao
- Xong filter q/status, canDelete và DELETE một/bulk theo scope+filter mọi trang; trạng thái khác CANCELLED không xóa. Khóa parent, cascade tệp DB, cleanup storage sau commit; rollback bảo toàn file. UI list/detail, xác nhận và mock/API đã nối, giữ filter khi lỗi và về trang 1 sau xóa.
- PAR-07 đã có sẵn: không tự nhận, kiểm lại resolver đa vai trò và mock BGH; giữ sender khi preview/gửi ngay/hẹn/lặp. OBS-30 đổi sang [08:00,cutoff cấu hình), cron mỗi 5 phút 08–23h ngày thứ Hai–Sáu, chặn ngày nghỉ/khóa/đã điểm danh, dedupe theo slot+link lớp.
- BE: 47 test trong 7 file đã có kết quả đạt cuối (service 16, controller 6, resolver 9, schedule 5, reminder 3, PostgreSQL integration 4, OpenAPI 4). Lượt integration đầu lỗi fixture tên campus trùng, sửa fixture unique, rerun đúng file 4/4. Không lỗi/skip ở kết quả cuối; không tuyên bố một lượt full-suite.
- FE: notifications 26/26; sau thêm guard mock role chạy lại đúng mock 6/6, 20 ca độc lập không thay đổi. ESLint/Prettier/Vite build đạt (3.33s, cảnh báo thư viện có sẵn); BE compile/Spotless check đạt, git diff --check đạt. OpenAPI thêm q/status, canDelete, 2 DELETE; API_INDEX cập nhật 372 endpoint. Không migration, không đổi App, không kiểm browser hoặc API môi trường thật; không e2e theo user. Không chạy lại giáo dục/auth/menu độc lập đã đạt trước.
- Không còn gap triển khai theo AC1–6 qua đối chiếu thủ công artifact/code/tests. Không commit/push. Cách test người dùng đã ghi trong school-notifications.md.
