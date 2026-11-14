# Domain spec — Ảnh đại diện nhân sự

Nguồn: user chốt 14/11/2026 trong phiên làm việc (G94-355 / BE-FEAT-57, G94-356 / FE-FEAT-57): nhân sự tự đổi ảnh; nhân sự xem ảnh theo điểm trường; phụ huynh xem ảnh GV lớp con. Rule mới AUTH-27 (`docs/business/BUSINESS_RULES.md`). Kế thừa: AUTH-01/02/16/21, lưu tệp spec 017 (`USER_AVATAR`). Plan: `docs/plans/active/2026-11-14-staff-avatar.md`.

## 1. Domain Overview
Hôm nay chỉ phụ huynh có ảnh đại diện (`PUT /accounts/me/photo`, role `PARENT`). Nhân sự (Admin, HT, PHT, GV, bếp) chỉ hiện chữ cái đầu. Feature cho nhân sự tự đặt ảnh và cho người liên quan nhìn thấy ảnh đó, đúng phạm vi.

## 2. Scope
Trong: nhân sự tự tải/thay/xóa ảnh của mình; lấy link xem ảnh của nhiều người một lần theo quyền xem (nhân sự, phụ huynh xem GV). Ngoài: đổi ảnh hộ người khác; ảnh phụ huynh (giữ luật cũ, vẫn đi qua trẻ); cắt/xoay ảnh; màn App (phase sau); FE (plan client G94-356).

## 3. Actors & Roles
Nhân sự (ADMIN, PRINCIPAL, VICE_PRINCIPAL, TEACHER, KITCHEN_STAFF): tự đổi ảnh, xem ảnh nhân sự trong phạm vi. Bếp dùng App (AUTH-21) nên đổi ảnh trên App ở phase sau; BE mở sẵn. Phụ huynh: xem ảnh GV đang dạy lớp con. Hệ thống: kiểm định dạng/kích thước, lưu S3, ký link có hạn, lọc theo quyền.

## 4. Domain Concepts
Ảnh đại diện tài khoản: một ảnh mỗi tài khoản (không theo role), lưu ở `user_accounts.photo_key`. Phạm vi điểm trường của nhân sự: campus theo phân công còn hiệu lực hôm nay (PHT/bếp: campus phân công; GV: campus của lớp đang phân công). Link xem: URL ký có hạn 15 phút.

## 5. Domain Relationships
Tài khoản 1–0..1 ảnh. Nhân sự N–N campus qua phân công. Phụ huynh → trẻ (liên kết) → lớp (đang học hôm nay) → GV (phân công còn hiệu lực).

## 6. User Requirements
UR-1 nhân sự tải ảnh (jpg/png/webp ≤ 2 MB) làm ảnh đại diện, thay ảnh cũ. UR-2 nhân sự xóa ảnh, quay về chữ cái đầu. UR-3 màn có nhiều người (danh bạ, phân công, menu người dùng) hiện ảnh của những người được phép xem bằng một lần gọi. UR-4 phụ huynh thấy ảnh GV lớp con (App phase sau).

## 7. Business Rules
AUTH-27 (mới): chỉ tự đổi; tài khoản có role phụ huynh không xóa được ảnh (ảnh phụ huynh bắt buộc — chỉ thay); Admin/HT xem mọi nhân sự; PHT/GV/bếp xem nhân sự cùng điểm trường và mọi HT; ai cũng xem ảnh mình; nhân sự chưa có phân công chỉ bản thân + Admin/HT xem; phụ huynh chỉ xem GV đang phân công lớp con đang học. SIG/017: ảnh lưu S3 loại `USER_AVATAR`, thay ⇒ lưu mới, commit, xóa cũ. AUTH-21 không đổi.

## 8. Business Processes
`[B]` nhân sự mở Hồ sơ → chọn ảnh → `[S]` kiểm tệp, lưu S3, ghi key, xóa ảnh cũ sau commit → `[B]` thấy ảnh mới. `[B]` mở màn có danh sách người → `[S]` nhận danh sách userId, giữ người có ảnh và được xem, trả link 15 phút → `[B]` thấy ảnh; người còn lại hiện chữ cái đầu. `[B]` xóa ảnh → `[S]` bỏ key, xóa tệp sau commit.

## 9. State / Lifecycle
Ảnh: Không có → (tải) Có → (tải lại) Có (tệp mới, tệp cũ xóa) → (xóa) Không có. Không duyệt, không lịch sử.

## 10. Business Constraints
≤ 2 MB, jpg/png/webp nhận diện theo nội dung (`USER_AVATAR`). Một lần lấy link tối đa 100 userId. Người không được xem bị bỏ qua (không lộ ai có ảnh). Bucket private, không URL công khai.

## 11. Non-Functional Requirements
Một màn = một request lấy link. Ký link cục bộ (không gọi mạng). Không log userId của người được xem theo danh sách dài (chỉ đếm). Không cache ảnh ở proxy.

## 12. Acceptance Criteria
Xem `spec.md` AC-1…AC-9.

## 13. Domain Terminology
Ảnh đại diện = ảnh chân dung tài khoản. Nhân sự = tài khoản có role khác `PARENT`. Cùng điểm trường = giao nhau giữa tập campus của hai người theo phân công hôm nay.

## 14. Assumptions & Open Questions
Giả định: GV dạy thay vẫn tính phân công còn hiệu lực (đúng như phân công khác). Một tài khoản vừa nhân sự vừa phụ huynh: một ảnh chung, luật xem nhân sự áp khi người đó là GV/PHT…, luật phụ huynh áp cho ảnh qua trẻ. Không còn câu hỏi mở.
