---
id: CASE-261111-menu-ai-kcal-out-of-reach
type: edge-case
modules: [nutrition, ai-assistance]
rules: [NUT-09, NUT-18, NUT-24, AI-07]
status: open
date: 2026-11-11
keywords: [gợi ý thực đơn, thiếu năng lượng, NORM_BALANCE, KCAL_REACH, định lượng, định mức, feasible=false, điểm không đổi]
similar_to: []
---

# CASE-261111-menu-ai-kcal-out-of-reach — mọi ngày thiếu năng lượng vì dữ liệu, không vì cách chọn món

## Symptom
Gợi ý thực đơn tuần khối Nhà trẻ (bản sao DB local, tuần 16/11/2026, AI tắt): cả 5 ngày báo "Năng lượng thấp hơn định mức" (453–511 kcal so với 600–651), `feasible=false`; người duyệt đổi món mặn thì điểm vẫn 51.

## Điều kiện tái hiện
Định mức nhóm tuổi có mức năng lượng tối thiểu cao hơn tổng món nhiều năng lượng nhất của từng ô trong khung bữa (khung lấy theo bộ thực đơn mẫu: sáng 1 món, trưa 4, chiều 1). Ở bản sao: tối đa 534 kcal/ngày, định mức từ 600.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Nghi phương án theo quy tắc/bước sửa chọn món kém | Loại | Cộng món nhiều năng lượng nhất mỗi ô từ `dish_ingredients` × `foods` của nhóm tuổi vẫn dưới mức tối thiểu ⇒ không lưới nào đạt |
| 2 | Thêm dòng bảng kiểm `KCAL_REACH` nói rõ giới hạn dữ liệu | Dùng | Người duyệt biết phải sửa định lượng/danh mục/định mức, không sửa món |

## Root cause
Dữ liệu: định lượng món của nhóm tuổi quá thấp so với định mức đang hiệu lực (và món không khai dầu mỡ nên % năng lượng từ béo cũng thấp). Engine không tạo/sửa số liệu dinh dưỡng (NUT-05, AI-07) nên không tự bù được.

## Xử lý
`MenuCriteriaReport.kcalReach`: khi còn ngày chưa đạt và chứng minh được (tổng món cao nhất mỗi ô < min hoặc thấp nhất > max), bảng kiểm có dòng `KCAL_REACH` (`FAIL`); FE hiện câu này dưới bảng sửa tuần. Test `MenuSuggestionEngineTest.kcalReachOnlyReportsProvableDataLimits`. Còn mở: dữ liệu demo cần người có thẩm quyền chỉnh định lượng hoặc định mức; chỉ xét năng lượng, chưa có cận cho cơ cấu % đạm/béo/bột đường.

## Phòng tránh
Thấy mọi ngày cùng lệch một chiều ⇒ xem `KCAL_REACH` và so tổng món cao/thấp nhất mỗi ô với định mức trước khi sửa engine hay prompt.
