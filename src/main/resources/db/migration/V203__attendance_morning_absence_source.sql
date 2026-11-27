-- ATT-03/B2, ATT-14 (user chốt 25/11/2026): giữ nguồn vắng sáng để tính và hiện đúng 0,5 ngày.
ALTER TABLE attendance_records
    ADD COLUMN absent_part_unexcused BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE attendance_record_history
    ADD COLUMN absent_part_unexcused BOOLEAN NOT NULL DEFAULT FALSE;
