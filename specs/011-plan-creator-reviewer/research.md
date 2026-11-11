# Research — Education workflow policy

06/11/2026. Chỉ đọc source BE/Web, không đọc dữ liệu thật; APP source không có cục bộ. Quyết định kỹ thuật là đề xuất để duyệt.

## Evidence

| Source | Hiện trạng và hệ quả |
| --- | --- |
| `src/main/java/com/carenest/service/eduplan/impl/EduConfigServiceImpl.java:82` | PLAN_CONFIG cho HT/PHT; policy mới cần principal hiệu lực |
| `src/main/java/com/carenest/controller/eduplan/EduConfigController.java:40` | PUT full config chứa signers: phải khóa đường đổi workflow gián tiếp |
| `src/main/resources/db/migration/V27__create_edu_settings.sql:13` | Seed TL/VP trùng với creator roles hiện tại: không backfill mù |
| `src/main/java/com/carenest/entity/eduplan/EduYearPlan.java:57`, `EduClassDoc.java:88` cùng thư mục | ownerId/createdBy bất biến: baseline chắc chắn |
| `src/main/java/com/carenest/entity/eduplan/EduClassDoc.java:130`, `:135` | updatedBy đổi cả khi approve/reject: không dùng làm coauthor |
| `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java:1135` | Event không ghi đủ mọi content save: không khôi phục đủ coauthor |
| `src/main/java/com/carenest/service/eduplan/impl/EduClassDocServiceImpl.java:433`, `:897` | Snapshot chain ưu tiên: cần thay pending bằng current policy |
| Cùng file `:483` | Approval có userId/name/roleKey/at: tính theo roleKey/vòng, không suy từ label |
| `src/main/java/com/carenest/utils/enums/EduPlanStatus.java:4` | Source year hiện chỉ PENDING_VP; target sau quyết định mới là REVIEW + waitingRole, không thêm status theo role |
| `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java:678`, `:1388` | VP được sửa pending: phải tách review khỏi author lease |
| `src/main/java/com/carenest/repository/eduplan/EduYearPlanRepository.java:33`, `EduClassDocRepository.java:24` | Có document locks; settings chưa lock/version |
| `src/main/java/com/carenest/service/eduplan/impl/EduClassDocServiceImpl.java:440`, `:864` | Dossier gồm theme + weeks: hợp contributors cả hồ sơ |
| `src/main/java/com/carenest/repository/observation/ThemeEvaluationRepository.java:54`, `src/main/java/com/carenest/service/observation/impl/ThemeEvaluationServiceImpl.java:536` | issuedBy đang bị hiểu là PHT ký: không gán tên HT vào ô PHT |

## Business decisions resolved in planning

User 06/11/2026: mỗi loại có ít nhất một role soạn/duyệt, không lặp; sửa/gửi lại sau trả lại và version mới duyệt từ đầu; legacy dùng creator đã biết làm baseline, theo dõi coauthor từ nâng cấp và ghi rõ giới hạn; seed year TL→VP, branch/theme GV→TL→VP. Các đáp án nằm trong spec. Không còn unknown nghiệp vụ chặn thiết kế.

## D1 — Storage

**Decision**: Policy JSONB/revision trong settings; year thêm approvals/round; class thêm round; một contributor ledger có FK year hoặc class. **Rationale**: Có đúng ba policy, approvals nhỏ và luôn đọc theo hồ sơ. Ledger cần FK/unique, không chỉ nhét ID trong JSON. **Alternatives**: chỉ thêm creator roles không giải quyết multi-step/provenance; normalized workflow-instance/step framework tăng scope không cần.

## D2 — Scope, role và candidate

**Decision**: Business role predicates tách khỏi permission grants; full JWT roles của caller + assignment hiệu lực. Reviewer-precedence áp theo loại hồ sơ: có business reviewer-role hiệu lực ở bất kỳ phân công nào thì không được author cùng loại qua role khác; sau đó kiểm scope từng hành động riêng. Year: GV theo nhóm tuổi lớp đang dạy, TL đúng nhóm tuổi, HT/PHT theo education scope đang có; class: GV lớp, TL các lớp nhóm tuổi, HT/PHT school-wide như source hiện tại. Không lấy permission quản lý tài khoản/campus để mở rộng dữ liệu giáo dục. Không dùng UI single `user.role`.

Cấp soạn kéo theo đọc nội dung cần soạn trong cùng scope; reviewer được đọc bản chờ để duyệt nhưng không có lease sửa. Quyền chỉ đọc ngoài tác vụ không mở rộng. Đây là delta với EDU-06 phải được duyệt, không khẳng định viewScope cũ tự hỗ trợ. Candidate IDs batch từ phân công hiệu lực; lọc ACTIVE dùng đúng helper AccountService.findActiveUserIds user cho phép riêng sau lệnh fix, không thêm API hoặc quyền tài khoản; không đọc repo liên module, không gọi Keycloak per-document. Caller vẫn kiểm token roles; assignment/account thay đổi đánh giá request kế tiếp.

**Alternatives**: chỉ đổi controller hoặc chỉ đổi policy đều không đủ, vì year create/submit/issue/return và class save/adjustment đều hardcode entry roles.

## D3 — Round và reconciliation

**Decision**: Engine nhận current policy + current-round approvals + contributor union + candidates. Policy-only change giữ role đã duyệt, chỉ bước thiếu theo thứ tự mới; role bị bỏ giữ trong history. Mọi role chuỗi mới đã duyệt hợp lệ thì hoàn tất; kết quả engine không phụ thuộc trigger. Nếu chỉ còn bước giữa thiếu candidate được phép skip và role cuối đã duyệt thì cũng complete, ví dụ [TL thiếu người, VP đã duyệt]; không ghi fake approval cho skip. Không biến bước thiếu người thành human approval; bước cuối chưa duyệt thiếu người phải chờ. Sau reject/sửa/gửi lại tăng round, lưu history cũ nhưng không tính approvals cũ. Version mới không mang active approvals của nguồn.

Contributor exclusion tồn tại suốt tài liệu; không xóa người đã soạn vì đổi round. Copy/version ghi creator/copy actor đích, không nhận authors nguồn là authors đích. EDU-27 adjustments là phần bổ sung tách khỏi approved content nên không mở lại bản, nhưng vẫn kiểm author capability/ghi người sửa. **Alternatives**: giữ pending snapshot trái lựa chọn B; reset khi chỉ đổi policy trái yêu cầu không duyệt lại; giữ approvals sau sửa trái đáp án trong plan.

## D4 — Atomicity và publication

**Decision**: Workflow PUT exclusive settings lock, expected policy revision; khóa year ID tăng rồi class dossier/branch ID tăng và children. Ghi hồ sơ khác lấy shared settings lock trước document lock. Config + reconciliation commit/rollback cùng transaction. Metadata GET không ghi status; dùng read-only REPEATABLE_READ cùng transaction/connection cho policy/document/assignments/accounts, không ghép policy cũ với trạng thái mới sau concurrent commit. Assignment changes đánh giá lại eligibility, không tạo approval trong GET.

Prospective auto-completion year phải chạy publish validators hiện có (cây, mục tiêu, lịch chủ đề, version constraints). Một bản không hợp lệ thì từ chối policy save và rollback toàn bộ, không publish thiếu dữ liệu. Không gán HT cấu hình làm publisher: published_by dùng actual approval theo role cuối trong chuỗi mới được công nhận, issued_by legacy chỉ actual VP approval hoặc null; completedAt/reason ghi riêng POLICY_CHANGE, không tạo human approval giả. Policy change bump year revision/class member+root versions và thu hồi author lease không còn quyền. Class contract bắt buộc expected member/root version, compare dưới lock để request stale tuần tự cũng conflict; JPA version đơn lẻ không đủ. Audit expire-lock job/releaseExpiredLocks/finishEditing/heartbeat theo cùng lock order, không restore status cũ sau reconcile.

Dùng helper validator chung + workflow service chỉ phụ thuộc edu repos/services đọc account/assignment; tránh vòng ConfigService→YearService→ConfigService. Storage IO ngoài transaction dài theo convention hiện có, check lại quyền khi commit metadata.

## D5 — API và client

**Decision**: Endpoint policy riêng principal-only; GET config additive workflow metadata. Legacy PUT config không đổi được workflow qua signers; các field khác theo quyền hiện có. Signers là projection deprecated cho compatibility, không là nguồn phân quyền. Bản in dùng duy nhất review.printSnapshot đã lưu với role/label/names; legacy completed giữ baseline print snapshot tại nâng cấp, không khẳng định đó là lịch sử cấu hình chính xác trước tracking.

Giữ URL year `/issue`, nghĩa approve current step; final mới ban hành. Review không cần author lease nhưng vẫn revision/Idempotency-Key; content writes giữ headers lease cũ. New clients gửi action intent rõ, không suy submit từ desired PENDING_VP. Consumer cụ thể ở contracts.

**Alternatives**: HT-only toàn bộ PUT config sẽ đổi cả quyền các phần khác ngoài yêu cầu; giữ endpoint cũ tự hiểu signers thành workflow cho PHT là bypass. No global matrix/new Keycloak roles/dependencies.

## Resolved limits

Legacy provenance không đầy đủ là giới hạn đã được user chấp thuận, DTO nêu LEGACY_BASELINE/mốc tracking. APP source chưa có: chưa xác minh compatibility, phải kiểm trước rollout khi source khả dụng. Source eduplan còn dùng role gates theo EDU-08/AUTH-20; ADR-0024 đề xuất giữ policy local thay vì đưa vào ma trận global. Approval plan/contract/migration/ADR là gate con người; chưa được cấp quyền sửa FE/APP.


## Delta sau analyze — phạm vi và ba trạng thái

User chốt DRAFT/REVIEW/APPROVED (Nháp/Duyệt/Ban hành), REVIEW chỉ reviewer thao tác, tác giả không sửa/lấy lease. Migration đổi legacy pending về REVIEW, rejected về DRAFT giữ history, EDITING thành lease metadata trên trạng thái thật. Không đổi mã APPROVED để consumer cũ đang lọc ban hành không bị gãy. Hai tập xử lý policy là REVIEW và DRAFT-held-leases mất quyền, không chỉ bản đang duyệt. Snapshot bản in đã được user chốt; shape riêng print_snapshot/review.printSnapshot tại data-model §9.

Chỉ giáo dục: không sửa observation/organization; account chỉ đúng helper đọc ACTIVE-ID và test user cho phép sau đó, không feature khác. Đề xuất producer compatibility trong edu: published_by/completed_at là publisher thật; issued_by legacy chỉ actual VP approval hoặc null, không điền HT/TL. Test trong edu kiểm giá trị projection, không triển khai module quan sát.

Dependency U3 đã có phương án user cho phép06/11 sau lệnh fix: thêm đúng Set<Long> AccountService.findActiveUserIds(Collection<Long>) trong interface/implementation hiện có, dùng inherited UserAccountRepository.findAllById một batch và lọc UserStatus.ACTIVE. Empty input không query; missing/inactive IDs bị loại; không trả tên/PII, không endpoint/migration/quyền/business tài khoản mới. EduWorkflowService gom IDs candidate từ assignment, gọi helper một lần rồi giao tập ACTIVE vào evaluator; scope/author exclusion do giáo dục kiểm. Ngoại lệ source account này do user cho phép riêng, không phải mở nghiệp vụ module khác; không đọc repo chéo hoặc giả danh HT. Test helper tại service/account/impl và candidate integration tại eduplan. Implementation vẫn cần duyệt plan riêng, chưa tạo source trong lượt fix hồ sơ.


## Sửa I2 và I3 — 06/11/2026
Decision: hai migration expand/contract, không deploy trung gian. P1 thêm schema/REVIEW nhưng giữ legacy enums/constraints/caller behavior để T014 compile/full test thật. T030..T032/T045 chuyển logic/fixtures; T047 atomic source+schema cutover bỏ legacy constants và remap/siết CHECK, T051 full cuối. Loại phương án xóa enum sớm gây missing-symbol và phương án bỏ/giảm cổng verify. Contract print cũ đã thống nhất chỉ review.printSnapshot là nguồn ô ký/name/role; completionPolicy chỉ policy/role order. Đây là thiết kế, chưa migration/source.

## Binding lúc implementation06/11
- Mốc schema hiện tại V79; expand V80__expand_edu_workflow_policy.sql, contract V81__contract_edu_workflow_status.sql (kiểm lại trước khi tạo contract). Không sửa migration đã commit.
- Gap enumerate HT/PHT đã xác minh StaffAssignmentServiceImpl.findCampusConfirmerIds fallback; user cho phép helper read-only findActiveEducationReviewerAssignments(LocalDate) + test như task delta, không source nghiệp vụ tổ chức khác. Cần lấy đầy đủ HT dù có PHT rồi lọc ACTIVE qua AccountService; không giả actor hoặc đọc repository chéo.

## Ruling trong vòng implement/converge06/11
Quyền xóa hành chính kế hoạch năm giữ EDU-07 VP-only hiện hành: user xác định quyền soạn là tạo/soạn/sửa/gửi và không yêu cầu đổi quyền xóa. Cụm configured delete trong T030 được hiểu là tính capability và bảo toàn scope/trạng thái/revision theo rule xóa hiện hữu, không cấp xóa cho mọi author hoặc tự sửa rule CONFIRMED. Version/copy/content vẫn bắt buộc configured author và reviewer-precedence.


## 2026-11-06 — Legacy status triggers after contract cutover
V89 drops both V88 education status-normalization triggers and their functions. No active writer requires legacy pending/editing/rejected values: BE enum/status producers use DRAFT/REVIEW/APPROVED, and the only observed external writer was the DailyAssessment fixture, now updated to REVIEW with user authorization. Migration integration coverage verifies legacy values inserted during expand/contract normalize as expected, then direct writes of PENDING_VP after V89 fail the CHECK constraint for both year and class documents. Keep the compatibility remapping during migration only; reject post-cutover legacy writes to protect the three-state contract and workflow guards.
