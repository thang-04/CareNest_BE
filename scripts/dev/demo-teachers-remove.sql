-- Gỡ giáo viên demo do scripts/dev/demo-teachers.sql thêm (mọi phân công của họ, rồi tài khoản). Chỉ cho DB dev.
DELETE FROM staff_assignments WHERE user_id IN (SELECT id FROM user_accounts WHERE username LIKE 'demo.gv.%');
DELETE FROM user_accounts WHERE username LIKE 'demo.gv.%';
