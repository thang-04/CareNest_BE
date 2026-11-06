# Domain spec — Ảnh trẻ và phụ huynh khi tiếp nhận

Nguồn: user ngày 05/11/2026 yêu cầu code nút tải ảnh trẻ/phụ huynh ở tiếp nhận trên FE và BE. Yêu cầu này đã xác định; các lựa chọn dưới đây đã được user duyệt bằng “duyệt” cùng ngày. Không coi đề xuất trước đó của trợ lý là quyết định user.

## 1. Domain Overview
Nhân viên nhận ảnh do gia đình cung cấp, gắn với đúng trẻ và phụ huynh để xem hồ sơ và đối chiếu khi trả trẻ. Hiện BE có ảnh tài khoản phụ huynh tự tải từ app; tiếp nhận chưa nhận ảnh, trẻ chưa có nơi lưu ảnh.

## 2. Scope
Thêm chọn tệp, xem trước, thay/bỏ ảnh đang chọn ở form tiếp nhận; lưu ảnh trên BE; hiển thị khi xem hồ sơ và trả trẻ. Đề xuất dùng ảnh trẻ làm avatar ở các vị trí hồ sơ/danh sách đang có avatar. Không làm camera trực tiếp, nhận diện khuôn mặt, link upload công khai, nhập ảnh qua Excel hoặc thay đổi APP.

## 3. Actors & Roles
Nhân sự có CHILD_ENROLL tiếp nhận trong phạm vi campus hiện có. Người xem hồ sơ xem ảnh theo ChildAccessPolicy; giáo viên phụ trách xem ảnh qua luồng trả trẻ hiện có. Không cấp thêm quyền quản lý tài khoản để tải ảnh hồ sơ.

## 4. Domain Concepts
Ảnh trẻ: ảnh chân dung gắn hồ sơ trẻ. Ảnh phụ huynh trên hồ sơ: ảnh gắn đúng ChildGuardian, tồn tại cả khi chưa kích hoạt tài khoản. Ảnh tài khoản: nguồn hiện có do phụ huynh tự tải; không đồng nhất hai nguồn nếu chưa được user duyệt.

## 5. Domain Relationships
Đề xuất mỗi Child tối đa một ảnh; mỗi ChildGuardian tối đa một ảnh hồ sơ; một trẻ vẫn tối đa ba phụ huynh theo quy định hiện có. Tệp riêng tư lưu S3, DB lưu metadata; không lưu ảnh vào khai báo sức khỏe hoặc log.

## 6. User Requirements
FR-1: chọn ảnh trẻ và ảnh riêng cho từng phụ huynh ở bước thông tin, xem lại trước khi xác nhận.
FR-2: giữ tệp và ảnh xem trước khi chuyển bước/lỗi lưu; bỏ ảnh đang chọn không xóa nhầm người khác.
FR-3: lưu và đọc lại đúng ảnh qua BE; đề xuất ảnh không bắt buộc để giữ luồng tiếp nhận cũ.
FR-4: màn trả trẻ đọc được ảnh tiếp nhận, kể cả phụ huynh chưa có tài khoản.
FR-5: giữ request JSON không ảnh của FE/APP/import hiện có.

## 7. Business Rules
CHD-01/CHD-02: tiếp nhận và hồ sơ có ảnh; AUTH-04: scope phụ huynh; PCK-02: đối chiếu ảnh khi trả trẻ. Nguồn: BUSINESS_RULES.md:18,56–57,94. Đề xuất bổ sung nguồn ảnh từ nhân viên tiếp nhận; không bỏ yêu cầu cập nhật ảnh ở lần đăng nhập APP đầu tiên. Ảnh không tự chứng minh quyền đón trẻ.

## 8. Business Processes
[B] Gia đình gửi ảnh, nhân viên chọn đúng ảnh → [S] preview trong form → [B] kiểm thông tin và xác nhận → [S] kiểm quyền/form/toàn bộ tệp, lưu hồ sơ và liên kết ảnh → [S] trả hồ sơ có photoUrl. Lỗi ảnh: trả lỗi đúng trường, giữ form. Không âm thầm tạo trẻ rồi báo thất bại khiến người dùng tiếp nhận trùng.

## 9. State / Lifecycle
Tệp mới chọn chỉ nằm trong bộ nhớ FE, có object URL tạm; xác nhận thành công mới gắn ảnh lưu trữ vào hồ sơ. Thu hồi object URL khi bỏ/thay/rời form. Dọn object mới khi giao dịch rollback. Khi sửa hồ sơ sau này phải giữ ảnh theo đúng danh tính phụ huynh, không theo vị trí mảng nếu người đã đổi.

## 10. Business Constraints
Đề xuất ảnh tùy chọn; JPEG/PNG/WEBP tối đa 2 MiB mỗi ảnh, theo convention ParentPhotoServiceImpl/ImageFiles. Không tin MIME client hoặc tên file. Không thay đổi quyền tiếp nhận, quan hệ phụ huynh, tài khoản, SMS và điều kiện nhập học.

## 11. Non-Functional Requirements
Private S3 qua PrivateImageStorage hiện có; API đọc ảnh yêu cầu token và scope, cache private/no-store, nosniff. Không thêm dependency/cloud mới. Không trả storage key, base64 hay ảnh thật trong log/fixture. Giới hạn multipart tương ứng tối đa bốn ảnh và payload JSON.

## 12. Acceptance Criteria
AC-1: chọn một ảnh trẻ và ảnh từng phụ huynh → preview và xem lại đúng người; chuyển bước không mất ảnh.
AC-2: lưu hợp lệ → mở lại hồ sơ có ảnh; ảnh trẻ dùng được ở avatar.
AC-3: không có ảnh → tiếp nhận JSON vẫn thành công như cũ; dữ liệu cũ hiện initials.
AC-4: tệp rỗng/quá dung lượng/sai nội dung/chỉ số phụ huynh sai → từ chối đúng trường, không tạo trẻ hoặc gửi SMS từ request không hợp lệ.
AC-5: người ngoài campus/class/child không đọc hoặc ghi được ảnh.
AC-6: phụ huynh chưa kích hoạt có ảnh hồ sơ → luồng trả trẻ vẫn xem được; không có ảnh hồ sơ thì dùng ảnh tài khoản hiện có.
AC-7: S3/DB lỗi → không có hồ sơ thành công giả hoặc tệp mới còn được liên kết; retry không tạo bản ghi ngoài ý muốn.
AC-8: sửa thông tin không kèm ảnh không làm mất ảnh; bỏ/đổi người giám hộ không chuyển nhầm ảnh sang người khác.

## 13. Domain Terminology
photoUrl là endpoint BE có kiểm quyền, không phải URL công khai S3. Guardian trong tính năng này là phụ huynh/người giám hộ đã có trên hồ sơ, không tự bổ sung người đón hộ.

## 14. Assumptions & Open Questions
User đã duyệt gói phương án ngày 05/11/2026: ảnh tùy chọn; ảnh phụ huynh lưu theo hồ sơ trẻ, ưu tiên khi trả trẻ và fallback ảnh tài khoản; JPEG/PNG/WEBP ≤2 MiB; một lượt triển khai BE+FE rồi review. Nguồn: câu trả lời “duyệt”. Không còn câu hỏi nghiệp vụ chặn triển khai.
