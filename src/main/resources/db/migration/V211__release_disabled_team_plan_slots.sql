-- EDU-116: xóa phiên bản điều chỉnh kế hoạch tổ là xóa mềm; chỉ bản còn dùng chiếm mã, số phiên bản và bản đang mở (như V106)
ALTER TABLE edu_team_plans DROP CONSTRAINT uq_edu_team_plans_code;
CREATE UNIQUE INDEX uq_edu_team_plan_active_code ON edu_team_plans (code) WHERE disabled = false;
ALTER TABLE edu_team_plans DROP CONSTRAINT uq_edu_team_plan_version;
CREATE UNIQUE INDEX uq_edu_team_plan_version ON edu_team_plans (school_year_id, age_group_id, version_no)
    WHERE disabled = false;
DROP INDEX uq_edu_team_plan_open;
CREATE UNIQUE INDEX uq_edu_team_plan_open ON edu_team_plans (school_year_id, age_group_id)
    WHERE status <> 'APPROVED' AND disabled = false;
