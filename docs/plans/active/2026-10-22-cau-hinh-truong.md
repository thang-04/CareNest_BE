---
title: Cấu hình trường — API năm học (phase 1) và các màn cấu hình còn lại
status: in-progress
owner: Nhữ Đình Cần
jira: G94-235 / BE-FEAT-03
branch: feature/G94-235-BE-FEAT-03-school-config   # tạo từ feature/G94-226 (cần auth + quy ước G94-25 chưa vào main)
modules: [school-structure]
rules: [AUTH-01, AUTH-02, AUTH-03]
risk: high
created: 2026-10-22
updated: 2026-10-22
---
# Cấu hình trường — API năm học

## Context
- **UI là chuẩn.** CareNest_FE nhánh `feature/G94-179-FE-FEAT-42-attendance-daily-health-academic-year-configuration` (commit `8fca8d5`), menu "Cấu hình trường": `/school/years`, `/school/years/new`, `/school/years/:id/edit` (`SchoolYearListPage`, `SchoolYearFormPage`).
- **FE gọi mock.** `src/services/school-config/api/schoolConfigApi.js` ghi "PROPOSED endpoints only"; hành vi lấy từ `schoolConfigMockRepository.js` (saveSchoolYear, activateSchoolYear, closeSchoolYear, deleteSchoolYear) và `schoolConfigValidation.js#validateSchoolYear`.
- **Hiện trạng BE.** Bảng `school_years` (V2) chỉ có `name`, `start_date`, `end_date`; chưa có API quản lý (dev dùng `db/dev/R__dev_seed.sql`). `SchoolStructureService` chỉ đọc cho module education-plan.

## Quyết định đã chốt
1. Cần yêu cầu code API năm học để test Postman rồi nối FE — Cần, 22/10/2026.
2. Bám path và field của FE; BE giữ tên bảng/ID/ràng buộc hiện có (không theo `CareNest_ERD` ở các điểm lệch) — Cần, 22/10/2026.

## Làm rõ nghiệp vụ
- **Actor:** Hiệu trưởng tạo/sửa/xóa/kích hoạt/kết thúc năm học; Phó hiệu trưởng và giáo viên chỉ xem (FE `schoolConfigPermissions.js`: `canConfigureSchool = isPrincipal`, `canViewSchoolStructure` gồm HT, PHT, GV, tổ trưởng).
- **Rule theo hành vi FE (PROPOSED, chưa có ID trong BUSINESS_RULES):**
  - Tên `NNNN-NNNN`, năm sau = năm trước + 1; không trùng tên; `endDate > startDate`; không chồng khoảng ngày với năm khác.
  - Năm mới luôn `PLANNED`. `CLOSED` chỉ đọc.
  - Đổi tên chỉ khi `PLANNED` và chưa có lớp.
  - Xóa chỉ khi `PLANNED`, chưa có lớp và không bảng nào khác tham chiếu.
  - Kích hoạt chỉ năm `PLANNED`, gõ đúng tên để xác nhận, từ 7 ngày trước ngày bắt đầu (`ACTIVATION_LEAD_DAYS`, FE đề xuất 13/10/2026); kích hoạt tự kết thúc năm `ACTIVE` cũ trong cùng transaction ⇒ luôn tối đa một năm `ACTIVE`.
  - Kết thúc chỉ năm `ACTIVE`.
- **Ảnh hưởng:** module school-structure; education-plan đọc `SchoolYearResponse` (không đổi). FE: `/school/years*` cần bóc envelope `{code, desc, data}`, đọc danh sách ở `data.items`, dùng `id` số thay cho tên năm.

### Câu hỏi mở
- Readiness (lớp, phòng, phân công, xếp lớp) chặn kích hoạt: để phase xếp lớp, chưa kiểm ở phase 1.
- Đưa các rule năm học vào `BUSINESS_RULES.md` với ID mới: chờ nhóm xác nhận.

## Acceptance criteria
- **AC-1** (`AUTH-01`): Given HT có phân công còn hiệu lực When POST `/school-years` hợp lệ Then 201, `status = PLANNED`.
- **AC-2** (`AUTH-02`): Given PHT When POST/PUT/DELETE/activate/close Then 403.
- **AC-3**: Given năm trùng tên hoặc chồng ngày Then 409.
- **AC-4**: Given năm `ACTIVE` đang có When kích hoạt năm `PLANNED` hợp lệ Then năm cũ `CLOSED`, năm mới `ACTIVE`, DB còn đúng một năm `ACTIVE`.
- **AC-5**: Given năm có lớp When xóa Then 409.

## Phases
### Phase 1 — Năm học
- Files: C `db/migration/V10__add_school_year_lifecycle.sql` · C `utils/enums/SchoolYearStatus` · M `entity/organization/SchoolYear` · M `repository/organization/SchoolYearRepository`, `ClassroomRepository` · C `dto/organization/SchoolYearRequest`, `ActivateSchoolYearRequest`, `SchoolYearDetailResponse` · M `mapper/organization/SchoolStructureMapper` · C `service/organization/SchoolYearService` + `impl/SchoolYearServiceImpl` · C `controller/organization/SchoolYearController`.
- Tests: `SchoolYearServiceImplTest`, `SchoolYearControllerTest`, `SchoolYearServiceIntegrationTest`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0; snapshot `docs/api/openapi.yaml`; Postman folder "Năm học".

### Phase sau (chưa làm)
Điểm trường, nhóm tuổi & lớp, phòng, giờ chốt điểm danh, phân công giáo viên/tổ trưởng, xếp lớp năm mới + readiness. Tài khoản nhân sự: đối chiếu G94-60 trước.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | AUTH-01 | `SchoolYearServiceImplTest#createsPlannedYear`, `SchoolYearControllerTest#createReturns201` | 1 | |
| AC-2 | AUTH-02 | `SchoolYearServiceImplTest#vicePrincipalCannotCreate`, `SchoolYearControllerTest#vicePrincipalCannotCreate` | 1 | |
| AC-3 | — | `SchoolYearServiceImplTest#duplicatedNameIsConflict`, `#overlappingDatesIsConflict`, `SchoolYearServiceIntegrationTest#overlappingYearIsRejected` | 1 | |
| AC-4 | — | `SchoolYearServiceIntegrationTest#createListActivateKeepsSingleActiveYear`, `#databaseRejectsSecondActiveYear` | 1 | |
| AC-5 | — | `SchoolYearServiceImplTest#deleteBlockedWhenYearHasClasses`, `SchoolYearServiceIntegrationTest#deleteBlockedByClassroom` | 1 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| V10 suy trạng thái năm cũ theo ngày hiện tại | Năm dev seed có thể thành ACTIVE/CLOSED khác mong muốn | Rule rõ trong migration; HT chỉnh lại qua API | Migration mới bỏ cột/index |
| FE đang dùng tên năm làm id | FE phải đổi sang id số | Ghi trong tác động FE | — |

## Validation log
- 2026-10-22: phase 1 code xong, chưa commit (chưa có mã Jira).

## Progress log
- 2026-10-22: Phase 1 (năm học) xong trên working tree, chưa commit. `node scripts/verify.mjs` → `VERIFY PASS full | tests 262 fail 0 err 0 skip 0`. Snapshot OpenAPI và Postman folder "6. Năm học" đã cập nhật.
- 2026-10-22: Mở rộng phạm vi theo yêu cầu Cần (toàn menu Cấu hình trường trừ Phân công PHT, Vai trò & quyền). Xong: điểm trường (V11), phòng (V12), nhóm tuổi & lớp + cấu trúc năm học + sao chép lớp (V13), giờ chốt điểm danh (V14, bỏ reopenWindowMinutes theo Cần 21/10). `node scripts/verify.mjs` → `VERIFY PASS full | tests 304 fail 0 err 0 skip 0`.
- 2026-10-22: Dừng trước Phân công giáo viên/tổ trưởng, Xếp lớp năm mới, Tài khoản nhân sự: DB chưa biết vai trò/điểm trường của nhân sự (role chỉ ở Keycloak, `user_accounts` không có campus) và xếp lớp phải ghi `enrollments` (module child, school-structure không được gọi ngược). Chờ quyết định.
- 2026-10-22: Phân công GV & tổ trưởng (V15) xong theo phương án C tạm (TeacherDirectory). Xếp lớp năm mới + readiness + kích hoạt chuyển trẻ (V16) đặt ở module child — **PROPOSED**, chờ Cần/nhóm xác nhận (ADR-0004); `POST /school-years/{id}/activate` chuyển sang `YearTransitionController`. Tài khoản nhân sự: chờ chọn A/B/C.
