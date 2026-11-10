# Implementation Plan: Lịch sử phiên bản

**Spec**: [spec.md](spec.md) · **Date**: 2026-11-03 · Chỉ CareNest_FE; không BE/DB/dependency.

## Technical Context
- Kế hoạch năm: `hooks/education-plan/useYearPlanAutosave.js` + `services/education-plan/yearPlanRecovery.js` (khóa `carenest:year-plan-recovery:<user>:<plan>:<slot>`, mỗi tab một slot). Tài liệu lớp: `hooks/education-plan/useManualSave.js` + `services/education-plan/planBackup.js` + `models/education-plan/planBackup.mjs` (khóa `carenest:plan-backup:<user>:<doc>`, 1 bản).
- UI kit: `SidePanel`, icon `History`, `.badge-dot`.

## Design
- **Model thuần** `models/education-plan/planHistory.mjs`: `upsertSnapshot(list, entry, max=2)` (cập nhật theo session, mới nhất trước, cắt còn 2); `outlineLines(doc, kind)` (năm: theo `recoveryText`; lớp: duyệt chuỗi chữ của nội dung); `diffLines(a, b)` (LCS theo dòng ⇒ same/add/del).
- **Tài liệu lớp**: một khóa chứa `{ entries: [...] }` (đọc được bản cũ 1 entry). Mỗi `markClean` mở session mới; `keepBackup` upsert entry của session; mốc không dùng được (mốc phiên bản đổi hoặc trùng nội dung) bỏ như cũ. Hook trả `drafts` và `restoreDraft(entry)`; bỏ `recovery`.
- **Kế hoạch năm**: giữ slot theo tab (= session). Ghi slot mới thì xóa slot cũ hơn để còn ≤2. Hook trả `drafts` (≤2, nội dung khác bản đang mở) và `restore(entry)`; bỏ chặn lưu/ghi khi có recovery; trang bỏ khóa form và banner. Mốc có `baseRevision` khác revision hiện tại ⇒ chỉ xem.
- **UI** `components/education-plan/VersionHistory.jsx`: nút + SidePanel; chọn mốc ⇒ bảng dòng có tô màu; Dùng bản này (khóa + lý do khi không dùng được). CSS module `ga-history-*` dùng token.

## Constitution / rules
Lưu tay EDU-22 ✔ · EDU-10 không đổi ✔ · không DB/API/dependency ✔ · không chữ "tự lưu" ✔ · UI kit dùng lại ✔.

## Progress log
- 2026-11-03: domain spec, spec (clarify = 6 câu Tiền), plan, data-model, tasks.
