---
id: BUG-261101-dev-seed-class-no-age-group
type: bug
modules: [school-structure, attendance, nutrition, education-plan]
rules: [NUT-17, SCH-03, AUTH-23]
status: fixed
date: 2026-11-01
keywords: [seed dev, R__dev_seed, lớp không nhóm tuổi, mealSessions rỗng, meal_counts trống, Tổ trưởng, VPS]
similar_to: []
---

# BUG-261101-dev-seed-class-no-age-group — Seed dev tạo lớp không nhóm tuổi/phòng/Tổ trưởng ⇒ không có suất ăn, hồ sơ lớp kẹt

## Symptom
Profile `dev` (cả VPS): lớp A1/B1 `mealSessions: []`, gửi bữa ăn lỗi `6001`, `meal_counts` 0 dòng mọi ngày, xác nhận/điều chỉnh 404 `6004`; GV thấy "Kế hoạch… của khối chưa được ban hành" dù đã có bản APPROVED.

## Điều kiện tái hiện
DB dev dựng bằng `R__dev_seed.sql` cũ: `classrooms` chỉ có campus, năm, tên. HT không vá được qua UI vì lớp đã có trẻ thì cấm đổi nhóm tuổi (`ClassManagementServiceImpl`, 409 `2211`, cả `null`→X).

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Sửa nhóm tuổi lớp qua UI/API | 409 `2211` | Lớp đã có trẻ; seed là cách vá duy nhất |

## Root cause
Seed viết trước khi lớp bắt buộc nhóm tuổi/phòng (V15, V16) và trước khi có Tổ trưởng (AUTH-23); `INSERT ... ON CONFLICT DO NOTHING` không vá lớp đã tồn tại; không test nào chạy seed dev.

## Fix
`src/main/resources/db/dev/R__dev_seed.sql`: thêm phòng `DEMO-A1`/`DEMO-B1`; `UPDATE classrooms` điền nhóm tuổi (A1 ⇒ ag-4, B1 ⇒ ag-5 theo năm sinh trẻ demo), phòng, sức chứa khi đang trống; phân công `AGE_GROUP_TEAM_LEADER` (teacher1 ag-4, teacher2 ag-5) nếu nhóm tuổi chưa có tổ trưởng năm đó. Repeatable migration chạy lại vì đổi checksum ⇒ VPS nhận khi deploy.

## Regression test
`src/test/java/com/carenest/repository/organization/DevSeedIntegrationTest.java`: `freshSeedSatisfiesClassInvariants`, `seedPatchesLegacyClassesWithoutAgeGroup` (DB có lớp kiểu seed cũ ⇒ seed mới vá; chạy lại không đổi). Trước fix: lớp seed không có `room_id`/nhóm tuổi ⇒ assert fail.

## Ảnh hưởng
Chỉ dữ liệu dev/demo. Ngày đã điểm danh trước khi vá không có suất (đã lưu bữa rỗng).

## Lesson
Seed demo phải thỏa mọi invariant mà UI/API bắt buộc, và có test chạy seed trên DB kiểu cũ (R__ + `ON CONFLICT DO NOTHING` không sửa dòng đã có).
