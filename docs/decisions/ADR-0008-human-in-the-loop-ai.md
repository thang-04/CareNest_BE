# ADR-0008 — Human-in-the-loop cho mọi output AI

- Status: ACCEPTED
- Date: 2026-10-03
- Liên quan: AI-01..03, NUT-08, HLT-05, OBS-05

## Bối cảnh
AI hỗ trợ gợi ý thực đơn, diễn giải sức khỏe, nháp nhận xét. AI không được tự ra quyết định hay tạo dữ liệu chính thức.

## Quyết định
```text
Authoritative data → Deterministic rules → AI DRAFT → Human review/edit → APPROVED (official)
```
- Output AI lưu DRAFT ở module nghiệp vụ (`source=AI`, model, thời điểm), validate schema + rule xác định trước khi lưu.
- Chỉ người có permission tương ứng chuyển sang APPROVED; ghi audit.
- Mọi luồng có baseline không AI (nhập tay, template).

## Hệ quả
Không endpoint nào cho phép AI ghi trực tiếp trạng thái chính thức. Test: AI tắt ⇒ luồng vẫn hoàn thành.
