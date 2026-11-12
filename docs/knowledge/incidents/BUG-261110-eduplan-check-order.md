---
id: BUG-261110-eduplan-check-order
type: bug
modules: [education-plan]
rules: [EDU-73, EDU-40, EDU-39, EDU-52]
status: "fixed"
date: 2026-11-10
keywords: [retry, requestId, return, week-alignment, validation-order]
---

# BUG-261110 — Thứ tự kiểm sai ở luồng kế hoạch năm

**Module:** education-plan · **Ngày:** 10/11/2026 · **Phát hiện:** sửa fixture test spec 003 (FR-017)

## 1. Thử lại "trả lại" bị 7384
- Triệu chứng: PHT trả lại kế hoạch rồi gửi lại cùng `requestId` (mạng chập chờn) ⇒ 403 7384 "Bản này chưa ban hành".
- Nguyên nhân: `returnYearPlan` gọi `loadPlanForUpdate` (có `requireViewable`, EDU-73) trước khi nhận ra lần thử lại; kế hoạch đã
  về Nháp nên người duyệt không còn quyền xem.
- Sửa: `loadPlanForUpdate(user, id, false)` → kiểm `requestId` trùng `lastSaveRequestId` → mới `requireViewable`.
- Test: `EduReviewRoundIntegrationTest.reviewerMustWaitBeforeReturning`.

## 2. Chủ đề lệch tuần trả mã chung
- Triệu chứng: chủ đề không trọn Thứ 2–6 khi gửi trả `EDUCATION_CALENDAR_INVALID` thay vì `THEME_WEEK_ALIGNMENT` (EDU-40, có tên chủ đề).
- Nguyên nhân: `validateSubmissionSchedule` kiểm thời lượng (`EduTeachingCalendar.requireWholeWeeks`) trước `requireThemeWeeks`.
- Sửa: gọi `requireThemeWeeks` ngay sau kiểm đủ ngày trong `validateSubmissionSchedule`.
- Test: `EduYearPlanServiceIntegrationTest.themesMustCoverWholeWeeksWithoutOverlap`.

Liên quan: EDU-52 (Tiền 10/11/2026) — gửi và ban hành dùng chung `requireReadyToIssue`.
