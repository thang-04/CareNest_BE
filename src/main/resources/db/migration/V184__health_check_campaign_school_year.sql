-- G94-180/HLT-08 (user chốt 2026-11-22, cách B): đợt khám, kỳ cân đo thuộc năm học đang hoạt động lúc tạo, lưu cùng đợt;
-- kích hoạt năm mới không đổi năm của đợt. Null (đợt do seed, test chèn thẳng) ⇒ tính theo ngày bắt đầu như trước.
ALTER TABLE health_check_campaigns
    ADD COLUMN school_year_id BIGINT REFERENCES school_years (id) ON DELETE SET NULL;

-- Đợt đã có: năm học gần nhất đã bắt đầu tính tới ngày bắt đầu đợt (luật cũ)
UPDATE health_check_campaigns c
SET school_year_id = (
    SELECT y.id
    FROM school_years y
    WHERE y.start_date <= c.start_date
    ORDER BY y.start_date DESC
    LIMIT 1);

CREATE INDEX idx_health_check_campaigns_school_year ON health_check_campaigns (school_year_id);
