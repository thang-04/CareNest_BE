---
title: Hoàn thiện thông báo Web App và retry riêng thiết bị
status: draft
owner: Tiền — review; người triển khai chưa phân công
jira: G94-289/G94-290/G94-291 là nguồn BE; mã việc bổ sung FE/APP chưa cấp
branch: feature/G94-288-BE-FEAT-55-school-notifications
modules: [notification]
rules: [PAR-03, PAR-05, PAR-07, PAR-08, PAR-09, PAR-10, PAR-11, PAR-12, EDU-73, EDU-74]
risk: high
created: 2026-11-15
updated: 2026-11-15
---
# Plan quản trị — notification client readiness (902)

## Context

Plan chính liên repo; thiết kế chi tiết: plan 902 (`specs/902-notification-client-readiness/plan.md`).
Source hiện có:
- PushOutboxWorker.java:76 dùng transaction theo notification, :149 bất kỳ máy thành công đánh SENT, :153 kiểm expiry sau send.
- DeviceToken.java:55 chuyển user trên cùng token ID; FcmPushGateway.java:78 gửi notification+data, :93 success chỉ là FCM accepted.
- FE notificationService.js:6 làm rơi announcementId; accountApi.js:38 tìm detail trong list giới hạn, NotificationBell.jsx:24 chỉ mở link.
Các Java path tương đối src/main/java/com/carenest/service/notification/impl, entity/notification, integration/push; FE path đầy đủ ở plan 902/research R6.

## Quyết định đã chốt

1. PAR-11, Tiền 15/11 Q1=A: retry riêng máy lỗi trong 60m từ tạo tin; không gửi lại máy đã thành công vì máy khác lỗi.
2. PAR-12, Tiền 15/11 Q2=A: crash sau FCM nhận trước lưu kết quả có thể retry; chấp nhận banner trùng hiếm gặp, App chống xử lý trùng phần mình quản lý.
3. Public API hiện có giữ nguyên; không broker/dependency mới; không ghi Jira/deploy trong bước plan.

## Làm rõ nghiệp vụ

[B] BGH chọn nội dung/target/giờ; [S] kiểm quyền, preview, lưu/giao nền; [B] người nhận mở; [S] auth/scope/full text/file.
Ảnh hưởng: mọi producer NotificationService gồm pickup/health/meal/attendance/EDU; không đổi thời điểm công bố/chuỗi duyệt. Ledger mới chỉ tuyến gửi, không ai đã đọc. PHT gửi theo PAR-07 cụ thể, không suy rộng quyền AUTH-02.

### Câu hỏi mở

Không còn câu hỏi nghiệp vụ chặn thiết kế sau Q1/Q2. Các điều kiện **triển khai chưa đạt**: duyệt plan/schema, repo App và review hướng dẫn của nó, môi trường Firebase/Android, quyền Jira, mã việc/nhánh khi cần Git. Không tự đánh approved; dependency không coi là kết quả đã đạt.

| Câu hỏi | Trả lời | Ai/ngày | Nguồn |
| --- | --- | --- | --- |
| Nhiều thiết bị | Retry riêng | Tiền 15/11 | PAR-11 |
| Crash sau FCM nhận | Cho retry, App chống trùng | Tiền 15/11 | PAR-12 |

## Acceptance criteria

- AC-1 (PAR-11): A thành công/B lỗi tạm thời; lần sau chỉ gọi B, kể cả restart.
- AC-2 (PAR-11/12): hết 60m không send; crash trước commit được retry chưa ghi kết quả, App dedup.
- AC-3 (PAR-05/07/08): Web soạn/preview/gửi/đọc full/file đúng quyền, không người đã đọc.
- AC-4 (PAR-09/10): HT quản lý quy tắc/lịch; PHT bị từ chối, holiday/month-end đúng.
- AC-5 (PAR-03/12): Android thật banner nhạy cảm chung, auth rồi mở đúng tin.
- AC-6 (AGENTS.md): kiểm chứng có lệnh/revision/kết quả, Jira đọc thật hoặc chưa xác minh; không e2e khi chưa yêu cầu.

## Key decisions

| Chọn đề xuất | Phương án khác | Lý do |
| --- | --- | --- |
| Child ledger + marker | State cha/JSON/broker | Cần retry riêng có ràng buộc, không thêm hạ tầng |
| Mỗi máy một transaction | Chung mọi máy / claim lease | Commit A trước B; đơn giản cho scale trường |
| Giữ API public | Thêm API device dashboard | Client không cần thay API, ngoài scope |

ADR proposed (`specs/902-notification-client-readiness/adr-per-device-delivery.md`), schema proposed (`specs/902-notification-client-readiness/data-model.md`): 1 bảng + 1 cột, chờ duyệt trước viết SQL.

## Phases

Đề xuất chia thành gói vì >8 file và >3 phase; không một đợt sửa toàn bộ.

### Phase B1 — Ledger backend

- C: entity/repository delivery, migration mới sau duyệt; M: worker, notification, device repo; không thay public DTO.
- Khóa/recheck owner, expiry trước send, từng máy một transaction, backfill/cutover không replay terminal cũ.
- Sửa due query lấy cả expired dù next_at tương lai, đóng mọi child pending khi hết hạn; visited IDs mỗi tick chống spin. Thêm worker-enabled cho binary mới.
- Exit: lệnh BE ở quickstart §2, V08/V11..V14 đạt; API snapshot không đổi. Dừng review phase theo workflow trước bước tiếp.

### Phase W1/W2 — Web

- Plan client ở FE; facade, routes/menu, full notification detail/file/count, mẫu/quy tắc/lịch.
- Exit: test notifications + menu/account liên quan, lint/build; V01..V07 theo phạm vi kiểm được, ghi chưa e2e. Dừng review.

### Phase A1 — Android và môi trường thật

- Chỉ sau cấp repo/thiết bị/config đúng môi trường; không tự chọn stack App hoặc deploy VPS.
- Exit: V08..V12 có bằng chứng thật, gồm PAR-11/12 và token switch. Chưa có điều kiện thì blocked. Dừng review.

### Phase E1 — Bàn giao

- FR-019..025: đóng verify cũ bằng native checks/inventory, đối chiếu Jira thật; không tự ghi Jira.
- Exit: bằng chứng/giới hạn đầy đủ; thiếu điều kiện không nâng Done toàn luồng.

## Test matrix

| AC | Rule | Test / V | Phase | Kết quả hiện tại |
| --- | --- | --- | --- | --- |
| 1/2 | PAR-11/12 | PushOutboxWorker/Integration + delivery/migration test mới; V11..14 | B1 | Chưa chạy |
| 3 | PAR-05/07/08 | node:test adapter/role; V01..04/V06/07 | W1 | Chưa chạy |
| 4 | PAR-09/10 | Rule/schedule BE + client request tests; V05 | W2 | Chưa chạy |
| 5 | PAR-03/12 | Android thật; V08..12 | A1 | Chưa có điều kiện |
| 6 | AGENTS.md | Native checks + inventory/Jira; V13..15 | E1 | Chưa xác minh |

## Rủi ro & rollback

- Dừng mọi API binary cũ khi cutover (worker cũ không có pause flag), không mixed rollout. Rollback giữ binary mới với CARENEST_PUSH_WORKER_ENABLED=false, giữ in-app/schema/ledger; không chạy old worker trên pending đã fan-out.
- Không FK device ledger để token cleanup không mất evidence; owner kiểm lại dưới khóa.
- Secrets mount read-only ngoài git/image; logging SENT không chứng minh push thật.
- HTTP giữ khóa bounded, không transaction qua cả bộ máy; đo latency/retry/load.

## Validation log

- [x] Claim source có file:line; không coi code là rule mới.
- [x] Không đổi public endpoint/DTO; consumer cụ thể ở client-integration.md.
- [x] AC map test/V, exit commands/expected outcomes ở quickstart; không bịa kết quả.
- [x] Đã chia B1/W1/W2/A1/E1 cho phạm vi lớn.
- [x] Rule dùng CONFIRMED và Q1/Q2; không dùng PENDING làm chuẩn.
- [x] Tự review thiết kế, explicit approval/dependency gates, không placeholder schema.

## Progress log

### 2026-11-16 — Duyệt schema

Tiền duyệt thiết kế dữ liệu spec 902 (bảng delivery, cột và ràng buộc/index) trong chat. Kiểm source thấy V132 là migration lớn nhất; dự kiến V133 mới. Chưa chạy migration trên môi trường thực, không chạy e2e.

### 2026-11-15 — Draft plan

Chỉ tạo tài liệu thiết kế; chưa implement, chưa duyệt schema/plan, chưa chạy test/build/e2e, chưa deploy hoặc commit/push. Không dùng setup/verify harness theo chỉ đạo 07/11. Tiếp: tasks/analyze, sau đó review trước implementation.
