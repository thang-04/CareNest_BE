-- EDU-11: cấu hình kế hoạch giáo dục của trường (1 dòng): chuỗi người ký, dòng hoạt động của kế hoạch tuần (JSON)
CREATE TABLE edu_settings (
    id         SMALLINT     PRIMARY KEY DEFAULT 1,
    signers    TEXT         NOT NULL,
    week_rows  TEXT         NOT NULL,
    updated_by BIGINT,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_edu_settings_single CHECK (id = 1),
    CONSTRAINT fk_edu_settings_updated_by FOREIGN KEY (updated_by) REFERENCES user_accounts (id)
);

INSERT INTO edu_settings (signers, week_rows) VALUES (
    '{"year":["TEAM_LEADER","VICE_PRINCIPAL"],"branch":["TEAM_LEADER","VICE_PRINCIPAL"],"week":["TEAM_LEADER","VICE_PRINCIPAL"]}',
    '[{"key":"don","label":"Đón trẻ","canMerge":true,"allWeek":true,"extraLabel":null},'
    || '{"key":"tro_chuyen","label":"Trò chuyện","canMerge":true,"allWeek":true,"extraLabel":null},'
    || '{"key":"the_duc","label":"Thể dục sáng","canMerge":true,"allWeek":true,"extraLabel":null},'
    || '{"key":"hoc","label":"Hoạt động học","canMerge":false,"allWeek":false,"extraLabel":null},'
    || '{"key":"ngoai_troi","label":"Chơi, hoạt động ngoài trời","canMerge":false,"allWeek":false,"extraLabel":"Chơi tự do (cả tuần)"},'
    || '{"key":"goc","label":"Chơi, hoạt động ở các góc","canMerge":true,"allWeek":true,"extraLabel":null},'
    || '{"key":"chieu","label":"Chơi, hoạt động buổi chiều","canMerge":false,"allWeek":false,"extraLabel":"Chơi tự chọn; nêu gương cuối ngày (cả tuần)"},'
    || '{"key":"tra_tre","label":"Trả trẻ","canMerge":true,"allWeek":true,"extraLabel":null}]'
);

-- 5 lĩnh vực của Chương trình GDMN không xóa được; lĩnh vực trường thêm có standard = false
ALTER TABLE edu_domains ADD COLUMN standard BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE edu_domains SET standard = TRUE WHERE sort_order BETWEEN 1 AND 5;
