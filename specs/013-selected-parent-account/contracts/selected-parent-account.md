# Dữ liệu và contract đã triển khai — Selected parent account

## Trạng thái
User đã duyệt triển khai; DTO, migration V81 và OpenAPI trên nhánh đã cập nhật. Nguồn quyết định và AC ở domain-spec.md; plan chính `docs/plans/completed/2026-11-05-selected-parent-account.md`.

## Dữ liệu
- Thêm `child_guardians.account_recipient BOOLEAN NOT NULL DEFAULT FALSE` bằng migration mới; unique partial index theo child_id khi account_recipient=true. Service bảo đảm đúng một người ở hồ sơ tiếp nhận mới.
- Lựa chọn gắn bản ghi guardian, không gắn thứ tự mảng hay tên. Đổi thứ tự/ảnh không đổi người nhận tài khoản.
- Request `guardians[].accountRecipient`: một true khi có nhiều guardian; một guardian mà client cũ không có field thì BE tự chọn. Nhiều guardian mà client bỏ field: trả lỗi chọn người nhận, không tạo tất cả như cũ.
- Response guardian thêm accountRecipient và id ổn định. GET /children/{id}/account-recipient trả ứng viên trong scope quản lý tài khoản; PUT cùng endpoint nhận {guardianId}, ghi lịch sử, chặn đổi người đã kích hoạt. FE adapter tổng hợp accountRecipient/selectionRequired và đếm danh sách tài khoản được chọn.
- Không đổi nghĩa guardianCount ở API dùng chung nếu caller cũ vẫn cần tổng liên kết; field mới phục vụ projection của màn quản lý tài khoản. Không lọc mất các guardian khác khỏi hồ sơ/đón trẻ.

## Tiếp nhận bằng form
`POST /children/enrollments`, JSON hoặc multipart payload: giữ ảnh childPhoto/guardianPhoto0..2 theo Spec012. Kiểm selection trước tạo identity/SMS; load/kiểm trùng danh tính theo rule hiện có nhưng chỉ tạo/liên kết recipient. Không gửi thông tin tài khoản của người không chọn trong kết quả kích hoạt.

## Excel
Template giữ cột nhóm guardian đầu tiên cũ, bổ sung các nhóm tùy chọn 2/3; thêm cột tùy chọn “CCCD phụ huynh nhận tài khoản”. Phải khớp đúng một guardian cùng dòng; parser phải phân biệt header nhóm 1/2/3 thay vì bắt prefix nhóm 1 cho mọi cột.

Preview trả payload.guardians đầy đủ và trạng thái selection; dropdown sửa lựa chọn chỉ trong dòng. Dòng chưa chọn khi nhiều người không phải dòng hợp lệ để nhập; giữ cách nhập các dòng hợp lệ hiện có và báo dòng bị bỏ qua.

POST /children/enrollments/import/preview nhận multipart file CSV/XLSX/XLS; POST /children/enrollments/import nhận {rows, campusId}. Đã có controller/service/parser thật, không thêm dependency. Import chỉ lưu hồ sơ/selection; không tạo hoặc liên kết UserAccount/GuardianChildLink tại bước này trong thiết kế mới.

## Quản lý tài khoản
- Read model danh sách/panel/pending chỉ trả recipient và thông tin account của người đó trong scope quản lý tài khoản. Hồ sơ chung vẫn trả các guardian theo quyền hiện có.
- GET /children/{id}/guardians/pending trả recipient chưa liên kết; POST /children/{id}/guardians/activate bắt buộc body {guardianId}.
- Kích hoạt gửi guardianId người đang xem; service khóa/đọc lại selection và từ chối khi không còn khớp. Nếu đã liên kết đúng người, retry trả kết quả đã liên kết, không tạo/gửi lại.
- Các đường tạo/liên kết cũ như `POST /children/{id}/guardians` phải kiểm eligibility của recipient hoặc được thay bằng luồng mới có kiểm tương đương; không để gọi API cũ tạo tài khoản cho người không chọn.
- Chọn lại trước kích hoạt: endpoint sửa lựa chọn có quyền/scope, kiểm guardian thuộc child, lưu audit; thay đổi sau kích hoạt cần thao tác quản lý có chủ đích, không tự gỡ liên kết.

## Hồ sơ cũ
Backfill tự chọn khi đúng một guardian, hoặc chỉ một guardian đã có liên kết. Trường hợp còn nhiều khả năng để requiresRecipientSelection=true, không đoán. Giữ nguyên user account, link và ảnh cũ; ghi rõ nhiều liên kết cũ vẫn tồn tại nếu chưa có thao tác quản lý thu hồi. Không dùng migration này để tự vô hiệu hóa tài khoản người khác.

## Tác động và kiểm chứng
BE: enrollment orchestration/validator/draft/ChildGuardian, import, guardian account reads/activation, các đường link cũ và migration. FE: ChildInfoFields/EnrollChildPage, template/parser/ImportChildrenPage, ParentActivationPage/GuardianPanel/guardianCoverage và mock/API adapters. Giữ ảnh, pickup, scope và cơ chế mật khẩu/thông báo hiện có; không đổi APP. Kiểm AC-1..8 bằng unit/HTTP/PostgreSQL/browser, gồm raw API bypass, selection đổi trong khi modal đang mở và account dùng chung nhiều trẻ.
