-- Bản nháp lưu được khi còn soạn dở: chủ đề chưa có viết tắt, trùng viết tắt, ngày chưa hợp lệ vẫn phải lưu được.
-- Các điều kiện này kiểm ở service khi gửi duyệt / ban hành (EDU-04, EDU-05), không chặn ở DB.
DROP INDEX uk_edu_themes_year_plan_abbr;
ALTER TABLE edu_themes ALTER COLUMN abbr SET DEFAULT '';
ALTER TABLE edu_themes DROP CONSTRAINT ck_edu_themes_date_range;
ALTER TABLE edu_breaks DROP CONSTRAINT ck_edu_breaks_date_range;
