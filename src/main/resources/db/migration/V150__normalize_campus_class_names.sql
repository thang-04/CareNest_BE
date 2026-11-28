-- Cần chốt 19/11/2026: tên điểm trường, tên lớp lưu dạng chuẩn hóa (bỏ khoảng trắng đầu/cuối, gộp nhiều khoảng trắng,
-- dấu tiếng Việt NFC) để so trùng không lọt "Cơ sở  1" / kiểu gõ dấu khác.
-- Tên nào chuẩn hóa xong bị trùng với tên khác thì GIỮ NGUYÊN (không tự sửa/xóa), chỉ in NOTICE để HT tự đổi tên.

CREATE OR REPLACE FUNCTION pg_temp.cn_norm_name(t TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE AS
$$ SELECT regexp_replace(btrim(normalize(t, NFC), E' \t\r\n'), '\s+', ' ', 'g') $$;

-- Điểm trường: trùng toàn trường, không phân biệt hoa thường (uk_campuses_name)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.id, c.code, c.name
        FROM campuses c
        WHERE EXISTS (SELECT 1 FROM campuses o
                      WHERE o.id <> c.id
                        AND lower(pg_temp.cn_norm_name(o.name)) = lower(pg_temp.cn_norm_name(c.name)))
        ORDER BY c.id
    LOOP
        RAISE NOTICE 'Điểm trường trùng tên sau chuẩn hóa, giữ nguyên để đổi tên: id=% mã=% tên=%', r.id, r.code, r.name;
    END LOOP;
END $$;

UPDATE campuses c
SET name = pg_temp.cn_norm_name(c.name)
WHERE c.name <> pg_temp.cn_norm_name(c.name)
  AND NOT EXISTS (SELECT 1 FROM campuses o
                  WHERE o.id <> c.id
                    AND lower(pg_temp.cn_norm_name(o.name)) = lower(pg_temp.cn_norm_name(c.name)));

-- Lớp: trùng trong cùng năm học + cùng điểm trường, không phân biệt hoa thường
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.id, c.school_year_id, c.campus_id, c.name
        FROM classrooms c
        WHERE EXISTS (SELECT 1 FROM classrooms o
                      WHERE o.id <> c.id
                        AND o.school_year_id = c.school_year_id
                        AND o.campus_id = c.campus_id
                        AND lower(pg_temp.cn_norm_name(o.name)) = lower(pg_temp.cn_norm_name(c.name)))
        ORDER BY c.school_year_id, c.campus_id, c.id
    LOOP
        RAISE NOTICE 'Lớp trùng tên sau chuẩn hóa, giữ nguyên để đổi tên: id=% năm=% điểm trường=% tên=%',
            r.id, r.school_year_id, r.campus_id, r.name;
    END LOOP;
END $$;

UPDATE classrooms c
SET name = pg_temp.cn_norm_name(c.name)
WHERE c.name <> pg_temp.cn_norm_name(c.name)
  AND NOT EXISTS (SELECT 1 FROM classrooms o
                  WHERE o.id <> c.id
                    AND o.school_year_id = c.school_year_id
                    AND o.campus_id = c.campus_id
                    AND lower(pg_temp.cn_norm_name(o.name)) = lower(pg_temp.cn_norm_name(c.name)));
