---
id: BUG-261101-meal-substitute-reset-on-attendance
type: bug
modules: [nutrition, attendance]
rules: [NUT-02, NUT-04, ATT-07]
status: fixed
date: 2026-11-01
keywords: [MealCount, replaceClassFigures, substitute, suất thay thế, điều chỉnh, markAttendanceChanged, ATTENDANCE_CHANGED, meal-counts/adjustments]
similar_to: []
---

# BUG-261101-meal-substitute-reset-on-attendance — Suất thay thế PHT điều chỉnh bị xóa im lặng khi điểm danh lớp đổi

## Symptom
PHT điều chỉnh `substitute` của lớp qua `POST /meal-counts/adjustments`; sau đó GV sửa điểm danh lớp đó (mở lại, ATT-08) ⇒ số suất thay thế về 0, không dòng lịch sử nào nói điều chỉnh bị bỏ.

## Điều kiện tái hiện
Sổ báo ăn đã tổng hợp, có điều chỉnh `substitute > 0`, rồi `AttendanceChangedEvent` cho lớp đó (review E2E 01/11 FLOW-008, đọc tĩnh `MealCount.java`).

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Đọc code theo report | Xác nhận | `replaceClassFigures(..., normal)` gọi `setFigures(..., 0)` |

## Root cause
`MealCount.replaceClassFigures` ghi đè cả dòng của lớp bằng số tính từ điểm danh và hằng `substitute = 0`; so sánh `sameAs(..., 0)` cũng coi substitute đã điều chỉnh là "khác" nên luôn ghi đè.

## Fix
`replaceClassFigures` giữ `substitute` của dòng đang làm việc; `adjust` thay bằng `adjustSubstitute` (NUT-02: không sửa tay suất thường; `normal` khác số hiện tại ⇒ 409 `6008`).

## Regression test
`MealCountIntegrationTest.attendanceChangeKeepsAdjustedSubstitute`, `MealCountIntegrationTest.leadersAdjustOnlySubstituteServings`.

## Ảnh hưởng
FE gửi `normal: null`; client gửi `normal` bằng số đang thấy vẫn chạy.

## Lesson
Số do người điều chỉnh và số suy ra từ nguồn (điểm danh) phải là trường riêng; tính lại chỉ ghi trường suy ra.
