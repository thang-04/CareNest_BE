# Flow — Quan sát hằng ngày, hồ sơ phát triển, nhận xét tổng hợp

Module: learning-observation (đọc attendance, nutrition, health, education-plan; dùng ai-assistance). Rule: OBS-*, AI-*, EDU-19. ADR: 0006, 0008.

## Quan sát hằng ngày

```text
[S] Admin/BGH cấu hình ObservationCriteria + option (danh sách: PENDING P-11)
[S] GV nhập Observation cho cả lớp: chọn option theo tiêu chí + ghi chú tùy chọn (OBS-01)
    Mục tiêu UX: nhập nhanh cho 20–25 trẻ, dữ liệu nhất quán để tổng hợp
```

## Ngữ cảnh hoạt động và đánh giá theo MT (ADR-0006, OBS-07, EDU-19)

```text
[S] Giáo án tuần/ngày của lớp đã duyệt (education-plan): mỗi ô (giờ, ngày) có hoạt động và MT của kế hoạch năm
[S] Đánh giá hằng ngày: mỗi giờ có MT là một cột, GV chấm Đạt / Chưa đạt cho từng trẻ
      Lớp chưa có giáo án duyệt ⇒ khóa phần chấm theo MT
```

Không import GoKids, không nhập metadata hoạt động riêng. Luồng soạn, duyệt giáo án: `docs/business/flows/education-plan.md`.

## Hồ sơ phát triển

```text
Attendance history ─┐
Meal history ───────┤
Health + trend ─────┤
Observation ───┼─► DevelopmentProfile (read model theo trẻ, theo thời gian — OBS-03)
Đánh giá theo MT (giáo án đã duyệt) ┤
Assessment, FollowUp ─┘        (biểu mẫu PENDING P-16b)
```

## Nhận xét tổng hợp (OBS-04, OBS-05)

```text
Structured records trong kỳ (tuần theo trẻ + cuối kỳ; tóm tắt lớp hằng ngày — OBS-06)
      ├─ Baseline: template/rule-based summary (luôn có, không AI)
      └─ Tùy chọn: AI narrative draft (input tối thiểu, bí danh — AI-04)
      ↓
Summary DRAFT → GV review → GV sửa → GV APPROVED → gửi BGH
      ↓
Phụ huynh xem nếu policy cho phép (PAR-02, P-12)
```

## Khen thưởng — PROPOSED (OBS-10)

```text
Đánh giá hằng ngày trong tuần/tháng ─► [S] gợi ý trẻ đạt "phiếu bé ngoan" (rule theo tiêu chí — P-11)
                                     ─► GV/BGH xem, chỉnh, quyết định ─► lưu kết quả; cuối năm: "bé ngoan toàn diện"
```

AI chỉ có thể soạn lời nhận xét; không tự xếp hạng hay trao thưởng (AI-02).
