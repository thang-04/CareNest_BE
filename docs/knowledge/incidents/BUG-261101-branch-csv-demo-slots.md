---
id: BUG-261101-branch-csv-demo-slots
type: bug
modules: [education-plan]
rules: [EDU-47]
status: fixed
date: 2026-11-01
keywords: [branch, CSV, demo, nhánh, nghỉ, lệch tuần]
similar_to: []
---
# BUG-261101-branch-csv-demo-slots — CSV demo chiếm tuần bởi dòng nghỉ

## Symptom

Preview CSV demo giữ tiền tố Nhánh n và lấy dòng nghỉ/ôn tập làm tên, tiêu thụ một tuần học; tên tiếp theo bị đẩy sang tuần khác. BE thật đã bỏ các dòng này nên hai chế độ lệch nhau.

## Điều kiện tái hiện

File CSV tổng hợp: Chủ đề/Chủ đề nhánh, tên có nhãn Nhánh n, dòng Nghỉ Tết hoặc Từ ngày … Ôn tập nghỉ lễ xen giữa hai tên thật; lớp có hai tuần học của chủ đề.

## Attempts

| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Review source mock/classPlanMockRepository.js và BranchFileTable | Mock lấy mọi tên không rỗng, BE bỏ nhãn/nghỉ; gap được chỉ ra trước E2E | Fixture mock phải có dòng nghỉ/nhãn như file trường, không chỉ tên sạch |
| 2 | Thêm test CSV có nghỉ/ôn tập, ngày bắt đầu có/không từ và nhãn Nhánh lặp số | RED: hai tuần nhận Nghỉ lễ/Ôn tập thay Một/Hai; sau đồng bộ parser GREEN | Chỉ tăng slot sau tên thật hợp lệ |

## Root cause

Mock CSV chưa đồng bộ diễn giải bảng EDU-47: chỉ kiểm ô rỗng rồi tăng vị trí tuần.

## Fix

Mock chuẩn hóa nhãn/loại dòng nghỉ giống BranchFileTable trước khi ghép và tăng vị trí tuần. Không thay lịch hoặc quy tắc production.

## Regression test

`CareNest_FE/tests/education-plan/import-branch-mock.test.mjs`: test mock bỏ dòng nghỉ/ôn tập và nhãn số nhánh như BE, không chiếm tuần; có RED/GREEN, bộ test mới 11/11 đạt tại thời điểm sửa parity. Full gate sau thay đổi cuối ghi tại quickstart feature008.

## Ảnh hưởng

Chỉ preview nhập nhánh chế độ demo CSV; chưa có dữ liệu thật bị sửa trong task. Endpoint production không có lỗi này.

## Lesson

Mock là BE giả nên phải dùng fixture có nhãn/ô tiếp diễn/dòng nghỉ giống parser production; cùng số dòng chưa đủ chứng minh cùng số tuần.
