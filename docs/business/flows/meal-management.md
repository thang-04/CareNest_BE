# Flow — Thực đơn, dinh dưỡng, định lượng, kho, bếp

Module: nutrition (+ ai-assistance, child cho dị ứng, attendance cho suất). Rule: NUT-*, AI-*. ADR: 0005, 0007, 0008, 0009, 0018. Đối chiếu sơ đồ `Business process for day-boarding.drawio` của team (trang 1.–5.5), user chốt 2026-11-01; user chốt 2026-11-02: công bố thực đơn cả tuần (thay công bố theo ngày). Ma trận quyền, màn hình: `meal-management-permissions.md`.

## Thiết lập (NUT-05, NUT-17, NUT-18)

```text
[S] PHT phụ trách dinh dưỡng cấu hình theo nhóm tuổi: các bữa/ngày, chuẩn dinh dưỡng,
    giá suất, ngày hiệu lực                                                           (NUT-17, NUT-19)
[S] Danh mục thực phẩm: đơn vị, giá, dinh dưỡng có nguồn, chất dị ứng, Fresh/Stored,
    hệ số thải bỏ; nhập hàng loạt được                                                (NUT-05, NUT-10, NUT-18)
[S] Thực phẩm có nguồn gốc động vật / thực vật; món, bữa, ngày tính 6 chỉ số
    (đạm ĐV, đạm TV, béo ĐV, béo TV, glucid, kcal), % cơ cấu và % đạt định mức      (NUT-24)
[S] Món = nguyên liệu + gam/trẻ theo nhóm tuổi; sao chép món, dùng món tham khảo,
    sao chép định lượng giữa nhóm tuổi                                                (NUT-18)
```

## Lập và công bố thực đơn (NUT-08, NUT-09, NUT-13, NUT-19, NUT-20, NUT-23)

```text
[S] Thực đơn mẫu: bộ 4 tuần có nhãn mùa, theo khối nhà trẻ / mẫu giáo                 (NUT-13)
      ↓
[S] Kế hoạch tuần (DRAFT): lập tay | sao chép một tuần của bộ mẫu (xoay vòng) | AI gợi ý (AI-07)
      ↓
[S] Kiểm tra xác định từng ngày trong tuần
    cân đối theo nhu cầu của Bộ — chưa đạt ⇒ chặn công bố                             (NUT-08, NUT-18)
    giá vượt giá suất/ngày ⇒ cảnh báo                                                 (NUT-19)
    món lặp trong tuần ⇒ cảnh báo                                                     (NUT-20)
    dị ứng                                                                            (NUT-09)
    kho khô không đủ theo sĩ số dự kiến ⇒ cảnh báo, chưa trừ                          (NUT-11, P-38)
      └─ cộng dồn theo ngày từ hôm nay: số thực phẩm thiếu, ngày bắt đầu thiếu; chi tiết ở bản dự trù
[S] Món có chất dị ứng mức nặng: PHT chọn món thay thế theo chất × món,
    dùng chung cho mọi trẻ dị ứng chất đó; hệ thống gợi ý                             (NUT-23)
      ↓
[S] PHT công bố thực đơn CẢ TUẦN, một lần cho cả trường — công bố là duyệt;
    không chờ chốt suất, không công bố theo ngày                                      (NUT-08, chốt 02/11)
      └─ bếp và phụ huynh xem thực đơn tuần đã công bố                                (PAR-01)
[S] Đã công bố thì không sửa; ngoại lệ bất khả kháng: quyền riêng, lý do, báo bếp + PH (NUT-08, P-39)
[S] Đến ngày: thực đơn không đổi, chỉ thêm ảnh bữa thực tế                           (NUT-16)
```

## Hằng ngày

```text
[S] PHT lập bản dự trù: sĩ số dự kiến điền sẵn theo nhóm tuổi × cơ sở, PHT sửa,
    chọn kỳ và thực đơn dự kiến; lưu, in danh sách đặt hàng                           (NUT-07, NUT-12)
      └─ kèm hàng khô cần nhập thêm tới hết kỳ (tính khi xem, không lưu), in chung    (NUT-11, P-24 đổi 08/11)
[S] Bán trú hôm nay: kho khô sắp thiếu n thực phẩm từ ngày X (tới hết tuần sau)      (NUT-11)
[B] PHT gửi NCC; sau giờ chốt báo NCC tăng/giảm hàng tươi                             (NUT-12)
[B] Bếp kiểm hàng tươi, báo PHT qua Zalo/điện thoại
[S] Số suất ngày của cơ sở được xác nhận (flow attendance.md, NUT-02)
      └─ trừ kho khô cơ sở = định lượng thực đơn tuần đã công bố × suất đã xác nhận,
         lô hết hạn trước; thiếu ⇒ không chặn xác nhận, ghi phần thiếu, báo PHT      (NUT-07, NUT-11, P-38)
[S] PHT ghi đủ/thiếu/thừa: lần 1 so bản dự trù (sĩ số dự kiến),
    lần 2 so nhu cầu theo thực đơn tuần đã công bố × suất đã xác nhận                 (NUT-12)
[S] Bếp (App) xem thực đơn, số suất theo lớp từng bữa, định lượng,
    số suất dị ứng/thay thế theo lớp — không có tên trẻ                               (AUTH-05, NUT-17, NUT-23)
[B] Bếp nấu, chia suất theo danh sách lớp
    (không theo dõi trạng thái nấu, không ghi bàn giao — NUT-14, NUT-15 REMOVED)
[S] Bếp chụp ảnh mẫu bữa theo cơ sở, trong khung giờ HT cấu hình                      (NUT-16)
[B] Thiếu nguyên liệu, thiếu/thừa suất: báo PHT qua Zalo, điện thoại                  (NUT-22)
```

## Số suất xác nhận lại (ATT-07, NUT-03, NUT-11)

```text
[S] Số suất được xác nhận lại → ghi một lần điều chỉnh kho theo chênh lệch, báo bếp; thực đơn giữ nguyên
      └─ suất tăng mà kho không đủ: không chặn xác nhận lại
         [B] PHT đặt NCC mua thêm → [S] nhập phiếu bổ sung → trừ phần còn thiếu
```

## Kho khô (ADR-0009)

```text
[S] PHT quản lý danh mục NCC dùng chung hai cơ sở                                     (NUT-12)
[S] PHT nhập phiếu nhận trên Web: cơ sở, NCC, lô, hạn dùng, giá; xác nhận ⇒ tạo lô    (NUT-11)
[S] Trừ theo ngày khi suất cơ sở được xác nhận; điều chỉnh khi xác nhận lại suất     (NUT-11, P-38)
[S] Xem tồn theo cơ sở, theo lô, lịch sử nhập/trừ. Không chuyển liên cơ sở, không kiểm kê; bếp không dùng Web
```

## Lưu mẫu, hủy mẫu (NUT-21)

```text
[S] Bếp ghi lưu mẫu trên App (ngày, bữa, món) → PHT duyệt
[S] Sau ≥24 giờ bếp hủy mẫu, ghi hủy trên App → PHT duyệt
[S] Hệ thống tạo nhãn mẫu, biểu mẫu lưu/hủy từ bản ghi đã duyệt → PHT xuất/in
[B] Ký giấy các bên
```

Đã chốt 09–10/11: mẫu sổ lưu mẫu theo Mẫu 4, 5 (P-26) và kiểm thực ba bước do PHT điền, ký ảnh (P-27) — INS-01..04, SIG-01; chưa cài đặt, xem `docs/plans/active/2026-11-10-inspection-records-signatures.md`.

## AI gợi ý thực đơn (tùy chọn, không bắt buộc)

Bắt buộc: gợi ý kế hoạch tuần. Tùy chọn, làm nếu kịp: gợi ý thực đơn mẫu, gợi ý điều chỉnh thực đơn dự kiến ngày (AI-07). Nháp lưu ở `ai_drafts` (ADR-0018).

```text
Input tối thiểu: food/nutrition data, nhóm tuổi, thực đơn gần đây, mùa, khẩu vị vùng,
thời tiết, dị ứng/lưu ý sức khỏe tạm thời DẠNG TỔNG HỢP (không tên trẻ — AI-04)
      ↓ ai-assistance (port AiClient)
Nháp (source=AI) → kiểm tra xác định như trên (NUT-09)
      ↓
PHT nhận và sửa → lưu DRAFT | bỏ gợi ý
AI tắt/lỗi → lập thực đơn thủ công như bình thường (AI-03)
```
