# Flow — Kế hoạch giáo dục và giáo án

Module: education-plan → learning-observation (đánh giá theo MT). Rule: EDU-01..22, OBS-07. `[B]` = ngoài hệ thống, `[S]` = hệ thống.
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
      Lưu (nút Lưu / Ctrl+S) vẫn Đang sửa; thoát trang ⇒ Nháp (hoặc Bị trả lại); 30 phút không lưu ⇒ tự nhả
[S] Tab Mục tiêu: lĩnh vực > lĩnh vực con > nhóm kết quả mong đợi > MT n > nội dung; tích nội dung vào chủ đề
[S] Tab Chủ đề và sự kiện: chủ đề (tên, viết tắt, lễ hội, từ ngày – đến ngày, ghi chú), đợt nghỉ;
      số tuần và danh sách MT của chủ đề hệ thống tự tính
[S] Tổ trưởng gửi ⇒ Chờ Phó HT ban hành (khóa với Tổ trưởng)
[S] Phó HT: sửa nhỏ / Ban hành ⇒ Đã ban hành (khóa hẳn) | Trả lại (bắt buộc lý do) ⇒ Bị trả lại
      Phó HT tự lập thì ban hành thẳng từ Nháp
[S] Xuất Word / in PDF theo mẫu ở mọi bước
[B] Phó HT, Tổ trưởng ký tay trên bản in
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
[B] Bản in giáo án để trống "Đánh giá trẻ trong ngày", "Kế hoạch điều chỉnh và bổ sung" để viết tay
```

## Lưu ý cho implementation

- Trạng thái do BE quyết định qua endpoint hành động; client không gửi status.
- Kế hoạch năm đã có BE (`/education-plans/goals`, G94-226). Cấu hình, tài liệu lớp, AI: UI chạy mock, API PROPOSED (`docs/system/CROSS_REPO_MAP.md`).
- Module khác tham chiếu MT qua id dòng mục tiêu, không qua số MT (số đánh lại khi chèn/xóa dòng).
- Bản kế hoạch chủ đề đã lưu giữ cả MT, nội dung lấy từ kế hoạch năm (EDU-15).
- Lưu tay, hỏi khi rời trang, xác nhận khi xóa là quy tắc giao diện (EDU-22).
