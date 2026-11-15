---
id: BUG-261105-enrollment-photo-projection
type: bug
modules: [child, identity-access, attendance]
rules: [CHD-02, AUTH-20, PCK-02]
status: fixed
date: 2026-11-05
keywords: [photo, portrait, pending placement, accountStatus, smsStatus, WebP, projection]
similar_to: []
---
# BUG-261105-enrollment-photo-projection — ảnh và quyền đọc hồ sơ

## Symptom
Review trong lúc phát triển Spec012 phát hiện: PHT đọc trẻ có lớp ở campus khác được nhưng trẻ chờ xếp lớp lại bị chặn; endpoint hồ sơ mới có thể trả metadata quản trị tài khoản/SMS cho người chỉ xem hồ sơ; projection mock cho giáo viên làm mất ảnh phụ huynh. File WebP chỉ có header VP8X cũng qua kiểm cấu trúc ban đầu.

## Điều kiện tái hiện
Dữ liệu giả: PHT còn phân công ở một campus, trẻ chờ xếp lớp ở campus khác; giáo viên có quyền đọc hồ sơ; guardian có photoUrl và metadata tài khoản. File 30 byte RIFF/WEBP/VP8X có size RIFF=22, chunk=10, không có frame ảnh.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Tái sử dụng StaffAccessPolicy cho fallback ảnh trẻ chưa có lớp; trả thẳng ChildViewResponse | Review phát hiện scope đọc khác AUTH-20 và metadata tài khoản không được chiếu theo người xem | Scope đọc và scope quản trị tài khoản là hai việc riêng |
| 2 | Kiểm RIFF/WEBP + kích thước chunk đầu | Review dựng được header VP8X không có frame vẫn được nhận | Header container không chứng minh có ảnh |
| 3 | CampusAccessPolicy cho đọc; chiếu guardian theo quyền quản trị; giữ photoUrl trong mock; kiểm frame WebP | Kiểm thử mới đạt, review lại không còn blocker | Tái sử dụng policy hiện có, regression cho DTO và file sai |

## Root cause
Nguồn đọc hồ sơ tái sử dụng DTO tiếp nhận có dữ liệu quản trị, trong khi scope đọc khác scope ghi; projection cũ chỉ chọn các field text. WebP không có decoder JDK nên cần kiểm frame thay vì chỉ header container.

## Fix
`ChildPhotoServiceImpl.readableProfile/getProfile`: CampusAccessPolicy đọc; chỉ giữ account/SMS khi có PARENT_ACCOUNT_MANAGE và đúng phạm vi quản trị. `ChildViewResponse.withoutGuardianAccounts` bỏ metadata nhưng giữ ảnh. FE `childrenMockRepository.toView` giữ photoUrl cho giáo viên. WebP parser cần VP8/VP8L, chặn chunk sai và lồng ANMF quá một cấp.

## Regression test
`ChildPhotoServiceImplTest.rejectsWebpWithoutAnImageFrame`, `teacherProfileHidesAccountAdministration`, `crossCampusProfileRetainsPhotoWithoutAccountDetails`; `ChildEnrollmentIntegrationTest.enrollsPhotosBeforeAccountActivation`; FE `tests/children/enrollment-photos.test.mjs` ca giáo viên/đổi thứ tự. Các ca mới đã chạy sau fix; không tuyên bố đã chạy RED trước fix.

## Ảnh hưởng
Chỉ tính năng ảnh mới chưa commit. Không cần backfill ảnh, không đổi ảnh tài khoản APP hoặc quyền ghi tiếp nhận.

## Lesson
Khi tái sử dụng DTO ghi cho đường đọc, cần chiếu từng nhóm dữ liệu theo quyền tương ứng. Không đồng nhất phạm vi đọc và phạm vi quản trị tài khoản.
