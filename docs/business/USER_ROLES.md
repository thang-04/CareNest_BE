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
| Principal | Web | Xem tổng hợp 2 campus và báo cáo ngày/định kỳ (RPT-01, RPT-03), duyệt/xác nhận trong quyền, duyệt khen thưởng cuối năm (OBS-14), gửi thông báo của trường (PAR-05), xem hồ sơ phát triển; xem kế hoạch giáo dục toàn trường (trừ Nháp, Đang sửa), cấu hình kế hoạch, duyệt khi được thêm vào chuỗi người ký (EDU-06, EDU-11, EDU-18); cấp tài khoản và phân quyền nghiệp vụ cho PHT/GV/bếp/phụ huynh (AUTH-06, AUTH-10); cấu hình giờ chốt, số bữa và giá suất theo nhóm tuổi (ATT-06, NUT-17, NUT-19); xác nhận số suất thay PHT vắng (NUT-02) | Quản trị kỹ thuật (Keycloak, tạo tài khoản PRINCIPAL/ADMIN); giao quyền phân quyền cho người khác (AUTH-11); soạn kế hoạch giáo dục |
| Vice Principal | Web | Như Principal nhưng trong campus được phân công (AUTH-02); mỗi người phụ trách một mảng (dinh dưỡng / chuyên môn), sửa mảng khi HT cấp mã quyền mảng (AUTH-20). Tại cơ sở mình: xác nhận số suất, mở lại điểm danh có lý do (NUT-02, ATT-08). Mảng dinh dưỡng: lập và công bố thực đơn, nhập kho khô, ghi kiểm nhận hàng tươi, danh mục NCC (NUT-08, NUT-11, NUT-12). Mảng chuyên môn: nhập hồ sơ trẻ (CHD-01), xét khen cuối năm (OBS-14). Kế hoạch giáo dục áp dụng toàn trường: lập, cùng soạn, ban hành / trả lại / xóa kế hoạch năm; cấu hình kế hoạch; duyệt chủ đề nhánh, hồ sơ chủ đề theo chuỗi (EDU-02..07, EDU-11, EDU-18). Công bố kết quả cân đo (HLT-11); gửi thông báo của trường (PAR-05) | Xem campus khác trừ khi HT cấp quyền xem liên cơ sở — chỉ xem; quản lý tài khoản/phân công/phụ huynh campus khác (AUTH-20) |
| Teacher | Web + App | Điểm danh trên App (có mặt là ăn — ATT-01, ATT-02), đo nhiệt độ đầu ngày (HLT-07), đón trẻ: xác nhận người đón, giờ đón (PCK-02, PCK-04, ATT-10), ghi sức khỏe trong ngày (HLT-14), cân đo lớp mình và công bố (HLT-09, HLT-11), đánh giá ngày (OBS-01), nhận xét định kỳ và xác nhận (OBS-05), nhận xét lớp hằng ngày (RPT-02), phiếu bé ngoan và đề xuất khen cuối năm (OBS-10, OBS-14), soạn và gửi chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày của lớp; xem kế hoạch năm đã ban hành của nhóm tuổi; đánh giá trẻ theo MT (EDU-06, EDU-12..19); xem lại, chỉ đọc, lớp từng dạy (năm cũ hoặc dạy thay đã kết thúc) cùng cơ sở với lớp đang dạy (AUTH-03 — đã áp cho điểm danh, sức khỏe trong ngày). Tổ trưởng: dòng riêng bên dưới (AUTH-23) | Xem lớp không được phân công (trừ lớp cũ chỉ đọc — AUTH-03); tạo đơn nghỉ thay phụ huynh (ATT-05); sửa điểm danh sau giờ chốt khi chưa được mở lại (ATT-08); sửa đánh giá ngày đã qua (OBS-12); sửa kế hoạch năm |
| Tổ trưởng khối (Teacher + phân công AGE_GROUP_TEAM_LEADER) | Web | Lập, cùng soạn (mỗi lúc một người), gửi kế hoạch năm học của nhóm tuổi mình; duyệt chủ đề nhánh, hồ sơ chủ đề của các lớp trong nhóm tuổi theo chuỗi (EDU-02..04, EDU-10, EDU-18); chỉ HT phân công, hết phân công thì mất quyền (AUTH-23) | Ban hành, xóa kế hoạch năm; sửa kế hoạch nhóm tuổi khác |
| Parent | App | Xem các mục được phép của mọi con mình trên một tài khoản (PAR-01, PAR-06), dữ liệu có công bố chỉ sau khi công bố (PAR-03); nhận thông báo của trường, sức khỏe trong ngày, đã đón con (PAR-05, HLT-14, PCK-04); gửi báo nghỉ, không cần duyệt (ATT-05); sửa thông tin liên lạc của mình (CHD-05) | Chat thay Zalo; xem trẻ khác; sửa thông tin của trẻ; đăng ký trước người đón hộ trên App (PCK-03) |
| Kitchen Staff | App (chỉ App, màn tối thiểu — AUTH-21; cả tổ bếp mỗi cơ sở dùng chung một tài khoản — AUTH-22) | Xem thực đơn đã công bố, số suất đã xác nhận theo lớp từng bữa, định lượng, số suất dị ứng/thay thế theo lớp (AUTH-05, NUT-23); chụp ảnh mẫu bữa, ghi sổ lưu mẫu (NUT-16, NUT-21) | Đăng nhập Web; sửa điểm danh; xem hồ sơ hay tên trẻ dị ứng; nhập kho, ghi kiểm nhận hàng tươi (PHT ghi — NUT-11, NUT-12) |
| System Admin | Web | Kỹ thuật: tạo tài khoản PRINCIPAL, khóa/mở tài khoản, cấu hình hệ thống, master data kỹ thuật, xử lý sự cố (AUTH-06) | Ra quyết định nghiệp vụ; phân quyền nghiệp vụ; mặc định không xem dữ liệu trẻ (AUTH-06, P-16) |

## Permission (ADR-0017)

Danh mục chính thức là enum `utils/enums/Permission`; BE kiểm bằng `@permissionGuard.has(...)`. Quyền ADMIN/PRINCIPAL/PARENT cố định trong code; PHT/GV/bếp theo ma trận do Hiệu trưởng bật/tắt + ngoại lệ từng người (AUTH-10..14). Quyền không vượt scope (AUTH-13).

Kế hoạch giáo dục (đã có BE, chưa vào ma trận): `goals.view` · `goals.edit` · `goals.approve` · `plans.config` — hằng trong `utils/Constants`, tạm gán theo role tại `EduPlanAccessPolicy` (EDU-08). EDU-38 (ma trận + cột Tổ trưởng) SUPERSEDED — user chốt 29/10 giữ theo code.

| Quyền | Mặc định | HT cấu hình được cho |
| --- | --- | --- |
| `CHILD_PROFILE_VIEW` | PRINCIPAL, VICE_PRINCIPAL, TEACHER, PARENT | VICE_PRINCIPAL, TEACHER (không bếp — AUTH-05) |
| `ACCOUNT_MANAGE` (tạo tài khoản, cấp lại mật khẩu tạm), `ACCOUNT_STATUS_MANAGE` (khóa/mở) | ADMIN, PRINCIPAL | VICE_PRINCIPAL (mặc định tắt; chỉ GV/bếp campus mình — AUTH-15, AUTH-17) |
| `STAFF_ASSIGNMENT_MANAGE` (phân công) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; chỉ GV/bếp campus mình — AUTH-16) |
| `PARENT_ACCOUNT_MANAGE` (tài khoản phụ huynh gắn trẻ, khóa/mở, mật khẩu tạm) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; chỉ trẻ đang học trong campus mình — AUTH-18, AUTH-19) |
| `ALL_CAMPUS_VIEW` (xem dữ liệu trường mọi cơ sở, chỉ xem) | — | VICE_PRINCIPAL (mặc định tắt; vẫn cần phân công PHT còn hiệu lực; không mở tạo tài khoản/phân công/phụ huynh campus khác — AUTH-20) |
| `PERMISSION_MANAGE` | PRINCIPAL | không giao được (AUTH-11) |
| `CHILD_ENROLL` (tiếp nhận form, nhập Excel, mở lại hồ sơ) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; campus mình — CHD-01) |
| `CHILD_PROFILE_EDIT` (sửa hồ sơ, khai báo sức khỏe) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; trẻ campus mình — CHD-02) |
| `CHILD_PLACEMENT` (xếp/chuyển lớp, cho nghỉ học) | PRINCIPAL, VICE_PRINCIPAL | VICE_PRINCIPAL (mặc định bật; campus mình — CHD-01) |

Ba mã `CHILD_*` chốt ngày 28/10 (D-1 A, plan `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md`); có trong enum và seed ma trận PHT từ V39, API dùng các mã này làm theo các phase sau của plan. FE giao PHT mảng "Hồ sơ trẻ" theo Thêm/Sửa: Thêm = `CHILD_ENROLL`; Sửa = `CHILD_PROFILE_EDIT` + `CHILD_PLACEMENT` + `PARENT_ACCOUNT_MANAGE`. Phạm vi chốt 2026-10-28 (D-7 A): PHT chỉ trong campus được phân công (AUTH-02); không giao PHT phạm vi toàn trường — FE bỏ lựa chọn này ở màn giao quyền PHT.

Theo khảo sát team 27/10 (đang triển khai — plan `2026-10-27-survey-auth-alignment.md` (chưa có trong repo)):
- Mảng của PHT = mã quyền sửa của module, thêm cùng module; PHT mặc định tắt, HT cấp cho đúng người phụ trách mảng (AUTH-20). Không dùng mức Đầy đủ/Giới hạn/Không.
- Ma trận có cột `TEAM_LEADER` ("Tổ trưởng", đã có từ V38): áp cho GV đang có phân công `AGE_GROUP_TEAM_LEADER` còn hiệu lực, chỉ HT gán (AUTH-23). Không phải role đăng nhập — token mang role này bị bỏ qua. Mã quyền cho cột (vd. `goals.*`) thêm cùng module kế hoạch giáo dục.

Định hướng cho module chưa làm (mỗi module tự thêm quyền khi có API): `attendance:record` · `pickup:confirm` · `leave:create` · `meal-count:confirm` · `menu:approve` · `food-master:manage` · `health:record` · `health:publish` · `health-interpretation:approve` · `observation:record` · `summary:confirm` · `reward:approve` · mã quyền kế hoạch giáo dục (hiện theo role, EDU-08) · `notification:send` · `report:view` · `admin:accounts`. Người thực hiện lấy từ ma trận quyền, không hard-code theo role.
