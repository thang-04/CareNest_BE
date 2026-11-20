-- Dữ liệu dev: trẻ đăng ký vào năm học sắp diễn ra (V147), chưa xếp lớp, chờ ở màn Xếp lớp năm học mới; chạy lại
-- không nhân đôi.
INSERT INTO children (code, full_name, date_of_birth, gender, status, intake_campus_id, enrolled_on)
SELECT v.code, v.full_name, v.dob::date, v.gender, 'PENDING_PLACEMENT', c.id, v.enrolled_on::date
FROM (VALUES
    ('HS26301', 'Lê Bảo Ngọc', '2024-05-14', 'FEMALE', 'CS-A', '2026-11-16'),
    ('HS26302', 'Đỗ Minh Khang', '2023-03-09', 'MALE', 'CS-B', '2026-11-17')
) AS v (code, full_name, dob, gender, campus, enrolled_on)
JOIN campuses c ON c.code = v.campus
ON CONFLICT (code) DO NOTHING;

INSERT INTO child_code_sequences (code_prefix, last_number)
SELECT left(code, 4), max(substring(code FROM 5)::integer) FROM children GROUP BY left(code, 4)
ON CONFLICT (code_prefix) DO UPDATE SET last_number = GREATEST(child_code_sequences.last_number, EXCLUDED.last_number);

INSERT INTO planned_admissions (child_id, school_year_id, campus_id, start_date, status, created_by)
SELECT ch.id, y.id, ch.intake_campus_id, y.start_date, 'PENDING',
       (SELECT a.user_id FROM staff_assignments a WHERE a.staff_role = 'VICE_PRINCIPAL'
            AND a.campus_id = ch.intake_campus_id ORDER BY a.id LIMIT 1)
FROM children ch
JOIN school_years y ON y.name = '2027-2028' AND y.status = 'PLANNED'
WHERE ch.code IN ('HS26301', 'HS26302')
ON CONFLICT (child_id, school_year_id) DO NOTHING;
