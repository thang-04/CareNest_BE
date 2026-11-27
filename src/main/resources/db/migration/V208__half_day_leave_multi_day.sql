-- ATT-12 (Cần chốt 27/11/2026): đơn nghỉ buổi sáng / buổi chiều được chọn nhiều ngày.
ALTER TABLE leave_requests
    DROP CONSTRAINT ck_leave_requests_half_single_day;
