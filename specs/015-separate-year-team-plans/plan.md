# Implementation Plan: Phân biệt kế hoạch năm học và kế hoạch tổ

> **Quyết định mới user 10/11/2026 — EDU-72:** Bỏ thao tác lập kế hoạch tổ lần đầu. Khi kế hoạch năm được ban hành, hệ thống tự tạo một TEAM DRAFT cùng năm học/nhóm tuổi, gắn phiên bản năm đã ban hành mới nhất và giao tổ trưởng duy nhất đang phụ trách tuổi đó soạn. User làm rõ “chỉ có một tổ trưởng của một nhóm tuổi, gửi cho người đó soạn”. Quyết định này thay phần “không tự lập tổ/giữ nút lập tổ/chọn nguồn thủ công” ở EDU-62 và tài liệu09/11; các quy tắc giữ nội dung đang soạn, rà soát và giữ tổ ban hành cũ làm nguồn lớp vẫn áp dụng. Đây là chỉ đạo user, không suy là xác nhận mới từ giáo viên trường.


**Branch**: `feature/G94-326-BE-FEAT-09-school-year-goals-backup` (không tạo/chuyển nhánh). **Date**: 2026-11-09. **Spec**: [spec.md](spec.md).

**Status**: Thiết kế sửa theo analyze và lệnh user “fix”; chưa phê duyệt triển khai/API/DDL hoặc sửa repo FE/APP. Làn L. Không coi các nhãn PASS cũ là bằng chứng triển khai.

## Summary và nguồn nghiệp vụ

Tách edu_year_plans thành bảng năm và edu_team_plans, giữ ID, toàn nội dung con, mapping, workflow, lịch sử và nguồn lớp. Feature đích bao gồm BE và FE: nhãn chờ duyệt đúng loại/tên, lập tổ vào form trực tiếp, chọn một annual APPROVED hiện hành, list đủ cột, ẩn UI phiên bản tổ và rà soát khi nguồn năm ban hành.

Bắt buộc đối chiếu [PROJECT_CONTEXT](../../docs/context/PROJECT_CONTEXT.md), [module card](../../docs/modules/education-plan.md), [flow](../../docs/business/flows/education-plan.md), [BUSINESS_RULES](../../docs/business/BUSINESS_RULES.md) và [bộ xác nhận09/11](../../docs/business/education-plan-confirmation-2026-11-09.md). Nghiệp vụ đích không suy từ code cũ; mức CONFIRMED không là delivery hay quyền triển khai. Các rule EDU-01/07/25/26/54/55/57/60/62 được trace trong tasks §Coverage.

- **D1**: một bộ bảng con; đúng **9 bảng nhận hai cột sở hữu**, liệt kê data-model §2. goal_contents/ticks suy qua cha; tác giả class doc giữ class_doc_id không ép owner header (data-model §2), FK ghép cây/theme được tái tạo; approvals là JSONB, EduGoalRow chỉ mapped superclass. Giữ ID và lineage, không bảng ánh xạ đổi ID.
- **D2 nghiệp vụ**: khi năm mới ban hành, cập nhật bản tổ đang mở trước; chỉ sinh nháp từ tổ APPROVED khi chưa có bản mở. Không lập tổ lần đầu; không sửa bản ban hành cũ/lớp. Thiết kế sửa đề xuất xử lý cùng transaction ban hành, thay event AFTER_COMMIT để không mất cập nhật.
- **D3 định danh đề xuất**: sequence edu_plan_id_seq dùng chung hai bảng, đặt lớn hơn max ID toàn bộ trong maintenance; giữ numeric endpoint. Kiểm trùng ID và tất cả writer/copy/default, không suy bảng theo tên.
- **Bảo vệ nghiệp vụ**: không ai xóa TEAM (FR-016); TT duyệt/trả lại theo policy/tuổi/lượt, không tự duyệt/coauthor (FR-017); server chặn sửa cây/import TEAM (FR-004).
- **D4 chủ đề/nhánh lớp**: annual chỉ là nguồn PHT; TEAM APPROVED là nguồn duy nhất của lớp. Chủ đề lớn lớp chỉ đọc; bản nhánh lớp tách theo chủ nhiệm/lớp/TEAM, không sửa mẫu PHT/tổ. Nhánh không còn workflow riêng: thuộc hồ sơ kế hoạch chủ đề khi gửi/duyệt. Trang “Chủ đề / Chủ đề nhánh” dùng một bảng Tháng/Chủ đề/Chủ đề nhánh/Tuần/Ghi chú; PHT lọc toàn trường, TT lọc đúng tuổi, GV chỉ đọc lớp mình.

## Technical Context

- Java 21, Spring Boot 4.1.1, Maven wrapper; Spring Data JPA, Flyway, PostgreSQL 17, springdoc, Keycloak Resource Server (ADR-0011). Không thêm dependency.
- Layer hiện có controller → service interface/impl → repository; LOG_PREFIX và error handling theo AGENTS/ADR-0014/0016.
- Một trường/hai campus; tổ dùng chung hai campus theo năm/tuổi; không multi-tenant.
- Bảng chính edu_year_plans/edu_team_plans; 9 owner trực tiếp, edu_team_review_items; baseline previous_source_plan_id lưu trên tổ.
- Tối đa một DRAFT/REVIEW mỗi loại/năm/tuổi, lineage cùng loại, nguồn năm khác quan hệ lịch sử tổ.
- Không đặt SLA “không tăng query” khi chưa có baseline/yêu cầu nghiệp vụ; tuân thủ tránh N+1, không thêm benchmark hạ tầng vào feature này.
- Test: JUnit/unit + DB integration trên dữ liệu tổng hợp; FE tests sẵn có và browser E2E cho thao tác UI. Service test không chứng minh số lần bấm hoặc ẩn nút.

## Phases và gates

1. **Thiết kế/phê duyệt**: T001/T002/T033/T034/T047. Trình plan làn L, DDL/sequence/baseline/cutover và contract cùng impact FE/APP. T047 ghi phê duyệt riêng theo phạm vi; task FE chỉ là kế hoạch, không cấp quyền sửa client.
2. **Expand + rehearsal dữ liệu**: T003/T029/T030 và phần DB T021. V96 local là đầu vào review, không bằng chứng đã migrate. Snapshot trước expand/cutover; kiểm thử schema đích/V97 trên scratch trước cutover thật. Không chuyển app sang bảng TEAM rỗng trong giai đoạn expand.
3. **BE trên schema đích scratch**: T004–T008/T010–T028 và T038–T042/T048–T050 theo dependency tasks; bảo vệ nguồn, cấm xóa, quyền TT, mapping/baseline, consumer hồ sơ lớp/nguồn đánh giá sau tách bảng, legacy NULL không tự gán nguồn, phiên cũ conflict và transaction lỗi/lặp/concurrent. T009 chưa chạy production ở phase này.
4. **FE được phê duyệt riêng**: T043–T046. Nhãn/form/list/versions/review UI dùng nhà component hiện có; test thực tế UI + API. APP chỉ đánh giá nếu có consumer; nếu có thay đổi phải có task/quyền riêng trước cutover.
5. **Kiểm chứng trước cutover**: T031/T032/T035/T036/T037 trên scratch; đối chiếu đủ payload/ownership/FK/lineage/nguồn lớp/legacy và sequence; API snapshot/check chọn theo impact, regression feature012/013 liên quan. Không đánh hoàn thành khi thiếu FE hoặc E2E skip chưa giải quyết.
6. **Cutover T009**: sau mọi gate trên, maintenance dừng writers/jobs, backup/restore rehearsal, transaction copy TEAM/chuyển FK/sequence/bỏ cột cũ theo data-model §2; đối chiếu trước xóa và sau chuyển, mở app mới sau kiểm smoke. Không tự chạy migration/deploy trong lượt tài liệu.

V96/V97 là slot dự kiến phải kiểm tra lại. Nếu V96 đã commit/applied, sửa thiết kế bằng migration mới; không chỉnh checksum. Không dùng app boot tự apply V97 lên DB đang dùng để kiểm thử. Mỗi phase làn L cần review theo plan được duyệt.

## Constitution Check — thiết kế, chưa chứng nhận runtime

| Nguyên tắc | Thiết kế/điều kiện kiểm chứng |
|---|---|
| I. Business truth | Nguồn hiện hành và FR-016/017/004 trace về EDU; không đổi nghiệp vụ theo code; các delta ngoài feature được ghi ranh giới |
| II. Stack/layer | Giữ stack và convention API; shared sequence + synchronous same-module call là đề xuất cần duyệt, ADR-0004 event còn PROPOSED, không viện dẫn như bắt buộc |
| III. Security/privacy | Kiểm server scope/policy/lease; cấm xóa TEAM; nguồn lớp TEAM; dữ liệu tổng hợp |
| IV. Verification/human control | Task test hành vi + scratch/cutover evidence; chưa chạy hoặc phê duyệt runtime |
| V. Traceability | Rule → FR/AC → task → quickstart/coverage; đọc trực tiếp, không harness/hook |

Không tuyên bố “gate pass” trước kiểm chứng/phê duyệt. Analysis tài liệu ghi trong [analysis.md](analysis.md); triển khai chỉ bắt đầu khi các gate thuộc phạm vi đó có bằng chứng.

## Project Structure

- Artifacts: spec.md, domain-spec.md, database-context.md, research.md, data-model.md, contracts/api.md, tasks.md, quickstart.md, analysis.md.
- Entity/repository eduplan: EduYearPlan, EduTeamPlan mới; EduPlanGoal, EduPlanSubDomain, EduPlanExpectedResult, EduTheme, EduBreak, EduPlanEvent, EduDocumentAuthor, EduPlanBranchTemplate, EduClassDoc; EduTeamReviewItem mới. EduGoalRow là superclass, không bảng/entity approval mới.
- Service eduplan: EduYearPlanService/impl chỉ năm; EduTeamPlanService/impl mới; impl/EduTeamUpdateGenerator và impl/EduPlanReviewDiff. Generator same-module đồng bộ, không listener bắt buộc.
- Consumer nguồn TEAM: EduClassDocServiceImpl (requireIssuedPlan/isSuperseded/batch/context/lock/copy/docKey), EduLessonQueryServiceImpl và caller observation theo đường phụ thuộc thực tế; giữ API lớp/docKey và lịch sử, T048 regression.
- Class branch/UI: `EduClassDocServiceImpl`, `EduWorkflowServiceImpl` và policy/repository phân quyền theo `StaffAssignment.isHomeroom`; `BranchPlanPage`, `BranchCalendarEditor`, `ClassPlanPage`, `ClassThemePlanPage`, `menuConfig` và route/sidebar. Không dùng workflow/config/signers riêng cho branch.
- Controller: EduYearPlanController; DTO YearPlanRequest/SummaryResponse/DetailResponse; mapper và EduPlanAccessPolicy hiện có. API spec docs/api/openapi.yaml cập nhật khi implement.
- Migration: V89__separate_team_plans.sql local + V90__drop_team_from_year_plans.sql dự kiến (hoặc migration mới theo history); docs/database/DATA_DICTIONARY.md, ERD.md cập nhật cùng implementation.
- FE (task có gate riêng): GoalListPage, GoalFormPage, GoalDetailPage, PlanReviewQueuePage; GoalTables, YearPlanSourceCard, planUi, VersionWidgets/VersionHistory; educationPlanApi/yearPlanMapper và test education-plan.

## Ranh giới và MVP

US1+US2 chỉ là increment có thể demo trên schema đích scratch **có FE**, không hoàn thành feature và không được ship giữa expand/contract. Mốc hoàn thành feature phải đủ US1–US4/SC-001..006, task bổ sung và cutover đã duyệt/kiểm chứng. Không thay đổi thời lượng/catalog/AI hoặc tự sửa APP. Regression nguồn lớp chỉ bảo vệ đường chuyển TEAM và EDU-26, không viết lại nghiệp vụ giáo án.
