-- EDU-12..18: tài liệu kế hoạch của lớp (chủ đề nhánh, kế hoạch chủ đề, giáo án tuần gồm kế hoạch tuần + 5 kế hoạch ngày)
-- gộp một bảng. Cột khóa, trạng thái là cột thường (lọc, ràng buộc, khóa ngoại); nội dung soạn, người duyệt, lịch sử là
-- JSONB vì cấu trúc bảng tuần / tiến trình ngày đổi theo cấu hình dòng hoạt động của trường và luôn đọc cả tài liệu.
CREATE TABLE edu_class_docs (
    id            BIGSERIAL    PRIMARY KEY,
    doc_key       VARCHAR(80)  NOT NULL,
    kind          VARCHAR(10)  NOT NULL,
    classroom_id  BIGINT       NOT NULL,
    year_plan_id  BIGINT       NOT NULL,
    theme_id      BIGINT,
    week_start    DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    content       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    approvals     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    history       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_by    BIGINT       NOT NULL,
    updated_by    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_edu_class_docs_key UNIQUE (doc_key),
    CONSTRAINT ck_edu_class_docs_kind CHECK (kind IN ('branch', 'theme', 'week')),
    CONSTRAINT ck_edu_class_docs_status
        CHECK (status IN ('DRAFT', 'PENDING_TL', 'PENDING_VP', 'PENDING_HT', 'REJECTED', 'APPROVED')),
    -- Mỗi loại đúng các khóa của nó: branch theo lớp + kế hoạch năm, theme thêm chủ đề, week thêm thứ Hai đầu tuần
    CONSTRAINT ck_edu_class_docs_shape CHECK (
        (kind = 'branch' AND theme_id IS NULL AND week_start IS NULL)
        OR (kind = 'theme' AND theme_id IS NOT NULL AND week_start IS NULL)
        OR (kind = 'week' AND theme_id IS NOT NULL AND week_start IS NOT NULL AND EXTRACT(ISODOW FROM week_start) = 1)),
    CONSTRAINT ck_edu_class_docs_json CHECK (
        jsonb_typeof(content) = 'object' AND jsonb_typeof(approvals) = 'array' AND jsonb_typeof(history) = 'array'),
    CONSTRAINT fk_edu_class_docs_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_edu_class_docs_year_plan FOREIGN KEY (year_plan_id) REFERENCES edu_year_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_edu_class_docs_theme FOREIGN KEY (theme_id) REFERENCES edu_themes (id),
    CONSTRAINT fk_edu_class_docs_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id),
    CONSTRAINT fk_edu_class_docs_updated_by FOREIGN KEY (updated_by) REFERENCES user_accounts (id)
);

-- Một tài liệu cho mỗi (lớp, kế hoạch năm, loại, chủ đề, tuần) kể cả khi khóa Web đổi cách đặt
CREATE UNIQUE INDEX uq_edu_class_docs_natural
    ON edu_class_docs (classroom_id, year_plan_id, kind, COALESCE(theme_id, 0), COALESCE(week_start, DATE '1970-01-01'));
