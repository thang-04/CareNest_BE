# Module — education-plan

Feature: `eduplan` (`docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang code (mục tiêu năm học, cấu hình, tài liệu lớp xong BE, G94-226; AI chưa làm)

## Mục đích
Soạn và duyệt kế hoạch giáo dục theo mẫu MN Thái Dương, nối bằng số MT tới đánh giá trẻ:
- **Kế hoạch năm học** theo nhóm tuổi, áp dụng toàn trường: lĩnh vực > lĩnh vực con > nhóm kết quả mong đợi > MT n > nội dung; chủ đề, đợt nghỉ; tích nội dung vào chủ đề. Tổ trưởng và Phó HT cùng soạn (khóa Đang sửa), Tổ trưởng gửi, Phó HT ban hành.
- **Cấu hình**: tên lĩnh vực, chuỗi người ký, dòng hoạt động của kế hoạch tuần.
- **Tài liệu của lớp**: chủ đề nhánh (cả năm), kế hoạch chủ đề, giáo án tuần (kế hoạch tuần + 5 kế hoạch ngày); hồ sơ chủ đề gửi, duyệt một lần theo chuỗi.
- Luồng đầy đủ: `docs/business/flows/education-plan.md`.

## Owns
- Kế hoạch năm: `EduYearPlan` (kèm khóa Đang sửa), `EduPlanSubDomain`, `EduPlanExpectedResult`, `EduPlanGoal`, `EduGoalContent`, `EduTheme`, `EduBreak`, `EduContentThemeTick`, lịch sử `EduPlanEvent`.
- Danh mục và cấu hình: `EduDomain`, `EduExpectedResult`, `EduSettings` (chuỗi người ký, dòng hoạt động tuần).
- Tài liệu của lớp: `EduClassDoc`, một bảng cho chủ đề nhánh, kế hoạch chủ đề, giáo án tuần + 5 kế hoạch ngày. Khóa, trạng thái là cột thường có ràng buộc; nội dung, người duyệt, lịch sử là JSONB (bảng tuần đổi theo cấu hình dòng, luôn đọc cả tài liệu).

## KHÔNG owns
Năm học, nhóm tuổi, lớp, phân công (school-structure); tài khoản/role (identity-access); kết quả đánh giá trẻ theo MT (learning-observation — chỉ đọc giáo án đã duyệt của module này).

## Rules (ID)
EDU-01..28, EDU-39..41 (lịch chủ đề; `docs/business/BUSINESS_RULES.md`); EDU-29..38 (khảo sát team 27/10 từ `dev`, đổi số khi merge; EDU-31/32/35/38 SUPERSEDED theo code — xem flow); OBS-07, PAR-01 (EDU-37), AI-07, AUTH-03, AUTH-23. ADR-0020 (số cũ ADR-0017), ADR-0021, ADR-0018, ADR-0006, ADR-0003; phân quyền động: ADR-0017. ADR-0019 của `dev` (làm lại trên `dev`) lệch với việc đã merge nhánh này — chờ user chốt.

## Flows
`docs/business/flows/education-plan.md`. Sequence mục tiêu năm học SD-01..SD-10: `huong_dan_nghiep_vu/sequence/muc_tieu_nam_hoc_sequence.drawio` (ngoài repo).

## Phụ thuộc
- Dùng: school-structure (`SchoolStructureService`, `StaffAssignmentService`), identity-access (`AccountService.findFullNames`), `security/authorization`; AI qua `integration/` (khi làm gợi ý chủ đề nhánh, tiến trình giờ học). Không đọc repository của module khác.
- Được dùng bởi: learning-observation (đọc MT, hoạt động của giáo án đã duyệt — EDU-19).

## API & bảng
- Có: `GET/POST /education-plans/goals`, `GET/PUT/DELETE /education-plans/goals/{planId}`, `POST /education-plans/goals/{planId}/submit|issue|return|start-editing|finish-editing|editing-heartbeat`, `GET/PUT /education-plans/config` (cấu hình: lĩnh vực, người ký, dòng tuần — PUT cho HT, PHT, quyền `plans.config`), `GET /education-plans/expected-results` (`docs/api/openapi.yaml`).
- Có: `GET /education-plans/class-docs?schoolYearId=` (tài liệu + lớp + giáo viên/tổ trưởng trong phạm vi người xem), `PUT /education-plans/class-docs/{kind}/{docKey}` (giáo viên của lớp lưu; tạo mới nếu chưa có), `POST /education-plans/class-docs/{kind}/{docKey}/actions` (`submit|approve|reject` + `note`). `kind` = `branch|theme|week`; `docKey` do Web đặt theo lớp: `bp_<lớp>_<kế hoạch năm>`, `tp_<lớp>_<chủ đề>`, `wp_<lớp>_<thứ Hai>`, BE kiểm khớp.
- PROPOSED (AI, chưa làm): `POST /education-plans/ai/branch-names`, `/ai/branch-names/rewrite`, `/ai/lesson-steps`.
- Code: `EduYearPlanController`, `EduYearPlanService(Impl)`, `EduConfigService(Impl)`, `EduClassDocController`, `EduClassDocService(Impl)`, quyền ở `security/authorization/EduPlanAccessPolicy` (EDU-06, EDU-08), job nhả khóa `EduPlanLockCleanupConfig`.
- PUT kế hoạch năm gửi cả cây: dòng/nội dung/chủ đề có id thì cập nhật, không id thì thêm, vắng thì xóa; nội dung tích chủ đề qua `themeKeys` trỏ `themes[].key` (chủ đề mới chưa có id).
- Bảng `edu_*`: `docs/database/DATA_DICTIONARY.md`.

## PENDING
Lịch chủ đề EDU-39..41 đã chốt 30/10 (đóng P-29). Mục khảo sát mâu thuẫn code (EDU-31, EDU-32, EDU-35, EDU-38) đã chốt theo code 29/10. Còn lại: AI gợi ý (EDU-20), đọc giáo án đã duyệt cho đánh giá trẻ (EDU-19).

## Known pitfalls

- V35 backfill với FK deferred phải SET CONSTRAINTS ALL IMMEDIATE trước DDL; [incident](../knowledge/incidents/BUG-261029-hierarchy-deferred-ddl.md). Lưu chuyển cha phải flush trước khi xóa tiêu đề cũ để CASCADE không xóa MT còn giữ.

- Thiết kế ba bảng cha–con (user, 29/10/2026): yêu cầu tách lĩnh vực con, nhóm kết quả mong đợi, mục tiêu thành ba bảng vật lý có FK; user chọn giữ cấp cha tùy chọn để bảo toàn dữ liệu đang có. Source dùng ba entity/repository và V35 chuyển dữ liệu từ `edu_goal_rows`; `EduGoalRow` chỉ là mapped superclass. Migration giữ ID/lineage, API giữ rows/kind. Xem phần bổ sung DB trong [plan mục tiêu năm](../plans/active/2026-10-22-muc-tieu-nam-hoc.md).
- Status do BE quyết định. Không nhận status từ client.
- Khóa Đang sửa (EDU-10): mọi thao tác ghi đọc kế hoạch bằng `findForUpdateById` (khóa dòng) để hai người bấm sửa cùng lúc không cùng lấy được khóa; khóa quá hạn coi như trống ngay cả khi job (1 phút/lần) chưa chạy.
- Tài liệu lớp: lịch tuần (`teachingWeeks`) phải tính giống Web (`yearWeeks` trong `CareNest_FE/src/models/education-plan/classPlan.js`): bỏ tuần trùng đợt nghỉ, chủ đề bắt đầu T7/CN thì từ Thứ Hai kế tiếp; lệch thì giáo án tuần bị từ chối hoặc gửi bị chặn.
- Gửi / duyệt hồ sơ chủ đề đổi trạng thái cả kế hoạch chủ đề và giáo án các tuần nhánh trong cùng giao dịch (khóa dòng tài liệu chính).
- MT đánh số lại khi xóa hoặc chèn dòng; module khác tham chiếu MT qua id dòng, không qua số.
- Kế hoạch đã ban hành bị khóa sửa, vì lớp dùng chủ đề, đợt nghỉ và MT của nó; sửa = tạo phiên bản mới (EDU-25), không sửa dòng của bản đã ban hành.
- So sánh phiên bản dùng lineage hiệu dụng `COALESCE(lineage_id, id)`; MT đánh lại số không phải là đổi. Tra "hiện hành" bằng truy vấn chuỗi, không lưu cờ.
- Thứ tự chủ đề, đợt nghỉ = thứ tự mảng trong PUT (`sort_order`); dấu tích lưu theo chênh lệch, không xóa JPQL rồi ghi lại ([BUG-261023-edu-theme-tick-lost-on-resave](../knowledge/incidents/BUG-261023-edu-theme-tick-lost-on-resave.md)).
- Kế hoạch chủ đề lưu luôn MT, nội dung lấy từ kế hoạch năm để bản đã duyệt không đổi theo (EDU-15).
- Hai chỗ comment FE lệch code, làm BE theo code: mock ghi chủ đề nhánh "Phó HT duyệt" nhưng chuỗi mặc định là Tổ trưởng → Phó HT; gợi ý người ký `week` ghi "mỗi tuần duyệt riêng" nhưng code duyệt cả hồ sơ chủ đề một lần (EDU-17).
- Mã YCCĐ (TC1.1…) trong `educationPlanConstants.js` của FE chỉ còn để đọc dữ liệu cũ; không dùng cho dữ liệu mới (EDU-09).

- Lấy mục tiêu giữ context cả sau normalize/mở lại: thêm sub đầu tiên vào lĩnh vực đích vốn không-sub có thể đổi cha goal cũ; xem [case](../knowledge/incidents/CASE-261028-year-plan-import-first-sub-reparent.md). Helper import mới đã có guard và regression round-trip.

## Related issues
- [CASE-261027-activity-media-positional-identity](../knowledge/incidents/CASE-261027-activity-media-positional-identity.md): không ghép học liệu theo ID vị trí đơn lẻ.
- [BUG-261023-edu-theme-tick-lost-on-resave](../knowledge/incidents/BUG-261023-edu-theme-tick-lost-on-resave.md)

## Đọc thêm khi
Đổi quyền hoặc phân công Tổ trưởng ⇒ `docs/business/USER_ROLES.md`, ADR-0003. Đụng đánh giá theo MT ⇒ card learning-observation, ADR-0006.

## Lưu mục tiêu năm (Tiền duyệt 27/10/2026)

EDU-10/22/23: phiên sửa riêng từng tab, TTL 90s và heartbeat 20s; cùng tài khoản gọi start-editing bằng phiên mới thì nhận quyền ngay (`EduYearPlan.acquireSession`), phiên cũ nhận 409 ở heartbeat/lưu, người khác vẫn 409 tới khi hết hạn; FE chỉ ghi khi bấm Lưu/Ctrl+S hoặc gửi/ban hành (bỏ tự lưu 2s/15s, Tiền chốt 27/10), kiểm revision và request ID chống lưu trùng. PENDING_VP giữ trạng thái khi khóa; draft giữ nội dung chưa hoàn chỉnh, gửi/ban hành mới kiểm tra đầy đủ. V30 bổ sung phiên/revision; contract ghi cần X-Edit-Session, X-Plan-Revision, X-Request-Id. Bản phục hồi Web riêng theo user/kế hoạch/slot; không trộn hay ghi đè khi xung đột. Chi tiết: [đặc tả](../../specs/001-year-plan-autosave/spec.md).

## Học liệu hoạt động (Tiền yêu cầu, 27/10/2026)
Mọi binary ảnh/video/slide/tệp lưu S3 private, metadata riêng trong edu_learning_resources; backend scope check, không public bucket/URL. Không đổi MT/chuỗi duyệt EDU-12..18. Sửa hoạt động làm fingerprint không khớp thì cần gắn lại. Chi tiết [feature002](../../specs/002-class-plan-teaching-media/spec.md), [S3](../system/EDUCATION_MEDIA_S3.md), ADR-0018. Tích hợp S3 thật cần cấu hình và smoke riêng.

## Mã lỗi (chuẩn ERROR_CONTRACT, merge daily-attendance 28/10/2026)
Mọi lỗi của module ném `GlobalException.of(ApiCode.X)`; response có `errorCode` 4 số, Web/App phân biệt bằng mã, không so `desc`. Dải: 70xx kế hoạch năm (7006 `Đang được {0} sửa`, 7020 mất phiên sửa, 7021 revision cũ, 7022 chờ duyệt chỉ Phó HT sửa…), 71xx tài liệu lớp + học liệu (7115–7120), 72xx cấu hình; tệp/S3 dùng 80xx (8003 kho lỗi, 8004 loại tệp, 8005 quá cỡ, 8006–8013). Thiếu part multipart/header ⇒ 9003. Danh sách đầy đủ: `ApiCode.java`, `docs/contracts/ERROR_CONTRACT.md`.

## Phiên bản kế hoạch năm, kế hoạch điều chỉnh (Tiền chốt 28/10/2026)

EDU-25/26/27: mỗi phiên bản là một dòng `edu_year_plans` (`version_no`, `source_plan_id`); dòng con chép giữ `lineage_id` (NULL = chính id). `POST /goals/{id}/versions` tạo phiên bản n+1, `GET /goals/{id}/changes?fromPlanId=` so sánh theo lineage (`YearPlanVersionDiff`). Tài liệu lớp tách theo `year_plan_id`; khóa tuần phiên bản ≥2 `wp_<lớp>_<thứ Hai>_<id kế hoạch>`; bản chép nhớ `source_doc_id`, chặn gửi 7121 khi còn ô đỏ, ghi trên phiên bản cũ 7122. Ô "Kế hoạch điều chỉnh và bổ sung" ở cột `adjustments`, `PUT /class-docs/week/{key}/adjustments`. V32. Chi tiết: [feature004](../../specs/004-year-plan-versioning/spec.md).

## Lấy mục tiêu từ kế hoạch có sẵn (user chốt 28/10/2026)

EDU-24: giữ quyền nguồn EDU-06; chỉ Phó HT lấy khác nhóm tuổi, Tổ trưởng chỉ nhóm tuổi mình (có thể khác năm). Tích từng mục tiêu hoặc tất cả trong một nguồn; chỉ chép mục tiêu/nội dung, không chép chủ đề, đợt nghỉ, lịch hoặc dấu tích. Giữ dữ liệu đích, bản nguồn và luồng sửa ba bước. Source Web đã triển khai, 13/13 E2E thật đạt và T019/T021 hoàn tất: [feature003](../../specs/003-import-year-plan-goals/spec.md).

## Lấy mục tiêu từ Chương trình của Bộ (Tiền chốt 28/10/2026)

EDU-28: danh mục chỉ đọc nạp bằng V33/V34 (`edu_curriculum_*`) từ VBHN 01/VBHN-BGDĐT: 327 mục tiêu (nhà trẻ 41, 3–4 tuổi 84, 4–5 tuổi 99, 5–6 tuổi 103) kèm nội dung, 423 gợi ý nội dung (mục C). API `GET /education-plans/curriculum/{sources,goals,contents}`; kế hoạch năm gửi/trả `curriculumGoalId` (+ `curriculumGoalText`, `curriculumSectionRef`), sai nhóm tuổi ⇒ 7030. Lấy vào kế hoạch làm ở Web như EDU-24 rồi lưu tay. Công cụ tách và kiểm: specs/005-ministry-goal-catalog/research.md.
