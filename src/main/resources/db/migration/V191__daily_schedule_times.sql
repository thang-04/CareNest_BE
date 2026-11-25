ALTER TABLE attendance_cutoffs
    ADD COLUMN dropoff_open_from TIME NOT NULL DEFAULT '06:30',
    ADD COLUMN morning_start TIME NOT NULL DEFAULT '07:00',
    ADD COLUMN morning_end TIME NOT NULL DEFAULT '11:00',
    ADD COLUMN afternoon_start TIME NOT NULL DEFAULT '13:30',
    ADD COLUMN pickup_start TIME NOT NULL DEFAULT '15:00',
    ADD COLUMN full_day_pickup_from TIME NOT NULL DEFAULT '15:30',
    ADD COLUMN pickup_end TIME NOT NULL DEFAULT '17:00',
    ADD COLUMN afternoon_end TIME NOT NULL DEFAULT '17:00';
