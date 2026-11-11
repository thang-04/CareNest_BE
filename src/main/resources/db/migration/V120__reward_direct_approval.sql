-- OBS-14 (Cần chốt 11/11/2026): HT duyệt thẳng danh sách PHT chưa xét ⇒ ghi riêng "HT duyệt trực tiếp" trong lịch sử
ALTER TABLE reward_list_events DROP CONSTRAINT ck_reward_list_events_action;
ALTER TABLE reward_list_events ADD CONSTRAINT ck_reward_list_events_action CHECK (
    action IN ('CREATED', 'UPDATED', 'SUBMITTED', 'RESUBMITTED', 'FORWARDED', 'RETURNED', 'APPROVED', 'DIRECT_APPROVED'));
