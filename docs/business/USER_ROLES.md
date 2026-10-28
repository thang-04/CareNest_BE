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
SYSTEM                 System Admin       Kỹ thuật (tài khoản PRINCIPAL, khóa/mở, cấu hình) — không mặc định xem dữ liệu trẻ
```

## Actor

| Role | Client chính | Trách nhiệm trong CareNest | Không làm |
| --- | --- | --- | --- |
| Principal | Web | Xem tổng hợp 2 campus, duyệt/xác nhận trong quyền, theo dõi sự cố, xem hồ sơ phát triển; cấp tài khoản và phân quyền nghiệp vụ cho PHT/GV/bếp/phụ huynh (AUTH-06, AUTH-10); xem kế hoạch giáo dục toàn trường (trừ Nháp, Đang sửa), cấu hình kế hoạch, duyệt khi được thêm vào chuỗi người ký (EDU-06, EDU-11, EDU-18) | Quản trị kỹ thuật (Keycloak, tạo tài khoản PRINCIPAL/ADMIN); giao quyền phân quyền cho người khác (AUTH-11); soạn kế hoạch giáo dục |
| Vice Principal | Web | Như Principal nhưng trong campus được phân công (AUTH-02). Kế hoạch giáo dục áp dụng toàn trường: lập, cùng soạn, ban hành / trả lại / xóa kế hoạch năm; cấu hình kế hoạch; duyệt chủ đề nhánh, hồ sơ chủ đề theo chuỗi (EDU-02..07, EDU-11, EDU-18) | Xem campus khác (trừ khi được phân công) |
| Teacher | Web + App | Điểm danh, báo ăn, quan sát hằng ngày, duyệt summary của lớp, báo sự cố CSVC, nhập đo sức khỏe (người nhập: PENDING); soạn và gửi chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày của lớp; xem kế hoạch năm đã ban hành của nhóm tuổi; đánh giá trẻ theo MT (EDU-06, EDU-12..19) | Xem lớp không được phân công; sửa kế hoạch năm |
| Tổ trưởng khối (Teacher + phân công AGE_GROUP_TEAM_LEADER) | Web | Lập, cùng soạn (mỗi lúc một người), gửi kế hoạch năm học của nhóm tuổi mình; duyệt chủ đề nhánh, hồ sơ chủ đề của các lớp trong nhóm tuổi theo chuỗi (EDU-02..04, EDU-10, EDU-18) | Ban hành, xóa kế hoạch năm; sửa kế hoạch nhóm tuổi khác |
| Parent | App | Xem thông tin được phép của con (PAR-*); gửi báo nghỉ, không cần duyệt (ATT-05) | Chat thay Zalo; xem trẻ khác |
| Kitchen Staff | App (+Web nếu cần) | Xem suất đã chốt, thực đơn đã duyệt, định lượng, dị ứng cần cho nấu | Sửa điểm danh; xem hồ sơ trẻ |
| System Admin | Web | Kỹ thuật: tạo tài khoản PRINCIPAL, khóa/mở tài khoản, cấu hình hệ thống, master data kỹ thuật, xử lý sự cố (AUTH-06) | Ra quyết định nghiệp vụ; phân quyền nghiệp vụ; mặc định không xem dữ liệu trẻ (AUTH-06, P-16) |

## Permission (ADR-0017)

Danh mục chính thức là enum `utils/enums/Permission`; BE kiểm bằng `@permissionGuard.has(...)`. Quyền ADMIN/PRINCIPAL/PARENT cố định trong code; PHT/GV/bếp theo ma trận do Hiệu trưởng bật/tắt + ngoại lệ từng người (AUTH-10..14). Quyền không vượt scope (AUTH-13).

| Quyền | Mặc định | HT cấu hình được cho |
| --- | --- | --- |
| `CHILD_PROFILE_VIEW` | PRINCIPAL, VICE_PRINCIPAL, TEACHER, PARENT | VICE_PRINCIPAL, TEACHER (không bếp — AUTH-05) |
| `ACCOUNT_MANAGE` (tạo tài khoản, cấp lại mật khẩu tạm), `ACCOUNT_STATUS_MANAGE` (khóa/mở) | ADMIN, PRINCIPAL | VICE_PRINCIPAL (mặc định tắt; chỉ GV/bếp campus mình — AUTH-15, AUTH-17) |
| `STAFF_ASSIGNMENT_MANAGE` (phân công) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; chỉ GV/bếp campus mình — AUTH-16) |
| `PARENT_ACCOUNT_MANAGE` (tài khoản phụ huynh gắn trẻ, khóa/mở, mật khẩu tạm) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; chỉ trẻ đang học trong campus mình — AUTH-18, AUTH-19) |
| `PERMISSION_MANAGE` | PRINCIPAL | không giao được (AUTH-11) |
| `CHILD_ENROLL` (tiếp nhận form, nhập Excel, mở lại hồ sơ) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; campus mình — CHD-01) |
| `CHILD_PROFILE_EDIT` (sửa hồ sơ, khai báo sức khỏe) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; trẻ campus mình — CHD-02) |
| `CHILD_PLACEMENT` (xếp/chuyển lớp, cho nghỉ học) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; campus mình — CHD-01) |

Ba mã `CHILD_*` chốt ngày 28/10 (D-1 A, plan `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md`); có trong enum và seed ma trận PHT từ V10, API dùng các mã này làm theo các phase sau của plan. FE giao PHT mảng "Hồ sơ trẻ" theo Thêm/Sửa: Thêm = `CHILD_ENROLL`; Sửa = `CHILD_PROFILE_EDIT` + `CHILD_PLACEMENT` + `PARENT_ACCOUNT_MANAGE`. Phạm vi chốt 2026-10-28 (D-7 A): PHT chỉ trong campus được phân công (AUTH-02); không giao PHT phạm vi toàn trường — FE bỏ lựa chọn này ở màn giao quyền PHT.

Định hướng cho module chưa làm (mỗi module tự thêm quyền khi có API): `attendance:record` · `leave:create` · `meal-count:confirm` · `menu:approve` · `food-master:manage` · `health:record` · `health-interpretation:approve` · `observation:record` · `summary:approve` · `facility-issue:report` · `facility-issue:manage` · `report:view` · `admin:accounts`. Hành động có người thực hiện PENDING (meal-count:confirm, health:record) ⇒ để cấu hình được, không hard-code.
