-- V91: Thêm cờ xóa mềm (disabled) cho kế hoạch năm và kế hoạch tổ (BR-10, BR-17, FR-002)
-- Tuyệt đối cấm xóa cứng (no hard delete), thao tác xóa chỉ chuyển disabled = true

ALTER TABLE edu_year_plans ADD COLUMN IF NOT EXISTS disabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE edu_team_plans ADD COLUMN IF NOT EXISTS disabled BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_edu_year_plans_disabled ON edu_year_plans (disabled);
CREATE INDEX IF NOT EXISTS idx_edu_team_plans_disabled ON edu_team_plans (disabled);
