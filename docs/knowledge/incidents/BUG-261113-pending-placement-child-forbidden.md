---
id: BUG-261113-pending-placement-child-forbidden
type: bug
modules: [identity-access, child, health]
rules: [AUTH-01, AUTH-02]
status: fixed
date: 2026-11-13
keywords: [chờ xếp lớp, PENDING_PLACEMENT, hiệu trưởng, sổ sức khỏe, xu hướng sức khỏe, 403, 5025, HEALTH_RECORD_ACCESS_DENIED, ChildAccessPolicy]
similar_to: []
---

# BUG-261113-pending-placement-child-forbidden — Hiệu trưởng bị 403 khi xem sổ sức khỏe của trẻ chờ xếp lớp

## Symptom
Web màn Xu hướng sức khỏe / Sổ sức khỏe hiện "Bạn không có quyền xem hồ sơ này" với tài khoản Hiệu trưởng.
```text
GET /children/{childId}/health → 403 {"errorCode":5025,"desc":"Bạn không có quyền xem sổ sức khỏe của trẻ này"}
```
`GET /children/{childId}/profile` của cùng trẻ vẫn 200.

## Điều kiện tái hiện
Trẻ `PENDING_PLACEMENT` (có `intake_campus_id`, chưa có dòng `enrollments`). Đăng nhập Hiệu trưởng còn phân công, mở sổ sức khỏe của trẻ đó.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Nghi phân công PRINCIPAL hết hạn | Loại: danh sách trẻ toàn trường vẫn 200 | Phải xem response profile của đúng trẻ trước khi nghi tài khoản |
| 2 | Đọc response profile trên dev | `status: PENDING_PLACEMENT`, `classId: null`, `placements: []` | Xác nhận nguyên nhân |

## Root cause
`ChildAccessPolicy.canAccessChild` chỉ xét phạm vi qua lớp trẻ đang học hôm nay (`findActiveClassroomIds`). Trẻ chưa có lớp ⇒ danh sách rỗng ⇒ `anyMatch` luôn false, kể cả Hiệu trưởng (AUTH-01 toàn trường). Endpoint profile không lộ lỗi vì có thêm nhánh `canReadAllCampuses`.

## Fix
`ChildAccessPolicy`: trẻ không có lớp hôm nay và đang chờ xếp lớp thì xét theo điểm trường tiếp nhận — `canViewCampus` (thao tác) / `canReadCampus` (đọc). `EnrollmentService.findPendingPlacementCampusId` trả điểm trường tiếp nhận, rỗng với trẻ đã có lớp, đã nghỉ, id không tồn tại. Giáo viên không có phạm vi điểm trường nên vẫn bị từ chối; trẻ đã nghỉ học giữ nguyên hành vi cũ.

## Regression test
`ChildAccessPolicyTest.principalCanViewChildPendingPlacement`, `ChildAccessPolicyTest.pendingPlacementChildFollowsIntakeCampusScope` — fail trước fix ("Expecting value to be true but was false"), pass sau fix.

## Ảnh hưởng
Mọi nơi gọi `canViewChild` / `canReadChild` (sổ sức khỏe, phiếu khám, đánh giá trẻ, ảnh hồ sơ): BGH nay vào được trẻ chờ xếp lớp thuộc phạm vi điểm trường. Không đổi API contract, không cần sửa dữ liệu. FE/APP không phải sửa.

## Lesson
Policy lấy phạm vi từ quan hệ (lớp) phải có nhánh cho thực thể chưa có quan hệ đó; nếu không, vai trò "toàn trường" cũng bị chặn.
