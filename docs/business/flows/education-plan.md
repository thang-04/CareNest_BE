# Flow — Kế hoạch giáo dục và giáo án

Module: education-plan → learning-observation (đánh giá theo MT). Rule: EDU-01..41, OBS-07, AI-07. ADR: 0020, 0006. `[B]` = ngoài hệ thống, `[S]` = hệ thống.
Nguồn: UI CareNest_FE (Tiền chốt UI là chuẩn, 20/10, 22/10), mẫu MN Thái Dương, `huong_dan_nghiep_vu/phan_viec_cac_actor_giao_an.md`.

Kế hoạch năm theo **nhóm tuổi**, áp dụng toàn trường. Chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày theo **từng lớp**. Sợi dây xuyên suốt là **số MT 1..n** của kế hoạch năm:

```text
Kế hoạch năm: nội dung của MT tích vào chủ đề
  → Kế hoạch chủ đề: GV viết hoạt động giáo dục cho từng MT của chủ đề
    → Giáo án tuần: chọn hoạt động vào ô (giờ, ngày) ⇒ ô nhớ MT
      → Kế hoạch ngày: "Mục tiêu trong ngày" = MT của hoạt động trong ngày
        → Đánh giá trẻ hằng ngày theo MT (learning-observation)
```

## 0. Cấu hình (Hiệu trưởng, Phó HT) — EDU-11

```text
[S] Đổi tên hiển thị lĩnh vực; thêm lĩnh vực khi Bộ bổ sung (5 lĩnh vực chuẩn không xóa)
[S] Chuỗi người ký cho kế hoạch năm, chủ đề nhánh, hồ sơ chủ đề (mặc định Tổ trưởng → Phó HT; thêm Hiệu trưởng được)
[S] Các dòng hoạt động của kế hoạch tuần, dòng nào gộp cả tuần
```

## 1. Kế hoạch năm học (Tổ trưởng nhóm tuổi, Phó HT) — EDU-01..10

```text
[S] Phó HT (mọi nhóm tuổi) hoặc Tổ trưởng (nhóm tuổi mình) lập kế hoạch: 1 bản / (năm học, nhóm tuổi)
[S] Vào trang soạn ⇒ Đang sửa, khóa cho người đó; người kia chỉ xem "Đang được [tên] sửa"
      Lưu tay: chỉ ghi lên hệ thống khi bấm Lưu/Ctrl+S hoặc gửi/ban hành (Tiền chốt 27/10); giữ nội dung dở
      Heartbeat 20 giây, hết quyền sau 90 giây mất liên lạc; thoát bình thường nhả quyền ngay khi server nhận
      Cùng tài khoản mở lại (vd. sau mất điện) ⇒ phiên mới nhận quyền ngay, Khôi phục dùng được ngay;
      tab cũ còn sống ⇒ "Đã mất quyền sửa…", giữ bản giữ; người khác vẫn chờ 90 giây (EDU-10)
      Chờ duyệt: chỉ Phó HT sửa và cũng giữ quyền theo phiên, không đổi trạng thái chờ
      Web giữ ẩn bản chưa lưu trên máy để khôi phục khi mở lại; phải kiểm quyền/revision, không ghi đè bản mới
[S] Tab Mục tiêu: lĩnh vực > lĩnh vực con > nhóm kết quả mong đợi > MT n > nội dung; tích nội dung vào chủ đề
      Lấy từ Chương trình của Bộ (EDU-28): chọn MT theo nhóm tuổi, kèm nội dung; gợi ý nội dung mục C; trang nguồn để đối chiếu
[S] Lấy từ kế hoạch có sẵn (EDU-24, yêu cầu đã chốt; chưa triển khai): chọn năm học/nhóm tuổi và một nguồn;
      giữ quyền EDU-06 (chỉ Phó HT khác nhóm tuổi, Tổ trưởng chỉ nhóm tuổi mình), tích từng MT hoặc tất cả;
      chỉ chép MT và nội dung, không chép chủ đề/đợt nghỉ/lịch/dấu tích, giữ dữ liệu đích; Lưu theo EDU-22
[S] Tab Chủ đề và sự kiện: chủ đề (tên, viết tắt, lễ hội, từ ngày – đến ngày, ghi chú), đợt nghỉ;
      số tuần và danh sách MT của chủ đề hệ thống tự tính
[S] Tổ trưởng gửi ⇒ Chờ Phó HT ban hành (khóa với Tổ trưởng)
[S] Phó HT: sửa nhỏ / Ban hành ⇒ Đã ban hành (khóa hẳn) | Trả lại (bắt buộc lý do) ⇒ Bị trả lại
      Phó HT tự lập thì ban hành thẳng từ Nháp
[S] Xuất Word / in PDF theo mẫu ở mọi bước
[B] Phó HT, Tổ trưởng ký tay trên bản in
[S] Sửa sau ban hành (EDU-25): Tạo phiên bản điều chỉnh ⇒ phiên bản n+1 Nháp chép đủ nội dung ⇒ TT gửi → PHT ban hành
      (PHT tự tạo thì ban hành thẳng); bản cũ giữ nguyên, lớp dùng bản cũ tới khi bản mới ban hành
[S] Bản mới ban hành ⇒ tài liệu lớp của bản cũ chỉ xem; giáo viên tạo tài liệu trên bản mới từ bản mẫu hoặc chép bản cũ,
      ô gắn MT/chủ đề đổi hoặc bị bỏ hiện đỏ, sửa/đánh dấu xong mới gửi; đi lại chủ đề nhánh → hồ sơ chủ đề (EDU-26)
```

## 2. Chủ đề nhánh (giáo viên lớp, cả năm) — EDU-12, EDU-14, EDU-18

```text
[S] Mở khi kế hoạch năm của nhóm tuổi đã ban hành
[S] Bảng Tháng | Chủ đề | Chủ đề nhánh | Tuần n (dd/mm → dd/mm, có dòng nghỉ) | Ghi chú; tuần và chủ đề lấy từ kế hoạch năm
[S] GV đặt tên từng tuần; AI gợi ý / viết lại tên là bản nháp, giữ ✓ hoặc bỏ ✗ từng ô (EDU-20)
[S] Gửi khi mọi tuần có tên và hết ô AI chưa xử lý ⇒ duyệt theo chuỗi (mặc định Tổ trưởng → Phó HT)
[S] Người tới lượt: Duyệt ⇒ chuyển người kế tiếp / Đã duyệt | Trả lại (lý do) ⇒ Bị trả lại, GV sửa và gửi lại
```

## 3. Kế hoạch chủ đề (giáo viên lớp, mỗi chủ đề) — EDU-13, EDU-15

```text
[S] Mở khi chủ đề nhánh đã duyệt
[S] I. MT | Mục tiêu | Nội dung (lấy sẵn từ kế hoạch năm, chỉ nội dung đã tích vào chủ đề, GV không sửa) | Hoạt động giáo dục
      GV viết hoạt động dạng "Loại giờ: nội dung" (loại giờ = dòng của kế hoạch tuần)
      Trang nhắc MT chưa có hoạt động
[S] II. Môi trường giáo dục: trong lớp, ngoài lớp
[S] Lưu ⇒ mở được giáo án tuần của các nhánh trong chủ đề
```

## 4. Giáo án tuần = kế hoạch tuần + 5 kế hoạch ngày — EDU-16

```text
[S] Kế hoạch tuần: I. Mục đích (kiến thức, kỹ năng, thái độ), II. Chuẩn bị, III. bảng dòng hoạt động × Thứ 2–6
      Ô có gợi ý từ cột Hoạt động giáo dục của kế hoạch chủ đề theo loại giờ; chọn ⇒ ô nhớ MT
      Mỗi hoạt động chỉ xếp vào 1 ô ngày trong cả chủ đề; ô gộp cả tuần lặp được
[S] Kế hoạch ngày (Thứ 2–6): đề tài lấy từ ô Hoạt động học; Mục tiêu trong ngày = MT các hoạt động trong ngày;
      tiến trình hoạt động học (ổn định, nội dung trọng tâm, kết thúc), ngoài trời, buổi chiều: cô | trẻ | ghi chú
      AI gợi ý tiến trình giờ học chỉ điền ô trống, GV giữ / bỏ (EDU-20)
```

## 5. Gửi và duyệt hồ sơ chủ đề — EDU-17, EDU-18

```text
[S] Hồ sơ chủ đề = kế hoạch chủ đề + giáo án tất cả các nhánh; gửi 1 lần (thiếu nhánh thì chặn)
      Hạn gửi = ngày bắt đầu chủ đề (chỉ nhắc); cảnh báo không chặn khi còn hoạt động chưa xếp vào tuần
[S] Duyệt theo chuỗi; Tổ trưởng chỉ duyệt lớp thuộc nhóm tuổi mình; GV kiêm Tổ trưởng của lớp ⇒ gửi thẳng Phó HT
[S] Trả lại bắt buộc lý do ⇒ Bị trả lại cả hồ sơ
[S] Xuất Word / in hồ sơ; cuối mỗi tuần có khung "Duyệt giáo án từ ngày … đến ngày …"
[B] Người duyệt ký tay trên bản in
```

## 6. Đánh giá trẻ hằng ngày — EDU-19, OBS-07

```text
[S] Giáo án tuần đã duyệt ⇒ nút "Đánh giá trẻ ngày này" mở trang Đánh giá hằng ngày
[S] Mỗi giờ có MT là một cột chấm Đạt / Chưa đạt theo MT; lớp chưa có giáo án duyệt ⇒ khóa phần này
[B] Bản in giáo án để trống "Đánh giá trẻ trong ngày" để viết tay
[S] "Kế hoạch điều chỉnh và bổ sung" của từng ngày: giáo viên lớp ghi trong hệ thống ở mọi trạng thái, kể cả đã duyệt (EDU-27); in kèm nội dung
```

## Lưu ý cho implementation

- Trạng thái do BE quyết định qua endpoint hành động; client không gửi status.
- Đã có BE (G94-226): kế hoạch năm (`/education-plans/goals`), cấu hình (`/education-plans/config`), tài liệu lớp (`/education-plans/class-docs`). AI: UI chạy mock, API PROPOSED (`docs/system/CROSS_REPO_MAP.md`).
- Module khác tham chiếu MT qua id dòng mục tiêu, không qua số MT (số đánh lại khi chèn/xóa dòng).
- Bản kế hoạch chủ đề đã lưu giữ cả MT, nội dung lấy từ kế hoạch năm (EDU-15).
- Mục tiêu năm học và các màn kế hoạch lớp đều lưu tay (Tiền chốt 27/10); Web giữ ẩn bản chưa lưu trên máy để khôi phục. Rời khi chưa lưu và xóa mục có cảnh báo/xác nhận theo EDU-22.

## Học liệu và dạy học
Giáo viên lớp lưu activities trước khi upload; S3 giữ binary private và DB metadata. Chỉ Nháp/Bị trả lại được upload/gắn lại/gỡ. Các mutation khóa parent cùng luồng gửi/duyệt. Người xem scope hiện hành đọc học liệu active; editor thấy phần cần gắn lại. Trình chiếu không đổi trạng thái duyệt, giữ MT và nội dung đã xếp ngày; stale pick không ghép media theo ID vị trí. Ảnh/video/PDF trực tiếp, Office tải. Xem specs/002-class-plan-teaching-media và ADR-0018.

## Đối chiếu khảo sát team 27/10 (từ `dev`, EDU-29..38)

Khảo sát G94-254 viết trên `dev` thành EDU-01..10; khi merge G94-226 đổi số thành EDU-29..38. Luồng trên (EDU-01..28) là luồng code đang chạy. Khớp: EDU-30 (soạn/ban hành mục tiêu năm), EDU-33 (duyệt không ký), EDU-34 (mẫu có cấu trúc + Word), EDU-36 (nhân bản bản đã duyệt — EDU-25, EDU-26). Mới, chưa làm:

```text
[S] Phụ huynh: tên các hoạt động trong ngày của lớp con   (EDU-37, PAR-01)
[S] AI gợi ý nội dung hoạt động từ mục tiêu, GV sửa       (AI-07, EDU-20)
```

Mâu thuẫn với code — user chốt 29/10/2026 theo code, dòng khảo sát SUPERSEDED:
- EDU-31 (Tổ trưởng duyệt kế hoạch chính lớp mình) ⇒ theo EDU-18: bỏ bước Tổ trưởng khi Tổ trưởng dạy chính lớp đó.
- EDU-32 (chỉ duyệt một lần đầu năm, tuần/ngày không duyệt) ⇒ theo EDU-17..19: hồ sơ chủ đề gồm giáo án tuần/ngày duyệt theo từng chủ đề; đánh giá theo giáo án đã duyệt.
- EDU-35 (GV tự thêm mục kế hoạch ngày) ⇒ theo EDU-11, EDU-16: dòng hoạt động do HT/PHT cấu hình.
- EDU-38 (quyền theo ma trận ADR-0017) ⇒ theo EDU-08: `goals.*`, `plans.config` gán theo role tại `EduPlanAccessPolicy`.

Lịch chủ đề (đóng P-29, user chốt 30/10 theo mẫu Thái Dương): thời lượng do người lập đặt, mỗi tuần học là một chủ đề nhánh (EDU-39); chủ đề bắt đầu Thứ 2, kết thúc Thứ 6, không trùng nhau — kiểm khi gửi/ban hành (EDU-39, EDU-40); đợt nghỉ ghi được từng ngày, tuần chỉ thành tuần nghỉ khi nghỉ trọn Thứ 2 → Thứ 6 (EDU-41).
