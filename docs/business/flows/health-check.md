# Flow — Theo dõi sức khỏe định kỳ

Module: health (→ learning-observation đọc cho DevelopmentProfile). Rule: HLT-*, AI-*.

```text
[B] Đợt đo định kỳ (~3 tháng/lần — HLT-01; không hard-code chu kỳ)
[S] Nhập HealthRecord theo lớp (chiều cao, cân nặng, tình trạng, ghi chú)   người nhập: PENDING
      ↓
[S] Tính xác định: trend, chênh lệch so với lần trước, biểu đồ (HLT-02)
    So với ngưỡng tham chiếu: PENDING P-13 — chưa chọn chuẩn ⇒ chưa gắn cờ "bất thường"
      ↓ tùy chọn
[S] AI interpretation DRAFT: giải thích số liệu, highlight trend, điểm cần chú ý
    KHÔNG chẩn đoán, KHÔNG khuyên điều trị (HLT-04)
      ↓
[S] Nhân viên có quyền review/sửa/duyệt (HLT-05) → hiển thị chính thức
      ↓
DevelopmentProfile đọc measurement + trend + interpretation APPROVED
Phụ huynh xem theo policy (PAR-02)
```

Dị ứng không thuộc module này (thuộc child — HLT-06).
