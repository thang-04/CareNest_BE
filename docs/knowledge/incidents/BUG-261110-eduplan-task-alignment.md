---
id: BUG-261110-eduplan-task-alignment
type: bug
modules: [education-plan]
rules: [EDU-07, EDU-62, EDU-71, EDU-72]
status: "fixed; runtime verification pending"
date: 2026-11-10
keywords: [lease, snapshot, publication, auto-draft]
---

# BUG-261110 — education plan task alignment

## Triệu chứng và nguyên nhân

FE còn gọi lease sau khi BE bỏ khóa; reorder được thực hiện sau guard nguồn TEAM; snapshot unknown bị tự thay bằng cấu hình hiện tại; năm disabled vẫn đọc theo ID và chiếm unique slot. Mock bỏ cả bước duyệt cuối thiếu người nên có thể tự ban hành.

## Sửa

Bỏ gọi lease và polling FE, giữ revision/recovery. Validate source identity sau reorder cả save/preview. Không tự điền snapshot cũ; kiểm lại khi publication. Repository loại disabled; V94 unique index chỉ giữ bản active. Mock giữ bước cuối và lọc ứng viên thực tế. Config yêu cầu một creator role và reviewer cao hơn.

## Regression và giới hạn

44 test BE chọn lọc và 320 test giáo dục FE đạt; lint/build FE đạt. Xem báo cáo workspace `huong_dan_nghiep_vu/ket_qua_trien_khai_task_ke_hoach_giao_duc_2026-11-10.md`. BE mở rộng còn fixture lỗi; không khẳng định full suite hoặc runtime đã đạt. Chưa áp dụng migration DB thực, chưa E2E. Cấu hình hiện chọn role, chưa chọn người cụ thể; EDU-64 còn mở.


## Delta EDU-72 — tự tạo tổ khi năm ban hành

User thay nghiệp vụ lập tổ thủ công: tự tạo DRAFT và giao tổ trưởng đúng tuổi. Generator trước đây bỏ qua chain rỗng; nay dùng phân công hiệu lực + tài khoản active để chọn đúng một owner, copy nội dung và giữ nguồn năm mới nhất. Dùng activation hiện có để đối soát năm đã ban hành, guard phiên bản tránh rebase ngược. UI bỏ CTA/redirect new; mock cùng vòng đời. Thiếu/trùng tổ trưởng báo lỗi (kể cả activation), không chọn người tùy ý.

Regression ban đầu phát hiện fixture thiếu group và assertion giả định chỉ một domain (API trả cả domain rỗng); sửa fixture/assertion theo cấu trúc thực. Thử ghi author origin AUTO_ASSIGNED bị CHECK DB chặn; đã bỏ hoàn toàn write này, owner hiện có đủ để workflow ghi nhận tác giả; không nới constraint. Kết quả cuối 81 BE/325 FE đạt, lint source sửa/build FE đạt. Chưa restart/runtime/E2E; xem báo cáo workspace và T025–T027 EDU-72.


## Reset dữ liệu test do user yêu cầu

User chỉ định DB carenest_merge, cổng host5433 (proxy vào PostgreSQL5432), rồi xác nhận chỉ reset giáo dục/nhân sự/lớp, giữ module khác. Đã backup trước thay đổi, commit xóa hồ sơ giáo dục, thay phân công và tạo 16 lớp/32 giáo viên/4 tổ trưởng/HT/PHT. Lớp cũ CLOSED giữ lịch sử; 89 bảng nền/cấu hình/module khác có fingerprint nội dung không đổi. Không nới FK/CHECK hoặc sửa migration.

Attempts: Keycloak từ chối firstName chứa số/ký hiệu của tên nhóm tuổi; dùng tên người giả hợp lệ. Giao dịch SQL đầu bị CHECK ck_edu_year_plans_source chặn khi thử xóa source_plan_id của version2; toàn bộ SQL rollback. Sửa bằng DELETE nguyên chuỗi trong một câu lệnh, giữ nguồn/constraint nguyên vẹn. Lượt sau commit thành công. Tổ trưởng là phân công AGE_GROUP_TEAM_LEADER, roles trong accounts/me vẫn là TEACHER theo thiết kế; sửa assertion kiểm tra đúng phân công, không gán role giả.

Kiểm tra 34/34 mật khẩu qua Keycloak direct grant dev và accounts/me; API kế hoạch năm/tổ rỗng. Runtime /api/auth/login trả401; không coi gateway đã kiểm thành công, không đổi auth. Chưa browser/restart. Chi tiết roster và vị trí backup trong báo cáo workspace huong_dan_nghiep_vu/du_lieu_test_ke_hoach_giao_duc_2026-11-10.md.
