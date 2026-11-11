# Data model — Education workflow policy

Thiết kế khởi tạo ngày 06/11/2026; được triển khai và kiểm chứng qua migrations V87–V89 cùng source education hiện hành. Chi tiết migration/caller/test ban đầu bên dưới là kế hoạch lịch sử; kết quả cuối ở [quickstart](quickstart.md). Tất cả FK tài khoản dùng ID, không lưu token/secret. Giữ bảng hiện có, không tạo workflow subsystem dùng chung toàn trường.

## 1. EduSettings singleton

Thêm `workflow_policy` JSONB NOT NULL, `workflow_revision` BIGINT NOT NULL (khởi đầu 1), `workflow_updated_by` FK user, `workflow_updated_at`. Version workflow tách khỏi updated_at của domain labels/week rows để PHT lưu phần khác không làm conflict policy.

Policy có đúng keys `year`, `branch`, `theme`; mỗi key có `creators: string[]`, `reviewers: string[]` theo thứ tự. Creator domain = TEACHER/TEAM_LEADER/VICE_PRINCIPAL/PRINCIPAL. Reviewer domain giữ ba vai trò đang hỗ trợ TEAM_LEADER/VICE_PRINCIPAL/PRINCIPAL; không mở thêm GV duyệt như một feature mới. Mỗi tập tối thiểu một, không lặp, không giao nhau; kiểm toàn bộ JSON tại service. DB CHECK bảo đảm JSON object/array shapes cơ bản, revision >0; không viết SQL validation JSON tùy tiện khác service.

Seed đã được user chọn:

| Loại | Creators | Reviewers |
| --- | --- | --- |
| year | TEAM_LEADER | VICE_PRINCIPAL |
| branch | TEACHER | TEAM_LEADER, VICE_PRINCIPAL |
| theme | TEACHER | TEAM_LEADER, VICE_PRINCIPAL |

`week`/day không có policy riêng. `signers` cũ giữ để tương thích/freeze legacy print; sau activation là projection reviewer roles, không dùng authorize. Migration lưu bản legacy config để phục hồi print trước khi ghi projection. Không sửa V27/V41.

## 2. Document metadata

Delta sau analyze 06/11/2026 có ưu tiên so với mô tả cũ: chỉ ba trạng thái `DRAFT` = Nháp, `REVIEW` = Duyệt (đang duyệt), `APPROVED` = Ban hành. Giữ mã APPROVED cho trạng thái cuối để không làm thay đổi các truy vấn chỉ đọc đã có của module khác. Vai trò tới lượt nằm ở `review.waitingRole`, không mã hóa vào status.

| Field | Year | Class | Ý nghĩa |
| --- | --- | --- | --- |
| ownerId/createdBy | giữ | giữ | Creator bất biến, không nhận từ client |
| review_round INTEGER | thêm | thêm | 0 trước submit đầu, tăng mỗi vòng mới |
| approvals JSONB | thêm `[]` | reuse | Approval active của round hiện tại |
| applied_policy_revision BIGINT nullable | thêm | thêm | Revision đã đối soát; legacy null |
| completion_policy JSONB nullable | thêm | thêm | Policy/roles hiệu lực lúc hoàn tất, phục vụ metadata và lịch sử; không dùng làm nguồn ô ký bản in |
| print_snapshot JSONB nullable | thêm | thêm | Các ô ký, nhãn vai trò và tên đã chụp khi ban hành; độc lập policy hiện tại |
| published_by BIGINT nullable FK user_accounts | thêm | không cần riêng | Người duyệt thực tế theo role cuối được công nhận; nguồn cho DTO issued/publisher mới |
| completion_mode varchar nullable | thêm | thêm | HUMAN_APPROVAL/POLICY_CHANGE/LEGACY |
| completed_at timestamptz nullable | thêm, tách issuedAt legacy | thêm | Thời điểm trạng thái hoàn tất, khác thời điểm từng approval |
| author_tracking_since timestamptz | thêm | thêm | Mốc biết đầy đủ contributor tracking |
| author_coverage varchar | thêm | thêm | COMPLETE hoặc LEGACY_BASELINE |

Year giữ sentBy và dữ liệu issuedBy/issuedAt cũ. Với bản mới, `published_by` + `completed_at` biểu diễn người/thời điểm ban hành thật, DTO issued của giáo dục dùng dữ liệu này. `issued_by` được coi là compatibility projection cho người PHT thực sự đã duyệt nếu có, không ghi HT/TL vào cột này; nếu không có PHT duyệt thì null. Nhờ vậy chỉ sửa producer trong giáo dục, không sửa repository/service của observation đang đọc issued_by như PHT ký. Legacy giữ issued_by/issued_at cũ, backfill published_by/completed_at tương ứng. Tên cột compatibility không được dùng làm bằng chứng người ban hành thật của workflow mới.

Completion do policy đổi không được ghi event rằng HT vừa approve; DTO có mode/approvals cho client hiểu. completed_at là thời điểm ban hành; human approval có at riêng. Thay đổi projection legacy này là thiết kế đề xuất cần review lại, chưa có source/migration thật và không giả vờ đã xác minh toàn bộ consumer.

Approval record: `{userId, name, roleKey, at, note, round}`. Snapshot roleKey chính là bước đang duyệt, không dùng actorRole ưu tiên VP để gắn nhầm role khi người có nhiều vai trò. `name` dùng chính sách snapshot hiện có, không expose PII khác. Một role chỉ có một active approval trong cùng round; row lock ngăn duplicate concurrent writes, request retry không append thêm.

Year/class enums và DB CHECK sau cutover T047 chỉ DRAFT/REVIEW/APPROVED. P1 chuẩn bị nội bộ, không rollout: expand thêm REVIEW/fields nhưng giữ legacy constants và CHECK tương thích; switch exhaustive có REVIEW=false cho quyền sửa. T030..T032/T045 chuyển caller/test; T047 mới xóa constants cũ và thêm migration contract remap/siết CHECK. Đang sửa ở target cuối là lease metadata, không status. Giữ version/idempotency; class expected member/root versions compare dưới lock.

Ma trận tác vụ: DRAFT cho author hợp lệ sửa nội dung/lấy lease/gửi; REVIEW không ai soạn/sửa nội dung/lấy lease, chỉ reviewer đúng lượt approve/return; APPROVED không sửa nội dung gốc. Submit đóng lease và chuyển REVIEW; intermediate approve giữ REVIEW, final chuyển APPROVED. Return có lý do và lịch sử đưa về DRAFT để soạn lại, là phép ánh xạ để giữ đúng ba trạng thái. EDU-27 adjustment vẫn là ghi chú bổ sung riêng của hồ sơ đã ban hành, không sửa approved content hoặc mở lại workflow.

## 3. Contributor ledger

Một bảng `edu_document_authors`:

- `id` PK; `year_plan_id` nullable FK edu_year_plans ON DELETE CASCADE; `class_doc_id` nullable FK edu_class_docs ON DELETE CASCADE.
- CHECK đúng một document FK khác null; `user_id` FK user_accounts, `first_authored_at`, `last_authored_at` NOT NULL, `origin` = CREATOR/CONTENT_WRITE/LEGACY_CREATOR.
- Unique partial indexes `(year_plan_id,user_id)` WHERE year_plan_id IS NOT NULL và `(class_doc_id,user_id)` tương ứng. FK/index lookup theo document; không xóa contributor khi gửi lại.
- Insert/upsert chỉ sau khi content persistence thực sự thay đổi. Không ghi contributor cho acquire lease, preview không lưu, approve/reject, policy config, GET, retry/no-op.
- Create/copy/version mới ghi creator đích; không sao chép authors của nguồn. Những lần soạn nội dung tiếp theo ghi actor server. Adjustment EDU-27 kiểm author policy và ghi contributor, nhưng không thay approved content hoặc mở lại workflow.
- Dossier theme dùng union creators + authors của theme và mọi week thuộc hồ sơ; day là nội dung trong week. Reviewer từng soạn bất kỳ phần nào bị loại cho cả dossier. Cập nhật status/approvals của cả hồ sơ trong cùng transaction.

## 4. Vòng duyệt và history

Submit từ DRAFT sau soạn: tăng round, active approvals rỗng, chain lấy current policy; history vòng trước vẫn giữ. Return từ REVIEW chấm dứt vòng và về DRAFT kèm lý do; gửi lại bắt đầu vòng mới từ đầu. Version mới là document mới bắt đầu DRAFT, không copy approvals. Chỉ policy-only change không tăng round hoặc xóa approvals đã có.

Year events và class history append entry cấu trúc có actorId, roleKey khi là human action, round, policyRevision. Policy reconcile event ghi `POLICY_RECONCILED`/`COMPLETED_BY_POLICY` với actor cấu hình, không nằm trong human approvals. Lịch sử cũ không sửa tên/role label hoặc giả dựng IDs từ tên. Thiết kế không log payload nội dung/tài khoản thật.

## 5. Evaluator contract

Pure evaluator nhận policy, current-round approvals, excludedAuthorIds và eligible candidates theo từng role; trả `{waitingRole, skippedRoles, complete, completionRole}`. Không tự query DB hay mutate entities.

1. Bỏ role đã có approval hợp lệ trong round hiện tại khỏi tập việc còn thiếu, giữ history kể cả role đã bị bỏ khỏi policy.
2. Duyệt vai trò còn thiếu theo thứ tự policy. Role có candidate hợp lệ → waitingRole đó.
3. Role chưa duyệt không có candidate: bỏ qua nếu còn role sau trong policy; nếu đây là role cuối và chưa duyệt thì giữ chờ tại đó. Không ghi approval cho skip.
4. Nếu không còn bước chưa duyệt không-skip và role cuối đã có approval hợp lệ thì complete, không phụ thuộc trigger là approve, config reconcile hay activation. Bao gồm mọi role đã duyệt hoặc ví dụ `[TL không có candidate, VP đã duyệt]`: TL được skip, kết quả complete=true/waitingRole=null; không tạo fake approval cho TL. Nếu còn bước mới có candidate thì chờ bước đó dù role cuối đã duyệt.
5. Nếu thiếu người ở mọi bước và role cuối chưa duyệt thì không complete. Nếu candidate đổi theo assignment thì GET chỉ trả effective waitingRole; actual status cập nhật khi có mutation dưới lock, không side effect trong GET.

Tính capability và reconcile dùng cùng evaluator; test bắt buộc bảo đảm UI metadata không báo được duyệt nhưng service từ chối cùng input snapshot. Reviewer không được author cùng loại, kể cả qua role TEACHER ở nhóm tuổi khác. Reviewer-precedence xét role/phân công hiệu lực theo loại trước; quyền duyệt thật vẫn phải đúng scope hồ sơ, không vì có role nơi khác mà được duyệt ở đây.

## 6. Transaction và khóa

Thứ tự duy nhất: settings (shared cho ghi hồ sơ, exclusive cho save policy) → year IDs tăng → class root IDs tăng → children IDs tăng. Preview/read không giữ lock chặn cấu hình suốt thời gian đọc file/IO. Media metadata ghi phải recheck policy trong transaction commit; giữ cleanup/bù trừ hiện có.

Policy PUT kiểm expected revision rồi validate JSON. Khóa/đối soát toàn bộ pending chưa hoàn tất, year trước class; giữ policy candidate snapshot trong transaction. Auto-completion year gọi cùng publish validators như issue (EDU-51/52/53 và readiness/version hiện có); nếu một prospective completion không hợp lệ thì toàn bộ policy transaction rollback, trả lỗi nghiệp vụ với ID cần xử lý. Không commit config rồi để queue/cron cập nhật hồ sơ sau.

Policy update có hai tập xử lý trong cùng transaction: (1) bản REVIEW để đối soát lượt/kết quả; (2) mọi DRAFT đang có lease để thu hồi lease của người không còn quyền soạn, tăng revision/version và từ chối tab cũ. Bản REVIEW luôn không có author lease; lỗi legacy có lease phải được dọn lúc chuyển đổi. Bản APPROVED không bị đổi nội dung/trạng thái/print snapshot. Cùng thứ tự khóa settings→year IDs→class roots/children cho cả hai tập, không chỉ query REVIEW rồi bỏ qua các bản nháp đang mở.

Mọi write đã lấy settings shared lock xong trước policy save hoặc bắt buộc đọc policy mới sau policy commit; không có window dùng chuỗi cũ để approve sau save thành công. Duplicate config revision/approval bị 409, không append lại history. Lock timeout/deadlock trả conflict theo convention, không retry mù một thao tác đã có human approval.

Class precondition: response node có `version` và `dossierVersion`; request create dùng expected version -1 (chưa tồn tại), update/action/adjustment dùng version >=0 hiện server trả. Sai expected version trả 9012 trước mutation. Theme/weekly writes cùng khóa dossier root rồi children; mọi content/member-status/policy change làm root dossier version tăng, kể cả sửa một tuần. Review theme phải so expected root version để không duyệt snapshot cũ khi tuần vừa sửa. Week write gửi thêm expected dossierVersion, compare cả root và member; metadata không được serialize vào content. Không dùng updatedAt hoặc đọc entity mới rồi coi request cũ là mới.

Metadata GET gồm policy+document+assignments+account trong transaction read-only REPEATABLE_READ cùng connection/propagation REQUIRED; không trộn READ_COMMITTED nhiều snapshot. GET không mutate state, không giữ row lock qua storage/file/network IO. Integration test cho config commit xen giữa hai read chứng minh không ghép policy cũ với state mới. Audit cả expire-lock job, releaseExpiredLocks, finishEditing/heartbeat: các writer housekeeping cũng lấy settings shared lock trước document lock, không khôi phục editingFrom/status cũ sau reconciliation.

## 7. Migration/backfill

- T002 cấp số cho hai migration mới expand/contract khi implement, không sửa migration cũ. Backup/snapshot dữ liệu test hoặc staging được phép trước activation, không lưu dữ liệu thật trong repo.
- Expand T006: thêm columns/FK/index/policy seed/creator baseline và snapshot nguồn print cũ, cho phép legacy states cùng REVIEW để source/test hiện tại chạy. T007 thêm REVIEW nhưng giữ constants cũ; switch exhaustive thêm REVIEW cấm sửa, fields mới có default phù hợp. Public callers/permission behavior cũ giữ tới P2, chưa activate workflow mới. Không suy creators từ signers.
- Contract T047 sau khi T027/T028/T030..T032/T045 đã chuyển logic/fixtures: cùng một cutover source/test, bỏ constants cũ, tạo SQL remap legacy và siết CHECK chỉ ba trạng thái. T014 full kiểm expand + legacy compatibility; T051 full kiểm final contract. Không rollout P1; release cuối áp cả hai migration trước readiness/activation.
- Ledger baseline = year.owner_id/class.created_by duy nhất chắc chắn; không lấy updated_by, lock owner, tên history làm coauthor. `LEGACY_BASELINE` và tracking_since mốc nâng cấp cho mọi document cũ; document mới COMPLETE.
- Freeze legacy completed print snapshot từ config/sign_chain và tên/role của creator/sentBy/issued/approvals thực tế tại nâng cấp, đánh dấu legacy baseline (không có bằng chứng để khẳng định cấu hình gốc trước tracking). Snapshot phải chứa role order + tên/role từng ô cần in, không chỉ revision; không suy tên HT cấu hình thành reviewer. Không sửa nội dung, trạng thái, issuer hoặc approvals completed.
- Tại contract migration: PENDING_TL/VP/HT→REVIEW, REJECTED→DRAFT giữ history; EDITING dùng editing_from để remap DRAFT/REVIEW, giữ APPROVED. REVIEW không lease. DRAFT lease do activation xác minh holder theo policy, SQL không đoán account eligibility. Chỉ sau remap mới siết CHECK ba giá trị.
- Class đang duyệt có approval JSON: thêm round metadata, giữ actor/name/role/at. Year legacy PENDING_VP chưa có partial approvals: active approvals rỗng, không biến sentBy thành reviewer. Legacy completed snapshot giữ tên/role đã biết và đánh dấu baseline.
- Activation driver ứng dụng: `config/EduWorkflowActivationConfig.java` dùng startup ApplicationRunner gọi service transaction activateLegacyPending; hoàn tất trước readiness nhận traffic, lỗi không báo ready. Driver idempotent dựa applied-policy metadata, không public endpoint hoặc schema flag mới; deployment vẫn phải chặn traffic cho tới readiness. Chạy reconciliation pending bằng cùng engine đã test, ghi mode/history rõ. Publication validation không hợp lệ thì activation thất bại, không chạy nửa chừng. Phân biệt Flyway DDL với ứng dụng activation để không nhét business logic/role eligibility vào SQL.
- Không tự tạo endpoint sửa lịch sử legacy; giới hạn provenance là lựa chọn user. Backfill không thể chứng minh mọi coauthor trước nâng cấp, phải thể hiện giới hạn cho người dùng có quyền.

## 8. Derived data và compatibility

Review role/capabilities từ current policy và assignments. Approvals/history/print snapshot là dữ liệu đã ghi, không suy lại từ config mới cho completed. Không sửa source observation: giáo dục giữ mã trạng thái cuối APPROVED và ghi compatibility issued_by theo actual PHT approval hoặc null như mô tả §2; truy vấn/fallback của consumer khác giữ nguyên. Không coi kiểm tra dependency là quyền triển khai module khác.

## 9. Print snapshot contract — chốt MEDIUM U2

Lưu `print_snapshot`, trả ở `review.printSnapshot`; không nhét tên người vào completion_policy như một shape JSON không xác định. Shape phiên bản 1:

```json
{
  "schemaVersion": 1,
  "capturedAt": "2026-11-06T00:00:00Z",
  "origin": "PUBLICATION",
  "documentType": "year",
  "policyRevision": 2,
  "slots": [
    {"roleKey": "TEAM_LEADER", "label": "Người soạn", "names": ["Người soạn mẫu"]},
    {"roleKey": "VICE_PRINCIPAL", "label": "Phó hiệu trưởng", "names": ["Người duyệt mẫu"]}
  ]
}
```

Tên trên là fixture tổng hợp. roleKey là role nghiệp vụ của ô, label/names chụp từ actor/nguồn được phép tại thời điểm ban hành. names có thể nhiều tên; không biết thì [] để ký tay, không bịa người đã ký. origins chỉ PUBLICATION/LEGACY_BASELINE; legacy policyRevision có thể null. Thứ tự slots là thứ tự in đã chụp. Không dùng đổi tên tài khoản/config hiện tại để dựng lại snapshot. DRAFT/REVIEW trả printSnapshot=null; preview in trước ban hành có thể dùng dữ liệu hiện hành nhưng không ghi snapshot như thể đã ban hành. Final human/policy completion ghi snapshot trong cùng transaction; version mới tạo snapshot riêng khi ban hành. Completed cũ chỉ bổ sung legacy baseline có nhãn giới hạn, không sửa kết quả duyệt cũ.

Rollback deployment không xóa ledger/approvals. Không chạy ứng dụng cũ đọc pending enum mới với DB mới. Nếu cần rollback, phục hồi DB+ứng dụng từ cùng mốc backup trước activation bằng quy trình deploy đã duyệt; không tự downgrade Flyway hoặc sửa SQL đã commit.
