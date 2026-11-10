-- OBS-27: bảng đánh giá cuối chủ đề gắn với dòng gốc của chủ đề (giữ qua các bản kế hoạch năm học), để bản kế hoạch mới
-- không làm mất bảng đã lưu / đã xác nhận
UPDATE theme_evaluations e
SET theme_id = t.lineage_id
FROM edu_themes t
WHERE t.id = e.theme_id
  AND t.lineage_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM theme_evaluations o WHERE o.classroom_id = e.classroom_id AND o.theme_id = t.lineage_id);

COMMENT ON COLUMN theme_evaluations.theme_id IS 'Dòng gốc của chủ đề: COALESCE(edu_themes.lineage_id, edu_themes.id)';
