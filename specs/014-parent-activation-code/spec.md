# Specification — 014 Mã kích hoạt tài khoản phụ huynh

User duyệt làm Phase 2 ngày 2026-11-11 ("làm phase 2 đi"); quyết định nghiệp vụ AUTH-18 chốt 10/11. Domain spec AC-6..14 và contract `contracts/parent-activation.md` là chuẩn.

## Stories and requirements
FR1 bật SMS thật ⇒ tài khoản phụ huynh mới (form tiếp nhận, kích hoạt ở màn Tài khoản phụ huynh) tạo không mật khẩu, sinh mã, gửi SMS chứa tên đăng nhập + mã sau commit. FR2 endpoint công khai kích hoạt bằng SĐT + mã + mật khẩu mới; một lỗi chung cho mọi trường hợp không hợp lệ. FR3 đếm lần sai, khóa khi đủ 5; hạn 72 giờ; dùng một lần. FR4 HT/PHT gửi lại mã trong phạm vi, cách 60 giây, chỉ cho tài khoản đang chờ kích hoạt; trả mã khi không gửi được SMS thật. FR5 cổng giả giữ luồng mật khẩu tạm.

## Acceptance
AC-6..AC-14 theo domain spec mục 12.

## Clarifications
Câu 3, 5–9 của plan quản trị: user nhận đề xuất ngày 10/11/2026. Không đổi APP/FE ở spec này.
