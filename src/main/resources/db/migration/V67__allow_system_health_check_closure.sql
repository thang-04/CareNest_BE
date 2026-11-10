-- Đóng đợt tự động ghi nhận tác nhân hệ thống, không gán thao tác cho người tạo.
ALTER TABLE health_check_campaigns DROP CONSTRAINT ck_health_check_campaigns_closed;
ALTER TABLE health_check_campaigns ADD CONSTRAINT ck_health_check_campaigns_closed
    CHECK (closed_by IS NULL OR closed_at IS NOT NULL);

ALTER TABLE health_check_campaign_history ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE health_check_campaign_history ADD CONSTRAINT ck_health_check_campaign_history_actor
    CHECK (user_id IS NOT NULL OR action = 'CLOSED');
