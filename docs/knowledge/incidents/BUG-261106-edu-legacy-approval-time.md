---
id: BUG-261106-edu-legacy-approval-time
type: bug
modules: [education-plan]
rules: [EDU-54]
status: fixed
date: 2026-11-06
keywords: [Cannot read stored approval history, DateTimeParseException, approvals, legacy timestamp]
similar_to: [BUG-261106-edu-approval-json-fail-open]
---

# BUG-261106-edu-legacy-approval-time — Không đọc được hồ sơ có lịch sử duyệt cũ

## Symptom
Đọc danh sách hồ sơ lớp lỗi 500 khi lịch sử duyệt chứa timestamp cũ theo giờ trường:
```text
Cannot read stored approval history
DateTimeParseException: Text '2026-11-04 06:28' could not be parsed at index 10
```

## Điều kiện tái hiện
Gọi `GET /education-plans/class-docs?schoolYearId=...` khi một hồ sơ có approvals JSON chứa `at: "2026-11-04 06:28"`. Không cần dữ liệu người dùng thật.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Thêm regression `legacyLocalApprovalTimestampIsReadAsSchoolTime` trước khi sửa | RED: cùng `DateTimeParseException` tại `Instant.parse` | Xác nhận lỗi nằm ở bộ đọc timestamp, không phải JSONB hoặc truy vấn danh sách |
| 2 | Chạy full verify sau khi format service | 1.130 test, 0 fail/error/skip; ArchUnit và snapshot đạt; Spotless chỉ báo file regression test chưa format | Full test suite đã chạy; cần format test rồi chốt formatter trên source cuối |
| 3 | Format regression test và chạy `node scripts/verify.mjs --quick` | PASS: 19 test, 0 fail/error/skip; ArchUnit và Spotless đạt | Quick map cả service test và kiểm soát định dạng sau lần sửa cuối |

## Root cause
`EduClassDocServiceImpl` phiên bản cũ ghi `at` bằng `LocalDateTime.now(clock.withZone(Asia/Ho_Chi_Minh)).format("yyyy-MM-dd HH:mm")`. `V82__harden_edu_workflow_cutover.sql` bổ sung `round` nhưng giữ nguyên timestamp lịch sử. Parser mới tại `EduWorkflowServiceImpl.parseApprovals` chỉ gọi `Instant.parse`, nên dữ liệu hợp lệ theo định dạng cũ ném lỗi. Lỗi lộ ra sau cutover EDU-54 khi GET hồ sơ gọi parser chung.

## Fix
`EduWorkflowServiceImpl` đọc ISO `Instant` trước, sau đó hỗ trợ định dạng legacy theo `Asia/Ho_Chi_Minh`. Timestamp sai định dạng tiếp tục fail-closed để không làm rỗng hoặc bỏ mất lịch sử duyệt; không cần cập nhật dữ liệu trong DB.

## Regression test
`EduWorkflowSerializationTest.legacyLocalApprovalTimestampIsReadAsSchoolTime` tái hiện RED trước sửa và xác nhận `2026-11-04 06:28` chuyển thành `2026-11-03T23:28:00Z` sau sửa.

## Ảnh hưởng
Module education-plan: đọc danh sách/chi tiết và thao tác tiếp hồ sơ lớp hoặc kế hoạch năm có lịch sử cũ. API/FE/APP không đổi. Không có dữ liệu cần sửa lại; hồ sơ đã duyệt giữ nguyên.

## Lesson
Khi nâng cấp parser dữ liệu JSONB lâu năm, phải giữ tương thích định dạng mà writer cũ đã lưu; migration thêm trường không đồng nghĩa dữ liệu lịch sử đã được chuẩn hóa.
