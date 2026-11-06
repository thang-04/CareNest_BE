# Domain spec — Một phụ huynh nhận tài khoản cho mỗi trẻ

Nguồn: user ngày 05/11/2026 yêu cầu chọn một phụ huynh nếu hồ sơ có nhiều người; màn Tài khoản phụ huynh chỉ hiện người được chọn. Câu trả lời làm rõ cùng ngày: **Form kích hoạt ngay khi lưu; Excel kích hoạt sau**. Các phương án migration/UX trong tài liệu là thiết kế đề xuất, chưa phải phê duyệt triển khai.

## 1. Domain Overview
Hồ sơ trẻ có thể có nhiều phụ huynh/người giám hộ để liên hệ và đón trẻ. Luồng hiện có tạo/kích hoạt nhiều tài khoản theo toàn bộ danh sách; nhu cầu mới chọn đúng một người nhận tài khoản cho hồ sơ mới.

## 2. Scope
Thiết kế thay đổi form tiếp nhận, template/preview/import nhiều phụ huynh, lưu lựa chọn và màn quản lý tài khoản. Giữ ảnh và danh sách phụ huynh dùng đối chiếu đón trẻ. Không tự khóa/xóa tài khoản hoặc thu hồi liên kết hồ sơ cũ; không thay chính sách mật khẩu/SMS hoặc APP.

## 3. Actors & Roles
HT/PHT có CHILD_ENROLL chọn người ở form/import đúng scope hiện tại. PARENT_ACCOUNT_MANAGE quản lý/kích hoạt đúng campus theo policy tài khoản hiện có. Người không có quyền quản lý tài khoản vẫn lưu hồ sơ và lựa chọn theo ngoại lệ hiện có, không được tạo tài khoản mới.

## 4. Domain Concepts
Guardian: người trong hồ sơ, có hoặc chưa có tài khoản. Account recipient: đúng một guardian được chỉ định cho thao tác tài khoản của trẻ. UserAccount: danh tính đăng nhập dùng chung của một người. GuardianChildLink: nguồn quyền truy cập trẻ, khác việc một người có ảnh/liên hệ trong hồ sơ.

## 5. Domain Relationships
Một Child có 1..3 ChildGuardian theo giới hạn hiện hành; tối đa một guardian được đánh dấu account recipient. Một UserAccount có thể được chọn cho nhiều trẻ. Không tạo thêm tài khoản vì cùng người được chọn cho nhiều trẻ.

## 6. User Requirements
FR-1: chỉ một guardian nhận tài khoản; có một người thì tự chọn, nhiều người phải chọn rõ.
FR-2: form tạo/liên kết chỉ người được chọn khi lưu, theo quyền.
FR-3: Excel lưu đủ người và lựa chọn, kích hoạt sau ở màn tài khoản; preview cho sửa lựa chọn theo từng trẻ.
FR-4: màn tài khoản và các số đếm/chờ kích hoạt chỉ xét người được chọn.
FR-5: giữ đủ ảnh/liên hệ/người đón; không tự tạo/liên kết người không được chọn.
FR-6: bảo toàn tài khoản/liên kết cũ, kiểm lựa chọn tại BE và chống request cũ kích hoạt nhầm người.

## 7. Business Rules
G94-180/AUTH-20 hiện xác định form kích hoạt ngay, Excel kích hoạt sau và ngoại lệ quyền; user giữ thời điểm nhưng thay tập người được tạo/liên kết từ cả danh sách thành người được chọn. AUTH-04/18/19 giữ scope/liên kết/một người một tài khoản; PCK-02 giữ toàn bộ người đón. P-28 đã chốt cho phạm vi Spec013 ngày 05/11/2026: yêu cầu mới chốt một người nhận tài khoản cho luồng tiếp nhận mới; hồ sơ đã có nhiều liên kết xử lý riêng, không suy diễn thành thu hồi quyền hàng loạt.

## 8. Business Processes
Form: [B] gia đình xác định người nhận → [S] chọn radio, kiểm đúng một người → [B] lưu → [S] lưu hồ sơ, tạo/liên kết chỉ người được chọn theo quyền.
Excel: [B] chuẩn bị file → [S] đọc nhiều guardian, preview chọn sẵn theo CCCD khi có → [B] bổ sung lựa chọn → [S] nhập hồ sơ/lựa chọn → [B] mở Tài khoản phụ huynh, bấm kích hoạt → [S] kiểm lại người được chọn/quyền, tạo hoặc liên kết.

## 9. State / Lifecycle
Selection: chưa chọn / đã chọn; account state độc lập: chưa có / có sẵn chờ liên kết / liên kết hoạt động / liên kết tài khoản khóa. Xóa người đang chọn: còn một thì tự chọn, còn nhiều thì phải chọn lại. Đổi thứ tự không đổi người. Sau kích hoạt không thay người/ngắt liên kết ngầm.

## 10. Business Constraints
BE kiểm cùng một child và đúng một recipient; không tin userId/CCCD ngoài hồ sơ. Thiếu selection với nhiều guardian là lỗi ô/dòng nhập. Ảnh và vai trò người đón không phụ thuộc được chọn làm tài khoản. Dữ liệu cũ được backfill theo thiết kế đã duyệt: chỉ chọn khi không mơ hồ, giữ nguyên các liên kết.

## 11. Non-Functional Requirements
Lưu selection và dữ liệu mới trong cùng transaction; partial unique index bảo vệ tối đa một recipient/child. Account creation giữ compensation và hành vi mật khẩu/thông báo hiện có. Kích hoạt lặp không tạo tài khoản/trẻ/liên kết hoặc gửi thông báo lặp. Không log danh tính hay thông tin xác thực.

## 12. Acceptance Criteria
AC-1: một guardian tự chọn; nhiều guardian thiếu/đánh dấu nhiều người bị chặn; xóa/đổi thứ tự giữ đúng người và ảnh.
AC-2: form lưu ba guardian nhưng chỉ recipient được tạo/liên kết tài khoản; thiếu quyền vẫn lưu và chờ kích hoạt theo quy định hiện có.
AC-3: file một người cũ vẫn đọc; file hai/ba người có thể chọn bằng CCCD hoặc dropdown; sai/trùng/missing selection báo đúng dòng.
AC-4: nhập Excel không tạo/kích hoạt tài khoản; màn tài khoản chỉ hiện recipient và kích hoạt đúng người sau đó.
AC-5: tài khoản có sẵn được dùng lại khi đến bước liên kết; người không chọn không có liên kết mới với trẻ.
AC-6: gọi trực tiếp API/bấm từ modal cũ không kích hoạt người không được chọn hoặc ở ngoài scope; các đường thêm/liên kết cũ cũng phải tuân eligibility.
AC-7: các guardian khác, ảnh và người đón vẫn đầy đủ; lỗi/rollback không làm mất selection hay tạo side effect sai người.
AC-8: chuyển dữ liệu không khóa/xóa/thu hồi liên kết cũ; case nhiều người chưa rõ được đánh dấu để chọn, không tự chọn người đứng đầu.

## 13. Domain Terminology
“Phụ huynh nhận tài khoản” là nhãn giao diện. Không dùng “phụ huynh chính” để suy ra quyền giám hộ hay quyền đón. “Kích hoạt” trong luồng gồm tạo tài khoản mới hoặc liên kết người đã có tài khoản; không đồng nghĩa tự mở khóa tài khoản.

## 14. Assumptions & Open Questions
Đã chốt thời điểm form/Excel và chỉ một recipient. User đã duyệt cùng plan: tự chọn khi chỉ một người; cột CCCD tùy chọn và dropdown preview; hỗ trợ tối đa ba người như form hiện tại; migration không thu hồi quyền cũ. Tính năng đã triển khai; bằng chứng kiểm chứng ở quickstart.md. Không có câu hỏi nào về thời điểm kích hoạt còn mở.
