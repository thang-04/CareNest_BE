---
id: BUG-261101-e2e-goal-id-domains
type: bug
modules: [education-plan]
rules: [EDU-24, EDU-46]
status: fixed
date: 2026-11-01
keywords: [e2e, identity, rowId, contentId, sequence, false positive, import]
similar_to: []
---
# BUG-261101-e2e-goal-id-domains — Test so ID khác loại thực thể

## Symptom

E2E nhập mục tiêu trên DB mới báo ID bị tái sử dụng dù dữ liệu được tạo mới đúng. Lượt hồi quy đầu: 34 passed, 1 failed, 4 chưa chạy do suite serial.

## Điều kiện tái hiện

DB kiểm thử mới, sequence mục tiêu và nội dung có giá trị trùng nhau. Test year-plan-import ghép source row IDs và content IDs vào cùng một Set rồi cấm mọi trùng số ở đích.

## Attempts

| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Hồi quy trên PostgreSQL riêng, đối chiếu schema và assertion | Trùng số giữa hai loại thực thể, không phải dùng lại ID cùng loại | ID chỉ có nghĩa trong miền thực thể, không phải UUID toàn cục |
| 2 | Tách sourceRowIds/sourceContentIds, vẫn kiểm target rows/contents mới so với nguồn cùng loại | Lượt cuối year-plan-import 17/17 đạt với BE thật | Không bỏ assertion nhận diện, chỉ so đúng miền |

## Root cause

Test CareNest_FE/tests/e2e/system/year-plan-import.spec.mjs so ID giữa hàng mục tiêu và nội dung, trong khi edu_goal_contents dùng identity riêng. Đây là lỗi test, không có dữ liệu production bị đổi sai.

## Fix

Giữ hai tập ID riêng; hàng đích không trùng hàng nguồn, nội dung đích không trùng nội dung nguồn. Vẫn kiểm số mục tiêu, nội dung, dấu tích, hierarchy và thao tác lưu.

## Regression test

EDU-24/22 double confirm imports once; only manual Save persists hierarchy and preserves themes/breaks/ticks. RED tại target/feature008-e2e-all.log; GREEN trong target/feature008-e2e-final-goal17.log (17 passed).

## Ảnh hưởng

Chỉ kiểm thử; không nới ràng buộc dữ liệu hoặc sửa source ứng dụng. Chạy trên DB synthetic task-owned, không sửa DB đang dùng.

## Lesson

So sánh/ghép dữ liệu theo cặp loại thực thể + ID; không giả định hai bảng identity độc lập có ID toàn cục không trùng.
