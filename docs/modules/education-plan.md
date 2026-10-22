# Module — education-plan

Feature: `eduplan` (`docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang code (mục tiêu năm học, G94-226)

## Mục đích
Kế hoạch giáo dục theo mẫu MN Thái Dương. Phần đang làm là kế hoạch năm học theo nhóm tuổi, áp dụng toàn trường:
- lĩnh vực > lĩnh vực con > nhóm kết quả mong đợi > MT n > nội dung giáo dục;
- chủ đề và đợt nghỉ;
- tích nội dung vào chủ đề;
- duyệt Tổ trưởng → Phó HT.

## Owns
- Kế hoạch năm: `EduYearPlan`, `EduGoalRow`, `EduGoalContent`, `EduTheme`, `EduBreak`, `EduContentThemeTick`.
- Lịch sử: `EduPlanEvent` (người, thời điểm ban hành lưu ở `EduYearPlan.issuedBy/issuedAt`).
- Danh mục: `EduDomain`, `EduExpectedResult`.
- Sau này thêm: chủ đề nhánh, kế hoạch chủ đề, kế hoạch tuần/ngày.

## KHÔNG owns
Năm học, nhóm tuổi, lớp, phân công (school-structure); tài khoản/role (identity-access); đánh giá trẻ theo MT (learning-observation).

## Rules (ID)
EDU-01..09 (`docs/business/BUSINESS_RULES.md`), AUTH-03. ADR-0017, ADR-0003.

## Flows
Sequence SD-01..SD-10 (thư mục nghiệp vụ của team: `huong_dan_nghiep_vu/sequence/muc_tieu_nam_hoc_sequence.drawio`, ngoài repo).

## Phụ thuộc
- Dùng: school-structure (`SchoolStructureService`, `StaffAssignmentService`), identity-access (`AccountService.findFullNames` lấy tên người soạn/gửi/ban hành), `security/authorization`; không đọc repository của module khác.
- Được dùng bởi: learning-observation (đọc MT đã ban hành).

## API & bảng
- API: `GET/POST /education-plans/goals`, `GET/PUT/DELETE /education-plans/goals/{planId}`, `POST /education-plans/goals/{planId}/submit|issue|return`, `GET /education-plans/config`, `GET /education-plans/expected-results` (`docs/api/openapi.yaml`).
- Code: `EduYearPlanController`, `EduYearPlanService(Impl)`, `EduConfigService(Impl)`, quyền ở `security/authorization/EduPlanAccessPolicy` (EDU-06, EDU-08).
- PUT gửi cả cây: dòng/nội dung/chủ đề có id thì cập nhật, không id thì thêm, vắng thì xóa; nội dung tích chủ đề qua `themeKeys` trỏ `themes[].key` (chủ đề mới chưa có id).
- Bảng `edu_*`; schema ở `docs/database/DATA_DICTIONARY.md` khi có migration.

## PENDING
Không có (các câu hỏi đã chốt 22/10).

## Known pitfalls
- Status do BE quyết định. Không nhận status từ client.
- MT đánh số lại khi xóa hoặc chèn dòng; module khác tham chiếu MT qua id dòng, không qua số.
- Kế hoạch đã ban hành bị khóa sửa, vì lớp dùng chủ đề và đợt nghỉ của nó.

## Related issues
Chưa có.

## Đọc thêm khi
Đổi quyền hoặc phân công Tổ trưởng ⇒ `docs/business/USER_ROLES.md`, ADR-0003.
