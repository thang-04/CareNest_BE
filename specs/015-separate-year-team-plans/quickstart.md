# Quickstart — kiểm chứng spec015

Đây là kịch bản tương lai, **chưa chạy trong lượt fix tài liệu**. Dùng dữ liệu tổng hợp, DB scratch riêng, native Maven/SQL/Node/browser; không harness/hook hoặc dữ liệu thật. Thiết kế/API/DDL/code/client cần gate T001/T047 theo phạm vi trước thực thi. T031/T035/T037 chạy trên scratch **trước T009 production**. Không boot app tự apply V97 lên DB đang dùng để thử.

## 0. Chuẩn bị, snapshot và rehearsal expand/contract

1. Kiểm migration history/slot V96/V97 và trạng thái Git: đã commit/applied thì không sửa checksum, dùng migration mới. Seed schema trước expand có năm/tổ nhiều version, owner/coauthor, approvals/print_snapshot, lease/revision, legacy không nguồn, cây/tick/mẫu nhánh/class docs và nguồn đánh giá. T029 lưu seed tổng hợp trong src/test/resources/eduplan/.
2. Snapshot trước expand. Apply expand riêng trên scratch bằng công cụ Flyway/native đã có, ghi chính xác command/target/version. Mô phỏng app cũ tạo/sửa thêm sau expand; refresh snapshot ngay trước contract để kiểm backfill cập nhật lần cuối. Không bật app mới ghi hai bảng trong khoảng expand.
3. T030 chuẩn bị candidate V97 và test integration với schema đích. Trước contract dừng writer/job scratch, backup, snapshot; sau contract kiểm đối chiếu §4 trước mở app mới. Diễn tập lỗi giữa migration/restore, ghi kết quả.

Mẫu snapshot SQL **chỉ cho scratch**, chạy cùng database và giữ schema qa_spec015 qua rehearsal. Không phải migration ứng dụng:

```sql
CREATE SCHEMA qa_spec015;
CREATE TABLE qa_spec015.headers AS
SELECT p.id, p.plan_level AS kind, to_jsonb(p) AS payload FROM edu_year_plans p;
CREATE TABLE qa_spec015.children (table_name text, id bigint, payload jsonb);
DO $qa$
DECLARE n text;
BEGIN
  FOREACH n IN ARRAY ARRAY['edu_plan_goals','edu_plan_sub_domains','edu_plan_expected_results',
    'edu_themes','edu_breaks','edu_plan_events','edu_document_authors',
    'edu_plan_branch_templates','edu_class_docs'] LOOP
    EXECUTE format(
      'INSERT INTO qa_spec015.children SELECT %L, c.id,
       (to_jsonb(c) - ''year_plan_id'') || jsonb_build_object(
         ''school_year_plan_id'', CASE WHEN p.plan_level = ''SCHOOL_YEAR'' THEN p.id END,
         ''team_plan_id'', CASE WHEN p.plan_level = ''TEAM'' THEN p.id END)
       FROM %I c LEFT JOIN edu_year_plans p ON p.id = c.year_plan_id', n, n);
  END LOOP;
END $qa$;
CREATE TABLE qa_spec015.contents AS SELECT id, to_jsonb(c) payload FROM edu_goal_contents c;
CREATE TABLE qa_spec015.ticks AS SELECT id, to_jsonb(t) payload FROM edu_content_theme_ticks t;
```

T029/T030 phải assert trước snapshot rằng mọi year_plan_id khác NULL có header; riêng authors class_doc_id có class doc và không đồng thời year_plan_id (không để JOIN che orphan), owners/ID hợp lệ, không có dữ liệu TEAM mới ngoài bảng chung. Schema baseline phải lấy lại trên scratch mới khi rehearsal lượt tiếp theo, không ghi đè snapshot sau chuyển. Ngoài bốn bảng snapshot mẫu, fixture test lưu và so sánh theme_evaluations/presentation_snapshot và mọi consumer FK header/theme thực tế tìm thấy trong inventory.

## 1. US1 — loại/tên, scope và duyệt theo cấu hình

- Tạo/gửi năm PHT→HT và tổ TT→PHT; list/chờ duyệt/detail hiển thị đúng planType/planTypeLabel/title/link. planType/planLevel cùng giá trị cho cùng kết quả; khác nhau bị validation error. Giữ sentBy.at tương thích sentAt.
- Hai tài khoản TT/GV khác tuổi: list/detail/source/version/chờ duyệt đúng phân công; mất phân công mất scope ngay. HT/PHT quản lý hiệu lực mọi tuổi nhưng quyền trạng thái vẫn theo policy năm độc lập, không mở nháp chỉ nhờ scope tuổi.
- HT cấu hình TT duyệt năm/tổ: TT role TEACHER đúng tuổi/lượt REVIEW, không tham gia soạn được approve/return; bước cuối mới APPROVED. Sai tuổi/lượt, mất phân công, owner/coauthor bị từ chối; return thiếu lý do bị từ chối; dữ liệu giữ nguyên ở ca bị chặn. REVIEW cấm sửa/lease kể cả PHT.
- DELETE TEAM bằng HTTP trực tiếp với owner/HT/PHT/TT, lần lượt DRAFT/REVIEW/APPROVED: tất cả bị từ chối, header/children/events/class docs giữ nguyên. DELETE năm vẫn giữ EDU-07 và lock guard; UI tổ không có xóa.

## 2. US2 — UI lập tổ, nguồn và bất biến cây

- Từ list bấm Lập tổ đúng một lần vào form, không modal, không POST tạo row khi chỉ mở form. Nhập tên riêng, chọn đúng một annual APPROVED hiện hành cùng năm/tuổi; trường nguồn đúng nhãn, đủ cây kể cả chưa tick.
- Chọn nguồn nháp/chờ, khác năm/tuổi, ngoài quyền, vừa bị thay thế hoặc mất quyền trước save: server từ chối, form giữ phần nhập và cho chọn lại. Bấm lưu lặp giữ idempotency, không tạo chuỗi thứ hai.
- Tick một nội dung, MT tương ứng đi theo, không tự tick mọi nội dung; lưu/mở lại giữ tên/tick/nguồn. Tên tổ trùng chuỗi chữ với tên năm vẫn độc lập, không đổi tên annual.
- Request giả PUT thêm/sửa/xóa cây MT/nội dung/nhãn hoặc import thay cây TEAM bị server chặn; annual/TEAM trước/sau request không đổi. Preview/apply file annual vẫn đúng quyền và chế độ hiện hành; không dùng giả planType để vượt guard loại thực.
- List đủ 5 cột; chưa gửi “—”, không ngày tạo; gửi rồi dùng sent_at. TEAM không có bảng/nút create/compare/version, annual vẫn có; gọi versions/changes/create-version TEAM trực tiếp bị conflict.
- Bằng chứng SC-002/004 phải có browser E2E trên FE/API thật. Unit/service/mock không đủ chứng minh điều hướng/ẩn UI.

## 3. US3 — chuyển nguồn và review

Ma trận fixture bắt buộc:

| Trạng thái tổ khi annual mới ban hành | Kỳ vọng |
|---|---|
| Không có chuỗi tổ | Không tự lập lần đầu |
| Legacy không nguồn, cùng năm/tuổi với annual | Giữ nguồn/baseline NULL, nội dung/mapping/trạng thái/nguồn lớp; không tự tạo update/review hoặc gán annual |
| Chỉ có tổ APPROVED | Một n+1 DRAFT, giữ bản cũ làm nguồn lớp |
| Đã có DRAFT song song APPROVED | Reuse DRAFT, không thêm bản mở |
| Đã có REVIEW | Reuse, chuyển DRAFT, gửi lại vòng mới sau xử lý |
| Source đã áp dụng, request ban hành lặp | Không thêm version/item/event trùng |

- Annual v2 nháp/chờ chưa đổi tổ. Ban hành v2 đổi text/xóa nội dung/liên kết chủ đề bị ảnh hưởng: chỉ phần ảnh hưởng chưa kết thúc cần review. Chỉ đánh lại số không đỏ; dòng mới nhãn riêng không tự unresolved; end_date < ngày hiện tại Asia/Ho_Chi_Minh không đỏ.
- Tạo lỗi giữa copy/diff/save: rollback cả ban hành năm và cập nhật tổ, trạng thái/approvals cũ giữ nguyên. Ban hành đồng thời/lập tổ đồng thời vẫn tối đa một bản mở, không trùng header ID; không dựa AFTER_COMMIT để chuyển nguồn.
- GET detail/reload giữ previousSourcePlanId, nguồn hiện tại/revision, reviewItems và tiến độ. reviewItemId khác itemId; xác nhận sai tổ/nguồn/revision/lease hoặc còn tick tham chiếu đã xóa bị từ chối. Remap/gỡ tham chiếu lỗi hợp lệ rồi resolve mới được; retry requestId không lặp sự kiện.
- Gửi còn unresolved/tham chiếu sai bị chặn kể cả request giả resolved=true. Năm v3 ban hành trước khi tổ cập nhật hoàn tất: baseline là nguồn dùng ngay trước chuyển v3, giữ phần nhập và carry-forward các lỗi cũ vẫn tồn tại, không tự bỏ cảnh báo; phần hợp lệ không đổi giữ tiến độ.
- Giữ form tổ đang có lease/revision trước chuyển nguồn; ban hành annual mới: revision tăng và session cũ bị vô hiệu trong cùng transaction. PUT/submit/resolve/heartbeat/finish-editing cũ conflict, dữ liệu mới không đổi, input cục bộ còn để đối chiếu; detail/reacquire lease mới rồi ghi hợp lệ. Save-cache request cũ không vượt kiểm revision; xử lý lặp cùng nguồn không tăng revision hoặc hủy lease lần nữa; lỗi generator rollback cả lease/revision. Race save-vs-switch chỉ có thứ tự hợp lệ do khóa, không lost update (T050).
- Legacy không nguồn và tổ có nguồn hợp lệ cùng năm/tuổi trong một fixture: chỉ tổ có FK nguồn thuộc chuỗi annual được chuyển, legacy giữ nguyên cả DRAFT/REVIEW/APPROVED, không baseline/review giả. Đối chiếu trước/sau và retry theo T049, không dùng cùng tuổi để đoán liên kết.
- FE có lý do bằng chữ kèm màu, mục mới riêng, thao tác sửa/tick/resolve, conflict giữ input; submit khóa khi còn lỗi. Bản tổ cũ/class docs/lịch sử giữ nguyên đến khi tổ cập nhật duyệt cuối. Hồ sơ lớp mới chỉ nhận TEAM APPROVED hiện hành, không annual; regression nguồn giáo án đã duyệt/lineage đánh giá và EDU-26 không mất dữ liệu do chuyển FK.

## 4. US4 — đối chiếu sau contract và gate cutover

Không truy vấn plan_level/year_plan_id đã drop. Sau contract, trước mở writer, các truy vấn sau phải trả **0 dòng**:

```sql
-- ID header phải rời nhau; sequence chung sinh mới không trùng sau restart/concurrency.
SELECT y.id FROM edu_year_plans y JOIN edu_team_plans t ON y.id = t.id;

-- Legacy đúng nguồn NULL, tổ có nguồn không bị gán sang TEAM hoặc năm/tuổi khác.
SELECT t.id FROM edu_team_plans t LEFT JOIN edu_year_plans y ON y.id=t.curriculum_source_plan_id
WHERE (t.legacy_team_without_source AND t.curriculum_source_plan_id IS NOT NULL)
   OR (NOT t.legacy_team_without_source AND
       (y.id IS NULL OR y.school_year_id<>t.school_year_id OR y.age_group_id<>t.age_group_id));

-- So toàn payload TEAM cũ: chỉ bỏ plan_level; baseline mới chưa có lịch sử phải NULL.
WITH expected AS (
  SELECT id, payload - 'plan_level' AS payload FROM qa_spec015.headers WHERE kind='TEAM'
), actual AS (
  SELECT id, to_jsonb(t) - 'previous_source_plan_id' AS payload FROM edu_team_plans t
)
(SELECT * FROM expected EXCEPT SELECT * FROM actual)
UNION ALL
(SELECT * FROM actual EXCEPT SELECT * FROM expected);

-- So toàn payload annual, loại các cột TEAM được chủ đích bỏ.
WITH expected AS (
  SELECT id, payload - ARRAY['plan_level','curriculum_source_plan_id',
      'legacy_team_without_source','duration_policy_snapshot'] AS payload
  FROM qa_spec015.headers WHERE kind='SCHOOL_YEAR'
), actual AS (SELECT id, to_jsonb(y) AS payload FROM edu_year_plans y)
(SELECT * FROM expected EXCEPT SELECT * FROM actual)
UNION ALL
(SELECT * FROM actual EXCEPT SELECT * FROM expected);

-- Nội dung và tick giữ nguyên toàn bộ; chạy tương tự hai chiều cho cả hai bảng.
(SELECT id,payload FROM qa_spec015.contents
 EXCEPT SELECT id,to_jsonb(c) FROM edu_goal_contents c)
UNION ALL
(SELECT id,to_jsonb(c) FROM edu_goal_contents c
 EXCEPT SELECT id,payload FROM qa_spec015.contents);
(SELECT id,payload FROM qa_spec015.ticks
 EXCEPT SELECT id,to_jsonb(t) FROM edu_content_theme_ticks t)
UNION ALL
(SELECT id,to_jsonb(t) FROM edu_content_theme_ticks t
 EXCEPT SELECT id,payload FROM qa_spec015.ticks);
```

Kiểm cả 9 owner và payload, báo lỗi nếu có sai khác:

```sql
DO $qa$
DECLARE n text; bad boolean;
BEGIN
  FOREACH n IN ARRAY ARRAY['edu_plan_goals','edu_plan_sub_domains','edu_plan_expected_results',
    'edu_themes','edu_breaks','edu_plan_events','edu_document_authors',
    'edu_plan_branch_templates','edu_class_docs'] LOOP
    EXECUTE format('SELECT EXISTS (
      (SELECT id,payload FROM qa_spec015.children WHERE table_name=%L
       EXCEPT SELECT id,to_jsonb(c) FROM %I c)
      UNION ALL
      (SELECT id,to_jsonb(c) FROM %I c
       EXCEPT SELECT id,payload FROM qa_spec015.children WHERE table_name=%L)
    )', n,n,n,n) INTO bad;
    IF bad THEN RAISE EXCEPTION 'Payload/ownership changed: %', n; END IF;
    IF n='edu_document_authors' THEN
      SELECT EXISTS (SELECT 1 FROM edu_document_authors a
        LEFT JOIN edu_year_plans y ON y.id=a.school_year_plan_id
        LEFT JOIN edu_team_plans t ON t.id=a.team_plan_id
        LEFT JOIN edu_class_docs d ON d.id=a.class_doc_id
        WHERE num_nonnulls(a.school_year_plan_id,a.team_plan_id,a.class_doc_id)<>1
           OR (a.school_year_plan_id IS NOT NULL AND y.id IS NULL)
           OR (a.team_plan_id IS NOT NULL AND t.id IS NULL)
           OR (a.class_doc_id IS NOT NULL AND d.id IS NULL)) INTO bad;
    ELSE
      EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I c
      LEFT JOIN edu_year_plans y ON y.id=c.school_year_plan_id
      LEFT JOIN edu_team_plans t ON t.id=c.team_plan_id
      WHERE (c.school_year_plan_id IS NULL)=(c.team_plan_id IS NULL)
         OR (c.school_year_plan_id IS NOT NULL AND y.id IS NULL)
         OR (c.team_plan_id IS NOT NULL AND t.id IS NULL))', n) INTO bad;
    END IF;
    IF bad THEN RAISE EXCEPTION 'Invalid owner: %', n; END IF;
  END LOOP;
END $qa$;
```

T030 phải hoàn thiện/execute SQL mẫu trên scratch và ghi actual output trước coi rehearsal đạt.

T030 còn phải assert: không orphan từ trước snapshot; class docs mới TEAM-only/legacy đúng inventory; lineage/self-FK/snapshot tác giả/duyệt/in giữ đủ; tick/content/theme cùng owner; CHECK/FK validate toàn bộ (gồm FK ghép cây/theme/templates và unique tác giả theo 3 target); không còn FK tới header TEAM đã xóa; sequence/default hai bảng dùng chung và next ID > max hợp hai bảng. Chạy ca create/copy/concurrent/restart ở DB scratch clone sau đối chiếu baseline, tránh làm sai phép so trước/sau bằng dữ liệu test mới.

T009 production chỉ sau T001/T030/T031/T034/T035/T037/T047 và review từng phase: maintenance, backup/restore rehearsal, dùng bản SQL đã duyệt, đối chiếu trước xóa/sau chuyển và smoke bằng code mới. Bất kỳ sai khác/fail/skip liên quan bảo toàn chặn mở writer. Transaction lỗi thì rollback; sau commit giữ maintenance và restore backup hoặc migration tiến có duyệt, không chạy app cũ trên schema mới.

## Kết quả cần báo cáo

Ghi target scratch/production, migration version/checksum, commit/code/schema, command đầy đủ, fixture, số ca pass/fail/skip và output đối chiếu. Chưa chạy ghi chưa kiểm chứng; không đánh task hoàn thành bằng việc viết kịch bản. Báo cáo tài liệu sau refinement ở [analysis.md](analysis.md), không thay evidence runtime.

## 5. Consumer sau tách bảng — T048 (trước cutover)

Trên schema đích scratch, seed TEAM APPROVED lịch sử/hiện hành, annual và hồ sơ branch/theme/week với đủ ID/docKey/authors/snapshot. Test requireIssuedPlan/isSuperseded/batch context/source-copy/lock/preview/save/submit không còn lookup TEAM ID ở edu_year_plans. Tạo mới chỉ từ TEAM APPROVED hiện hành đúng lớp-năm-tuổi, annual hoặc TEAM nháp/sai scope bị chặn; đọc/in hồ sơ cũ vẫn từ đúng TEAM lịch sử.

Ban hành annual mới khi tổ cập nhật chưa duyệt: nguồn lớp cũ chưa superseded, đường đọc/in/soạn theo EDU-26 còn đúng. Duyệt tổ cập nhật: nguồn cũ chỉ xem/in, người duyệt vẫn xử lý hồ sơ lớp đang chờ theo rule; lập/copy sang nguồn tổ mới remap lineage hợp lệ. Rà và test EduLessonQueryServiceImpl/consumer observation lấy đúng MT từ giáo án đã duyệt, không lấy annual hoặc mất lineage vì header TEAM đã chuyển bảng. Giữ API field yearPlanId/docKey hiện có mang ID TEAM, không đổi key lịch sử. Báo rõ caller đã rà/test và phần chưa kiểm chứng, không dùng kiểm FK/row count thay bằng chứng hành vi.
