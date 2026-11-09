# Spec015 — đối chiếu sau remediation 09/11/2026

Phạm vi: sửa artifact theo lệnh user “fix”, rồi đối chiếu lại tài liệu. Không triển khai code/FE, SQL runtime, migration/deploy; không commit/push. Không phải số đo test/code coverage.

## Nguồn và thứ tự đối chiếu

[PROJECT_CONTEXT](../../docs/context/PROJECT_CONTEXT.md) §Context kế hoạch giáo dục, [module card](../../docs/modules/education-plan.md), [flow](../../docs/business/flows/education-plan.md), [BUSINESS_RULES](../../docs/business/BUSINESS_RULES.md) EDU-01/07/10/25/26/54/55/57/60/62, [bộ xác nhận](../../docs/business/education-plan-confirmation-2026-11-09.md), [constitution](../../.specify/memory/constitution.md). Quyết định mới nhất có nguồn áp dụng trong đúng phạm vi; nghiệp vụ đích tách source/delivery. Không nâng mọi chi tiết EDU-56/60 thành CONFIRMED bằng câu trả lời khác phạm vi.

Đọc thêm migration V35/V80/V83/V84 và entity EduDocumentAuthor để kiểm schema, ADR-0004 để không coi event PROPOSED là bắt buộc; chỉ đọc đường dẫn consumer FE để xác định task, không sửa FE.

## Findings trước → remediation trong tài liệu

| ID cũ | Remediation | Kiểm chứng được lập task |
|---|---|---|
| G1 FE thiếu | Plan đích gồm BE+FE, không coi đánh giá impact là implementation | T043–T046, gate T047; SC-001..005 |
| G2 cấm xóa tổ | FR-016, US4.3, AC-17, contract DELETE rõ TEAM mọi trạng thái | T008/T010/T044/T046; quickstart §1/§4 |
| G3 TT duyệt | FR-017, US1.5, AC-15, policy tuổi/lượt/coauthor | T039/T044/T046; §1 |
| I1 DDL legacy/index | Nguồn nullable + CHECK legacy; partial UNIQUE INDEX ngoài CREATE TABLE; DROP INDEX cho unique V83 | T029/T030; §0/§4 |
| I2 schema drift | 9 entity thật; không entity approval, EduGoalRow superclass | T006/T007/T030; data-model §2 |
| I3 thứ tự cutover | Candidate SQL/scratch trước, T009 production cuối; không ship app hai bảng giữa expand/contract | T029–T031/T035/T037 trước T009 |
| U1 ID API | Shared sequence hai header, giữ ID/route, fail khi inventory trùng | T040; data-model §1.3, §4 quickstart |
| U2 cập nhật nguồn | Reuse bản mở trước, transaction ban hành đồng bộ; lỗi rollback, lặp không nhân bản | T020/T023/T024/T042; §3 |
| U3 resolve/baseline | Baseline cột bền vững, reviewItemId khác itemId, validate nguồn/revision/lease/mapping; carry-forward lỗi còn tồn tại | T021/T022/T025/T026/T041; §3 |
| I4 SQL kiểm chứng | Bỏ query bảng/cột đã drop; snapshot + EXCEPT hai chiều toàn payload/owner, trước xóa/sau chuyển | T030/T031; §0/§4 |
| G4 cây nguồn/import | FR-004/US2.7/AC-16 kiểm server kể cả request giả/import | T038/T043/T046; §2 |
| G5 performance | Bỏ cam kết không tăng query thiếu baseline; giữ convention tránh N+1 | Không thêm benchmark/SLA không được yêu cầu |
| Bổ sung từ source | V80 authors chọn header hoặc class_doc_id; FK ghép V35/V84 giữ scope cây/theme | T006/T030/T031, snapshot LEFT JOIN và check 3 target |

Các finding trên đã có cách xử lý nhất quán trong artifact; không có nghĩa implementation/test runtime đã đạt. Thiết kế shared sequence và transaction đồng bộ là đề xuất mới cần duyệt cùng plan/API/DDL, không ghi như user đã duyệt chi tiết kỹ thuật.

## Coverage tài liệu

[Tasks §Coverage](tasks.md#coverage--rule--requirement--task--check) là bảng Rule → Requirement → Task → Check chính. 17 FR + 6 SC = **23 yêu cầu**, mỗi yêu cầu có task triển khai/kiểm chứng tương ứng; **23/23 (100%) coverage tài liệu**. Các yêu cầu UI có task FE/E2E riêng; phê duyệt/client access là gate thực thi, không đánh task đã xong. Giữ T001–T047, bổ sung T048–T050 sau lần analyze tiếp theo: **50 task**, tất cả chưa đánh hoàn thành. Setup/impact/docs/approval hỗ trợ US chung, không có task không liên quan mục tiêu.

Kết luận vòng refinement đầu chưa phát hiện thêm blocker; lần analyze tiếp theo đã bổ sung G1 consumer và U1/U2 bên dưới. Không dùng kết luận vòng trước để phủ nhận các gap phát hiện sau. Các gate trước implementation/cutover vẫn mở: duyệt plan/API/DDL và quyền repo theo scope; xác nhận slot/history migration, consumer APP; thực thi tests/E2E/scratch và ghi bằng chứng. Không coi các gate này là đã pass.

## Constitution

Giữ stack/module, server authorization, bảo mật dữ liệu và non-AI baseline; không sửa constitution. Không chạy check-prerequisites/harness/hook theo constitution V/AGENTS. Không thực hiện migration hoặc sửa cross-repo. Plan không còn tự chứng nhận PASS; task có regression hành vi, native verification và human gates trước cutover.

## Kiểm chứng lượt tài liệu

Kiểm trực tiếp bằng Python assert: UTF-8/fence, ID FR/SC/task duy nhất và liên tục, task refs/coverage hợp lệ, local Markdown links tồn tại, 9 owner thật/ngoại lệ authors, các quyết định ID/transaction/resolve và thứ tự scratch→cutover đồng bộ. Kiểm whitespace bằng git diff --check và kiểm riêng dòng cuối khoảng trắng trong artifact untracked. Kết quả thực thi cuối ghi ở chat và docs/quality/VERIFICATION.md.

SQL trong quickstart là fragment/kịch bản cần được T030 chạy thật trên scratch, chưa xác nhận PostgreSQL execution. Không chạy Maven/FE/browser/DB trong lượt tài liệu; không báo các suite cũ như chạy mới.

## Remediation sau lần analyze tiếp theo — user “ok fix”, 09/11/2026

| Finding vòng tiếp theo | Artifact/task đã bổ sung | Cách kiểm chứng dự kiến |
|---|---|---|
| G1 HIGH: thiếu chuyển consumer nguồn lớp | Plan nguồn TEAM và data-model §6, contract consumer; T048 chuyển requireIssuedPlan/isSuperseded/batch/copy/lock/preview/keys và rà lesson-query/observation | Quickstart §5, behavior tests trên schema đích trước T035/T009; giữ hồ sơ cũ và nguồn lớp tới khi TEAM mới duyệt |
| U1 MEDIUM: legacy không baseline | FR-011/013 và edge case; data-model §4 chọn qua FK nguồn, legacy NULL không được suy/gán năm theo tên/tuổi; T049 | Ma trận legacy mở/ban hành + tổ có nguồn cùng tuổi, assert chỉ tổ có FK được cập nhật, legacy không review/baseline giả |
| U2 MEDIUM: form/lease/revision cũ | Data-model §4, contract conflict/FE giữ input; T050 tích hợp generator và FE theo scope T047 | Save/submit/resolve/heartbeat/finish-editing cũ conflict; session mới hợp lệ, replay không invalidate hai lần, rollback phục hồi nguồn/revision/lease |

Bảng coverage tasks đã thêm T048 cho FR-007/008/011 và SC-005/006, T049 cho FR-011/013, T050 cho FR-009/012/013. 23 yêu cầu vẫn có task/check; 50 task chưa đánh hoàn thành. Đây là remediation tài liệu theo quyền user, không là implementation hoặc một lần chạy speckit-analyze có quyền ghi.

Các ràng buộc legacy chỉ cụ thể hóa bảo toàn/no-guess nguồn của FR-011; không thêm luồng gán nguồn, không sửa business rule nguồn hoặc tự nâng mức xác nhận. Chi tiết kỹ thuật mới được review cùng plan trước code; SQL/runtime/E2E vẫn chưa kiểm chứng. Native kiểm nội dung/links/IDs/dependency và git diff --check phải chạy lại sau sửa cuối, kết quả ghi chat/VERIFICATION.
