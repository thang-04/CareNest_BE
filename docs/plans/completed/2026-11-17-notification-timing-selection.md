---
title: Cấu hình thời gian thông báo và xóa bằng checkbox
status: done
owner: Tiền
jira: G94-288 / FE-FEAT-55 / BE-FEAT-55
branch: feature/G94-288-BE-FEAT-55-notifications-integrated
modules: [notification, observation]
rules: [PAR-07, PAR-08, PAR-13, OBS-30]
risk: high
created: 2026-11-17
updated: 2026-11-17
---
# Cấu hình thời gian và chọn dòng thông báo

Tiền yêu cầu và chốt chọn tất cả khớp filter trên mọi trang qua câu trả lời 17/11/2026. Domain/spec/plan/data/tasks và AC7–9 tại CareNest_BE/specs/019-school-notifications/domain-spec.md. Không migration/dependency/App. Test native theo ảnh hưởng; không e2e, không sửa DB thật hoặc commit/push.

## Progress log
- Kiểm constructor callers, push retry/outbox, hai worker hẹn/lặp, reminder điểm danh/đánh giá/báo ăn, properties scan/YAML trước sửa. Giữ cấu hình push hiện có. User yêu cầu comment chi tiết nên YAML/class có giải thích theo từng nhóm.
- Quyền, list filter, khóa/xóa/afterCommit cũ tái sử dụng. Chọn rõ ids hoặc allMatching trừ excludedIds; thêm cancelledCount server, UI giữ selection khi đổi trang/reset khi đổi filter. Kiểm request/role trước xóa, không thành công một phần.

## Kết quả
- Hoàn tất AC7–9/T7–10: timing mặc định/cron trong YAML có comment, properties fail-fast, cùng interval cho cron/dedupe; giữ key push và dữ liệu giờ trong DB. UI checkbox riêng/toàn filter mọi trang/trừ bỏ tick, count server, reset khi đổi filter; POST lựa chọn kiểm toàn bộ trước xóa. Không migration/App/dependency.
- BE 64 test thuộc 9 file đạt; sau chỉnh jitter min1ms, chạy lại 18 ca push liên quan đạt, không chạy lại phần độc lập. FE 29/29 tests notifications đạt, lint/format/build đạt (2.33s); BE Spotless và diff check đạt. OpenAPI cập nhật list count + request lựa chọn; API_INDEX 373 endpoint.
- Chưa kiểm browser/e2e hoặc môi trường thật; không tác động DB thật. Các bộ giáo dục/auth/menu độc lập đã đạt trước không chạy lại. Không commit/push.
