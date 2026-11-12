---
id: BUG-261108-curriculum-target-filter
type: bug
modules: [education-plan]
rules: [EDU-28, EDU-61]
status: open
date: 2026-11-08
keywords: [gợi ý nội dung, lĩnh vực con, mục tiêu đích, filter, stale]
similar_to: []
---

# BUG-261108-curriculum-target-filter — Mục tiêu đích không theo bộ lọc

## Symptom

User báo đổi lĩnh vực con trong lấy nội dung Bộ nhưng mục tiêu đã chọn ở nhánh trước vẫn còn. Chưa tái hiện runtime trong lượt specify.

## Điều kiện tái hiện

Theo user: mở lấy nội dung → chọn mục tiêu của lĩnh vực con A → đổi bộ lọc B → mục tiêu A vẫn còn. Cần fixture tổng hợp hai nhánh.

## Attempts — đã thử

| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Đọc EDU-28 và CurriculumGoalResponse/CurriculumContentResponse BE | Mục tiêu nguồn có expectedResultId/contents; nội dung độc lập mục C không có quan hệ nhóm | Chỉ xác định dữ liệu hiện có, chưa chứng minh nguyên nhân hoặc tái hiện UI |

## Root cause

Chưa rõ; chưa đọc source client hoặc kiểm runtime, chưa xác định lỗi BE/Web.

## Fix

Chưa triển khai. [Feature014](../../../specs/014-curriculum-cascade-suggestions/spec.md) ghi hành vi đích và ba câu trả lời user.

## Regression test

Chưa chạy. Cần ca đích A → đổi B → bỏ đích A, giữ dữ liệu đã thêm; kiểm cả lĩnh vực/nhóm và tải muộn.

## Ảnh hưởng

education-plan Web theo phản ánh; Mobile cần khảo sát. Chưa xác định dữ liệu lưu sai, không tự sửa dữ liệu.

## Lesson

Lọc nguồn và lọc mục tiêu đích đều phải theo ngữ cảnh; đặc tả chưa phải bằng chứng đã fix.

## Khảo sát thiết kế 08/11/2026

- Attempts2: đọc Web CurriculumGoalsModal.jsx:26/:29–33/:86–95 và curriculum.mjs:88 — đích lấy mọi goal, nguồn mới được lọc; confirm vẫn lấy checked từ toàn catalog. Đây là lỗ hổng tĩnh phù hợp phản ánh, chưa chứng minh bằng thao tác runtime.
- Attempts3: review source mock —327/327 source goals seed thiếu domain/sub/group IDs; adapter trả raw nên phải normalize tại boundary mock để thiết kế014 hoạt động cả mock/real, không tự gắn nguồn cho dòng tay.
- Attempts4: review live test fixture —hai legacy suite tạo TEAM/expect EDITING, không đủ nghiệm thu nhập cây năm; plan đổi sang live suite014 riêng SCHOOL_YEAR/policy hiện hành.
- Fix chưa thực hiện, status vẫn open; thiết kế/ma trận kiểm tại specs014. Không coi kiểm source/plan là regression RED→GREEN.

- Implement08/11 RED: Node curriculum.test.mjs23tests,20pass/3fail. Mock thiếu source parent IDs, goalTargets không trả actual sub/group, chưa có reconcile helper; giữ nguyên20regression cũ. Bắt đầu sửa đúng root cause này, chưa claim runtime fixed.
