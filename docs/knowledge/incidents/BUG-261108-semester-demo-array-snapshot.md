---
id: BUG-261108-semester-demo-array-snapshot
type: bug
modules: [learning-observation]
rules: [OBS-25]
status: fixed
date: 2026-11-08
keywords: [MismatchedInputException, SemesterAssessmentDtos, START_ARRAY, result_snapshot]
similar_to: []
---

# Script demo tạo bảng học kỳ đã xác nhận với snapshot sai cấu trúc

## Symptom

GET đánh giá học kỳ trả 500. Log người dùng có `Cannot deserialize value of type SemesterAssessmentDtos$Response from Array value` tại SemesterAssessmentServiceImpl.build.

## Điều kiện tái hiện

Chạy bản cũ scripts/dev/seed-test-three-modules.sql, mở học kỳ I. Script tạo result_snapshot='[]' cùng confirmed_at và confirmed_by khác null.

## Root cause

Script chốt bảng bằng mảng JSON rỗng, trong khi nhánh confirmed đọc snapshot theo DTO Response dạng đối tượng. Dữ liệu do service xác nhận đúng không có lỗi này. Test trước đó chỉ xác nhận qua service, chưa chạy block SQL seed.

## Attempts

| Cách thử | Kết quả |
| --- | --- |
| Đối chiếu stack người dùng với nhánh đọc snapshot | JSON array không đọc được thành Response |
| Query metadata DB dev PostgreSQL local | 10 bảng snapshot=[] có marker dữ liệu dev |
| Thêm regression chạy SQL seed vào DB test rồi GET | Lần đầu fixture thiếu ngày học kỳ; bổ sung ngày vào fixture trước seed |
| Sao lưu và sửa dữ liệu dev hiện có | Sao lưu CSV trong TEMP, chỉ 10 bản snapshot=[] và note đúng marker được trả về nháp; số bản sai còn lại bằng 0 |

## Fix

Block seed học kỳ I tạo nháp với snapshot, người xác nhận, ngày xác nhận để null. Snapshot chỉ được service chốt khi giáo viên xác nhận. Không dùng fallback tính lại kết quả cho bảng đã xác nhận thật.

## Regression test

ThemeEvaluationIntegrationTest.seededSemesterBoardIsReadableDraft chạy block SQL seed hai lần, kiểm GET trả DRAFT, confirmedAt=null và chỉ có một bảng cho lớp/học kỳ.

## Dữ liệu dev đã xử lý

Backup tại `%TEMP%/carenest-semester-repair-20261108-210132/semester-invalid-demo.csv`. Transaction sửa theo cả snapshot='[]', confirmed_at khác null và note marker `Bảng học kỳ I (dữ liệu dev)`, đồng thời tăng version.
