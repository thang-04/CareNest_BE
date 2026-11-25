-- RAG soạn giáo án (bước a): kho đoạn tham khảo MT / hoạt động / giáo án mẫu + vector để truy xuất theo ngữ nghĩa.
-- AI-04: chỉ chứa văn bản chương trình và giáo án mẫu công khai (đã bỏ tên giáo viên, tên trường); KHÔNG có dữ liệu trẻ.
-- model/content_hash lệch hoặc embedding NULL ⇒ embed lười lại; AI tắt thì truy xuất bằng từ khóa (AI-03).
-- Không tạo index ANN (HNSW): khoảng 1.000 dòng, quét tuần tự đủ nhanh.
CREATE TABLE edu_rag_chunks (
    id           VARCHAR(80)   NOT NULL PRIMARY KEY,
    chunk_type   VARCHAR(20)   NOT NULL, -- mt | activity | lesson
    source       VARCHAR(20)   NOT NULL, -- SEED | ISSUED | WEB
    source_ref   VARCHAR(255),
    content      TEXT          NOT NULL,
    meta         JSONB         NOT NULL DEFAULT '{}',
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    model        VARCHAR(100),
    content_hash VARCHAR(64)   NOT NULL,
    embedding    public.vector,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_edu_rag_chunks_type CHECK (chunk_type IN ('mt', 'activity', 'lesson')),
    CONSTRAINT ck_edu_rag_chunks_status CHECK (status IN ('ACTIVE', 'HIDDEN'))
);

CREATE INDEX idx_edu_rag_chunks_type_status ON edu_rag_chunks (chunk_type, status);
