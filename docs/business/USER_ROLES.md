# User Roles

Quyền = **Role → Permission (hành động)** ∩ **Access scope (dữ liệu)**. Scope lấy từ StaffAssignment (school-structure) hoặc GuardianChildLink (child). Rule ID: `BUSINESS_RULES.md` nhóm AUTH.

## Scope hierarchy

```text
SCHOOL                 Principal          StaffAssignment(scope=SCHOOL)
  └ CAMPUS             Vice Principal     StaffAssignment(scope=CAMPUS, hiệu lực từ–đến)
      └ CLASS          Teacher            StaffAssignment(scope=CLASS)
          └ CHILD      Parent             GuardianChildLink(child)
CAMPUS (bếp)           Kitchen Staff      StaffAssignment(scope=CAMPUS) — chỉ dữ liệu nutrition
SYSTEM                 System Admin       Tài khoản/role/cấu hình — không mặc định xem dữ liệu trẻ
```

## Actor

| Role | Client chính | Trách nhiệm trong CareNest | Không làm |
| --- | --- | --- | --- |
| Principal | Web | Xem tổng hợp 2 campus, duyệt/xác nhận trong quyền, theo dõi sự cố, xem hồ sơ phát triển | Quản trị tài khoản kỹ thuật |
| Vice Principal | Web | Như Principal nhưng trong campus được phân công (AUTH-02) | Xem campus khác (trừ khi được phân công) |
| Teacher | Web + App | Điểm danh, báo ăn, quan sát hằng ngày, duyệt summary của lớp, báo sự cố CSVC, nhập đo sức khỏe (người nhập: PENDING) | Xem lớp không được phân công |
| Parent | App | Xem thông tin được phép của con (PAR-*); gửi đơn nghỉ (PENDING P-15) | Chat thay Zalo; xem trẻ khác |
| Kitchen Staff | App (+Web nếu cần) | Xem suất đã chốt, thực đơn đã duyệt, định lượng, dị ứng cần cho nấu | Sửa điểm danh; xem hồ sơ trẻ |
| System Admin | Web | Tài khoản, role, phân công, cấu hình hệ thống, master data kỹ thuật | Ra quyết định nghiệp vụ; mặc định không xem dữ liệu trẻ (AUTH-06) |

## Hành động cần permission riêng (danh sách khởi đầu)

`attendance:record` · `leave:create` · `leave:approve` · `meal-count:confirm` · `menu:approve` · `food-master:manage` · `health:record` · `health-interpretation:approve` · `observation:record` · `summary:approve` · `facility-issue:report` · `facility-issue:manage` · `report:view` · `admin:accounts`

Gán permission cho role nào ở các hành động có người thực hiện PENDING (meal-count:confirm, leave:approve, health:record) ⇒ cấu hình, không hard-code.
