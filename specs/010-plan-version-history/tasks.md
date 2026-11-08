# Tasks: Lịch sử phiên bản

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

- [ ] T001 Model `planHistory.mjs` (upsertSnapshot ≤2, outlineLines năm/lớp, diffLines) + unit test (FR-004, FR-006)
- [ ] T002 Tài liệu lớp: `planBackup` lưu `{entries}`, đọc bản cũ; `useManualSave` session/drafts/restoreDraft, bỏ recovery (FR-006, US2)
- [ ] T003 Kế hoạch năm: `yearPlanRecovery` liệt kê ≤2 mốc và cắt slot cũ; `useYearPlanAutosave` drafts/restore(entry), bỏ chặn khi có recovery (FR-006, US3.1)
- [ ] T004 `VersionHistory.jsx` (nút + chấm + SidePanel + xem khác biệt + Dùng bản này) + CSS module (FR-002..005)
- [ ] T005 Gắn vào GoalFormPage, BranchPlanPage, ClassThemePlanPage, WeekPlanPage; bỏ DraftRecoveryNotice và khóa form (FR-001)
- [ ] T006 Cập nhật Playwright ST-03, ST-03b, ST-10, ST-11 dùng Lịch sử phiên bản; unit test cũ plan-backup / year-plan-recovery (SC-001, SC-002)
- [ ] T007 FE verify, Playwright kế hoạch giáo dục với BE thật, ảnh 1280/375
