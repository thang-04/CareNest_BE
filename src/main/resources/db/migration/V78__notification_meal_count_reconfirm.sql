-- NUT-03 (Cần chốt 04/11/2026): thông báo PHT khi sổ báo ăn đã xác nhận có số mới cần xác nhận lại
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM'));
