# Flow — Quan sát hằng ngày, hồ sơ phát triển, nhận xét tổng hợp

Module: learning-observation (đọc attendance, nutrition, health; dùng ai-assistance). Rule: OBS-*, AI-*. ADR: 0006, 0008.

## Quan sát hằng ngày

```text
[S] Admin/BGH cấu hình ObservationCriteria + option (danh sách: PENDING P-11)
[S] GV nhập Observation cho cả lớp: chọn option theo tiêu chí + ghi chú tùy chọn (OBS-01)
    Mục tiêu UX: nhập nhanh cho 20–25 trẻ, dữ liệu nhất quán để tổng hợp
```

## Ngữ cảnh hoạt động — OPEN DECISION (ADR-0006)

AI/nhận xét thiếu ngữ cảnh nếu không biết hoạt động dự kiến, nội dung đã dạy, mục tiêu. Nguồn ActivityContext chưa chốt:
A) CareNest quản lý tối thiểu lịch hoạt động tuần · B) import/sync từ GoKids · C) GV/admin nhập metadata cần thiết.
Model `ActivityContext` thiết kế **không phụ thuộc nguồn** (`source = MANUAL | IMPORT | GOKIDS`). Không implement soạn/duyệt giáo án (OBS-08).

## Hồ sơ phát triển

```text
Attendance history ─┐
Meal history ───────┤
Health + trend ─────┤
Observation ───┼─► DevelopmentProfile (read model theo trẻ, theo thời gian — OBS-03)
ActivityContext/Participation ┤
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
