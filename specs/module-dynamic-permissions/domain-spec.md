# Domain spec — Phân quyền động cho cấu hình trường, điểm danh và đánh giá trẻ

Nguồn quy trình: [.ai/workflows/feature-spec-loop.md](../../.ai/workflows/feature-spec-loop.md).
Nguồn nghiệp vụ: AUTH-07, AUTH-10, AUTH-11, AUTH-13, AUTH-20; user duyệt triển khai ngày 09/11/2026.

## 1. Domain Overview

- Business Context: nhiều endpoint của ba module vẫn kiểm tra role cứng, nên HT không thể cấp/thu hồi đúng từng việc.
- Business Goals: mọi thao tác có thể giao cho PHT/GV/tổ trưởng/bếp phải qua mã quyền động; scope campus/lớp vẫn kiểm độc lập ở BE.
- Target Users: HT cấu hình; PHT, GV, tổ trưởng và bếp thực hiện trong phạm vi được phân công.

## 2. Scope

- In Scope: phòng ban; xem/ghi/mở lại điểm danh; sức khỏe trong ngày; trả trẻ; xác nhận/điều chỉnh suất; xem/ghi/xác nhận/mở lại/xét duyệt đánh giá; xét khen thưởng cấp PHT; menu/route/nút FE và guard BE.
- Out of Scope: sửa màn/logic Vai trò & quyền, tài khoản nhân sự, phân công PHT; phân quyền kế hoạch giáo dục; APP; thay đổi phạm vi campus/lớp; quyền quản lý phân quyền; duyệt khen thưởng cuối cùng; cấu hình năm học, học kỳ, ngày nghỉ, giờ chốt, điểm trường, nhóm tuổi/lớp.

## 3. Actors & Roles

- HT: quyền nghiệp vụ cố định toàn trường và là người duy nhất quản lý ma trận/ngoại lệ.
- PHT: chỉ làm việc được cấp, trong campus/phạm vi quyền còn hiệu lực.
- GV/tổ trưởng: chỉ làm việc được cấp, trên lớp đang được phân công; tổ trưởng không tự có quyền trên toàn nhóm tuổi nếu policy không cho.
- Bếp: chỉ quyền bán trú/bếp được cấp; không xem dữ liệu trẻ ngoài DTO nghiệp vụ tối thiểu.

## 4. Domain Concepts

- Permission code: một việc có thể cấp/thu hồi.
- Effective permission: quyền cố định hoặc quyền từ ma trận cộng GRANT trừ REVOKE.
- Assignment scope: campus/lớp/trẻ mà actor được thao tác; không bị permission mở rộng.
- Capability flag: cờ BE trả để FE hiển thị thao tác theo cả quyền và scope.

## 5. Domain Relationships

- Role có nhiều permission qua role_permissions; tài khoản có ngoại lệ theo permission.
- Permission không sở hữu scope; scope tiếp tục thuộc phân công và Campus/Classroom policy.
- Mỗi API ghi cần đồng thời effective permission và scope hợp lệ.

## 6. User Requirements

- HT bật/tắt từng việc theo vai trò và ngoại lệ từng người.
- Người bị thu hồi không thấy thao tác và gọi API trực tiếp nhận 403.
- Người được cấp chỉ thao tác campus/lớp được phân công.
- Quyền có hiệu lực ở request kế tiếp, không cần đăng nhập lại.

## 7. Business Rules

- AUTH-07: BE thực thi quyền; ẩn UI không thay authorization.
- AUTH-10: HT cấu hình ma trận và ngoại lệ, hiệu lực request kế tiếp.
- AUTH-11: PERMISSION_MANAGE cố định cho HT.
- AUTH-13: permission không vượt scope.
- AUTH-20: việc được giao theo mã quyền; đọc và ghi vẫn theo policy module.
- SCH-05: cấu hình thời gian chỉ HT.
- ATT-08, OBS-19, OBS-20: mở lại vẫn giới hạn campus.
- NUT-02: xác nhận suất theo quyền và campus; điều chỉnh suất thay thế dùng cùng quyền.

## 8. Business Processes

- [B] HT chọn quyền → [S] lưu ma trận/ngoại lệ và lịch sử → [S] request sau tính quyền mới.
- [B] actor mở màn → [S] FE đọc permissions/capability → [S] BE kiểm permission → [S] BE kiểm scope → [S] thực hiện hoặc trả 403.
- Ngoại lệ: có quyền nhưng ngoài scope vẫn 403; đúng scope nhưng thiếu quyền vẫn 403.

## 9. State / Lifecycle

- Quyền role: có/không trong role_permissions.
- Ngoại lệ: GRANT/REVOKE; thay đổi ghi permission_change_log.
- Không thêm lifecycle nghiệp vụ mới cho điểm danh hay đánh giá.

## 10. Business Constraints

- HT/ADMIN/PARENT là quyền cố định theo AUTH-11; ma trận chỉ sửa role cấu hình được.
- Dữ liệu cũ được backfill thành grant role tương đương hành vi trước nâng cấp, sau đó HT có thể thu hồi.
- Cấu hình trường quan trọng và phê duyệt khen thưởng cuối cùng vẫn role cố định.

## 11. Non-Functional Requirements

- Security: mọi endpoint ghi thuộc scope phải có guard và policy; 403 không làm lộ dữ liệu ngoài scope.
- Performance: dùng effective permissions đã nạp một lần/request.
- Usability: FE không hiển thị menu/nút không dùng được, nhưng vẫn xử lý 403 từ BE.

## 12. Acceptance Criteria

- AC-01: Given HT thu hồi ATTENDANCE_UNLOCK của PHT, When PHT gọi mở lại, Then 403 dù đúng campus.
- AC-02: Given HT cấp ATTENDANCE_UNLOCK, When PHT mở lớp campus mình/khác campus, Then lần lượt thành công/403.
- AC-03: Given GV bị thu hồi ATTENDANCE_RECORD hoặc ASSESSMENT_RECORD, When lưu, Then 403 và FE ẩn nút.
- AC-04: Given PHT có MEAL_COUNT_CONFIRM, When xác nhận hoặc điều chỉnh suất thay thế trong scope, Then được phép; ngoài scope bị chặn.
- AC-05: Given PHT bị thu hồi ASSESSMENT_UNLOCK/ASSESSMENT_CLASS_REVIEW/REWARD_REVIEW, Then các thao tác tương ứng bị chặn độc lập.
- AC-06: Given HT đăng nhập, Then các quyền nghiệp vụ cố định vẫn hoạt động toàn trường và PERMISSION_MANAGE không giao được.
- AC-07: Given nâng cấp dữ liệu cũ, Then người dùng giữ hành vi cũ qua role_permissions, không qua hasRole cứng.

## 13. Domain Terminology

- Quyền động: mã Permission được HT quản lý qua ma trận/ngoại lệ.
- Scope: phạm vi campus/lớp/trẻ từ phân công, không phải quyền.
- Capability: kết quả cuối cùng của permission + scope + trạng thái nghiệp vụ.

## 14. Assumptions & Open Questions

- User xác nhận ngày 09/11/2026: HT cấp quyền gì thì actor mới được làm việc đó.
- Migration giữ hành vi hiện có dưới dạng grant role để tránh gián đoạn; HT có thể thu hồi sau nâng cấp.
- Không còn câu hỏi nghiệp vụ chặn triển khai.
