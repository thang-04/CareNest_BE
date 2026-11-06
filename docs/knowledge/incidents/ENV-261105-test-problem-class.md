---
id: ENV-261105-test-problem-class
type: env
modules: [child]
rules: []
status: workaround
date: 2026-11-05
keywords: [Unresolved compilation problems, TestPermissions, clean, test-classes]
similar_to: []
---
# ENV-261105-test-problem-class — bytecode test báo lỗi biên dịch lúc chạy

## Symptom
Maven qua testCompile nhưng khi chạy test xuất hiện `java.lang.Error: Unresolved compilation problems: TestPermissions cannot be resolved` trong `ChildEnrollmentIntegrationTest.setUp`.

## Điều kiện tái hiện
Windows, IDE đang mở, nhiều lần sửa test và chạy Maven tăng dần. Đây là bytecode test đã có lỗi, không phải exception nghiệp vụ của tính năng ảnh.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Sửa test rồi chạy test tăng dần | Lỗi lúc chạy vẫn trỏ TestPermissions đã import đúng | Không tiếp tục đổi code nghiệp vụ theo lỗi bytecode |
| 2 | `mvnw.cmd -q clean -Dtest=ChildPhotoServiceImplTest,ChildPhotoControllerTest,ChildEnrollmentControllerTest,ChildEnrollmentIntegrationTest,PickupServiceImplTest test` | 95 test đạt, không skip | Xây lại test-classes loại bỏ artifact lỗi |

## Root cause
Bytecode test cũ/lỗi trong output build; clean rồi biên dịch lại giải quyết. IDE ghi đồng thời target là giả thuyết, chưa chứng minh; không khẳng định đã xác định tác nhân tạo bytecode.

## Fix
Workaround là clean build trước khi chạy lại, tránh sửa source trong lúc Maven biên dịch. Không sửa IDE settings, dependency hoặc xóa test để bỏ qua lỗi.

## Regression test
95 kiểm thử có PostgreSQL Testcontainers đã chạy lại đạt; lỗi môi trường chưa có cách tái hiện ổn định để viết regression riêng.

## Ảnh hưởng
Giới hạn bằng chứng kiểm thử khi xảy ra; không ảnh hưởng contract/runtime bản build sạch.

## Lesson
Phân biệt compiler error thực và problem-class phát nổ lúc chạy; giữ log ngắn và làm sạch artifact khi có bằng chứng phù hợp.
