-- Học kỳ do Hiệu trưởng chọn cả ngày bắt đầu và kết thúc, trong khoảng năm học (Cần chốt 30/10/2026).
-- Thay quy tắc cũ của V42 (HK I từ đầu năm, HK II từ ngày sau HK I tới hết năm). Có thể có ngày nghỉ giữa 2 học kỳ.
ALTER TABLE school_years
    ADD COLUMN semester1_start DATE,
    ADD COLUMN semester2_start DATE,
    ADD COLUMN semester2_end DATE;

-- Năm đã đặt ngày kết thúc HK I theo quy tắc cũ: điền 3 ngày còn lại đúng như quy tắc đó
UPDATE school_years
SET semester1_start = start_date,
    semester2_start = semester1_end + 1,
    semester2_end = end_date
WHERE semester1_end IS NOT NULL;

ALTER TABLE school_years DROP CONSTRAINT ck_school_years_semester1_end;
ALTER TABLE school_years
    ADD CONSTRAINT ck_school_years_semesters CHECK (
        (semester1_start IS NULL AND semester1_end IS NULL AND semester2_start IS NULL AND semester2_end IS NULL)
        OR (semester1_start >= start_date
            AND semester1_start <= semester1_end
            AND semester1_end < semester2_start
            AND semester2_start <= semester2_end
            AND semester2_end <= end_date));
