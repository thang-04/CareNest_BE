-- Cần chốt 19/11/2026: mọi cấu hình của năm nào tính theo năm đó. Thông tin nhóm tuổi, phòng / phòng ban và trạng thái
-- điểm trường lưu riêng từng năm; bảng chung (age_groups, rooms, campuses) giữ giá trị của năm đang hoạt động cho các
-- module khác đọc. Năm đã kết thúc vẫn đọc bản chụp lúc đóng năm (V20).

-- Nhóm tuổi: thông tin riêng trên dòng của năm (V148); null = dùng bảng chung
ALTER TABLE school_year_age_group_members
    ADD COLUMN name            VARCHAR(100),
    ADD COLUMN short_name      VARCHAR(50),
    ADD COLUMN age_from_months SMALLINT,
    ADD COLUMN age_to_months   SMALLINT,
    ADD COLUMN meal_sessions   VARCHAR(40),
    ADD COLUMN max_class_size  SMALLINT,
    ADD COLUMN nutrition_note  VARCHAR(500);

UPDATE school_year_age_group_members m
SET name = s.name, short_name = s.short_name, age_from_months = s.age_from_months, age_to_months = s.age_to_months,
    meal_sessions = s.meal_sessions, max_class_size = s.max_class_size, nutrition_note = s.nutrition_note
FROM school_year_age_groups s
WHERE s.school_year_id = m.school_year_id AND s.age_group_id = m.age_group_id;

UPDATE school_year_age_group_members m
SET name = g.name, short_name = g.short_name, age_from_months = g.age_from_months, age_to_months = g.age_to_months,
    meal_sessions = g.meal_sessions, max_class_size = g.max_class_size, nutrition_note = g.nutrition_note
FROM age_groups g
WHERE g.id = m.age_group_id AND m.name IS NULL;

ALTER TABLE school_year_age_group_members
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN short_name SET NOT NULL,
    ALTER COLUMN meal_sessions SET NOT NULL,
    ALTER COLUMN nutrition_note SET NOT NULL,
    ADD CONSTRAINT ck_year_age_groups_age_range CHECK (
        (age_from_months IS NULL AND age_to_months IS NULL)
        OR (age_from_months >= 0 AND age_to_months <= 72 AND age_to_months > age_from_months)),
    ADD CONSTRAINT ck_year_age_groups_max_class_size CHECK (max_class_size IS NULL OR max_class_size > 0);
CREATE UNIQUE INDEX uk_year_age_group_name_ci
    ON school_year_age_group_members (school_year_id, lower(name));

-- Điểm trường: trạng thái hoạt động / ngừng theo năm (V151, V152)
ALTER TABLE school_year_campus_members ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE school_year_campuses ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE school_year_campus_members m
SET status = c.status
FROM campuses c
WHERE c.id = m.campus_id;

ALTER TABLE school_year_campus_members
    ALTER COLUMN code SET NOT NULL,
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN short_name SET NOT NULL,
    ALTER COLUMN address SET NOT NULL,
    ADD CONSTRAINT ck_year_campus_status CHECK (status IN ('ACTIVE', 'INACTIVE'));
CREATE UNIQUE INDEX uk_year_campus_code_ci
    ON school_year_campus_members (school_year_id, lower(code));
CREATE UNIQUE INDEX uk_year_campus_name_ci
    ON school_year_campus_members (school_year_id, lower(name));

-- Phòng / phòng ban của từng năm còn mở: thêm, sửa, xóa ở năm nào chỉ năm đó
CREATE TABLE school_year_room_members (
    school_year_id  BIGINT       NOT NULL REFERENCES school_years (id) ON DELETE CASCADE,
    room_id         BIGINT       NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    campus_id       BIGINT       NOT NULL REFERENCES campuses (id),
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    room_type       VARCHAR(20)  NOT NULL,
    capacity        SMALLINT,
    manager_user_id BIGINT       REFERENCES user_accounts (id),
    status          VARCHAR(20)  NOT NULL,
    note            VARCHAR(500) NOT NULL DEFAULT '',
    PRIMARY KEY (school_year_id, room_id),
    CONSTRAINT fk_year_rooms_year_campus FOREIGN KEY (school_year_id, campus_id)
        REFERENCES school_year_campus_members (school_year_id, campus_id),
    CONSTRAINT ck_year_rooms_room_type
        CHECK (room_type IN ('CLASS', 'KITCHEN', 'FUNCTION_ROOM', 'DEPARTMENT', 'STORAGE')),
    CONSTRAINT ck_year_rooms_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT ck_year_rooms_capacity CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT ck_year_rooms_class_capacity CHECK (room_type <> 'CLASS' OR capacity IS NOT NULL)
);
CREATE INDEX idx_school_year_room_members_room ON school_year_room_members (room_id);
CREATE INDEX idx_school_year_room_members_manager ON school_year_room_members (manager_user_id);
CREATE UNIQUE INDEX uk_year_room_code_ci
    ON school_year_room_members (school_year_id, campus_id, lower(code));
CREATE UNIQUE INDEX uk_year_room_name_ci
    ON school_year_room_members (school_year_id, campus_id, lower(name));

INSERT INTO school_year_room_members (school_year_id, room_id, campus_id, code, name, room_type, capacity,
                                      manager_user_id, status, note)
SELECT y.id, r.id, r.campus_id, r.code, r.name, r.room_type, r.capacity, r.manager_user_id, r.status, r.note
FROM school_years y
CROSS JOIN rooms r
WHERE y.status <> 'CLOSED' AND r.deleted_at IS NULL AND r.status <> 'DELETED';
