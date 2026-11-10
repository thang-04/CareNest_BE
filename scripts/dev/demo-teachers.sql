-- DỮ LIỆU GIẢ CHỈ CHO DB DEV: thêm 4 giáo viên demo mỗi điểm trường (CS-A, CS-B) để thử phân công 2 giáo viên / lớp.
-- Không phải migration, không chạy tự động. Chạy tay: psql -h localhost -U postgres -d carenest -f scripts/dev/demo-teachers.sql
-- Gỡ: scripts/dev/demo-teachers-remove.sql
-- Danh sách giáo viên tạm (phương án C) lấy người từng có phân công giáo viên; mỗi người có một phân công đã kết thúc
-- (01–31/08/2026, trước năm 2026-2027) ở một lớp của điểm trường ⇒ hiện trong danh sách chọn, chưa dạy lớp nào.
-- Không có user Keycloak: không đăng nhập được bằng các tài khoản này.

INSERT INTO user_accounts (keycloak_user_id, username, email, full_name)
SELECT v.kc, v.username, v.username || '@carenest.test', v.full_name
FROM (VALUES
          ('00000000-0000-4000-b000-000000000001', 'demo.gv.a1', 'Nguyễn Thị Lan (demo)'),
          ('00000000-0000-4000-b000-000000000002', 'demo.gv.a2', 'Trần Thu Hà (demo)'),
          ('00000000-0000-4000-b000-000000000003', 'demo.gv.a3', 'Lê Minh Anh (demo)'),
          ('00000000-0000-4000-b000-000000000004', 'demo.gv.a4', 'Phạm Thu Hương (demo)'),
          ('00000000-0000-4000-b000-000000000005', 'demo.gv.b1', 'Đỗ Thị Mai (demo)'),
          ('00000000-0000-4000-b000-000000000006', 'demo.gv.b2', 'Vũ Ngọc Anh (demo)'),
          ('00000000-0000-4000-b000-000000000007', 'demo.gv.b3', 'Hoàng Thị Thảo (demo)'),
          ('00000000-0000-4000-b000-000000000008', 'demo.gv.b4', 'Bùi Thanh Tâm (demo)')
     ) AS v (kc, username, full_name)
ON CONFLICT (keycloak_user_id) DO NOTHING;

INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to)
SELECT u.id, 'TEACHER', r.id, DATE '2026-08-01', DATE '2026-08-31'
FROM user_accounts u
JOIN campuses c ON c.code = CASE WHEN u.username LIKE 'demo.gv.a%' THEN 'CS-A' ELSE 'CS-B' END
JOIN LATERAL (
    SELECT cl.id FROM classrooms cl
    JOIN school_years y ON y.id = cl.school_year_id AND y.name = '2026-2027'
    WHERE cl.campus_id = c.id
    ORDER BY cl.id
    LIMIT 1
) r ON TRUE
WHERE u.username LIKE 'demo.gv.%'
  AND NOT EXISTS (SELECT 1 FROM staff_assignments a WHERE a.user_id = u.id);
