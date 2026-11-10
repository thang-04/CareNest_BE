---
id: BUG-261101-eduplan-vp-without-assignment
type: bug
modules: [education-plan, identity-access]
rules: [AUTH-20, EDU-08]
status: fixed
date: 2026-11-01
keywords: [EduPlanAccessPolicy, isVicePrincipal, GOALS_APPROVE, phân công hết hạn, PHT không phân công, ban hành kế hoạch]
similar_to: []
---

# BUG-261101-eduplan-vp-without-assignment — PHT không có/hết phân công vẫn lập, ban hành, duyệt kế hoạch giáo dục

## Symptom
HT tạo PHT với `assignments: []` ⇒ PHT đó tạo kế hoạch năm 201 và ban hành 200 `APPROVED`. Cùng user: sửa số suất 403 `6003`, mở lại điểm danh 403 `2201`. HT hết phân công vẫn ký bước HT hồ sơ lớp.

## Điều kiện tái hiện
User role `VICE_PRINCIPAL` không có dòng `staff_assignments` còn hiệu lực (tạo không phân công, hoặc phân công đã kết thúc — kết thúc phân công không gỡ role).

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Tái hiện trên stack local (review E2E) | 201/200 | Xác nhận lỗi quyền |

## Root cause
`EduPlanAccessPolicy` viết theo EDU-08 "tạm gán theo role": quyền chỉ đọc role trong token, không qua `StaffAssignmentService` như attendance/meals; bước HT dùng `user.hasRole(PRINCIPAL)`. AUTH-20 chỉ miễn phạm vi mảng cho kế hoạch giáo dục, không miễn phân công.

## Fix
`EduPlanAccessPolicy.hasPermission` chỉ tính role HT/PHT khi còn phân công (`hasActivePrincipalAssignment`, `findActiveCampusIds(VICE_PRINCIPAL)`); thêm `isPrincipal`; `EduClassDocServiceImpl` dùng `accessPolicy.isPrincipal` thay `hasRole(PRINCIPAL)`.

## Regression test
`EduYearPlanServiceIntegrationTest.vicePrincipalWithoutActiveAssignmentIsDenied` (PHT không phân công và PHT hết phân công ⇒ `FORBIDDEN`). Fixture `EduClassDocServiceIntegrationTest`, `EduConfigServiceIntegrationTest`, `EduYearPlanServiceIntegrationTest` thêm phân công cho HT/PHT.

## Ảnh hưởng
PHT/HT không còn phân công mất quyền xem/sửa/duyệt kế hoạch giáo dục (giống module khác). FE: BE trả 403, `ErrorState` hiển thị.

## Lesson
Mọi quyền theo role HT/PHT phải đi kèm kiểm phân công còn hiệu lực; "tạm theo role" chỉ áp cho phạm vi mảng.
