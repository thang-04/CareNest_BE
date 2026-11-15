---
id: BUG-261109-year-team-approval-identity
type: bug
modules: [education-plan]
rules: [EDU-55, EDU-62]
status: open
date: 2026-11-09
keywords: [kế hoạch năm học, kế hoạch tổ, chờ duyệt, tên loại, phiên bản]
similar_to: []
---

# Hồ sơ năm chờ HT duyệt mang tên loại kế hoạch tổ

## Symptom

User báo nộp kế hoạch năm học nhưng trang HT chờ duyệt hiển thị thành kế hoạch tổ. Ảnh kèm cho thấy bảng phiên bản; ảnh không tự chứng minh nguyên nhân nhãn chờ duyệt. User yêu cầu phiên bản chỉ hiện ở năm, tổ tập trung mapping.

## Điều kiện tái hiện

Theo báo cáo user: PHT lập/gửi năm theo nhóm tuổi → HT mở chờ duyệt → quan sát loại/tên và đích mở hồ sơ. Chưa tái hiện runtime trong lượt đặc tả.

## Attempts — đã thử

| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Đọc entity, V90, service tạo/chọn nguồn và mapper list | Cả năm/tổ chung bảng, phân biệt level; có title và nguồn riêng | Chung bảng chưa đủ chứng minh nguyên nhân nhãn sai |
| 2 | Đối chiếu yêu cầu/ảnh và đọc logic versionFlags của Web | Changed/removed cần xử lý, mục mới có nhãn riêng; có ngoại lệ chủ đề kết thúc | Không áp màu đỏ toàn bộ một cách máy móc; cần chốt delta cho tổ |

## Root cause

Chưa rõ. Chưa có regression/runtime chứng minh; không kết luận lỗi DB hay gộp bảng là nguyên nhân trực tiếp.

## Fix

Chưa sửa source. Đã ghi yêu cầu và quyết định mới trong [feature015](../../../specs/015-separate-year-team-plans/spec.md), EDU-62. Đích tách bảng là chỉ đạo user, khác với kết luận nguyên nhân bug.

## Regression test

Chưa chạy; lúc triển khai phải kiểm gửi năm → HT chờ duyệt, gửi tổ → PHT theo cấu hình, nhãn và đích mở đúng cả hai, bảo toàn lịch sử sau tách.

## Ảnh hưởng

Web danh sách chờ duyệt/list/detail, backend định danh nguồn/loại; thiết kế DB/FK và consumers lớp sẽ được đánh giá ở plan. Chưa sửa FE/APP/API/DB runtime.

## Lesson

Phân biệt loại hồ sơ, tên riêng, nguồn năm và bản trước trong lịch sử; không suy loại từ title hoặc tên bảng cũ.
