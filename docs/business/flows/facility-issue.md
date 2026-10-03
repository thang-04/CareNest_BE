# Flow — Báo sự cố cơ sở vật chất

Module: facility-issue. Rule: FAC-01..04.

> Scope V1 đã thu hẹp: **chỉ báo và theo dõi sự cố**. Không phải asset management (FAC-03).

```text
[B] GV phát hiện thiết bị hỏng / thiếu / không đủ
[S] GV tạo FacilityReport (loại, mô tả, vị trí: campus/lớp/khu vực, ảnh tùy chọn)   (FAC-01)
      ↓ notification tới BGH trong scope campus
[S] BGH xem danh sách theo campus/trạng thái → cập nhật trạng thái + ghi chú      (FAC-02)
    OPEN → IN_PROGRESS → RESOLVED | REJECTED   (FAC-04, PROPOSED)
[B] Sửa chữa / mua bổ sung — ngoài hệ thống
[S] Lịch sử trạng thái được lưu; reporting đọc số liệu theo campus
```
