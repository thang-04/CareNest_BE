---
id: CASE-261027-activity-media-positional-identity
type: edge-case
modules: [education-plan]
rules: [EDU-15, EDU-16]
status: fixed
date: 2026-10-27
keywords: [activity, media, reorder, positional-id, day-picks]
similar_to: []
---
# Hoạt động theo vị trí có thể đổi nghĩa sau khi sửa chủ đề

## Symptom
Review học liệu phát hiện ID goalId#index được tái dùng khi đổi thứ tự dòng. Ngày đã chọn #0/nội dung A có thể bị presenter lấy #0/nội dung B từ chủ đề mới.

## Điều kiện tái hiện
Chủ đề A/B, ngày chọn A#0; đổi chủ đề B/A rồi mở dạy học ngày. Dữ liệu giả trong FE learning-resources.test.mjs.

## Attempts
| # | Cách | Kết quả |
| --- | --- | --- |
| 1 | Thiết kế lọc chỉ ID | Review bác bỏ: ID positional không chứng minh cùng nội dung |
| 2 | Kiểm ID+goalId+saved text cho day pick; fingerprint toàn row khi gắn tệp | Test đạt, stale giữ nội dung ngày và không ghép media hiện tại |
| 3 | Fixture dán Word với NBSP/BOM | RED: Java isBlank/strip khác JS trim/whitespace, heading rỗng bị đếm; đổi regex whitespace cho parity, BE/FE fixture đạt |

## Root cause
FE classPlan.js themeActivities đánh ID theo số hoạt động xuất ra; heading rỗng không tạo hoạt động. ID theo vị trí ổn cho grid hiện hành nhưng không đủ cho binary binding lâu dài.

## Fix
Giữ ID/MT cũ; LearningResourcePolicy SHA256 row.activities và key tồn tại; day presenter matchDayActivities kiểm nội dung trước ghép media, stale báo kiểm tra lại. Không tự rewrite dữ liệu ngày cũ.

## Regression test
FE tests/education-plan/learning-resources.test.mjs: reorder và heading/plus parity. BE LearningResourcePolicyTest và EduClassDocServiceIntegrationTest: stale binding/rebind/private scope.
Fixture `pastedWordWhitespaceMatchesJsActivityParser`: NBSP/BOM trên heading/child, RED trước fix và GREEN sau fix; không đổi ID cũ hay nội dung người soạn.

## Ảnh hưởng
Chỉ feature học liệu mới. Không đổi lưu/duyệt hoặc schema JSONB tài liệu lớp.

## Lesson
ID theo vị trí phải đi cùng dấu nội dung khi tài nguyên có vòng đời dài hơn thao tác nhập hiện tại.
