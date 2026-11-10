-- RAG dùng chung cho mọi module AI (ADR-0007): tài liệu tham khảo đã duyệt, cắt chunk và lưu embedding (pgvector).
-- Không chứa dữ liệu trẻ. LLM không tự quyết tài liệu còn hiệu lực: lọc bằng status/effective_date/expiry_date.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE ai_documents (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title          VARCHAR(255)  NOT NULL,
    document_type  VARCHAR(40)   NOT NULL,
    source_name    VARCHAR(255)  NOT NULL,
    source_url     VARCHAR(1000),
    -- Mã vùng áp dụng (vd. HAI_PHONG); ALL = áp dụng mọi nơi
    region         VARCHAR(50)   NOT NULL DEFAULT 'ALL',
    effective_date DATE          NOT NULL,
    expiry_date    DATE,
    version        VARCHAR(50),
    status         VARCHAR(20)   NOT NULL,
    created_by     BIGINT        NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_ai_documents_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_ai_documents_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_ai_documents_document_type CHECK (document_type IN (
        'NUTRITION_STANDARD', 'FOOD_SAFETY_GUIDANCE', 'FOOD_SAFETY_ALERT', 'HEALTH_MONITORING_GUIDANCE',
        'CHILD_DEVELOPMENT_STANDARD', 'SCHOOL_POLICY', 'SCHOOL_SOP', 'APPROVED_MENU', 'MENU_GUIDELINE',
        'OTHER_REFERENCE')),
    CONSTRAINT ck_ai_documents_valid_range CHECK (expiry_date IS NULL OR expiry_date >= effective_date)
);
CREATE INDEX idx_ai_documents_status_type_region ON ai_documents (status, document_type, region);

-- embedding để kiểu vector không cố định số chiều: đổi provider/model không cần migration, chỉ cần reindex.
-- Không tạo index ANN: kho tài liệu của một trường nhỏ, quét tuần tự sau khi lọc metadata vẫn nhanh.
CREATE TABLE ai_document_chunks (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id     BIGINT       NOT NULL,
    chunk_index     INTEGER      NOT NULL,
    content         TEXT         NOT NULL,
    -- SHA-256 của content: chunk trùng nội dung với cùng model thì dùng lại embedding, không gọi API
    content_hash    VARCHAR(64)  NOT NULL,
    embedding_model VARCHAR(100),
    embedding       vector,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_ai_document_chunks_document_id FOREIGN KEY (document_id) REFERENCES ai_documents (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_ai_document_chunks_document_chunk UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_ai_document_chunks_hash_model ON ai_document_chunks (content_hash, embedding_model);
