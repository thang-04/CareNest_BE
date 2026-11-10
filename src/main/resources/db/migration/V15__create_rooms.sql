-- Phòng của điểm trường (Cấu hình trường › Phòng ban): phòng học, bếp, phòng chức năng, phòng ban, kho.
-- Xóa phòng đã từng được lớp dùng là xóa mềm (status DELETED) để lớp năm cũ vẫn hiện tên phòng.
CREATE TABLE rooms (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id       BIGINT       NOT NULL,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    room_type       VARCHAR(20)  NOT NULL,
    capacity        SMALLINT,
    manager_user_id BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    note            VARCHAR(500) NOT NULL DEFAULT '',
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_rooms_campus_id FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_rooms_manager_user_id FOREIGN KEY (manager_user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_rooms_room_type CHECK (room_type IN ('CLASS', 'KITCHEN', 'FUNCTION_ROOM', 'DEPARTMENT', 'STORAGE')),
    CONSTRAINT ck_rooms_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT ck_rooms_capacity CHECK (capacity IS NULL OR capacity > 0),
    -- Phòng học bắt buộc có sức chứa
    CONSTRAINT ck_rooms_class_capacity CHECK (room_type <> 'CLASS' OR capacity IS NOT NULL)
);

-- Mã/tên phòng không trùng trong một điểm trường (phòng đã xóa mềm không tính)
CREATE UNIQUE INDEX uk_rooms_campus_code ON rooms (campus_id, LOWER(code)) WHERE status <> 'DELETED';
CREATE UNIQUE INDEX uk_rooms_campus_name ON rooms (campus_id, LOWER(name)) WHERE status <> 'DELETED';
CREATE INDEX idx_rooms_manager_user_id ON rooms (manager_user_id);

ALTER TABLE classrooms ADD COLUMN room_id BIGINT;
ALTER TABLE classrooms ADD CONSTRAINT fk_classrooms_room_id FOREIGN KEY (room_id) REFERENCES rooms (id);
CREATE INDEX idx_classrooms_room_id ON classrooms (room_id);
