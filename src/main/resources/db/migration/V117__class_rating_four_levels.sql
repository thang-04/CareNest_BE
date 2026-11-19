-- OBS-24 (Cần chốt 10/11/2026): xếp loại nhóm lớp 4 mức Xuất sắc / Tốt / Khá / Trung bình (thay Tốt / Khá / Đạt / Chưa đạt).
-- Dữ liệu cũ: Đạt, Chưa đạt chuyển thành Trung bình (mức thấp nhất của thang mới).
ALTER TABLE class_ratings DROP CONSTRAINT ck_class_ratings_levels;

UPDATE class_ratings SET
    records = CASE WHEN records IN ('DAT', 'CHUA_DAT') THEN 'TRUNG_BINH' ELSE records END,
    program = CASE WHEN program IN ('DAT', 'CHUA_DAT') THEN 'TRUNG_BINH' ELSE program END,
    theme = CASE WHEN theme IN ('DAT', 'CHUA_DAT') THEN 'TRUNG_BINH' ELSE theme END,
    environment = CASE WHEN environment IN ('DAT', 'CHUA_DAT') THEN 'TRUNG_BINH' ELSE environment END,
    routine = CASE WHEN routine IN ('DAT', 'CHUA_DAT') THEN 'TRUNG_BINH' ELSE routine END,
    health = CASE WHEN health IN ('DAT', 'CHUA_DAT') THEN 'TRUNG_BINH' ELSE health END,
    parents = CASE WHEN parents IN ('DAT', 'CHUA_DAT') THEN 'TRUNG_BINH' ELSE parents END,
    overall = CASE WHEN overall IN ('DAT', 'CHUA_DAT') THEN 'TRUNG_BINH' ELSE overall END;

ALTER TABLE class_ratings ADD CONSTRAINT ck_class_ratings_levels CHECK (
    (records IS NULL OR records IN ('XUAT_SAC', 'TOT', 'KHA', 'TRUNG_BINH'))
    AND (program IS NULL OR program IN ('XUAT_SAC', 'TOT', 'KHA', 'TRUNG_BINH'))
    AND (theme IS NULL OR theme IN ('XUAT_SAC', 'TOT', 'KHA', 'TRUNG_BINH'))
    AND (environment IS NULL OR environment IN ('XUAT_SAC', 'TOT', 'KHA', 'TRUNG_BINH'))
    AND (routine IS NULL OR routine IN ('XUAT_SAC', 'TOT', 'KHA', 'TRUNG_BINH'))
    AND (health IS NULL OR health IN ('XUAT_SAC', 'TOT', 'KHA', 'TRUNG_BINH'))
    AND (parents IS NULL OR parents IN ('XUAT_SAC', 'TOT', 'KHA', 'TRUNG_BINH'))
    AND (overall IS NULL OR overall IN ('XUAT_SAC', 'TOT', 'KHA', 'TRUNG_BINH')));
