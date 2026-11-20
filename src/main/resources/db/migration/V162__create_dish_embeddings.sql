-- AI-03, AI-06: vector ngữ nghĩa của món (pgvector) chỉ để xếp lại thứ tự món ứng viên theo "mong muốn" của PHT, luôn sau
-- lọc cứng bằng SQL; không chứa dữ liệu trẻ. Thiếu/lỗi vector thì gợi ý chạy như cũ.
-- Extension nằm ở schema public và kiểu được gọi kèm schema: test migration chạy nhiều schema trên cùng một DB, extension
-- chỉ tạo một lần nên kiểu "vector" không được phụ thuộc search_path của schema đang migrate.
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

-- Một dòng mỗi món. Kiểu vector không cố định số chiều: đổi model embedding chỉ cần embed lại (model/content_hash
-- lệch ⇒ embed lười lần gợi ý kế tiếp). Không tạo index ANN: danh mục món của một trường chỉ vài trăm món, quét tuần
-- tự sau khi lọc theo model vẫn nhanh.
CREATE TABLE dish_embeddings (
    dish_id      BIGINT        NOT NULL PRIMARY KEY,
    model        VARCHAR(100)  NOT NULL,
    -- SHA-256 của văn bản embed (tên, loại, thực phẩm chính, nhãn mùa): đổi nội dung món ⇒ lệch hash ⇒ embed lại
    content_hash VARCHAR(64)   NOT NULL,
    embedding    public.vector NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_dish_embeddings_dish_id FOREIGN KEY (dish_id) REFERENCES dishes (id) ON DELETE CASCADE
);
