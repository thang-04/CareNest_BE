-- Mẫu chung kế hoạch tuần (spec 005, Tiền duyệt 11/11/2026): đổi tên dòng Góc / Ngoài trời / Chiều, thêm dòng "Ăn, ngủ"
-- trước dòng Chiều nếu chưa có, đưa dòng Góc lên trước dòng Ngoài trời. Chỉ sửa dữ liệu cấu hình, không đổi bảng/cột.
UPDATE edu_settings s
SET week_rows = (
  SELECT jsonb_agg(r.row ORDER BY r.pos)::text
  FROM (
    SELECT CASE e.row->>'key'
             WHEN 'goc'        THEN jsonb_set(e.row, '{label}', '"Hoạt động góc"')
             WHEN 'ngoai_troi' THEN jsonb_set(e.row, '{label}', '"Hoạt động ngoài trời"')
             WHEN 'chieu'      THEN jsonb_set(e.row, '{label}', '"Hoạt động chiều"')
             ELSE e.row
           END AS row,
           CASE WHEN e.row->>'key' = 'goc' AND ng.pos IS NOT NULL THEN ng.pos - 0.5 ELSE e.pos END AS pos
    FROM jsonb_array_elements(s.week_rows::jsonb) WITH ORDINALITY AS e(row, pos)
    LEFT JOIN LATERAL (
      SELECT x.pos FROM jsonb_array_elements(s.week_rows::jsonb) WITH ORDINALITY AS x(row, pos)
      WHERE x.row->>'key' = 'ngoai_troi'
    ) ng ON true
    UNION ALL
    SELECT '{"key":"an_ngu","label":"Ăn, ngủ","canMerge":true,"allWeek":true,"extraLabel":null}'::jsonb, c.pos - 0.5
    FROM jsonb_array_elements(s.week_rows::jsonb) WITH ORDINALITY AS c(row, pos)
    WHERE c.row->>'key' = 'chieu'
      AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(s.week_rows::jsonb) y WHERE y->>'key' = 'an_ngu')
  ) r
)
WHERE s.id = 1;
