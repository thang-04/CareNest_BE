-- Chuyển lớp giữ nguyên đánh giá cũ và cho lớp mới đánh giá độc lập trong cùng kỳ.
ALTER TABLE child_evaluations DROP CONSTRAINT uk_child_evaluations_child_period;
ALTER TABLE child_evaluations ADD CONSTRAINT uk_child_evaluations_child_period
    UNIQUE(child_id, classroom_id, period_type, period_start);
