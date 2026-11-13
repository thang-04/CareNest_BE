# Domain spec — Mã kích hoạt tài khoản phụ huynh qua SMS

Nguồn: AUTH-18 (`docs/business/BUSINESS_RULES.md:32`, user chốt 30/10 và 10/11/2026), AUTH-21; plan `docs/plans/active/2026-11-10-twilio-sms-otp.md`; ADR-0024.

## 1. Domain Overview
Khi trường có nhà cung cấp SMS, phụ huynh nhận mã kích hoạt thay cho mật khẩu tạm và tự đặt mật khẩu trên App. Không ai ngoài phụ huynh biết mật khẩu.

## 2. Scope
Trong: tạo tài khoản phụ huynh mới (lưu form tiếp nhận, kích hoạt ở màn Tài khoản phụ huynh) khi bật SMS thật; gửi mã; gửi lại mã; kích hoạt bằng SĐT + mã. Ngoài: thêm phụ huynh kiểu cũ `POST /children/{id}/guardians` và cấp lại mật khẩu tạm cho tài khoản đã kích hoạt (giữ in phiếu); đổi SĐT; quên mật khẩu phụ huynh; giới hạn theo IP; APP/FE (plan client riêng).

## 3. Actors & Roles
HT, PHT có `PARENT_ACCOUNT_MANAGE` (trong phạm vi điểm trường — AUTH-17/19): tạo tài khoản, gửi lại mã, in phiếu mã. Phụ huynh (chưa đăng nhập): nhập SĐT + mã + mật khẩu trên App. Hệ thống: sinh mã, gửi SMS, đếm lần sai.

## 4. Domain Concepts
Mã kích hoạt: 6 chữ số ngẫu nhiên, gắn một tài khoản phụ huynh, hạn 72 giờ, tối đa 5 lần sai, dùng một lần. Tài khoản chờ kích hoạt: có mã chưa dùng, chưa có mật khẩu.

## 5. Domain Relationships
Một tài khoản phụ huynh có tối đa một mã hiện hành; gửi lại thay mã cũ. Mã thuộc tài khoản, không thuộc trẻ.

## 6. User Requirements
UR-1 phụ huynh nhận SMS chứa tên đăng nhập và mã khi tài khoản được tạo. UR-2 phụ huynh nhập SĐT + mã + mật khẩu mới hai lần để kích hoạt. UR-3 HT/PHT gửi lại mã cho phụ huynh chưa kích hoạt. UR-4 SMS không gửi được thật ⇒ HT/PHT nhận mã để in phiếu.

## 7. Business Rules
AUTH-18: không gửi mật khẩu qua SMS; mã 6 số, 72 giờ, 5 lần sai hủy; phụ huynh tự đặt mật khẩu; chỉ HT/PHT gửi lại, cách 60 giây; SMS lỗi ⇒ phiếu in mã (không in mật khẩu); tài khoản đã có không gửi mã. AUTH-21: phụ huynh không dùng gateway Web.

## 8. Business Processes
`[B]` HT/PHT lưu form tiếp nhận hoặc kích hoạt → `[S]` tạo tài khoản không mật khẩu + mã → `[S]` sau commit gửi SMS, ghi `parent_sms_logs` → `[ngoài]` phụ huynh mở App → `[APP]` nhập SĐT, mã, mật khẩu → `[S]` kiểm mã, đặt mật khẩu Keycloak, đánh dấu đã kích hoạt → `[APP]` đăng nhập. Nhánh: SMS lỗi → `[B]` HT/PHT bấm gửi lại → `[S]` mã mới, SMS; vẫn lỗi → trả mã để in.

## 9. State / Lifecycle
Mã: Hiệu lực → (đúng) Đã dùng | (sai lần 5) Hết lượt | (quá 72 giờ) Hết hạn | (gửi lại) Bị thay. Chỉ Hiệu lực kích hoạt được.

## 10. Business Constraints
Không trả mã qua API khi SMS thật đã gửi thành công. Lỗi kiểm mã không phân biệt SĐT không tồn tại/mã sai/hết hạn. Mật khẩu theo policy Keycloak.

## 11. Non-Functional Requirements
Không log SĐT đầy đủ, mã, mật khẩu. Mã lưu dạng hash. Đếm lần sai an toàn khi gửi đồng thời (khóa dòng).

## 12. Acceptance Criteria
AC-6 tạo tài khoản mới khi bật SMS thật ⇒ Keycloak không có mật khẩu, SMS chứa mã, không chứa mật khẩu. AC-7 đúng SĐT + mã ⇒ đặt mật khẩu, mã dùng một lần. AC-8 sai 5 lần ⇒ mã hủy, mã đúng sau đó cũng bị từ chối. AC-9 quá 72 giờ ⇒ từ chối. AC-10 gửi lại trong 60 giây ⇒ từ chối; sau đó mã cũ hết hiệu lực. AC-11 tài khoản đã kích hoạt hoặc tạo bằng mật khẩu tạm ⇒ không gửi lại mã. AC-12 SMS thất bại hoặc cổng giả ⇒ response gửi lại có mã để in. AC-13 người ngoài phạm vi điểm trường không gửi lại được. AC-14 cổng giả ⇒ luồng mật khẩu tạm giữ nguyên.

## 13. Domain Terminology
Mã kích hoạt (activation code); kích hoạt (đặt mật khẩu lần đầu bằng mã); phiếu in mã.

## 14. Assumptions & Open Questions
Giả định (agent, ghi Progress log): bật/tắt theo `carenest.sms.provider` (cổng giả = chưa có nhà cung cấp ⇒ giữ mật khẩu tạm, đúng AUTH-18 "khi có nhà cung cấp SMS"); bảng riêng thay `password_tokens`; đặt mật khẩu không cần đổi lại lần đầu. Không còn câu hỏi nghiệp vụ chặn.
