# Data Model — tách bảng năm/tổ (spec015)

Thiết kế sửa ngày 09/11/2026 theo lệnh user “fix”; chưa duyệt DDL/API/triển khai. V89 local đã tồn tại; không chứng nhận đã apply, không sửa SQL trong lượt tài liệu. Trước implement kiểm tra Flyway history/commit; migration đã commit/applied phải được bổ sung bằng migration mới, không đổi checksum. V89/V90 là tên dự kiến, phải kiểm tra slot thực tế trước tạo.

## 1. Bảng chính và định danh

### 1.1 `edu_year_plans`

Chỉ giữ SCHOOL_YEAR sau cutover. Giữ nguyên ID, title, năm/tuổi, version_no, lineage, workflow/approvals/print_snapshot, authors, lease/revision và request chống lặp. Chỉ bỏ plan_level và các cột dành riêng TEAM sau khi code/consumer đã chuyển. source_plan_id của năm vẫn self-FK tới năm.

Hai unique cũ của V83 là INDEX, không phải table constraint. Mẫu sau chỉ là fragment thiết kế, không phải migration có thể chạy độc lập:

```sql
DROP INDEX uq_edu_plan_level_version;
DROP INDEX uq_edu_plan_level_open;
CREATE UNIQUE INDEX uq_year_plan_version ON edu_year_plans(school_year_id, age_group_id, version_no);
CREATE UNIQUE INDEX uq_year_plan_open ON edu_year_plans(school_year_id, age_group_id)
    WHERE status <> 'APPROVED';
```

### 1.2 `edu_team_plans`

Chép đầy đủ các cột và giá trị TEAM từ bảng cũ, giữ nguyên ID; không chỉ chép tên/trạng thái. Entity `EduTeamPlan` có các trường workflow, approvals, print_snapshot, tác giả, lock/session/revision, idempotency và timestamps tương đương dữ liệu cũ.

| Cột/nhóm | Ràng buộc |
|---|---|
| id | BIGINT PK; dùng sequence chung theo §1.3 |
| code/title/description, school_year_id/age_group_id | Giữ giá trị, độ dài, FK hiện có; tên tổ độc lập |
| status | DRAFT/REVIEW/APPROVED theo EDU-54 |
| version_no/source_plan_id | Lịch sử tổ, self-FK tới edu_team_plans; giữ quy tắc root/version hiện có |
| curriculum_source_plan_id | BIGINT **nullable**, FK tới edu_year_plans; chỉ legacy có thể NULL |
| legacy_team_without_source | Giữ cờ legacy, không cho client tạo tổ không nguồn |
| previous_source_plan_id | BIGINT nullable FK tới edu_year_plans; baseline trước lần chuyển nguồn, không phải source_plan_id tổ |
| duration_policy_snapshot | Giữ nguyên snapshot và ngoại lệ legacy |

```sql
ALTER TABLE edu_team_plans ADD CONSTRAINT ck_team_plan_source CHECK (
    (NOT legacy_team_without_source AND curriculum_source_plan_id IS NOT NULL)
    OR (legacy_team_without_source AND curriculum_source_plan_id IS NULL)
);
CREATE UNIQUE INDEX uq_team_plan_version ON edu_team_plans(school_year_id, age_group_id, version_no);
CREATE UNIQUE INDEX uq_team_plan_open ON edu_team_plans(school_year_id, age_group_id)
    WHERE status <> 'APPROVED';
```

Giữ các CHECK/unique còn lại của bảng nguồn theo inventory T029/T030. Không ai được xóa tổ ở server (FR-016); không dùng cascade như một quyền xóa nghiệp vụ. Các dòng legacy không có nguồn vẫn đọc/in theo quyền, không tạo năm giả hoặc tự gán nguồn khi không xác định được.

### 1.3 ID chung cho hai bảng — thiết kế đề xuất

Giữ endpoint numeric hiện có bằng cách dùng **một sequence PostgreSQL `edu_plan_id_seq` cho cả hai bảng**, không hai identity độc lập. Trong cutover dừng mọi writer/job, bỏ identity riêng, đặt default nextval từ sequence chung; next ID phải lớn hơn MAX(id) của hợp hai bảng. Không OWNED BY riêng một bảng. JPA dùng cơ chế sinh ID tương thích default này, mọi create/copy đi qua sequence chung, không tự tính MAX+1 trong app.

Dữ liệu cũ xuất phát từ một bảng nên ID hai tập phải rời nhau. T030 phải kiểm trùng trước cutover, abort nếu có; không suy loại theo title hoặc chọn ngẫu nhiên bảng. Thử tạo năm/tổ đồng thời và phiên bản mới, restart rồi tạo tiếp: ID vẫn duy nhất toàn bộ, detail/action mở đúng loại. API không nhận client tự cấp ID. Đây là thay đổi thiết kế cần T001 duyệt, chưa đổi schema runtime.

## 2. Chín bảng con và cutover

| Bảng | Entity |
|---|---|
| edu_plan_goals | EduPlanGoal |
| edu_plan_sub_domains | EduPlanSubDomain |
| edu_plan_expected_results | EduPlanExpectedResult |
| edu_themes | EduTheme |
| edu_breaks | EduBreak |
| edu_plan_events | EduPlanEvent |
| edu_document_authors | EduDocumentAuthor |
| edu_plan_branch_templates | EduPlanBranchTemplate |
| edu_class_docs | EduClassDoc |

Mỗi bảng nhận school_year_plan_id/team_plan_id nullable khi expand; sau contract 8 bảng phải đúng một owner năm/tổ, có FK tới bảng tương ứng và index theo truy vấn; edu_document_authors có ngoại lệ hồ sơ lớp bên dưới. EduGoalRow là mapped superclass; không có bảng edu_goal_rows (drop V35) hoặc entity/bảng approvals riêng (approvals JSONB, bảng cũ drop V23).

**Ngoại lệ tác giả (V80):** edu_document_authors có cả class_doc_id. CHECK đích phải là `num_nonnulls(school_year_plan_id, team_plan_id, class_doc_id) = 1`; tác giả class doc giữ class_doc_id và hai owner header NULL, không suy/gán owner trực tiếp theo nguồn lớp. Giữ FK/unique (class_doc_id,user_id), tách unique partial (school_year_plan_id,user_id)/(team_plan_id,user_id) cho tác giả header và giữ origin/timestamps/coverage. Snapshot không được INNER JOIN làm mất tác giả lớp.

**FK ghép (V35/V84):** inventory mọi FK/index dùng year_plan_id trước DROP. Tái tạo bộ unique/FK ghép theo owner năm/tổ riêng cho subdomain→expected_result→goal (giữ domain/sub_domain_key và deferred semantics), theme→branch_template; kiểm không cross-owner. Không chỉ thay FK header rồi bỏ cột khiến mất ràng buộc cây. Không dùng DROP CASCADE bỏ âm thầm FK/unique.

edu_goal_contents suy qua goal_id; edu_content_theme_ticks suy qua content/theme; không thêm owner dư. Server kiểm hai đầu mapping/cây cùng owner. theme_evaluations giữ tham chiếu theme/snapshot, không đổi ID theme. Hồ sơ lớp trong luồng mới chỉ nhận TEAM APPROVED hiện hành; dữ liệu legacy bất thường phải được inventory và chặn cutover để xử lý có nguồn, không gán sang năm tùy ý.

### Trình tự triển khai được đề xuất

1. T001 duyệt thiết kế/DDL/plan và ghi bằng chứng; xác nhận chưa có migration tương ứng đã applied/commit.
2. T003 expand: thêm bảng và cột nullable, giữ year_plan_id/plan_level; không chuyển app sang hai bảng đang rỗng. V89 local chỉ là đầu vào review.
3. T029/T030 chuẩn bị bộ seed, snapshot và test cả expand/contract trên DB scratch. Kiểm tất cả 9 owner, children, legacy, class docs, approvals, lineage, sequence. Không cho ghi mới vào bảng team mở rộng trước cutover.
4. Hoàn thiện code/consumer và kiểm thử schema đích trên scratch. T009 production cutover chỉ sau các gate T001/T030/T031/T034/T035/T037/T047; dừng writer/job, backup và rehearsed restore.
5. Trong cutover: refresh owner từ year_plan_id để bao gồm dữ liệu phát sinh sau expand; chép toàn bộ TEAM giữ ID (OVERRIDING SYSTEM VALUE nếu identity cũ còn tồn tại), giữ source_plan_id self-FK theo thứ tự/deferred FK; thay defaults sequence chung. Đối chiếu **trước xóa**.
6. Chuyển tất cả FK trỏ header/cây: hai owner có CHECK đúng một cột; gỡ FK year_plan_id và bỏ cột cũ, xử lý self-FK TEAM trước xóa TEAM khỏi năm để không cascade mất dữ liệu. Chỉ sau đó bỏ plan_level/cột TEAM và thay unique index năm. Xác nhận không còn FK/consumer tham chiếu header TEAM cũ.
7. Kiểm đối chiếu toàn vẹn sau contract trước mở writer/job bằng code mới. Lỗi trong migration transaction phải rollback; nếu đã commit, giữ maintenance và restore backup đã diễn tập hoặc migration tiến có duyệt. Không hứa đảo DDL bằng app cũ, không sửa migration đã commit.

Không ship app hai bảng giữa expand và cutover. V90 không được auto-apply chỉ để “thử chạy app” trên DB đang dùng. Test expand và contract là prerequisite của cutover, không là việc hoàn thiện migration sau khi đã deploy.

## 3. Tiến độ rà soát

Bảng `edu_team_review_items`, entity/repository riêng, không JSONB review song song:

| Cột | Ý nghĩa |
|---|---|
| id | PK review item; trả API bằng reviewItemId |
| team_plan_id | FK tổ đang mở |
| source_plan_id | FK năm đích của lượt rà soát; khác source_plan_id trên header tổ |
| item_kind/item_id | CONTENT/THEME/GOAL và ID đối tượng tổ; giữ đối tượng tham chiếu cũ tới khi mapping bị mất được xử lý, không FK đa hình giả |
| reason | SOURCE_CHANGED/SOURCE_REMOVED/REF_LOST |
| resolved/resolved_at/resolved_by | Tiến độ và dấu vết người xử lý; user_accounts FK |

Unique (team_plan_id, source_plan_id, item_kind, item_id); nếu một đối tượng có nhiều nguyên nhân thì chọn ưu tiên SOURCE_REMOVED > REF_LOST > SOURCE_CHANGED và kiểm hết các tham chiếu liên quan trước resolve. Liên kết chủ đề bị ảnh hưởng được đánh dấu theo mapping, không chỉ text mục tiêu. Không chỉ dựa số MT; dùng lineage hiệu dụng, không tạo cảnh báo chỉ vì đánh lại số. Mục mới có nhãn mới riêng trong detail, không sinh unresolved bắt buộc chỉ vì mới.

Resolve khóa bản tổ/item, kiểm quyền soạn + DRAFT + lease/revision/request chống lặp như lưu kế hoạch, item thuộc tổ và nguồn đích hiện tại. Item nguồn cũ/revision cũ trả conflict; không chỉ nhận resolved=true từ client. Với nguồn bị xóa, gỡ/remap mọi tick/tham chiếu đang lỗi sang nguồn hiện tại trước resolve; nội dung còn tồn tại phải giữ đúng ID/lineage/cây nguồn. Bản đã ban hành không resolve trực tiếp. Submit tính lại tính hợp lệ và unresolved, không tin flag cũ; chủ đề end_date < ngày hiện tại Asia/Ho_Chi_Minh được miễn đỏ theo FR-014, không ép sửa lịch sử đã kết thúc.

## 4. Cập nhật nguồn nguyên tử (đề xuất thay AFTER_COMMIT)

Năm và tổ cùng module eduplan. Gọi EduTeamUpdateGenerator đồng bộ trong **cùng transaction ban hành bước cuối**; không event AFTER_COMMIT cho thay đổi nguồn bắt buộc. Lỗi copy/diff/lưu ⇒ rollback cả lần ban hành, giữ trạng thái/chữ ký trước thao tác; không để năm APPROVED nhưng tổ bỏ cập nhật. Không thêm outbox/job/retry hạ tầng.

Khóa chuỗi năm/tuổi ổn định rồi khóa các bản tổ theo ID tăng dần; mọi đường create/version/update/issue dùng cùng thứ tự và unique index bảo vệ một bản mở. Lặp request ban hành không tăng version nếu nguồn đích đã áp dụng.

- Legacy không nguồn: chọn tổ liên quan bằng FK curriculum_source_plan_id thuộc chuỗi năm đang ban hành, không chỉ cùng năm/tuổi. Tổ legacy có FK NULL nằm ngoài tập cập nhật tự động; giữ nguyên cờ legacy, nguồn/baseline NULL, nội dung/mapping/trạng thái và nguồn lớp, không tạo review giả hoặc gán năm mới theo tuổi/tên. Đây là thiết kế bảo toàn theo FR-011, không thêm luồng liên kết nguồn thủ công; dữ liệu không thể xác định nguồn phải báo trong inventory, không tự suy diễn.
- Không có chuỗi tổ: không tự tạo lần đầu.
- Có DRAFT/REVIEW: dùng bản đang mở, lưu nguồn trước vào previous_source_plan_id, đổi nguồn mới nhất, REVIEW về DRAFT, mở vòng duyệt mới theo policy khi gửi lại; giữ phần nhập và tiến độ hợp lệ.
- Chỉ có tổ APPROVED: copy bản ban hành hiện hành thành n+1 DRAFT (source_plan_id là bản tổ trước), giữ bản cũ/class docs; không tạo nếu bản mở đã được writer khác tạo.
- Phiên soạn đang mở: khi thực sự đổi nguồn trên bản tổ đang mở, tăng content_revision trong cùng transaction; vô hiệu edit_session_id và toàn bộ editing lease/timestamps theo mô hình hiện có, giữ sổ tác giả/approvals lịch sử. PUT/submit/resolve/heartbeat/finish-editing với revision hoặc session cũ phải conflict, không gia hạn/hồi sinh lease hoặc trả cache save cũ để bỏ qua kiểm tra revision. Client giữ input cục bộ, đọc lại detail và lấy lease mới trước lưu/resolve; không tự áp lại payload cũ vào cây mới. Ban hành lặp khi nguồn đã áp dụng không tăng revision hoặc hủy phiên thêm lần nữa; rollback generator khôi phục cả revision/lease trước chuyển. T050 kiểm các đường này.
- Chuyển nhiều lần: diff nguồn ngay trước chuyển với nguồn mới; hợp các mục còn lỗi từ lượt trước sau khi remap lineage, không âm thầm xóa unresolved vẫn sai. Lượt resolve không còn áp dụng phải invalidated; phần không đổi/hợp lệ giữ tiến độ. Cập nhật baseline, cây nguồn, mapping và items trong một transaction.

## 5. State machine và response

DRAFT → REVIEW → APPROVED; trả lại + lý do về DRAFT. Nguồn mới chuyển tổ đang REVIEW về DRAFT, không sửa APPROVED. Chỉ năm có UI tạo/xem phiên bản. Detail tổ trả previousSourcePlanId, curriculumSourcePlanId, contentRevision, reviewItems và newItems theo contract. Chỉ khi tổ cập nhật duyệt cuối mới thay nguồn tổ hiện hành cho lớp, giữ lịch sử theo EDU-26/62.

## 6. Consumer hồ sơ lớp và kiểm chứng

T048 phải chuyển EduClassDocServiceImpl sang nguồn EduTeamPlan/Team repository: requireIssuedPlan, isSuperseded, batch LoadContext/plans, requireCopySource, khóa header, expectedKey và mọi caller dùng header chung cũ. Dùng service cùng module hiện có, không thêm abstraction cho đủ bước. Schema đổi ownership không có nghĩa các đường đọc Java tự đổi theo.

Lập hồ sơ mới chỉ từ TEAM APPROVED hiện hành, cùng năm/tuổi/phạm vi lớp. Đọc/in hồ sơ lịch sử vẫn theo bản TEAM mà hồ sơ trỏ tới; annual mới ban hành không làm bản tổ cũ superseded. Chỉ tổ cập nhật APPROVED mới làm tổ cũ bị thay thế theo EDU-26 (giữ quyền duyệt/trả lại hồ sơ lớp đang chờ). Chuyển truy vấn cây/theme/templates/authors/lease/revision và copy/remap lineage theo TEAM, không còn tìm TEAM ID trong bảng năm sau cutover. Giữ ID/docKey và payload/API lớp hiện có, không tự đổi format chỉ vì tên field legacy yearPlanId. EduLessonQueryServiceImpl/consumer learning-observation được rà theo caller thực tế: chuyển đường nào còn đọc header chung, regression nguồn giáo án duyệt/MT/lineage, không thay nghiệp vụ đánh giá.

## 7. Bảo toàn và kiểm chứng

Quickstart §0/§4 quy định snapshot trước expand/cutover, EXCEPT hai chiều toàn payload và owner, mọi FK/lineage/tick/class doc, approvals/print_snapshot, legacy và sequence dùng chung. T030/T031 chạy scratch trước T009 production; ghi lệnh/kết quả, không suy test đã đạt từ thiết kế.
