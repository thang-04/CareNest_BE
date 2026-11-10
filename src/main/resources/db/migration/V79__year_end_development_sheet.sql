-- Phiếu đánh giá sự phát triển của trẻ cuối năm (OBS-28, Cần chốt 04/11/2026): MT của kế hoạch năm học tích Đạt / Chưa
-- đạt theo trẻ, kèm 4 lưu ý điều chỉnh kế hoạch giáo dục năm sau. Gắn vào đánh giá cuối năm (child_evaluations, YEAR).
ALTER TABLE child_evaluations
    -- Ô GV sửa tay (trước khi xác nhận) hoặc cả phiếu khi đã xác nhận: [{"g":goalLineage,"m":true,"x":manual}]
    ADD COLUMN goal_results     TEXT,
    ADD COLUMN note_for_child   VARCHAR(1000),
    ADD COLUMN note_for_teacher VARCHAR(1000),
    ADD COLUMN note_for_school  VARCHAR(1000),
    ADD COLUMN note_for_parent  VARCHAR(1000);
