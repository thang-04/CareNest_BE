-- Spec 004 (DB-C, Tiền duyệt 11/11/2026): hạn mới khi Hiệu trưởng mở khóa kế hoạch năm học / tổ (action = 'unlock')
ALTER TABLE edu_plan_events ADD COLUMN deadline DATE NULL;
