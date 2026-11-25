---
id: BUG-261123-leave-overlap-skipped-day
type: bug
modules: [attendance]
rules: [ATT-05b]
status: fixed
date: 2026-11-23
keywords: [BUG-015, trùng đơn nghỉ, ngày bỏ qua, giờ chốt]
---

# Kiểm trùng đơn trước khi loại ngày không áp dụng

## Symptom

Đơn từ hôm nay tới ngày mai gửi sau giờ chốt chỉ áp dụng ngày mai. Gửi lại riêng hôm nay báo trùng đơn dù ngày đó bị bỏ qua.

## Root cause

createLeaveRequest kiểm toàn bộ khoảng ngày thô trước vòng loại ngày nghỉ và ngày đã khóa.

## Fix

Tính applied/skipped trước; không có ngày áp dụng trả LEAVE_NO_SCHOOL_DAY. Chỉ kiểm trùng trên ngày thực sự áp dụng và effectiveTo của đơn còn hiệu lực. Giữ khóa lớp và kiểm ngày quá khứ/khoảng năm/số ngày.

## Regression

LeaveRequestIntegrationTest.afterCutoffTodayIsKeptAndLeaveAppliesFromNextSchoolDay thêm ca gửi lại riêng hôm nay sau đơn đã bỏ qua ngày đó. 13/13 test LeaveRequestIntegrationTest đạt, không skip. Chưa kiểm UI phụ huynh; APP ngoài phạm vi.
