-- Nguồn bản nháp nhận xét tháng / học kỳ / cuối năm (OBS-34, AI-01): mẫu câu hoặc AI viết, kèm model và prompt version.
-- Nháp đã có đều là mẫu câu (AI chưa nối), nên mặc định TEMPLATE; model và prompt version chỉ có khi nguồn là AI.
ALTER TABLE child_evaluations
    ADD COLUMN draft_source         VARCHAR(10) NOT NULL DEFAULT 'TEMPLATE',
    ADD COLUMN draft_model          VARCHAR(100),
    ADD COLUMN draft_prompt_version VARCHAR(60),
    ADD CONSTRAINT ck_child_evaluations_draft_source CHECK (draft_source IN ('TEMPLATE', 'AI'));
