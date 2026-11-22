---
title: Ngày bắt đầu đi học khi tiếp nhận, ngày hiệu lực xếp và chuyển lớp, nhắc trẻ tới ngày chưa có lớp (đợt 2)
status: in-progress
owner: ATPX48-69
jira: G94-180 / BE-FIX-10
branch: fix/G94-180-BE-FIX-10-school-year-scope
modules: [child, school-structure]
rules: [G94-180/CHD-03, G94-180/CHD-10, G94-180/CHD-11, G94-180/CHD-14, CFG-YR-01, CFG-YR-05]
risk: high
created: 2026-11-22
updated: 2026-11-23
---
# Ngày bắt đầu đi học khi tiếp nhận, ngày hiệu lực xếp và chuyển lớp, nhắc trẻ tới ngày chưa có lớp (đợt 2)

Đợt 2, làm sau đợt 1 (`2026-11-22-placement-age-group-history-rename.md`). Plan FE: `FE:docs/plans/active/2026-11-22-intake-start-date.md`. Có migration và đổi contract ⇒ risk high. Ngày 23/11 Cần nhờ user (qua lời nói): tiếp nhận phải có ngày bắt đầu đi học, chuyển lớp phải chọn ngày thực hiện. Vì vậy user tự làm phần này, không chờ Cần.

## Context
- Tiếp nhận năm đang học không có ngày bắt đầu: lớp tính từ hôm nay (`ChildIntakeServiceImpl.java:414`, `PlacementRules.java:90-92`). `startDate` chỉ dùng cho năm sau: mặc định ngày bắt đầu năm HT cấu hình, phải nằm trong năm (`EnrollChildRequest.java:15`, `ChildIntakeServiceImpl.java:656-660`, `:698-720`). Mở lại hồ sơ dùng hôm nay (`:272`).
- `children.enrolled_on` là ngày tiếp nhận, không sửa được (`Child.java:54-55`) và dùng để kiểm ngày nghỉ học (`ChildRecordServiceImpl.java:546-549`). Vì vậy không dùng cột này làm ngày bắt đầu đi học.
- Xếp và chuyển lớp (`ChildRecordServiceImpl.place`, `:331-433`) luôn tính từ hôm nay. Lớp cũ kết thúc hôm qua, hoặc bị xóa nếu bắt đầu hôm nay (`:402-410`); lớp mới bắt đầu hôm nay (`:411`).
- Các dòng Cần viết trong `place`, đều xét theo hôm nay:
  - chặn ngày không học (`:355-357`, e51624d2);
  - chặn khi hôm nay đã có điểm danh, sức khỏe hoặc đánh giá (`:389-393`, abc511dc);
  - chuyển trong năm chỉ cùng nhóm tuổi (`:370-376`, 8588ea75);
  - trẻ đăng ký cho năm sau (`:341-349`).
- Sĩ số lớp đích đếm tại hôm nay (`PlacementRules.java:57`). Lịch sử xếp lớp chỉ có `placed_at` (`ChildPlacement.java:43-44`). Thông báo cho giáo viên ghi "từ ngày" bằng ngày hôm nay (`PlacementRules.java:98-130`).
- Cho nghỉ học lấy enrollment có `valid_to` null (`ChildRecordServiceImpl.java:534-535`) rồi gọi `endOn(leftAt - 1)` (`:578`). Nếu enrollment đó bắt đầu ở tương lai thì vi phạm `ck_enrollments_valid_range` (`V3__create_children.sql:21`). Mỗi trẻ chỉ có 1 enrollment mở (`V63__child_enrollment_guardian_accounts.sql:81`).
- Điểm danh, sức khỏe, đánh giá và suất ăn đọc lớp theo ngày (`EnrollmentRepository.java:14-24`, `:46-52`). Vì vậy trẻ tự ở lớp cũ tới hết ngày trước ngày chuyển, không phải sửa module khác. Đơn xin nghỉ gắn với trẻ, không gắn với lớp (`LeaveRequest.java:29-35`).
- Ngày mở chuẩn bị luôn sau ngày kết thúc năm liền trước (CFG-YR-05). Mọi lịch chuyển (ngày hiệu lực ≤ ngày kết thúc năm) đều đã có hiệu lực trước khi xếp lớp năm mới, nên không đụng bảng xếp lớp của Cần.

## Quyết định đã chốt
1. Ô năm học tiếp nhận bắt buộc, không chọn sẵn. Chỉ có năm ACTIVE và năm PLANNED đã tới ngày chuẩn bị. Áp dụng cho form, mở lại hồ sơ và Excel — user 22/11.
2. Năm đang học: ngày bắt đầu đi học bắt buộc, từ hôm nay tới ngày kết thúc năm, mặc định là ngày học gần nhất. Năm sau giữ như hiện nay: mặc định là ngày bắt đầu năm HT cấu hình — user 22/11, nhắc lại 23/11.
3. Ngày bắt đầu đi học (cả năm sau) và ngày chuyển lớp phải là ngày học: thứ 2–6, ngoài ngày nghỉ của trường. BE chặn — user 23/11. Quyết định này thay cho 2A ngày 22/11 (khi đó chỉ cảnh báo).
4. Xếp lớp lần đầu cho trẻ chờ xếp lớp: có ô ngày, mặc định max(hôm nay, ngày bắt đầu đi học); không được trước ngày bắt đầu, không quá cuối năm — user 23/11. Thay cho 3A (tự tính, không có ô).
5. Trang tiếp nhận không phụ thuộc năm trên header (phần FE) — user 22/11.
6. Tới ngày bắt đầu mà trẻ chưa có lớp: BE trả `startDue`; FE gắn nhãn, đưa lên đầu và hiện dòng nhắc cho HT, PHT — user 22/11.
7. Chuyển lớp chọn ngày từ hôm nay tới ngày kết thúc năm, không chọn ngày đã qua. Ngày tương lai là lịch chuyển — user 23/11.
8. Khi có lịch chuyển, Xếp lớp và hồ sơ hiện "Sẽ chuyển sang X từ dd/mm". Tới hết ngày trước ngày đó, trẻ vẫn ở lớp cũ và tham gia mọi hoạt động của lớp cũ — user 23/11.
9. Trước ngày hiệu lực: chuyển lần nữa thì thay lịch cũ; có thao tác "Hủy lịch chuyển"; cho nghỉ học thì xóa lịch — user 23/11.

## Làm rõ nghiệp vụ
- Thêm rule G94-180/CHD-14 (ngày bắt đầu đi học, ngày hiệu lực xếp và chuyển lớp), ghi khi plan được duyệt. Bổ sung G94-180/CHD-03: cho nghỉ học khi đang có lịch chuyển thì xóa lịch.
- Giữ CHD-10, CHD-11, CFG-YR-01, CFG-YR-05. Giữ luật của Cần (dữ liệu trong ngày, cùng nhóm tuổi, trẻ năm sau), nhưng xét ở ngày hiệu lực thay vì hôm nay.
- Ảnh hưởng:
  - FE: `/children/new`, hộp Nhập Excel, `/children`, `/children/placement`, `/children/:id`.
  - APP: không gọi các endpoint này.
  - Dữ liệu cũ: `start_date` null; `effective_from` lấy theo ngày của `placed_at`.

### Câu hỏi mở
| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc |
| --- | --- | --- | --- | --- |
| Năm tiếp nhận theo header? | Chọn bắt buộc, không có năm đã qua; trang không theo header | user | 2026-11-22 | G94-180/CHD-14 |
| Ngày bắt đầu năm đang học | Từ hôm nay tới cuối năm, mặc định ngày học gần nhất | user | 2026-11-22 | G94-180/CHD-14 |
| Ngày bắt đầu, ngày chuyển vào ngày nghỉ | Chặn, phải là ngày học (thay "lưu, cảnh báo" ngày 22/11) | user | 2026-11-23 | G94-180/CHD-14 |
| Xếp lớp lần đầu cho trẻ chờ | Có ô ngày, mặc định max(hôm nay, ngày bắt đầu) | user | 2026-11-23 | G94-180/CHD-14 |
| Năm sau | Mặc định ngày bắt đầu năm HT cấu hình | user | 2026-11-23 | G94-180/CHD-11 |
| Tới ngày A chưa có lớp | Nhãn, đưa lên đầu, dòng nhắc HT và PHT | user | 2026-11-22 | G94-180/CHD-14 |
| Ngày chuyển lớp | Từ hôm nay tới cuối năm, không chọn ngày đã qua | user | 2026-11-23 | G94-180/CHD-14 |
| Khi có lịch chuyển | Hiện "Sẽ chuyển sang X từ dd/mm"; trẻ ở lớp cũ tới ngày đó | user | 2026-11-23 | G94-180/CHD-14 |
| Sửa, hủy lịch chuyển trước ngày hiệu lực | Chuyển lần nữa thì thay lịch cũ; có nút "Hủy lịch chuyển"; cho nghỉ học xóa lịch | user | 2026-11-23 | G94-180/CHD-14 |

## Acceptance criteria
- **AC-1** (`G94-180/CHD-14`): Given năm ACTIVE, When tiếp nhận thiếu `startDate`, hoặc ngày trước hôm nay, sau ngày kết thúc năm, hoặc không phải ngày học, Then lỗi 400 ở ô `startDate`. Năm sau: `startDate` không phải ngày học ⇒ 400 (thêm vào phần kiểm "trong năm" đã có).
- **AC-2** (`G94-180/CHD-14`): Given tiếp nhận có lớp với `startDate` ở tương lai, Then enrollment có `valid_from = startDate`. Danh sách điểm danh trước ngày đó không có trẻ, từ ngày đó có.
- **AC-3** (`G94-180/CHD-14`): Given trẻ chờ xếp lớp, When xếp lớp mà không gửi `effectiveDate`, Then lớp tính từ max(hôm nay, `startDate`). `effectiveDate` trước mốc đó, sau cuối năm hoặc không phải ngày học ⇒ 400 ở ô `effectiveDate`.
- **AC-4** (`G94-180/CHD-14`): Given trẻ chỉ có lớp chưa bắt đầu, When xếp lại, Then thay lớp và ngày của enrollment đó: không tạo enrollment chồng nhau, không bắt nhập lý do, vẫn ghi lịch sử.
- **AC-5** (`G94-180/CHD-14`): Given chuyển lớp với `effectiveDate` = hôm nay, Then làm như hiện nay, kể cả chặn khi đã có dữ liệu trong ngày. Given `effectiveDate` ở tương lai, Then lớp cũ có `valid_to = effectiveDate − 1`, lớp mới có `valid_from = effectiveDate`. Trước ngày đó trẻ vẫn trong danh sách điểm danh và đánh giá của lớp cũ; từ ngày đó chỉ ở lớp mới.
- **AC-6** (`G94-180/CHD-14`): `effectiveDate` trước hôm nay, sau ngày kết thúc năm hoặc không phải ngày học ⇒ 400 ở ô `effectiveDate`. Lý do chuyển lớp vẫn bắt buộc. Sĩ số lớp đích đếm mọi trẻ có lớp đó từ ngày hiệu lực trở đi.
- **AC-7** (`G94-180/CHD-14`): `GET /children` và `GET /children/{id}` trả thêm:
  - `startDate`;
  - `startDue`: true khi trẻ chờ xếp lớp có `startDate` ≤ hôm nay;
  - `nextClass {classId, className, from}` khi trẻ có lớp bắt đầu sau hôm nay.

  `GET /children/{id}/placements` trả thêm `effectiveFrom`.
- **AC-8** (`G94-180/CHD-03`): Given trẻ có lớp bắt đầu ở tương lai, When cho nghỉ học, Then xóa enrollment tương lai, lớp hiện tại (nếu có) kết thúc trước ngày nghỉ, trẻ thành LEFT, không lỗi ràng buộc DB.
- **AC-9** (`G94-180/CHD-14`): Thông báo cho giáo viên lớp cũ và lớp mới ghi đúng ngày hiệu lực.
- **AC-11** (`G94-180/CHD-14`): Given trẻ có lịch chuyển, When chuyển lần nữa, Then lịch cũ bị thay (không còn enrollment tương lai cũ). When hủy lịch (`DELETE /children/{id}/placement/scheduled`), Then xóa enrollment tương lai, lớp hiện tại mở lại (`valid_to` null), ghi lịch sử, báo giáo viên hai lớp. Không có lịch ⇒ 400.
- **AC-10** (`G94-180/CHD-14`, `CFG-YR-01`, `G94-180/CHD-11`): Mở lại hồ sơ và nhập Excel vào năm đang học kiểm `startDate` như AC-1 (Excel dùng một ngày cho cả file). Năm đã đóng ⇒ từ chối. Năm sau chưa tới ngày chuẩn bị ⇒ 2412 như hiện nay.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lưu ngày bắt đầu đi học | Cột mới `children.start_date` (nullable) | Dùng `enrolled_on` · dùng `planned_admissions` | `enrolled_on` là ngày tiếp nhận; `planned_admissions` gây lỗi 2414 khi xếp lớp và đụng code Cần |
| Lịch chuyển | Enrollment tương lai; lớp cũ có `valid_to` | Bảng lịch riêng và job chạy đúng ngày | Module khác đọc lớp theo ngày nên tự đúng; không cần scheduler |
| Ngày hiệu lực trong lịch sử | Cột `child_placements.effective_from`, dòng cũ điền ngày của `placed_at` | Suy từ enrollment | Đọc thẳng, vẫn đúng khi enrollment bị thay |
| Sĩ số lớp đích | Đếm enrollment của lớp còn hiệu lực từ ngày đó trở đi | Đếm đúng tại ngày đó | Không vượt sĩ số khi đã có trẻ khác hẹn vào lớp sau ngày đó |
| Không gửi `effectiveDate` | Hôm nay (chuyển lớp) / max(hôm nay, `startDate`) (xếp lần đầu) | Bắt buộc gửi | Client cũ vẫn chạy được |
| Cho nghỉ trước ngày bắt đầu | Xóa enrollment tương lai | `endOn` | `endOn` vi phạm `ck_enrollments_valid_range` |
| Kiểm ngày học | `SchoolYearService.isSchoolDay` (của Cần, chỉ đọc) | Tự tính | Một nguồn lịch nghỉ |

Đổi contract (consumer: `FE:src/services/children/api/childrenApi.js` và mock FE; APP không gọi):
- `startDate`: bắt buộc cho năm đang học ở `POST /children/enrollments`, `/import`, `/import/preview`, `POST /children/{id}/readmit`.
- `effectiveDate`: thêm vào `POST /children/{id}/placement`.
- `startDate`, `startDue`, `nextClass`: thêm vào item `GET /children` và `GET /children/{id}`.
- `effectiveFrom`: thêm vào `GET /children/{id}/placements`.
- Endpoint mới `DELETE /children/{id}/placement/scheduled` (hủy lịch chuyển, quyền `CHILD_PLACEMENT`).
- Cập nhật snapshot bằng `-Dopenapi.snapshot.update=true` (`OpenApiSnapshotTest.java:23,58`).

## Phases
### Phase 1 — Ngày bắt đầu khi tiếp nhận, mở lại hồ sơ, Excel (AC-1, AC-2, AC-10)
- Files:
  - C `db/migration/V173__child_start_date.sql` (kiểm số version với `origin/dev` và mọi nhánh đã push trước khi tạo).
  - M `Child.java`, `EnrollmentFormValidator.java`, `ChildIntakeServiceImpl.java` (gồm `plannedTarget` của Cần: thêm kiểm ngày học), `ChildEnrollmentImportServiceImpl.java`, DTO.
  - M test integration tiếp nhận và import; M `docs/api/openapi.yaml`.
- Exit: `./mvnw verify` không có failure mới (ngoài các failure Edu và stock có sẵn); snapshot OpenAPI khớp.
- **Dừng chờ user review.**

### Phase 2 — Xếp lần đầu có ngày, lớp chưa bắt đầu, cho nghỉ, `startDue` (AC-3, AC-4, AC-7 phần `startDate`/`startDue`, AC-8)
- Files:
  - M `ChildRecordServiceImpl.java` (`place`, `withdraw`), `PlacementRules.java`, `PlaceChildRequest.java`, `ChildListServiceImpl.java`, DTO.
  - M test integration.
  - M `docs/business/BUSINESS_RULES.md` (G94-180/CHD-14, CHD-03), `docs/modules/child.md`.
- Exit: như phase 1. **Dừng chờ user review.**

### Phase 3 — Chuyển lớp theo ngày, lịch chuyển, hủy lịch, lịch sử, thông báo (AC-5, AC-6, AC-7 phần `nextClass`/`effectiveFrom`, AC-9, AC-11)
- Files:
  - C `db/migration/V174__child_placement_effective_from.sql`.
  - M `ChildRecordServiceImpl.java` (`place`; các dòng Cần `:355-357`, `:389-393` chuyển sang xét ngày hiệu lực).
  - M `PlacementRules.java` (sĩ số từ ngày, `recordPlacement`), `ChildPlacement.java`, `ChildPlacementResponse.java`, `EnrollmentRepository.java`.
  - M test integration chuyển lớp, thêm regression danh sách điểm danh theo ngày.
- Exit: như phase 1. **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2, AC-10 | G94-180/CHD-14, CHD-11 | integration tiếp nhận, import | 1 | |
| AC-3, AC-4, AC-7 (`startDue`), AC-8 | G94-180/CHD-14, CHD-03 | integration xếp lớp, cho nghỉ | 2 | |
| AC-5, AC-6, AC-7 (`nextClass`), AC-9, AC-11 | G94-180/CHD-14 | integration chuyển lớp, hủy lịch + danh sách điểm danh theo ngày | 3 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Trùng số migration với `dev` hoặc nhánh khác | Flyway lỗi khi gộp | Kiểm trước khi tạo; migration chưa commit mới được đổi số | Đổi số trước khi commit |
| Chỗ lấy "lớp mới nhất" thấy lớp tương lai quá sớm | Hiện sai lớp hoặc sai điểm trường | Đã rà: `ChildIntakeServiceImpl.java:781-784`, `ChildRecordServiceImpl.java:447`, `:1147-1157` chỉ dùng khi hôm nay không có lớp (LEFT hoặc chỉ có lớp tương lai). Thêm test | — |
| Sửa dòng Cần viết (`place`, `plannedTarget`) | Lệch luật Cần | Giữ luật, chỉ đổi ngày xét; báo Cần trước khi push | Revert phase |
| FE cũ gửi thiếu `startDate` | 400 ở năm đang học | Gộp BE và FE cùng lúc; thiếu `effectiveDate` thì dùng mặc định | Revert cả hai |

## Validation log
- [x] Khẳng định về code có `file:line` (đối chiếu HEAD 39929938)
- [x] Đổi contract ⇒ consumer cụ thể (`childrenApi.js`, mock FE; APP không gọi)
- [x] Mỗi AC có test; mỗi phase có Exit; 3 phase (không quá giới hạn tách)
- [x] Rule mới G94-180/CHD-14 chỉ ghi khi user duyệt plan
- [x] Không còn câu hỏi mở (sửa, hủy lịch chuyển: user 23/11)

## Progress log
### 2026-11-22 — draft, chờ user duyệt
- Commit: chưa commit. Đợt 2 sửa cả dòng Cần viết; khi đó chờ user quyết làm hay chờ Cần.

### 2026-11-23 — cập nhật theo yêu cầu của Cần
- Cần nhờ user làm ngày bắt đầu đi học và ngày chuyển lớp, nên bỏ điều kiện "chờ Cần".
- User chốt quyết định 7, 8; đổi quyết định 3 (chặn ngày nghỉ) và 4 (có ô ngày); nhắc lại mặc định năm sau.
- Thêm phase 3; migration dự kiến V173, V174 (V169–V172 đã dùng sau lần merge 39929938).
- Tiếp: user trả lời câu hỏi mở và duyệt plan. Commit: chưa commit.

### 2026-11-23 — approved, phase 1 xong
- User: "như bạn đề xuất và hãy hoàn thành tất cả các phase không phải hỏi lại" ⇒ chốt quyết định 9, AC-11; làm cả 3 phase không dừng.
- Phase 1: V173 `children.start_date`; `placeOf`/`readmit` kiểm `startDate` năm đang học (thiếu, trước hôm nay, sau cuối năm, ngày nghỉ); năm sau thêm kiểm ngày học, để trống = ngày học đầu tiên của năm; lớp lúc tiếp nhận tính từ ngày bắt đầu.
- Khác plan: fixture năm sau của `ChildEnrollmentIntegrationTest` đổi ngày bắt đầu 05/09/2027 (chủ nhật) thành 06/09/2027 vì ngày bắt đầu phải là ngày học.
- Verify: `ChildEnrollmentIntegrationTest` 54 test, 1 error có sẵn (`plannedClassOfOtherAgeGroupIsAccepted`, luật nhóm tuổi của Cần, chờ 2A); controller, import, photo, list, validator test đạt (166 test cùng đợt).
