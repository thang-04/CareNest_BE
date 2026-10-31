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
[B] HT/PHT mở kỳ cân đo cho điểm trường (thường 3 tháng/lần)                    (HLT-01, sửa 2026-10-31)
[S] Ngày bắt đầu cách đợt/kỳ khác chưa hủy dưới 2 tháng 15 ngày ⇒ cảnh báo, vẫn lưu
    Mỗi điểm trường một đợt/kỳ chưa đóng (tính chung đợt khám)
[S] GV nhập trong kỳ đang diễn ra cho lớp mình: chiều cao, cân nặng, vòng đầu, ghi chú (HLT-09)
    Không cân đo lẻ ngoài kỳ; mỗi trẻ một lần đo/kỳ
[B] HT/PHT đóng kỳ ⇒ trẻ chưa cân thành "Vắng", số đo khóa; mở lại cần lý do
      ↓
[S] Lưu từng lần đo; trend, chênh lệch so với lần trước, biểu đồ             (HLT-02, HLT-13)
[S] Tự phân loại theo chuẩn WHO; GV chỉnh được, bắt buộc lý do — HOÃN      (HLT-03, HLT-10)
    Suy dinh dưỡng/thừa cân: chỉ đánh dấu, không có quy trình can thiệp     (HLT-12)
[S] Sửa số đo ⇒ ghi lý do, lưu lịch sử                                        (HLT-13)
      ↓
[S] Lưu là công bố ⇒ phụ huynh thấy kết quả + biểu đồ                        (HLT-11, PAR-01)
      ↓ tùy chọn
[S] AI giải thích DRAFT: giải thích số liệu, highlight trend, điểm cần chú ý cho GV (AI-07)
    KHÔNG chẩn đoán, KHÔNG khuyên điều trị (HLT-04)
[S] Nhân viên có quyền review/sửa/duyệt (HLT-05) ⇒ hiển thị chính thức; phụ huynh chỉ thấy bản đã duyệt
      ↓
DevelopmentProfile đọc measurement + trend + interpretation APPROVED (OBS-03)
```

## Khám bác sĩ (HLT-08)

```text
[B] HT/PHT mở đợt khám: bác sĩ khám định kỳ 1 lần/năm học, không chặn         (HLT-08, sửa 2026-10-31)
[S] Năm học đã có đợt khám ⇒ cảnh báo khi tạo; đợt khám cũng là một kỳ cân đo
[S] GV lớp mở phiếu khám Phụ lục II của trẻ: hành chính từ hồ sơ, tiền sử điền sẵn từ khai báo,
    người đi cùng chọn trong danh sách phụ huynh                                 (HLT-08)
[B] Bác sĩ khám; GV nhập sinh tồn, cân đo, dinh dưỡng, phát triển, tiêm chủng, lâm sàng; lưu nháp nhiều lần
[S] "Điểm cần bác sĩ xem lại" (gợi ý theo tuổi, không phải kết luận)
[B] GV nhập kết luận của bác sĩ ⇒ [S] kiểm đủ phiếu, công bố, mức theo dõi theo kết luận (HLT-10, HLT-11)
[S] Tiền sử ghi ngược về khai báo sức khỏe                                      (HLT-06)
    ├─ bình thường / vấn đề sức khỏe ⇒ mức NONE / WATCH (vấn đề đã biết: KNOWN_STABLE)
    └─ chuyển khám chuyên khoa / nguy cơ lao ⇒ CLOSE_WATCH ⇒ mở phiếu theo dõi   (HLT-16)
[S] Sửa phiếu đã kết luận ⇒ bắt buộc lý do, lưu các ô đổi, công bố lại        (HLT-13, HLT-11)
[S] Phát hiện dị ứng mới ⇒ cập nhật hồ sơ trẻ (module child — HLT-06)
```

## Phiếu theo dõi (HLT-16)

```text
[S] Mở phiếu "Chờ trao đổi phụ huynh", hạn 7 ngày từ lúc lưu kết quả
    Người đi cùng nghe bác sĩ kết luận tại buổi khám ⇒ tính là đã trao đổi
[B] GV lớp trao đổi với phụ huynh ⇒ [S] "Đã trao đổi"
[B] Phụ huynh báo kết quả khám chuyên khoa ⇒ GV ghi kết quả, nơi khám, tối đa 3 ảnh, ngày hẹn khám lại
[S] Lần khám sau có bất thường mới ⇒ vòng trao đổi mới (quay về "Chờ trao đổi")
[B] GV đóng phiếu theo lời bác sĩ:
    ├─ bình thường ⇒ mức NONE
    ├─ vấn đề đã biết ⇒ KNOWN_STABLE, thêm vào tiền sử bản thân của khai báo (HLT-06)
    └─ chưa có kết quả chuyên khoa ⇒ giữ mức của lần khám
[S] Quá hạn tính khi xem (lọc "quá hạn"), không nhắc tự động; BGH chỉ xem
[S] Trẻ ra trường khi chuyển năm học ⇒ phiếu tự đóng
```

Dị ứng không thuộc module này (thuộc child — HLT-06).
