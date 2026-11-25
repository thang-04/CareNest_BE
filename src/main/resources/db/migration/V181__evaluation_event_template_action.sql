-- AI-03: bản nháp hệ thống tạo từ mẫu câu ghi vết riêng (TEMPLATE_DRAFTED), không ghi là AI_DRAFTED
ALTER TABLE child_evaluation_events DROP CONSTRAINT ck_child_evaluation_events_action;
ALTER TABLE child_evaluation_events
    ADD CONSTRAINT ck_child_evaluation_events_action
        CHECK (action IN ('AI_DRAFTED', 'AI_REDRAFTED', 'TEMPLATE_DRAFTED', 'EDITED', 'CONFIRMED'));
