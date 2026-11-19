-- AI-02, AI-07: người duyệt được sửa món trên nháp gợi ý tuần trước khi dùng. Lưu số ô khác gợi ý gốc lúc dùng để
-- phân biệt "dùng nguyên gợi ý" với "dùng sau khi sửa" khi đo giá trị AI; lưới gốc trong output_json không đổi.
ALTER TABLE menu_ai_drafts
    ADD COLUMN edited_cells INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_menu_ai_drafts_edited_cells CHECK (edited_cells >= 0);
