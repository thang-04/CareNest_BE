-- Tiếp nhận trẻ, phụ huynh trên hồ sơ, tài khoản phụ huynh theo CCCD (plan 2026-10-28-child-enrollment-guardian-accounts,
-- mục 2–3 của Đặc tả). Chưa có dữ liệu thật: trẻ có sẵn chỉ là dữ liệu demo, seed dev nhập đủ thông tin (CHD-08).

-- Quyền tiếp nhận / sửa hồ sơ / xếp lớp (CHD-01, D-1 A): PHT mặc định bật, HT tắt được (AUTH-10)
INSERT INTO role_permissions (role, permission_code) VALUES
    ('VICE_PRINCIPAL', 'CHILD_ENROLL'),
    ('VICE_PRINCIPAL', 'CHILD_PROFILE_EDIT'),
    ('VICE_PRINCIPAL', 'CHILD_PLACEMENT');

-- Quan hệ 6 loại cho cả hồ sơ lẫn liên kết tài khoản (AUTH-18, D-5a B); OTHER chỉ còn ở dữ liệu cũ
ALTER TABLE guardian_child_links DROP CONSTRAINT ck_guardian_child_links_relationship;
ALTER TABLE guardian_child_links ADD CONSTRAINT ck_guardian_child_links_relationship
    CHECK (relationship IN ('FATHER', 'MOTHER', 'GRANDFATHER', 'GRANDMOTHER', 'SIBLING', 'GUARDIAN', 'OTHER'));

-- CCCD phụ huynh là khóa nhận diện (AUTH-18): một CCCD một tài khoản; tài khoản cũ bổ sung khi hồ sơ trẻ ghi CCCD
ALTER TABLE user_accounts ADD COLUMN citizen_id VARCHAR(12);
ALTER TABLE user_accounts ADD CONSTRAINT uk_user_accounts_citizen_id UNIQUE (citizen_id);
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_citizen_id CHECK (citizen_id ~ '^[0-9]{12}$');

-- Trẻ: mã, giới tính, trạng thái (CHD-03), điểm trường khi chưa có lớp (CHD-02), ngày tiếp nhận / nghỉ / mở lại hồ sơ
ALTER TABLE children
    ADD COLUMN code             VARCHAR(20),
    ADD COLUMN gender           VARCHAR(10),
    ADD COLUMN status           VARCHAR(20),
    ADD COLUMN intake_campus_id BIGINT,
    ADD COLUMN enrolled_on      DATE,
    ADD COLUMN left_on          DATE,
    ADD COLUMN left_reason      VARCHAR(300),
    ADD COLUMN readmitted_on    DATE;

-- Trẻ có sẵn (mục 3.9): Đang học khi có enrollment đang mở, không thì Chờ xếp lớp ở điểm trường của lớp gần nhất;
-- ngày tiếp nhận = enrollment đầu tiên, không có thì ngày tạo hồ sơ
UPDATE children ch SET
    status = CASE WHEN EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id AND e.valid_to IS NULL)
                  THEN 'ACTIVE' ELSE 'PENDING_PLACEMENT' END,
    enrolled_on = COALESCE((SELECT min(e.valid_from) FROM enrollments e WHERE e.child_id = ch.id),
                           (ch.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date);
UPDATE children ch SET intake_campus_id = (
    SELECT r.campus_id FROM enrollments e JOIN classrooms r ON r.id = e.classroom_id
    WHERE e.child_id = ch.id
    ORDER BY e.valid_from DESC, e.id DESC
    LIMIT 1)
WHERE ch.status = 'PENDING_PLACEMENT';

-- Mã trẻ = HS + 2 số cuối năm tiếp nhận + số thứ tự trong năm, tối thiểu 3 chữ số (BE giả nextCode)
UPDATE children ch SET code = v.code
FROM (SELECT id,
             'HS' || to_char(enrolled_on, 'YY')
                  || lpad((row_number() OVER (PARTITION BY to_char(enrolled_on, 'YY') ORDER BY id))::text, 3, '0') AS code
      FROM children) v
WHERE v.id = ch.id;

ALTER TABLE children
    ALTER COLUMN code SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN enrolled_on SET NOT NULL,
    ADD CONSTRAINT uk_children_code UNIQUE (code),
    ADD CONSTRAINT fk_children_intake_campus_id FOREIGN KEY (intake_campus_id) REFERENCES campuses (id),
    ADD CONSTRAINT ck_children_status CHECK (status IN ('ACTIVE', 'PENDING_PLACEMENT', 'LEFT')),
    ADD CONSTRAINT ck_children_gender CHECK (gender IN ('MALE', 'FEMALE')),
    -- Ngày, lý do nghỉ chỉ có khi Đã nghỉ học; mở lại hồ sơ xóa cả hai (CHD-03)
    ADD CONSTRAINT ck_children_left CHECK ((status = 'LEFT') = (left_on IS NOT NULL) AND (left_on IS NULL) = (left_reason IS NULL)),
    ADD CONSTRAINT ck_children_left_on CHECK (left_on IS NULL OR left_on >= enrolled_on);
-- Hồ sơ mới/sửa phải đủ (CHD-08): có giới tính; trẻ chờ xếp lớp có điểm trường tiếp nhận (CHD-02).
-- NOT VALID: trẻ có trước V10 (chỉ dữ liệu demo) chưa có giới tính — nhập đủ khi sửa hồ sơ, seed dev đã nhập đủ (mục 3.9)
ALTER TABLE children ADD CONSTRAINT ck_children_complete
    CHECK (gender IS NOT NULL AND (status <> 'PENDING_PLACEMENT' OR intake_campus_id IS NOT NULL)) NOT VALID;
CREATE INDEX idx_children_intake_campus_id ON children (intake_campus_id);
CREATE INDEX idx_children_date_of_birth ON children (date_of_birth);

-- Số thứ tự mã trẻ theo năm: tăng bằng upsert trong giao dịch tiếp nhận, nhiều người lưu cùng lúc không cấp trùng mã
CREATE TABLE child_code_sequences (
    code_prefix VARCHAR(10) PRIMARY KEY,
    last_number INTEGER     NOT NULL,
    CONSTRAINT ck_child_code_sequences_last_number CHECK (last_number > 0)
);
INSERT INTO child_code_sequences (code_prefix, last_number)
SELECT left(code, 4), count(*) FROM children GROUP BY left(code, 4);

-- Một trẻ một lớp đang học (mục 3.5, CMR-04); chồng thời gian giữa các enrollment đã đóng do service kiểm
CREATE UNIQUE INDEX uk_enrollments_child_open ON enrollments (child_id) WHERE valid_to IS NULL;
ALTER TABLE enrollments
    ADD COLUMN reason     VARCHAR(300),
    ADD COLUMN created_by BIGINT,
    ADD CONSTRAINT fk_enrollments_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id);

-- Hành chính của trẻ, 1–1 (CHD-05; dữ liệu nhạy cảm, phiếu khám đọc từ đây): bắt buộc trừ số định danh, nhóm máu,
-- tuần thai khi không sinh non; nguồn chi trả luôn BHYT, không nhập
CREATE TABLE child_profiles (
    child_id         BIGINT PRIMARY KEY,
    id_number        VARCHAR(20),
    premature        VARCHAR(3)   NOT NULL,
    gestation_weeks  SMALLINT,
    ethnicity        VARCHAR(100) NOT NULL,
    beneficiary      VARCHAR(100) NOT NULL,
    payment_source   VARCHAR(20)  NOT NULL DEFAULT 'BHYT',
    blood_type       VARCHAR(100),
    address_province VARCHAR(100) NOT NULL,
    address_ward     VARCHAR(100) NOT NULL,
    address_street   VARCHAR(300) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_child_profiles_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT ck_child_profiles_premature CHECK (premature IN ('YES', 'NO')),
    CONSTRAINT ck_child_profiles_gestation_weeks CHECK (gestation_weeks BETWEEN 20 AND 42),
    CONSTRAINT ck_child_profiles_gestation_required CHECK (premature = 'NO' OR gestation_weeks IS NOT NULL),
    CONSTRAINT ck_child_profiles_payment_source CHECK (payment_source = 'BHYT')
);

-- Phụ huynh trên hồ sơ trẻ, có trước khi có tài khoản (AUTH-20); tối đa 3 người, position 0–2 theo thứ tự nhập (AUTH-18).
-- Liên kết tài khoản (nguồn scope, AUTH-04) vẫn ở guardian_child_links; user_id = tài khoản đã kích hoạt cho trẻ.
CREATE TABLE child_guardians (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id     BIGINT       NOT NULL,
    position     SMALLINT     NOT NULL,
    full_name    VARCHAR(150) NOT NULL,
    relationship VARCHAR(30)  NOT NULL,
    phone        VARCHAR(10)  NOT NULL,
    citizen_id   VARCHAR(12),
    email        VARCHAR(255),
    user_id      BIGINT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_child_guardians_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_child_guardians_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_child_guardians_position CHECK (position BETWEEN 0 AND 2),
    CONSTRAINT ck_child_guardians_relationship
        CHECK (relationship IN ('FATHER', 'MOTHER', 'GRANDFATHER', 'GRANDMOTHER', 'SIBLING', 'GUARDIAN', 'OTHER')),
    -- SĐT lưu dạng chuẩn hóa = tên đăng nhập của tài khoản phụ huynh
    CONSTRAINT ck_child_guardians_phone CHECK (phone ~ '^0[0-9]{9}$'),
    CONSTRAINT ck_child_guardians_citizen_id CHECK (citizen_id ~ '^[0-9]{12}$'),
    -- Hai phụ huynh của một trẻ không chung SĐT/CCCD; DEFERRABLE để Sửa hồ sơ đổi thông tin giữa hai người trong một lần lưu
    CONSTRAINT uk_child_guardians_child_position UNIQUE (child_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_child_guardians_child_phone UNIQUE (child_id, phone) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_child_guardians_child_citizen_id UNIQUE (child_id, citizen_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_child_guardians_child_user UNIQUE (child_id, user_id)
);
CREATE INDEX idx_child_guardians_phone ON child_guardians (phone);
CREATE INDEX idx_child_guardians_citizen_id ON child_guardians (citizen_id);
CREATE INDEX idx_child_guardians_user_id ON child_guardians (user_id);

-- Mỗi liên kết có sẵn ⇒ một phụ huynh trên hồ sơ (mục 3.9): SĐT = tên đăng nhập, email theo tài khoản, CCCD nhập bổ sung.
-- Tên đăng nhập không phải SĐT (tài khoản demo cũ) không chuyển được — seed dev tự ghi phụ huynh cho trẻ demo.
INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, email, user_id)
SELECT v.child_id, v.position, v.full_name, v.relationship, v.username, v.email, v.user_id
FROM (SELECT l.child_id, l.relationship, u.id AS user_id, u.username, u.email, u.full_name,
             (row_number() OVER (PARTITION BY l.child_id ORDER BY l.created_at, l.id) - 1)::smallint AS position
      FROM guardian_child_links l
      JOIN user_accounts u ON u.id = l.user_id
      WHERE u.username ~ '^0[0-9]{9}$') v
WHERE v.position <= 2;
-- CCCD phụ huynh bắt buộc (AUTH-18); NOT VALID: phụ huynh vừa chuyển từ liên kết cũ chưa có CCCD (mục 3.9)
ALTER TABLE child_guardians ADD CONSTRAINT ck_child_guardians_citizen_id_required CHECK (citizen_id IS NOT NULL) NOT VALID;

-- Khai báo sức khỏe, 1–1 với trẻ (CHD-06, HLT-06). Mỗi mục REPORTED (Có) / NONE_REPORTED (Không có) / NOT_PROVIDED
-- (Chưa cung cấp); tiền sử bản thân, gia đình, tiếp xúc người bệnh lao chỉ Có/Không. "Có" phải có nội dung, khác "Có" thì không.
CREATE TABLE child_health_declarations (
    child_id             BIGINT PRIMARY KEY,
    allergies_state      VARCHAR(20)  NOT NULL,
    diet_state           VARCHAR(20)  NOT NULL,
    diet_text            VARCHAR(500),
    other_notes_state    VARCHAR(20)  NOT NULL,
    other_notes_text     VARCHAR(500),
    self_history_state   VARCHAR(20)  NOT NULL,
    self_history_text    VARCHAR(300),
    family_history_state VARCHAR(20)  NOT NULL,
    family_history_text  VARCHAR(300),
    tb_contact_state     VARCHAR(20)  NOT NULL,
    tb_contact_date      DATE,
    declared_by          BIGINT       NOT NULL,
    declared_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_child_health_declarations_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_child_health_declarations_declared_by FOREIGN KEY (declared_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_child_health_declarations_allergies_state
        CHECK (allergies_state IN ('REPORTED', 'NONE_REPORTED', 'NOT_PROVIDED')),
    CONSTRAINT ck_child_health_declarations_diet_state CHECK (diet_state IN ('REPORTED', 'NONE_REPORTED', 'NOT_PROVIDED')),
    CONSTRAINT ck_child_health_declarations_other_notes_state
        CHECK (other_notes_state IN ('REPORTED', 'NONE_REPORTED', 'NOT_PROVIDED')),
    CONSTRAINT ck_child_health_declarations_self_history_state CHECK (self_history_state IN ('REPORTED', 'NONE_REPORTED')),
    CONSTRAINT ck_child_health_declarations_family_history_state
        CHECK (family_history_state IN ('REPORTED', 'NONE_REPORTED')),
    CONSTRAINT ck_child_health_declarations_tb_contact_state CHECK (tb_contact_state IN ('REPORTED', 'NONE_REPORTED')),
    CONSTRAINT ck_child_health_declarations_diet_text CHECK ((diet_state = 'REPORTED') = (diet_text IS NOT NULL)),
    CONSTRAINT ck_child_health_declarations_other_notes_text
        CHECK ((other_notes_state = 'REPORTED') = (other_notes_text IS NOT NULL)),
    CONSTRAINT ck_child_health_declarations_self_history_text
        CHECK ((self_history_state = 'REPORTED') = (self_history_text IS NOT NULL)),
    CONSTRAINT ck_child_health_declarations_family_history_text
        CHECK ((family_history_state = 'REPORTED') = (family_history_text IS NOT NULL)),
    CONSTRAINT ck_child_health_declarations_tb_contact_date
        CHECK ((tb_contact_state = 'REPORTED') = (tb_contact_date IS NOT NULL))
);

-- Dị ứng của khai báo, có hiệu lực ngay khi lưu: bếp, điểm danh đọc bảng này (CHD-06); thứ tự theo id
CREATE TABLE child_allergies (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id BIGINT       NOT NULL,
    name     VARCHAR(100) NOT NULL,
    CONSTRAINT fk_child_allergies_child_id FOREIGN KEY (child_id) REFERENCES child_health_declarations (child_id)
);
CREATE INDEX idx_child_allergies_child_id ON child_allergies (child_id);

-- Lịch sử khai báo sức khỏe, chỉ thêm: allergy_change / history_change = { from, to } khi phần đó đổi
CREATE TABLE child_health_declaration_history (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id       BIGINT      NOT NULL,
    action         VARCHAR(30) NOT NULL,
    user_id        BIGINT      NOT NULL,
    note           VARCHAR(500),
    allergy_change JSONB,
    history_change JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_child_health_declaration_history_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_child_health_declaration_history_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_child_health_declaration_history_action
        CHECK (action IN ('DECLARED', 'UPDATED', 'UPDATED_FROM_FOLLOW_UP'))
);
CREATE INDEX idx_child_health_declaration_history_child_id ON child_health_declaration_history (child_id, created_at);

-- Lịch sử hồ sơ trẻ, chỉ thêm (mục 3.7): changes = [{ field, label, from, to }]; số định danh của trẻ chỉ ghi 3 số cuối
CREATE TABLE child_history (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id   BIGINT      NOT NULL,
    action     VARCHAR(20) NOT NULL,
    user_id    BIGINT      NOT NULL,
    note       VARCHAR(500),
    changes    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_child_history_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_child_history_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_child_history_action CHECK (action IN ('ENROLLED', 'UPDATED', 'WITHDRAWN', 'READMITTED'))
);
CREATE INDEX idx_child_history_child_id ON child_history (child_id, created_at);

-- Lần gửi SMS tài khoản phụ huynh (L-15): không lưu nội dung, mật khẩu; FE hiện lần gửi gần nhất của từng phụ huynh
CREATE TABLE parent_sms_logs (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT      NOT NULL,
    phone   VARCHAR(10) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    status  VARCHAR(10) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_parent_sms_logs_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_parent_sms_logs_purpose CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET', 'USERNAME_CHANGED')),
    CONSTRAINT ck_parent_sms_logs_status CHECK (status IN ('SENT', 'FAILED'))
);
CREATE INDEX idx_parent_sms_logs_user_id ON parent_sms_logs (user_id, sent_at DESC);
