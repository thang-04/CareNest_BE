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
[S] Xếp mức BMI theo tuổi chuẩn WHO khi đọc: gầy còm nặng, gầy còm, bình thường,
    nguy cơ thừa cân (đến 60 tháng), thừa cân, béo phì                        (HLT-18, sửa 2026-11-04)
    Chỉ đánh dấu, không chẩn đoán, không quy trình can thiệp                  (HLT-12)
[S] Sửa số đo ⇒ ghi lý do, lưu lịch sử, mức BMI tính lại                      (HLT-13)
      ↓
[S] Lưu là công bố ⇒ phụ huynh thấy kết quả + biểu đồ                        (HLT-11, PAR-01)
[S] Cảnh báo BMI lưu ở sổ sức khỏe; danh sách lớp, chi tiết kỳ hiện mức và số trẻ theo mức (HLT-19)
      ↓ tùy chọn
[S] AI giải thích DRAFT: giải thích số liệu, highlight trend, điểm cần chú ý cho GV (AI-07)
    KHÔNG chẩn đoán, KHÔNG khuyên điều trị (HLT-04)
[S] Nhân viên có quyền review/sửa/duyệt (HLT-05) ⇒ hiển thị chính thức; phụ huynh chỉ thấy bản đã duyệt
      ↓
DevelopmentProfile đọc measurement + trend + interpretation APPROVED (OBS-03)
```

## Đóng và mở lại đợt khám (HLT-17)

```text
[S] Hết ngày kết thúc khám theo giờ trường ⇒ tự đóng EXAM, ghi lịch sử "Hệ thống"
    Trẻ chưa có phiếu ⇒ "Vắng"; phiếu nháp ⇒ "Chưa kết luận"; khóa nhập/sửa kết quả
    Đợt đã đóng/hủy không đóng lặp
[B] HT/PHT có thể đóng sớm hoặc mở lại để nhập bù (cần lý do)
    Đợt EXAM đã hết hạn ⇒ chọn ngày kết thúc mới từ hôm nay
[S] Mở lại xóa danh sách vắng cũ; hết ngày kết thúc mới ⇒ tự đóng tiếp
```

Nguồn: user, 2026-11-01 (HLT-17, chưa có xác nhận trường/team). Job chạy lúc 00:00 theo `carenest.time-zone`, đồng thời đóng bù khi ứng dụng khởi động. Kỳ cân đo vẫn đóng thủ công theo luồng trên.

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
    ├─ bình thường / vấn đề sức khỏe ⇒ mức NONE / WATCH (không hỏi vấn đề mới hay đã biết — bỏ 2026-11-04;
    │   KNOWN_STABLE chỉ còn ở phiếu cũ đã trả lời "đã biết")
    └─ chuyển khám chuyên khoa / nguy cơ lao ⇒ CLOSE_WATCH                      (HLT-10)
[S] Kết luận là cảnh báo ở sổ sức khỏe; không mở phiếu, không việc GV phải xử lý (HLT-19, bỏ HLT-16 2026-11-04)
[S] Sửa phiếu đã kết luận ⇒ bắt buộc lý do, lưu các ô đổi, công bố lại        (HLT-13, HLT-11)
[S] Phát hiện dị ứng mới ⇒ cập nhật hồ sơ trẻ (module child — HLT-06)
```

## Cảnh báo ở sổ sức khỏe (HLT-19)

```text
[S] Mỗi kết quả đã công bố mang cảnh báo của nó: đợt khám = kết luận bác sĩ; kỳ cân đo = mức BMI (HLT-18)
[S] Đầu sổ: kết luận của lần khám gần nhất đã kết luận + mức BMI của lần đo gần nhất trong kỳ cân đo xếp được mức
[S] Chi tiết đợt/kỳ: số trẻ theo mức, danh sách trẻ cần chú ý (đợt khám: theo dõi / theo dõi sát; kỳ cân đo: BMI khác bình thường)
    Tính khi đọc; không phiếu, không hạn, không nhắc
```

Phiếu theo dõi sức khỏe (HLT-16) đã bỏ ngày 2026-11-04: dữ liệu cũ (`health_follow_ups`, `health_follow_up_events`, `health_follow_up_images`) giữ trong DB, không còn API/màn hình. Phiếu khám đã kết luận theo mẫu theo dõi cũ vẫn xem, sửa được theo mẫu đó.

Dị ứng không thuộc module này (thuộc child — HLT-06).
