# Nối FE — Cấu hình trường

So sánh API BE (nhánh làm việc của Cần, 22/10/2026) với lớp api + mock của CareNest_FE nhánh `feature/G94-179-FE-FEAT-42-attendance-daily-health-academic-year-configuration` (commit `8fca8d5`). Path, method và hành vi nghiệp vụ theo mock FE. Bảng 1 là những gì FE vẫn phải đổi; bảng 2 là những gì BE đã chỉnh cho khớp FE. Kiểm tra bằng Postman folder **13. Kiểm tra khớp FE** (mỗi request có Example là response thật).

## 1. FE phải đổi (chuẩn chung của BE, không đổi được)

| # | FE file / hàm | Hiện tại (mock) | Đổi thành |
| --- | --- | --- | --- |
| 1 | `src/services/http/axiosClient.js` (interceptor response) | trả `response.data` là dữ liệu; lỗi đọc `data.message` | BE trả `{code, desc, data}` ⇒ dữ liệu = `response.data.data`; lỗi đọc `response.data.desc`; HTTP status = `code` |
| 2 | Mọi chỗ dùng id | id chuỗi (`'2026-2027'`, `'c1'`, `'cls1'`, `'loc1'`) | id số (Long). Năm học: dùng `id` số, `name` chỉ để hiển thị (`SchoolYearContext`, `?year=` trên URL) |
| 3 | Form lỗi (mock `ApiError(422, …, errors)`) | 422 + `errors = {field: message}` | 400 + `data = [{field, message}]` (validation) hoặc 400/409 chỉ có `desc` (rule nghiệp vụ). `desc` tiếng Anh — FE tự hiển thị câu tiếng Việt theo field/status nếu cần |
| 4 | `schoolConfigApi.saveCutoffSetting` | gửi `reopenWindowMinutes` | bỏ (BE bỏ theo Cần 21/10; gửi thừa thì BE bỏ qua) |
| 5 | Mọi màn có `history[]` (năm học, nhóm tuổi, lớp, phòng) | mock trả `history` | BE chưa lưu lịch sử thay đổi (trừ giờ chốt: `setting.history`) ⇒ ẩn khối lịch sử hoặc để trống |
| 6 | Trẻ trong xếp lớp | `code`, `photoUrl` có giá trị | BE luôn `null` (chưa có cột mã trẻ/ảnh) |
| 7 | `schoolConfigApi.getTeacherAssignments` / `getStaffAccounts` | GV = user có `role`, `campusId` | BE tạm lấy GV từ phân công đang có (phương án C): `teachers[{id, fullName, campusId, campusIds}]`; chưa có `/users` (Tài khoản nhân sự chờ chốt) |
| 8 | Activate năm học | readiness bị chặn bởi bước PHT | bước `vicePrincipals` dựa trên phân công PHT đang có trong DB (màn Phân công PHT không thuộc phần Cần) |

## 2. BE đã chỉnh cho khớp FE

| API | FE cần | BE trước | BE bây giờ | Postman |
| --- | --- | --- | --- | --- |
| `GET /school-years` | `data` là mảng | `data.items` (trang) | `data` = mảng, mới nhất trước; lọc `status` | 13 › School years; 6 › list |
| `GET /school-years/{id}/cutoff` | `{year, setting:{schoolYear, time, effectiveFrom, updatedBy, updatedAt, history[]}}` | `history` ở ngoài, `schoolYearId/schoolYearName` | `{year, setting{schoolYear, time, effectiveFrom, note, updatedBy, updatedByName, updatedAt, history[]}, todayTime}` | 13 › Cutoff; 10 |
| `GET /campuses/{id}` | `{campus, classes(+childCount), ageGroups, vicePrincipals, teachers, kitchen{locations, staff}}` | thiếu `ageGroups`, `kitchen`, `childCount`; có `kitchenStaff` | đủ các field FE + `schoolYearId`; `classes[{id, name, ageGroupId, capacity, locationId, childCount}]` | 13 › Campus detail |
| Query `schoolYear` | `?schoolYear=` | chỉ nhận `schoolYearId` | nhận cả `schoolYear` và `schoolYearId` (campuses, locations, school-structure, teacher-assignments, team-leader-assignments) | 13 › Rooms, Structure, … |
| `POST/PUT /classes` | body `{name, campusId, ageGroupId, schoolYear, capacity, locationId}` | `schoolYearId`, `roomId` | nhận `schoolYear`/`schoolYearId`, `locationId`/`roomId`; response `locationId` | 13 › Class; 9 › class |
| `GET /school-structure` | class `plannedCount`, `groupStats.planned`, `teamLeaders[{ageGroupId, userId, campusId}]` | không có | `plannedCount` (năm PLANNED = trẻ đã xếp vào lớp, năm khác `null`), `groupStats{classCount, enrolled, planned, capacity}`, `teamLeaders{ageGroupId, userId, fullName, campusId}` | 13 › School structure |
| `GET /locations` | `usage` | không có | `usage = {classes: n}` (số lớp mọi năm dùng phòng) + `yearClasses`, `blockers`, `minCapacity`, `managerName` | 13 › Rooms |
| `GET /teacher-assignments` | `campusId=all`; teacher có `campusId` | `campusId` phải là số; `campusIds[]` | nhận `all`/trống = mọi điểm trường trong phạm vi; teacher `{id, fullName, campusId, campusIds}` | 13 › Teacher assignments; 11 |
| `GET /team-leader-assignments` | `leaderGroups` = nhóm tuổi phẳng + `classCount`; candidate `campusId`, `leadsGroup`; leader `campusId` | `leaderGroups[{ageGroup, classCount}]`, thiếu `leadsGroup`, `campusId` | `leaderGroups[{id, code, name, shortName, ageFromMonths, ageToMonths, mealSessions, maxClassSize, classCount}]`, candidate `{id, fullName, campusId, campusIds, leadsAgeGroupId, leadsGroup}`, leader `{ageGroupId, userId, fullName, campusId}` | 13 › Team leaders |
| `PUT /team-leader-assignments` | body `{schoolYear, ageGroupId, userId}` | chỉ `schoolYearId` | nhận `schoolYear`/`schoolYearId` | 13 › Team leader; 11 |
| `GET /school-years/{id}/readiness` | `steps[{key, label, required, ok, detail}]` | thiếu `label` | có `label` (tiếng Việt như FE `READINESS_STEPS`); `blockers` dạng "Nhãn: chi tiết" | 13 › Readiness |
| `GET /school-years/{id}/placements` | child `{id, code, fullName, photoUrl, birthDate, placement}`; placement đầy đủ | thiếu `code`, `photoUrl`; placement chỉ `{action, toClassId}` | child đủ field (`code`, `photoUrl` = null); placement `{schoolYear, childId, fromClassId, action, toClassId, updatedBy, updatedAt}` | 13 › Placements |

Không đổi vì đã khớp: body năm học, kích hoạt `{confirmName}`, điểm trường, nhóm tuổi (`mealSessions` MORNING/LUNCH/AFTERNOON, `mealsPerDay`), phòng (`room`, `type` CLASS/KITCHEN/FUNCTION_ROOM/DEPARTMENT/STORAGE), `PATCH /locations/{id}/status`, `PUT /classes/{id}/teachers`, `PUT /placements` `{items[{childId, action, toClassId}]}`, `POST /placements/auto`, sao chép lớp. Ngày `yyyy-MM-dd`, giờ `HH:mm`, thời điểm ISO-8601 UTC.
