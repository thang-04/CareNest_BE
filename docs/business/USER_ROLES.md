# User Roles

Quyền = **Role → Permission (hành động)** ∩ **Access scope (dữ liệu)**. Role toàn cục nằm ở Keycloak (ADR-0011); scope chỉ có hiệu lực khi role trong token VÀ phân công/liên kết còn hiệu lực trong DB cùng khớp. Scope lấy từ StaffAssignment (school-structure) hoặc GuardianChildLink (child). Rule ID: `BUSINESS_RULES.md` nhóm AUTH.

## Scope hierarchy

```text
SCHOOL                 Principal          StaffAssignment(staff_role=PRINCIPAL)
  └ CAMPUS             Vice Principal     StaffAssignment(staff_role=VICE_PRINCIPAL, campus, hiệu lực từ–đến)
      └ CLASS          Teacher            StaffAssignment(staff_role=TEACHER, classroom)
AGE GROUP              Team Leader        Teacher + StaffAssignment(staff_role=AGE_GROUP_TEAM_LEADER, age_group) — kế hoạch giáo dục của khối
          └ CHILD      Parent             GuardianChildLink(child)
CAMPUS (bếp)           Kitchen Staff      StaffAssignment(staff_role=KITCHEN_STAFF, campus) — chỉ dữ liệu nutrition
SYSTEM                 System Admin       Tài khoản/role/cấu hình — không mặc định xem dữ liệu trẻ
```

## Actor

| Role | Client chính | Trách nhiệm trong CareNest | Không làm |
| --- | --- | --- | --- |
| Principal | Web | Xem tổng hợp 2 campus, duyệt/xác nhận trong quyền, theo dõi sự cố, xem hồ sơ phát triển; xem kế hoạch giáo dục toàn trường (trừ Nháp, Đang sửa), cấu hình kế hoạch, duyệt khi được thêm vào chuỗi người ký (EDU-06, EDU-11, EDU-18) | Quản trị tài khoản kỹ thuật; soạn kế hoạch giáo dục |
| Vice Principal | Web | Như Principal nhưng trong campus được phân công (AUTH-02). Kế hoạch giáo dục áp dụng toàn trường: lập, cùng soạn, ban hành / trả lại / xóa kế hoạch năm; cấu hình kế hoạch; duyệt chủ đề nhánh, hồ sơ chủ đề theo chuỗi (EDU-02..07, EDU-11, EDU-18) | Xem campus khác (trừ khi được phân công) |
| Teacher | Web + App | Điểm danh, báo ăn, quan sát hằng ngày, duyệt summary của lớp, báo sự cố CSVC, nhập đo sức khỏe (người nhập: PENDING); soạn và gửi chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày của lớp; xem kế hoạch năm đã ban hành của nhóm tuổi; đánh giá trẻ theo MT (EDU-06, EDU-12..19) | Xem lớp không được phân công; sửa kế hoạch năm |
| Tổ trưởng khối (Teacher + phân công AGE_GROUP_TEAM_LEADER) | Web | Lập, cùng soạn (mỗi lúc một người), gửi kế hoạch năm học của nhóm tuổi mình; duyệt chủ đề nhánh, hồ sơ chủ đề của các lớp trong nhóm tuổi theo chuỗi (EDU-02..04, EDU-10, EDU-18) | Ban hành, xóa kế hoạch năm; sửa kế hoạch nhóm tuổi khác |
| Parent | App | Xem thông tin được phép của con (PAR-*); gửi báo nghỉ, không cần duyệt (ATT-05) | Chat thay Zalo; xem trẻ khác |
| Kitchen Staff | App (+Web nếu cần) | Xem suất đã chốt, thực đơn đã duyệt, định lượng, dị ứng cần cho nấu | Sửa điểm danh; xem hồ sơ trẻ |
| System Admin | Web | Tài khoản, role, phân công, cấu hình hệ thống, master data kỹ thuật | Ra quyết định nghiệp vụ; mặc định không xem dữ liệu trẻ (AUTH-06) |

## Hành động cần permission riêng (danh sách khởi đầu)

`attendance:record` · `leave:create` · `meal-count:confirm` · `menu:approve` · `food-master:manage` · `health:record` · `health-interpretation:approve` · `observation:record` · `summary:approve` · `facility-issue:report` · `facility-issue:manage` · `report:view` · `admin:accounts` · `goals.view` · `goals.edit` · `goals.approve` · `plans.config` (EDU-08, tạm gán theo role)

Gán permission cho role nào ở các hành động có người thực hiện PENDING (meal-count:confirm, health:record) ⇒ cấu hình, không hard-code.
