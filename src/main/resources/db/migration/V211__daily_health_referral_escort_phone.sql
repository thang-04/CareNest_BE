-- Số điện thoại người đi cùng khi chuyển trẻ đến cơ sở y tế (Cần chốt 28/11/2026)
ALTER TABLE daily_health_entries ADD COLUMN referral_escort_phone VARCHAR(10);
