-- Ngày kết thúc học kỳ I theo năm học (Cấu hình thời gian, 30/10/2026). Học kỳ II bắt đầu ngày hôm sau đến hết năm học.
ALTER TABLE school_years ADD COLUMN semester1_end DATE;
ALTER TABLE school_years
    ADD CONSTRAINT ck_school_years_semester1_end
        CHECK (semester1_end IS NULL OR (semester1_end >= start_date AND semester1_end < end_date));
