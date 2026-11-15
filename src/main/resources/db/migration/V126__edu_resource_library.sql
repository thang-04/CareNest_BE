-- Spec 900 / EDU-82…88 (PROPOSED, chờ Tiền duyệt): học liệu lưu một lần trong kho, giáo án ngày chỉ giữ dòng gắn.
-- Bảng edu_learning_resources giữ nguyên (không dùng nữa), xóa bằng migration riêng sau khi kiểm tra.
CREATE TABLE edu_resource_items (
    id BIGSERIAL PRIMARY KEY,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('FILE', 'LINK')),
    title VARCHAR(255) NOT NULL CHECK (length(btrim(title)) > 0),
    owner_id BIGINT NOT NULL REFERENCES user_accounts(id),
    scope VARCHAR(10) NOT NULL DEFAULT 'MINE' CHECK (scope IN ('MINE', 'CLASS', 'SCHOOL')),
    classroom_id BIGINT REFERENCES classrooms(id),
    share_status VARCHAR(10) CHECK (share_status IN ('PENDING', 'REJECTED')),
    share_reason VARCHAR(1000),
    share_by BIGINT REFERENCES user_accounts(id),
    share_at TIMESTAMPTZ,
    age_group_id BIGINT,
    hidden BOOLEAN NOT NULL DEFAULT false,
    original_name VARCHAR(255),
    content_type VARCHAR(120),
    byte_size BIGINT,
    storage_key VARCHAR(255) UNIQUE,
    upload_request_id UUID,
    url VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_resource_item_kind CHECK (
        (kind = 'FILE' AND storage_key IS NOT NULL AND url IS NULL)
        OR (kind = 'LINK' AND url IS NOT NULL AND storage_key IS NULL)),
    CONSTRAINT ck_resource_item_class CHECK (scope <> 'CLASS' OR classroom_id IS NOT NULL),
    CONSTRAINT uq_resource_item_request UNIQUE (owner_id, upload_request_id)
);
CREATE INDEX ix_resource_items_owner ON edu_resource_items(owner_id);
CREATE INDEX ix_resource_items_scope ON edu_resource_items(scope, age_group_id);
CREATE INDEX ix_resource_items_class ON edu_resource_items(classroom_id);

CREATE TABLE edu_resource_item_tags (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES edu_resource_items(id) ON DELETE CASCADE,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('THEME', 'GOAL')),
    ref_id BIGINT,
    curriculum_goal_id BIGINT,
    name VARCHAR(500),
    CONSTRAINT uq_resource_item_tag UNIQUE (item_id, kind, ref_id)
);
CREATE INDEX ix_resource_item_tags_item ON edu_resource_item_tags(item_id);

CREATE TABLE edu_resource_attachments (
    id BIGSERIAL PRIMARY KEY,
    class_doc_id BIGINT NOT NULL REFERENCES edu_class_docs(id) ON DELETE CASCADE,
    activity_key VARCHAR(180) NOT NULL
        CHECK (activity_key ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}#(hoc|ngoai_troi|goc|chieu)$'),
    activity_label VARCHAR(1000) NOT NULL,
    item_id BIGINT NOT NULL REFERENCES edu_resource_items(id) ON DELETE RESTRICT,
    created_by BIGINT NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_resource_attachment UNIQUE (class_doc_id, activity_key, item_id)
);
CREATE INDEX ix_resource_attachments_item ON edu_resource_attachments(item_id);

-- Chuyển học liệu cũ: mỗi tệp thành một học liệu "Của tôi" của người tải (giữ nguyên khóa S3, không chép tệp)
ALTER TABLE edu_resource_items ADD COLUMN legacy_id BIGINT;
INSERT INTO edu_resource_items (kind, title, owner_id, age_group_id, original_name, content_type, byte_size,
                                storage_key, upload_request_id, created_at, updated_at, legacy_id)
SELECT 'FILE',
       left(coalesce(nullif(regexp_replace(r.original_name, '\.[^.]*$', ''), ''), r.original_name), 255),
       r.uploaded_by, c.age_group_id, r.original_name, r.content_type, r.byte_size, r.storage_key,
       -- một người trùng mã yêu cầu ở nhiều giáo án: chỉ dòng đầu giữ mã
       CASE WHEN row_number() OVER (PARTITION BY r.uploaded_by, r.upload_request_id ORDER BY r.id) = 1
            THEN r.upload_request_id END,
       r.created_at, r.created_at, r.id
FROM edu_learning_resources r
JOIN edu_class_docs d ON d.id = r.class_doc_id
LEFT JOIN classrooms c ON c.id = d.classroom_id;

INSERT INTO edu_resource_attachments (class_doc_id, activity_key, activity_label, item_id, created_by, created_at)
SELECT r.class_doc_id, r.activity_key, r.activity_label, i.id, r.uploaded_by, r.created_at
FROM edu_learning_resources r
JOIN edu_resource_items i ON i.legacy_id = r.id
WHERE r.activity_key IS NOT NULL;

INSERT INTO edu_resource_item_tags (item_id, kind, ref_id)
SELECT i.id, 'THEME', d.theme_id
FROM edu_learning_resources r
JOIN edu_resource_items i ON i.legacy_id = r.id
JOIN edu_class_docs d ON d.id = r.class_doc_id
WHERE d.theme_id IS NOT NULL;

ALTER TABLE edu_resource_items DROP COLUMN legacy_id;
