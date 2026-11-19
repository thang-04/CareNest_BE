# Spec — Ảnh đại diện nhân sự (019)

Domain spec: [domain-spec.md](domain-spec.md). Rule: AUTH-27. Jira: G94-355 (BE), G94-356 (FE).

## User stories
- US-1 (P1) Nhân sự đổi/xóa ảnh đại diện của mình.
- US-2 (P1) Người dùng Web thấy ảnh nhân sự trong phạm vi được xem ở mọi chỗ hiện avatar.
- US-3 (P2) Phụ huynh lấy được link ảnh GV lớp con (App dùng ở phase sau).

## Functional requirements
- FR-1 `PUT /accounts/me/photo` (multipart `file`) cho mọi role đã đăng nhập; lưu `USER_AVATAR`, thay ảnh cũ.
- FR-2 `GET /accounts/me/photo`, `GET /accounts/me/photo/url` cho mọi role đã đăng nhập.
- FR-3 `DELETE /accounts/me/photo` mới: bỏ ảnh, xóa tệp sau commit; chưa có ảnh ⇒ vẫn 200 (idempotent). Tài khoản có role `PARENT` (kể cả đa vai trò) không được xóa — ảnh phụ huynh bắt buộc (`profileUpdateRequired`), chỉ thay ⇒ 403.
- FR-4 `POST /accounts/photos/urls` `{userIds}` mới: 1–100 id; trả `[{userId, url, expiresAt}]` chỉ cho người có ảnh và người gọi được xem theo AUTH-27; id trùng gộp; thiếu/rỗng/quá 100 ⇒ 400.
- FR-5 Luật xem gom ở `AccountPhotoAccessPolicy` (package `security/authorization`).
- FR-6 Endpoint ảnh phụ huynh qua trẻ giữ nguyên.

## Acceptance (Given/When/Then)
- AC-1 Given GV đăng nhập, When `PUT /accounts/me/photo` ảnh PNG 500 KB, Then 200, `photo_key` dưới `avatars/users/`, ảnh cũ (nếu có) bị xóa sau commit.
- AC-2 Given GV đã có ảnh, When `DELETE /accounts/me/photo`, Then 200, `hasPhoto=false`, tệp bị xóa; gọi lại vẫn 200.
- AC-3 Given tệp 3 MB hoặc PDF, When PUT, Then 413 `FILE_TOO_LARGE` / 415 `UNSUPPORTED_MEDIA_TYPE` (mã 8005/8004).
- AC-4 Given HT, When lấy link cho GV hai campus và Admin, Then trả đủ người có ảnh.
- AC-5 Given PHT campus A, When lấy link cho GV campus A, GV campus B, HT, bếp campus A, Then trả GV A, HT, bếp A; không có GV B.
- AC-6 Given GV lớp campus A, When lấy link cho GV khác campus A, PHT campus B, chính mình, Then trả GV A và chính mình.
- AC-7 Given nhân sự chưa có phân công X có ảnh, When GV/PHT lấy link X, Then không có X; When HT lấy, Then có X.
- AC-8 Given phụ huynh có con đang học lớp L, When lấy link cho GV lớp L, GV lớp khác, HT, Then chỉ trả GV lớp L.
- AC-9 Given 101 id hoặc danh sách rỗng, When POST urls, Then 400.
- AC-10 Given tài khoản có role PARENT (kể cả GV kiêm phụ huynh), When `DELETE /accounts/me/photo`, Then 403, ảnh giữ nguyên.

## Success criteria
Một màn danh sách hiện ảnh bằng 1 request; không ai lấy được link ảnh ngoài AUTH-27 (ma trận AC-4…AC-8 xanh).

## Ngoài phạm vi
Đổi ảnh hộ; ảnh trong App; cắt ảnh; thay đổi luật ảnh phụ huynh.
