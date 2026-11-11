---
id: BUG-261108-theme-date-column-overflow
type: bug
modules: [education-plan]
rules: [EDU-60]
status: fixed
date: 2026-11-08
keywords: [date, column, overflow, flex-wrap, nowrap]
similar_to: []
---
# Ngày chủ đề và chủ đề nhánh tràn cột

## Symptom / Điều kiện tái hiện
User cung cấp ảnh ngày cuối khoảng tràn sang cột viết tắt ở bảng phần3 kế hoạch tổ. Bảng năm/tổ dùng chung renderer ngày; cột nhỏ hơn tổng chiều rộng hai ngày và dấu nối sẽ tái hiện. Mong đợi: đủ ngày/tháng/năm, tự xuống dòng khi hẹp, không cắt chữ. Không có dữ liệu cần sửa lại.

## Root cause
CSS `.ga-branch-date-range` ép `flex-wrap: nowrap`, endpoint không co và trigger ép `white-space: nowrap`. Khi bảng dùng table-layout fixed, text vẫn giữ chiều rộng tự nhiên nên tràn ô. Ngày đơn lẻ không lộ lỗi nếu cột đủ rộng; thêm cột kế thừa làm khoảng ngày thiếu chỗ.

## Attempts / Fix
1. Thêm kiểm browser thật: duyệt các text rect trong mọi range ngày, so với biên td; RED có bốn ngày kết thúc vượt ô, gồm chủ đề và nhánh ở cả năm/tổ.
2. Bỏ hai override nowrap, dùng flex-wrap có sẵn; endpoint được co, trigger cho phép wrap khi ngày đơn lẻ cũng không đủ chỗ. Không đổi format/ngày lưu, API hay DatePicker.
3. Chạy lại browser fixture ở1440/698/390: GREEN, mọi ngày nằm trong cột, tick/select và annual editor vẫn hoạt động.

## Regression / Ảnh hưởng
`CareNest_FE/tests/education-plan/team-plan-browser.mjs`: kiểm text geometry với CSS và component thật, gồm năm editable và tổ readonly, ngày chủ đề/nhánh. Log TEMP/carenest013-date-red.log và carenest013-date-green.log. Chỉ sửa CSS phần giáo dục Web; không ảnh hưởng APP, dữ liệu, quyền hoặc format API.
