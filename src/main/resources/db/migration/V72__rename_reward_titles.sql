-- OBS-14 (user chốt 03/11/2026): đổi danh hiệu khen thưởng cũ sang danh hiệu phổ biến của mầm non.
-- titles lưu dạng "a|b"; danh hiệu không còn dùng (năng khiếu nghệ thuật) bị bỏ.
UPDATE reward_list_items i
SET titles = COALESCE((
    SELECT string_agg(mapped, '|' ORDER BY ord)
    FROM (
        SELECT DISTINCT ON (mapped) mapped, ord
        FROM (
            SELECT CASE t
                       WHEN 'Bé chăm' THEN 'Bé chăm ngoan'
                       WHEN 'Bé ngoan' THEN 'Bé ngoan'
                       WHEN 'Bé ngoan toàn diện' THEN 'Bé chăm ngoan xuất sắc'
                       WHEN 'Bé khỏe – Bé đẹp' THEN 'Bé khỏe, bé ngoan'
                       WHEN 'Hoàn thành chương trình GDMN' THEN 'Hoàn thành tốt chương trình giáo dục mầm non'
                   END AS mapped,
                   ord
            FROM unnest(string_to_array(i.titles, '|')) WITH ORDINALITY AS x(t, ord)
        ) m
        WHERE mapped IS NOT NULL
        ORDER BY mapped, ord
    ) d
), '')
WHERE i.titles <> '';
