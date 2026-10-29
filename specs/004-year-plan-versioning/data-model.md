# Data model & thiết kế database — feature 004

PostgreSQL 17, Flyway. Convention `docs/database/DATABASE.md` mục 9: id `BIGINT GENERATED ALWAYS AS IDENTITY` (riêng `edu_class_docs` vẫn BIGSERIAL như V25), cột audit `created_at`/`updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, người tạo theo cột có sẵn của bảng (`owner_id` của kế hoạch năm = người tạo phiên bản; `created_by`/`updated_by` của tài liệu lớp). Tên ràng buộc `uk_/ck_/fk_/idx_`. Migration mới **V29__add_edu_plan_versions.sql**; không sửa V8–V28.

## 1. Kế hoạch năm theo phiên bản — `edu_year_plans`

| Cột mới | Kiểu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| version_no | INTEGER NOT NULL DEFAULT 1 | `ck_edu_year_plans_version_no` ≥ 1 | Số phiên bản trong chuỗi (năm học, nhóm tuổi) |
| source_plan_id | BIGINT NULL | `fk_edu_year_plans_source_plan_id` → edu_year_plans(id); `ck_edu_year_plans_source` (version_no = 1) = (source_plan_id IS NULL) | Phiên bản được chép |

Ràng buộc thay thế EDU-01:

- DROP `uk_edu_year_plans_school_year_age_group`.
- ADD `uk_edu_year_plans_school_year_age_group_version UNIQUE (school_year_id, age_group_id, version_no)`.
- ADD `CREATE UNIQUE INDEX uk_edu_year_plans_open_version ON edu_year_plans (school_year_id, age_group_id) WHERE status <> 'APPROVED'` — tối đa một phiên bản chưa ban hành (kể cả EDITING).
- `idx_edu_year_plans_source_plan_id` cho tra phiên bản con.

Audit của phiên bản: `owner_id`/`owner_title` (người tạo), `created_at`, `updated_at`, `sent_by/at`, `issued_by/at` có sẵn. Mã: phiên bản 1 giữ `KHNH-…`, phiên bản n ≥ 2 = mã nguồn gốc + `-V<n>` (≤30 ký tự; UNIQUE code giữ nguyên).

Hiện hành: `max(version_no) where status = 'APPROVED'` trong chuỗi (truy vấn, không lưu cờ để không lệch).

## 2. Lineage — dòng mục tiêu, nội dung, chủ đề, đợt nghỉ

| Bảng | Cột mới | Index |
| --- | --- | --- |
| edu_goal_rows | lineage_id BIGINT NULL | idx_edu_goal_rows_lineage_id |
| edu_goal_contents | lineage_id BIGINT NULL | idx_edu_goal_contents_lineage_id |
| edu_themes | lineage_id BIGINT NULL | idx_edu_themes_lineage_id |
| edu_breaks | lineage_id BIGINT NULL | — |

- Lineage hiệu dụng = `COALESCE(lineage_id, id)`. Dòng gốc (kể cả dữ liệu trước V29) để NULL; dòng chép đặt `lineage_id = COALESCE(src.lineage_id, src.id)`. Không FK (dòng gốc có thể bị xóa ở phiên bản sau; lineage chỉ là định danh).
- PUT kế hoạch: dòng có id giữ nguyên lineage; dòng mới (id null) có lineage NULL = chính nó (MT "mới").
- Invariant: trong một kế hoạch, lineage hiệu dụng duy nhất theo bảng (chép 1-1). Không ép bằng index (biểu thức coalesce + plan) để PUT không vấp thứ tự ghi; service bảo đảm.

## 3. Tài liệu lớp — `edu_class_docs`

| Cột mới | Kiểu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| source_doc_id | BIGINT NULL | `fk_edu_class_docs_source_doc_id` → edu_class_docs(id) ON DELETE SET NULL; idx | Tài liệu cùng lớp/loại ở phiên bản cũ được chép |
| adjustments | JSONB NOT NULL DEFAULT '{}' | `ck_edu_class_docs_adjustments` jsonb_typeof = 'object'; `ck_edu_class_docs_adjustments_week` (kind = 'week' OR adjustments = '{}') | "Kế hoạch điều chỉnh và bổ sung": { "yyyy-mm-dd": "văn bản" } |

- `year_plan_id` sẵn có = phiên bản tài liệu thuộc về; unique tự nhiên `(classroom_id, year_plan_id, kind, theme_id, week_start)` đã tách theo phiên bản.
- `doc_key` tuần cho phiên bản ≥2: `wp_<lớp>_<thứ Hai>_<year_plan_id>` (BE kiểm khớp).
- `content.versionChecks`: mảng chuỗi `goal:<id>` / `theme:<id>` (id phiên bản mới) mà giáo viên đã đánh dấu "Đã kiểm tra". Nằm trong content JSONB do Web soạn; BE đọc khi gửi.
- `sourceDocKey` chỉ nhận khi tạo (PUT lần đầu), BE tra id, kiểm cùng lớp, cùng loại, kế hoạch cùng chuỗi và phiên bản thấp hơn.

## 4. Thực thể logic (DTO, không lưu)

- **YearPlanChanges** `{ planId, versionNo, fromPlanId, fromVersionNo, goals[], contents[], themes[] }`; mỗi phần tử `{ lineageId, id, fromId, change: UNCHANGED|CHANGED|REMOVED|ADDED, fields[] }`; goals thêm `goalNo`, `fromGoalNo`, `text`, `fromText`; contents thêm `goalLineageId`; themes thêm `name`, `fromName`.
- **VersionSummary** trong chi tiết kế hoạch: `{ id, versionNo, status, ownerName, createdAt, issuedAt, current }`.

## 5. Trạng thái và chuyển

```text
v1 APPROVED (hiện hành) --create-version--> v2 DRAFT/EDITING --submit--> PENDING_VP --issue--> APPROVED
                                                       \--(PHT owner) issue--------------------/
v2 APPROVED ⇒ v1 "đã thay thế" (tính bằng truy vấn); tài liệu lớp của v1: chỉ xem, duyệt/trả lại hồ sơ đang chờ, sửa adjustments
```

## 6. Migration V29 (nội dung)

```sql
ALTER TABLE edu_year_plans
    ADD COLUMN version_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN source_plan_id BIGINT,
    ADD CONSTRAINT ck_edu_year_plans_version_no CHECK (version_no >= 1),
    ADD CONSTRAINT ck_edu_year_plans_source CHECK ((version_no = 1) = (source_plan_id IS NULL)),
    ADD CONSTRAINT fk_edu_year_plans_source_plan_id FOREIGN KEY (source_plan_id) REFERENCES edu_year_plans (id);
ALTER TABLE edu_year_plans DROP CONSTRAINT uk_edu_year_plans_school_year_age_group;
ALTER TABLE edu_year_plans ADD CONSTRAINT uk_edu_year_plans_school_year_age_group_version
    UNIQUE (school_year_id, age_group_id, version_no);
CREATE UNIQUE INDEX uk_edu_year_plans_open_version ON edu_year_plans (school_year_id, age_group_id)
    WHERE status <> 'APPROVED';
CREATE INDEX idx_edu_year_plans_source_plan_id ON edu_year_plans (source_plan_id);

ALTER TABLE edu_goal_rows ADD COLUMN lineage_id BIGINT;
ALTER TABLE edu_goal_contents ADD COLUMN lineage_id BIGINT;
ALTER TABLE edu_themes ADD COLUMN lineage_id BIGINT;
ALTER TABLE edu_breaks ADD COLUMN lineage_id BIGINT;
CREATE INDEX idx_edu_goal_rows_lineage_id ON edu_goal_rows (lineage_id);
CREATE INDEX idx_edu_goal_contents_lineage_id ON edu_goal_contents (lineage_id);
CREATE INDEX idx_edu_themes_lineage_id ON edu_themes (lineage_id);

ALTER TABLE edu_class_docs
    ADD COLUMN source_doc_id BIGINT,
    ADD COLUMN adjustments JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT fk_edu_class_docs_source_doc_id FOREIGN KEY (source_doc_id) REFERENCES edu_class_docs (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_edu_class_docs_adjustments CHECK (jsonb_typeof(adjustments) = 'object'),
    ADD CONSTRAINT ck_edu_class_docs_adjustments_week CHECK (kind = 'week' OR adjustments = '{}'::jsonb);
CREATE INDEX idx_edu_class_docs_source_doc_id ON edu_class_docs (source_doc_id);
```

Rollback (thủ công, chỉ khi chưa có phiên bản ≥2): DROP các cột/index trên, tạo lại `uk_edu_year_plans_school_year_age_group`. Dữ liệu cũ hợp lệ ngay sau V29 (version_no = 1, source NULL, lineage NULL, adjustments '{}').
