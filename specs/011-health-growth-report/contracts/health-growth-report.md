# Contract — Báo cáo tổng hợp tăng trưởng (Feature011)

Snapshot: `docs/api/openapi.yaml` (operation `getGrowthReport`, schema `HealthGrowthReportResponse`). Consumer: FE (màn báo cáo mới, BE giả mirror contract này); APP không gọi.

## `GET /health-checks/growth-report?month=yyyy-MM[&campusId=]`
- Vai trò: `PRINCIPAL`, `VICE_PRINCIPAL` (`@PreAuthorize`); vai trò khác ⇒ 403 errorCode 1101 (`FORBIDDEN`); gọi service trực tiếp với vai trò khác ⇒ 403 5010.
- `month` bắt buộc, dạng `yyyy-MM`; thiếu/sai ⇒ 400 errorCode 9002 (`MALFORMED_REQUEST`).
- Phạm vi: HT mọi điểm trường; PHT điểm trường được phân công. `campusId` ngoài phạm vi (PHT, kể cả id không tồn tại) ⇒ 403 errorCode 5010 (`HEALTH_CHECK_ACCESS_DENIED`); HT với `campusId` không tồn tại ⇒ 404 errorCode 2103 (`CAMPUS_NOT_FOUND`).

## Response `data` — `HealthGrowthReportResponse`
```
{ month: "2026-09",
  campuses: [{ campusId, campusName,
      campaigns: [{ id, name, kind: "EXAM"|"MEASUREMENT", status: "UPCOMING"|"IN_PROGRESS"|"CLOSED", startDate, endDate }],
      classes: [{ classId, className, ageGroupName, level: "PRESCHOOL"|"NURSERY"|null, counts }],
      preschool: counts, nursery: counts, total: counts }],
  preschool: counts, nursery: counts, total: counts }
counts = { enrolled, measured, weightNormal, underweight, heightNormal, stunted,
           bmiNormal, wasted, overweight, obese, unclassified }   // số nguyên ≥ 0
```
- `campuses`: thứ tự như danh sách điểm trường (điểm chính trước, rồi theo mã). Điểm trường không có đợt/kỳ trong tháng: `campaigns: []`, `classes: []`, mọi số 0; riêng điểm trường ngừng hoạt động (`INACTIVE`) thì bị ẩn, trừ khi lọc đúng `campusId` đó.
- `campaigns`: đợt khám và kỳ cân đo chưa hủy có `startDate` trong tháng, theo `startDate` rồi `id`.
- `classes`: lớp của các đợt/kỳ đó; MG (`PRESCHOOL`) theo nhóm tuổi lớn trước rồi tên lớp, NT (`NURSERY`) theo tên, lớp không nhóm tuổi (`level` null) cuối. `ageGroupName` = tên nhóm tuổi (null khi không có).
- `preschool`, `nursery`: cộng các lớp cùng cấp; `total` của điểm trường cộng mọi lớp (kể cả `level` null). Khối ngoài cùng cộng các điểm trường.
- Ánh xạ: `weightNormal` W/A bình thường, `underweight` W/A < −2SD; `heightNormal` H/A bình thường, `stunted` H/A < −2SD; `bmiNormal` BMI bình thường + nguy cơ thừa cân, `wasted` gầy còm + gầy còm nặng, `overweight`, `obese` (HLT-18); `unclassified` trẻ được cân đo có chỉ số không xếp được (ngoài 0–84 tháng, thiếu ngày sinh/giới tính).
- Tỷ lệ % FE tính (vd. `measured / enrolled`, `underweight / measured`).
