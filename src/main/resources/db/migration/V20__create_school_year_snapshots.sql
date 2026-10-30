-- CFG-YR-02: năm học kết thúc ⇒ chụp lại phòng, điểm trường, nhóm tuổi của năm đó. Xem năm đã kết thúc đọc bản chụp,
-- sửa/xóa ở năm sau không làm đổi dữ liệu năm cũ. Không FK tới bảng gốc: bản ghi gốc có thể bị xóa hẳn sau này.
CREATE TABLE school_year_rooms (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_year_id  BIGINT       NOT NULL REFERENCES school_years (id) ON DELETE CASCADE,
    room_id         BIGINT       NOT NULL,
    campus_id       BIGINT       NOT NULL,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    room_type       VARCHAR(20)  NOT NULL,
    capacity        SMALLINT,
    manager_user_id BIGINT,
    status          VARCHAR(20)  NOT NULL,
    note            VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uk_school_year_rooms UNIQUE (school_year_id, room_id)
);

CREATE TABLE school_year_campuses (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_year_id BIGINT       NOT NULL REFERENCES school_years (id) ON DELETE CASCADE,
    campus_id      BIGINT       NOT NULL,
    code           VARCHAR(30)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    short_name     VARCHAR(50)  NOT NULL,
    address        VARCHAR(255) NOT NULL,
    phone          VARCHAR(20),
    is_main        BOOLEAN      NOT NULL,
    CONSTRAINT uk_school_year_campuses UNIQUE (school_year_id, campus_id)
);

CREATE TABLE school_year_age_groups (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_year_id  BIGINT       NOT NULL REFERENCES school_years (id) ON DELETE CASCADE,
    age_group_id    BIGINT       NOT NULL,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    short_name      VARCHAR(50)  NOT NULL,
    sort_order      SMALLINT     NOT NULL,
    age_from_months SMALLINT,
    age_to_months   SMALLINT,
    meal_sessions   VARCHAR(40)  NOT NULL,
    max_class_size  SMALLINT,
    nutrition_note  VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uk_school_year_age_groups UNIQUE (school_year_id, age_group_id)
);

-- Năm đã kết thúc trước khi có bản chụp: phòng theo thời điểm tạo/xóa so với lúc đóng năm; tên, sức chứa,
-- điểm trường, nhóm tuổi lấy giá trị hiện có (trước đây không lưu lịch sử sửa).
INSERT INTO school_year_rooms (school_year_id, room_id, campus_id, code, name, room_type, capacity, manager_user_id,
                               status, note)
SELECT y.id, r.id, r.campus_id, r.code, r.name, r.room_type, r.capacity, r.manager_user_id,
       CASE WHEN r.status = 'DELETED' THEN 'ACTIVE' ELSE r.status END, r.note
FROM school_years y
JOIN rooms r ON r.created_at <= COALESCE(y.closed_at, now())
            AND (r.deleted_at IS NULL OR r.deleted_at > COALESCE(y.closed_at, now()))
WHERE y.status = 'CLOSED';

INSERT INTO school_year_campuses (school_year_id, campus_id, code, name, short_name, address, phone, is_main)
SELECT y.id, c.id, c.code, c.name, c.short_name, c.address, c.phone, c.is_main
FROM school_years y
JOIN campuses c ON c.created_at <= COALESCE(y.closed_at, now())
WHERE y.status = 'CLOSED';

INSERT INTO school_year_age_groups (school_year_id, age_group_id, code, name, short_name, sort_order, age_from_months,
                                    age_to_months, meal_sessions, max_class_size, nutrition_note)
SELECT y.id, g.id, g.code, g.name, g.short_name, g.sort_order, g.age_from_months, g.age_to_months, g.meal_sessions,
       g.max_class_size, g.nutrition_note
FROM school_years y
CROSS JOIN age_groups g
WHERE y.status = 'CLOSED';
