---
id: CASE-261028-year-plan-import-first-sub-reparent
type: edge-case
screens: [education-plan]
modules: [education-plan]
rules: [EDU-09, EDU-24]
status: fixed
date: 2026-10-28
keywords: [import goals, hierarchy, first sub, nestUnderFirstSub, toRows, đổi cha]
similar_to: []
---
# Nhập sub đầu tiên có thể đổi cha mục tiêu cũ khi mở lại

## Symptom

Tái hiện bằng helper hiện có với dữ liệu giả, chưa triển khai tính năng import mới: candidate [goal target, sub source, goal source] sau toRows trở thành [sub source, goal target, goal source]. Goal cũ đang ở cấp lĩnh vực bị gán vào sub nguồn.

## Điều kiện tái hiện

Lĩnh vực đích đã có goal/group nhưng không có sub; thuật toán ghép mới định append nguồn có sub đầu tiên. GoalFormPage gọi toRows khi formOf; GoalTables cũng normalize khi cập nhật.

## Attempts — đã thử

| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Review thiết kế append heading/goal, đối chiếu goalTable.js:17–25 và GoalFormPage.jsx:60–62 | Phát hiện normalize có thể đổi cha target dù input không bị mutate | Deep-compare đầu vào không đủ |
| 2 | Chạy toRows trên fixture ba hàng bằng node test loader hiện có | before: target,source-sub,source-goal; after: source-sub,target,source-goal | Cần kiểm cả round-trip và context của row cũ |

## Root cause

CareNest_FE/src/models/education-plan/goalTable.js:17–25 chủ động đưa sub đầu lên đầu để các row trước nó nằm trong sub. Đây là invariant hiện hành của bảng; copy heading mới vào flat rows phải tôn trọng nó. Không coi normalizer là lỗi để tự sửa toàn cục.

## Fix

Đã triển khai guard trong CareNest_FE/src/models/education-plan/importGoals.mjs. Helper kiểm thử candidate qua normalize/reopen rồi đối chiếu path row cũ và mới; mục nhập làm đổi context không biểu diễn được phải báo unavailable và không áp candidate. Không tạo tên cha giả hoặc đổi helper dùng chung.

## Regression test

Phase 2 feature003: source/target không mutate; target có goal/group không-sub + source có sub; ghép → normalize → context row cũ giữ nguyên hoặc incoming được báo unavailable. Bao phủ cả đích rỗng, đích đã có sub và nguồn không có cha. Test tại CareNest_FE/tests/education-plan/import-goals.test.mjs đã đạt trong 15/15 ca helper và 59/59 suite giáo dục; chưa thay thế E2E thật.

## Lesson

Với cây biểu diễn bằng các dòng heading liên tiếp, cần kiểm semantic context sau mọi bước normalize, không chỉ dữ liệu ngay sau ghép.
