-- ATT-06 (Cần chốt 27/11/2026): hủy được lịch giờ chốt chưa tới ngày áp dụng; giữ dòng để tra lịch sử.
ALTER TABLE attendance_cutoffs
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by BIGINT;

ALTER TABLE attendance_cutoffs
    ADD CONSTRAINT fk_attendance_cutoffs_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES user_accounts (id);
