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
Structured records trong kỳ (ngày/tuần/tháng — PENDING P-12)
      ├─ Baseline: template/rule-based summary (luôn có, không AI)
      └─ Tùy chọn: AI narrative draft (input tối thiểu, bí danh — AI-04)
      ↓
Summary DRAFT → GV review → GV sửa → GV APPROVED → BGH xem
      ↓
Phụ huynh xem nếu policy cho phép (PAR-02)
```
