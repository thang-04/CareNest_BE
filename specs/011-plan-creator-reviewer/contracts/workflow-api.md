# Implemented contract — Education workflow

Implemented in the education controller/DTO and reflected in OpenAPI snapshot. API prefix lấy cấu hình hiện tại; các đường dưới đây không chứa prefix. Response vẫn `ResponseJson`, lỗi vẫn errorCode số; không đổi auth/token/permission matrix toàn hệ thống. Verified in `target/verify.log`; limitations and client evidence are in [client-impact](client-impact.md).

## 1. Read/write policy riêng

`GET /education-plans/workflow-policy`: roles giáo dục được đọc trong phạm vi chức năng; trả policy snapshot và `canConfigure` server tính. Không trả danh sách tài khoản ứng viên hoặc PII để client tự quyết quyền.

`PUT /education-plans/workflow-policy`: chỉ PRINCIPAL + phân công HT hiệu lực; PHT bị 403 kể cả có PLAN_CONFIG hoặc tự gửi API. Body:

```json
{
  "revision": 1,
  "policies": {
    "year": {"creators": ["TEAM_LEADER"], "reviewers": ["VICE_PRINCIPAL"]},
    "branch": {"creators": ["TEACHER"], "reviewers": ["TEAM_LEADER", "VICE_PRINCIPAL"]},
    "theme": {"creators": ["TEACHER"], "reviewers": ["TEAM_LEADER", "VICE_PRINCIPAL"]}
  }
}
```

Response `data`: `{revision, policies, canConfigure, updatedAt}`. Một transaction tăng revision đúng một lần; expected revision sai trả 9012. Validate đúng keys, roles allowlist, non-empty, distinct và disjoint; reject cả unknown key `week` để tránh hiểu là giáo án tuần duyệt riêng. Không nhận account IDs, actor IDs hoặc status/completedAt từ client. Save thành công có nghĩa mọi pending đã được đối soát cùng transaction. Policy revision changed dù chuỗi mới trùng current? request no-op không bump/history; stale revision vẫn conflict trước so no-op để không che lost update.

## 2. GET/PUT config cũ

`GET /education-plans/config` thêm `workflow` cùng shape response trên; giữ domains/schoolYears/ageGroups/weekRows/signerNames. Field `signers` deprecated, chỉ projection reviewer role cho client chuyển tiếp, không dùng kiểm quyền. `week` của legacy signers tương ứng policy theme. Không coi giá trị hiện tại là chữ ký lịch sử của hồ sơ completed.

`PUT /education-plans/config` giữ field cấu hình khác và quyền hiện có; **mọi caller**, kể cả HT, không được đổi workflow qua `signers`. Nếu signers payload giống projection server hiện tại thì echo được bỏ qua; nếu khác thì trả lỗi yêu cầu dùng workflow-policy endpoint. Không nhận workflow trong body cũ hoặc âm thầm persist unknown fields. PHT gửi lại signers cũ khi policy đã đổi được báo lỗi/tải lại, không ghi đè policy.

Như vậy PHT không thể đổi phần trách nhiệm qua full-config PUT; không tự thu hồi quyền PHT sửa tên lĩnh vực/dòng tuần nếu chưa có yêu cầu đổi phần đó. Web tách nút/phạm vi lưu và chỉ HT thấy chức năng sửa người tạo/duyệt.

## 3. Year actions và metadata

Giữ URL `/education-plans/goals/{id}/submit|issue|return`; `/issue` là approve bước hiện tại, final mới APPROVED. Entry role gates cho HT/VP/TEACHER vào module nhưng quyết định thực nằm tại configured policy + assignment; TL vẫn là giáo viên + assignment. Không để fixed controller VP-only chặn HT/TL khi đến lượt.

- Submit: chỉ từ DRAFT, author capability + readiness/lease/revision/idempotency; đóng lease, tăng round, chuyển REVIEW. Trong REVIEW không ai được sửa nội dung/lấy author lease; duyệt trung gian giữ REVIEW, bước cuối xong thì APPROVED (Ban hành). Return bắt buộc lý do, về DRAFT để sửa, không tạo REJECTED riêng.
- Issue/return: review capability và đúng waitingRole, không author lease. Vẫn cần `X-Plan-Revision`, `Idempotency-Key`; bỏ yêu cầu `X-Edit-Session` cho hai review actions. Return note bắt buộc.
- Content PUT/start/finish/heartbeat, version/copy/delete/import preview/media/adjustments: rà theo author capability đúng loại; lease không thay quyền. Hết quyền vì cấu hình đổi thì stale tab không ghi tiếp được.
- Year/class status chỉ DRAFT/REVIEW/APPROVED tương ứng Nháp/Duyệt/Ban hành; không trả PENDING theo vai trò hoặc EDITING/REJECTED như trạng thái nghiệp vụ. review.waitingRole nêu người tới lượt trong REVIEW; field status client chỉ đọc, không gửi để server suy action. `permissions.canIssue` nghĩa được approve bước hiện tại; `review.waitingRole` cho UI chọn nhãn Duyệt/Ban hành. Thêm `permissions.canApprove` alias rõ, giữ canIssue deprecated trong chuyển tiếp.

Year detail/summary thêm `review` và per-document permissions (summary cần đủ để queue lọc quyền), không sửa fields nội dung cây. Dữ liệu tham chiếu trong GET config thêm `yearCreationScopes` (ageGroup IDs được tạo cho current user, server tính, không account IDs), để list/form không đoán theo role. Fields thiết kế:

```text
review: {policyRevision, round, roles, waitingRole, approvals,
         authorCoverage, authorTrackingSince, completionMode, completedAt,
         completionPolicy, printSnapshot}
permissions: {canEdit, canSubmit, canApprove, canIssue, canReturn,
              canDelete, canCreateVersion}
```

Approval shape theo data-model; không cần trả toàn bộ contributor list cho client. Creator và các actor hiển thị theo DTO giáo dục; year issued dùng published_by/completed_at mới (legacy fallback dữ liệu cũ), không lấy compatibility issued_by làm publisher của workflow mới. Completion do policy đổi vẫn chứa người duyệt thật đã có, `completionMode=POLICY_CHANGE`, không có human approval của người lưu cấu hình.

## 4. Class documents

Giữ `GET /education-plans/class-docs?schoolYearId=`, `PUT /class-docs/{kind}/{key}`, `POST /class-docs/{kind}/{key}/actions`. Action `submit|approve|reject`, note như hiện có. Year/class effective role/status do server tính, không dùng `actorRole` ưu tiên VP thay role đang thực hiện.

Mỗi node branch/theme/week thêm metadata chỉ đọc `version: long`, `dossierVersion: long|null`, `review` như year và `permissions={canEdit,canSubmit,canApprove,canReject,canEditAdjustments}`. `review.approvals` là array `{userId:long,name:string,roleKey:string,at:ISO-8601,note:string,round:int}` theo data-model, không dùng role label làm key.

Response list thêm `creationCapabilities: [{classId:long,kind:"branch"|"theme"|"week",canCreate:boolean,requires:"ISSUED_YEAR_PLAN"|"APPROVED_BRANCH"|"THEME_DOSSIER",reasonCode:string|null}]`. `canCreate` là quyền role/scope cho tài liệu chưa có; prerequisites theo `requires` vẫn phải đủ ở context năm/chủ đề khi tạo. Branch cần year hiện hành đã ban hành; theme cần branch đã duyệt; week cần theme dossier cùng lớp/năm. Client không được coi canCreate=true là bỏ qua validation đầy đủ/unique key. Không expose danh sách người soạn/candidate account IDs để frontend tự authorize.

Week actions không gửi riêng; `canSubmit/canApprove/canReject=false` ở week, hiển thị hồ sơ theme tương ứng. Quyền chỉnh adjustment EDU-27 kiểm riêng author policy, không cấp cho reviewer.

Dossier snapshot/capabilities dựa union authors tất cả tuần/ngày và theme. Khi review/reconcile, các member status/round/approvals nhất quán; không cho client sửa metadata lịch sử dưới dạng content JSON.

Mọi class PUT content, POST action và PUT adjustment thêm header bắt buộc `X-Class-Doc-Version`: -1 khi create (document phải chưa tồn tại), >=0 khi đã có. Week/member write thêm `X-Dossier-Version` bằng version của theme root; theme action dùng expected root version làm X-Class-Doc-Version. Server lấy lock rồi compare expected member/root version, mismatch trả 9012; không load latest rồi chấp nhận payload cũ. Mọi member content/status/policy mutation bump root version, response trả versions sau flush; JPA @Version không thay thế precondition này. `version`, `dossierVersion`, `review`, `permissions` và creationCapabilities là metadata server, không lưu vào content JSON hay nhận làm nguồn quyền từ request.

## 5. Lỗi và snapshot

Reuse 7201 EDU_CONFIG_FORBIDDEN (403), đổi mô tả thành thông báo trung tính về thiếu quyền cấu hình phần đang sửa để không nói PHT được cấu hình workflow; 7202 SIGNERS_INVALID (400) cho role/chain policy không hợp lệ nếu mô tả sửa thành thuật ngữ soạn/duyệt; 7204 CLASS_DOC_APPROVER_REQUIRED cho không có reviewer, đổi mô tả để bao gồm kế hoạch năm; 7203 YEAR_PLAN_SIGNER_REQUIRED giữ enum deprecated nhưng không còn ép PHT trong policy mới hoặc khi echo signers qua config cũ; 9012 CONCURRENT_UPDATE cho policy/doc revision conflict. Mã phải đối chiếu ApiCode hiện tại khi implement, không gán số trùng hay sửa error wrapper. Cấm reviewer soạn/author duyệt dùng access-denied hiện hành của loại hồ sơ; NOT_YOUR_REVIEW_STEP cho sai lượt. Thiếu publish prerequisites dùng mã hiện hành, rollback policy save; message không chứa dữ liệu trẻ.

Snapshot OpenAPI phải thể hiện new endpoints/DTOs/statuses/header requirements và capabilities. Không đổi mã lỗi ngoài phạm vi này, không cập nhật snapshot bằng cách ghi đè bỏ diff ngoài feature. Regression tests kiểm cả body `errorCode` và HTTP status.

## 6. Compatibility/release

Đây là thay đổi có ảnh hưởng semantic và enum, không thể quảng cáo tương thích tuyệt đối với Web cũ. Release BE+Web đã update phối hợp sau approval; GET policy no write, không để mock bypass scope. Completed docs chỉ dùng review.printSnapshot đã lưu (origin PUBLICATION hoặc LEGACY_BASELINE) cho print, không global signers mới. Không bật feature trên backend giữa P1/P2/P3; APP consumer review chưa xác minh, phải ghi giới hạn/giải quyết trước rollout nếu có phụ thuộc.


## 7. Delta sau analyze — ba trạng thái và snapshot bản in

User06/11 chốt người soạn không sửa khi Duyệt, chỉ DRAFT/REVIEW/APPROVED; khóa sửa là metadata của DRAFT. Config change đối soát cả REVIEW và DRAFT đang giữ lease bị mất quyền, trả revision mới để tab cũ bị từ chối. Mã APPROVED vẫn giữ cho trạng thái cuối nhằm bảo toàn consumer chỉ đọc hiện hữu, không thêm thao tác ban hành độc lập sau duyệt cuối.

review.printSnapshot có schema chính xác tại data-model.md §9: schemaVersion, capturedAt, origin, documentType, policyRevision, slots[{roleKey,label,names}]. DRAFT/REVIEW=null; bản Ban hành dùng snapshot đã lưu, không dựng lại từ config/tên tài khoản hiện tại. Field chỉ đọc, không nhận từ client như nội dung. Print snapshot khác completionPolicy (policy/role order), không trộn hai shape.

Phạm vi chỉ giáo dục: không sửa controller/service/repository của observation hoặc organization; ngoại lệ account chỉ đúng helper đọc findActiveUserIds và test được user cho phép, không endpoint/permission/nghiệp vụ tài khoản. Education producer lưu actual publisher ở published_by/completed_at và compatibility issued_by chỉ chứa actual VP approval hoặc null; read consumer cũ giữ nguyên. T009 dùng helper nội bộ AccountService.findActiveUserIds đã được user cho phép: input Collection<Long>, output Set<Long> tồn tại và ACTIVE, unknown/inactive bị loại, empty input trả rỗng không query. Không expose helper qua HTTP, không trả tên/tài khoản cho UI, không đọc repo feature khác từ edu hoặc mở quyền module khác.
