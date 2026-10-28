# Module — education-plan

Feature: `eduplan` (`docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang code (mục tiêu năm học, cấu hình, tài liệu lớp xong BE, G94-226; AI chưa làm)

## Mục đích
Soạn và duyệt kế hoạch giáo dục theo mẫu MN Thái Dương, nối bằng số MT tới đánh giá trẻ:
- **Kế hoạch năm học** theo nhóm tuổi, áp dụng toàn trường: lĩnh vực > lĩnh vực con > nhóm kết quả mong đợi > MT n > nội dung; chủ đề, đợt nghỉ; tích nội dung vào chủ đề. Tổ trưởng và Phó HT cùng soạn (khóa Đang sửa), Tổ trưởng gửi, Phó HT ban hành.
- **Cấu hình**: tên lĩnh vực, chuỗi người ký, dòng hoạt động của kế hoạch tuần.
- **Tài liệu của lớp**: chủ đề nhánh (cả năm), kế hoạch chủ đề, giáo án tuần (kế hoạch tuần + 5 kế hoạch ngày); hồ sơ chủ đề gửi, duyệt một lần theo chuỗi.
- Luồng đầy đủ: `docs/business/flows/education-plan.md`.

## Owns
- Kế hoạch năm: `EduYearPlan` (kèm khóa Đang sửa), `EduGoalRow`, `EduGoalContent`, `EduTheme`, `EduBreak`, `EduContentThemeTick`, lịch sử `EduPlanEvent`.
- Danh mục và cấu hình: `EduDomain`, `EduExpectedResult`, `EduSettings` (chuỗi người ký, dòng hoạt động tuần).
- Tài liệu của lớp: `EduClassDoc`, một bảng cho chủ đề nhánh, kế hoạch chủ đề, giáo án tuần + 5 kế hoạch ngày. Khóa, trạng thái là cột thường có ràng buộc; nội dung, người duyệt, lịch sử là JSONB (bảng tuần đổi theo cấu hình dòng, luôn đọc cả tài liệu).

## KHÔNG owns
Năm học, nhóm tuổi, lớp, phân công (school-structure); tài khoản/role (identity-access); kết quả đánh giá trẻ theo MT (learning-observation — chỉ đọc giáo án đã duyệt của module này).

## Rules (ID)
EDU-01..23 (`docs/business/BUSINESS_RULES.md`), OBS-07, AI-*, AUTH-03. ADR-0017, ADR-0006, ADR-0003.

## Flows
`docs/business/flows/education-plan.md`. Sequence mục tiêu năm học SD-01..SD-10: `huong_dan_nghiep_vu/sequence/muc_tieu_nam_hoc_sequence.drawio` (ngoài repo).

## Phụ thuộc
- Dùng: school-structure (`SchoolStructureService`, `StaffAssignmentService`), identity-access (`AccountService.findFullNames`), `security/authorization`; AI qua `integration/` (khi làm gợi ý chủ đề nhánh, tiến trình giờ học). Không đọc repository của module khác.
- Được dùng bởi: learning-observation (đọc MT, hoạt động của giáo án đã duyệt — EDU-19).

## API & bảng
- Có: `GET/POST /education-plans/goals`, `GET/PUT/DELETE /education-plans/goals/{planId}`, `POST /education-plans/goals/{planId}/submit|issue|return|start-editing|finish-editing`, `GET/PUT /education-plans/config` (cấu hình: lĩnh vực, người ký, dòng tuần — PUT cho HT, PHT, quyền `plans.config`), `GET /education-plans/expected-results` (`docs/api/openapi.yaml`).
- Có: `GET /education-plans/class-docs?schoolYearId=` (tài liệu + lớp + giáo viên/tổ trưởng trong phạm vi người xem), `PUT /education-plans/class-docs/{kind}/{docKey}` (giáo viên của lớp lưu; tạo mới nếu chưa có), `POST /education-plans/class-docs/{kind}/{docKey}/actions` (`submit|approve|reject` + `note`). `kind` = `branch|theme|week`; `docKey` do Web đặt theo lớp: `bp_<lớp>_<kế hoạch năm>`, `tp_<lớp>_<chủ đề>`, `wp_<lớp>_<thứ Hai>`, BE kiểm khớp.
- PROPOSED (AI, chưa làm): `POST /education-plans/ai/branch-names`, `/ai/branch-names/rewrite`, `/ai/lesson-steps`.
- Code: `EduYearPlanController`, `EduYearPlanService(Impl)`, `EduConfigService(Impl)`, `EduClassDocController`, `EduClassDocService(Impl)`, quyền ở `security/authorization/EduPlanAccessPolicy` (EDU-06, EDU-08), job nhả khóa `EduPlanLockCleanupConfig`.
- PUT kế hoạch năm gửi cả cây: dòng/nội dung/chủ đề có id thì cập nhật, không id thì thêm, vắng thì xóa; nội dung tích chủ đề qua `themeKeys` trỏ `themes[].key` (chủ đề mới chưa có id).
- Bảng `edu_*`: `docs/database/DATA_DICTIONARY.md`.

## PENDING
Không có câu hỏi nghiệp vụ mở. Còn lại: AI gợi ý (EDU-20), đọc giáo án đã duyệt cho đánh giá trẻ (EDU-19).

## Known pitfalls
- Status do BE quyết định. Không nhận status từ client.
- Khóa Đang sửa (EDU-10): mọi thao tác ghi đọc kế hoạch bằng `findForUpdateById` (khóa dòng) để hai người bấm sửa cùng lúc không cùng lấy được khóa; khóa quá hạn coi như trống ngay cả khi job (1 phút/lần) chưa chạy.
- Tài liệu lớp: lịch tuần (`teachingWeeks`) phải tính giống Web (`yearWeeks` trong `CareNest_FE/src/models/education-plan/classPlan.js`): bỏ tuần trùng đợt nghỉ, chủ đề bắt đầu T7/CN thì từ Thứ Hai kế tiếp; lệch thì giáo án tuần bị từ chối hoặc gửi bị chặn.
- Gửi / duyệt hồ sơ chủ đề đổi trạng thái cả kế hoạch chủ đề và giáo án các tuần nhánh trong cùng giao dịch (khóa dòng tài liệu chính).
- MT đánh số lại khi xóa hoặc chèn dòng; module khác tham chiếu MT qua id dòng, không qua số.
- Kế hoạch đã ban hành bị khóa sửa, vì lớp dùng chủ đề, đợt nghỉ và MT của nó.
- Thứ tự chủ đề, đợt nghỉ = thứ tự mảng trong PUT (`sort_order`); dấu tích lưu theo chênh lệch, không xóa JPQL rồi ghi lại ([BUG-261023-edu-theme-tick-lost-on-resave](../knowledge/incidents/BUG-261023-edu-theme-tick-lost-on-resave.md)).
- Kế hoạch chủ đề lưu luôn MT, nội dung lấy từ kế hoạch năm để bản đã duyệt không đổi theo (EDU-15).
- Hai chỗ comment FE lệch code, làm BE theo code: mock ghi chủ đề nhánh "Phó HT duyệt" nhưng chuỗi mặc định là Tổ trưởng → Phó HT; gợi ý người ký `week` ghi "mỗi tuần duyệt riêng" nhưng code duyệt cả hồ sơ chủ đề một lần (EDU-17).
- Mã YCCĐ (TC1.1…) trong `educationPlanConstants.js` của FE chỉ còn để đọc dữ liệu cũ; không dùng cho dữ liệu mới (EDU-09).

## Related issues
- [BUG-261023-edu-theme-tick-lost-on-resave](../knowledge/incidents/BUG-261023-edu-theme-tick-lost-on-resave.md)

## Đọc thêm khi
Đổi quyền hoặc phân công Tổ trưởng ⇒ `docs/business/USER_ROLES.md`, ADR-0003. Đụng đánh giá theo MT ⇒ card learning-observation, ADR-0006.
