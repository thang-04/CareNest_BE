-- Dữ liệu dev: phòng / phòng ban riêng của từng năm (V154, Cần chốt 19/11/2026).
-- Năm đã kết thúc dùng bản chụp; năm còn mở nhận thông tin hiện hành. Chạy lại không ghi đè cấu hình đã sửa.
INSERT INTO school_year_room_members (school_year_id, room_id, campus_id, code, name, room_type, capacity,
                                      manager_user_id, status, note)
SELECT s.school_year_id, s.room_id, s.campus_id, s.code, s.name, s.room_type, s.capacity,
       s.manager_user_id, s.status, s.note
FROM school_year_rooms s
JOIN school_years y ON y.id = s.school_year_id AND y.status = 'CLOSED'
JOIN rooms r ON r.id = s.room_id
ON CONFLICT DO NOTHING;

INSERT INTO school_year_room_members (school_year_id, room_id, campus_id, code, name, room_type, capacity,
                                      manager_user_id, status, note)
SELECT y.id, r.id, r.campus_id, r.code, r.name, r.room_type, r.capacity,
       r.manager_user_id, r.status, r.note
FROM school_years y
CROSS JOIN rooms r
WHERE y.status <> 'CLOSED' AND r.deleted_at IS NULL AND r.status <> 'DELETED'
ON CONFLICT DO NOTHING;
