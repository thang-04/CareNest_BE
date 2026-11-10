-- Nhóm tuổi: độ tuổi theo tháng [from, to), bữa ăn trong ngày, sĩ số tối đa/lớp, ghi chú dinh dưỡng.
-- Cột cho phép NULL vì nhóm tuổi cũ chưa có số liệu; API bắt buộc khi tạo/sửa.
ALTER TABLE age_groups ADD COLUMN age_from_months SMALLINT;
ALTER TABLE age_groups ADD COLUMN age_to_months SMALLINT;
ALTER TABLE age_groups ADD COLUMN meal_sessions VARCHAR(40) NOT NULL DEFAULT '';
ALTER TABLE age_groups ADD COLUMN max_class_size SMALLINT;
ALTER TABLE age_groups ADD COLUMN nutrition_note VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE age_groups
    ADD CONSTRAINT ck_age_groups_age_range CHECK (
        (age_from_months IS NULL AND age_to_months IS NULL)
        OR (age_from_months >= 0 AND age_to_months <= 72 AND age_to_months > age_from_months));
ALTER TABLE age_groups ADD CONSTRAINT ck_age_groups_max_class_size CHECK (max_class_size IS NULL OR max_class_size > 0);
CREATE UNIQUE INDEX uk_age_groups_name ON age_groups (LOWER(name));

-- Giá trị khởi tạo cho 4 nhóm tuổi của V10 lấy theo dữ liệu mẫu FE (Hiệu trưởng sửa lại được)
UPDATE age_groups SET age_from_months = 24, age_to_months = 36, meal_sessions = 'MORNING,LUNCH,AFTERNOON', max_class_size = 25
WHERE code = 'ag-2';
UPDATE age_groups SET age_from_months = 36, age_to_months = 48, meal_sessions = 'LUNCH,AFTERNOON', max_class_size = 25
WHERE code = 'ag-3';
UPDATE age_groups SET age_from_months = 48, age_to_months = 60, meal_sessions = 'LUNCH,AFTERNOON', max_class_size = 30
WHERE code = 'ag-4';
UPDATE age_groups SET age_from_months = 60, age_to_months = 72, meal_sessions = 'LUNCH,AFTERNOON', max_class_size = 35
WHERE code = 'ag-5';

-- Lớp: sĩ số tối đa và trạng thái (đóng cùng năm học). Lớp cũ lấy sĩ số tối đa của nhóm tuổi.
ALTER TABLE classrooms ADD COLUMN capacity SMALLINT;
ALTER TABLE classrooms ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE classrooms ADD CONSTRAINT ck_classrooms_capacity CHECK (capacity IS NULL OR capacity > 0);
ALTER TABLE classrooms ADD CONSTRAINT ck_classrooms_status CHECK (status IN ('ACTIVE', 'CLOSED'));
UPDATE classrooms c SET capacity = g.max_class_size FROM age_groups g WHERE g.id = c.age_group_id;
UPDATE classrooms c SET status = 'CLOSED' FROM school_years y WHERE y.id = c.school_year_id AND y.status = 'CLOSED';
-- Một phòng chỉ là phòng của một lớp trong cùng năm học
CREATE UNIQUE INDEX uk_classrooms_school_year_room ON classrooms (school_year_id, room_id) WHERE room_id IS NOT NULL;
