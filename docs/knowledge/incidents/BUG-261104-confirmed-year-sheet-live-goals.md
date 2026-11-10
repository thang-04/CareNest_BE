---
id: BUG-261104-confirmed-year-sheet-live-goals
type: bug
modules: [learning-observation, education-plan]
rules: [OBS-28]
status: fixed
date: 2026-11-04
keywords: [year-end sheet, confirmed, snapshot, latest plan, frozen goals]
similar_to: []
---
# Confirmed year-end sheet still renders the latest plan's goal list

## Evidence
Reviewed BE 29e1e5a. YearEndSheetServiceImpl.StoredGoal stores only lineage, result and manual flag (line 64). freeze persists these cells (147–150), but view queries yearGoals again (206) and iterates that live goal list (212). ThemeEvaluationRepository.findYearGoals selects the newest approved plan version (87–88). The list, wording and domains are not frozen.

An isolated probe against the actual compiled service with mocked repositories and synthetic records reproduced one confirmed sheet changing from one goal to two, including changed wording, when the latest-plan query changed. Output: REPRODUCED. Helper .git/YearEndFrozenAudit.java; this is a service-level controlled probe, not a live DB/HTTP test.

## Impact / proposal
OBS-28 says confirmation freezes the sheet. Historical display and print can change after a new plan version adds/removes/renames goals, despite the saved cells staying intact. Store or pin the complete goal metadata/version at confirmation; confirmed rendering must use that snapshot. Consider the other printed metadata separately; only goal-list/text drift was reproduced here.

## Attempts
Initial Java source-launcher probe could not access package-private rules due to its separate classloader; compiling the helper into an ignored directory resolves that setup issue. A nested Mockito stubbing call was moved outside the thenReturn argument. The final probe reproduced the behavior successfully. No product source was changed.

## Fix
freeze lưu cả số MT, câu MT, lĩnh vực và nguồn "Theo chủ đề" cho mọi MT của phiếu; phiếu đã xác nhận hiển thị từ bản chốt (bản chốt cũ chỉ có kết quả thì vẫn lấy danh sách MT hiện hành). Test: ThemeEvaluationIntegrationTest — sau khi có bản kế hoạch năm mới đổi câu MT, thêm MT, phiếu đã xác nhận giữ nguyên 3 MT và câu cũ.
