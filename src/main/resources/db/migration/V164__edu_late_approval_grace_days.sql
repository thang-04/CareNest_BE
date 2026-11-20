-- EDU-97: số ngày lịch người duyệt còn được xử lý sau hạn ban hành.
ALTER TABLE edu_settings
    ADD COLUMN late_approval_grace_days SMALLINT NOT NULL DEFAULT 2;
ALTER TABLE edu_settings
    ADD CONSTRAINT ck_edu_settings_late_approval_grace_days
        CHECK (late_approval_grace_days BETWEEN 0 AND 30);
