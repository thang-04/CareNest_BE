-- EDU-07: giữ bản nháp đã xóa để truy vết, chỉ bản còn dùng chiếm mã và slot.
ALTER TABLE edu_year_plans DROP CONSTRAINT uk_edu_year_plans_code;
CREATE UNIQUE INDEX uq_edu_year_plan_active_code ON edu_year_plans(code) WHERE disabled = false;
DROP INDEX uq_edu_year_plan_version;
CREATE UNIQUE INDEX uq_edu_year_plan_version ON edu_year_plans(school_year_id,age_group_id,version_no)
    WHERE disabled = false;
DROP INDEX uq_edu_year_plan_open;
CREATE UNIQUE INDEX uq_edu_year_plan_open ON edu_year_plans(school_year_id,age_group_id)
    WHERE status <> 'APPROVED' AND disabled = false;
