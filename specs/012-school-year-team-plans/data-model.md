# Data model — feature012

**Thiết kế delta Q4=A/Q5=A cập nhật 07/11/2026:** mẫu nhánh PHT tùy chọn lấy/không lấy và sửa tên bản lớp; đổi thứ tự chuyển cả khối chủ đề kèm nhánh, giữ số tuần học, dời ngày. Thiết kế đã cập nhật theo quyết định mới, còn draft chờ duyệt triển khai; không giữ ràng buộc khóa tên hoặc bắt lấy đủ mẫu cũ.

Thiết kế đề xuất 07/11/2026; chưa tạo entity/migration. Giữ Java 21/PostgreSQL 17, ADR-0021 ba bảng cây, ID BIGINT identity, enum VARCHAR+CHECK, tên constraint theo repo. [Spec](spec.md), [Research](research.md).

## 1. Hai cấp cùng họ kế hoạch

`edu_year_plans` thêm:

| Field | Kiểu / constraint | Nghĩa |
| --- | --- | --- |
| plan_level | VARCHAR(20) NOT NULL, CHECK SCHOOL_YEAR/TEAM | Cấp; dữ liệu cũ TEAM |
| curriculum_source_plan_id | BIGINT NULL FK self RESTRICT | Bản SCHOOL_YEAR chính xác mà TEAM kế thừa |
| legacy_team_without_source | BOOLEAN NOT NULL DEFAULT false | Chỉ migration/clone version legacy thiết lập, không có trong request |
| duration_policy_snapshot | JSONB NOT NULL | Limits/mode/revision áp dụng lúc tạo; bất biến sau tạo |

Giữ `source_plan_id` là predecessor cùng cấp, `version_no`, status, revision/lease, authors, approvals và print snapshot. Unique `(plan_level, school_year_id, age_group_id, version_no)`; partial unique `(plan_level, school_year_id, age_group_id) WHERE status <> 'APPROVED'`. Index curriculum source. Namespace code mới `KHN-<year>-<age>-V<n>` theo convention generator hiện có; TEAM giữ mã cũ. Không đổi source/version IDs để đổi nhãn UI.

CHECK: SCHOOL_YEAR phải source NULL và legacy=false; TEAM phải có source hoặc legacy=true, không đồng thời cả hai. Với source: service kiểm APPROVED, level SCHOOL_YEAR, cùng năm/nhóm tuổi, không phải self, có quyền xem nguồn; lock source khi tạo/copy. Không dùng CHECK/FK đơn để giả bảo đảm điều kiện cross-row. Predecessor cũng phải cùng level/năm/nhóm tuổi. Relation/marker/level không đổi qua PUT. Mọi query current/version/open/source đều gồm level.

POST phiên bản TEAM có thể chọn curriculumSourcePlanId mới tường minh để nhận annual mới, hoặc nhận annual lần đầu cho chuỗi legacy. Version mới clone curriculum từ nguồn được chọn, map ticks/themes/templates bằng lineage, có change summary/warnings cho liên kết không còn đích; không có orphan và không ghép theo tên. sourceTemplateId của mẫu tổ mới trỏ annual mới, IDs local remap sang plan mới. Predecessor vẫn bản TEAM trước; snapshot/marker của bản mới INHERITED theo nguồn, không sửa predecessor. Không truyền source mới thì clone nguồn/legacy mode của chuỗi cũ như trước; limits vẫn chụp mới vì version mới là hồ sơ mới. Source pointer bất biến suốt đời mỗi record, không có sync khi nguồn đổi.

## 2. Curriculum snapshot

Giữ `edu_plan_sub_domains`, `edu_plan_expected_results`, `edu_plan_goals`, `edu_goal_contents`, `edu_content_theme_ticks`. Tạo source-backed TEAM clone hierarchy/domain/order/text/curriculum refs/lineage từ annual approved; sinh ID cục bộ và giữ lineage nguồn. GoalNo theo snapshot, không tự đánh lại do tick. Theme IDs map sang bản tổ trước khi copy nhánh mẫu; annual không có content-theme ticks để copy. Nguồn là exact version, không tham chiếu live tree.

Để guard immutable không cần thêm FK source ở mọi row. So request với canonical persisted tree theo từng loại row, không nhầm ID content với ID goal. Team source-backed cho phép `themeKeys` thay đổi, không thay domain/kind/parent/order/text/row IDs/content membership/curriculum refs; xóa dòng bằng omission cũng là vi phạm. Validate trước saveThemes/saveBreaks hay flush bất kỳ phần nào để lỗi rollback toàn bộ. Legacy TEAM giữ logic cây cũ, không được request tự chuyển sang legacy. Nhập file/nguồn/Bộ không được bypass guard.

## 3. Nhánh mẫu của kế hoạch

Bảng mới `edu_plan_branch_templates`:

| Field | Kiểu / constraint |
| --- | --- |
| id | BIGINT GENERATED ALWAYS AS IDENTITY PK |
| year_plan_id | BIGINT NOT NULL FK edu_year_plans RESTRICT |
| theme_id | BIGINT NOT NULL; FK ghép `(year_plan_id, theme_id)` tới theme cùng plan |
| source_template_id | BIGINT NULL FK self RESTRICT, TEAM copy từ annual; version copy theo remap |
| lineage_id | BIGINT NULL, lineage hiệu dụng COALESCE(lineage_id,id) |
| name | VARCHAR(150) NOT NULL; nháp có thể rỗng, gửi/ban hành phải có tên |
| start_date / end_date | DATE NULL cho mẫu gợi ý chỉ có tên, kể cả nguồn đã ban hành |
| sort_order | INTEGER NOT NULL |
| created_at / updated_at | TIMESTAMPTZ NOT NULL |

Thêm unique phù hợp trên `edu_themes(year_plan_id,id)` để FK ghép thật. Index plan/theme và source; source_template_id nếu có phải là nhánh annual đúng curriculum_source_plan_id và cha tương ứng, kiểm trong service. Không unique name toàn trường; identity khác tên. SCHOOL_YEAR tạo/sửa nhánh theo author policy mặc định PHT. Mẫu nhánh trên TEAM giữ source identity và tên nguồn; GV không sửa mẫu nguồn nhưng được đổi tên bản nhánh lấy về lớp. GV được chọn không lấy/bỏ chọn từng mẫu theo Q4=A, không còn ràng buộc khóa tên bản lớp. Version clone giữ lineage và remap FK, không trỏ theme của version khác. Không thêm nhánh lớp vào bảng mẫu.

Xóa nháp xóa template con trong transaction trước plan/theme; source đã được bản kế thừa tham chiếu không bị xóa nhầm nhờ FK RESTRICT. Xóa chủ đề không được làm hỏng FK template nguồn; mọi remap/xóa tường minh đi qua source guard và rule nháp hiện có.

## 4. Tài liệu lớp và lịch v2

Giữ `edu_class_docs`, FK `year_plan_id` chỉ được trỏ TEAM APPROVED đúng năm/tuổi lớp (service enforced), kind `branch|theme|week`, status và docKey cũ. Thêm `duration_policy_snapshot JSONB NULL`: có giá trị cho branch, theme/week dùng lịch branch đã duyệt. Snapshot không nhận từ client.

`branch.content` mới:

| Field | Shape | Quyền / validation |
| --- | --- | --- |
| schemaVersion | 2 | Nhận 2, format khác reject |
| themeSchedules | array `{themeId,startDate,endDate}` | ID theme của TEAM; không đổi tên/nội dung nguồn; được đổi thứ tự dạy tại lớp, chuyển cả chủ đề kèm nhánh, giữ số tuần học và dời ngày theo thứ tự mới (Q5=A) |
| branches | array `{key,themeId,sourceTemplateId,name,startDate,endDate,weekNotes}` | key UUID ổn định, theme thuộc TEAM; name tại lớp sửa được, source identity giữ đúng nếu lấy mẫu; mẫu tùy chọn lấy/bỏ chọn (Q4=A); custom source NULL; weekNotes map theo offset tuần học0..n-1 trong nhánh |
| notes | read projection `{Monday:note}` | v2 sinh từ branch.weekNotes, không là nguồn ghi; v1 adapter giữ từng note theo Monday vào offset0 của nhánh legacy1tuần |
| names | read projection `{Monday:name}` | Server sinh từ branches/effective calendar; v2 không dùng names làm nguồn ghi |

Client tạo UUID cho nhánh chưa lưu; server kiểm định dạng/unique và source theo persisted/source plan. Name tại lớp có thể khác tên mẫu, không còn kiểm tên bằng mẫu hoặc tự coi đổi tên là giả source. Không yêu cầu mọi mẫu phải có mặt trong bản lớp; việc không lấy/bỏ mẫu là hợp lệ theo Q4=A; ownership/source ID vẫn phải hợp lệ. Nhánh custom được GV thêm trong tài liệu branch DRAFT đúng lớp, theo workflow creator hiện có. Không mở quyền PHT sửa hồ sơ lớp chỉ vì PHT tạo mẫu annual.

Lịch hiệu lực: legacy chưa có themeSchedules dùng lịch TEAM; v2 lấy lịch lớp. `EduTeachingCalendar` tính tuần trọn T2–T6, bỏ tuần nghỉ phủ toàn bộ; nghỉ lẻ vẫn tính; chủ đề lớn cùng cấp không overlap, nhánh trong cha và không overlap. Khi gửi branch: mỗi tuần học của mỗi chủ đề phải thuộc đúng một nhánh có tên (giữ rule đủ tên cả năm EDU-14); nhánh có thể 1 hoặc 2 tuần theo snapshot, không suy từ hai tên bằng nhau. Nháp cho lịch/tên chưa đủ, nhưng ownership/source identity bất hợp lệ luôn bị chặn.

DocKeys giữ `bp_<class>_<teamPlan>`, `tp_<class>_<teamTheme>`; week là `wp_<class>_<Monday>` khi versionNo=1, `wp_<class>_<Monday>_<teamPlan>` khi versionNo>1, đúng helper hiện tại (`EduClassDocServiceImpl.java:725`, FE planVersions.mjs:32). Một nhánh hai tuần có hai week docs riêng, không dùng một key chung theo nhánh và không bỏ suffix của version mới. Theme/week lưu branchKey/calendarRevision cho trace của lịch đã dùng; giá trị server resolve, không nhận làm quyền. Khi branch đang REVIEW/APPROVED không sửa lịch; thay đổi sau ban hành đi theo version/clone hiện có, không sửa nguồn/dossier đã duyệt. Không tự di chuyển/xóa week docs hoặc học liệu khi đổi ngày; nếu nháp phụ thuộc cần sửa thì báo unresolved theo cơ chế version fixes trước gửi.

### 4.1. Mẫu nhánh và thứ tự chủ đề — delta đã chốt

- Không thêm schema delta ngoài bảng template/JSON v2 đã đề xuất. Không có cột mandatory/selected/teacherCanRename; mảng branches chính là các bản lớp đã lấy hoặc tự tạo. Bắt đầu với branches rỗng hợp lệ khi nháp; GET/normalize/restore/import không tự lấy lại tất cả mẫu. Bỏ chọn mẫu là xóa entry local trong nháp được phép, không xóa template. Key/sourceTemplateId/themeId của entry đã tồn tại không đổi chỉ vì sửa tên; chọn nguồn khác là thao tác lấy bản mới có key mới, không tráo provenance của key cũ.
- Pure helper `reorderThemeBlocks` trong EduTeachingCalendar nhận base calendar của **form**, orderedThemeIds, breaks, bounds và snapshot limits; trả calendar mới/counts. Không lưu một cột sort_order riêng cho lớp: thứ tự dạy suy từ dates, orderedThemeIds chỉ là command. Theme name/content/ticks không thuộc dữ liệu được sửa của command; giữ toàn bộ theme IDs/branch UUIDs/source/parent/local names/notes.
- Trục tính ngày là các Monday học đang được các theme base sử dụng, sắp tăng; giữ anchor sớm nhất của trục. Chia trục theo tuần học từng theme trong order mới, chuyển branches bằng offset tuần học trong cha; bỏ tuần nghỉ trọn theo hợp break T2–T6, nghỉ lẻ vẫn tính. Chạy resolver lại trước trả để chứng minh counts chủ đề/nhánh không đổi. Không cộng7ngày máy móc hoặc swap range2tuần với range4tuần.
- Nếu trục có khoảng trống tuần học khiến range sau chia bao thêm tuần ngoài trục, hoặc dates/branch offsets chưa đủ/ngoài năm/overlap thì trả lỗi calendar-invalid, không thay form/DB. Không tự pack lịch để lấp/nén gap và không tự tạo đợt nghỉ. Đây là giới hạn biểu diễn range hiện tại; user có thể dùng chỉnh lịch FR-010 riêng rồi đổi thứ tự, không có rule mới bắt lịch cả năm kín tuần.
- Preview là pure read, chưa ghi. PUT branch v2 nhận optional orderedThemeIds: có thì server dùng cùng helper trên draft được gửi, kiểm invariant và lưu kết quả một transaction; không có thì đi nhánh sửa lịch/duration FR-010 bình thường. Áp lại cùng order lên calendar đã preview là idempotent, nên không cần token lưu DB/counter/schema mới. Nếu user đổi duration sau preview thì đó là chỉnh lịch được phép, helper khi lưu lấy draft hiện tại làm base; không lấy duration đã lỗi thời từ source.
- Khi lưu/reorder kiểm expected branch version theo convention cũ: -1 khi chưa có record, version hiện tại khi đã có; không dùng0 làm sentinel tạo mới. CalendarRevision đọc từ identity/revision branch+TEAM, không cấp quyền cho request stale. Plan→branch→dossier/week lock order giữ nguyên; các dependent draft bị lệch ngày hiện unresolved, không bị mất khỏi truy vấn chỉ vì Monday mới khác.
- Copy sang TEAM version mới remap theme/template bằng lineage, giữ local name/custom và việc không lấy mẫu; không auto-add templates mới. Mẫu nguồn không còn khớp được báo để user xử lý version fixes, không ghép theo tên hoặc tự chọn nguồn khác. Không đổi dữ liệu review/approved hoặc rename docKey/weekStart/media khi chỉ reorder.
- Ghi chú tuần không được ở lại Monday cũ khi reorder: FE cũ dùng notes[Monday] (`BranchPlanPage.jsx:587`, `classPlanDoc.js:223`). V2 ghi ở branch.weekNotes theo offset tuần học, pure helper giữ nguyên map theo UUID/offset, sinh lại notes[Monday] cho ngày mới. Hai tuần của một nhánh có ghi chú riêng không bị gộp/hoán đổi. V2 serializer bỏ notes projection; validate keys offset thuộc số tuần nhánh, không drop note mồ côi âm thầm. Adapter v1 không sửa DB khi đọc, giữ notes vào từng nhánh tuần riêng.

## 5. Cấu hình thời lượng

`edu_settings`: thêm `duration_policy JSONB NOT NULL`, `duration_revision BIGINT NOT NULL DEFAULT 1`, updated_by/at theo pattern workflow. Nội dung `{theme:{minWeeks:2,maxWeeks:4},branch:{minWeeks:1,maxWeeks:2}}`; integer>0, min≤max mỗi loại; không thêm giới hạn tuyệt đối mới hoặc min/max liên loại chưa có rule. Revision expected body, khóa settings/recheck quyền HT còn hiệu lực cùng transaction. Không dùng workflow_revision làm duration revision.

Snapshot: `{mode:'BOUNDED',revision,theme:{minWeeks,maxWeeks},branch:{minWeeks,maxWeeks}}` chụp khi tạo kế hoạch/branch. Hồ sơ legacy được snapshot `{mode:'LEGACY_UNBOUNDED'}` trong migration: giữ validate ngày cũ, không áp trần mới hồi tố. Snapshot của hồ sơ cũ không bị cập nhật khi cấu hình đổi. Version mới sau cutover là hồ sơ mới nên chụp limits hiện hành, dù copy nội dung legacy; nháp được lưu chưa đủ nhưng phải chỉnh hợp lệ trước gửi/ban hành. Theme/week không có lịch riêng để tự thay limits, dùng branch nguồn như một phần hồ sơ. Không client field nào được đặt `LEGACY_UNBOUNDED`.

## 6. Workflow và chuyển trạng thái

`workflow_policy` thêm key `schoolYear`, giữ `year`=TEAM/branch/theme. Existing custom values giữ nguyên. Seed schoolYear `{creators:['VICE_PRINCIPAL'],reviewers:['PRINCIPAL']}`. Publication/print JSON `documentType:'year'` legacy vẫn TEAM; không rewrite. `edu_document_authors.year_plan_id` dùng cả hai cấp, không nhập tác giả nguồn vào target. Reconcile chọn key từ level, chỉ loại thay đổi chịu effect, lịch sử đã ban hành bất biến.

Nháp → REVIEW → APPROVED; trả lại về Nháp kèm lý do; chỉ sửa Nháp; không tự duyệt, reviewer precedence theo loại, thiếu người bước cuối giữ chờ. Duration validators và source guard dùng cả submit/approve cuối/auto-complete reconcile, không để policy change ban hành hồ sơ invalid.

## 7. Downstream read model

Thêm `EduPlanCalendarQueryService`/Impl trả projection `EffectiveTheme(themeId,lineage,teamPlanId,name,startDate,endDate)` theo từng classroom, chỉ đọc TEAM đã ban hành và lịch lớp branch APPROVED; fallback lịch TEAM khi chưa có lịch lớp được duyệt (gồm legacy), không để draft lịch lớp đổi kết quả đánh giá live. Caller observation kiểm viewer/child scope trước gọi, tương tự EduLessonQueryService. Không query lấy annual làm nguồn chỉ vì số version cao.

`ThemeEvaluationRepository` giữ semantics mục tiêu/issuer/year goals nhưng filter TEAM trong ba query. `ThemeEvaluationServiceImpl` dùng projection lịch lớp cho list/get/confirm/range enrollment/daily results/signers/childThemeResults từng lớp; không đổi công thức đánh giá. `entity/observation/ThemeEvaluation.java:41–48` đã lưu theme_name/start_date/end_date và cells nhưng matrix/view vẫn lấy goal columns/membership từ live theme (`ThemeEvaluationServiceImpl.java:332`). Để không mất bản đã chốt khi nguồn bỏ theme, thêm `theme_evaluations.presentation_snapshot JSONB NULL`: `{origin,theme:{id,lineageId,name,startDate,endDate},goals:[{lineageId,goalNo,text}],childIds:[...]}`. Snapshot chỉ chứa metadata cần đọc bản chốt, không thêm dữ liệu trẻ thật vào fixture/docs.

Khi xác nhận bản mới, chụp presentation và đồng bộ name/dates theo lịch lớp cùng transaction với cells; origin `CONFIRMED`. List union confirmed saved với themes live theo class+lineage, frozen ưu tiên; GET/history link lookup confirmed trước, kể cả theme không còn trong TEAM hiện hành, resolve historical ID→lineage theo scope nếu cần. Frozen view dùng saved name/date/cells/presentation, không chạy lại enrollment/live-goals; childThemeResults cũng đọc frozen riêng từng lớp chứ không chỉ lặp themes hiện hành.

Migration mới backfill CONFIRMED legacy origin `LEGACY_BASELINE`: giữ name/date/cells đã lưu, childIds/goal lineages từ cells; goal labels lấy từ TEAM lịch sử theo lineage (ưu tiên bản approved tại thời điểm confirm nếu timestamps đủ, nếu không lấy baseline lịch sử cuối cùng có theme tại cutover). Không bịa text mất dấu hoặc tuyên bố tái dựng chính xác metadata vốn chưa snapshot; ghi origin baseline. Thiếu goal/theme lineage thực sự thì migration dry-run/gate báo dữ liệu cần xử lý, không drop cell hoặc đoán mapping. Không thay cells hoặc thời điểm/người xác nhận. Regression theme bị bỏ ở version mới vẫn list/get được và frozen child results không mất là exit phase2.

## 8. Migration và rollout

Đề xuất ba migration mới sau V89: `V83__split_education_plan_levels.sql` (level/source/legacy/index/workflow key và limits/snapshot), `V84__add_education_branch_templates.sql` (templates), `V85__snapshot_theme_evaluation_presentation.sql` (metadata frozen phục vụ tương thích lịch/source, không tính lại cells). Số được kiểm tra lại ngay trước implement nếu branch có migration mới; không sửa file đã commit hoặc có trong môi trường dùng chung.

1. Backup/test trên DB synthetic từ V89. Thêm cột, backfill toàn bộ plans=TEAM, legacy=true, snapshot LEGACY_UNBOUNDED. Không sửa status/review_round/approvals/history/print_snapshot/goal IDs hoặc source_plan_id.
2. Đổi unique/version/open indexes gồm level, thêm source constraints, thêm schoolYear policy nếu chưa có; không reset policies cũ. Seed duration mặc định cho hồ sơ mới.
3. Tạo bảng mẫu rỗng, không suy nhánh mẫu annual từ tên từng tuần cũ. Legacy branch JSON giữ nguyên trong DB; adapter đọc ra v2 theo từng Monday với key ổn định sinh từ docKey+Monday, không gom theo tên. Không flush chuyển đổi khi chỉ GET.
4. Khi user thực sự lưu legacy branch còn DRAFT bằng client v2, chuyển sang shape v2 trong transaction/version guard, giữ từng tuần và snapshot legacy của hồ sơ đó. REVIEW/APPROVED đọc tương thích, không rewrite.
5. Migration integration so checksum dữ liệu lịch sử trước/sau, FK không orphan; concurrency tạo cùng level chỉ một open version, hai level tồn tại song song.

Rollback không drop schema/dữ liệu sau khi có annual hoặc v2 writes; rollback app chỉ được dùng bản đọc được schema/payload mới. Trước writes mới có thể tắt UI mới trong đợt rollout phối hợp. Sau writes mới ưu tiên forward fix; phục hồi backup là tác vụ vận hành cần user duyệt, không tự viết down-migration phá dữ liệu.

### Delta nguồn user — FE review 07/11/2026
- Mẫu nhánh/dự án được nhập tên ngay trong cột phần 2 annual, Enter thêm dòng. start/end mẫu nullable; không bắt lịch để ban hành annual khi chỉ gợi ý tên. Khi GV lấy mẫu về lớp thì chọn lịch theo snapshot limits và parent bounds. TEAM giữ cột map mục tiêu.
- FR018/EDU57: canDelete=false và DELETE TEAM bị từ chối nếu actor có vai trò PHT; không đổi quyền xóa các cấp/chức năng khác.

## Delta displayLabel — 07/11/2026

EduPlanGoal.displayLabel: nullable VARCHAR(100), migration V93 additive. Chỉ GOAL có field; sub/group trả null. Không unique vì nhãn không làm khóa. Dữ liệu cũ null dùng MT + goalNo; không backfill. Null/omitted giữ nhãn trên update, empty/whitespace clear. Clone annual→TEAM và version giữ label. Quyền sửa/lease/status cũ giữ; TEAM từ chối mutation nhãn. Không đổi quan hệ hoặc goalNo.

## FR021 — không đổi schema
Dùng lại edu_plan_branch_templates.theme_id/start_date/end_date. So overlap trong mỗi themeId, không thêm index/constraint hoặc đổi dữ liệu legacy tự động.
