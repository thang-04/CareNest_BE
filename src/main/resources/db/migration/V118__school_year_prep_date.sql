-- CFG-YR-05: ngày mở chuẩn bị năm học (GV xem lớp, danh sách trẻ năm mới, soạn giáo án trước ngày bắt đầu)
ALTER TABLE school_years ADD COLUMN prep_date DATE;

-- Năm cũ: điền mặc định 1 tháng trước ngày bắt đầu, không sớm hơn ngày sau khi năm liền trước kết thúc
UPDATE school_years y
SET prep_date = GREATEST(
        (y.start_date - INTERVAL '1 month')::date,
        COALESCE((SELECT MAX(p.end_date) + 1 FROM school_years p WHERE p.end_date < y.start_date),
                 (y.start_date - INTERVAL '1 month')::date));

ALTER TABLE school_years ADD CONSTRAINT ck_school_years_prep_date CHECK (prep_date <= start_date);
