# Research — spec015, refinement 09/11/2026

Nguồn: spec/domain, PROJECT_CONTEXT, BUSINESS_RULES và [bộ xác nhận](../../docs/business/education-plan-confirmation-2026-11-09.md). Các thiết kế bổ sung dưới đây là đề xuất sửa artifact theo lệnh “fix”, chưa phê duyệt triển khai. Quyết định nghiệp vụ đã chốt không suy thành đã migrate/delivery.

## R-1 — Một bộ bảng con, hai cột sở hữu

Giữ D1, giữ ID. **9 bảng trực tiếp** trong data-model §2; goal_contents/ticks suy qua cha, EduGoalRow là superclass, approvals JSONB. Không nhân đôi bảng con hoặc chỉ dùng cờ loại chung header. CHECK đúng một owner sau cutover (authors chọn đúng một trong hai header/class_doc_id theo V87); giữ FK ghép V35/V91 thay vì chỉ FK header; nullable tạm chỉ trong expand.

## R-2 — Tự cập nhật tổ đã tồn tại, không tự lập lần đầu

EDU-62/FR-008/013: năm mới ban hành ⇒ nếu có bản tổ DRAFT/REVIEW thì dùng bản đó, giữ phần nhập/tiến độ hợp lệ, REVIEW về DRAFT. Chỉ khi chưa có bản mở mà có APPROVED mới copy thành n+1 nháp. Không có chuỗi tổ thì không tạo. Luôn giữ tổ ban hành/class docs tới khi cập nhật duyệt cuối. Không hai bản mở khi có retry/concurrent hoặc năm v3 tới trước khi tổ v2 hoàn tất.

## R-3 — Loại/tên và ID

Loại lấy từ bảng vật lý, không từ title/code. DTO dùng planType/planTypeLabel, tên riêng độc lập. Đề xuất sequence chung edu_plan_id_seq cho cả hai header để numeric routes hiện có không nhập nhằng và giữ ID lịch sử. Hai sequence độc lập bị loại vì có thể trùng ID sau cutover; typed routes không cần thiết nếu tất cả writer tuân sequence chung. T040 kiểm default/JPA/create/copy/concurrency và abort khi inventory có ID trùng.

## R-4 — Expand, rehearsal, cutover

V96 local đã có nhưng chưa chứng nhận applied; inventory migration trước triển khai. Expand giữ cột cũ và app cũ; code/schema đích chỉ chạy scratch đến lúc cutover. T029/T030/T031 kiểm bảo toàn trước T009 production. T009 dừng writer/job, refresh backfill, copy nguyên TEAM, đổi FK/sequence, kiểm trước xóa, rồi contract trong transaction. Sau commit không hứa rollback app cũ: cần restore backup hoặc migration tiến được duyệt. Không sửa migration đã commit/applied; slot V96/V97 kiểm lại.

## R-5 — Rà soát và baseline

Chỉ dùng bảng edu_team_review_items (không JSONB review song song). previous_source_plan_id trên tổ lưu nguồn ngay trước lần chuyển; source_plan_id trên item là nguồn năm đích. So nguồn trước/new theo lineage hiệu dụng, đánh phần đổi/xóa/liên kết ảnh hưởng; mục mới nhãn riêng, chủ đề kết thúc không đỏ. Giữ/remap mục chưa xử lý còn lỗi qua nhiều lần chuyển, không đánh mất tiến độ khi reload. Resolve phải kiểm server mapping hợp lệ, nguồn/revision/lease/scope; submit kiểm lại, không tin resolved flag. Contract phân biệt reviewItemId và itemId.

## R-6 — Cùng transaction, không AFTER_COMMIT cho cập nhật bắt buộc

Bản cũ đề xuất event AFTER_COMMIT. ADR-0004 chỉ ACCEPTED phần gọi service, event còn PROPOSED và có rủi ro mất sau commit. Năm/tổ cùng eduplan nên đề xuất generator đồng bộ trong transaction duyệt cuối, có khóa chuỗi và unique một bản mở: lỗi generator rollback toàn lần ban hành, replay không nhân bản. Không thêm outbox, queue/job hoặc listener transaction riêng. Đây là sửa thiết kế chờ duyệt, không đổi ADR hay tuyên bố user đã chọn chi tiết transaction.

## R-7 — API, bảo vệ nguồn và consumer

Giữ prefix/ResponseJson/ApiCode; sentBy.at hiện có giữ tương thích, thêm sentAt nếu consumer mới cần (cùng giá trị sent_at), không đổi ngày chưa gửi thành ngày tạo. Không ai DELETE TEAM, TT issue/return theo policy thay hard-coded role, TEAM không sửa cây qua PUT/import. Versions/changes TEAM trả conflict thống nhất; UI không render nhưng giữ lịch sử nội bộ. FE phải có task implement/E2E riêng; đánh giá tác động không đủ hoàn thành UI. APP kiểm consumer và quyền trước cutover, không tự sửa.

## R-8 — Phạm vi nghiệp vụ và hiệu năng

Chỉ SCHOOL_YEAR và TEAM, không loại thứ ba/môn học. EDU-14/17/56 phủ tuần và giáo án là context cần bảo toàn qua regression nguồn lớp, không thêm tính năng lịch vào015. Bỏ mục tiêu kỹ thuật “không tăng query” chưa có baseline; tuân tránh N+1 theo convention hiện có, không phát minh SLA/benchmark.

## R-9 — Remediation lần analyze tiếp theo: consumer, legacy và phiên cũ

G1: T048 chuyển đầy đủ các caller hồ sơ lớp còn dùng EduYearPlanRepository sang TEAM, không chỉ FK/entity. Giữ docKey/API lớp, đọc bản tổ lịch sử, chỉ superseded khi tổ mới duyệt; regression nguồn giáo án/đánh giá.

U1: T049 xác định tổ liên quan bằng FK nguồn năm, không năm/tuổi đơn thuần; legacy NULL không có baseline để diff nên giữ nguyên, không đoán nguồn hoặc tạo review giả (FR-011). Không thêm luồng liên kết thủ công ngoài phạm vi.

U2: T050 tăng revision và vô hiệu lease/session khi thực sự đổi nguồn, reject form cũ trên mọi đường ghi/lease, giữ input FE. Replay không invalidate thêm, transaction lỗi rollback cả lease/revision. Đây là thiết kế chống ghi đè theo cơ chế lease/revision hiện có, không cấp thêm quyền hoặc thay workflow.
