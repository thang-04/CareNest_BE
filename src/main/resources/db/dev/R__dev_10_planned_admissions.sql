-- Dữ liệu dev: trẻ đăng ký vào năm học sắp diễn ra (V147), chưa xếp lớp, chờ ở màn Xếp lớp năm học mới; chạy lại
-- không nhân đôi.
WITH fixture (code, full_name, dob, gender, campus, enrolled_on) AS (VALUES
    ('HS26301', 'Lê Bảo Ngọc', '2024-05-14', 'FEMALE', 'CS-A', '2026-11-16'),
    ('HS26302', 'Đỗ Minh Khang', '2023-03-09', 'MALE', 'CS-B', '2026-11-17')
), seed AS (
    SELECT f.*, c.id AS campus_id FROM fixture f JOIN campuses c ON c.code = f.campus
), inserted AS (
    INSERT INTO children (code, full_name, date_of_birth, gender, status, intake_campus_id, enrolled_on)
    SELECT code, full_name, dob::date, gender, 'PENDING_PLACEMENT', campus_id, enrolled_on::date
    FROM seed
    ON CONFLICT (code) DO NOTHING
    RETURNING id, intake_campus_id
), pending_fixture AS (
    SELECT id, intake_campus_id FROM inserted
    UNION ALL
    -- Mã trùng với hồ sơ khác trong DB dev cũ không phải là trẻ mẫu của seed này.
    SELECT ch.id, ch.intake_campus_id
    FROM children ch JOIN seed s ON s.code = ch.code
        AND s.full_name = ch.full_name AND s.dob::date = ch.date_of_birth
        AND s.gender = ch.gender AND s.campus_id = ch.intake_campus_id
        AND s.enrolled_on::date = ch.enrolled_on
    WHERE ch.status = 'PENDING_PLACEMENT'
)
INSERT INTO planned_admissions (child_id, school_year_id, campus_id, start_date, status, created_by)
SELECT ch.id, y.id, ch.intake_campus_id, y.start_date, 'PENDING',
       (SELECT a.user_id FROM staff_assignments a WHERE a.staff_role = 'VICE_PRINCIPAL'
            AND a.campus_id = ch.intake_campus_id ORDER BY a.id LIMIT 1)
FROM pending_fixture ch
JOIN school_years y ON y.name = '2027-2028' AND y.status = 'PLANNED'
ON CONFLICT (child_id, school_year_id) DO NOTHING;

INSERT INTO child_code_sequences (code_prefix, last_number)
SELECT left(code, 4), max(substring(code FROM 5)::integer) FROM children GROUP BY left(code, 4)
ON CONFLICT (code_prefix) DO UPDATE SET last_number = GREATEST(child_code_sequences.last_number, EXCLUDED.last_number);
