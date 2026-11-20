-- Trả lại sau hạn: số ngày lịch người soạn được gửi lại, tính từ lần trả lại trễ đầu tiên, một lần mỗi tài liệu (Tiền 18/11/2026)
ALTER TABLE edu_settings
    ADD COLUMN late_return_grace_days SMALLINT NOT NULL DEFAULT 2;
ALTER TABLE edu_settings
    ADD CONSTRAINT ck_edu_settings_late_return_grace_days
        CHECK (late_return_grace_days BETWEEN 0 AND 30);
