# Flow — Sức khỏe: đầu ngày, trong ngày, cân đo định kỳ

Module: health (→ learning-observation đọc cho DevelopmentProfile; → notification). Rule: HLT-*, AI-*, PAR-03.

## Đầu ngày (HLT-07)

```text
[B] GV nhận trẻ, quan sát, đo nhiệt độ bằng nhiệt kế
[S] GV nhập nhiệt độ, biểu hiện cùng màn điểm danh (App hoặc Web) — flow attendance.md
    Không chụp ảnh trẻ; dặn dò miệng của phụ huynh không ghi
```

Còn mở: danh sách triệu chứng chọn nhanh (P-21), trẻ có dấu hiệu bất thường có nhận không (P-22), mức ưu tiên Capstone (P-23).

## Trong ngày (HLT-14)

```text
[B] Trẻ có triệu chứng hoặc tai nạn nhỏ (ngã, trầy xước)
[S] GV ghi triệu chứng/sự việc và cách xử trí; xử lý tùy mức độ
      ↓ event after-commit
[S] Phụ huynh nhận thông báo ngay (không chờ công bố — PAR-03)
      ├─ trẻ về sớm ⇒ flow pickup.md (PCK-04)
      └─ BGH xem số trẻ ốm/bất thường theo lớp trong báo cáo ngày (RPT-01)
```

## Cân đo định kỳ

```text
[B] Đợt cân đo: mặc định 3 lần/năm, chu kỳ cấu hình được (HLT-01)
[S] GV cân đo và nhập cho lớp mình: chiều cao, cân nặng, tình trạng, ghi chú   (HLT-09)
      ↓
[S] Lưu từng lần đo; trend, chênh lệch so với lần trước, biểu đồ             (HLT-02, HLT-13)
[S] Tự phân loại theo chuẩn WHO; GV chỉnh được, bắt buộc lý do              (HLT-03, HLT-10)
    Suy dinh dưỡng/thừa cân: chỉ đánh dấu, không có quy trình can thiệp     (HLT-12)
[S] Sửa số đo ⇒ ghi lý do, lưu lịch sử                                        (HLT-13)
      ↓
[S] GV hoặc PHT công bố ⇒ phụ huynh thấy kết quả + biểu đồ                   (HLT-11, PAR-01)
      ↓ tùy chọn
[S] AI giải thích DRAFT: giải thích số liệu, highlight trend, điểm cần chú ý cho GV (AI-07)
    KHÔNG chẩn đoán, KHÔNG khuyên điều trị (HLT-04)
[S] Nhân viên có quyền review/sửa/duyệt (HLT-05) ⇒ hiển thị chính thức; phụ huynh chỉ thấy bản đã duyệt
      ↓
DevelopmentProfile đọc measurement + trend + interpretation APPROVED (OBS-03)
```

## Khám bác sĩ (HLT-08)

```text
[B] Bác sĩ khám định kỳ 2 lần/năm
[S] Phát hiện dị ứng mới ⇒ cập nhật hồ sơ trẻ (module child — HLT-06)
    Nội dung khám khác ghi gì trên hệ thống: chưa có rule
```

Dị ứng không thuộc module này (thuộc child — HLT-06).
