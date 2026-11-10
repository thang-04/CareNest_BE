# Flow — Đánh giá hằng ngày, nhận xét định kỳ, khen thưởng, hồ sơ phát triển

Module: learning-observation (đọc attendance, nutrition, health, education-plan; dùng ai-assistance). Rule: OBS-*, AI-*, PAR-03, EDU-19. ADR: 0006, 0008.

## Đánh giá hằng ngày (OBS-01, OBS-02, OBS-12, OBS-13)

```text
[S] Danh sách trẻ có mặt hôm nay (attendance); trẻ vắng tự bỏ qua
[S] Mặc định mọi trẻ "bình thường"; GV chỉ sửa trẻ khác thường
    7 mặt: ăn uống, ngủ, cảm xúc, hành vi/quan hệ với bạn, tham gia hoạt động,
           kiến thức–kỹ năng theo bài học, sức khỏe
    Mức: Đạt / Cần cố gắng / Chưa đạt · ô nhận xét không bắt buộc
    Tiêu chí cố định theo Bộ hay trường cấu hình: P-11
[S] Kiến thức–kỹ năng đánh giá theo mục tiêu hoạt động của kế hoạch ngày (education-plan — OBS-07)
[S] GV lưu ⇒ phụ huynh xem đánh giá ngày của con (OBS-13)
      ↓ hết ngày
[S] Khóa sửa (OBS-12)
[S] GV viết nhận xét chung về lớp 1–2 câu ⇒ BGH xem trong báo cáo ngày (RPT-02)
```

## Ngữ cảnh hoạt động và đánh giá theo MT (ADR-0006, OBS-07, EDU-19)

```text
[S] Giáo án tuần/ngày của lớp đã duyệt (education-plan): mỗi ô (giờ, ngày) có hoạt động và MT của kế hoạch năm
[S] Đánh giá hằng ngày: mỗi giờ có MT là một cột, GV chấm Đạt / Chưa đạt cho từng trẻ
      Lớp chưa có giáo án duyệt ⇒ khóa phần chấm theo MT
```

Không import GoKids, không nhập metadata hoạt động riêng. Luồng soạn, duyệt giáo án: `docs/business/flows/education-plan.md`.

## Nhận xét định kỳ (OBS-04, OBS-05, OBS-06)

```text
Đánh giá ngày trong kỳ (tuần / tháng / học kỳ / cuối năm), theo mẫu riêng của trường (P-30)
      ├─ Mẫu nền: điền số liệu sẵn, luôn có, không AI
      └─ Tùy chọn: AI nháp (input tối thiểu, bí danh — AI-04, AI-08; giọng văn AI-10)
      ↓
Nháp → GV sửa → GV xác nhận là xong (không có bước BGH duyệt; BGH xem)
      ↓
Phụ huynh nhận tất cả các kỳ (OBS-06, PAR-03)
```

## Khen thưởng (OBS-10, OBS-14)

```text
[S] GV cắm cờ cho trẻ theo biểu hiện trong ngày
[S] Cuối tuần: trẻ đi học ≥4 ngày và có ≥3 cờ đủ điều kiện; GV tự chọn trẻ nhận phiếu bé ngoan
    (hệ thống không gợi ý) ⇒ phụ huynh thấy ngay khi GV xác nhận
    3 phiếu trong tháng = "xuất sắc"
[S] Cuối năm: GV đề xuất khen → PHT xét → HT duyệt (OBS-14)
    Capstone làm cả hai: P-31
```

AI không xếp hạng, không trao thưởng (AI-02).

## Hồ sơ phát triển (OBS-03)

```text
Điểm danh ─────────────┐
Sức khỏe + tăng trưởng ┤
Đánh giá ngày ─────────┼─► DevelopmentProfile (read model theo trẻ, theo thời gian)
Nhận xét định kỳ ──────┤
Khen thưởng ───────────┘
```

Không có mục theo dõi/can thiệp riêng (OBS-03, HLT-12).
