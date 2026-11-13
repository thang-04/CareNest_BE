# Flow — Đón trẻ, xác nhận trả trẻ

Module: attendance (→ notification). Rule: PCK-01..04, ATT-10, HLT-15, PAR-01. `[B]` = ngoài hệ thống, `[S]` = hệ thống.

## Ảnh phụ huynh

```text
[B] Nhà trường tải ảnh phụ huynh ở hồ sơ tiếp nhận                                           (PCK-02)
[S] Phụ huynh đăng nhập App lần đầu: chỉ đổi mật khẩu tạm, không bắt tải ảnh (user chốt 13/11)
    profileUpdateRequired luôn false; phụ huynh vẫn tự tải ảnh tài khoản được (dùng khi hồ sơ chưa có ảnh)
```

## Trả trẻ

```text
[B] Người đến đón (lúc nào cũng được — không phân biệt về sớm, đón muộn)                      (PCK-04)
[S] GV lớp mở bảng đón của lớp: trẻ có mặt "Chờ đón"; vắng, chưa điểm danh không trả được
[S] GV chọn trẻ ⇒ hiện phụ huynh trên hồ sơ trẻ kèm ảnh                                       (PCK-02)
      ├─ khớp ảnh một phụ huynh ⇒ chọn phụ huynh đó, nhập giờ trả ⇒ trả trẻ
      └─ người lạ / chưa có ảnh ⇒ gọi phụ huynh, ghi nội dung cuộc gọi:
            ├─ phụ huynh xác nhận ⇒ trả trẻ (người đón ghi trong nội dung cuộc gọi)
            └─ từ chối / không liên lạc được ⇒ không trả (vẫn lưu lần xác minh)
[S] Lưu người đón, giờ trả thực tế; mỗi trẻ trả một lần/ngày; không sửa, không hủy        (PCK-04, ATT-10)
[S] Trẻ đã trả ⇒ khóa ghi sức khỏe trong ngày                                                (HLT-15)
      ↓ cùng giao dịch: thông báo cho tài khoản phụ huynh của trẻ; sau khi lưu: đẩy tới thiết bị
[S] Phụ huynh nhận thông báo con đã được đón; xem trong danh sách thông báo của App         (PCK-04, PAR-01)
```

## Không có

- Phụ huynh đăng ký trước người đón hộ trên App: không làm; phụ huynh báo GV qua gọi/nhắn (PCK-03).
- Ghi riêng "về sớm", "đón muộn": không làm (user chốt 2026-10-31).
