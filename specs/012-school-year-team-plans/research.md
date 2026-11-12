# Research — Tách năm học / tổ

**Cập nhật thiết kế sau Q4=A/Q5=A, 07/11/2026:** mẫu nhánh PHT tùy chọn, tên bản lớp sửa được; đổi thứ tự chuyển cả khối chủ đề/nhánh, giữ số tuần học. Research delta đã hoàn tất; thiết kế bên dưới thay các ràng buộc khóa tên/bắt lấy đủ mẫu cũ, vẫn chờ user duyệt triển khai.

Ngày 07/11/2026. Nghiên cứu source hiện có; không đổi source, migration, dependency hoặc client. Hai research agent độc lập rà persistence/workflow và consumers FE; root rà lịch, nhập nhánh và downstream. Kết luận dưới đây là đề xuất thiết kế để user duyệt, không phải nghiệp vụ mới hay bằng chứng implementation.

## 1. Persistence và phiên bản

**Decision:** giữ `edu_year_plans`/các bảng con; thêm `plan_level = SCHOOL_YEAR | TEAM`, mặc định tương thích TEAM. Thêm `curriculum_source_plan_id` riêng cho tổ lấy nguồn từ bản năm chính xác. Copy snapshot cây qua cơ chế hiện có, không dùng chung mutable rows.

**Evidence:** `src/main/resources/db/migration/V32__add_edu_plan_versions.sql:4` dùng `source_plan_id` cho predecessor, CHECK tại dòng 7; unique version và open-version tại dòng 13–15 chưa phân cấp. `V28__create_edu_class_docs.sql:33` giữ FK tới ID kế hoạch. `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java:628` tạo version; `:1302` clone cây, lineage và remap tick. Code kế hoạch có unique toàn cục, cần namespace riêng cho năm mới; không đổi code cũ của tổ.

**Rationale:** giữ ID goal/theme/docKey, approval, version và các FK đã được dùng để đánh giá. Snapshot cục bộ cho phép cùng logic tick theo content và cùng lineage diff; không rewrite lịch sử.

**Alternatives:** bảng năm riêng nhân đôi version/workflow/author targets; tham chiếu live cây năm làm quyền sở hữu, tick và version phụ thuộc nguồn có thể đổi; dùng `source_plan_id` cho cả hai quan hệ phá CHECK V32. Không chọn.

**Review correction:** source FK bất biến trên từng record không có nghĩa toàn chuỗi bị khóa nguồn mãi. POST version TEAM cho chọn annual mới tường minh, absent vẫn clone như cũ; legacy→inherited cũng qua version này. Map bằng lineage, giữ bản cũ và full review, không live-sync. Contract/detail có sourceChanges/remapWarnings, không bỏ liên kết không khớp mà không thông báo.

## 2. Cây kế thừa cố định và legacy

**Decision:** TEAM có nguồn năm copy toàn cây tại tạo; ghi sau đó kiểm cây persisted chuẩn hóa, chỉ đổi `themeKeys` của content và các phần kế hoạch được phép. So ID, kind, domain, cha, thứ tự, text, curriculum refs, membership; reject trước mutation, không silently ignore. SCHOOL_YEAR không nhận tick.

**Evidence:** `EduYearPlanServiceImpl.java:1043` hiện save toàn cây và xóa dòng/content vắng mặt; `:1130` lấy tick từ `themeKeys`, `:1146` ghi delta. `src/main/java/com/carenest/service/eduplan/impl/EduGoalFileServiceImpl.java:37` chỉ preview, không thay thế guard khi save. `CareNest_FE/src/components/education-plan/GoalTables.jsx:182` một cờ readOnly chung hiện khóa cả tick; `GoalFormPage.jsx:504` có đường nhập/chép nguồn/Bộ.

**Rationale:** đúng câu clarify “logic như cũ, chỉ cố định thông tin”. Tách quyền `canEditCurriculum`/`canAssignContents`, không khóa cả form rồi mất tick.

**Legacy:** migration đánh dấu hồ sơ tổ cũ không có nguồn năm bằng `legacy_team_without_source`; giữ logic soạn/phiên bản cũ và toàn bộ dữ liệu của chúng, không gán giả nguồn. Guard nguồn cố định áp dụng tổ thực sự kế thừa năm. Nhánh legacy source-less chỉ sinh từ migration và version cùng chuỗi, client không được tự chọn miễn trừ. Đây là tương thích theo FR-013 và chỉ dẫn giữ logic cũ; cần test riêng trước cutover.

## 3. Workflow không đổi nghĩa key cũ

**Decision:** `year` tiếp tục là TEAM; thêm `schoolYear` cho SCHOOL_YEAR, mặc định PHT → HT. Không reset policy TEAM/branch/theme trường đã cấu hình; mặc định TEAM hiện đã là TL → PHT. Resolve policy từ level tại mọi action/read/reconcile/lease/preview.

**Evidence:** `src/main/resources/db/migration/V80__expand_edu_workflow_policy.sql:7` seed ba key; `EduWorkflowServiceImpl.java:138`, `:394`, `:410`, `:435` hardcode year; exact-key validator tại `:1124`, `:1232`; settings lock/revision/reconcile tại `:1178`, `:1216`. FE `src/models/education-plan/eduConfig.js:21` đang gọi year là Kế Hoạch Tổ.

**Rationale:** không rewrite `completion_policy`, `print_snapshot`, approvals hoặc ledger cũ. Người soạn năm không tự trở thành tác giả bản tổ chỉ vì copy; ledger target vẫn từng document ID. Chỉ policy keys thực sự đổi được reconcile, tránh bump revision ở loại không đổi.

**Alternative:** đổi year→team trên mọi payload và publication JSON tăng rủi ro mà không cần cho yêu cầu; không chọn. Settings revision vẫn một giá trị như hiện có cho workflow.

## 4. Lịch lớp và nhánh nhiều tuần

**Decision:** thêm nhánh mẫu có định danh tại kế hoạch năm/tổ; tài liệu `branch` lớp schema v2 lưu lịch chủ đề hiệu lực và danh sách nhánh có key/range/source identity. Giữ theme ID của tổ và docKey tuần theo Thứ Hai, nhánh không thay thế đơn vị giáo án tuần. Tập trung dựng lịch ở `EduTeachingCalendar`, dùng cùng đầu vào cho preview/save/submit/dossier/version checks.

**Evidence:** `EduClassDocServiceImpl.java:149` TeachingWeek, `:845` dựng tuần từ theme nguồn; `:957` kiểm `names[weekStart]` và `:993` nhóm tuần dossier. `:923` nguồn chỉ kiểm APPROVED + cùng năm/tuổi, thiếu TEAM. FE `src/models/education-plan/classPlan.js:95` dựng tuần từ plan.themes, `:142` names theo tuần; `BranchPlanPage.jsx:118` một tên mỗi tuần; `src/utils/education-plan/classPlanDoc.js:164` in cứng “1 tuần”.

**Rationale:** một nhánh hai tuần cần identity/range, không suy diễn tên trùng nghĩa là cùng nhánh. Teacher đổi lịch của lớp, không ghi đè `edu_themes` của tổ đã ban hành. Tuần vẫn tạo đủ hai giáo án, toàn bộ EDU-13/17 về trình tự/hồ sơ được giữ.

**Alternatives:** hai names tuần giống nhau không có identity, khó theo dõi nguồn nhánh khi GV sửa tên bản lớp; tạo thêm bảng cho mọi ô lịch lặp lại JSON docs đang có; không chọn. Chỉ nhánh mẫu ở kế hoạch dùng bảng có FK, lịch lớp tiếp tục JSONB hiện có.

## 5. Cấu hình thời lượng và legacy không hồi tố

**Decision:** endpoint riêng `/education-plans/duration-policy` phục vụ cùng trang cấu hình, chỉ HT ghi; settings lưu JSON limits + revision. Hồ sơ có lịch (năm/tổ/branch lớp) chụp `duration_policy_snapshot` khi tạo. Theme/week dùng lịch đã duyệt của branch, không tự lấy lại global limits. Giới hạn dương nguyên, min≤max từng loại; không tự áp trần mới 2/4 tuần lên hồ sơ đã tồn tại.

**Evidence:** `EduConfigRequest.java:16` cấu hình chung hiện có quyền rộng hơn phần HT-only; workflow đã tách endpoint/expected revision tại `EduWorkflowPolicyRequest.java:12`. `YearPlanPublicationValidator.java:39` là cổng publication dùng cả action và reconcile, phải dùng cùng validator ở hai đường. `EduClassDocServiceImpl.java:845` đã có semantics bỏ tuần nghỉ trọn.

**Rationale:** không thêm field bắt buộc vào config chung làm PHT phải roundtrip cấu hình HT-only; tương tự workflow riêng. Legacy snapshot có mode `LEGACY_UNBOUNDED`, bảo toàn kiểm tra ngày cũ nhưng không áp min/max mới. Hồ sơ tạo mới sau nâng cấp chụp limits đang cấu hình; không kế thừa mode miễn trừ qua request hay tự copy sang version mới.

## 6. Contract và rollout

**Decision:** thêm metadata/field có chủ đích, giữ `/goals` mặc định TEAM cho GET không filter và POST không level. URL/ID cũ vẫn tổ. Source-backed TEAM chỉ tạo server-side từ annual APPROVED đúng scope; không mở tạo nguồn-less mới. Client FE cập nhật mới bật trang năm và JSON lịch v2; backend có thể đọc JSON legacy.

**Evidence:** FE `src/models/education-plan/planVersions.mjs:14` chain chỉ year/age và `planUi.jsx:29` nguồn GV không phân cấp; `src/services/education-plan/api/educationPlanApi.js:151` tải tất cả. BE `YearPlanRequest.java:19`, detail `:14`, summary `:8` chưa có level. `EduClassDocServiceImpl.java:106` metadata client bị strip, cần bổ sung fields readonly mới.

**Rationale:** không để client cũ trộn hai cấp/chọn sai nguồn cho GV. Backend default TEAM không bảo đảm client cũ vẫn tạo được TEAM nguồn-less: đường tạo đó chủ động từ chối sau chuyển đổi; cần rollout FE đồng bộ, không tuyên bố backward compatibility tuyệt đối.

## 7. Downstream và test

`EduLessonQueryServiceImpl.java:48` đọc giáo án tuần đã APPROVED theo ngày/goal ID: giữ contract để không đổi đánh giá ngày. `service/observation/impl/ThemeEvaluationServiceImpl.java:121`, `:190`, `:277` lấy theme và ngày kết thúc qua native query; `:332` dùng range cho enrollment và chấm ngày, `:236` tổng hợp theo từng lớp trẻ từng học. `repository/observation/ThemeEvaluationRepository.java:47`, `:57`, `:87` có ba query chọn plan chưa lọc cấp: themes, issuer, year goals; consumer phiếu cuối năm `YearEndSheetServiceImpl.java:182`. Phải lọc TEAM và resolve lịch hiệu lực theo đúng lớp tại mọi đường đọc chưa chốt; không dùng ngày nguồn chung hoặc lịch lớp hiện tại cho lớp cũ. Bản đánh giá đã xác nhận giữ frozen data/metadata; không tính lại. Mở projection service chỉ đọc ở eduplan cho observation, không phụ thuộc ngược hoặc mở endpoint đánh giá mới. Đây là phần tương thích bắt buộc của FR-010/012/013, không mở rộng nghiệp vụ đánh giá.

Codebase Memory được dùng để trace teachingWeeks: xác nhận previewBranchFile/saveClassDoc/unresolvedVersionFixes/requireCanSubmit/dossierWeekDocs là callers. Graph có cạnh nhập nhằng tên getId/add sang module khác, nên chỉ dùng các caller đã xác minh bằng source, không đưa cạnh nhiễu vào thiết kế.

**Review correction:** date metadata đã lưu chưa đủ để đọc bản confirmed khi theme biến khỏi current plan; findTheme source hiện current-only tại `ThemeEvaluationServiceImpl.java:277`. Thiết kế union/lookup confirmed trước live projection, thêm presentation snapshot goal columns/membership để frozen cells còn đọc được mà không phụ thuộc current curriculum. Legacy backfill là baseline có nguồn, không giả lịch sử chính xác nếu trước đây chưa snapshot; xem data model mục7/8.

## 8. Research delta Q4/Q5 — mẫu tùy chọn và đổi thứ tự

**Decision:** không thêm bảng/cột/chỉ dấu mandatory hoặc selected cho delta. `branches[]` chứa các bản lớp đã chọn/tự tạo, UUID không đổi khi rename/reorder. `sourceTemplateId` optional chỉ trace nguồn; template không có trong branches là chưa chọn/bỏ chọn hợp lệ, không auto-add lại khi GET/normalize/import/restore. Mẫu nguồn readonly, name local mutable, không kiểm localName==templateName. Lịch đủ nhánh khi gửi là calendar coverage (EDU-14), độc lập template coverage đã bỏ.

**Evidence:** `EduClassDocServiceImpl.java:957–965` kiểm tên theo mọi tuần học, không biết template; `:725–729` khóa tuần đã có suffix plan ID ở version>1. FE `src/models/education-plan/planVersions.mjs:164` clone branch hiện chỉ đổi docId, phải remap theme/template lineage v2 nhưng giữ local name/custom và trạng thái không chọn mẫu. `BranchPlanPage.jsx:78` manual save/backup hiện có; không thêm autosave khi lấy mẫu/reorder.

**Decision retime:** pure `EduTeachingCalendar.reorderThemeBlocks(base, orderedThemeIds, breaks, yearBounds, limits)` trả lịch mới cùng identities và counts. Thao tác reorder được phân biệt với resize FR-010 bằng `orderedThemeIds` trong command preview/save; cùng helper ở BE để không chỉ tin phép tính FE. Endpoint preview chỉ tính, không persist; PUT branch cũ vẫn là điểm ghi, có expected version. Không tạo bảng sự kiện thao tác hoặc lưu thêm counter riêng.

1. Base là lịch nháp hiện có của form (có thể chưa lưu), không phải luôn lịch nguồn TEAM. Validate scope, readonly theme data, branch/source identity, dates đủ để tính, bounds/overlap và snapshot limits; thiếu ngày trả lỗi giữ nguyên form, không đoán từ hôm nay.
2. `orderedThemeIds` phải là permutation đầy đủ/duy nhất đúng tập theme; anchor là Monday sớm nhất trong vùng lịch hiện có. Ghi counts của từng theme, offset đầu/cuối theo **tuần học** của từng branch trong cha; tên/ID/source/parent giữ nguyên.
3. Dựng axis là union các Monday học đang thuộc các theme trong base, bỏ tuần nghỉ toàn bộ theo predicate cũ (hợp nhiều break phủ đủ T2–T6 cũng được bỏ). Không thêm slot từ ngày trống, không nén gap hoặc tự biến gap thành nghỉ. Chia lại axis theo counts của themes theo thứ tự mới; branches theo offsets trong danh sách Monday mới của cha.
4. Chuyển các slot thành date ranges T2→T6 và chạy resolver lại: nếu range băng qua **tuần học chưa từng thuộc lịch** làm tăng counts, hoặc offsets không biểu diễn được/ra khỏi năm/overlap thì báo không thể sắp theo lịch hiện có; không thay base. Đây là conservative validation dùng cấu trúc range hiện tại, không phải quy tắc tự lấp khoảng trống. GV vẫn có thao tác chỉnh lịch FR-010 riêng để tạo lịch hợp lệ rồi thử lại.
5. Trả counts trước/sau, lịch mới, revision và projection names; cùng order trên lịch đã tính là idempotent. A2/B4→B4/A2, nhánh 1/2 tuần giữ tuần khi qua nghỉ trọn/nghỉ lẻ. Không rename docKey, move weekStart/media hay sửa hồ sơ đã ban hành; bản sao nháp phụ thuộc lịch cũ được báo unresolved theo cơ chế version fixes trước gửi.

**Alternatives:** swap hai khoảng ngày làm đổi số tuần trái Q5; pack liên tục hoặc dời gaps theo suy đoán tạo quy tắc chưa có; chỉ sắp tên hiển thị không đổi lịch; chỉ FE tính mà BE không phân biệt reorder/resize. Không chọn. Hạn chế lịch có gaps và ca reject phải hiện rõ trong preview/test, không hứa mọi permutation luôn tự thành công.

**Decision import từ form rỗng:** response phân biệt `RENAME` branch có sẵn và `CREATE_CUSTOM` cho tuần học chưa có nhánh, không phụ thuộc đã lấy template. Mặc định giữ granularity từng tuần của parser cũ; một entry không tự thành nhánh hai tuần vì hai tên giống nhau. Với limits mới không cho một tuần thì cảnh báo chưa đạt giới hạn để GV ghép/chỉnh trước gửi; không tự lấy duration/ngày trong file hoặc áp rule ghép chưa chốt.

**Evidence:** FE `src/models/education-plan/importBranchFile.mjs:7–18` ghi được vào map names rỗng; `ImportBranchFileModal.jsx:28,37,109` hiện request chỉ class/yearPlan và key theo Monday. `BranchPlanPage.jsx:281–299` chưa guard local revision toàn form nên rename/reorder/source switch trong lúc preview có thể bị response cũ ghi đè. Mọi response preview phải gắn request identity + form revision; không chỉ calendar revision DB. Apply creates UUID một lần, bỏ response stale/đóng modal/lỗi không đổi form.

**Validation delta:** thêm Q18/Q19 ở quickstart, source key regression cho version>1, mẫu none/some/all, none→import custom, duplicate/foreign source IDs, rename giữ provenance, reorder có selected/custom branches/gaps/full-break union, snapshot/copy/version/late-response/manual-save. Không cần migration/dependency mới ngoài ba migration đã đề xuất ở plan trước.

**Ghi chú đi theo nhánh:** source FE `BranchPlanPage.jsx:587`, `classPlanDoc.js:223` đọc notes[Monday]. Chỉ giữ nguyên map này khi retime sẽ làm ghi chú ở lại tuần cũ. Chọn v2 branch.weekNotes theo offset tuần học, root notes chỉ là projection Monday; v1 adapter chép note vào offset0 từng nhánh tuần, không gộp note hoặc ghi chuyển đổi lúc GET. Pure retime giữ UUID/offset notes và sinh projection mới. Không thêm bảng hoặc field DB riêng; test hai note khác nhau của nhánh2tuần khi đổi thứ tự, import rename không xóa notes.

## Kết quả Phase 0 sau cập nhật

Không cần library/infra mới, không còn unknown kỹ thuật phải giả làm rule. Các phương án ở trên là đề xuất triển khai, chờ user duyệt plan/contract làn L. Contract đầy đủ ở [contracts/education-plans.md](contracts/education-plans.md), dữ liệu ở [data-model.md](data-model.md); không tạo tasks hoặc code ở bước plan.

## Nhãn MT — 07/11/2026

Chọn cột nhãn nullable riêng thay vì đổi goalNo thành text hoặc lưu localStorage: giữ liên kết/consumer và tải lại đa phiên. Chọn null/omitted=no-op, blank=clear để request record String tương thích client cũ mà không thêm thư viện. Chuỗi kéo là presentation-only; giữ số liên tục EDU09. Không mở rộng nhãn sang pipeline đánh giá/đổi keys ngoài scope annual/TEAM.
