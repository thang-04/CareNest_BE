-- Hạn nhận xét tuần cố định 1 ngày học sau tuần, không cấu hình theo năm học (Cần chốt 14/11/2026; ngoại lệ thì BGH
-- mở lại lớp – tuần). Bỏ cột thêm ở V128.
ALTER TABLE school_years DROP CONSTRAINT ck_school_years_weekly_comment_deadline_days;
ALTER TABLE school_years DROP COLUMN weekly_comment_deadline_days;
