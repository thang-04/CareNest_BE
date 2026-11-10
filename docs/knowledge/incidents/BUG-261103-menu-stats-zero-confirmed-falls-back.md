---
id: BUG-261103-menu-stats-zero-confirmed-falls-back
type: bug
modules: [nutrition]
rules: [NUT-07, NUT-03]
status: fixed
date: 2026-11-03
keywords: [menu-statistics, MenuStatistics, headcountOf, childrenSource, CONFIRMED, ENROLLED, số suất 0, cả lớp vắng]
similar_to: []
---

# BUG-261103-menu-stats-zero-confirmed-falls-back — Thống kê thực đơn lấy sĩ số khi số suất đã chốt là 0

## Symptom
`POST /nutrition/menu-statistics` có `campusId`, không nhập `children`: cơ sở–ngày đã xác nhận suất nhưng nhóm tuổi 0 suất (cả lớp vắng, hoặc xác nhận lại về 0) ⇒ trả `childrenSource = ENROLLED`, `children` = sĩ số, tiền > 0 cho ngày bếp không nấu.

## Điều kiện tái hiện
Test luồng bán trú trên bản sao DB dev 03/11: chốt suất Lớp A1 = 1, mở lại điểm danh, cả 2 trẻ vắng, xác nhận lại ⇒ màn thống kê ngày 03/11 hiện "2 trẻ, 35.039 đ" kèm "Theo sĩ số đang học".

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Đọc `MealPlanServiceImpl.headcountOf` | Xác nhận | Điều kiện `confirmed > 0` coi 0 là "chưa chốt" |

## Root cause
`headcountOf` chỉ dùng số chốt khi tổng suất của nhóm > 0, nên 0 suất đã chốt rơi xuống nhánh sĩ số.

## Fix
Cơ sở–ngày có `MealCount` đã xác nhận ⇒ luôn dùng số chốt (kể cả 0). Regression: `MenuStatisticsIntegrationTest.confirmedZeroServingsIsNotReplacedByEnrolment`.
