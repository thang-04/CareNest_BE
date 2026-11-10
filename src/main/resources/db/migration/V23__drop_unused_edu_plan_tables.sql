-- Thu gọn schema kế hoạch năm (Tiền chốt 22/10):
-- người/thời điểm ban hành đã có ở edu_year_plans.issued_by, issued_at và lịch sử ở edu_plan_events;
-- thứ tự người ký khi in làm ở task Cấu hình kế hoạch; UI không có đính kèm file.
DROP TABLE edu_plan_approvals;
DROP TABLE edu_approval_steps;
ALTER TABLE edu_year_plans DROP COLUMN attachment_key;
